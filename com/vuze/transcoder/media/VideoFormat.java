package com.vuze.transcoder.media;

public enum VideoFormat implements Format {
	
	H264("AVC"),
	MPEG4("MPEG-4 Visual"),
	MPEG1("MPEG Video"),
	VC1("VC-1");
	
	String[] formats;
	
	VideoFormat(String... formats) {
		this.formats = formats;
	}
	
	public String[] getFormatIds() {
		return formats;
	}
	
	public static VideoFormat getFromString(String format) {
		for(VideoFormat candidate:VideoFormat.values()) {
			for(String candidateID:candidate.formats) {
				if(candidateID.equals(format)) {
					return candidate;
				}
			}
		}
		
		return null;
	}
}
