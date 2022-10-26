package com.nextlabs.vfs;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;

public class URLFileName extends org.apache.commons.vfs2.provider.URLFileName {

	private static final int BUFFER_SIZE = 250;

	public URLFileName(String scheme, String hostName, int port, int defaultPort, String userName, String password, String path, FileType type, String queryString) {
		super(scheme, hostName, port, defaultPort, userName, password, path, type, queryString);
	}
	
	// Constructor to cast org.apache.commons.vfs2.provider.URLFileName to com.nextlabs.vfs.URLFileName
	public URLFileName(org.apache.commons.vfs2.provider.URLFileName name) {
		super(name.getScheme(), name.getHostName(), name.getPort(), name.getDefaultPort(), name.getUserName(), name.getPassword(), name.getPath(), name.getType(), name.getQueryString());
	}

	@Override
	public String getPathQueryEncoded(final String charset) throws URIException, FileSystemException {
		if (getQueryString() == null) {
			if (charset != null) {
				return URIUtil.encodePath(getPath(), charset);
			}
			return URIUtil.encodePath(getPath());
		}

		final StringBuilder sb = new StringBuilder(BUFFER_SIZE);
		if (charset != null) {
			sb.append(URIUtil.encodePath(getPath(), charset));
		} else {
			sb.append(URIUtil.encodePath(getPath()));
		}
		sb.append("?");
		sb.append(getQueryString());
		return sb.toString();
	}

}
