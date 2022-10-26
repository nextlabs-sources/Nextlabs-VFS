// The three classes in this package mostly exist because VFS 2.2 does not support SSL encryption and WebDAV simultaneously by default
// These classes enable subclass the WebDAV classes from VFS 2.2 and enable SSL support on them.

package com.nextlabs.vfs.webdav;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.DefaultHttpParams;
import org.apache.commons.httpclient.params.HttpParams;
import org.apache.commons.httpclient.util.DateUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.vfs2.FileNotFolderException;
import org.apache.commons.vfs2.FileNotFoundException;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.DefaultFileContent;
import org.apache.commons.vfs2.provider.UriParser;
import org.apache.commons.vfs2.provider.http.HttpFileObject;
import org.apache.commons.vfs2.provider.webdav.ExceptionConverter;
import org.apache.commons.vfs2.provider.webdav.WebdavFileSystemConfigBuilder;
import org.apache.commons.vfs2.util.FileObjectUtils;
import org.apache.commons.vfs2.util.MonitorInputStream;
import org.apache.commons.vfs2.util.MonitorOutputStream;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.CheckinMethod;
import org.apache.jackrabbit.webdav.client.methods.CheckoutMethod;
import org.apache.jackrabbit.webdav.client.methods.DavMethod;
import org.apache.jackrabbit.webdav.client.methods.DavMethodBase;
import org.apache.jackrabbit.webdav.client.methods.DeleteMethod;
import org.apache.jackrabbit.webdav.client.methods.MkColMethod;
import org.apache.jackrabbit.webdav.client.methods.MoveMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.client.methods.PropPatchMethod;
import org.apache.jackrabbit.webdav.client.methods.PutMethod;
import org.apache.jackrabbit.webdav.client.methods.UncheckoutMethod;
import org.apache.jackrabbit.webdav.client.methods.VersionControlMethod;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.VersionControlledResource;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.w3c.dom.Node;

import com.nextlabs.vfs.URLFileName;
import com.nextlabs.vfs.authentication.SharepointOnline;
import com.nextlabs.vfs.constant.AuthType;

public class WebdavFileObject extends HttpFileObject<WebdavFileSystem> {

	static class HttpInputStream extends MonitorInputStream {
		private static final Logger logger = LogManager.getLogger(HttpInputStream.class);

		private final GetMethod method;

		public HttpInputStream(final GetMethod method) throws IOException {
			super(method.getResponseBodyAsStream());
			this.method = method;
		}

//		@Override
//		public long skip(long n) throws IOException {
//			long toSkip = n;
//			while (toSkip > 0) {
//				long skipped = super.skip(toSkip);
//				if (skipped == 0) break;
//				toSkip -= skipped;
//			}
//			return n - toSkip;
//		}
//
		@Override
		public int read(byte[] b) throws IOException {
			return super.read(b);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int r = 0;
			while (r < len) {
				byte[] bytes = new byte[b.length];
				int rr = super.read(bytes, 0, len - r);
				if (rr == -1 && r == 0) r = -1; // EOF and current bytes read is 0
				if (rr < 0) break; // No bytes read and read returned 0 or -1 for EOF
				System.arraycopy(bytes, 0, b, r, rr);
				r += rr;
			}
			return r;
		}

		/**
		 * Called after the stream has been closed.
		 */
		@Override
		protected void onClose() throws IOException {
			method.releaseConnection();
		}
	}

	private static final Logger logger = LogManager.getLogger(WebdavFileObject.class);

	public static final DavPropertyName RESPONSE_CHARSET = DavPropertyName.create("response-charset");
	private final WebdavFileSystemConfigBuilder builder;
	private final WebdavFileSystem fileSystem;

	protected WebdavFileObject(final AbstractFileName name, final WebdavFileSystem fileSystem) {
		super(new URLFileName((org.apache.commons.vfs2.provider.URLFileName) name), fileSystem);
		this.fileSystem = fileSystem;
		this.builder = (WebdavFileSystemConfigBuilder) WebdavFileSystemConfigBuilder.getInstance();

	}

