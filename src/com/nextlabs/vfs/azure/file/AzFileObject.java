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
package com.nextlabs.vfs.azure.file;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.file.CloudFile;
import com.microsoft.azure.storage.file.CloudFileDirectory;
import com.microsoft.azure.storage.file.FileInputStream;
import com.microsoft.azure.storage.file.FileOutputStream;
import com.microsoft.azure.storage.file.FileProperties;
import com.microsoft.azure.storage.file.ListFileItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileObject;
import org.apache.commons.vfs2.provider.AbstractFileSystem;
import org.apache.commons.vfs2.provider.UriParser;
import org.apache.commons.vfs2.util.FileObjectUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.nextlabs.vfs.URLFileName;

/**
 * The main FileObject class in this provider. It holds most of the API
 * callbacks for the provider.
 * 
 * @author Theodore Lee
 */
public class AzFileObject extends AbstractFileObject<AbstractFileSystem> {
	private static final Logger log = LogManager.getLogger(AzFileObject.class);

	private final AzFileSystem fileSystem;
	private CloudFile currFile;
	private CloudFileDirectory rootDir;
	private CloudFileDirectory currDir;
	private FileProperties currFileProperties;
	
	private static class AzFileOutputStream extends OutputStream implements AutoCloseable {
		private static final Logger logger = LogManager.getLogger(AzFileOutputStream.class);
		private AzFileObject file;
		private boolean bAppend;
		private List<byte[]> bytes;
		private long appendSize;
		private long fileSize;
		private FileOutputStream os;
		
		public AzFileOutputStream(AzFileObject file, boolean bAppend) throws StorageException, URISyntaxException, IOException {
			this.file = file;
			this.bytes = new ArrayList<byte[]>();
			this.bAppend = bAppend;
			
			if (bAppend && file.exists()) {
				fileSize = file.currFile.getProperties().getLength();
				os = file.currFile.openWriteExisting();
				try(FileInputStream is = file.currFile.openRead()) {
					byte[] originalContent = new byte[(int) fileSize];
					is.read(originalContent);
					bytes.add(originalContent);
				} catch (IOException e) {
					e.printStackTrace();
					logger.error(e.getMessage(), e);
				}
			}
		}

		@Override
		public void write(int b) throws IOException {
			appendSize++;
			bytes.add(new byte[] {(byte) b});
		}
		
		@Override
		public void write(byte[] b) {
			write(b, 0, b.length);
		}
		
		@Override
		public void write(byte[] b, int off, int len) {
			appendSize += len;
			bytes.add(Arrays.copyOfRange(b, off, off + len));
		}
		
		@Override
		public void close() throws IOException {
			writeBytes();
			if (os != null) {
				os.flush();
				os.close();
			}
			super.close();
		}
		
		@Override
		public void flush() throws IOException {
			super.flush();
		}
		
		private void writeBytes() {
			if (!bytes.isEmpty()) {
				try {
					if (os == null && !file.exists()) os = file.currFile.openWriteNew(appendSize);
					else {
						fileSize += appendSize;
						file.currFile.resize(fileSize);
						if (os == null) os = file.currFile.openWriteExisting();
					}
					for (byte[] b : bytes) os.write(b);
					
				} catch (StorageException | URISyntaxException | IOException e) {
					e.printStackTrace(); logger.error(e.getMessage(), e);
				}
				bytes.clear(); appendSize = 0;
			}
		}
	}

	/**
	 * Creates a new FileObject for use with a remote Azure File Storage file or
	 * folder.
	 * 
	 * @param name
	 * @param fileSystem
	 */
	protected AzFileObject(final AbstractFileName name, final AzFileSystem fileSystem) {
		super(new URLFileName((org.apache.commons.vfs2.provider.URLFileName) name), fileSystem);
		this.fileSystem = fileSystem;

		currFile = null;
		rootDir = null;
		currDir = null;
		currFileProperties = null;
	}

	/**
	 * Convenience method that returns the container and path from the current URL.
	 * 
	 * @return A tuple containing the container name and the path.
	 */
	protected Pair<String, String> getContainerAndPath() {
		Pair<String, String> res = null;

		try {
			URLFileName currName = (URLFileName) getName();

			String currNameStr = currName.getPath();
			currNameStr = StringUtils.stripStart(currNameStr, "/");

			if (StringUtils.isBlank(currNameStr)) {
				log.warn(String.format("getContainerAndPath() : Path '%s' does not appear to be valid", currNameStr));

				return null;
			}

			// Deal with the special case of the container root.
			if (StringUtils.contains(currNameStr, "/") == false) {
				// Container and root
				return new ImmutablePair<>(currNameStr, "/");
			}

			String[] resArray = StringUtils.split(currNameStr, "/", 2);

			res = new ImmutablePair<>(resArray[0], resArray[1]);
		} catch (Exception ex) {
			log.error(String.format("getContainerAndPath() : Path does not appear to be valid"), ex);
		}
		return res;
	}

	/**
	 * Callback used when this FileObject is first used. We connect to the remote
	 * server and check early so we can 'fail-fast'. If there are no issues then
	 * this FileObject can be used.
	 * 
	 * @throws Exception
	 */
	@Override
	protected void doAttach() throws Exception {
		Pair<String, String> path = getContainerAndPath();
		try {
			// Check the container. Force a network call so we can fail-fast
			rootDir = fileSystem.getClient().getShareReference(path.getLeft()).getRootDirectoryReference();
		} catch (RuntimeException ex) {
			log.error(String.format("doAttach() Exception for '%s' : '%s'", path.getLeft(), path.getRight()), ex);
			throw ex;
		}
		currFile = rootDir.getFileReference(path.getRight());
		currDir = path.getRight().equals("/") ? rootDir : rootDir.getDirectoryReference(path.getRight());
	}

