package com.vuze.transcoder;

public interface TranscodeOperation {
	
	public void cancel();
	
	public int getCurrentFrame();
	
	public int getNbFrames();
	
	public double getFramesPerSecond();
	
	//in seconds
	public int getETA();
	
	public boolean isFinished();

}