	private class WebdavOutputStream extends MonitorOutputStream {
		private final WebdavFileObject file;

		public WebdavOutputStream(final WebdavFileObject file, boolean bAppend) {
			super(new ByteArrayOutputStream());
			if (bAppend) {
				try (InputStream is = file.doGetInputStream()) {
					for (int nextByte = is.read(); nextByte != -1; nextByte = is.read()) {
						super.write(nextByte);
					}
				} catch (Exception e) {
					logger.error(e.getMessage(), e);
				}
			}
			this.file = file;
		}

		private boolean createVersion(final String urlStr) {
			try {
				final VersionControlMethod method = new VersionControlMethod(urlStr);
				setupMethod(method);
				execute(method);
				return true;
			} catch (final Exception ex) {
				return false;
			}
		}

		@Override
		protected void onClose() throws IOException {
			final RequestEntity entity = new ByteArrayRequestEntity(((ByteArrayOutputStream) out).toByteArray());
			final URLFileName fileName = (URLFileName) getName();
			String urlStr;
			urlStr = toUrlString(fileName);
			if (builder.isVersioning(getFileSystem().getFileSystemOptions())) {
				DavPropertySet set = null;
				boolean fileExists = true;
				boolean isCheckedIn = true;
				try {
					set = getPropertyNames(fileName);
				} catch (final FileNotFoundException fnfe) {
					fileExists = false;
				}
				if (fileExists && set != null) {
					if (set.contains(VersionControlledResource.CHECKED_OUT)) {
						isCheckedIn = false;
					} else if (!set.contains(VersionControlledResource.CHECKED_IN)) {
						DavProperty prop = set.get(VersionControlledResource.AUTO_VERSION);
						if (prop != null) {
							prop = getProperty(fileName, VersionControlledResource.AUTO_VERSION);
							if (DeltaVConstants.XML_CHECKOUT_CHECKIN.equals(prop.getValue())) {
								createVersion(urlStr);
							}
						}
					}
				}
				if (fileExists && isCheckedIn) {
					try {
						final CheckoutMethod checkout = new CheckoutMethod(urlStr);
						setupMethod(checkout);
						execute(checkout);
						isCheckedIn = false;
					} catch (final FileSystemException ex) {
						// Ignore the exception checking out.
					}
				}

				try {
					final PutMethod method = new PutMethod(urlStr);
					method.setRequestEntity(entity);
					setupMethod(method);
					execute(method);
					setUserName(fileName, urlStr);
				} catch (final FileSystemException ex) {
					if (!isCheckedIn) {
						try {
							final UncheckoutMethod method = new UncheckoutMethod(urlStr);
							setupMethod(method);
							execute(method);
							isCheckedIn = true;
						} catch (final Exception e) {
							// Ignore the exception. Going to throw original.
						}
						throw ex;
					}
				}
				if (!fileExists) {
					createVersion(urlStr);
					try {
						final DavPropertySet props = getPropertyNames(fileName);
						isCheckedIn = !props.contains(VersionControlledResource.CHECKED_OUT);
					} catch (final FileNotFoundException fnfe) {
						// Ignore the error
					}
				}
				if (!isCheckedIn) {
					final CheckinMethod checkin = new CheckinMethod(urlStr);
					setupMethod(checkin);
					execute(checkin);
				}
			} else {
				final PutMethod method = new PutMethod(urlStr);
				method.setRequestEntity(entity);
				setupMethod(method);
				execute(method);
				try {
					setUserName(fileName, urlStr);
				} catch (final IOException e) {
					// Ignore the exception if unable to set the user name.
				}
			}
			((DefaultFileContent) this.file.getContent()).resetAttributes();
		}

