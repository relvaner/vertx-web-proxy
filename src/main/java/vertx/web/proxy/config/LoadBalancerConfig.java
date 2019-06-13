package vertx.web.proxy.config;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import vertx.web.proxy.ProxyWebClient;

public class LoadBalancerConfig {
	protected ProxyWebClient proxyWebClient;
	protected List<String> targetUris; // "/*" -> "https://host:port, https://host:port..."
	protected String unavailable_html = "<p style=\"color:red;\">Whoops, seems like the service is temporary unavailable! Your Proxy-Vert.x Team</p>";
		
	public LoadBalancerConfig(ProxyWebClient proxyWebClient, List<String> targetUris) {
		super();
		this.proxyWebClient = proxyWebClient;
		this.targetUris = targetUris;
	}
	
	public void config(Router router) {
		router.route("/*").handler(routingContext -> {
			String targetUri = targetUris.get(ThreadLocalRandom.current().nextInt(targetUris.size()));
			if (targetUri==null)
				routingContext.fail(404);
			else
				try {	
					proxyWebClient.execute(routingContext, "/*", targetUri);
				}
				catch (Exception e) {
					e.printStackTrace();
					routingContext.fail(e);
				}
		})
		.failureHandler(routingContext -> {
			if (routingContext.statusCode()==404) {
				routingContext.response().setStatusCode(404);
				routingContext.response().end();
			}
			else if (routingContext.statusCode()==503) {
				routingContext.response().setStatusCode(503);
				routingContext.response().headers().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(unavailable_html.getBytes().length));
				try {
					routingContext.response().end(Buffer.buffer(unavailable_html.getBytes("UTF-8")));
				} catch (UnsupportedEncodingException e) {
					routingContext.response().end();
					e.printStackTrace();
				}
			}
		});
	}
}
