package com.vuze.transcoder;

public abstract class AbstractTranscodeOperation implements TranscodeOperation {

	public int getETA() {
		double framesPerSeconds = getFramesPerSecond();
		if(framesPerSeconds > 0.0) {
			return (int) ( (getNbFrames() - getCurrentFrame()) / framesPerSeconds );
		}
		
		return Integer.MAX_VALUE;
	}

}
