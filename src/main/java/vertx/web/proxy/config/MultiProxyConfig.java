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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang3.tuple.Pair;
import java.util.TreeMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import vertx.web.proxy.ProxyWebClient;

public class MultiProxyConfig extends ProxyConfig {
	protected final Map<String, String> targetUris; // urlPattern -> targetUri; e.g., "/domain" or "/" -> "https://host:port"
		
	public MultiProxyConfig(ProxyWebClient proxyWebClient, Map<String, String> targetUris) {
		super(proxyWebClient);
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
		config(router, "/*");
	}

	protected void config(Router router, String path) {
		config(router, path, (routingContext) -> {
			String urlPattern = matchesUrlPattern(routingContext);
			return Pair.of(urlPattern, targetUris.get(urlPattern));
		});
	}
}
