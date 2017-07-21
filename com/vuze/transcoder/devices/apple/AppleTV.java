package com.vuze.transcoder.devices.apple;

import java.util.List;

import com.vuze.transcoder.devices.DeviceSpecification;
import com.vuze.transcoder.media.AudioFormat;
import com.vuze.transcoder.media.AudioStream;
import com.vuze.transcoder.media.ContainerFormat;
import com.vuze.transcoder.media.ContainerProfile;
import com.vuze.transcoder.media.MPEGProfile;
import com.vuze.transcoder.media.MediaInformation;
import com.vuze.transcoder.media.VideoFormat;
import com.vuze.transcoder.media.VideoStream;

public class AppleTV extends DeviceSpecification {

	public String getDeviceName() {
		return "AppleTV";
	}
	
	public boolean isFileNameCompatible(String inputfileName) {
		if(inputfileName == null) {
			return false;
		}
		return inputfileName.endsWith(".mov") || inputfileName.endsWith(".mp4") || inputfileName.endsWith(".m4v");
	}
	
	public boolean isCompatible(MediaInformation info) {
		return isFormatCompatible(info) && isVideoCompatible(info) && isAudioCompatible(info);
	}
	
	public boolean isAudioCompatible(MediaInformation info,
			ContainerFormat containerFormat, VideoFormat videoFormat) {
		return isAudioCompatible(info);
	}
	
	public boolean isVideoCompatible(MediaInformation info,
			ContainerFormat format, int maxWidth, int maxHeight) {
		return false;
		//return isVideoCompatible(info);
	}
	
	private boolean isFormatCompatible(MediaInformation info) {
		if(info == null) {
			return false;
		}
		
		if(!info.checkContainer(ContainerFormat.MPEG4) ) {
			return false;
		}
		
		if(!info.checkContainerProfiles(ContainerProfile.BASEMEDIA,ContainerProfile.QUICKTIME) ) {
			return false;
		}
		
		return true;
	}
	
	private boolean isVideoCompatible(MediaInformation info) {
		if(info == null) {
			return false;
		}
		
		List<VideoStream> streams = info.getVideoStreams();
		if(streams == null) {
			return false;
		}
		
		//Let's be safe and not allow more than 1 video stream
		if(streams.size() != 1) {
			return false;
		}
		
		//1 stream, let's look at it
		VideoStream stream = streams.get(0);
		
		if(stream.checkFormat(VideoFormat.H264)) {
			//From the apple website :
			//H.264 video, up to 5 Mbps, 1280 by 720 pixels, 24 frames per second, Main Profile up to Level 3.0
			if(!stream.checkBitrate(5000) && !info.checkBitrate(5000)) {
				return false;
			}
			
			if(!stream.checkResolution(1280, 720)) {
				return false;
			}
			
			if(!stream.checkFrameRate(24)) {
				return false;
			}
			
			if(!stream.checkMPEGProfiles(3.0, MPEGProfile.BASELINE,MPEGProfile.MAIN)) {
				return false;
			}
			
			//And it seems that there is a constraint on max 5 Re Frame ...
			if(!stream.checkReFrames(5)) {
				return false;
			}
			
			//Ok, it seems to be all good ...
			return true;
			
		} 
		
		//it seems that the iTunes / the iPod is actually quite picky on
		//MPEG4 video streams, so let's force transcode them all the time.
		else if(stream.checkFormat(VideoFormat.MPEG4)) {
			//From the apple website :
			//MPEG-4 video, up to 3 Mbps, 720 by 432 pixels, 30 frames per second, Simple Profile
			if(!stream.checkBitrate(3000) && !info.checkBitrate(3000)) {
				return false;
			}
			
			if(!stream.checkResolution(720, 432)) {
				return false;
			}
			
			if(!stream.checkFrameRate(30)) {
				return false;
			}
			
			if(!stream.checkMPEGProfile(3.0, MPEGProfile.SIMPLE)) {
				return false;
			}
			
			//Ok, it seems to be all good ...
			return true;
			
		}
		
		return false;
		
	}
	
	private boolean isAudioCompatible(MediaInformation info) {
		if(info == null) {
			return false;
		}
		
		List<AudioStream> streams = info.getAudioStreams();
		if(streams == null) {
			return false;
		}
		
		//Let's be safe and not allow more than 1 video stream
		if(streams.size() != 1) {
			return false;
		}
		
		//1 stream, let's look at it
		AudioStream stream = streams.get(0);
		
		
		//From the apple website:
		//AAC-LC audio up to 160 Kbps, 48kHz, stereo 
		if(!stream.checkFormat(AudioFormat.AAC)) {
			return false;
		}
		
		if(!stream.checkProfile("LC")) {
			return false;
		}
		
		if(!stream.checkBitrate(160)) {
			return false;
		}
		
		if(!stream.checkSampleRate(48000)) {
			return false;
		}
		
		if(!stream.checkChannels(2)) {
			return false;
		}
		
		return true;
	}

}
