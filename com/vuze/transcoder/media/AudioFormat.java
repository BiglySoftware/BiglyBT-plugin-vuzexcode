package com.vuze.transcoder.media;

public enum AudioFormat implements Format{
	
	DTS("A_DTS"),
	AC3("AC-3"),
	WMA2("WMA2"),
	WMA3("WMA3"),
	AAC("AAC"),
	MP3("MPEG Audio"),
	MP2("MPEG Audio");
	
	String[] formats;
	
	AudioFormat(String... formats) {
		this.formats = formats;
	}
	
	public String[] getFormatIds() {
		return formats;
	}
	
	public static AudioFormat getFromString(String format) {
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
