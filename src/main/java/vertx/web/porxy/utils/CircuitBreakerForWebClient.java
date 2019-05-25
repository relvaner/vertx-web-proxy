package vertx.web.porxy.utils;

import java.util.HashMap;
import java.util.Map;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;

public class CircuitBreakerForWebClient {
	protected Vertx vertx;
	protected final CircuitBreakerOptions circuitBreakerOptions;
	protected final Map<String, CircuitBreaker> circuitBreakersMap;

	public CircuitBreakerForWebClient(Vertx vertx, CircuitBreakerOptions circuitBreakerOptions) {
		super();
		
		this.vertx = vertx;
		this.circuitBreakerOptions = circuitBreakerOptions;
		circuitBreakersMap = new HashMap<>();
	}
	
	public CircuitBreaker get(String host) {
		CircuitBreaker result = circuitBreakersMap.get(host);
		
		if (result==null) {
			result = CircuitBreaker.create(host, vertx, circuitBreakerOptions);
			/*
			.openHandler(v -> {
				System.out.println("Circuit opened");
			}).closeHandler(v -> {
				System.out.println("Circuit closed");
			});
			*/
			circuitBreakersMap.put(host, result);
		}
		
		return result;
	}
}
