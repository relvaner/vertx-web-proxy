package vertx.web.proxy;

import static vertx.web.proxy.ProxyWebClientUtils.*;
import static vertx.web.proxy.ProxyLogger.*;

import java.net.HttpCookie;
import java.net.URI;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;

import org.apache.commons.lang3.mutable.MutableBoolean;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import vertx.web.proxy.utils.CircuitBreakerForWebClient;
import vertx.web.proxy.utils.URIInfo;

public class ProxyWebClient extends AbstractProxyWebClient {
	protected Function<String, Boolean> cookieFilterRequest;
	protected Function<HttpCookie, Boolean> cookieFilterResponse;
	
	protected Function<Entry<String, String>, Boolean> headerFilterResponse;
	
	protected MutableBoolean contentFilterEnabled;
	protected Function<Buffer, Buffer> contentFilter;
	
	protected String uploadsDirectory;
	
	public ProxyWebClient(WebClient proxyClient, ProxyWebClientOptions proxyWebClientOptions, CircuitBreakerForWebClient circuitBreakerForWebClient) {
		super(proxyClient, proxyWebClientOptions, circuitBreakerForWebClient);
		
		contentFilterEnabled = new MutableBoolean(false);
		uploadsDirectory = "file-uploads";
	}
	
	public ProxyWebClient setCookieFilterRequest(Function<String, Boolean> cookieFilterRequest) {
		this.cookieFilterRequest = cookieFilterRequest;
		
		return this;
	}

	public ProxyWebClient setCookieFilterResponse(Function<HttpCookie, Boolean> cookieFilterResponse) {
		this.cookieFilterResponse = cookieFilterResponse;
		
		return this;
	}
	
	public ProxyWebClient setHeaderFilterResponse(Function<Entry<String, String>, Boolean> headerFilterResponse) {
		this.headerFilterResponse = headerFilterResponse;
		
		return this;
	}
	
	public boolean getContentFilterEnabled() {
		return contentFilterEnabled.getValue();
	}

	public ProxyWebClient setContentFilterEnabled(boolean contentFilterEnabled) {
		this.contentFilterEnabled.setValue(contentFilterEnabled);
		
		return this;
	}

	public ProxyWebClient setContentFilter(Function<Buffer, Buffer> contentFilter) {
		this.contentFilter = contentFilter;
		
		return this;
	}
	
	public String getUploadsDirectory() {
		return uploadsDirectory;
	}

	public ProxyWebClient setUploadsDirectory(String uploadsDirectory) {
		this.uploadsDirectory = uploadsDirectory;
		
		return this;
	}

	public void execute(RoutingContext routingContext, String urlPattern, String targetUri) {
		execute(routingContext, urlPattern, (promise) -> 
			doExecute(routingContext, targetUri, URIInfo.create(targetUri, "").getUri(), serverRequestUriInfo.getPathInfo(), promise));
	}

