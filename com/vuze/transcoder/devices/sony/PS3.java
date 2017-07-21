package com.vuze.transcoder.devices.sony;

import java.util.List;

import com.vuze.transcoder.devices.DeviceSpecification;
import com.vuze.transcoder.media.AudioFormat;
import com.vuze.transcoder.media.AudioStream;
import com.vuze.transcoder.media.ContainerFormat;
import com.vuze.transcoder.media.ContainerProfile;
import com.vuze.transcoder.media.GeneralStream;
import com.vuze.transcoder.media.MediaInformation;
import com.vuze.transcoder.media.VideoFormat;
import com.vuze.transcoder.media.VideoStream;

public class PS3 extends DeviceSpecification {
	
	//http://manuals.playstation.net/document/en/ps3/current/video/filetypes.html
	//I'm lazy, so I'll just code the MPEG4 part
	//and the .avi / divx one ...
	
	
	
	public boolean isFileNameCompatible(String inputfileName) {
		return inputfileName.endsWith(".pm4");
	}
	
	public boolean isAudioCompatible(MediaInformation info,
			ContainerFormat containerFormat, VideoFormat videoFormat) {
		
		if(containerFormat == null) {
			return false;
		}
		//We'll handle .mp4 output only for now
		if(!containerFormat.equals(ContainerFormat.MPEG4)) {
			return false;
		}
		
		return checkAudioInMPEG4Container(info);
	}
	
	public boolean isVideoCompatible(MediaInformation info,
			ContainerFormat format, int maxWidth, int maxHeight) {
		if(format == null) {
			return false;
		}
		//We'll handle .mp4 output only for now
		if(!format.equals(ContainerFormat.MPEG4)) {
			return false;
		}
		
		if(!checkVideoInMPEG4Container(info)) {
			return false;
		}
		
		//Resolution check (in case we do want to transcode from HD to SD)
		if(info == null) {
			return false;
		}
		VideoStream video = info.getFirstVideoStream();
		if(video == null) {
			return false;
		}
		if(maxWidth > 0 && video.getWidth() > maxWidth) {
			return false;
		}
		if(maxHeight > 0 && video.getHeight() > maxHeight) {
			return false;
		}
		
		//this seems to be a compatible video stream...
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
	
	public boolean isCompatible(MediaInformation info) {
		if(info == null) {
			return false;
		}
		
		if(info.getVideoStreams().size() == 0) {
			//No video, audio only
			return isFormatCompatibleForAudioOnly(info) && isAudioCompatibleForAudioOnly(info);
		}
		
		//Divx support for the PS3 seems to mean avi + mpeg4 video + mp3 audio ...
		//the PS3 also seems to support AC3 audio in an avi container ...
		//It seems that the PS3 doesn't like warppoints
		//TODO : verify that warppoints are the problem on the PS3
		if(info.checkContainer(ContainerFormat.AVI)) {
			List<VideoStream> videoStreams = info.getVideoStreams();
			
			if(videoStreams == null || videoStreams.size() != 1 ) {
				return false;
			}
			VideoStream video = videoStreams.get(0);
			if(!video.checkFormat(VideoFormat.MPEG4)) {
				return false;
			}
			
			if(!video.checkWarpPoints(0)) {
				return false;
			}
			
			List<AudioStream> audioStreams = info.getAudioStreams();
			if(audioStreams == null || audioStreams.size() != 1 ) {
				return false;
			}
			AudioStream audio = audioStreams.get(0);
			if(!audio.checkFormats(AudioFormat.MP3,AudioFormat.AC3)) {
				return false;
			}
			
			
			
			return true;
		}
		
		if(info.checkContainer(ContainerFormat.MPEG1)) {
			//Mpeg1 video and mpeg1 layer 2 audio
			List<VideoStream> videoStreams = info.getVideoStreams();
			
			if(videoStreams == null || videoStreams.size() != 1 ) {
				return false;
			}
			VideoStream video = videoStreams.get(0);
			if(!video.checkFormat(VideoFormat.MPEG1)) {
				return false;
			}
			
			List<AudioStream> audioStreams = info.getAudioStreams();
			if(audioStreams == null || audioStreams.size() != 1 ) {
				return false;
			}
			AudioStream audio = audioStreams.get(0);
			if(!audio.checkFormats(AudioFormat.MP2)) {
				return false;
			}
			
			if(!audio.checkProfile("Layer 2")) {
				return false;
			}
			
			return true;
		}
		
		if(info.checkContainer(ContainerFormat.WMV)) {
			//Mpeg1 video and mpeg1 layer 2 audio
			List<VideoStream> videoStreams = info.getVideoStreams();
			
			if(videoStreams == null || videoStreams.size() != 1 ) {
				return false;
			}
			VideoStream video = videoStreams.get(0);
			if(!video.checkFormat(VideoFormat.VC1)) {
				return false;
			}
			
			List<AudioStream> audioStreams = info.getAudioStreams();
			if(audioStreams == null || audioStreams.size() != 1 ) {
				return false;
			}
			AudioStream audio = audioStreams.get(0);
			if(!audio.checkFormats(AudioFormat.WMA2)) {
				return false;
			}
			
			GeneralStream generalStream = info.getGeneralStream();
			if(generalStream == null || generalStream.isEncrypted()) {
				return false;
			}
			
			return true;
		}
		
		
		if(info.checkContainer(ContainerFormat.MPEG4)) {
			
			//No quicktime stuff on the PS3
			if(!info.checkContainerProfiles(ContainerProfile.BASEMEDIA) ) {
				return false;
			}
		
			if(!checkVideoInMPEG4Container(info)) {
				return false;
			}
		
			return checkAudioInMPEG4Container(info);
		}
		
		return false;
	}

	private boolean checkVideoInMPEG4Container(MediaInformation info) {
		List<VideoStream> videoStreams = info.getVideoStreams();
		if(videoStreams == null || videoStreams.size() != 1 ) {
			return false;
		}
		VideoStream video = videoStreams.get(0);
		if(!video.checkFormat(VideoFormat.MPEG4) && ! video.checkFormat(VideoFormat.H264)) {
			return false;
		}
		
		return true;
	}

	private boolean checkAudioInMPEG4Container(MediaInformation info) {
		List<AudioStream> audioStreams = info.getAudioStreams();
		if(audioStreams == null || audioStreams.size() != 1 ) {
			return false;
		}
		AudioStream audio = audioStreams.get(0);
		if(!audio.checkFormat(AudioFormat.AAC)) {
			return false;
		}
		if(!audio.checkProfile("LC")) {
			return false;
		}
		
		return true;
	}

}
