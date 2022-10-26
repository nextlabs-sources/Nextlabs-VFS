package com.nextlabs.vfs;

import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.apache.commons.vfs2.impl.DefaultFileSystemManager;
import org.apache.commons.vfs2.provider.FileProvider;
import org.apache.commons.vfs2.provider.LocalFileProvider;
import org.apache.commons.vfs2.provider.http.HttpFileSystemConfigBuilder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.vfs2.CacheStrategy;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.auth.StaticUserAuthenticator;
import org.apache.commons.vfs2.cache.NullFilesCache;
import org.apache.commons.vfs2.FileSystemException;

import com.nextlabs.vfs.authentication.SharepointOnline;
import com.nextlabs.vfs.azure.blob.AzBlobProvider;
import com.nextlabs.vfs.azure.file.AzFileProvider;
import com.nextlabs.vfs.constant.AuthType;
import com.nextlabs.vfs.constant.RepositoryType;
import com.nextlabs.vfs.dto.Repository;
import com.nextlabs.vfs.dto.RepositoryCredentials;
import com.nextlabs.vfs.smb.SmbFileProvider;
import com.nextlabs.vfs.webdav.WebdavFileProvider;
import com.nextlabs.vfs.webdav.WebdavFileSystemConfigBuilder;

import jcifs.context.SingletonContext;

/*
 * This subclass of the VFS FileSystemManager handles SmartClassifier specific mappings
 * for repository authentication as well as handling varying file systems across multiple repositories.
 * 
 * The intention is to have a single universal class that will handle all file operations regardless of
 * authentication process or file access protocol based solely upon the URI of the file.
 * 
 * The URI of the file is used to identify which repository it belongs to and hence determines which
 * authentication process as well as the correct protocol to use to access the file.
 * 
 * By abstracting out file/directory access protocols and authentication processes, SmartClassifier should
 * in theory be able to handle *any* file repository once the authentication process and access protocols
 * have been correctly implemented.
 * 
 * In most cases where a standard protocol is concerned, they should already be implemented in the
 * Apache Commons VFS library, so this abstraction minimizes developer efforts expended on
 * re-implementing file access procedures for each new repository type.
 * 
 * See https://commons.apache.org/proper/commons-vfs/filesystems.html for a list of file systems supported
 * by Apache Commons VFS.
 * 
 * - Theodore Lee
 * */
public class RepositoryFileSystemManager extends DefaultFileSystemManager {

	FileSystemOptions fso;
	protected static Map<String, RepositoryCredentials> authStore;
	
	static Map<Triple<String, String, String>, FileSystemOptions> SPOCredentialCache;
	static Map<FileSystemOptions, Long> SPOCredentialLastUpdate;
	static final Long SPOCredsExpiryTime = 3600000L; //1 hour in milliseconds
	
	static Map<String, RepositoryType> repoTypeMapping;

	public RepositoryFileSystemManager() throws FileSystemException {
		super();
		if ("true".equals(System.getProperty("enable_debugging"))) {
			System.out.println("RepositoryFileSystemManager constructor begins");
		}
		this.setFilesCache(new NullFilesCache());
		this.setCacheStrategy(CacheStrategy.ON_CALL);
		SPOCredentialCache = new HashMap<Triple<String, String, String>, FileSystemOptions>();
		SPOCredentialLastUpdate = new HashMap<FileSystemOptions, Long>();
		authStore = new HashMap<String, RepositoryCredentials>();
		repoTypeMapping = new HashMap<String, RepositoryType>();
		fso = new FileSystemOptions();

		this.addProvider(RepositoryType.SHARED_FOLDER.getName(), new SmbFileProvider());
		this.addProvider(RepositoryType.SHAREPOINT.getName(), new WebdavFileProvider());
		this.addProvider(RepositoryType.AZURE_FILE_STORAGE.getName(), new AzFileProvider());
		this.addProvider(RepositoryType.AZURE_BLOB_STORAGE.getName(), new AzBlobProvider());
		if ("true".equals(System.getProperty("enable_debugging"))) {
			System.out.println("RepositoryFileSystemManager constructor ends");
		}
		if ("true".equals(System.getProperty("enable_debugging"))) {
			this.printSingletonContextProperties();
		}
	}
	
