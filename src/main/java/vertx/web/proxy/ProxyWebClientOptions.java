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

	@Override
	public String toString() {
		return "ProxyWebClientOptions [log=" + log + ", sendUrlFragment=" + sendUrlFragment + ", preserveHost="
				+ preserveHost + ", preserveCookies=" + preserveCookies + ", forwardIP=" + forwardIP
				+ ", preserveCookiesContextPath=" + preserveCookiesContextPath + ", preserveCookiesProxyPath="
				+ preserveCookiesProxyPath + ", ssl=" + ssl + "]";
	}
}
