package com.vuze.transcoder.devices.apple;

public class IPodClassic extends IPod {

	protected String getDeviceName() {
		return "iPod classic";
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
