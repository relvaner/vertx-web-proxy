package vertx.web.proxy;

import static vertx.web.proxy.ProxyWebClientUtils.*;
import static vertx.web.proxy.ProxyLogger.*;

import java.net.HttpCookie;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;

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
	
	public void execute(RoutingContext routingContext, String urlPattern, String targetUri) {
		execute(routingContext, urlPattern, (future) -> 
			doExecute(routingContext, targetUri, URIInfo.create(targetUri, "").getUri(), serverRequestUriInfo.getPathInfo(), null, urlPattern, future));
	}

	protected void doExecute(RoutingContext routingContext, String targetUri,
			URI targetObj, String pathInfo, final Function<Entry<String, String>, Boolean> filter,
			String urlPattern,  Future<Object> future) {
		Handler<AsyncResult<HttpResponse<Buffer>>> handler = asyncResult -> {
			try {
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
					Function<Entry<String, String>, Boolean> filterInternal = new Function<Entry<String, String>, Boolean>() {
						@Override
						public Boolean apply(Entry<String, String> header) {
							boolean result = false;
		
							if (filter!=null)
								result = filter.apply(header);
							
							return result;
						}
					};
					
					copyResponseHeaders(asyncResult.result(), routingContext, targetUri, filterInternal, urlPattern);
					
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
							if (!routingContext.response().closed() && !routingContext.response().ended() && routingContext.response().bytesWritten()==0) {
								Buffer buffer = proxyResponse.body().copy();
								routingContext.response().headers().set("Content-Length", String.valueOf(buffer.length()));
								routingContext.response().write(buffer);
							}
						}
					}
					if (!routingContext.response().closed() && !routingContext.response().ended())
						routingContext.response().end();
					future.complete();
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
		String proxyRequestUri = rewriteUrlFromRequest(routingContext, targetUri, pathInfo, urlPattern);
		logger().debug("ProxyWebClient::Request: "+proxyRequestUri);
		
		HttpRequest<Buffer> proxyRequest = proxyClient
				.requestAbs(method, proxyRequestUri)
				.ssl(proxyWebClientOptions.ssl);
				
		if (proxyWebClientOptions.log)
			logger().info(routingContext.request().method() + " uri: " + routingContext.request().absoluteURI() + " --> " + proxyRequestUri);
		
		copyRequestHeaders(routingContext, proxyRequest, targetObj);

		setXForwardedForHeader(routingContext, proxyRequest);
		
		Buffer buffer = routingContext.getBody().copy();
		if (buffer!=null) {
			proxyRequest.headers().set("Content-Length", String.valueOf(buffer.length()));
			proxyRequest.sendBuffer(buffer, handler);
		}
		else
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
	protected String rewriteUrlFromRequest(RoutingContext routingContext, String targetUri, String pathInfo, String urlPattern) {
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
		String contentLengthHeader = routingContext.request().headers().get("Content-Length");
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
			String targetUri, Function<Entry<String, String>, Boolean> filter, String urlPattern) {
		Iterator<Entry<String, String>> headers = proxyResponse.headers().iterator();
		while (headers.hasNext()) {
			Entry<String, String> header = headers.next();
			if (filter != null) {
				if (!filter.apply(header))
					copyResponseHeader(proxyResponse, routingContext, targetUri, header, urlPattern);
			} else
				copyResponseHeader(proxyResponse, routingContext, targetUri, header, urlPattern);
		}
	}

	/**
	 * Copy a proxied response header back to the servlet client. This is easily
	 * overwritten to filter out certain headers if desired.
	 */
	protected void copyResponseHeader(HttpResponse<Buffer> proxyResponse, RoutingContext routingContext, String targetUri, Entry<String, String> header, String urlPattern) {
		String headerName = header.getKey();
		if (hopByHopHeaders.containsKey(headerName))
			return;
		String headerValue = header.getValue();
		if (headerName.equalsIgnoreCase("Set-Cookie") || headerName.equalsIgnoreCase("Set-Cookie2"))
			copyProxyCookie(routingContext, proxyResponse.cookies().toString().substring(1, proxyResponse.cookies().toString().length()-1)/*headerValue*/); // not so nice, some parts where missing!!!
		else if (headerName.equalsIgnoreCase("Location"))
			// LOCATION Header may have to be rewritten.
			routingContext.response().headers().add(headerName, rewriteUrlFromResponse(routingContext.request(), targetUri, headerValue, urlPattern));
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
}
