package com.nextlabs.vfs.test.integration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;

import com.nextlabs.common.io.IOUtils;
import com.nextlabs.vfs.RepositoryFileSystemManager;
import com.nextlabs.vfs.constant.AuthType;
import com.nextlabs.vfs.constant.RepositoryType;
import com.nextlabs.vfs.dto.RepositoryCredentials;

import static org.junit.Assert.*;

public class IntegrationTest {
	private static RepositoryFileSystemManager fsMgr;
	
	private static final int CRAWL_TEST_DEPTH_LIMIT = 10;
	
	private static final ExecutorService es = Executors.newFixedThreadPool(128);
	
	public static void main (String[] args) throws Exception {
		fsMgr = new RepositoryFileSystemManager();
		fsMgr.init();
		
		RepositoryCredentials cifs = new RepositoryCredentials("nextlabs", "thlee", "nextlabs3!", AuthType.CIFS);
//		RepositoryCredentials azsa = new RepositoryCredentials("", "cdcstorage01", "v2oCBWgA70i4P+SPcTTI9Qz+ed9RHZ5maC4OV3x+/K1n6trMWa9FgNOeHQD9uMyAMoSdcatCU4lY5j2Yui+Csw==", AuthType.AZURE);
//		RepositoryCredentials spsa = new RepositoryCredentials("nextlabs", "thlee@nextlabs.com", "nextlabs3!", AuthType.SHAREPOINT_ONLINE);
//		RepositoryCredentials spop = new RepositoryCredentials("qapf1.qalab01.nextlabs.com", "abraham.lincoln", "abraham.lincoln", AuthType.HTTP_NTLM);
//		RepositoryCredentials spsa2 = new RepositoryCredentials("nextlabstest", "jimmy.carter@nextlabstest.onmicrosoft.com", "123blue!", AuthType.SHAREPOINT_ONLINE);
//		SourceAuthenticationDTO spsa2 = new SourceAuthenticationDTO("nextlabs2", "jimmy.carter@nextlabs2.onmicrosoft.com", "123Blue!", "Sharepoint Online");
//		SourceAuthenticationDTO spop = new SourceAuthenticationDTO("nextlabs", "abraham.lincoln", "abraham.lincoln", "NTLM");
		
//		crawlTest("https://nextlabstest.sharepoint.com/library1", spsa2, RepositoryType.SHAREPOINT);
		
		dirIOTest("//semakau/share/Users/thlee", cifs, RepositoryType.SHARED_FOLDER);
//		crawlTest("//semakau/share/Users/thlee", cifs, RepositoryType.SHARED_FOLDER);
//		dirIOTest("https://cdcstorage01.file.core.windows.net/fileshare01", azsa, RepositoryType.AZURE_FILE_STORAGE);
//		dirIOTest("https://nextlabs.sharepoint.com/sites/smartc/Shared Documents", spsa, RepositoryType.SHAREPOINT);
//		dirIOTest("https://nextlabs2.sharepoint.com/library1", spsa2);
				
//		dirIOTest("https://tleesp01w12r2.qapf1.qalab01.nextlabs.com/Documents", spop, RepositoryType.SHAREPOINT);
		
//		FileObject file = fsMgr.resolveFile("https://nextlabs.sharepoint.com/sites/smartc/Shared Documents/CC 8 Design Issues.docx", spsa, RepositoryType.SHAREPOINT);
//		System.out.println(file.getParent().getName().getPath());
//		System.out.println(file.exists());
//		FileObject backupFile = fsMgr.resolveFile("https://nextlabs.sharepoint.com/sites/smartc/Shared Documents/~$CC 8 Design Issues.docx.bak", spsa, RepositoryType.SHAREPOINT);
//		backupFile.createFile();
//		System.out.println(backupFile.exists());
//		backupFile.copyFrom(file, new AllFileSelector());
//		printHexStreamFromByteArrayRead(file.getContent().getInputStream(), 64);
//		System.out.println("========================================");
//		printHexStreamFromByteArrayRead(backupFile.getContent().getInputStream(), 64);
		
//		
//		System.out.println("=======================================================");
//		
//		FileObject file2 = fsMgr.resolveFile("//semakau/share/Users/thlee/test.txt.nxl", cifs, RepositoryType.SHARED_FOLDER);
//		try (InputStream is = file2.getContent().getInputStream()) {
//			printHexStreamFromByteArrayRead(is, 64);
//		}
		
//		FileObject file = fsMgr.resolveFile("https://nextlabs.sharepoint.com/sites/smartc/Shared Documents/!@$^&(&)(_)(&^$#$@!@$^&(&)(_)(&^$#$@", spsa);
//		file.delete();
//		
		
//		crawlTest("https://cdcstorage01.file.core.windows.net/fileshare01/SmartClassifierTest", azsa);
//		
//		crawlTest("https://nextlabs2.sharepoint.com/library1", spsa2);
//		crawlTest("https://nextlabs2.sharepoint.com/subsite1/library2", spsa2);
//		crawlTest("https://nextlabs2.sharepoint.com/subsite2/library3", spsa2);
//		crawlTest("https://nextlabs2.sharepoint.com/subsite2/Lists/list3", spsa2);
//		crawlTest("https://nextlabs2.sharepoint.com/sitecollection1/library4", spsa2);
//		crawlTest("https://nextlabs2.sharepoint.com/sitecollection1/Lists/list4", spsa2);
//		crawlTest("https://nextlabs2.sharepoint.com/sitecollection1/subsite3/library5", spsa2);
//		crawlTest("https://nextlabs2.sharepoint.com/sitecollection1/subsite3/Lists/list5", spsa2);
//		crawlTest("https://nextlabs2.sharepoint.com/sitecollection1/subsite4/library6", spsa2);
//		crawlTest("https://nextlabs2.sharepoint.com/sitecollection1/subsite4/Lists/list 6", spsa2);
		
		es.shutdown();
	}
	
