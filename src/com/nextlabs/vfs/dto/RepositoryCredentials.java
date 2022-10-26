package com.nextlabs.vfs.dto;

import com.nextlabs.vfs.constant.AuthType;

public class RepositoryCredentials {
	private String domain;
	private String userName;
	private String password;
	private AuthType type;
	
	public RepositoryCredentials(String domain, String username, String password, AuthType type) {
		this.domain = domain;
		this.userName = username;
		this.password = password;
		this.type = type;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public AuthType getType() {
		return type;
	}

	public void setType(AuthType type) {
		this.type = type;
	}

	public String toString() {
		return "[" + domain + "," + userName + "," + password + "]";
	}
}
