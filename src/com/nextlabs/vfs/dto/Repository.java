package com.nextlabs.vfs.dto;

import com.nextlabs.vfs.constant.RepositoryType;

public class Repository {
	
	String path;
	RepositoryType type;
	RepositoryCredentials creds;
	
	public Repository(String repoPath, RepositoryType repoType, RepositoryCredentials creds) {
		this.path = repoPath;
		this.type = repoType;
		this.creds = creds;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public RepositoryType getType() {
		return type;
	}

	public void setType(RepositoryType type) {
		this.type = type;
	}

	public RepositoryCredentials getCreds() {
		return creds;
	}

	public void setCreds(RepositoryCredentials creds) {
		this.creds = creds;
	}

}