	protected void doExecute(RoutingContext routingContext, String targetUri,
			URI targetObj, String pathInfo,  Promise<Object> promise) {
		Handler<AsyncResult<HttpResponse<Buffer>>> handler = asyncResult -> {
			try {
				if (asyncResult.succeeded()) {
					//routingContext.response().setChunked(true);
					
					// Process the response:
		
					// Pass the response code. This method with the "reason phrase" is
					// deprecated but it's the
					// only way to pass the reason along too.
					int statusCode = asyncResult.result().statusCode();
					// noinspection deprecation
					routingContext.response().setStatusCode(statusCode);
					routingContext.response().setStatusMessage(asyncResult.result().statusMessage());
		
					// Copying response headers to make sure SESSIONID or other Cookie
					// which comes from the remote
					// server will be saved in client when the proxied url was
					// redirected to another one.
					// See issue
					// [#51](https://github.com/mitre/HTTP-Proxy-Servlet/issues/51)
					copyResponseHeaders(asyncResult.result(), routingContext, targetUri, headerFilterResponse);
					
					if (statusCode == HttpResponseStatus.NOT_MODIFIED.code()) {
						// 304 needs special handling. See:
						// http://www.ics.uci.edu/pub/ietf/http/rfc1945.html#Code304
						// Don't send body entity/content!
						routingContext.response().headers().set(HttpHeaders.CONTENT_LENGTH, "0");
						
						if (proxyWebClientOptions.log)
							logger().info(routingContext.request().method() + " uri: " + routingContext.request().absoluteURI() + " <-- 304 Not Modified ");
					} else {
						// Send the content to the client
						// changed by David A. Bauer
						HttpResponse<Buffer> proxyResponse = asyncResult.result();
						if (proxyResponse.body()!=null) {
							if (!routingContext.response().closed() && !routingContext.response().ended() && routingContext.response().bytesWritten()==0) {
								Buffer buffer = proxyResponse.body().copy();
								
								if (contentFilterEnabled.isTrue())
									buffer = contentFilter.apply(buffer);
								
								routingContext.response().headers().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()));
								routingContext.response().write(buffer);
							}
						}
					}
					if (!routingContext.response().closed() && !routingContext.response().ended())
						routingContext.response().end();
					promise.complete();
				}
			}
			catch (Exception e) {
				e.printStackTrace();
				routingContext.fail(e);
			}
		};
		
		// Make the Request
		// note: we won't transfer the protocol version because I'm not sure it
		// would truly be compatible
		HttpMethod method = routingContext.request().method();
		String proxyRequestUri = rewriteUrlFromRequest(routingContext, targetUri, pathInfo);
		logger().debug("ProxyWebClient::Request: "+proxyRequestUri);
		
		if (targetObj!=null) 
			proxyWebClientOptions.ssl = targetObj.getScheme().equalsIgnoreCase("https");
		HttpRequest<Buffer> proxyRequest = proxyClient
				.requestAbs(method, proxyRequestUri)
				.ssl(proxyWebClientOptions.ssl);
				
		if (proxyWebClientOptions.log)
			logger().info(routingContext.request().method() + " uri: " + routingContext.request().absoluteURI() + " --> " + proxyRequestUri);

		boolean isMultipartForm = isMultipartForm(routingContext);
		
		if (isMultipartForm)
			copyRequestHeadersForMultipartForm(routingContext, proxyRequest, targetObj);
		else
			copyRequestHeaders(routingContext, proxyRequest, targetObj);
		
		setXForwardedForHeader(routingContext, proxyRequest);
		
		if (isMultipartForm)
			proxyRequest.sendMultipartForm(createMultipartForm(routingContext), handler);
		else {
			Buffer buffer = routingContext.getBody().copy();
			if (buffer!=null) {
				proxyRequest.headers().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()));
				proxyRequest.sendBuffer(buffer, handler);
			}
			else
				proxyRequest.send(handler);	
		}
	}
	
	protected boolean isMultipartForm(RoutingContext routingContext) {
		boolean result = false;
		
		String value = null;
		if ((value=routingContext.request().getHeader(HttpHeaders.CONTENT_TYPE))!=null)
			result = value.contains(HttpHeaderValues.MULTIPART_FORM_DATA.toString());
		
		return result;
	}
	
	protected MultipartForm createMultipartForm(RoutingContext routingContext) {
		MultipartForm result = MultipartForm.create();
		
		MultiMap formAttributes = routingContext.request().formAttributes();
		Iterator<Entry<String, String>> formAttributesIterator = formAttributes.iterator();
		while (formAttributesIterator.hasNext()) {
			Entry<String, String> entry = formAttributesIterator.next();
			result.attribute(entry.getKey(), entry.getValue());
		}

		Set<FileUpload> uploads = routingContext.fileUploads();
		Iterator<FileUpload> uploadsIterator =  uploads.iterator();
	  	while (uploadsIterator.hasNext()) {
	  		FileUpload uploadFile = uploadsIterator.next();
	  		String fileName = uploadFile.uploadedFileName().replace(uploadsDirectory+"\\", "").replace(uploadsDirectory+"/", "");
	  		result.binaryFileUpload(uploadFile.name(), uploadFile.fileName(), uploadsDirectory+"/"+fileName, uploadFile.contentType());
	  	}
	  	
	  	return result;
	}
	
	/**
	 * Reads the request URI from {@code servletRequest} and rewrites it,
	 * considering targetUri. It's used to make the new request.
	 */
	/**
	 * Reads the request URI from {@code servletRequest} and rewrites it,
	 * considering targetUri. It's used to make the new request.
	 */
	protected String rewriteUrlFromRequest(RoutingContext routingContext, String targetUri, String pathInfo) {
		StringBuilder uri = new StringBuilder(500);
		uri.append(targetUri);
		
		if (pathInfo != null)
			uri.append(encodeUriQuery(pathInfo, true));

		// Handle the query string & fragment
		String queryString = serverRequestUriInfo.getQueryString();// ex:(following
																// '?'):
																// name=value&foo=bar#fragment
		String fragment = null;
		// split off fragment from queryString, updating queryString if found
		if (queryString != null) {
			int fragIdx = queryString.indexOf('#');
			if (fragIdx >= 0) {
				fragment = queryString.substring(fragIdx + 1);
				queryString = queryString.substring(0, fragIdx);
			}
		}

		queryString = rewriteQueryStringFromRequest(routingContext.request(), queryString);
		if (queryString != null && queryString.length() > 0) {
			uri.append('?');
			uri.append(encodeUriQuery(queryString, false));
		}

		if (proxyWebClientOptions.sendUrlFragment && fragment != null) {
			uri.append('#');
			uri.append(encodeUriQuery(fragment, false));
		}
		
		return uri.toString();
	}
	
	protected String rewriteQueryStringFromRequest(HttpServerRequest serverRequest, String queryString) {
		return queryString;
	}
	
	// Get the header value as a long in order to more correctly proxy very
	// large requests
	protected long getContentLength(RoutingContext routingContext) {
		String contentLengthHeader = routingContext.request().headers().get(HttpHeaders.CONTENT_LENGTH);
		if (contentLengthHeader != null)
			return Long.parseLong(contentLengthHeader);
		
		return -1L;
	}
	
	/**
	 * Copy request headers from the servlet client to the proxy request. This
	 * is easily overridden to add your own.
	 */
	protected void copyRequestHeaders(RoutingContext routingContext, HttpRequest<Buffer> proxyRequest, URI targetObj) {
		Iterator<Entry<String, String>> headers = routingContext.request().headers().iterator();
		while (headers.hasNext()) {// sometimes more than one value
			Entry<String, String> header = headers.next();
			String headerName = header.getKey();
			String headerValue = header.getValue();
			
			// Instead the content-length is effectively set via InputStreamEntity, set in ProxyWebClient::doExecute
			if (headerName.equalsIgnoreCase("Content-Length"))
				continue;
			if (hopByHopHeaders.containsKey(headerName))
				continue;
			
			// In case the proxy host is running multiple virtual servers,
			// rewrite the Host header to ensure that we get content from
			// the correct virtual server
			 
			if (!proxyWebClientOptions.preserveHost && headerName.equalsIgnoreCase("Host")) {
				headerValue = targetObj.getHost();
				if (targetObj.getPort() != -1)
					headerValue += ":" + targetObj.getPort();
			} 
			else if (header.getKey().equalsIgnoreCase("Cookie"))
				headerValue = getRealCookie(headerValue, proxyWebClientOptions.preserveCookies, cookieFilterRequest);
			
			proxyRequest.headers().set(headerName, headerValue);
			logger().debug("ProxyWebClient::Request::Header: " + headerName + ":" + headerValue);
		}
	}
	
	protected void copyRequestHeadersForMultipartForm(RoutingContext routingContext, HttpRequest<Buffer> proxyRequest, URI targetObj) {
		Iterator<Entry<String, String>> headers = routingContext.request().headers().iterator();
		while (headers.hasNext()) {// sometimes more than one value
			Entry<String, String> header = headers.next();
			String headerName = header.getKey();
			String headerValue = header.getValue();
			 
			if (!proxyWebClientOptions.preserveHost && headerName.equalsIgnoreCase("Host")) {
				headerValue = targetObj.getHost();
				if (targetObj.getPort() != -1)
					headerValue += ":" + targetObj.getPort();
				
				proxyRequest.headers().set(headerName, headerValue);
				logger().debug("ProxyWebClient::Request::Header: " + headerName + ":" + headerValue);
			} 
			else if (header.getKey().equalsIgnoreCase("Cookie")) {
				headerValue = getRealCookie(headerValue, proxyWebClientOptions.preserveCookies, cookieFilterRequest);
				
				proxyRequest.headers().set(headerName, headerValue);
				logger().debug("ProxyWebClient::Request::Header: " + headerName + ":" + headerValue);
			}
			else if (header.getKey().equalsIgnoreCase("Authorization")) {
				proxyRequest.headers().set(headerName, headerValue);
				logger().debug("ProxyWebClient::Request::Header: " + headerName + ":" + headerValue);
			}
		}
	}
	
	protected void setXForwardedForHeader(RoutingContext routingContext, HttpRequest<Buffer> proxyRequest) {
		if (proxyWebClientOptions.forwardIP) {
			String forHeaderName = "X-Forwarded-For";
			String forHeader = routingContext.request().remoteAddress().host();
			String existingForHeader = routingContext.request().headers().get(forHeaderName);
			if (existingForHeader != null)
				forHeader = existingForHeader + ", " + forHeader;

			proxyRequest.headers().set(forHeaderName, forHeader);
			logger().debug("ProxyWebClient::Request::Header: " + forHeaderName + ":" + forHeader);

			String protoHeaderName = "X-Forwarded-Proto";
			String protoHeader = serverRequestUriInfo.getScheme();
			proxyRequest.headers().set(protoHeaderName, protoHeader);
			logger().debug("ProxyWebClient::Request::Header: " + protoHeaderName + ":" + protoHeader);
		}
	}
	
	/** Copy proxied response headers back to the servlet client. */
	protected void copyResponseHeaders(HttpResponse<Buffer> proxyResponse, RoutingContext routingContext,
			String targetUri, Function<Entry<String, String>, Boolean> filter) {
		Iterator<Entry<String, String>> headers = proxyResponse.headers().iterator();
		while (headers.hasNext()) {
			Entry<String, String> header = headers.next();
			if (filter != null) {
				if (!filter.apply(header))
					copyResponseHeader(proxyResponse, routingContext, targetUri, header);
			} else
				copyResponseHeader(proxyResponse, routingContext, targetUri, header);
		}
	}

	/**
	 * Copy a proxied response header back to the servlet client. This is easily
	 * overwritten to filter out certain headers if desired.
	 */
	protected void copyResponseHeader(HttpResponse<Buffer> proxyResponse, RoutingContext routingContext, String targetUri, Entry<String, String> header) {
		String headerName = header.getKey();
		if (hopByHopHeaders.containsKey(headerName))
			return;
		String headerValue = header.getValue();
		if (headerName.equalsIgnoreCase("Set-Cookie") || headerName.equalsIgnoreCase("Set-Cookie2"))
			copyProxyCookie(routingContext, proxyResponse.cookies().toString().substring(1, proxyResponse.cookies().toString().length()-1)/*headerValue*/); // not so nice, some parts where missing!!!
		else if (headerName.equalsIgnoreCase("Location"))
			// LOCATION Header may have to be rewritten.
			routingContext.response().headers().add(headerName, rewriteUrlFromResponse(routingContext.request(), targetUri, headerValue));
		else
			routingContext.response().headers().add(headerName, headerValue);

		logger().debug("ProxyWebClient::Response::Header: " + header.getKey() + ":" + header.getValue());
	}
	
	protected void copyProxyCookie(RoutingContext routingContext, String headerValue) {
		List<HttpCookie> cookies = HttpCookie.parse(headerValue);
		String path = "";
		if (!proxyWebClientOptions.preserveCookiesContextPath)
			path = serverRequestUriInfo.getContextPath(); // path starts with / or
														// is empty string
		if (!proxyWebClientOptions.preserveCookiesProxyPath)
			path += serverRequestUriInfo.getProxyPath(); // servlet path starts with
														// /
														// or is empty string
		if (path.isEmpty())
			path = "/";

		for (HttpCookie cookie : cookies) {
			if (cookieFilterResponse != null && cookieFilterResponse.apply(cookie))
				continue;

			// set cookie name prefixed w/ a proxy value so it won't collide w/
			// other cookies
			String proxyCookieName = proxyWebClientOptions.preserveCookies ? cookie.getName()
					: getCookieNamePrefix() + cookie.getName();
			Cookie serverCookie = Cookie.cookie(proxyCookieName, cookie.getValue());
			//serverCookie.setComment(cookie.getComment()); not possible
			serverCookie.setMaxAge((int) cookie.getMaxAge());
			serverCookie.setPath(path); // set to the path of the proxy servlet
			// don't set cookie domain
			serverCookie.setSecure(cookie.getSecure());
			//serverCookie.setVersion(cookie.getVersion()); not possible
			routingContext.addCookie(serverCookie);
		}
	}
	
	/**
	 * Take any client cookies that were originally from the proxy and prepare
	 * them to send to the proxy. This relies on cookie headers being set
	 * correctly according to RFC 6265 Sec 5.4. This also blocks any local
	 * cookies from being sent to the proxy.
	 */
	public String getRealCookie(String cookieValue, boolean doPreserveCookie, Function<String, Boolean> filter) {
		StringBuilder escapedCookie = new StringBuilder();
		String cookies[] = cookieValue.split("[;,]");
		for (String cookie : cookies) {
			String cookieSplit[] = cookie.split("=");
			if (cookieSplit.length == 2) {
				String cookieName = cookieSplit[0].trim();
				if (filter!=null && filter.apply(cookieName))
					continue;
				
				if (!doPreserveCookie) {
					if (cookieName.startsWith(getCookieNamePrefix())) {
						cookieName = cookieName.substring(getCookieNamePrefix().length());
						if (escapedCookie.length() > 0)
							escapedCookie.append("; ");

						escapedCookie.append(cookieName).append("=").append(cookieSplit[1].trim()); 
					}
				}
				else {
					if (escapedCookie.length() > 0)
						escapedCookie.append("; ");

					escapedCookie.append(cookieName).append("=").append(cookieSplit[1].trim());
				}
			}
		}
		return escapedCookie.toString();
	}
	
	/** The string prefixing rewritten cookies. */
	public String getCookieNamePrefix() {
		return "!Proxy!";
	}
	
	/**
	 * For a redirect response from the target server, this translates
	 * {@code theUrl} to redirect to and translates it to one the original
	 * client can use.
	 */
	public String rewriteUrlFromResponse(HttpServerRequest serverRequest, final String targetUri, String theUrl) {
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
			StringBuffer curUrl = new StringBuffer();
			curUrl.append(serverRequestUriInfo.getRequestURL());
			int pos;
			// Skip the protocol part
			if ((pos = curUrl.indexOf("://")) >= 0) {
				// Skip the authority part
				// + 3 to skip the separator between protocol and authority
				if ((pos = curUrl.indexOf("/", pos + 3)) >= 0)
					// Trim everything after the authority part.
					curUrl.setLength(pos);
			}
			// Context path starts with a / if it is not blank
			curUrl.append(serverRequestUriInfo.getContextPath());
			// Servlet path starts with a / if it is not blank
			curUrl.append(serverRequestUriInfo.getProxyPath());
			
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
	public CharSequence encodeUriQuery(CharSequence in, boolean encodePercent) {
		// Note that I can't simply use URI.java to encode because it will
		// escape pre-existing escaped things.
		StringBuilder outBuf = null;
		Formatter formatter = null;
		for (int i = 0; i < in.length(); i++) {
			char c = in.charAt(i);
			boolean escape = true;
			if (c < 128) {
				if (asciiQueryChars.get((int) c) && !(encodePercent && c == '%'))
					escape = false;
			} else if (!Character.isISOControl(c) && !Character.isSpaceChar(c)) // not-ascii
				escape = false;
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
}
