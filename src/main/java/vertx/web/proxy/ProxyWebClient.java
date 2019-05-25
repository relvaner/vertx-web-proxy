package vertx.web.proxy;

import static vertx.web.proxy.ProxyWebClientUtils.*;
import static vertx.web.proxy.ProxyLogger.*;

import java.net.HttpCookie;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;

import org.apache.commons.lang3.mutable.MutableBoolean;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import vertx.web.porxy.utils.CircuitBreakerForWebClient;
import vertx.web.porxy.utils.URIInfo;

public class ProxyWebClient extends AbstractProxyWebClient {
	public static final int SC_NOT_MODIFIED = 304;
	
	protected Function<String, Boolean> cookieFilterRequest;
	protected Function<HttpCookie, Boolean> cookieFilterResponse;
	
	public ProxyWebClient(WebClient proxyClient, ProxyWebClientOptions proxyWebClientOptions, CircuitBreakerForWebClient circuitBreakerForWebClient) {
		super(proxyClient, proxyWebClientOptions, circuitBreakerForWebClient);
	}
	
	public void setCookieFilterRequest(Function<String, Boolean> cookieFilterRequest) {
		this.cookieFilterRequest = cookieFilterRequest;
	}

	public void setCookieFilterResponse(Function<HttpCookie, Boolean> cookieFilterResponse) {
		this.cookieFilterResponse = cookieFilterResponse;
	}
	
	public void execute(RoutingContext routingContext, String domain, String targetUri) {
		execute(routingContext, domain, targetUri, (future) -> 
			doExecute(routingContext, domain, targetUri, URIInfo.create(targetUri, "").getUri(), serverRequestUriInfo.getPathInfo(), new MutableBoolean(true), null, false, null, null, future));
	}

	protected void doExecute(RoutingContext routingContext, String domain, String targetUri,
			URI targetObj, String pathInfo, final MutableBoolean resource, final Function<Entry<String, String>, Boolean> filter,
			boolean withRequestPathInfo, String urlPattern, Function<byte[], byte[]> contentFilter, Future<Object> future) {
		Handler<AsyncResult<HttpResponse<Buffer>>> handler = asyncResult -> {
			byte[] result = null;
			if (asyncResult.succeeded()) {
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
				final boolean enabled = !resource.getValue();
				final MutableBoolean contentDisposition = new MutableBoolean(false);
				final MutableBoolean contentTypeHTML = new MutableBoolean(false);
				Function<Entry<String, String>, Boolean> filterInternal = new Function<Entry<String, String>, Boolean>() {
					@Override
					public Boolean apply(Entry<String, String> header) {
						boolean result = false;
	
						if (enabled) {
							if (header.getKey().equalsIgnoreCase("Content-Disposition"))
								contentDisposition.setTrue();
							else if (header.getKey().equalsIgnoreCase("Content-Type")) {
								if (header.getValue().contains("text/html"))
									contentTypeHTML.setTrue();
							}
						}
	
						if (filter!=null)
							result = filter.apply(header);
						
						return result;
					}
				};
				
				copyResponseHeaders(asyncResult.result(), routingContext, domain, targetUri, filterInternal, withRequestPathInfo, urlPattern);
				
				if (enabled)
					resource.setValue(!(contentDisposition.isFalse() && contentTypeHTML.isTrue()));
				
				if (statusCode == SC_NOT_MODIFIED) {
					// 304 needs special handling. See:
					// http://www.ics.uci.edu/pub/ietf/http/rfc1945.html#Code304
					// Don't send body entity/content!
					routingContext.response().headers().set("Content-Length", "0");
				} else {
					// Send the content to the client
					// changed by David A. Bauer
					HttpResponse<Buffer> proxyResponse = asyncResult.result();
					if (proxyResponse.body()!=null) {
						Buffer buffer = proxyResponse.body().copy();
						result = buffer.getBytes();
						if (resource.getValue()) {
							if (contentFilter!=null && contentTypeHTML.isTrue()) {
								byte[] content = contentFilter.apply(result);
								routingContext.response().headers().set("Content-Length", String.valueOf(content.length));
								routingContext.response().write(Buffer.buffer(content));
							}
							else
								routingContext.response().write(buffer);
						}
					}
				}
				routingContext.response().end();
				future.complete();
			
				// do something with "result"!?
			}
		};
		
		// Make the Request
		// note: we won't transfer the protocol version because I'm not sure it
		// would truly be compatible
		HttpMethod method = routingContext.request().method();
		String proxyRequestUri = rewriteUrlFromRequest(routingContext, targetUri, pathInfo, withRequestPathInfo,
				urlPattern);
		logger().debug("ProxyWebClient::Request: "+proxyRequestUri);
		
		HttpRequest<Buffer> proxyRequest = proxyClient
				.request(method, targetObj.getHost(), proxyRequestUri)
				.ssl(proxyWebClientOptions.doSSL);
				
		if (proxyWebClientOptions.doLog)
			logger().info(routingContext.request().method() + " uri: " + routingContext.request().absoluteURI() + " -- " + proxyRequestUri);
		
		copyRequestHeaders(routingContext, proxyRequest, domain, targetObj);

		setXForwardedForHeader(routingContext, proxyRequest);
		
		proxyRequest.send(handler);		
	}
	
