package com.nextlabs.vfs.webdav;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemConfigBuilder;
import org.apache.commons.vfs2.FileSystemOptions;

public class WebdavFileSystemConfigBuilder extends FileSystemConfigBuilder {
	private static final WebdavFileSystemConfigBuilder BUILDER = new WebdavFileSystemConfigBuilder();

	protected WebdavFileSystemConfigBuilder() {
		super("webdav.");
	}

	public void setAuthType(final FileSystemOptions opts, String authType) {
		setParam(opts, "authType", authType);
	}

	public void setCredentials(final FileSystemOptions opts, String domain, String username, String password) {
		setParam(opts, "credentials.username", username);
		setParam(opts, "credentials.password", password);
		setParam(opts, "credentials.domain", domain);
	}

	public String getAuthType(final FileSystemOptions opts) {
		return getString(opts, "authType");
	}

	public Triple<String, String, String> getCredentials(final FileSystemOptions opts) {
		return ImmutableTriple.of(getString(opts, "credentials.domain"), getString(opts, "credentials.username"), getString(opts, "credentials.password"));
	}

	@Override
	protected Class<? extends FileSystem> getConfigClass() {
		return WebdavFileSystem.class;
	}

	public static WebdavFileSystemConfigBuilder getInstance() {
		return BUILDER;
	}
}
