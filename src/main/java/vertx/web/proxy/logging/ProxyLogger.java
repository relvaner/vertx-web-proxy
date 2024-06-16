/*
 * Copyright (c) 2015-2021, David A. Bauer. All rights reserved.
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
package vertx.web.proxy.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ProxyLogger {
	public static final Level ERROR = Level.SEVERE;
	public static final Level WARN  = Level.WARNING;
	public static final Level INFO  = Level.INFO;
	public static final Level DEBUG = Level.CONFIG;
	public static final Level TRACE = Level.FINE;
	
	protected static Logger logger;
	
	static {
		logger = LoggerFactory.create("PROXY", DEBUG);
	}
	
	public static Logger logger() {
		return logger;
	}
	
	public static void logger(Logger logger) {
		ProxyLogger.logger = logger;
	}
	
	public static void showSimpleClassName() {
		showSimpleClassName(true);
	}
	
	public static void showSimpleClassName(boolean enabled) {
		LoggerFactory.simpleClassName = enabled;
	}
}
