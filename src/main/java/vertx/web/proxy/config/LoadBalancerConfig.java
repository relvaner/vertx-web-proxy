package vertx.web.proxy.config;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.commons.lang3.tuple.Pair;
import io.vertx.ext.web.Router;
import vertx.web.proxy.ProxyWebClient;

public class LoadBalancerConfig extends AbstractConfig {
	protected List<String> targetUris; // "/*" -> "https://host:port, https://host:port..."
		
	public LoadBalancerConfig(ProxyWebClient proxyWebClient, List<String> targetUris) {
		super(proxyWebClient);
		this.targetUris = targetUris;
	}
	
	public void config(Router router) {
		config(router, "/*");
	}
	
	public void config(Router router, String path) {
		config(router, path, (routingContext) -> Pair.of(path, targetUris.get(ThreadLocalRandom.current().nextInt(targetUris.size()))));
	}
}
