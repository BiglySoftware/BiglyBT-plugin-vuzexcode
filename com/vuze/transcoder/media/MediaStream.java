package com.vuze.transcoder.media;

public class MediaStream extends StreamParser {
	
	String id;
	
	String format;
	String profile;
	String codecId;
	String codecIdHint;
	

	//in secs
	int duration;
	
	//in kb/s
	int bitrate;
	
	String language;
	
	public MediaStream() {
		
	}
	
	public int
	getDurationSecs()
	{
		return( duration );
	}
	
	public void parseLine(String line) {
		String key = getKey(line);
		if(key != null) {
			if(key.equals("Format")) {
				
				format = getStringValue(line);
				
			} else if(key.equals("Duration")) {
				
				duration = getDurationValue(line);
					
			} else if(key.equals("Bit rate")) {
				
				bitrate = getBitrateValue(line);
					
			} else if(key.equals("Stream size")) {
				
				//only use the Stream size info if the bitrate is currently 0...
				//Stream size seems to be output after duration
				if(duration > 0 && bitrate == 0) {
					long size = getSizeValue(line);
					// 128 bytes in a Kb (1024 bits)
					bitrate = (int) (size / (128 * duration) );
					
						// we get here usually when the stream is variable-bit-rate. if we plug
						// this average value into a cbr encoder then we're going to get degraded
						// audio so fudge it up a bit
					
					bitrate = ( bitrate * 3 )/2;
				}
				
					
			} else if(key.equals("ID")) {
				
				id = getStringValue(line);
					
			} else if(key.equals("Codec ID")) {
				
				codecId = getStringValue(line);
					
			} else if(key.equals("Codec ID/Hint")) {
				
				codecIdHint = getStringValue(line);
					
			} else if(key.equals("Format profile")) {
				
				profile = getStringValue(line);
					
			} else if(key.equals("Language")) {
				
				language = getStringValue(line);
					
			}
		}
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Stream ");
		sb.append("\nID : ");
		sb.append(id);
		sb.append("\nFormat : ");
		sb.append(format);
		sb.append("\nProfile : ");
		sb.append(profile);
		sb.append("\nCodec ID : ");
		sb.append(codecId);
		sb.append("\nBitrate : ");
		sb.append(bitrate);
		sb.append(" kb/s\nDuration : ");
		sb.append(duration);
		sb.append("\nLanguage : ");
		sb.append(language);
		return sb.toString();
	}
	
	public boolean checkFormats(Format... validFormats) {
		
		for(Format validFormat:validFormats) {
			
			if(checkFormat(validFormat)) {
				
				return true;
				
			}
			
		}
		
		return false;
	}
	
	public boolean checkFormat(Format validFormat) {
		
		if(format == null) {
			return false;
		}
		for(String validFormatId : validFormat.getFormatIds()) {
			if(format.equals(validFormatId)) {
				return true;
			}
		}
		
		return false;
	}

	public boolean checkProfile(String validProfile) {
		if(profile == null) {
			return false;
		}
		
		return profile.equals(validProfile);
	}
	
	public boolean checkBitrate(int maxBitrate) {
		return bitrate > 0 && bitrate <= maxBitrate;
	}

	public int getBitrate() {
		return bitrate;
	}

	public String getCodecId() {
		return codecId;
	}

	public String getCodecIdHint() {
		return codecIdHint;
	}
}
