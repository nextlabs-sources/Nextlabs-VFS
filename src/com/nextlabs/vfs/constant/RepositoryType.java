package com.nextlabs.vfs.constant;

import java.util.Arrays;

public enum RepositoryType {
	LOCAL("LOCAL", "Local Drive"), 
	SHARED_FOLDER("SHARED FOLDER", "Shared Folder"), 
	SHAREPOINT("SHAREPOINT", "Sharepoint"), 
	AZURE_FILE_STORAGE("AUZREFS", "Azure File Storage"), 
	AZURE_BLOB_STORAGE("AZUREBS", "Azure Blob Storage");

	private final String name;

	private final String displayValue;

	RepositoryType(String name, String displayValue) {
		this.name = name;
		this.displayValue = displayValue;
	}

	public String getName() {
		return name;
	}

	public String getDisplayValue() {
		return displayValue;
	}

	public static RepositoryType getRepositoryType(String repositoryType) {
		return Arrays.stream(RepositoryType.values()).filter(t -> t.getDisplayValue().equals(repositoryType)).findFirst().orElse(null);
	}
}