	public void printSingletonContextProperties() {
		SingletonContext ctx = SingletonContext.getInstance();
		System.out.println("Checking Singleton Context properties...");
		System.out.println("getResolveOrder" + "=" + ctx.getConfig().getResolveOrder());
		System.out.println("isDfsDisabled" + "=" + ctx.getConfig().isDfsDisabled());
		System.out.println("getMinimumVersion" + "=" + ctx.getConfig().getMinimumVersion());
		System.out.println("getMaximumVersion" + "=" + ctx.getConfig().getMaximumVersion());
		System.out.println("getConnTimeout" + "=" + ctx.getConfig().getConnTimeout());
		System.out.println("getSoTimeout" + "=" + ctx.getConfig().getSoTimeout());
		System.out.println("getResponseTimeout" + "=" + ctx.getConfig().getResponseTimeout());
		System.out.println("getSessionTimeout" + "=" + ctx.getConfig().getSessionTimeout());
		System.out.println("isIpcSigningEnforced" + "=" + ctx.getConfig().isIpcSigningEnforced());
		System.out.println("isUseSMB2OnlyNegotiation" + "=" + ctx.getConfig().isUseSMB2OnlyNegotiation());
		System.out.println("isPort139FailoverEnabled" + "=" + ctx.getConfig().isPort139FailoverEnabled());
	}
	
	public static void addRepository(Repository repo) {
		String encodedRepoPath = urlEncode(repo.getPath()).toLowerCase();
		repoTypeMapping.put(encodedRepoPath, repo.getType());
		authStore.put(encodedRepoPath, repo.getCreds());
	}
	
	public static void setCredentials(String repoPath, RepositoryCredentials creds) throws IllegalArgumentException {
		String uriEncoded = urlEncode(repoPath).toLowerCase();
		if (repoTypeMapping.containsKey(uriEncoded)) authStore.put(uriEncoded, creds);
		else throw new IllegalArgumentException("Cannot set credentials on repository that does not exist");
	}
	
	public static void setAllCredentials(Map<String, RepositoryCredentials> credsMap) throws IllegalArgumentException {
		credsMap.entrySet().stream().forEach(entry -> { 
			try {
				setCredentials(entry.getKey() , entry.getValue()); 
			} catch (IllegalArgumentException e) {
				throw e;
			}
		});
	}
	
	public static RepositoryCredentials getCredentials(String uri) {
		String uriEncoded = urlEncode(uri).toLowerCase();
		return authStore.get(getRepoPath(uriEncoded));
	}
	
	private static String getRepoPath(String fileUri) {
		String uriEncoded = urlEncode(fileUri).toLowerCase();
		return authStore.keySet().stream().filter(path -> uriEncoded.startsWith(path)).findAny().orElse(null);
	}
	
	private static RepositoryType getRepoType(String fileUri) {
		return repoTypeMapping.get(getRepoPath(fileUri));
	}

	@Override
	public FileObject resolveFile(String uri) throws FileSystemException {
		RepositoryCredentials sa = getCredentials(uri);
		if ("true".equals(System.getProperty("enable_debugging"))) {
			System.out.println("RepositoryCredentials" + "=" + sa.toString());
		}
		setupRepositoryAuthentication(sa);
		return resolveFile(uri, fso);
	}
	
	public FileObject resolveFile(String uri, RepositoryCredentials rc, RepositoryType type) throws FileSystemException {
		if ("true".equals(System.getProperty("enable_debugging"))) {
			System.out.println("uri" + "=" + uri);
		}
		String uriEncoded = urlEncode(uri).toLowerCase();
		if ("true".equals(System.getProperty("enable_debugging"))) {
			System.out.println("uriEncoded" + "=" + uriEncoded);
		}
		repoTypeMapping.put(uriEncoded, type);
		setCredentials(uriEncoded, rc);
		return resolveFile(uri);
	}

	@Override
	public FileObject resolveFile(final String uri, final FileSystemOptions fileSystemOptions) throws FileSystemException {
		return resolveFile(getBaseFile(), uri, fileSystemOptions);
	}

	@Override
	public FileObject resolveFile(final FileObject baseFile, final String uri) throws FileSystemException {
		return resolveFile(baseFile, uri, baseFile == null ? null : baseFile.getFileSystem().getFileSystemOptions());
	}

