package com.vuze.transcoder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.ShellUtilityFinder;

import com.vuze.transcoder.media.AudioStream;
import com.vuze.transcoder.media.ContainerFormat;
import com.vuze.transcoder.media.MediaInformation;
import com.vuze.transcoder.media.VideoFormat;
import com.vuze.transcoder.media.VideoStream;

public class InternalTranscodeOperation extends AbstractTranscodeOperation {

	private static final int MAX_UNPARSED_OUTPUT_CAPTURED	= 48*1024;
	
	private static Pattern progressPattern = Pattern.compile("frame=\\s*([0-9]*) fps=\\s*([0-9]*) .*");
	private static Pattern progressPatternDups = Pattern.compile("frame=\\s*([0-9]*) fps=\\s*([0-9]*) .*?dup=\\s*([0-9]*) .*");
	private static Pattern finalLinePattern = Pattern.compile("video:.*?B audio:.*?B global headers:.*?B muxing overhead .*?%");

	private AEThread2 runner;
	
	private volatile boolean canceled = false;
	private volatile boolean done = false;
	
	private TranscodeContext context;
	
	private volatile int currentFrame;
	private volatile int currentFPS;
	
	private StringBuffer unParsedOutput;
	private volatile boolean finalLineSeen = false;
	

	private volatile Process	active_process;
	
	public InternalTranscodeOperation(TranscodeContext context) {
		
		this.context = context;
		
		this.unParsedOutput = new StringBuffer();
		
		initialize();
	}
	