	private static void crawlTest(String rootFolderURI, RepositoryCredentials sa, RepositoryType type) throws FileSystemException, InterruptedException, ExecutionException {
		System.out.println("Crawling " + rootFolderURI);
		@SuppressWarnings("unchecked")
		Future<Void> testResult = (Future<Void>) es.submit(() -> {
			try {
				crawlTest(fsMgr.resolveFile(rootFolderURI, sa, type), CRAWL_TEST_DEPTH_LIMIT);
			} catch (FileSystemException | InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		});
		try {
			testResult.get(600L, TimeUnit.SECONDS);
			System.out.println("Successfully ran crawlTest on : " + rootFolderURI);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			e.printStackTrace();
			System.out.println("Crawl Test for: " + rootFolderURI + " FAILED!");
		}
		
	}
	
	private static void crawlTest(FileObject f, int depthLimit) throws FileSystemException, InterruptedException, ExecutionException {
		FileType type = await(f::getType, null);
		StringBuffer buf = new StringBuffer();
		buf.append(f.toString() + "::" + type);
		if (type == FileType.FOLDER) {
			System.out.println(buf.toString());
			if (depthLimit > 0) {
				for (FileObject child : await(f::getChildren, new FileObject[] {})) {
					crawlTest(child, depthLimit -1);
				}
			}
		} else if (type == FileType.FILE) {
			FileContent content = await(f::getContent);
			//Long modifiedTime = await(content::getLastModifiedTime, null);
			//buf.append(" Last Modified: " +  modifiedTime);
			System.out.println(buf.toString());
		} else if (type == FileType.IMAGINARY) {
			System.out.println(buf.toString());
		}
	}
	
	private static void fileIOTest(FileObject dir) throws IOException, InterruptedException {
		if (dir.getType() == FileType.FOLDER) {
			//Initialize the child FileObject
			String testFileName = "~$!@#^R&^ T^&(&)(U(&^^^+_098]['!@#^%R&^ T^&(&)(U(&^^^+_098]['.txt";
			FileObject file = dir.resolveFile(testFileName);
			
			//Check that it doesn't already exist
			assertTrue("Test file already exists.", !file.exists());
			
			//Create a file in the current directory
			System.out.println("Creating test file...");
			file.createFile();
			
			//Check that the directory contains the file
			assertTrue("File creation failed.", file.getType() == FileType.FILE);
			
			//Verify file name
			String url = file.toString();
			String expectedUrl = dir.toString() + "/" + testFileName;
			
			System.out.println("Verifying name of created file...");
			assertTrue("Invalid file name, expected " + expectedUrl + ", got " + url, url.equals(expectedUrl));
			
			System.out.println("File name verified. Verifying file can be resolved from parent...");
			FileObject child = Arrays.stream(dir.getChildren()).filter(e -> e.toString().equals(expectedUrl)).findFirst().orElse(null);
			try {
				assertNotNull("Could not resolve child object from parent directory.", child);
			} catch (AssertionError e) {
				System.out.println(e.getMessage());
				System.out.println("Got the following child file objects instead: " );
				for (FileObject c : dir.getChildren()) {
					System.out.println(c.toString());
				}
			}

			
			//Store current last modified time to assert that it has changed after file modification
			System.out.println("File successfully created and verified. Storing current last modified time...");
			long lastModified = file.getContent().getLastModifiedTime();
			
			//Try to write to the blank file - write to the output stream twice to check if the OutputStream can handle multiple calls to OutputStream.write
			System.out.println("Last modified time: " + lastModified + ". Attempting to write to file via OutputStream...");
			String testString = "TESTSTRING!@#@%$##%@^$";
			String expectedFileContent = "";
			try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(file.getContent().getOutputStream(), StandardCharsets.UTF_8))) {
				pw.println(testString); expectedFileContent += testString + "\r\n";
				pw.println(testString); expectedFileContent += testString + "\r\n";
			}
			
			//Check that file content is as expected
			System.out.println("Successfully wrote to file. Verifying file content...");
			try (InputStream is = file.getContent().getInputStream()) {
				String fileContent = (IOUtils.toString(is, StandardCharsets.UTF_8));
				assertTrue("Write to OutputStream failed.\nCurrent file content:\n" + fileContent + "\nExpected file content:\n" + expectedFileContent, fileContent.equals(expectedFileContent));
			} catch (Exception e) {
				throw e;
			}
			
			// Verify file size
			System.out.println("File content verified. Verifying file size...");
			assertTrue("File size was incorrect.", file.getContent().getSize() == expectedFileContent.length());
			
			//Get new last modified time and check that it has changed after file modification
			System.out.println("File size verified. Verifying that modified time has changed...");
			long newLastModified = file.getContent().getLastModifiedTime();
			assertTrue("Last modified time did not change even though file was modified.", newLastModified != lastModified);
			System.out.println("New last modified time: " + newLastModified);
			lastModified = newLastModified;
			
			//Try to append to the end of the file
			System.out.println("Verified that last modified time changed. Attempting to append to end of file using new OutputStream...");
			try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(file.getContent().getOutputStream(true), StandardCharsets.UTF_8))) { pw.println(testString + "2"); expectedFileContent += testString + "2\r\n"; }
			
			//Check that file content is as expected
			System.out.println("Successfully wrote to OutputStream. Verifying file content...");
			try (InputStream is = file.getContent().getInputStream()) {
				String fileContent = IOUtils.toString(is, StandardCharsets.UTF_8);
				assertTrue("Append to end of file with OutputStream failed.\nCurrent file content:\n" + fileContent + "\nExpected file content:\n" + expectedFileContent, fileContent.equals(expectedFileContent));
			} catch (Exception e) {
				throw e;
			}
			
			// Write a significant amount of data to the file (~16MB) in chunks of 16 bytes
			System.out.println("File content verified. Overwriting file with ~4MB of test data...");
			int chunks = 250000; byte[] expectedFileContentBytes = new byte[16*chunks];
			try (OutputStream os = file.getContent().getOutputStream()) {
				int pos = 0; byte[] bytes = new byte[16]; Random randomGenerator = new Random();
				for (int i=0; i<chunks; i++) {
					randomGenerator.nextBytes(bytes);
					if (i > 0 && i%1000 == 0) os.flush();
					os.write(bytes); System.arraycopy(bytes, 0, expectedFileContentBytes, pos, 16); pos+=16;
				}
			}
			
			// Verify file size again
			System.out.println("Write complete. Verifying file size again...");
			long fileSize = file.getContent().getSize();
			assertTrue("File size was incorrect.", fileSize == expectedFileContentBytes.length);
			
			// Test single byte read from input stream
			System.out.println("File size verified. Verifying InputStream.read() method works as expected...");
			byte[] fileContent = new byte[(int) fileSize];
			try (InputStream is = file.getContent().getInputStream()) {
				int byteIndex = 0;
				for (int i = is.read(); i != -1; i = is.read()) {
					fileContent[byteIndex++] = (byte) i;
				}
			}
			assertTrue("File content incorrect. \n==========Expected file content: ==========\n" + hexReprFromByteArray(expectedFileContentBytes, 64) + "\n==========Actual file content: ==========\n" + hexReprFromByteArray(fileContent, 64), Arrays.equals(expectedFileContentBytes, fileContent));
			
			// Test 128-byte chunk reads from input stream
			System.out.println("InputStream.read() behaviour verified. Verifying InputStream.read(byte[] b) method works as expected...");
			fileContent = new byte[(int) fileSize];
			try (InputStream is = file.getContent().getInputStream()) {
				byte[] b = new byte[128]; int count = 0;
				for (int i = is.read(b); i != -1; i = is.read(b)) {
					System.arraycopy(b, 0, fileContent, count++*128, i);
				}
			}
			assertTrue("File content incorrect. \n==========Expected file content: ==========\n" + hexReprFromByteArray(expectedFileContentBytes, 64) + "\n==========Actual file content: ==========\n" + hexReprFromByteArray(fileContent, 64), Arrays.equals(expectedFileContentBytes, fileContent));
			
			//Update last modified time
			System.out.println("InputStream.read(byte[] b) behaviour verified. Verifying that last modified time does not change without file modification...");
			newLastModified = file.getContent().getLastModifiedTime();
			lastModified = newLastModified;
			
			//Wait 2 seconds and check that last modified time has not changed
			Thread.sleep(2000);
			newLastModified = file.getContent().getLastModifiedTime();
			assertTrue("Last modified time changed even though file was not modified", newLastModified == lastModified);
			lastModified = newLastModified;
			
			//Delete the file
			InputStream dangler = file.getContent().getInputStream();
			System.out.println("Successfully verified behaviour of last modified time. Deleting test file...");
			file.close();
			file.delete();
			
			//Check that the file has been deleted
			assertTrue("File delete failed.", !file.exists());
		} else {
			throw new FileSystemException("fileIOTest must be called with a directory FileObject");
		}
	}
	
	private static void dirIOTest(String rootFolderURI, RepositoryCredentials sa, RepositoryType type) {
		System.out.println("Performing dirIOTest on directory " + rootFolderURI + " of type " + type.getName());
		@SuppressWarnings("unchecked")
		Future<Void> testResult = (Future<Void>) es.submit(() -> {
			try {
				dirIOTest(fsMgr.resolveFile(rootFolderURI, sa, type));
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});
		try {
			testResult.get(300L, TimeUnit.SECONDS);
			System.out.println("Successfully ran dirIOTest on : " + rootFolderURI);
			return;
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			System.out.println("IO Test for: " + rootFolderURI + " FAILED!");
			e.printStackTrace();
			return;
		}
	}
	
	private static void dirIOTest(FileObject dir) throws IOException, InterruptedException {
		System.out.println("HELLO");
		System.out.println(dir.getType());
		if (dir.getType() == FileType.FOLDER) {
			String testDirName = "!@$^&(&)(_)(&^$#$ @%!@$^&(&)(_)(&^$#$@";
			//Initialize the child directory FileObject
			FileObject testDir = dir.resolveFile(testDirName);
			//Check that it doesn't already exist
			try {
				assertTrue("Test Directory already exists. Trying to delete test directory...", !testDir.exists());
			} catch (AssertionError e) {
				System.out.println(e.getMessage());
				testDir.delete();
				assertTrue("Test Directory already exists and could not be deleted.", !testDir.exists());
			}
			
			
			//Create the directory
			System.out.println("Creating test directory...");
			testDir.createFolder();
			
			//Check that the directory exists
			assertTrue("Failed to create test directory.",testDir.getType() == FileType.FOLDER);
			
			//Verify directory name
			String url = testDir.toString();
			String expectedUrl = dir.toString() + "/" + testDirName;
			assertTrue("Invalid file name, expected " + expectedUrl + ", got " + url, url.equals(expectedUrl));
			
			//Try to do file IO within the test directory
			System.out.println("Test directory created and verified, doing file IO tests...");
			fileIOTest(testDir);
			
			//Delete the directory
			System.out.println("File IO tests succeeded, deleting test directory...");
			testDir.delete();
			
			//Check that the directory has been deleted
			assertTrue("Failed to delete test directory.", !testDir.exists());
			System.out.println("Directory IO tests succeeded.");
		} else {
			throw new FileSystemException("dirIOTest must be called with a directory FileObject");
		}
	}
	
	public static <T> T await(Callable<T> task, T defaultValue) {
		try {
			return await(task);
		} catch (Exception e) {
			e.printStackTrace();
			return defaultValue;
		}
	}
	
	public static <T> T await(Callable<T> task) throws InterruptedException, ExecutionException {
		return await(task, 1L);
	}
	
	// A method to run any function/method reference in an asynchronous and non-blocking manner
	// Usage should be similar to async/await syntax in Python/Javascript/C#
	public static <T> T await(Callable<T> task, long sleepInterval) throws InterruptedException, ExecutionException {
		Future<T> fut = es.submit(task);
		while (!fut.isDone()) Thread.sleep(sleepInterval);
		return fut.get();
	}
	
	private static void printHexStream(final InputStream inputStream, final int numberOfColumns) throws IOException {
    long streamPtr = 0;
    for (int i = inputStream.read(); i != -1; i = inputStream.read()) {
      final long col = streamPtr++ % numberOfColumns;
      System.out.printf("%02x ", i);
      if (col == (numberOfColumns-1)) {
        System.out.printf("\n");
      }
    }
	}
	
	private static String hexReprFromByteArray(final byte[] bytes, final int numberOfColumns) throws IOException {
    long streamPtr = 0;
    StringBuilder s = new StringBuilder(); s.append(String.format("%08x ", 0));
    for (int i = 0; i < bytes.length; i++) {
      final long col = streamPtr++ % numberOfColumns;
      s.append(String.format("%02x ", bytes[i]));
      if (col == (numberOfColumns-1)) {
        s.append("\n");
        s.append(String.format("%08x ", streamPtr));
      }
    }
    return s.toString();
	}
	
	private static void printHexStreamFromByteArrayRead(final InputStream inputStream, final int numberOfColumns) throws IOException {
    byte[] bytes = new byte[numberOfColumns];
    int count = 0;
    for (int i = inputStream.read(bytes); i != -1; i = inputStream.read(bytes)) {
    	System.out.printf("%08x ", count++*numberOfColumns);
    	for (int b = 0; b < i; b++) {
    		if (bytes[b]!=0) System.out.printf("%02x ",bytes[b]);
    		else System.out.printf("__ ");
    	}
      System.out.printf("\n");
    }
	}
}
