## vertx-web-proxy - Example ##

```java
public class ServerVerticle extends AbstractVerticle {
	@Override
	public void start() throws Exception {
		Router router = Router.router(vertx);
		router.route().handler(CookieHandler.create());
		router.route().handler(BodyHandler.create()
				.setBodyLimit(-1));
		
		proxyConfig(router);
		
		vertx.createHttpServer(new HttpServerOptions().setSsl(true).setKeyStoreOptions(
		        new JksOptions().setPath("server-keystore.jks").setPassword("secret")))
			.requestHandler(router)
			.listen(443);
	}
	
	public void proxyConfig(Router router) {
		WebClientOptions options = new WebClientOptions();
		options.setSsl(true);
		options.setTrustAll(true);
		options.setVerifyHost(false);
		WebClient webClient = WebClient.create(vertx, options);
		
		ProxyWebClientOptions proxyOptions = new ProxyWebClientOptions();
		proxyOptions
			.setLog(true)
			.setSsl(true)
			.setForwardIP(false)
			.setCircuitBreakerUseAbsoluteURI(true);
		
		CircuitBreakerForWebClient circuitBreaker = new CircuitBreakerForWebClient(vertx, 
				new CircuitBreakerOptions()
					.setMaxFailures(3)
					.setTimeout(5_000)
					.setResetTimeout(2_000));
		
		ProxyWebClient proxyWebClient = new ProxyWebClient(webClient, proxyOptions, circuitBreaker);
		
		Map<String, String> targetUris = new HashMap<>();
		targetUris.put("/*", "https://host:443");
		targetUris.put("/apple", "https://host:443");
		targetUris.put("/banana/*", "https://host:443");
			
		ProxyLogger.logger().setLevel(Level.INFO);
		ProxyConfig proxyConfig = new ProxyConfig(proxyWebClient, targetUris);
		proxyConfig.config(router);
	}
}
```

## License ##
This library is released under an open source Apache 2.0 license.

## Announcement ##
This library is currently under a prototype state.