	private void initialize() {
		
		context.operation = this;
		
		runner = new AEThread2("Trancoder for " + context.getOriginalInputFileName(),true) {
			public void run() {
				
				String[] command = null;
				
				try {
					command = buildCommand();
					
				} catch(Throwable e) {
					
					String text = e.getMessage();
					if(context.mediaInformation != null) {
						text += "\n" + context.mediaInformation.getRawOutput();
					}
					
					context.reportFailed( new Exception( text ), true );
					
					return;
				}
				
				try {
					active_process = Runtime.getRuntime().exec(command);
					InputStream is = active_process.getErrorStream();
					BufferedReader br = new BufferedReader(new InputStreamReader(is));
					String line = null;
					String last_line = null;
					boolean	matched_at_least_one = false;							
					
					while( !canceled && (line = br.readLine()) != null) {
	
						//System.out.println(line);
						if ( parseProgress(line)){
							
							matched_at_least_one = true;
						}
						
						last_line = line;
					}
					
					//System.out.println(unParsedOutput);
					
					
					if ( canceled ){
						
						active_process.destroy();
						
						context.reportFailed( new Exception( "Cancelled" ), true );
						
					}else{
						
						int exitCode = active_process.waitFor();
						
						boolean partialFile = unParsedOutput.indexOf("partial file\n") != -1;
						
						if ( 	!matched_at_least_one || 
								!finalLineSeen || 
								exitCode != 0 || 
								partialFile ){
							
							String text;
							
							try{
								StringBuffer exceptionText = new StringBuffer();

								buildBaseExceptionText(matched_at_least_one,
										exitCode, partialFile, exceptionText);
								exceptionText.append("\n");
								exceptionText.append(unParsedOutput.toString());
								exceptionText.append("\nMedia Info\n");
								if(context.mediaInformation != null) {
									exceptionText.append(context.mediaInformation.getRawOutput());
								} else {
									exceptionText.append("null");
								}
								exceptionText.append("\nCommand : \n");
								if(command != null) {
									for(String cmdLine : command) {
										if(cmdLine.indexOf(" ") != -1) {
											exceptionText.append("\"");
										}
										exceptionText.append(cmdLine);
										if(cmdLine.indexOf(" ") != -1) {
											exceptionText.append("\"");
										}
										exceptionText.append(" ");
									}
								}
								
								//Remove any info about file names
								text = exceptionText.toString();
															
								//We may rename output files with 1_, and as input file name
								//is not always available (because source may be http), we want to get
								//the original file name
								//We also want to remove the file extension
								Pattern pDuplicates = Pattern.compile("([0-9]+_)?(.*?)(\\..+)?");
								Matcher m;
								
								String inputFileName = context.getOriginalInputFileName();
								
								if(inputFileName != null) {
									if(!inputFileName.startsWith("http:")) {
										File f = new File(inputFileName);
										String fileName = f.getName();
										
										m = pDuplicates.matcher(fileName);
										if(m.matches()) {
											fileName = m.group(2);
										}
										text = text.replace(fileName, "<INPUT>");
									}
								}
								
								String outputFileName = context.getOriginalOutputFileName();
								if(outputFileName != null) {
									if(!outputFileName.startsWith("tcp:")) {
										File f = new File(outputFileName);
										String fileName = f.getName();
										m = pDuplicates.matcher(fileName);
										if(m.matches()) {
											fileName = m.group(2);
										}
										text = text.replace(fileName, "<OUTPUT>");
									}
								}
							} catch ( Throwable e ){
								//Something went wrong while removing file names, so let's not log anything
								//which may have it
								StringBuffer exceptionText = new StringBuffer();
								buildBaseExceptionText(matched_at_least_one,
										exitCode, partialFile, exceptionText);
								
								text = exceptionText.toString();
							}						
							
								// two exceptions so the first is presentable to the user with the rest as
								// diagnostic info
														
							File target_file 	= new File( context.getModifiedOutputFileName());
							File target_parent	= target_file.getParentFile();
							
							String	error_message = "Operation aborted";
							
							boolean	perm_fail = false;
							
							if ( !target_parent.exists()){
								
								error_message = "Output folder '" + target_parent.getAbsolutePath() + "' does not exist";
								
								perm_fail = true;
								
							}else if ( !target_parent.isDirectory()){
								
								error_message = "Output location '" + target_parent.getAbsolutePath() + "' is not a folder";
								
								perm_fail = true;
								
							}else if ( !target_parent.canWrite()){
								
								error_message = "Output folder '" + target_parent.getAbsolutePath() + "' is not writable";
								
								perm_fail = true;
								
							}else{
								
									// test to see if we're out of space
								
								boolean	ok = false;
								
								try{
									File test_file = File.createTempFile( "AZU", ".tmp", target_parent );
								
									FileOutputStream fos = new FileOutputStream( test_file );
									
									try{
										byte[]	buffer = new byte[100*1024];
										
										for ( int i=0; i<10; i++ ){
											
											fos.write( buffer );
										}
										
										fos.close();
										
										ok = true;

									}finally{
										
										try{
											fos.close();
											
										}catch( Throwable e ){	
										}
										
										test_file.delete();
									}
																		
								}catch( Throwable e ){
									
									e.printStackTrace();
								}
								
								if ( !ok ){
									
									error_message = "Insufficient space in '" + target_parent.getAbsolutePath() + "'";
									
									perm_fail = true;
								}
							}
							
							context.reportFailed( new Exception( error_message, new Exception( text )), perm_fail );
							
						}else{ 
						
							context.reportDone();
						
						}
					}
				} catch (Throwable t) {
					context.reportFailed( t, false );
				} finally {
					if( active_process != null) {
						active_process.destroy();
					}
				}
				done = true;
			}

			private void buildBaseExceptionText(boolean matched_at_least_one,
					int exitCode, boolean partialFile,
					StringBuffer exceptionText) {
				exceptionText.append("Failed \n(matched at least once : ");
				exceptionText.append(matched_at_least_one);
				exceptionText.append(",final line seen : ");
				exceptionText.append(finalLineSeen);
				exceptionText.append(", exit code : ");
				exceptionText.append(exitCode);
				exceptionText.append(",partial file : ");
				exceptionText.append(partialFile);
				exceptionText.append(")");
			}
		};
	}
	
