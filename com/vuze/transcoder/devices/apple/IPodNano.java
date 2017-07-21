package com.vuze.transcoder.devices.apple;

public class IPodNano extends IPod {

	protected String getDeviceName() {
		return "iPod nano";
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
