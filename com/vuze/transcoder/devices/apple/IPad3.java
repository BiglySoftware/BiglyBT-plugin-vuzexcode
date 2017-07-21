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

public class IPad3 extends DeviceSpecification {
	
	
	
	public boolean isFileNameCompatible(String inputfileName) {
		if(inputfileName == null) {
			return false;
		}
		return inputfileName.endsWith(".mov") || inputfileName.endsWith(".mp4") || inputfileName.endsWith(".m4v");
	}
	
	public boolean isCompatible(MediaInformation info) {
		if(info.getVideoStreams().size() == 0) {
			//No video, audio only
			return isFormatCompatibleForAudioOnly(info) && isAudioCompatibleForAudioOnly(info);
		} else {
			return isFormatCompatible(info) && isVideoCompatible(info) && isAudioCompatible(info);
		}
	}
	
	public boolean isAudioCompatible(MediaInformation info,
			ContainerFormat containerFormat, VideoFormat videoFormat) {
		return isAudioCompatible(info);
	}
	
	public boolean isVideoCompatible(MediaInformation info,
			ContainerFormat format, int maxWidth, int maxHeight) {
		//we don't implement maxWidth and maxHeight for the iPod
//		return isVideoCompatible(info);
		return false;
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
	
	private boolean isFormatCompatibleForAudioOnly(MediaInformation info) {
		if(info == null) {
			return false;
		}
		
		return info.checkContainers(ContainerFormat.MPEGAUDIO,ContainerFormat.MPEG4);
		
	}
	
	private boolean isAudioCompatibleForAudioOnly(MediaInformation info) {
		if(info == null) {
			return false;
		}
		
		List<AudioStream> streams = info.getAudioStreams();
		if(streams == null) {
			return false;
		}
		
		//Let's be safe and not allow more than 1 audio stream
		if(streams.size() != 1) {
			return false;
		}
		
		//1 stream, let's look at it
		AudioStream stream = streams.get(0);
		
		if(!stream.checkFormats(AudioFormat.AAC,AudioFormat.MP3)) {
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
			//H.264 video up to 720p, 30 frames per second, Main Profile level 3.1
			if(!stream.checkBitrate(14000) && !info.checkBitrate(14000)) {
				return false;
			}
			
			if(!stream.checkResolution(1920, 1080)) {
				return false;
			}
			
			if(!stream.checkFrameRate(30)) {
				return false;
			}
			
			if( !(	stream.checkMPEGProfile(3.1, MPEGProfile.BASELINE)
					|| stream.checkMPEGProfile(3.1, MPEGProfile.MAIN)
				 ) ){
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
			//MPEG-4 video, up to 2.5 Mbps, 640 by 480 pixels, 30 frames per second, Simple Profile
			if(!stream.checkBitrate(2500) && !info.checkBitrate(2500)) {
				return false;
			}
			
			if(!stream.checkResolution(640, 480)) {
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
