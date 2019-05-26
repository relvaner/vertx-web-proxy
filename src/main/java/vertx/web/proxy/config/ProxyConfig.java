package vertx.web.proxy.config;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import vertx.web.proxy.ProxyWebClient;

public class ProxyConfig {
	protected ProxyWebClient proxyWebClient;
	protected Map<String, String> targetUris; // urlPattern -> targetUri; e.g., "/domain" or "/" -> "https://host:port"
	protected String unavailable_html = "<p style=\"color:red;\">Whoops, seems like the service is temporary unavailable!</p>";
		
	public ProxyConfig(ProxyWebClient proxyWebClient, Map<String, String> targetUris) {
		super();
		this.proxyWebClient = proxyWebClient;
		this.targetUris = targetUris;
	}
	
	protected String matchesUrlPattern(RoutingContext routingContext) {
		String result = "";
		
		try {
			URI uri = new URI(routingContext.request().absoluteURI());
			String path = uri.getPath();
			System.out.println(path);
			if (path.isEmpty())
				path = "/";
			
			Iterator<Entry<String, String>> iterator = targetUris.entrySet().iterator();
			while (iterator.hasNext()) {
				Entry<String, String> entry = iterator.next();
				
				if (entry.getKey().endsWith("/*")) {
					String key = entry.getKey().replace("/*", "");
					if (key.isEmpty())
						key = "/";
					if (path.startsWith(key)) {
						result =  entry.getKey();
						if (!result.equals("/"))
							break;
					}
				}
				else if (path.equals(entry.getKey())) {
					result = entry.getKey();
					break;
				}
			}
			
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		
		return result;
	}

	public void config(Router router) {
		router.route("/*").handler(routingContext -> {
			String urlPattern = matchesUrlPattern(routingContext);
			String targetUri = targetUris.get(urlPattern);
			System.out.println(targetUri);
			if (targetUri==null)
				routingContext.fail(404);
				
			try {	
				proxyWebClient.execute(routingContext, urlPattern, targetUri);
			}
			catch (Exception e) {
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
				routingContext.response().headers().set("Content-Length", String.valueOf(unavailable_html.getBytes().length));
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