	/**
	 * Callback for checking the type of the current FileObject. Typically can be of
	 * type... FILE for regular remote files FOLDER for regular remote containers
	 * IMAGINARY for a path that does not exist remotely.
	 * 
	 * @return
	 * @throws Exception
	 */
	@Override
	protected FileType doGetType() throws Exception {
		String prefix = getContainerAndPath().getRight();
		try {
			if (!prefix.equals("/") && currFile.exists()) return FileType.FILE;
			else if (currDir.listFilesAndDirectories().iterator().hasNext()) return FileType.FOLDER;
			else if (currDir.exists()) return FileType.FOLDER;
			return FileType.IMAGINARY;
		} catch (Exception e) {
			return FileType.IMAGINARY;
		}
	}

	/**
	 * Lists the children of this file. Is only called if {@link #doGetType} returns
	 * {@link FileType#FOLDER}. The return value of this method is cached, so the
	 * implementation can be expensive.<br />
	 * 
	 * @return a possible empty String array if the file is a directory or null or
	 *         an exception if the file is not a directory or can't be read.
	 * @throws Exception if an error occurs.
	 */
	@Override
	protected String[] doListChildren() throws Exception {
		// Use doListChildrenResolved
		return null;
	}
	
	@Override
	protected FileObject[] doListChildrenResolved() throws Exception {
		Iterable<ListFileItem> files = currDir.listFilesAndDirectories();
		List<ListFileItem> fileList = new ArrayList<>();
		// Pull it all in memory and work from there
		CollectionUtils.addAll(fileList, files);
		ArrayList<AzFileObject> resList = new ArrayList<>();
		for (ListFileItem currFile : fileList) {
			AzFileObject file = (AzFileObject) FileObjectUtils.getAbstractFileObject(getFileSystem().resolveFile(getFileSystem().getFileSystemManager().resolveName(getName(), UriParser.decode(currFile.getUri().toString().substring(currFile.getParent().getUri().toString().length()+1)), NameScope.CHILD)));
			resList.add(file);
		}
		AzFileObject[] res = resList.toArray(new AzFileObject[resList.size()]);
		return res;
	}

	private void checkFileProperties() throws StorageException {
		if (currFileProperties == null) {
			currFile.downloadAttributes();
			currFileProperties = currFile.getProperties();
		}
	}

	/**
	 * Callback for handling "content size" requests by the provider.
	 * 
	 * @return The number of bytes in the File Object's content
	 * @throws Exception
	 */
	@Override
	protected long doGetContentSize() throws Exception {
		long res = -1;

		checkFileProperties();
		res = currFileProperties.getLength();

		return res;
	}

	/**
	 * Get an InputStream for reading the content of this File Object.
	 * 
	 * @return The InputStream object for reading.
	 * @throws Exception
	 */
	@Override
	protected InputStream doGetInputStream() throws Exception {
		return currFile.openRead();
	}

	/**
	 * Callback for handling delete on this File Object
	 * 
	 * @throws Exception
	 */
	@Override
	protected void doDelete() throws Exception {
		currFile.deleteIfExists();
		currDir.deleteIfExists();
	}

	/**
	 * Callback for handling create folder requests. Since there are no folders in
	 * Azure Cloud Storage this call is ingored.
	 * 
	 * @throws Exception
	 */
	@Override
	protected void doCreateFolder() throws Exception {
		currDir.createIfNotExists();
	}
	
	@Override
	public void createFile() throws FileSystemException {
		super.createFile();
		try {
			currFile.create(0L);
		} catch (StorageException | URISyntaxException e) {
			e.printStackTrace();
			log.error(e.getMessage(), e);
			throw new FileSystemException("Could not create file" + currFile.getName());
		}
	}

	/**
	 * Callback for getting an OutputStream for writing into Azure File Storage
	 * file.
	 * 
	 * @param bAppend bAppend true if the file should be appended to, false if it
	 *                should be overwritten.
	 * @return
	 * @throws Exception
	 */
	@Override
	protected OutputStream doGetOutputStream(boolean bAppend) throws Exception {
		return new AzFileOutputStream(this, bAppend);
	}

	/**
	 * Callback for use when detaching this File Object from Azure File Storage.
	 * 
	 * The File Object should be reusable after <code>attach()</code> call.
	 * 
	 * @throws Exception
	 */
	@Override
	protected void doDetach() throws Exception {
		currFile = null;
		rootDir = null;
		currDir = null;
		currFileProperties = null;
	}

	/**
	 * Callback for handling the <code>getLastModifiedTime()</code> Commons VFS API
	 * call.
	 * 
	 * @return Time since the file has last been modified
	 * @throws Exception
	 */
	@Override
	protected long doGetLastModifiedTime() throws Exception {
		try {
			checkFileProperties();
			Date lm = currFileProperties.getLastModified();

			return lm.getTime();
		} catch (StorageException e) {
		// WARNING: THIS IS A HACK
			// AZURE FILE STORAGE CANNOT DETERMINE THE LASTMODIFIEDTIME ON FOLDERS
			// SO IF YOU CHECK THE LAST MODIFIED TIME ON A FOLDER IT WILL RETURN CURRENT TIME INSTEAD
			log.warn("Azure file storage cannot determine LastModifiedTime on non-file type objects. LastModifiedTime for " + this.getName().toString() + " will be returned as current time instead.");
			return System.currentTimeMillis();
		}
	}
}
