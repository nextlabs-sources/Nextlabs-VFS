/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nextlabs.vfs.smb;

import jcifs.CIFSContext;
import jcifs.smb.*;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileObject;
import org.apache.commons.vfs2.provider.UriParser;
import org.apache.commons.vfs2.util.RandomAccessMode;
import org.apache.commons.vfs2.util.UserAuthenticatorUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;

/**
 * A file in an SMB file system.
 */
public class SmbFileObject extends AbstractFileObject<SmbFileSystem> {
	// private final String fileName;
	private SmbFile file;
	private static final Logger logger = LogManager.getLogger(SmbFileObject.class);

	@SuppressWarnings("RedundantThrows")
	protected SmbFileObject(final AbstractFileName name, final SmbFileSystem fileSystem) throws FileSystemException {
		super(name, fileSystem);
	}

	/**
	 * Attaches this file object to its file resource.
	 */
	@Override
	protected void doAttach() throws Exception {
		// Defer creation of the SmbFile to here
		while (true) {
			try {
				if (file == null) {
					file = createSmbFile(getName());
				}
				break;
			} catch (SmbException e) {
				logger.debug(e.getMessage(), e);
				logger.debug("Failed to attach file, retrying in 3s...");
				System.out.println("Failed to attach file, retrying in 3s...");
				Thread.sleep(3000L);
			}
		}	
	}

	@SuppressWarnings("RedundantThrows")
	@Override
	protected void doDetach() throws Exception {
		// file closed through content-streams
		file.close();
		file = null;
	}

	private SmbFile createSmbFile(final FileName fileName) throws SmbException, FileSystemException, MalformedURLException {
		SmbFile file = null;
		try {
			final SmbFileName smbFileName = (SmbFileName) fileName;
			final String path = smbFileName.getUriWithoutAuth();
			CIFSContext cifsContext = getAbstractFileSystem().getCifsContext();

			if ("true".equals(System.getProperty("enable_debugging"))) {
				System.out.println("Checking CIFS Context properties...");
				System.out.println("getResolveOrder" + "=" + cifsContext.getConfig().getResolveOrder());
				System.out.println("isDfsDisabled" + "=" + cifsContext.getConfig().isDfsDisabled());
				System.out.println("getMinimumVersion" + "=" + cifsContext.getConfig().getMinimumVersion());
				System.out.println("getMaximumVersion" + "=" + cifsContext.getConfig().getMaximumVersion());
				System.out.println("getConnTimeout" + "=" + cifsContext.getConfig().getConnTimeout());
				System.out.println("getSoTimeout" + "=" + cifsContext.getConfig().getSoTimeout());
				System.out.println("getResponseTimeout" + "=" + cifsContext.getConfig().getResponseTimeout());
				System.out.println("getSessionTimeout" + "=" + cifsContext.getConfig().getSessionTimeout());
				System.out.println("isIpcSigningEnforced" + "=" + cifsContext.getConfig().isIpcSigningEnforced());
				System.out.println("isUseSMB2OnlyNegotiation" + "=" + cifsContext.getConfig().isUseSMB2OnlyNegotiation());
				System.out.println("isPort139FailoverEnabled" + "=" + cifsContext.getConfig().isPort139FailoverEnabled());
			}
	
			file = new SmbFile(path, cifsContext);
	
			if (file.isDirectory() && !file.toString().endsWith("/")) {
				file = new SmbFile(path + "/", cifsContext);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return file;
	}

	/**
	 * Determines the type of the file, returns null if the file does not exist.
	 */
	@Override
	protected FileType doGetType() throws Exception {
		try {
			if ("true".equals(System.getProperty("enable_debugging"))) {
				System.out.println("SmbFileObject.doGetType file exists? " + !file.exists());
			}
			if (!file.exists()) {
				return FileType.IMAGINARY;
			} else if (file.isDirectory()) {
				return FileType.FOLDER;
			} else if (file.isFile()) {
				return FileType.FILE;
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		throw new FileSystemException("vfs.provider.smb/get-type.error", getName());
	}

	/**
	 * Lists the children of the file. Is only called if {@link #doGetType} returns
	 * {@link FileType#FOLDER}.
	 */
	@Override
	protected String[] doListChildren() throws Exception {
		// VFS-210: do not try to get listing for anything else than directories
		if (!file.isDirectory()) {
			return null;
		}

		return file.list();
	}

	/**
	 * Determines if this file is hidden.
	 */
	@Override
	protected boolean doIsHidden() throws Exception {
		return file.isHidden();
	}

	/**
	 * Deletes the file.
	 */
	@Override
	protected void doDelete() throws Exception {
		file.close();
		file.delete();
	}

	@Override
	protected void doRename(final FileObject newfile) throws Exception {
		file.renameTo(createSmbFile(newfile.getName()));
	}

	/**
	 * Creates this file as a folder.
	 */
	@Override
	protected void doCreateFolder() throws Exception {
		file.mkdir();
		file = createSmbFile(getName());
	}

	/**
	 * Returns the size of the file content (in bytes).
	 */
	@Override
	protected long doGetContentSize() throws Exception {
		return file.length();
	}

	/**
	 * Returns the last modified time of this file.
	 */
	@SuppressWarnings("RedundantThrows")
	@Override
	protected long doGetLastModifiedTime() throws Exception {
		return file.getLastModified();
	}

	/**
	 * Creates an input stream to read the file content from.
	 */
	@Override
	protected InputStream doGetInputStream() throws Exception {
		if (file.isDirectory()) {
			throw new FileTypeHasNoContentException(getName());
		}
		try {
			return new SmbFileInputStream(file);
		} catch (final SmbException e) {
			int ntStatus = e.getNtStatus();
			if (ntStatus == NtStatus.NT_STATUS_NO_SUCH_FILE || ntStatus == NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND || ntStatus == NtStatus.NT_STATUS_NOT_FOUND) {
				throw new org.apache.commons.vfs2.FileNotFoundException(getName());
			}
			throw e;
		}
	}

	/**
	 * Creates an output stream to write the file content to.
	 */
	@Override
	protected OutputStream doGetOutputStream(final boolean bAppend) throws Exception {
		if (file.isDirectory()) {
			throw new FileTypeHasNoContentException(getName());
		}
		return new SmbFileOutputStream(file, bAppend);
	}

	/**
	 * random access
	 */
	@Override
	protected RandomAccessContent doGetRandomAccessContent(final RandomAccessMode mode) throws Exception {
		return new SmbFileRandomAccessContent(file, mode);
	}

	@Override
	protected boolean doSetLastModifiedTime(final long modtime) throws Exception {
		file.setLastModified(modtime);
		return true;
	}
}