package com.vuze.transcoder.media;

public enum ContainerProfile implements Format {
	
	BASEMEDIA("Base Media","Base Media / Version 2"),
	QUICKTIME("QuickTime");
	
	String[] formats;
	
	ContainerProfile(String... formats) {
		this.formats = formats;
	}
	
	public String[] getFormatIds() {
		return formats;
	}
	
	public static ContainerProfile getFromString(String format) {
		for(ContainerProfile candidate:ContainerProfile.values()) {
			for(String candidateID:candidate.formats) {
				if(candidateID.equals(format)) {
					return candidate;
				}
			}
		}
		
		return null;
	}
}
