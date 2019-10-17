package com.vuze.transcoder.media;

public class AudioStream extends MediaStream {

	//in Hz
	int sampleRate;
	
	//2 for stereo, 6 for 5.1 , ...
	int channels;
	
	//int bits
	int resolution;

	String title;

	public AudioStream() {
		super( false );
	}

	public String toString() {
		StringBuffer sb = new StringBuffer(super.toString());
		sb.append("\nChannels : ");
		sb.append(channels);
		sb.append("\nSample Rate :");
		sb.append(sampleRate);
		sb.append(" Hz\nResolution : ");
		sb.append(resolution);
		sb.append(" bits\nTitle : ");
		sb.append(title);
		return sb.toString();
	}

	public boolean checkChannels(int maxChannels) {
		
		return channels > 0 && channels <= maxChannels;
	}
	
	public boolean checkSampleRate(int maxSampleRate) {
		return sampleRate > 0 && maxSampleRate <= maxSampleRate;
	}
	
	public void parseLine(String line) {
		super.parseLine(line);
		String key = getKey(line);
		if(key != null) {
			if(key.equals("Channel(s)")) {
				
				channels = getIntegerValue(line);
				
			} else if(key.equals("Sampling rate")) {
				
				sampleRate = (int) (getDoubleValue(line) * 1000);
					
			} else if(key.equals("Resolution")) {
				
				resolution = getIntegerValue(line);
					
			} else if(key.equals("Title")) {
				
				title = getStringValue(line);
					
			}
		}
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public int getChannels() {
		return channels;
	}

	public int getResolution() {
		return resolution;
	}

}
