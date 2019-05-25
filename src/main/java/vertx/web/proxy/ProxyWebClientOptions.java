package vertx.web.proxy;

public class ProxyWebClientOptions {
	protected boolean doLog = false;
	protected boolean doSendUrlFragment = true;
	protected boolean doPreserveHost = false;
	protected boolean doPreserveCookies = false;
	protected boolean doForwardIP = true;
	protected boolean doPreserveCookiesContextPath = false;
	protected boolean doPreserveCookiesProxyPath = false;
	protected boolean doSSL = false;
	
	public ProxyWebClientOptions() {
		super();
	}
	
	public boolean isDoLog() {
		return doLog;
	}

	public void setDoLog(boolean doLog) {
		this.doLog = doLog;
	}
	
	public boolean isDoSendUrlFragment() {
		return doSendUrlFragment;
	}

	public void setDoSendUrlFragment(boolean doSendUrlFragment) {
		this.doSendUrlFragment = doSendUrlFragment;
	}

	public boolean isDoPreserveHost() {
		return doPreserveHost;
	}

	public void setDoPreserveHost(boolean doPreserveHost) {
		this.doPreserveHost = doPreserveHost;
	}

	public boolean isDoPreserveCookies() {
		return doPreserveCookies;
	}

	public void setDoPreserveCookies(boolean doPreserveCookies) {
		this.doPreserveCookies = doPreserveCookies;
	}

	public boolean isDoForwardIP() {
		return doForwardIP;
	}

	public void setDoForwardIP(boolean doForwardIP) {
		this.doForwardIP = doForwardIP;
	}

	public boolean isDoPreserveCookiesContextPath() {
		return doPreserveCookiesContextPath;
	}

	public void setDoPreserveCookiesContextPath(boolean doPreserveCookiesContextPath) {
		this.doPreserveCookiesContextPath = doPreserveCookiesContextPath;
	}

	public boolean isDoPreserveCookiesProxyPath() {
		return doPreserveCookiesProxyPath;
	}

	public void setDoPreserveCookiesProxyPath(boolean doPreserveCookiesProxyPath) {
		this.doPreserveCookiesProxyPath = doPreserveCookiesProxyPath;
	}

	public boolean isDoSSL() {
		return doSSL;
	}

	public void setDoSSL(boolean doSSL) {
		this.doSSL = doSSL;
	}
}
