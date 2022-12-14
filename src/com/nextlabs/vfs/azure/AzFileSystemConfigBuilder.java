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
package com.nextlabs.vfs.azure;

import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemConfigBuilder;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.UserAuthenticator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.nextlabs.vfs.azure.file.AzFileSystem;

/**
 *
 * @author kervin
 */
public class AzFileSystemConfigBuilder extends FileSystemConfigBuilder {
	private static final Logger log = LogManager.getLogger(AzFileSystemConfigBuilder.class);
	private static final AzFileSystemConfigBuilder BUILDER = new AzFileSystemConfigBuilder();

	@Override
	protected Class<? extends FileSystem> getConfigClass() {
		return AzFileSystem.class;
	}

	protected AzFileSystemConfigBuilder(String prefix) {
		super(prefix);
	}

	private AzFileSystemConfigBuilder() {
		super("azure.");
	}

	public static AzFileSystemConfigBuilder getInstance() {
		return BUILDER;
	}

	/**
	 * Sets the user authenticator to get authentication informations.
	 * 
	 * @param opts              The FileSystemOptions.
	 * @param userAuthenticator The UserAuthenticator.
	 * @throws FileSystemException if an error occurs setting the UserAuthenticator.
	 */
	public void setUserAuthenticator(FileSystemOptions opts, UserAuthenticator userAuthenticator) throws FileSystemException {
		setParam(opts, "userAuthenticator", userAuthenticator);
	}

	/**
	 * @see #setUserAuthenticator
	 * @param opts The FileSystemOptions.
	 * @return The UserAuthenticator.
	 */
	public UserAuthenticator getUserAuthenticator(FileSystemOptions opts) {
		return (UserAuthenticator) getParam(opts, "userAuthenticator");
	}
}
