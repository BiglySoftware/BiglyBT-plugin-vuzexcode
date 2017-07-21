package com.vuze.transcoder.devices.microsoft;

import java.util.List;

import com.vuze.transcoder.devices.DeviceSpecification;
import com.vuze.transcoder.media.AudioFormat;
import com.vuze.transcoder.media.AudioStream;
import com.vuze.transcoder.media.ContainerFormat;
import com.vuze.transcoder.media.MPEGProfile;
import com.vuze.transcoder.media.MediaInformation;
import com.vuze.transcoder.media.VideoFormat;
import com.vuze.transcoder.media.VideoStream;

public class XBox extends DeviceSpecification {
	
	public boolean isFileNameCompatible(String inputfileName) {
		return
			inputfileName.endsWith(".avi")  ||
			inputfileName.endsWith(".divx") ||
			inputfileName.endsWith(".mp4")  ||
			inputfileName.endsWith(".mp4v") ||
			inputfileName.endsWith(".m4v")  ||
			inputfileName.endsWith(".mov")  ||
			inputfileName.endsWith(".wmv");
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
		
		if(info.checkContainer(ContainerFormat.AVI)) {
//			Xbox 360 supports the following for AVI:
//				·         File Extensions: .avi, .divx
//				·         Containers: AVI
//				·         Video Profiles: MPEG-4 Part 2, Simple & Advanced Simple Profile
//				·         Video Bitrate: 5 Mbps with resolutions of 1280 x 720 at 30fps. See question number 11 for more information.
//				·         Audio Profiles: Dolby® Digital 2 channel and 5.1 channel, MP3
//				·         Audio Max Bitrate: No restrictions. See question number 11 for more information.
			List<VideoStream> videoStreams = info.getVideoStreams();
			if(videoStreams == null || videoStreams.size() != 1 ) {
				return false;
			}
			VideoStream video = videoStreams.get(0);
			if(!video.checkFormat(VideoFormat.MPEG4)) {
				return false;
			}
			if(!video.checkBitrate(5000)) {
				return false;
			}
			//Let's be a bit aggressive here, and not check for the profile... this gets the divx files through
			/*
			if(!video.checkMPEGProfile(3.0, MPEGProfile.SIMPLE,MPEGProfile.ADVANCEDSIMPLE,MPEGProfile.STREAMINGVIDEO)) {
				return false;
			}*/
			
			//The Xbox doesn't support the Divx3 or 4 codecs...
			if(video.getCodecId() != null) {
				if(video.getCodecId().equals("DIV3")) {
					return false;
				}
				if(video.getCodecId().equals("DIVX")) {
					if(video.getCodecIdHint() != null && video.getCodecIdHint().startsWith("DivX 4")) {
						return false;
					}
				}
				
			}
			
			
			if(!video.checkFrameRate(30.0)) {
				return false;
			}
			if(!video.checkResolution(1280,720)) {
				return false;
			}
			List<AudioStream> audioStreams = info.getAudioStreams();
			if(audioStreams == null || audioStreams.size() != 1 ) {
				return false;
			}
			AudioStream audio = audioStreams.get(0);
			if(!audio.checkFormats(AudioFormat.AC3,AudioFormat.MP3)) {
				return false;
			}
			if(!audio.checkChannels(6)) {
				return false;
			}
			return true;
		} else if(info.checkContainer(ContainerFormat.MPEG4)) {

//			File Extensions: .mp4, .m4v, mp4v, .mov
//			Containers: MPEG-4, QuickTime
			
			if(!checkAudioInMPEG4Container(info)) {
				return false;
			}
			
			
			return checkVideoInMPEG4Container(info);

		} else if(info.checkContainer(ContainerFormat.WMV)) {
//			Xbox 360 supports the following for WMV:
//			File Extensions: .wmv
//			Container: asf
//			Video Profiles: WMV7 (WMV1), WMV8 (WMV2), WMV9 (WMV3), VC-1 (WVC1 or WMVA) in simple, main, and advanced up to Level 3
//			Video Bitrate: 15 Mbps with resolutions of 1920 x 1080 at 30fps. See question number 11 for more information.
//			Audio Profiles: WMA7/8, WMA 9 Pro (stereo and 5.1), WMA lossless
//			Audio Max Bitrate: No restrictions. See question number 11 for more information.
			
			//bah, MS codec on MS console ... they must support it ;)
			
			return true;
		}
		
		return false;
	}


	private boolean checkAudioInMPEG4Container(MediaInformation info) {
		List<AudioStream> audioStreams = info.getAudioStreams();
		if(audioStreams == null || audioStreams.size() != 1 ) {
			return false;
		}
		AudioStream audio = audioStreams.get(0);
		
		
//			Audio Profiles: 2 channel AAC low complexity (LC)
//			Audio Max Bitrate: No restrictions. See question number 11 for more information.
		if(!audio.checkChannels(2)) {
			return false;
		}
		if(!audio.checkFormat(AudioFormat.AAC)) {
			return false;
		}
		if(!audio.checkProfile("LC")) {
			return false;
		}
		
		return true;
	}


	private boolean checkVideoInMPEG4Container(MediaInformation info) {
		List<VideoStream> videoStreams = info.getVideoStreams();
		if(videoStreams == null || videoStreams.size() != 1 ) {
			return false;
		}
		VideoStream video = videoStreams.get(0);
		if(!video.checkFrameRate(30.0)) {
			return false;
		}
		if(video.checkFormat(VideoFormat.MPEG4)) {
//				Xbox 360 supports the following for MPEG-4:
//				Video Profiles: Simple & **Advanced Simple Profile
//				Video Bitrate: 5 Mbps with resolutions of 1280 x 720 at 30fps. See question number 11 for more information.
			if(!video.checkResolution(1280,720)) {
				return false;
			}
			if(!video.checkBitrate(5000)) {
				return false;
			}
			if(!video.checkMPEGProfiles(3.0, MPEGProfile.SIMPLE,MPEGProfile.ADVANCEDSIMPLE,MPEGProfile.STREAMINGVIDEO)) {
				return false;
			}
			
			//The Xbox doesn't support the Divx3 or 4 codecs...
			if(video.getCodecId() != null) {
				if(video.getCodecId().equals("DIV3")) {
					return false;
				}
				if(video.getCodecId().equals("DIVX")) {
					if(video.getCodecIdHint() != null && video.getCodecIdHint().startsWith("DivX 4")) {
						return false;
					}
				}
			}
			
		} else if(video.checkFormat(VideoFormat.H264)) {
//				Xbox 360 supports the following for H.264:
//				Video Profiles: Baseline, main, and high (up to Level 4.1) profiles.
//				Video Bitrate: 10 Mbps with resolutions of 1920 x 1080 at 30fps. See question number 11 for more information.
			
			if(!video.checkResolution(1920,1080)) {
				return false;
			}
			//They actually support more than 10Mbps ... and some videos are using more,
			//and we do not want to transcode videos at such high bitrate, it would take forever
			//let's then assume they support 15Mbps ...
			if(!video.checkBitrate(15000)) {
				return false;
			}
			if(!video.checkMPEGProfiles(4.1, MPEGProfile.BASELINE,MPEGProfile.MAIN,MPEGProfile.HIGH)) {
				return false;
			}
			
		} else {
			return false;
		}

		return true;
	}

}
