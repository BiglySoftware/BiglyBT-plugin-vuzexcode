package com.vuze.transcoder.media;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StreamParser {
	
	final static Pattern durationPattern = Pattern.compile("(?:([0-9]*)h ?)?(?:([0-9]*)mn ?)?(?:([0-9]*)s)?");
	final static Pattern bitratePattern = Pattern.compile("([0-9 \\.]*)(Kbps|Mbps)");
	final static Pattern sizePattern = Pattern.compile("([0-9 \\.]*) (B|KiB|MiB|GiB)");
	final static Pattern intPattern = Pattern.compile("([0-9 ]*)");
	final static Pattern floatPattern = Pattern.compile("([0-9 \\.]*)");
	
	String getKey(String line) {
		int pos = line.indexOf(": ");
		if(pos > 0) {
			String key = line.substring(0,pos);
			return key.trim();
		}
		
		return null;
	}
	
	String getStringValue(String line) {
		int pos = line.indexOf(": ");
		if(pos > 0) {
			return line.substring(pos+2);
		}
		
		return null;
	}
	
	int getDurationValue(String line) {
		String value = getStringValue(line);
		Matcher matcher = durationPattern.matcher(value);
		if(matcher.find()) {
			int duration = 0;
			//hours
			if(matcher.group(1) != null) {
				try {
					duration += Integer.parseInt(matcher.group(1));
				} catch (Throwable t) {
					//Invalid number, ignore
				}
			}
			duration *= 60;
			//minutes
			if(matcher.group(2) != null) {
				try {
					duration += Integer.parseInt(matcher.group(2));
				} catch (Throwable t) {
					//Invalid number, ignore
				}
			}
			duration *= 60;
			//seconds
			if(matcher.group(3) != null) {
				try {
					duration += Integer.parseInt(matcher.group(3));
				} catch (Throwable t) {
					//Invalid number, ignore
				}
			}
			
			return duration;
		}
		return 0;
	}
	
	int getBitrateValue(String line) {
		String value = getStringValue(line);
		Matcher matcher = bitratePattern.matcher(value);
		if(matcher.find()) {
			double result = 0;
			String bitrate = matcher.group(1);
			if(bitrate != null) {
				bitrate = bitrate.trim().replaceAll(" ", "");
				try {
					result = Double.parseDouble(bitrate);
				} catch (Throwable t) {
					//Invalid number, let's return 0;
					return 0;
				}
			}
			String unit = matcher.group(2);
			if(unit != null && unit.equals("Mbps")) {
				result *= 1000;
			}
			return (int)result;
		}
		return 0;
	}
	
	long getSizeValue(String line) {
		String value = getStringValue(line);
		Matcher matcher = sizePattern.matcher(value);
		if(matcher.find()) {
			double result = 0;
			String size = matcher.group(1);
			if(size != null) {
				size = size.trim().replaceAll(" ", "");
				try {
					result = Double.parseDouble(size);
				} catch (Throwable t) {
					//Invalid number, let's return 0;
					return 0;
				}
			}
			String unit = matcher.group(2);
			if(unit != null && unit.equals("KiB")) {
				result *= 1024;
			}
			if(unit != null && unit.equals("MiB")) {
				result *= 1024 * 1024;
			}
			if(unit != null && unit.equals("GiB")) {
				result *= 1024 * 1024 * 1024;
			}
			return (long)result;
		}
		return 0;
	}
	
	int getIntegerValue(String line) {
		String value = getStringValue(line);
		Matcher matcher = intPattern.matcher(value);
		if(matcher.find()) {
			int result = 0;
			String number = matcher.group(1);
			if(number != null) {
				
				number = number.trim().replaceAll(" ", "");
				try {
					result = Integer.parseInt(number);
				} catch (Throwable t) {
					//Invalid number, let's return 0;
					return 0;
				}
			}
			return result;
		}
		return 0;
	}
	
	double getDoubleValue(String line) {
		String value = getStringValue(line);
		Matcher matcher = floatPattern.matcher(value);
		if(matcher.find()) {
			double result = 0;
			String number = matcher.group(1);
			if(number != null) {
				number = number.trim().replaceAll(" ", "");
				try {
					result = Double.parseDouble(number);
				} catch (Throwable t) {
					//Invalid number, let's return 0;
					return 0.0;
				}
			}
			return result;
		}
		return 0;
	}

}
