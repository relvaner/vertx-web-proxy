package vertx.web.proxy.config;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Pair;

import io.vertx.ext.web.Router;
import vertx.web.proxy.ProxyWebClient;

public class LoadBalancerConfig extends ProxyConfig {
	protected List<String> targetUris; // "/*" -> "https://host:port, https://host:port..."
	protected LoadBalancerMode mode;
	protected static AtomicInteger globalIndex;
		
	public LoadBalancerConfig(ProxyWebClient proxyWebClient, List<String> targetUris, LoadBalancerMode mode) {
		super(proxyWebClient);
		this.targetUris = targetUris;
		this.mode = mode;
		globalIndex = new AtomicInteger(0);
	}
	
	public void config(Router router) {
		config(router, "/*");
	}
	
	public void config(Router router, String path) {
		config(router, path, mode);
	}
	
	public void config(Router router, String path, LoadBalancerMode mode) {
		super.config(router, path, (routingContext) -> { 
			String target = null;
			if (mode==LoadBalancerMode.RANDOM)
				target = targetUris.get(ThreadLocalRandom.current().nextInt(targetUris.size()));
			else {
				int index = globalIndex.getAndUpdate((x) -> {
					if (x>=targetUris.size()-1) 
					    return 0;
					else
						return x+1;
				} );
				target = targetUris.get(index);
			}
			return Pair.of(path, target); 
		});
	}
}
