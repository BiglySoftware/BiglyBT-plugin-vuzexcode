package com.vuze.transcoder.media;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.internat.MessageText;

public class MediaInformation {
	
	private enum State {
		
		UNKNOWN {
			State parseLine(String line) {
				if(line.equals("General")) {
					return State.GENERAL;
				} else if(line.startsWith("Video")){
					return State.VIDEO;
				} else if(line.startsWith("Audio")){
					return State.AUDIO;
				} else {
					return State.UNKNOWN;
				}
			}
			
			Object doFinal() {
				return null;
			}
			
		},
		
		GENERAL {
			
			GeneralStream stream = new GeneralStream();
			
			State parseLine(String line) {
				stream.parseLine(line);
				return State.GENERAL;
			}
			
			Object doFinal() {
				GeneralStream result = stream;
				stream = new GeneralStream();
				return result;
			}
			
		},
		
		VIDEO {
			
			VideoStream stream = new VideoStream();
			
			State parseLine(String line) {
				stream.parseLine(line);
				return State.VIDEO;
			}
			
			Object doFinal() {
				VideoStream result = stream;
				stream = new VideoStream();
				return result;
			}
			
		},
		
		AUDIO {
			AudioStream stream = new AudioStream();
			
			State parseLine(String line) {
				stream.parseLine(line);
				return State.AUDIO;
			}
			
			Object doFinal() {
				AudioStream result = stream;
				stream = new AudioStream();
				return result;
			}
		};
		
		abstract State parseLine(String line);
		abstract Object doFinal();
	}

//	static final Pattern patternInput = Pattern.compile("Input #0, ([^ ]+) ");
//	static final Pattern patternDuration = Pattern.compile("  Duration: ([0-9]{2}):([0-9]{2}):([0-9]{2}).([0-9]{2}), start: ([0-9\\.]*), bitrate: (?:([0-9]+) ([kM]?b/s)|N/A)");
//	static final Pattern patternVideoStream = Pattern.compile("    Stream ([^\\(]*)(?:\\(([^\\)]*)\\))?: Video: ([^,]*), ([^,]*), ([0-9]*)x([0-9]*)(?: \\[PAR ([^ ]*) DAR ([^ ]*)\\])?, (?:([0-9]*) kb/s, )?([^ ]*)");
//	static final Pattern patternAudioStream = Pattern.compile("    Stream ([^\\(]*)(?:\\(([^\\)]*)\\))?: Audio: ([^,]*), ([0-9]*) Hz, ([^,]*), ([^,]*)(?:, ([0-9]*) kb/s)?");
	
	private State currentState = State.UNKNOWN;
	
	GeneralStream	 		 generalStream;
	final List<AudioStream>	 audioStreams;
	final List<VideoStream>  videoStreams;

	final StringBuffer rawOutput;
	
	public List<VideoStream> getVideoStreams() {
		return videoStreams;
	}
	
	public List<AudioStream> getAudioStreams() {
		return audioStreams;
	}

	public boolean hasGeneralStream(){
		return generalStream != null;
	}
	
	public MediaInformation() {
		videoStreams = new ArrayList<VideoStream>(1);
		audioStreams = new ArrayList<AudioStream>(1);
		rawOutput = new StringBuffer();
	}
	
	public void parseInfoLine(String line) {
		try {
			if ( line != null ){
				
				final String[] to_ignore = {
						"complete name",
						"album",
						"track",
						"track name",
						"genre",
						"performer",
						"comment",
						"apid", "cprt", "cnid",	// itunes stuff
				};
				
				String	lc_line = line.toLowerCase( MessageText.LOCALE_ENGLISH ).trim();
				
				boolean	skip = false;
				
				for ( int i=0;i<to_ignore.length;i++){
					
					if ( lc_line.startsWith( to_ignore[i])){
						
						skip = true;
						
						break;
					}
				}
				
				if ( !skip ){
				
					rawOutput.append(line);
					
					rawOutput.append("\n");
				}
			}
			
				//Blank line means end of section
			
			if(line.equals("")) {
				Object result = currentState.doFinal();
				if(result != null) {
					if(result instanceof GeneralStream) {
						this.generalStream = (GeneralStream)result;
					} else if(result instanceof VideoStream) {
						this.videoStreams.add((VideoStream)result);
					} else if(result instanceof AudioStream) {
						this.audioStreams.add((AudioStream)result);
					}
				}
				currentState = State.UNKNOWN;
				return;
			} else {
				currentState = currentState.parseLine(line);
			}
			
		}catch (Exception e) {
			//Do nothing
			e.printStackTrace();
		}
		
		
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		
		if(generalStream != null) {
			sb.append("Duration : ");
			sb.append(generalStream.duration);
			sb.append("\nBitrate: ");
			sb.append(generalStream.bitrate);
			sb.append(" kb/s\nContainer : ");
			sb.append(generalStream.format);
		} else {
			sb.append("No General Stream");
		}
		
		for(VideoStream stream:videoStreams) {
			sb.append("\n");
			sb.append(stream.toString());
		}
		
		for(AudioStream stream:audioStreams) {
			sb.append("\n");
			sb.append(stream.toString());
		}
		
		return sb.toString();
		
	}

	public boolean checkContainers(Format... validContainers) {
		for(Format container : validContainers) {
			if(checkContainer(container)) {
				return true;
			}
		}
		
		return false;
		
	}
	public boolean checkContainer(Format validContainer) {
		if(generalStream == null) {
			return false;
		}
		
		if(generalStream.format == null) {
			return false;
		}
		for(String containerId : validContainer.getFormatIds()) {
			if(generalStream.format.equals(containerId)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean checkContainerProfiles(ContainerProfile... validProfiles) {
		for(ContainerProfile validProfile : validProfiles) {
			if(checkContainerProfile(validProfile)) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean checkContainerProfile(ContainerProfile validProfile) {
		if(generalStream == null) {
			return false;
		}
		
		if(generalStream.profile == null) {
			return false;
		}
		for(String profile : validProfile.getFormatIds()) {
			if(generalStream.profile.equals(profile)) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean checkBitrate(int maxBitrate) {
		return generalStream.bitrate > 0 && generalStream.bitrate <= maxBitrate;
	}
	
	public GeneralStream getGeneralStream() {
		return generalStream;
	}
	
	public VideoStream getFirstVideoStream() {
		if(videoStreams != null && videoStreams.size() > 0) {
			return videoStreams.get(0);
		}
		return null;
	}
	
	public AudioStream getFirstAudioStream() {
		if(audioStreams != null && audioStreams.size() > 0) {
			return audioStreams.get(0);
		}
		return null;
	}

	public StringBuffer getRawOutput() {
		return rawOutput;
	}
}
