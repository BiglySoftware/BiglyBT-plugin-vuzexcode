package com.vuze.transcoder;

import java.io.File;

import com.vuze.transcoder.devices.DeviceSpecification;
import com.vuze.transcoder.media.GeneralStream;
import com.vuze.transcoder.media.MediaInformation;
import com.vuze.transcoder.media.VideoStream;

public class TranscodeContext {
	
	String[] ffmpegPaths;
	
	private String originalInputFileName;
	private String modifiedInputFileName;
	
	private String originalOutputFileName;
	private String modifiedOutputFileName;
	
	TranscodeProfile profile;
	
	TranscodeOperation operation;
	
	MediaInformation mediaInformation;
	DeviceSpecification deviceSpecification;
	//Set to true because this creates too many errors
	//The correct fix includes using tools like mkvdemux and mp4box
	//ffmpeg just can't handle (at this point) non monotone timestamps
	//and most h264 streams seem to be like that
	//TODO : remove / update once we get a better ffmpeg, or change the workflow
	boolean forceTranscode = true;
	
	int threads;
	
	TranscodeListener listener;
		
		// input file names
	
	protected void
	setOriginalInputFileName(
		String		str )
	{
		originalInputFileName 	= str;
		modifiedInputFileName	= str;
	}
	
	protected String
	getOriginalInputFileName()
	{
		return( originalInputFileName );
	}
	
	protected void
	setModifiedInputFileName(
		String		str )
	{
		modifiedInputFileName	= str;
	}
	
	protected String
	getModifiedInputFileName()
	{
		return( modifiedInputFileName );
	}
	
		// output file names
	
	protected void
	setOriginalOutputFileName(
		String		str )
	{
		originalOutputFileName 	= str;
		modifiedOutputFileName	= str;
	}
	
	protected void
	setModifiedOutputFileName(
		String		str )
	{
		modifiedOutputFileName	= str;
	}
	
	protected String
	getOriginalOutputFileName()
	{
		return( originalOutputFileName );
	}
	
	protected String
	getModifiedOutputFileName()
	{
		return( modifiedOutputFileName );
	}
	
	int nbFrames = 0;
	
	public void 
	reportDone()
	{
		if ( modifiedOutputFileName != originalOutputFileName ){
			
			File f_orig 	= new File( originalOutputFileName );
			File f_mod	 	= new File( modifiedOutputFileName );
			
			int	loop = 0;
			
			while( f_orig.exists()){
			
				f_orig.delete();
				
				loop++;
				
				if ( loop > 10 ){
					
					break;
					
				}else if ( loop > 1 ){
					
					try{
						
						Thread.sleep(1000);
						
					}catch( Throwable e ){
						
					}
				}
			}
			
			loop = 0;
			
			while( !f_mod.renameTo( f_orig )){

				loop++;
				
				if ( loop > 10 ){
					
					break;	
				}
				
				try{
					
					Thread.sleep(1000);
					
				}catch( Throwable e ){
					
				}
			}
		}
		
		if(listener != null) {
			listener.reportDone();
		}
	}
	
	public void reportFailed(Throwable e, boolean perm_fail ) {
				
		if ( modifiedOutputFileName != originalOutputFileName ){
			
			new File( modifiedOutputFileName ).delete();
		}
		
		if(listener != null) {
			listener.reportFailed(e,perm_fail);
		}
	}
	
	public void reportProgress(int currentFrame, int framesPerSec) {
		if(nbFrames == 0) {
			computeNbFrames();
		}
		if(nbFrames != 0) {
			if(listener != null) {
				listener.reportProgress(this,currentFrame, nbFrames, framesPerSec);
			}
		}
	}
	
	private void computeNbFrames() {
		if(mediaInformation != null) {
			GeneralStream generalStream = mediaInformation.getGeneralStream();
			VideoStream videoStream = mediaInformation.getFirstVideoStream();
			if(generalStream != null && videoStream != null) {
				nbFrames = (int) ((double)generalStream.getDurationSecs() * videoStream.getFrameRate());
			}
		}
	}

	public MediaInformation getMediaInformation() {
		return mediaInformation;
	}

	public void setMediaInformation(MediaInformation mediaInformation) {
		this.mediaInformation = mediaInformation;
	}

	public void setDeviceSpecification(DeviceSpecification deviceSpecification) {
		this.deviceSpecification = deviceSpecification;
	}

	public void setForceTranscode(boolean forceTranscode) {
		this.forceTranscode = forceTranscode;
	}
	
	public void log( String str ){
		if ( listener != null ){
			listener.log( str );
		}else{
			System.out.println( str );
		}
	}

	public void setThreads(int threads) {
		this.threads = threads;
	}
}
