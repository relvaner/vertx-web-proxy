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

import java.net.URI;
import java.net.URISyntaxException;

// @See: https://stackoverflow.com/questions/4931323/whats-the-difference-between-getrequesturi-and-getpathinfo-methods-in-httpservl
public class URIInfo {
	protected final URI uri;
	protected final String domain;
	
	public URIInfo(URI uri, String domain) {
		super();
		this.uri = uri;
		this.domain = domain;
	}
	
	public static URIInfo create(String uri, String domain) {
		URIInfo result = null;
		try {
			URI temp = new URI(uri);
			result = new URIInfo(temp, domain);
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	public URI getUri() {
		return uri;
	}

	public String getRequestURL() {
		// https://localhost:8888/domain/api/ping?test=2
		return uri.toString();
	}
	
	public String getRequestURI() {
		// /domain/api/ping?test=2
		return uri.getPath()+(uri.getQuery()!=null ? "?" : "")+uri.getQuery();
	}
	
	public String getContextPath() {
		// /domain
		return domain;
	}
	
	public String getPathInfo() {
		// /api/ping?test=2
		String result = uri.getPath().replaceFirst(domain, "");
		if (!result.startsWith("/"))
			result = "/" + result;
		return result;
	}
	
	public String getQueryString() {
		// test=2
		return uri.getQuery();
	}
	
	public String getScheme() {
		// https
		return uri.getScheme();
	}
	
	public String getServerName() {
		// localhost
		return uri.getHost();
	}        
	
	public int getServerPort() {
		// 8888
		return uri.getPort();
	}
	
	public String getProxyPath() {
		// /api/ping?
		return getPathInfo()+(uri.getQuery()!=null ? "?" : "");
	}
	
	public String getServerURL() {
		return uri.getScheme()+"://"+uri.getHost()+":"+uri.getPort();
	}
}
