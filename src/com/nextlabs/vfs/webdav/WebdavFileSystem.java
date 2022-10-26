package com.nextlabs.vfs.webdav;

import java.util.Collection;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.Capability;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.GenericFileName;
import org.apache.commons.vfs2.provider.webdav.WebdavFileProvider;

public class WebdavFileSystem extends org.apache.commons.vfs2.provider.webdav.WebdavFileSystem {

	protected WebdavFileSystem(final GenericFileName rootName, final HttpClient client, final FileSystemOptions fileSystemOptions) {
		super(rootName, client, fileSystemOptions);
	}

	@Override
	protected FileObject createFile(final AbstractFileName name) {
		return new WebdavFileObject(name, this);
	}
	
  @Override
  protected void addCapabilities(final Collection<Capability> caps) {
  	caps.add(Capability.APPEND_CONTENT);
  	super.addCapabilities(caps);
  }


	@Override
	public HttpClient getClient() {
		// make accessible
		return super.getClient();
	}
}