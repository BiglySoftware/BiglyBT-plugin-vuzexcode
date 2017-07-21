package com.vuze.transcoder.media;

public class GeneralStream extends StreamParser {
	
	String format;
	String profile;
	
	//in secs
	int duration;
	
	//in kb/s
	int bitrate;
	
	boolean encrypted;
	
	public void parseLine(String line) {
		String key = getKey(line);
		if(key != null) {
			if(key.equals("Format")) {
				
				format = getStringValue(line);
				
			} else if(key.equals("Duration")) {
				
				duration = getDurationValue(line);
					
			} else if(key.equals("Duration")) {
				
				duration = getDurationValue(line);
					
			}else if(key.equals("Format profile")) {
				
				profile = getStringValue(line);
					
			}else if(key.equals("Overall bit rate")) {
				
				bitrate = getBitrateValue(line);					
			}else if(key.equals("Encryption")) {
				
				String value = getStringValue(line);
				if(value != null && value.length() > 0) {
					encrypted = true;
				}
			}
		}
	}

	public int getDurationSecs() {
		return duration;
	}

	public boolean isEncrypted() {
		return encrypted;
	}

}
