package com.vuze.transcoder.devices;

import com.vuze.transcoder.media.ContainerFormat;
import com.vuze.transcoder.media.MediaInformation;
import com.vuze.transcoder.media.VideoFormat;

public abstract class DeviceSpecification {
	
	public boolean needsTranscoding(MediaInformation info) {
		return ! isCompatible(info);
	}
	
	//The format, video and audio might each be compatible with the device
	//but their combination may not be
	public abstract boolean isCompatible(MediaInformation info);
	
	/**
	 * 
	 * @param info the media information about the file being transcoded
	 * @param format the container format to which the video will be muxed
	 * @param maxWidth the max desired width for the video or 0 if no constraint
	 * @param maxHeight the max desired height for the video or 0 if no constraint
	 * @return true if the video stream can be simply copied
	 */
	public abstract boolean isVideoCompatible(MediaInformation info,ContainerFormat format,int maxWidth,int maxHeight);
	
	/**
	 * 
	 * @param info the media information about the file being transcoded
	 * @param containerFormat the container format to which the audio will be muxed
	 * @param videoFormat the video format with which the audio will be muxed
	 * @return true if the video stream can be simply copied
	 */
	public abstract boolean isAudioCompatible(MediaInformation info,ContainerFormat containerFormat,VideoFormat videoFormat);
	
	public abstract boolean isFileNameCompatible(String inputfileName);

}