		private void setUserName(final URLFileName fileName, final String urlStr) throws IOException {
			final List<DefaultDavProperty> list = new ArrayList<>();
			String name = builder.getCreatorName(getFileSystem().getFileSystemOptions());
			final String userName = fileName.getUserName();
			if (name == null) {
				name = userName;
			} else {
				if (userName != null) {
					final String comment = "Modified by user " + userName;
					list.add(new DefaultDavProperty(DeltaVConstants.COMMENT, comment));
				}
			}
			list.add(new DefaultDavProperty(DeltaVConstants.CREATOR_DISPLAYNAME, name));
			final PropPatchMethod method = new PropPatchMethod(urlStr, list);
			setupMethod(method);
			execute(method);
		}
	}

	@Override
	protected void doCreateFolder() throws Exception {
		final DavMethod method = new MkColMethod(toUrlString((URLFileName) getName()));
		setupMethod(method);
		try {
			execute(method);
		} catch (final FileSystemException fse) {
			throw new FileSystemException("vfs.provider.webdav/create-collection.error", getName(), fse);
		}
	}

	@Override
	public boolean delete() {
		try {
			doDelete();
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Override
	protected void doDelete() throws Exception {
		final DavMethod method = new DeleteMethod(toUrlString((URLFileName) getName()));
		setupMethod(method);
		execute(method);
	}

	@Override
	protected String[] doListChildren() throws Exception {
		// use doListChildrenResolved for performance
		return null;
	}

	@Override
	protected FileObject[] doListChildrenResolved() throws Exception {
		PropFindMethod method = null;
		try {
			final URLFileName name = (URLFileName) getName();
			if (isDirectory(name)) {
				final DavPropertyNameSet nameSet = new DavPropertyNameSet();
				nameSet.add(DavPropertyName.create(DavConstants.PROPERTY_DISPLAYNAME));

				method = new PropFindMethod(toUrlString(name), nameSet, DavConstants.DEPTH_1);
				setupMethod(method);
				execute(method);
				final List<WebdavFileObject> vfs = new ArrayList<>();
				if (method.succeeded()) {
					final MultiStatusResponse[] responses = method.getResponseBodyAsMultiStatus().getResponses();

					for (final MultiStatusResponse response : responses) {
						if (isCurrentFile(response.getHref(), name)) {
							continue;
						}
						final String resourceName = resourceName(response.getHref());
						if (resourceName != null && resourceName.length() > 0) {
							final WebdavFileObject fo = (WebdavFileObject) FileObjectUtils.getAbstractFileObject(getFileSystem().resolveFile(getFileSystem().getFileSystemManager().resolveName(getName(), UriParser.decode(resourceName), NameScope.CHILD)));
							vfs.add(fo);
						}
					}
				}
				return vfs.toArray(new WebdavFileObject[vfs.size()]);
			}
			throw new FileNotFolderException(getName());
		} catch (final FileNotFolderException fnfe) {
			throw fnfe;
		} catch (final DavException e) {
			throw new FileSystemException(e.getMessage(), e);
		} catch (final IOException e) {
			throw new FileSystemException(e.getMessage(), e);
		} finally {
			if (method != null) {
				method.releaseConnection();
			}
		}
	}

	@Override
	protected void doRename(final FileObject newFile) throws Exception {
		final String url = toUrlString((URLFileName) getName());
		final String dest = toUrlString((URLFileName) newFile.getName(), false);
		final DavMethod method = new MoveMethod(url, dest, false);
		setupMethod(method);
		execute(method);
	}

	@Override
	protected void doSetAttribute(final String attrName, final Object value) throws Exception {
		try {
			final URLFileName fileName = (URLFileName) getName();
			final String urlStr = toUrlString(fileName);
			final DavPropertySet properties = new DavPropertySet();
			final DavPropertyNameSet propertyNameSet = new DavPropertyNameSet();
			final DavProperty property = new DefaultDavProperty(attrName, value, Namespace.EMPTY_NAMESPACE);
			if (value != null) {
				properties.add(property);
			} else {
				propertyNameSet.add(property.getName()); // remove property
			}

			final PropPatchMethod method = new PropPatchMethod(urlStr, properties, propertyNameSet);
			setupMethod(method);
			execute(method);
			if (!method.succeeded()) {
				throw new FileSystemException("Property '" + attrName + "' could not be set.");
			}
		} catch (final FileSystemException fse) {
			throw fse;
		} catch (final Exception e) {
			throw new FileSystemException("vfs.provider.webdav/set-attributes", e, getName(), attrName);
		}
	}

	HeadMethod headMethod(String url) throws IOException {
		HeadMethod method = new HeadMethod(url);
		method.setFollowRedirects(false);
		setupMethod(method);
		execute(method);
		method.releaseConnection();
		return method;
	}

	@SuppressWarnings("finally")
	@Override
	protected FileType doGetType() throws Exception {
		// Use the HEAD method to probe the file.
		int status = 0;
		try {
			status = this.headMethod(toUrlString((URLFileName) getName())).getStatusCode();
		} catch (Exception e) {
			System.out.println(e.getMessage() + e);
			logger.debug(e.getMessage(), e);
			return FileType.IMAGINARY;
		}
		if (status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_BAD_METHOD /* method is bad, but resource exist */) {
			logger.debug("File type of " + ((URLFileName) getName()) + " is " + (isDirectory((URLFileName) getName()) ? FileType.FOLDER : FileType.FILE));
			return isDirectory((URLFileName) getName()) ? FileType.FOLDER : FileType.FILE;
		} else if (status == HttpURLConnection.HTTP_MOVED_TEMP) {
			if (isDirectory((URLFileName) getName())) return FileType.FOLDER;
			logger.debug("File type of " + ((URLFileName) getName()) + " is " + FileType.IMAGINARY);
			logger.debug("HTTP Status Code: " + status);
			return FileType.IMAGINARY;
		} else if (status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_GONE) {
			logger.debug("File type of " + ((URLFileName) getName()) + " is " + FileType.IMAGINARY);
			logger.debug("HTTP Status Code: " + status);
			return FileType.IMAGINARY;
		} else if (status == HttpURLConnection.HTTP_FORBIDDEN) {
			com.nextlabs.vfs.webdav.WebdavFileSystemConfigBuilder builder = (com.nextlabs.vfs.webdav.WebdavFileSystemConfigBuilder) com.nextlabs.vfs.webdav.WebdavFileSystemConfigBuilder.getInstance();
			if (builder.getAuthType(this.getFileSystem().getFileSystemOptions()).equals(AuthType.SHAREPOINT_ONLINE.getName())) {
				reauthenticate();
				return doGetType();
			}
			logger.debug("File type of " + ((URLFileName) getName()) + " is " + FileType.IMAGINARY);
			logger.debug("HTTP Status Code: " + status);
			return FileType.IMAGINARY;
		} else {
			logger.debug("File type of " + ((URLFileName) getName()) + " is " + FileType.IMAGINARY);
			logger.debug("HTTP Status Code: " + status);
			return FileType.IMAGINARY;
			// throw new FileSystemException("vfs.provider.http/head.error", getName(),
			// Integer.valueOf(status));
		}
	}

	@Override
	protected long doGetContentSize() throws Exception {
		return Long.parseLong((String) getProperties((URLFileName) getName()).get(DavConstants.PROPERTY_GETCONTENTLENGTH).getValue());
	}

	@Override
	protected InputStream doGetInputStream() throws Exception {
		final GetMethod getMethod = new GetMethod(toUrlString((URLFileName) getName()));
		// setupMethod(getMethod);
		addCookiesToMethod(getMethod);
		((WebdavFileSystem) fileSystem).getClient().executeMethod(getMethod);
		return new HttpInputStream(getMethod);
	}

	@Override
	protected long doGetLastModifiedTime() throws Exception {
		final Header header = headMethod(toUrlString((URLFileName) getName())).getResponseHeader("last-modified");
		if (header == null) {
			throw new FileSystemException("vfs.provider.http/last-modified.error", getName());
		}
		return DateUtil.parseDate(header.getValue()).getTime();
	}

	protected void execute(final HttpMethod method) throws FileSystemException {
		try {
			addCookiesToMethod(method);

			final int status = ((WebdavFileSystem) fileSystem).getClient().executeMethod(method);
			if (!(method instanceof HeadMethod) && (status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_GONE)) {
				throw new FileNotFoundException(method.getURI());
			}
//			if (method instanceof DavMethodBase) {
//				((DavMethodBase) method).checkSuccess();
//			}
		} catch (final FileSystemException fse) {
			throw fse;
		} catch (final IOException e) {
			throw new FileSystemException(e);
//		} catch (final DavException e) {
//			logger.debug(e.getMessage(), e);
		} finally {
			if (method != null) {
				method.releaseConnection();
			}
		}
	}

	@Override
	protected OutputStream doGetOutputStream(final boolean bAppend) throws Exception {
		return new WebdavOutputStream(this, bAppend);
	}

	DavPropertySet getProperties(final URLFileName name) throws FileSystemException {
		return getProperties(name, DavConstants.PROPFIND_ALL_PROP, new DavPropertyNameSet(), false);
	}

	DavPropertySet getProperties(final URLFileName name, final DavPropertyNameSet nameSet, final boolean addEncoding) throws FileSystemException {
		return getProperties(name, DavConstants.PROPFIND_BY_PROPERTY, nameSet, addEncoding);
	}

	DavPropertySet getProperties(final URLFileName name, final int type, final DavPropertyNameSet nameSet, final boolean addEncoding) throws FileSystemException {
		try {
			final String urlStr = toUrlString(name);
			final PropFindMethod method = new PropFindMethod(urlStr, type, nameSet, DavConstants.DEPTH_0);
			setupMethod(method);
			execute(method);
			if (method.succeeded()) {
				final MultiStatus multiStatus = method.getResponseBodyAsMultiStatus();
				final MultiStatusResponse response = multiStatus.getResponses()[0];
				final DavPropertySet props = response.getProperties(HttpStatus.SC_OK);
				if (addEncoding) {
					final DavProperty prop = new DefaultDavProperty(RESPONSE_CHARSET, method.getResponseCharSet());
					props.add(prop);
				}
				return props;
			}
			return new DavPropertySet();
		} catch (final FileSystemException fse) {
			throw fse;
		} catch (final Exception e) {
			throw new FileSystemException("vfs.provider.webdav/get-property.error", e, getName(), name, type, nameSet.getContent(), addEncoding);
		}
	}

	DavProperty getProperty(final URLFileName fileName, final DavPropertyName name) throws FileSystemException {
		final DavPropertyNameSet nameSet = new DavPropertyNameSet();
		nameSet.add(name);
		final DavPropertySet propertySet = getProperties(fileName, nameSet, false);
		return propertySet.get(name);
	}

	DavProperty getProperty(final URLFileName fileName, final String property) throws FileSystemException {
		return getProperty(fileName, DavPropertyName.create(property));
	}

	DavPropertySet getPropertyNames(final URLFileName name) throws FileSystemException {
		return getProperties(name, DavConstants.PROPFIND_PROPERTY_NAMES, new DavPropertyNameSet(), false);
	}

	private String hrefString(final URLFileName name) {
		final URLFileName newFile = new URLFileName(name.getScheme(), name.getHostName(), name.getScheme() == "https" ? 443 : 80, name.getScheme() == "https" ? 443 : 80, null, null, name.getPath(), name.getType(), name.getQueryString());
		try {
			return newFile.getURIEncoded(this.getUrlCharset());
		} catch (final Exception e) {
			return name.getURI();
		}
	}

	private boolean isCurrentFile(final String href, final URLFileName fileName) {
		String name = hrefString(fileName);
		if (href.endsWith("/") && !name.endsWith("/")) {
			name += "/";
		}
		return href.equals(name) || href.equals(fileName.getPath());
	}

	private boolean isDirectory(final URLFileName name) throws IOException {
		try {
			final DavProperty property = getProperty(name, DavConstants.PROPERTY_RESOURCETYPE);
			Node node;
			if (property != null && (node = (Node) property.getValue()) != null) {
				return node.getLocalName().equals(DavConstants.XML_COLLECTION);
			}
			return false;
		} catch (final FileNotFoundException fse) {
			throw new FileNotFolderException(name);
		}
	}

	private String resourceName(String path) {
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		final int i = path.lastIndexOf("/");
		return i >= 0 ? path.substring(i + 1) : path;
	}

	@Override
	protected void setupMethod(final HttpMethod method) throws FileSystemException, URIException {
		if (!(method instanceof DavMethod)) {
			final String pathEncoded = ((URLFileName) getName()).getPathQueryEncoded(this.getUrlCharset());
			method.setPath(pathEncoded);
		}
		method.setRequestHeader("User-Agent", "Jakarta-Commons-VFS");
		method.addRequestHeader("Cache-control", "no-cache");
		method.addRequestHeader("Cache-store", "no-store");
		method.addRequestHeader("Pragma", "no-cache");
		method.addRequestHeader("Expires", "0");
	}

	protected String toUrlString(final URLFileName name) {
		try {
			return toUrlString(name, true);
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
		}
		return "";
	}

	protected String toUrlString(final URLFileName name, final boolean includeUserInfo) {
		String user = null;
		String password = null;
		if (includeUserInfo) {
			user = name.getUserName();
			password = name.getPassword();
		}
		final URLFileName newFile = new URLFileName(name.getScheme(), name.getHostName(), name.getPort(), name.getDefaultPort(), user, password, name.getPath(), name.getType(), name.getQueryString());
		try {
			return newFile.getURIEncoded(this.getUrlCharset());
		} catch (Exception e) {
			return name.getURI();
		}
	}

	protected void addCookiesToMethod(final HttpMethod method) throws URIException {
		method.getParams().setCookiePolicy(CookiePolicy.IGNORE_COOKIES);
		Cookie[] cookies = ((WebdavFileSystem) fileSystem).getClient().getState().getCookies();
		if (cookies.length > 0) {
			String cookieString = "";
			for (Cookie c : cookies) {
				// logger.debug(c.getDomain());
				// logger.debug(method.getURI().getHost());
				if (c.getDomain().equals(method.getURI().getHost())) {
					cookieString = cookieString + c.getName() + "=" + c.getValue() + "; ";
				}
			}
			if (cookieString.length() > 0) {
				// logger.debug(cookieString);
				method.addRequestHeader("Cookie", cookieString);
			}
		} else {
//			logger.debug("No Authentication Cookies to add.");
		}

	}
	
	protected void reauthenticate() throws FileSystemException, InterruptedException {
		com.nextlabs.vfs.webdav.WebdavFileSystemConfigBuilder builder = (com.nextlabs.vfs.webdav.WebdavFileSystemConfigBuilder) com.nextlabs.vfs.webdav.WebdavFileSystemConfigBuilder.getInstance();
		Triple<String, String, String> creds = builder.getCredentials(this.getFileSystem().getFileSystemOptions());
		Pair<String, String> cookies = null;
		int trycount = 0;
		while (trycount < 5) {
			SharepointOnline.getCookies(creds.getLeft(), creds.getMiddle(), creds.getRight());
			if (cookies != null && !cookies.getLeft().isEmpty() && !cookies.getRight().isEmpty()) {
				((WebdavFileSystem) fileSystem).getClient().getState().clearCookies();
				((WebdavFileSystem) fileSystem).getClient().getState().addCookies(new Cookie[] { new Cookie(creds.getLeft() + ".sharepoint.com", "rtFa", cookies.getLeft()), new Cookie(creds.getLeft() + ".sharepoint.com", "FedAuth", cookies.getRight()) });
				return;
			} else {
				logger.debug("Unable to retrieve cookies for Sharepoint Online Credentials: [" + creds.getLeft() + ", " + creds.getMiddle() + "]");
				logger.debug("Retrying in 3s...");
				Thread.sleep(3000L);
				trycount++;
			}
		}
		throw new FileSystemException("Unable to retrieve cookies for Sharepoint Online Credentials: [" + creds.getLeft() + ", " + creds.getMiddle() + "] after 5 tries.");
	}
}
