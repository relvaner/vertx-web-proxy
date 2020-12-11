package vertx.web.proxy;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ProxyLogger {
	private static final Logger logger = LoggerFactory.getLogger(ProxyLogger.class);
	
	public static Logger logger() {
		return logger;
	}
}