	private boolean parseProgress(String line) {
		//System.out.println( line );
		Matcher matcher = progressPatternDups.matcher(line);
		if(matcher.matches()) {
			currentFrame = Integer.parseInt(matcher.group(1)) - Integer.parseInt(matcher.group(3));
			currentFPS = Integer.parseInt(matcher.group(2));			
			context.reportProgress(currentFrame, currentFPS);
			
			return( true );
		}
		
		matcher = progressPattern.matcher(line);
		if(matcher.matches()) {
			currentFrame = Integer.parseInt(matcher.group(1));
			currentFPS = Integer.parseInt(matcher.group(2));			
			context.reportProgress(currentFrame, currentFPS);
			
			return( true );
			
		}else{
			
			int	rem = MAX_UNPARSED_OUTPUT_CAPTURED - unParsedOutput.length();
			
			if ( rem > 0 ){
				
				if ( line.length() > rem ){
					
					line = line.substring( 0, rem );
				}
				
				unParsedOutput.append(line);
				unParsedOutput.append("\n");

				rem -= (line.length() + 1 );
				
				if ( rem <= 0 ){
					
					unParsedOutput.append( "...[truncated]\n" );
				}
			}
		}
		
		matcher = finalLinePattern.matcher(line);
		if(matcher.find()) {
			finalLineSeen = true;
		}
		
		return( false );
	}
	
