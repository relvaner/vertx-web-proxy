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
package vertx.web.proxy.utils;

import static vertx.web.proxy.logging.ProxyLogger.DEBUG;
import static vertx.web.proxy.logging.ProxyLogger.systemLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;

public class ConcurrentCircuitBreakerForWebClient {
	protected final Vertx vertx;
	protected final CircuitBreakerOptions circuitBreakerOptions;
	protected final Map<String, CircuitBreaker> circuitBreakersMap;
	
	protected final Lock circuitBreakerLock;

	public ConcurrentCircuitBreakerForWebClient(Vertx vertx, CircuitBreakerOptions circuitBreakerOptions) {
		super();
		
		this.vertx = vertx;
		this.circuitBreakerOptions = circuitBreakerOptions;
		
		circuitBreakersMap = new ConcurrentHashMap<>();
		circuitBreakerLock = new ReentrantLock();
	}
	
	public CircuitBreaker get(String urlPattern) {
		CircuitBreaker result = null;
		
		circuitBreakerLock.lock();
		try {
			result = circuitBreakersMap.get(urlPattern);
			
			if (result==null) {
				result = CircuitBreaker.create(urlPattern, vertx, circuitBreakerOptions);
				result
					.openHandler(v -> {
						systemLogger().log(DEBUG, String.format("Circuit opened: %s", urlPattern));
					})
					.halfOpenHandler(v -> {
						systemLogger().log(DEBUG, String.format("Circuit half open: %s", urlPattern));
					})
					.closeHandler(v -> {
						systemLogger().log(DEBUG, String.format("Circuit closed: %s", urlPattern));
					});
				circuitBreakersMap.put(urlPattern, result);
			}
		}
		finally {
			circuitBreakerLock.unlock();
		}
		
		return result;
	}
}
