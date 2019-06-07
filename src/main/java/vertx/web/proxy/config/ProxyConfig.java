package vertx.web.proxy.config;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import vertx.web.proxy.ProxyWebClient;

public class ProxyConfig {
	protected ProxyWebClient proxyWebClient;
	protected Map<String, String> targetUris; // urlPattern -> targetUri; e.g., "/domain" or "/" -> "https://host:port"
	protected String unavailable_html = "<p style=\"color:red;\">Whoops, seems like the service is temporary unavailable! Your Proxy-Vert.x Team</p>";
		
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
			if (path.isEmpty())
				path = "/";
			
			Iterator<Entry<String, String>> iterator = targetUris.entrySet().iterator();
			// search for absolute match
			TreeMap<Integer, String> relatives = new TreeMap<>(Collections.reverseOrder());
			while (iterator.hasNext()) {
				Entry<String, String> entry = iterator.next();
				
				if (path.equals(entry.getKey())) {
					result = entry.getKey();
					break;
				}
				else if (entry.getKey().endsWith("/*")) {
					String key = entry.getKey().replace("/*", "");
					if (key.isEmpty())
						key = "/";
					if (path.startsWith(key))
						relatives.put(key.length(),  entry.getKey());
				}
					
			}

			// search for relative match
			if (result.isEmpty() && relatives.firstEntry()!=null)
				result = relatives.firstEntry().getValue();
			
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		
		return result;
	}

	public void config(Router router) {
		router.route("/*").handler(routingContext -> {
			String urlPattern = matchesUrlPattern(routingContext);
			String targetUri = targetUris.get(urlPattern);
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
