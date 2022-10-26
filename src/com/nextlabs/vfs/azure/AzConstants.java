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

/**
 * Various constants used in the provider. Currently just the default scheme is
 * declared here but that might change in the future.
 * 
 * @author Kervin Pierre
 */
public class AzConstants {
	/**
	 * Azure Blob Storage API
	 */
	public static final String AZSBSCHEME = "azsb";

	/**
	 * Azure File Storage API
	 */
	public static final String AZSFSCHEME = "azsf";
}
