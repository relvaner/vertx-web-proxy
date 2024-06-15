/*
 * Copyright (c) 2019-2024, David A. Bauer. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package vertx.web.proxy.config;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Pair;

import io.vertx.ext.web.Router;
import vertx.web.proxy.ProxyWebClient;

public class LoadBalancerConfig extends ProxyConfig {
	protected final List<String> targetUris; // "/*" -> "https://host:port, https://host:port..."
	protected final LoadBalancerMode mode;
	protected static final AtomicInteger globalIndex = new AtomicInteger(0);
		
	public LoadBalancerConfig(ProxyWebClient proxyWebClient, List<String> targetUris, LoadBalancerMode mode) {
		super(proxyWebClient);
		this.targetUris = targetUris;
		this.mode = mode;
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
