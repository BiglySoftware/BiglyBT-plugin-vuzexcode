package com.vuze.transcoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.PluginInterface;


public class Transcoder {

	private File vzspath_exe;
	
	TranscodeContext context;
	
	public Transcoder(PluginInterface plugin_interface, String[] ffmpegPaths,String input ) throws TranscodingException {
				
		if ( Constants.isWindows && plugin_interface != null ){

			String plugin_dir_name = plugin_interface.getPerUserPluginDirectoryName();

			vzspath_exe = new File( plugin_dir_name, "vzspath.exe" );
		}
		
		context = new TranscodeContext();
		
		for ( String s: ffmpegPaths ){
			
			if ( s == null || s.isEmpty()){
				
				continue;
			}
			
			File f = new File( s );
			
			if (!f.exists()){
				
				throw new TranscodingException( "ffmpeg not found at " + s); 
			}
		}
		
		context.ffmpegPaths = ffmpegPaths;
		
		context.setOriginalInputFileName( input );

		if ( 	input.startsWith( "http://" ) || 
				input.startsWith( "https://" ) || 
				input.startsWith( "tcp://" )){
			
		}else{
		
			if ( !new File( input ).exists()){
				
				throw new TranscodingException("File " + input + " does not exists"); 
			}
			
			if ( Constants.isWindows ){
				
				try{
					if ( !input.equals( new String( input.getBytes( "ISO8859-1" ), "ISO8859-1" ))){
					
						String mod_input = getShortFileName( vzspath_exe, input );
						
						if ( new File( mod_input ).exists()){
							
							context.setModifiedInputFileName( mod_input );
						}
					}
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		}
		
	}

	public TranscodeOperation transcode(TranscodeListener listener,TranscodeProfile profile, String outputFileName) {
		context.listener = listener;
		context.profile = profile;
		
		context.setOriginalOutputFileName( outputFileName );
		
		if ( Constants.isWindows ){
			
			if ( 	outputFileName.startsWith( "http://" ) || 
					outputFileName.startsWith( "https://" ) || 
					outputFileName.startsWith( "tcp://" )){
			}else{
				
				File output_dir = new File( outputFileName ).getParentFile();

				if ( output_dir.exists()){
					
					try{
						if ( !outputFileName.equals( new String( outputFileName.getBytes( "ISO8859-1" ), "ISO8859-1" ))){
										
							File mod_output_dir = new File( getShortFileName( vzspath_exe, output_dir.getAbsolutePath()));
							
							if ( mod_output_dir.exists()){
																
								File temp = File.createTempFile( "AZU", ".tmp", mod_output_dir );
								
								String mod_output_file = temp.getAbsolutePath();
								
								temp.delete();
								
								context.setModifiedOutputFileName( mod_output_file );
							}
						}
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
			}
		}
		
		AbstractTranscodeOperation operation;
		
		if ( profile.isV2Supported()){
			
			operation = new InternalTranscodeOperation2( context);

		}else{
			
			operation = new InternalTranscodeOperation( context);
		}
		
		operation.start();
		
		return operation;
	}

	public TranscodeContext getContext() {
		return context;
	}
	
	private String
	getShortFileName(
		File		exe,
		String		name )
	{
			
		Process process = null;
		
		try{
			name = new File( name ).getAbsolutePath();
			
			ProcessBuilder pb =  createProcessBuilder( null, new String[]{ exe.getAbsolutePath(), name }, null );
						
			process = pb.start();
			
			InputStream is = process.getInputStream();
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			
			while( true ){
				
				byte[]	buffer = new byte[4096];
				
				int	len = is.read( buffer );
				
				if ( len <= 0 ){
					
					break;
				}
				
				baos.write( buffer, 0, len );
			}
			
			String result = new String( baos.toByteArray()).trim();
			
			if ( result.length() > 0 ){
				
				File short_file = new File( result );
			
				if ( short_file.exists()){
				
					name = short_file.getAbsolutePath();
				}
			}
						
		}catch( Throwable e ){
		
			Debug.out( e );
			
		}finally{
			
			if ( process != null ){
				
				try{
					process.destroy();
					
				}catch( Throwable e ){
					
					Debug.printStackTrace( e );
				}
			}
		}
		
		return( name );
	}
	
	public static ProcessBuilder 
	createProcessBuilder(
		File workingDir,
		String[] cmd, 
		String[] extra_env) 
	
		throws IOException 
	{
		ProcessBuilder pb;

		Map<String, String> newEnv = new HashMap<String, String>();
		newEnv.putAll(System.getenv());
		newEnv.put("LANG", "C.UTF-8");
		if (extra_env != null && extra_env.length > 1) {
			for (int i = 1; i < extra_env.length; i += 2) {
				newEnv.put(extra_env[i - 1], extra_env[i]);
			}
		}

		if ( Constants.isWindows ){
			String[] i18n = new String[cmd.length + 2];
			i18n[0] = "cmd";
			i18n[1] = "/C";
			i18n[2] = escapeDosCmd(cmd[0]);
			for (int counter = 1; counter < cmd.length; counter++) {
				if (cmd[counter].length() == 0) {
					i18n[counter + 2] = "";
				} else {
					String envName = "JENV_" + counter;
					i18n[counter + 2] = "%" + envName + "%";
					newEnv.put(envName, cmd[counter]);
				}
			}
			cmd = i18n;
		}

		pb = new ProcessBuilder(cmd);
		Map<String, String> env = pb.environment();
		env.putAll(newEnv);

		if (workingDir != null) {
			pb.directory(workingDir);
		}
		return pb;
	}

	private static String escapeDosCmd(String string) {
		String s = string.replaceAll("([&%^])", "^$1");
		s = s.replaceAll("'", "\"'\"");
		return s;
	}
}
