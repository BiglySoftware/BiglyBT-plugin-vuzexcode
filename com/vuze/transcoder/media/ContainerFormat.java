package com.vuze.transcoder.media;

public enum ContainerFormat implements Format {
	
	MPEG4("MPEG-4","mp4"),
	AVI("AVI"),
	WMV("Windows Media"),
	MPEG1("CDXA/MPEG-PS"),
	MPEGAUDIO("MPEG Audio");
	
	String[] formats;
	
	ContainerFormat(String... formats) {
		this.formats = formats;
	}
	
	public String[] getFormatIds() {
		return formats;
	}
	
	public static ContainerFormat getFromString(String format) {
		for(ContainerFormat candidate:ContainerFormat.values()) {
			for(String candidateID:candidate.formats) {
				if(candidateID.equals(format)) {
					return candidate;
				}
			}
		}
		
		return null;
	}
}
