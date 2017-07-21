package com.vuze.transcoder.devices.apple;

public class AppleTV2 extends IPad {

	protected String getDeviceName() {
		return "Apple TV 2";
	}
	
	protected int getMaxBitrate() {
		return 2500;
	}
	
	protected int getMaxVideoWidth() {
		return 640;
	}
	
	protected int getMaxVideoHeight() {
		return 480;
	}

}