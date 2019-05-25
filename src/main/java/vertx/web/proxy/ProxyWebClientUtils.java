package vertx.web.proxy;

import java.io.IOException;
import java.net.URI;
import java.util.BitSet;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import vertx.web.porxy.utils.URIInfo;

public class ProxyWebClientUtils {
	/**
	 * These are the "hop-by-hop" headers that should not be copied.
	 * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html I use an
	 * HttpClient HeaderGroup class instead of Set&lt;String&gt; because this
	 * approach does case insensitive lookup faster.
	 */
	public static final Map<String, String> hopByHopHeaders;
	static {
		hopByHopHeaders = new HashMap<>();
		String[] headers = new String[] { "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization", "TE",
				"Trailers", "Transfer-Encoding", "Upgrade" };
		for (String header : headers) {
			hopByHopHeaders.put(header, null);
		}
	}
	/**
	 * Take any client cookies that were originally from the proxy and prepare
	 * them to send to the proxy. This relies on cookie headers being set
	 * correctly according to RFC 6265 Sec 5.4. This also blocks any local
	 * cookies from being sent to the proxy.
	 */
	public static String getRealCookie(String domain, String cookieValue, boolean doPreserveCookie, Function<String, Boolean> filter) {
		StringBuilder escapedCookie = new StringBuilder();
		String cookies[] = cookieValue.split("[;,]");
		for (String cookie : cookies) {
			String cookieSplit[] = cookie.split("=");
			if (cookieSplit.length == 2) {
				String cookieName = cookieSplit[0].trim();
				if (filter!=null && filter.apply(cookieName))
					continue;
				
				if (!doPreserveCookie) {
					if (cookieName.startsWith(getCookieNamePrefix(domain, cookieName))) {
						cookieName = cookieName.substring(getCookieNamePrefix(domain, cookieName).length());
						if (escapedCookie.length() > 0) {
							escapedCookie.append("; ");
						}
						escapedCookie.append(cookieName).append("=").append(cookieSplit[1].trim()); 
					}
				}
				else {
					if (escapedCookie.length() > 0) {
						escapedCookie.append("; ");
					}
					escapedCookie.append(cookieName).append("=").append(cookieSplit[1].trim());
				}
			}
		}
		return escapedCookie.toString();
	}
	
	/** The string prefixing rewritten cookies. */
	public static String getCookieNamePrefix(String servletName, String name) {
		return "!Proxy!" + servletName;
	}

	/**
	 * Copy response body data (the entity) from the proxy to the servlet
	 * client.
	 */
	public static void copyResponseEntity(HttpResponse<Buffer> proxyResponse, HttpServerResponse serverResponse,
			HttpRequest<Buffer> proxyRequest, HttpServerRequest serverRequest) throws IOException {
		Buffer data = proxyResponse.body();
		if (data != null) 
			serverResponse.write(data);
	}

	/**
	 * For a redirect response from the target server, this translates
	 * {@code theUrl} to redirect to and translates it to one the original
	 * client can use.
	 */
	public static String rewriteUrlFromResponse(HttpServerRequest serverRequest, final String targetUri,
			String theUrl, boolean withRequestPathInfo, String urlPattern, String domain) {
		// TODO document example paths
		if (theUrl.startsWith(targetUri)) {
			/*-
			 * The URL points back to the back-end server.
			 * Instead of returning it verbatim we replace the target path with our
			 * source path in a way that should instruct the original client to
			 * request the URL pointed through this Proxy.
			 * We do this by taking the current request and rewriting the path part
			 * using this servlet's absolute path and the path from the returned URL
			 * after the base target URL.
			 */
			URIInfo serverRequestUriInfo = URIInfo.create(serverRequest.absoluteURI(), domain);
			StringBuffer curUrl = new StringBuffer();
			curUrl.append(serverRequestUriInfo.getRequestURL());
			int pos;
			// Skip the protocol part
			if ((pos = curUrl.indexOf("://")) >= 0) {
				// Skip the authority part
				// + 3 to skip the separator between protocol and authority
				if ((pos = curUrl.indexOf("/", pos + 3)) >= 0) {
					// Trim everything after the authority part.
					curUrl.setLength(pos);
				}
			}
			// Context path starts with a / if it is not blank
			curUrl.append(serverRequestUriInfo.getContextPath());
			// Servlet path starts with a / if it is not blank
			curUrl.append(serverRequestUriInfo.getProxyPath());
			// Added by David A. Bauer, handles urlPattern, if given
			if (serverRequestUriInfo.getPathInfo() != null && withRequestPathInfo)
				curUrl.append(urlPattern.replace("/*", ""));
			curUrl.append(theUrl, targetUri.length(), theUrl.length());
			theUrl = curUrl.toString();
		}
		return theUrl;
	}

	/**
	 * Encodes characters in the query or fragment part of the URI.
	 *
	 * <p>
	 * Unfortunately, an incoming URI sometimes has characters disallowed by the
	 * spec. HttpClient insists that the outgoing proxied request has a valid
	 * URI because it uses Java's {@link URI}. To be more forgiving, we must
	 * escape the problematic characters. See the URI class for the spec.
	 *
	 * @param in
	 *            example: name=value&amp;foo=bar#fragment
	 */
	public static CharSequence encodeUriQuery(CharSequence in, boolean encodePercent) {
		// Note that I can't simply use URI.java to encode because it will
		// escape pre-existing escaped things.
		StringBuilder outBuf = null;
		Formatter formatter = null;
		for (int i = 0; i < in.length(); i++) {
			char c = in.charAt(i);
			boolean escape = true;
			if (c < 128) {
				if (asciiQueryChars.get((int) c) && !(encodePercent && c == '%')) {
					escape = false;
				}
			} else if (!Character.isISOControl(c) && !Character.isSpaceChar(c)) {// not-ascii
				escape = false;
			}
			if (!escape) {
				if (outBuf != null)
					outBuf.append(c);
			} else {
				// escape
				if (outBuf == null) {
					outBuf = new StringBuilder(in.length() + 5 * 3);
					outBuf.append(in, 0, i);
					formatter = new Formatter(outBuf);
				}
				// leading %, 0 padded, width 2, capital hex
				formatter.format("%%%02X", (int) c);// TODO
			}
		}
		return outBuf != null ? outBuf : in;
	}

	public static final BitSet asciiQueryChars;
	static {
		char[] c_unreserved = "_-!.~'()*".toCharArray();// plus alphanum
		char[] c_punct = ",;:$&+=".toCharArray();
		char[] c_reserved = "?/[]@".toCharArray();// plus punct

		asciiQueryChars = new BitSet(128);
		for (char c = 'a'; c <= 'z'; c++)
			asciiQueryChars.set((int) c);
		for (char c = 'A'; c <= 'Z'; c++)
			asciiQueryChars.set((int) c);
		for (char c = '0'; c <= '9'; c++)
			asciiQueryChars.set((int) c);
		for (char c : c_unreserved)
			asciiQueryChars.set((int) c);
		for (char c : c_punct)
			asciiQueryChars.set((int) c);
		for (char c : c_reserved)
			asciiQueryChars.set((int) c);

		asciiQueryChars.set((int) '%');// leave existing percent escapes in
										// place
	}
}
