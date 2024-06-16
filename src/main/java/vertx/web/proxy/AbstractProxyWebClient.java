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
package vertx.web.proxy;

import static vertx.web.proxy.logging.ProxyLogger.*;

import java.util.function.Consumer;

import io.vertx.core.Promise;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import vertx.web.proxy.utils.ConcurrentCircuitBreakerForWebClient;
import vertx.web.proxy.utils.URIInfo;

public class AbstractProxyWebClient {
	protected WebClient proxyClient;
	protected final ProxyWebClientOptions proxyWebClientOptions;
	
	protected final ConcurrentCircuitBreakerForWebClient circuitBreakerForWebClient;
	
	protected URIInfo serverRequestUriInfo;
	
	public AbstractProxyWebClient(WebClient proxyClient, ProxyWebClientOptions proxyWebClientOptions, ConcurrentCircuitBreakerForWebClient circuitBreakerForWebClient) {
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
	
	public URIInfo getServerRequestUriInfo() {
		return serverRequestUriInfo;
	}

	public void execute(RoutingContext routingContext, String urlPattern, Consumer<Promise<Object>> consumer) {
		String domain = urlPattern.replace("/*", "");
		if (domain.isEmpty())
			domain = "/";
		serverRequestUriInfo = URIInfo.create(routingContext.request().absoluteURI(), domain);
		
		if (proxyWebClientOptions.circuitBreakerUseServerURL)
			urlPattern = serverRequestUriInfo.getServerURL();
		else if (proxyWebClientOptions.circuitBreakerUseAbsoluteURI)
			urlPattern = routingContext.request().absoluteURI();
		
		final String urlPattern_ = urlPattern;
		circuitBreakerForWebClient.get(urlPattern)
			.execute((promise) -> {
				try {
					consumer.accept(promise);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			})
			.onComplete(asyncResult -> {
				if (asyncResult.failed()) {
					logger().log(ERROR, String.format("WebClient failed ('%s'): %s", urlPattern_, asyncResult.cause()));
					routingContext.fail(503);
				}
			});
	}
}
