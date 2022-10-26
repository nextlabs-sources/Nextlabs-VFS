package com.nextlabs.vfs.webdav;

import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.provider.GenericFileName;
import org.apache.commons.vfs2.util.UserAuthenticatorUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.nextlabs.vfs.authentication.NTLMv2Scheme;
import com.nextlabs.vfs.authentication.SharepointOnline;

import org.apache.commons.vfs2.UserAuthenticationData;
import org.apache.commons.vfs2.provider.http.HttpClientFactory;

public class WebdavFileProvider extends org.apache.commons.vfs2.provider.webdav.WebdavFileProvider {

	private static final Logger logger = LogManager.getLogger(WebdavFileProvider.class);

	public WebdavFileProvider() {
		super();
	}

	@Override
	protected FileSystem doCreateFileSystem(final FileName name, final FileSystemOptions fileSystemOptions) throws FileSystemException {
		// Create the file system
		final GenericFileName rootName = (GenericFileName) name;
		final FileSystemOptions fsOpts = fileSystemOptions == null ? new FileSystemOptions() : fileSystemOptions;
		final WebdavFileSystemConfigBuilder builder = WebdavFileSystemConfigBuilder.getInstance();

		UserAuthenticationData authData = null;
		HttpClient httpClient;
		try {
			authData = UserAuthenticatorUtils.authenticate(fsOpts, AUTHENTICATOR_TYPES);
			logger.debug("Initializing HttpClient...");
			httpClient = HttpClientFactory.createConnection(org.apache.commons.vfs2.provider.webdav.WebdavFileSystemConfigBuilder.getInstance(), name.getScheme(), rootName.getHostName(), name.getScheme() == "https" ? 443 : 80,
					UserAuthenticatorUtils.toString(UserAuthenticatorUtils.getData(authData, UserAuthenticationData.USERNAME, UserAuthenticatorUtils.toChar(rootName.getUserName()))),
					UserAuthenticatorUtils.toString(UserAuthenticatorUtils.getData(authData, UserAuthenticationData.PASSWORD, UserAuthenticatorUtils.toChar(rootName.getPassword()))), fsOpts);

			if (builder.getAuthType(fsOpts) == "NTLM") {
				if ("true".equals(System.getProperty("enable_debugging"))) {
					System.out.println("Configuring HttpClient for NTLM Authentication...");
				}
				AuthPolicy.registerAuthScheme(AuthPolicy.NTLM, NTLMv2Scheme.class);
				Triple<String, String, String> credentials = builder.getCredentials(fsOpts);
				if ("true".equals(System.getProperty("enable_debugging"))) {
					System.out.println("NTLM Credentials = [" + credentials.getMiddle() + "," + credentials.getRight() + "," + rootName.getHostName() + "," + credentials.getLeft() + "]");
				}
				NTCredentials creds = new NTCredentials(credentials.getMiddle(), credentials.getRight(), "", rootName.getHostName());
				httpClient.getState().setCredentials(AuthScope.ANY, creds);
				httpClient.getParams().setAuthenticationPreemptive(true);
			} else if (builder.getAuthType(fsOpts) == "Sharepoint Online") {
				com.nextlabs.vfs.webdav.WebdavFileSystemConfigBuilder builder2 = (com.nextlabs.vfs.webdav.WebdavFileSystemConfigBuilder) com.nextlabs.vfs.webdav.WebdavFileSystemConfigBuilder.getInstance();
				Triple<String, String, String> creds = builder.getCredentials(fsOpts);
				Pair<String, String> cookies = SharepointOnline.getCookies(creds.getLeft(), creds.getMiddle(), creds.getRight());
				if (cookies != null && !cookies.getLeft().isEmpty() && !cookies.getRight().isEmpty()) {
					httpClient.getState().addCookies(new Cookie[] { new Cookie(creds.getLeft() + ".sharepoint.com", "rtFa", cookies.getLeft()), new Cookie(creds.getLeft() + ".sharepoint.com", "FedAuth", cookies.getRight()) });
				} else {
					logger.debug("Unable to retrieve authentication cookies for Sharepoint Online Credentials: [" + creds.getLeft() + ", " + creds.getMiddle() + "]");
				}
			}
		} finally {
			UserAuthenticatorUtils.cleanup(authData);
		}

		return new WebdavFileSystem(rootName, httpClient, fsOpts);
	}
}