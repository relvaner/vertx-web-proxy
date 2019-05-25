package vertx.web.proxy;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

/*
 * Adapted according to https://github.com/relvaner/actor4j/blob/master/actor4j-core/src/main/java/actor4j/core/utils/ActorLogger.java
 */
public class ProxyLogger {
	static ProxyLogger proxyLogger;

	protected Logger logger;

	static {
		proxyLogger = new ProxyLogger();
	}

	private ProxyLogger() {
		String hostAddress = "";
		try {
			hostAddress = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		String layout_console = "%d{yyyy-MM-dd hh:mm:ss,SSS}\t%-5p proxy [" + hostAddress + "] - %m%n";
		
		logger = Logger.getLogger(this.getClass());
		logger.addAppender(new ConsoleAppender(new PatternLayout(layout_console)));
	}

	public static Logger logger() {
		return proxyLogger.logger;
	}
}
