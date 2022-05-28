package vertx.web.proxy;

import static vertx.web.proxy.ProxyLogger.logger;

import java.util.function.Consumer;

import io.vertx.core.Promise;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import vertx.web.proxy.utils.CircuitBreakerForWebClient;
import vertx.web.proxy.utils.URIInfo;

public class AbstractProxyWebClient {
	protected WebClient proxyClient;
	protected ProxyWebClientOptions proxyWebClientOptions;
	
	protected CircuitBreakerForWebClient circuitBreakerForWebClient;
	
	protected URIInfo serverRequestUriInfo;
	
	public AbstractProxyWebClient(WebClient proxyClient, ProxyWebClientOptions proxyWebClientOptions, CircuitBreakerForWebClient circuitBreakerForWebClient) {
		super();
		this.proxyClient = proxyClient;
		this.proxyWebClientOptions = proxyWebClientOptions;
		this.circuitBreakerForWebClient = circuitBreakerForWebClient;
	}
	
	public void setProxyClient(WebClient proxyClient) {
		this.proxyClient = proxyClient;
	}

	public WebClient getProxyClient() {
		return proxyClient;
	}
	
	public URIInfo getServerRequestUriInfo() {
		return serverRequestUriInfo;
	}

	public void execute(RoutingContext routingContext, String urlPattern, Consumer<Promise<Object>> consumer) {
		String domain = urlPattern.replace("/*", "");
		if (domain.isEmpty())
			domain = "/";
		serverRequestUriInfo = URIInfo.create(routingContext.request().absoluteURI(), domain);
		
		if (proxyWebClientOptions.circuitBreakerUseAbsoluteURI)
			urlPattern = routingContext.request().absoluteURI();
		circuitBreakerForWebClient.get(urlPattern)
			.execute((promise) -> {
				try {
					consumer.accept(promise);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			})
			.onComplete(asyncResult -> {
				if (asyncResult.failed()) {
					logger().error(String.format("WebClient failed: %s%n", asyncResult.cause()));
					routingContext.fail(503);
				}
			});
	}
}
