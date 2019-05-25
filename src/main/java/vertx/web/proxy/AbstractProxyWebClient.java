package vertx.web.proxy;

import static vertx.web.proxy.ProxyLogger.logger;

import java.net.URI;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;

public class AbstractProxyWebClient {
	protected WebClient proxyClient;
	protected ProxyWebClientOptions proxyWebClientOptions;
	protected String domain;
	
	protected URIInfo serverRequestUriInfo;
	
	public AbstractProxyWebClient(WebClient proxyClient, ProxyWebClientOptions proxyWebClientOptions, String domain) {
		super();
		this.proxyClient = proxyClient;
		this.proxyWebClientOptions = proxyWebClientOptions;
		this.domain = domain;
	}
	
	public void setProxyClient(WebClient proxyClient) {
		this.proxyClient = proxyClient;
	}

	public WebClient getProxyClient() {
		return proxyClient;
	}
	
	protected void execute(RoutingContext routingContext, URI targetObj) {
		if (proxyWebClientOptions.doLog)
			logger().info(routingContext.request().method() + " uri: " + routingContext.request().absoluteURI());// + " -- "
			//		+ proxyRequest.getRequestLine().getUri());

		//return proxyClient.execute(URIUtils.extractHost(targetObj), proxyRequest);
	}
}
