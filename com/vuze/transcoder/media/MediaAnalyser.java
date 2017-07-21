package com.vuze.transcoder.media;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;


import com.vuze.plugins.transcoder.TranscoderPlugin;
import com.vuze.transcoder.TranscodingException;
import com.vuze.transcoder.devices.DeviceSpecification;
import com.vuze.transcoder.devices.DeviceSpecificationFinder;

public class MediaAnalyser {
	
	TranscoderPlugin plugin;
	String mediaInfoPath;
	Process analyser_process;
	boolean cancelled;
	
	public MediaAnalyser(TranscoderPlugin plugin,String mediaInfoPath) {
		this.plugin=plugin;
		this.mediaInfoPath = mediaInfoPath;
		this.cancelled = false;
		this.analyser_process = null;
	}
	
	public MediaInformation analyse(String inputFileName) throws TranscodingException {
		MediaInformation info = new MediaInformation();
		
		String[] command = new String[] {
				mediaInfoPath,
				inputFileName,
		};
		
		try {
			analyser_process = Runtime.getRuntime().exec(command);
			
			if ( cancelled ){
				
				analyser_process.destroy();
				
				throw( new Exception( "Operation cancelled" ));
			}
			
			InputStream is = analyser_process.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			String line = null;
			while( (line = br.readLine()) != null) {
				if ( plugin != null ){
					plugin.log( "    " + line );
				}
				info.parseInfoLine(line);
			}
		} catch(Exception e) {
			
			throw new TranscodingException("Exception while gathering file information",e);
			
		}finally{
			
			analyser_process = null;
		}
		
		return info;
	}
	
	
	public void
	cancel()
	{
		cancelled = true;
		
		Process p = analyser_process;
		
		if ( p != null ){
			
			p.destroy();
		}
	}
	
	public String getCompleteMediaInformation(String inputFileName) throws TranscodingException {
		StringBuffer sb = new StringBuffer();
		
		String[] command = new String[] {
				mediaInfoPath,
				"-f",
				inputFileName,
		};
		
		try {
			analyser_process = Runtime.getRuntime().exec(command);
			
			if ( cancelled ){
				
				analyser_process.destroy();
				
				throw( new Exception( "Operation cancelled" ));
			}
			
			InputStream is = analyser_process.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			String line = null;
			while( (line = br.readLine()) != null) {
				if(line.startsWith("Complete name") || line.startsWith("Folder name") || line.startsWith("File name")) {
					int pos = line.indexOf(": ");
					if(pos > 0) {
						line = line.substring(pos+2);
						line = line + "---";
					}
				}
				sb.append(line);
				sb.append("\n");
			}
		} catch(Exception e) {
			
			throw new TranscodingException("Exception while gathering file information",e);
			
		}finally{
			
			analyser_process = null;
		}
		
		return sb.toString();
	}
	


}