	private String[] buildCommand() throws TranscodingException {
		TranscodeProfile profile = context.profile;

		List<String> command = new ArrayList<String>();
		
		if(Constants.isWindows) {
			
		} else {
			command.add(ShellUtilityFinder.getNice());
			command.add("-n");
			command.add("15");
		}
		
		command.add(context.ffmpegPaths[0]);
		command.add("-y");
		
		command.add("-i");
		command.add(context.getModifiedInputFileName());
		
		if (context.threads > 0) {			
			if(profile.format == null || !profile.format.equalsIgnoreCase( "flv" )) {
				command.add("-threads");
				if(profile.autoThreads) {
					command.add("0");
				} else {
					command.add("" + context.threads);
				}
			}
		}
		
		ContainerFormat containerFormat = null;
		
		if(profile.format != null) {
			command.add("-f");
			command.add(profile.format);
			containerFormat = ContainerFormat.getFromString(profile.format);
		}
		
		MediaInformation mediaInformation = context.mediaInformation;
		
		if(mediaInformation == null) {
			throw new TranscodingException("No input media information was provided to the transcoder");
		}
		
		VideoStream videoStream = mediaInformation.getFirstVideoStream();
		
		if(videoStream == null) {
			throw new TranscodingException("No video stream found");
		}
		
		VideoFormat videoFormat = null;
		
		//Video stuff
		boolean videoCopy = false;
		if(context.deviceSpecification != null && !context.forceTranscode) {
			if(context.deviceSpecification.isVideoCompatible(mediaInformation, containerFormat, profile.maxWidth, profile.maxHeight)) {
				videoCopy = true;
			}
		}
		
		if(videoCopy) {
			
			command.add("-vcodec");
			command.add("copy");
			
			videoFormat = videoStream.getVideoFormat();
			
		} else {
			
			if(profile.target != null) {
				command.add("-target");
				command.add(profile.target);
			}
			
			int currentWidth = videoStream.getWidth();
			int currentHeight = videoStream.getHeight();						
			
			if(profile.maxWidth > 0 && profile.maxHeight > 0) {
				if(currentWidth > profile.maxWidth) {
					double aspectRatio = (double)currentHeight / (double)currentWidth;
					currentWidth = profile.maxWidth;
					currentHeight = (int) (currentWidth * aspectRatio);
					currentHeight = profile.pixelMod * (int)( (currentHeight+profile.pixelMod/2) / profile.pixelMod);
					if(currentHeight > profile.maxHeight) {
						currentHeight = profile.maxHeight;
					}
				}
				if(currentHeight > profile.maxHeight) {
					double aspectRatio = (double)currentWidth / (double)currentHeight;
					currentHeight = profile.maxHeight;
					currentWidth = (int) (currentHeight * aspectRatio);
					currentWidth = profile.pixelMod * (int)( (currentWidth+profile.pixelMod/2) / profile.pixelMod);
					if(currentWidth > profile.maxWidth) {
						currentWidth = profile.maxWidth;
					}
				}
				
				command.add("-s");
				command.add(currentWidth + "x" + currentHeight);
				
				if(context.listener != null) {
					context.listener.reportNewDimensions(currentWidth, currentHeight);
				}
			}
			
			if(profile.videoWidth > 0 && profile.videoHeight > 0) {
				float aspectRatio = (float) currentWidth / (float)currentHeight;
				currentWidth = profile.videoWidth;
				currentHeight = profile.videoHeight;
				
				if(profile.aspectRatios != null && profile.aspectRatios.length > 0) {
					AspectRatio choosenAspectRatio = null;
					// aspectRatios are sorted in the profile
					// As we ideally want to add top / bottom bars, we want to pick the widest aspect ratio possible
					for(AspectRatio ar : profile.aspectRatios) {
						choosenAspectRatio = ar;
						if(aspectRatio <= ar.getValue()) {
							break;
						}			
					}
					
					if(choosenAspectRatio != null) {
						command.add("-aspect");
						command.add(choosenAspectRatio.numerator + ":" + choosenAspectRatio.denominator);
					}
					
					if(choosenAspectRatio != null && profile.autoPadding) {
						int width,height,padding;
						if(aspectRatio >= choosenAspectRatio.getValue()) {
							width = profile.videoWidth;
							height = (int) (profile.videoHeight * choosenAspectRatio.numerator / (aspectRatio * choosenAspectRatio.denominator));
							int delta = profile.videoHeight - height;
							padding = delta / 2;
							int paddingMod = profile.paddingMod;							
							if(paddingMod > 0 && padding % paddingMod != 0) {
								padding -= (padding % paddingMod);
							}
							height = profile.videoHeight - 2 * padding;
							
							command.add("-padtop");
							command.add(""+padding);
							command.add("-padbottom");
							command.add(""+padding);
							
						} else {
							height = profile.videoHeight;
							width = (int) (profile.videoWidth * choosenAspectRatio.denominator * aspectRatio / choosenAspectRatio.numerator);
							int delta = profile.videoWidth - width;
							padding = delta / 2;
							int paddingMod = profile.paddingMod;							
							if(paddingMod > 0 && padding % paddingMod != 0) {
								padding -= (padding % paddingMod);
							}
							width = profile.videoWidth - 2 * padding;
							
							command.add("-padleft");
							command.add(""+padding);
							command.add("-padright");
							command.add(""+padding);
							
						}
						
						currentHeight = height;
						currentWidth = width;
					}
					
					command.add("-s");
					command.add(currentWidth + "x" + currentHeight);
					
				}
			}


			if(profile.maxFrameRate != 0.0 && profile.maxFrameRate < videoStream.getFrameRate()) {
				command.add("-r");
				command.add("" + profile.maxFrameRate);
			} else {
				//Same as source, unless there isn't one !
				if(videoStream.getFrameRate() > 0 && ! profile.disableFrameRate) {
					command.add("-r");
					command.add("" + videoStream.getFrameRate());
				}
			}
			
			if(profile.limitVideoBitrate && profile.videoTargetBitrate > 0 && profile.audioBitrate > 0 && profile.maxSize > 0) {
				int duration = videoStream.getDurationSecs();
				if(duration > 0) {
					long videoBitrate = profile.videoTargetBitrate;
					long audioSize = profile.audioBitrate * duration / 8;
					long videoSize = videoBitrate * duration / 8;
					if(videoSize + audioSize > profile.maxSize) {
						videoBitrate = (profile.maxSize - audioSize) * 8 / duration;
					}
					command.add("-b");
					command.add(""+videoBitrate);
				}
			}
			
			if(profile.videoArgs != null) {
				StringTokenizer st = new StringTokenizer(profile.videoArgs," ");
				while(st.hasMoreTokens()) {
					String token = st.nextToken();
					token = token.trim();
					
					if(token.endsWith(".ffpreset")) {
						File presetFolder = new File((new File(context.ffmpegPaths[0])).getParentFile(),"profiles");
						File presetFile = new File(presetFolder,token);
						token = presetFile.getAbsolutePath();
						
					}
					command.add(token);
				}
			}
			
		}
		
		
		
		//Audio stuff
		boolean audioCopy = false;
		if(context.deviceSpecification != null  && !context.forceTranscode) {
			if(context.deviceSpecification.isAudioCompatible(mediaInformation, containerFormat, videoFormat)) {
				audioCopy = true;
			}
		}

		AudioStream audioStream = mediaInformation.getFirstAudioStream();
		if(audioStream == null) {
			throw new TranscodingException("No audio stream found");
		}
		
		if(audioCopy) {
			
			command.add("-acodec");
			command.add("copy");
			
		} else {
			
			List<String>	audio_args = new ArrayList<String>();
			
			if ( profile.audioArgs != null ){
				StringTokenizer st = new StringTokenizer(profile.audioArgs," ");
				while(st.hasMoreTokens()) {
					audio_args.add(st.nextToken());
				}
			}
			int sampleRate = getConstrainedParameter(audioStream.getSampleRate(),profile.audioMaxSampleRate,profile.audioAllowedSampleRates);
			
			if(sampleRate > 0 && !audio_args.contains( "-ar" )){
				command.add("-ar");
				command.add("" + sampleRate);
			}
			
			int channels = getConstrainedParameter(audioStream.getChannels(), profile.audioMaxChannels, profile.audioAllowedChannels);
			
			if(channels > 0 && !audio_args.contains( "-ac" )){
				command.add("-ac");
				command.add("" + channels);
			}
			
			int bitrate = audioStream.getBitrate()*1024;
			
			if ( profile.audioMinBitrate > bitrate ){
				
				bitrate = profile.audioMinBitrate;
			}
			
			int maxbitrate = getConstrainedParameter(bitrate, profile.audioMaxBitrate, profile.audioAllowedBitrates);
			
			if( maxbitrate > 0 && !audio_args.contains( "-ab" )) {
				command.add("-ab");
				command.add("" + maxbitrate);
			}
			
			for ( String arg: audio_args ){
				
				command.add( arg );
			}

			
		}

		
		
		List<String> raw_params = profile.raw_options;
		
		if ( raw_params != null ){
			
			for ( String raw_param: raw_params ){
		
				int	pos = raw_param.indexOf( ' ' );
				
				if ( pos == -1 ){
					
					command.add( raw_param );
					
				}else{
					
					command.add( raw_param.substring( 0, pos ));
					command.add( raw_param.substring( pos+1 ));
				}
			}
		}
		
		command.add(context.getModifiedOutputFileName());
		
		String str_cmd = "";
		
		for(String t:command) {
			str_cmd += (str_cmd.length()==0?"":" ") + t;
		}
		
		context.log( str_cmd );
		
		return (String[]) command.toArray(new String[command.size()]);
	}

