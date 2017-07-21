package com.vuze.transcoder.devices.apple;

public class IPhone extends IPod {

	protected String getDeviceName() {
		return "iPhone";
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
