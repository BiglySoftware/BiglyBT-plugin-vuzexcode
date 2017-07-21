package com.vuze.transcoder.media;

public class VideoStream extends MediaStream {
	
	String colorSpace;
	int width;
	int height;
	double frameRate;
	
	int reFrames;
	
	int warpPoints;
	
	double displayAspectRatio;
	
	public String toString() {
		StringBuffer sb = new StringBuffer(super.toString());
		sb.append("\nColorspace : ");
		sb.append(colorSpace);
		sb.append("\nWidth : ");
		sb.append(width);
		sb.append("\nHeight : ");
		sb.append(height);
		sb.append("\nDisplay Aspect Ratio : ");
		sb.append(displayAspectRatio);
		sb.append("\nFrameRate : ");
		sb.append(frameRate);
		return sb.toString();
	}

	public boolean checkFrameRate(double maxFrameRate) {
		return frameRate > 0.0 && frameRate <= maxFrameRate;
	}
	
	public boolean checkResolution(int maxWidth,int maxHeight) {
		return width > 0 && height > 0 && width <= maxWidth && height <= maxHeight;
	}
	
	public int
	getWidth()
	{
		return( width );
	}
	
	public int
	getHeight()
	{
		return( height );
	}
	
	public void parseLine(String line) {
		super.parseLine(line);
		String key = getKey(line);
		if(key != null) {
			if(key.equals("Colorimetry")) {
				
				colorSpace = getStringValue(line);
				
			} else if(key.equals("Display aspect ratio")) {
				
				displayAspectRatio = getDoubleValue(line);
					
			} else if(key.equals("Width")) {
				
				width = getIntegerValue(line);
					
			} else if(key.equals("Height")) {
				
				height = getIntegerValue(line);
					
			} else if(key.equals("Frame rate")) {
				
				frameRate = getDoubleValue(line);
					
			}else if(key.equals("Format settings, ReFrames")) {
				
				reFrames = getIntegerValue(line);
					
			}else if(key.equals("Format settings, GMC")) {
				
				warpPoints = getIntegerValue(line);
					
			} 
		}
	}
	
	
	public boolean checkReFrames(int maxReFrames) {
		return reFrames > 0 && reFrames <= maxReFrames;
	}
	
	public boolean checkMPEGProfiles(double maxLevel,MPEGProfile... validMPEGProfiles) {
		
		for(MPEGProfile validMPEGProfile:validMPEGProfiles) {
			
			if(checkMPEGProfile(maxLevel, validMPEGProfile)) {
				
				return true;
				
			}
			
		}
		
		return false;
	}
	
	public boolean checkMPEGProfile(double maxLevel,MPEGProfile validMPEGProfile) {
		if(profile == null) {
			return false;
		}
		
		String[] profileParts = profile.split("@L");
		if(profileParts.length != 2) {
			return false;
		}
		
		boolean hasValidProfile = false;
		String[] profiles = validMPEGProfile.getFormatIds();
		
		for(String validProfile:profiles) {
			if(profileParts[0].equals(validProfile)) {
				hasValidProfile = true;
			}
		}
		
		if(!hasValidProfile) {
			return false;
		}
		
		try {
			double profileLevel = Double.parseDouble(profileParts[1]);
			if(profileLevel > maxLevel) {
				return false;
			}
		} catch (Throwable t) {
			//Can't parse the profile level, not a good sign...
			return false;
		}
		
		return true;
	}
	
	public boolean checkWarpPoints(int nbMaxWarpPoints) {
		return warpPoints <= nbMaxWarpPoints;
	}

	public double getFrameRate() {
		return frameRate;
	}
	
	public String getFormatAsString() {
		return format;
	}
	
	public VideoFormat getVideoFormat() {
		return VideoFormat.getFromString(format);
	}
}
