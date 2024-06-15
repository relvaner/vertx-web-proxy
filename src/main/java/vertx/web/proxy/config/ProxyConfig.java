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

import java.io.UnsupportedEncodingException;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import vertx.web.proxy.ProxyWebClient;

public class ProxyConfig {
	protected final ProxyWebClient proxyWebClient;
	protected final String UNAVAIALBLE_HTML = "<p style=\"color:red;\">Whoops, seems like the service is temporary unavailable! Your Proxy-Vert.x Team</p>";
	
	public ProxyConfig(ProxyWebClient proxyWebClient) {
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
				routingContext.response().headers().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(UNAVAIALBLE_HTML.getBytes().length));
				try {
					routingContext.response().end(Buffer.buffer(UNAVAIALBLE_HTML.getBytes("UTF-8")));
				} catch (UnsupportedEncodingException e) {
					routingContext.response().end();
					e.printStackTrace();
				}
			}
		});
	}
	
	public void config(Router router, String path, String target) {
		config(router, path, (routingContext) -> Pair.of(path, target));
	}
}
