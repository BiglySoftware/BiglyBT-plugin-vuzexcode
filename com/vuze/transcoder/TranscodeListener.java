package com.vuze.transcoder;

public interface TranscodeListener {
	
	public void reportProgress(TranscodeContext context, int currentFrame,int nbFrames,int framesPerSec);
	
	public void reportDone();
	
	public void reportFailed(Throwable e, boolean is_perm );
	
	public void reportNewDimensions(int width, int height);

	public void log( String str );
}
