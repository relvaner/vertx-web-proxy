package vertx.web.proxy.config;

import java.io.UnsupportedEncodingException;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import vertx.web.proxy.ProxyWebClient;

public class Config {
	protected ProxyWebClient proxyWebClient;
	protected String unavailable_html = "<p style=\"color:red;\">Whoops, seems like the service is temporary unavailable! Your Proxy-Vert.x Team</p>";
	
	public Config(ProxyWebClient proxyWebClient) {
		super();
		this.proxyWebClient = proxyWebClient;
	}

	public void config(Router router, String path, Function<RoutingContext, Pair<String, String>> function) {
		router.route(path).handler(routingContext -> {
			Pair<String, String> pair = function.apply(routingContext);
			if (pair!=null) {
				String urlPattern = pair.getLeft();
				String targetUri  = pair.getRight();
				if (targetUri==null)
					routingContext.fail(404);
				else
					try {	
						proxyWebClient.execute(routingContext, urlPattern, targetUri);
					}
					catch (Exception e) {
						e.printStackTrace();
						routingContext.fail(e);
					}
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
