package vertx.web.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// https://github.com/eclipse-vertx/vert.x/issues/2774
public class ProxyLogger {
	private static final Logger logger = LoggerFactory.getLogger(ProxyLogger.class);
	
	public static Logger logger() {
		return logger;
	}
}