	@Override
	@SuppressWarnings("unchecked")
	public FileObject resolveFile(final FileObject baseFile, final String uri, final FileSystemOptions fileSystemOptions) throws FileSystemException {
		FileObject foundFile = null;
		try {
			if ("true".equals(System.getProperty("enable_debugging"))) {
				if (baseFile != null) {
					System.out.println("baseFile" + " = " + baseFile.toString());
				} else {
					System.out.println("baseFile" + " = " + null);
				}
				System.out.println("uri" + " = " + uri);
			}
			
			final FileObject realBaseFile;
			if (baseFile != null && VFS.isUriStyle() && baseFile.getName().isFile()) {
				realBaseFile = baseFile.getParent();
			} else {
				realBaseFile = baseFile;
			}
			if ("true".equals(System.getProperty("enable_debugging"))) {
				if (realBaseFile != null) {
					System.out.println("realBaseFile" + " = " + realBaseFile.toString());
				} else {
					System.out.println("realBaseFile" + " = " + null);
				}
			}
			
			if (uri == null) {
				throw new IllegalArgumentException();
			}
	
			Map<String, FileProvider> providers = null;
			// All this nonsense just because "providers" is a private field in the superclass
			
			// The reason why I have to use providers from the superclass and not just create
			// a local mapping for repository types to providers is because Apache VFS
			// has some internal logic for initializing each file system and does it on each
			// file system when fsMgr.init() is called
			
			// It's simply easier to reuse the providers field in the superclass than to try to
			// reroute the initialization logic into the subclass
			try {
				Class<?> c = DefaultFileSystemManager.class;
				Field field = c.getDeclaredField("providers");
				field.setAccessible(true);
				providers = (Map<String, FileProvider>) field.get(this);
			} catch (Throwable t) {
				t.printStackTrace();
			}
			
			FileProvider provider = providers.get(getRepoType(uri).getName());
			if ("true".equals(System.getProperty("enable_debugging"))) {
				System.out.println("getRepoType(uri).getName" + " = " + getRepoType(uri).getName());
			}
			foundFile = provider.findFile(realBaseFile, uri, fileSystemOptions);
			if ("true".equals(System.getProperty("enable_debugging"))) {
				if (foundFile != null) {
					System.out.println("foundFile" + " = " + foundFile.toString());
				} else {
					System.out.println("foundFile" + " = " + null);
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return foundFile;
	}

	protected void setSharepointOnlineCredentials(String domain, String username, String password) throws FileSystemException {
		fso = new FileSystemOptions();
		WebdavFileSystemConfigBuilder builder = WebdavFileSystemConfigBuilder.getInstance();
		builder.setCredentials(fso, domain, username, password);
		builder.setAuthType(fso, AuthType.SHAREPOINT_ONLINE.getName());
	}
	
	protected void setCifsCredentials(String domain, String username, String password) throws FileSystemException {
		fso = new FileSystemOptions();
		StaticUserAuthenticator auth = new StaticUserAuthenticator(domain, username, password);
		DefaultFileSystemConfigBuilder.getInstance().setUserAuthenticator(fso, auth);
	}

	protected void setWindowsCredentials(String domain, String username, String password) throws FileSystemException {
		fso = new FileSystemOptions();
		WebdavFileSystemConfigBuilder builder = WebdavFileSystemConfigBuilder.getInstance();
		builder.setCredentials(fso, domain, username, password);
		builder.setAuthType(fso, AuthType.HTTP_NTLM.getName());
	}

	protected void setBasicAuthCredentials(String username, String password) throws FileSystemException {
		fso = new FileSystemOptions();
		StaticUserAuthenticator auth = new StaticUserAuthenticator("", username, password);
		DefaultFileSystemConfigBuilder.getInstance().setUserAuthenticator(fso, auth);
		HttpFileSystemConfigBuilder.getInstance().setPreemptiveAuth(fso, true);
		HttpFileSystemConfigBuilder.getInstance().setProxyAuthenticator(fso, auth);
	}
	
	protected void setAzureCredentials(String accName, String accKey) throws FileSystemException {
		fso = new FileSystemOptions();
		StaticUserAuthenticator auth = new StaticUserAuthenticator("", accName, accKey);
		DefaultFileSystemConfigBuilder.getInstance().setUserAuthenticator(fso, auth); 
	}

	protected void setupRepositoryAuthentication(RepositoryCredentials sa) throws FileSystemException {
		if (sa != null) {
			if (sa.getType() == AuthType.SHAREPOINT_ONLINE) setSharepointOnlineCredentials(sa.getDomain(), sa.getUserName(), sa.getPassword());
			else if (sa.getType() == AuthType.HTTP_BASIC) setBasicAuthCredentials(sa.getUserName(), sa.getPassword());
			else if (sa.getType() == AuthType.HTTP_NTLM) setWindowsCredentials(sa.getDomain(), sa.getUserName(), sa.getPassword());
			else if (sa.getType() == AuthType.CIFS) setCifsCredentials(sa.getDomain(), sa.getUserName(), sa.getPassword());
			else if (sa.getType() == AuthType.AZURE) setAzureCredentials(sa.getUserName(), sa.getPassword());
		}
	}
	
	public static String urlEncode(String path) {
		if (path != null) {
			String encoded = path.replace('\\', '/');
			// Remove the '/' at the end if present
			encoded = encoded.charAt(encoded.length() - 1) == '/' ? encoded.substring(0, encoded.length() - 1) : encoded;
			// If there's no protocol, start with file: instead
			encoded = (encoded.startsWith("//") ? "file:" : "") + encoded;
			return encoded;
		}
		return null;
	}
}