	private int getConstrainedParameter(int sourceValue,int maxValue,int[] allowedValues) {
		
		int value = sourceValue;
		
		//Apply the max value if there's one
		if(maxValue != 0 && maxValue < value) {
			value = maxValue;
		}
		
		
		//If the values are constrained by a set of pre-defined values, let's find the closest one
		if(allowedValues != null && sourceValue > 0) {
			//Let's pick the highest available value to start with
			int closestValue = maxValue;
			//Hence the error the is the diff between source and max
			int error = abs(sourceValue - maxValue);
			
			//Let's then iterate over all the available values, and pick the closest one
			for(int allowedValue:allowedValues) {
				if(abs(allowedValue-sourceValue) < error) {
					closestValue = allowedValue;
					error = abs(allowedValue-sourceValue);
				}
			}
			
			value = closestValue;
		}
		
		return value;
	}
	
	public void start() {
		runner.start();
	}
	
	public void cancel() {
		canceled = true;
		
		if ( active_process != null ){
			
			active_process.destroy();
		}
	}

	public int getCurrentFrame() {
		return currentFrame;
	}

	public double getFramesPerSecond() {
		return currentFPS;
	}

	public int getNbFrames() {
		return context.nbFrames;
	}
	
	public boolean isFinished() {
		return done;
	}
	
	private int abs(int n) {
		return n > 0 ? n : -n;
	}

}