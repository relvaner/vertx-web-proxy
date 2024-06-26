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

public class ProxyWebClientOptions {
	protected boolean log = false;
	protected boolean sendUrlFragment = true;
	protected boolean preserveHost = false;
	protected boolean preserveCookies = true;
	protected boolean forwardIP = true;
	protected boolean preserveCookiesContextPath = true;
	protected boolean preserveCookiesProxyPath = true;
	protected boolean ssl = false;
	
	protected int circuitBreakerMode = CIRCUIT_BREAKER_SERVER_URL;
	
	public static final int CIRCUIT_BREAKER_SERVER_URL   = 0;
	public static final int CIRCUIT_BREAKER_ABSOLUTE_URI = 1;
	public static final int CIRCUIT_BREAKER_URL_PATTERN  = 2;
	
	public ProxyWebClientOptions() {
		super();
	}
	
	public ProxyWebClientOptions setLog(boolean log) {
		this.log = log;
		
		return this;
	}
	
	public ProxyWebClientOptions setSendUrlFragment(boolean sendUrlFragment) {
		this.sendUrlFragment = sendUrlFragment;
		
		return this;
	}
	
	public ProxyWebClientOptions setPreserveHost(boolean preserveHost) {
		this.preserveHost = preserveHost;
		
		return this;
	}
	
	public ProxyWebClientOptions setPreserveCookies(boolean preserveCookies) {
		this.preserveCookies = preserveCookies;
		
		return this;
	}
	
	public ProxyWebClientOptions setForwardIP(boolean forwardIP) {
		this.forwardIP = forwardIP;
		
		return this;
	}
	
	public ProxyWebClientOptions setPreserveCookiesContextPath(boolean preserveCookiesContextPath) {
		this.preserveCookiesContextPath = preserveCookiesContextPath;
		
		return this;
	}
	
	public ProxyWebClientOptions setPreserveCookiesProxyPath(boolean preserveCookiesProxyPath) {
		this.preserveCookiesProxyPath = preserveCookiesProxyPath;
		
		return this;
	}
	
	public ProxyWebClientOptions setSsl(boolean ssl) {
		this.ssl = ssl;
		
		return this;
	}
	
	public ProxyWebClientOptions setCircuitBreakerMode(int circuitBreakerMode) {
		this.circuitBreakerMode = circuitBreakerMode;
		
		return this;
	}

	@Override
	public String toString() {
		return "ProxyWebClientOptions [log=" + log + ", sendUrlFragment=" + sendUrlFragment + ", preserveHost="
				+ preserveHost + ", preserveCookies=" + preserveCookies + ", forwardIP=" + forwardIP
				+ ", preserveCookiesContextPath=" + preserveCookiesContextPath + ", preserveCookiesProxyPath="
				+ preserveCookiesProxyPath + ", ssl=" + ssl + ", circuitBreakerMode=" + circuitBreakerMode + "]";
	}
}
