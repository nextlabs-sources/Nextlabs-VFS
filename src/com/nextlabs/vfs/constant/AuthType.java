package com.nextlabs.vfs.constant;

import java.util.Arrays;

public enum AuthType {
	HTTP_BASIC("Basic"), 
	HTTP_DIGEST("Digest"), 
	SHAREPOINT_ONLINE("Sharepoint Online"), 
	HTTP_NTLM("NTLM"), 
	CIFS("CIFS"), 
	AZURE("Azure Storage");

	private String name;

	private AuthType(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
	
	public static AuthType getTypeByNameString(String name) {
		return Arrays.stream(AuthType.values()).filter(t -> t.getName().equals(name)).findFirst().orElse(null);
	}
}
