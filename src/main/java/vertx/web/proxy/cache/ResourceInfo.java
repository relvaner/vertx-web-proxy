package vertx.web.proxy.cache;

public class ResourceInfo {
	protected String contentType;
	protected int contentLength;
	
	protected long lastModified;
	protected String eTag;
	
	public ResourceInfo() {
		super();
	}

	public ResourceInfo(String contentType, int contentLength, long lastModified, String eTag) {
		super();
		this.contentType = contentType;
		this.contentLength = contentLength;
		this.lastModified = lastModified;
		this.eTag = eTag;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public int getContentLength() {
		return contentLength;
	}

	public void setContentLength(int contentLength) {
		this.contentLength = contentLength;
	}

	public long getLastModified() {
		return lastModified;
	}

	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}

	public String geteTag() {
		return eTag;
	}

	public void seteTag(String eTag) {
		this.eTag = eTag;
	}

	@Override
	public String toString() {
		return "ResourceInfo [contentType=" + contentType + ", contentLength=" + contentLength + ", lastModified="
				+ lastModified + ", eTag=" + eTag + "]";
	}
}
