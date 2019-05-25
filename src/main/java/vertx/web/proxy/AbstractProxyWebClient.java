package vertx.web.proxy;

import java.util.function.Consumer;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import vertx.web.porxy.utils.CircuitBreakerForWebClient;
import vertx.web.porxy.utils.URIInfo;

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
	
	public void execute(RoutingContext routingContext, String domain, String targetUri, Consumer<Future<Object>> consumer) {
		serverRequestUriInfo = URIInfo.create(routingContext.request().absoluteURI(), domain);
		
		circuitBreakerForWebClient.get(domain)
			.execute((future) -> {
				consumer.accept(future);
			})
			.setHandler(asyncResult -> {
				if (asyncResult.failed()) {
					System.out.printf("WebClient failed: %s%n", asyncResult.cause());
					routingContext.fail(503);
				}
			});
	}
}
