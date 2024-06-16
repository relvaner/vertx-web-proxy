## vertx-web-proxy - Example ##

```java
ConcurrentCircuitBreakerForWebClient circuitBreaker = new ConcurrentCircuitBreakerForWebClient(vertx, 
	new CircuitBreakerOptions()
		.setMaxFailures(5)
		.setTimeout(2_000)
		.setResetTimeout(30_000));

public class ServerVerticle extends AbstractVerticle {
	@Override
	public void start() throws Exception {
		Router router = Router.router(vertx);
		router.route().handler(CookieHandler.create());
		router.route().handler(BodyHandler.create()
        	.setBodyLimit(-1)
			.setHandleFileUploads(true)
			.setDeleteUploadedFilesOnEnd(true)
			.setMergeFormAttributes(true)
        );
		
		proxyConfig(router);
		
		vertx.createHttpServer(new HttpServerOptions().setSsl(true).setKeyStoreOptions(
		        new JksOptions().setPath("server-keystore.jks").setPassword("secret")))
			.requestHandler(router)
			.listen(443);
	}
	
	public void proxyConfig(Router router) {
		WebClientOptions options = new WebClientOptions();
		options.setSsl(true);
		WebClient webClient = WebClient.create(vertx, options);
		
		ProxyWebClientOptions proxyOptions = new ProxyWebClientOptions();
        proxyOptions
        	.setLog(true)
			.setForwardIP(false);

		ProxyWebClient proxyWebClient = new ProxyWebClient(webClient, proxyOptions, circuitBreaker);
		
		Map<String, String> targetUris = new HashMap<>();
		targetUris.put("/*", "https://host:port");
		targetUris.put("/apple", "http://host:port");
		targetUris.put("/banana/*", "https://host:port");
			
		ProxyLogger.logger().setLevel(Level.INFO);
		MultiProxyConfig multiProxyConfig = new MultiProxyConfig(proxyWebClient, targetUris);
		multiProxyConfig.config(router);
	}
}
```

## License ##
This library is released under an open source Apache 2.0 license. Ported with adaptations from [Smiley's HTTP Proxy Servlet](https://github.com/mitre/HTTP-Proxy-Servlet).