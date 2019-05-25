package vertx.web.porxy.utils;

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
		return "/"+domain;
	}
	
	public String getPathInfo() {
		// /api/ping?test=2
		return uri.getPath().replaceFirst("/"+domain, "")+(uri.getQuery()!=null ? "?" : "")+uri.getQuery();
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
	
	public String getServletPath() {
		// /api/ping?
		return uri.getPath().replaceFirst("/"+domain, "")+(uri.getQuery()!=null ? "?" : "");
	}
}
