package com.vuze.transcoder.media;

public enum MPEGProfile implements Format {
	
	//H.264
	BASELINE("Baseline"),
	MAIN("Main"),
	HIGH("High"),
	
	//MPEG4
	SIMPLE("Simple"),
	ADVANCEDSIMPLE("Advanced Simple"),
	STREAMINGVIDEO("Streaming Video"),
	
	//MPEG AudioProfile
	MPEG1_LAYER2("Layer 2"),
	MPEG1_LAYER3("Layer 3");
	
	String[] formats;
	
	MPEGProfile(String... formats) {
		this.formats = formats;
	}
	
	public String[] getFormatIds() {
		return formats;
	}
	
	public Format getFromString(String format) {
		for(AudioFormat candidate:AudioFormat.values()) {
			for(String candidateID:candidate.formats) {
				if(candidateID.equals(format)) {
					return candidate;
				}
			}
		}
		
		return null;
	}
}