	/**
	 * Reads the request URI from {@code servletRequest} and rewrites it,
	 * considering targetUri. It's used to make the new request.
	 */
	/**
	 * Reads the request URI from {@code servletRequest} and rewrites it,
	 * considering targetUri. It's used to make the new request.
	 */
	protected String rewriteUrlFromRequest(RoutingContext routingContext, String targetUri, String pathInfo,
			boolean withRequestPathInfo, String urlPattern) {
		StringBuilder uri = new StringBuilder(500);
		uri.append(targetUri);
		// Handle the path given to the servlet
		// changed by David A. Bauer
		if (pathInfo != null)
			uri.append(encodeUriQuery(pathInfo, true));
		else if (serverRequestUriInfo.getPathInfo() != null && withRequestPathInfo) {
			urlPattern = urlPattern.replace("/*", "");
			uri.append(encodeUriQuery(serverRequestUriInfo.getPathInfo().substring(urlPattern.length(), serverRequestUriInfo.getPathInfo().length()), true));
		}

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

		if (proxyWebClientOptions.doSendUrlFragment && fragment != null) {
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
		String contentLengthHeader = routingContext.request().headers().get("Content-Length");
		if (contentLengthHeader != null) {
			return Long.parseLong(contentLengthHeader);
		}
		return -1L;
	}
	
	/**
	 * Copy request headers from the servlet client to the proxy request. This
	 * is easily overridden to add your own.
	 */
	protected void copyRequestHeaders(RoutingContext routingContext, HttpRequest<Buffer> proxyRequest, String domain, URI targetObj) {
		Iterator<Entry<String, String>> headers = routingContext.request().headers().iterator();
		while (headers.hasNext()) {// sometimes more than one value
			Entry<String, String> header = headers.next();
			String headerName = header.getKey();
			String headerValue = header.getValue();
			
			// Instead the content-length is effectively set via InputStreamEntity
			if (headerName.equalsIgnoreCase("Content-Length"))
				return;
			if (hopByHopHeaders.containsKey(headerName))
				return;
			
			
			logger().debug("ProxyWebClient::Request: " + headerName + ":" + headerValue); // debug
			// In case the proxy host is running multiple virtual servers,
			// rewrite the Host header to ensure that we get content from
			// the correct virtual server
			 
			if (!proxyWebClientOptions.doPreserveHost && headerName.equalsIgnoreCase("Host")) {
				headerValue = targetObj.getHost();
				if (targetObj.getPort() != -1)
					headerValue += ":" + targetObj.getPort();

			} else if (header.getKey().equalsIgnoreCase("Cookie")) {
				headerValue = getRealCookie(domain, headerValue, proxyWebClientOptions.doPreserveCookies, cookieFilterRequest);
			}
			proxyRequest.headers().set(headerName, headerValue);
		}
	}
	
	protected void setXForwardedForHeader(RoutingContext routingContext, HttpRequest<Buffer> proxyRequest) {
		if (proxyWebClientOptions.doForwardIP) {
			String forHeaderName = "X-Forwarded-For";
			String forHeader = routingContext.request().remoteAddress().host();
			String existingForHeader = routingContext.request().headers().get(forHeaderName);
			if (existingForHeader != null) {
				forHeader = existingForHeader + ", " + forHeader;
			}
			proxyRequest.headers().set(forHeaderName, forHeader);

			String protoHeaderName = "X-Forwarded-Proto";
			String protoHeader = serverRequestUriInfo.getScheme();
			proxyRequest.headers().set(protoHeaderName, protoHeader);
		}
	}
	
	/** Copy proxied response headers back to the servlet client. */
	protected void copyResponseHeaders(HttpResponse<Buffer> proxyResponse, RoutingContext routingContext, String domain,
			String targetUri, Function<Entry<String, String>, Boolean> filter, boolean withRequestPathInfo, String urlPattern) {
		Iterator<Entry<String, String>> headers = proxyResponse.headers().iterator();
		while (headers.hasNext()) {
			Entry<String, String> header = headers.next();
			if (filter != null) {
				if (!filter.apply(header))
					copyResponseHeader(routingContext, domain, targetUri, header, withRequestPathInfo, urlPattern);
			} else
				copyResponseHeader(routingContext, domain, targetUri, header, withRequestPathInfo, urlPattern);
		}
	}

	/**
	 * Copy a proxied response header back to the servlet client. This is easily
	 * overwritten to filter out certain headers if desired.
	 */
	protected void copyResponseHeader(RoutingContext routingContext, String domain, String targetUri, Entry<String, String> header, boolean withRequestPathInfo, String urlPattern) {
		logger().debug("ProxyWebClient::Response: " + header.getKey() + ":" + header.getValue());
		String headerName = header.getKey();
		if (hopByHopHeaders.containsKey(headerName))
			return;
		String headerValue = header.getValue();
		if (headerName.equalsIgnoreCase("Set-Cookie")
				|| headerName.equalsIgnoreCase("Set-Cookie2")) {
			copyProxyCookie(routingContext, domain, headerValue);
		} else if (headerName.equalsIgnoreCase("Location")) {
			// LOCATION Header may have to be rewritten.
			routingContext.response().headers().add(headerName, rewriteUrlFromResponse(routingContext.request(), targetUri, headerValue, withRequestPathInfo, urlPattern, domain));
		} else {
			routingContext.response().headers().add(headerName, headerValue);
		}
	}
	
	/**
	 * Copy cookie from the proxy to the servlet client. Replaces cookie path to
	 * local path and renames cookie to avoid collisions.
	 */
	protected void copyProxyCookie(RoutingContext routingContext, String domain, String headerValue) {
		List<HttpCookie> cookies = HttpCookie.parse(headerValue);
		String path = "";
		if (!proxyWebClientOptions.doPreserveCookiesContextPath)
			path = serverRequestUriInfo.getContextPath(); // path starts with / or
														// is empty string
		if (!proxyWebClientOptions.doPreserveCookiesProxyPath)
			path += serverRequestUriInfo.getProxyPath(); // servlet path starts with
														// /
														// or is empty string
		if (path.isEmpty()) {
			path = "/";
		}

		for (HttpCookie cookie : cookies) {
			if (cookieFilterResponse != null && cookieFilterResponse.apply(cookie))
				continue;

			// set cookie name prefixed w/ a proxy value so it won't collide w/
			// other cookies
			String proxyCookieName = proxyWebClientOptions.doPreserveCookies ? cookie.getName()
					: getCookieNamePrefix(domain, cookie.getName()) + cookie.getName();
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
}
