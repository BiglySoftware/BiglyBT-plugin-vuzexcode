package com.vuze.transcoder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeMap;

import com.biglybt.core.util.Constants;
import com.biglybt.core.util.StringInterner;

import com.vuze.plugins.transcoder.TranscoderPlugin;

public class TranscodeProfile {
	
	String 	name;
	String	display_name;
	String	description;
	String	icon_url;
	int		icon_index;
	
	long	overallBitrateHint;
	
	String extension;
	
	boolean autoThreads;
	
	int maxWidth;
	int maxHeight;
	int pixelMod;
	
	String target;
	int videoWidth;
	int videoHeight;
	boolean autoPadding;
	AspectRatio[] aspectRatios;
	int paddingMod;
	
	boolean disableFrameRate;
	
	double maxFrameRate;

	boolean limitVideoBitrate;
	long maxSize;
	long videoTargetBitrate;
	long audioBitrate;
	
	String videoArgs;
	
	//int bps
	int audioMinBitrate;
	int audioMaxBitrate;
	int[] audioAllowedBitrates;
	
	
	int audioMaxSampleRate;
	int[] audioAllowedSampleRates;
	
	int audioMaxChannels;
	int[] audioAllowedChannels;
	
	String audioArgs;
	
	String format;
	
	List<String>	raw_options;
	
	boolean streamable;
	
	String	device;
	
	boolean	v2Supported;
		
	public 
	TranscodeProfile( 
		InputStream profileDef,
		boolean		v2_enabled )
	
		throws TranscodingException 
	{
		if ( !v2_enabled ){
			
			LineNumberReader lnr = new LineNumberReader( new InputStreamReader( profileDef, Constants.UTF_8 ));		
			
			StringBuilder truncated = new StringBuilder();
			
			try{
				while( true ){
				
					String line = lnr.readLine();
					
					if ( line == null ){
						
						break;
					}
					
					String lc = line.toLowerCase( Locale.US ).trim();
					
					if ( lc.contains( "v2-supported=true" ) && !lc.startsWith( "#" )){
						
						break;
					}
					
					truncated.append( line );
					truncated.append( "\r\n"  );
				}
			}catch( Throwable e ){
				
				throw( new TranscodingException( e ));
				
			}finally{
				
				try{
					lnr.close();
					
				}catch( Throwable e ){
					
				}
			}
			
			profileDef = new ByteArrayInputStream( truncated.toString().getBytes( Constants.UTF_8 ));
		}
		
		Properties properties = new Properties();
		try {
			properties.load(profileDef);
			
			name = getStringProperty(properties,"name",null);
			
			if ( name == null ){
				throw( new TranscodingException( "Invalid Profile: name missing" ));
			}
			description = getStringProperty(properties,"description",null);
			icon_url = getStringProperty(properties,"icon_url",null);
			if (icon_url != null && icon_url.length() > 0 && !icon_url.startsWith("http")) {
				try {
  				String root = TranscoderPlugin.pluginInterface.getPluginDirectoryName();
  				File f = new File(new File(new File(root, "profiles"), "images"), icon_url);
  				icon_url = f.toURI().toString();
  			} catch (Throwable t) {
  			}
			}
			icon_index = getIntProperty(properties,"icon_index",0);
			extension = getStringProperty(properties,"file-extension",null);
			overallBitrateHint = getIntProperty(properties, "overall-bitrate-hint", 0);
			
			autoThreads = getBooleanProperty(properties, "auto-threads", false);
			
			target = getStringProperty(properties, "video-target", null);
			maxWidth = getIntProperty(properties,"video-max-width",0);
			maxHeight = getIntProperty(properties,"video-max-height",0);
			pixelMod = getIntProperty(properties,"video-pixel-mod",8);
			maxFrameRate = getDoubleProperty(properties,"max-frame-rate",0);
			videoArgs = getStringProperty(properties, "video-args", null);
			
			limitVideoBitrate = getBooleanProperty(properties, "video-limit-bitrate", false);
			maxSize = getLongProperty(properties,"maxsize",0);
			videoTargetBitrate = getLongProperty(properties, "video-base-bitrate", 0);
			audioBitrate = getLongProperty(properties, "video-audio-bitrate", 0);
			
			videoWidth = getIntProperty(properties, "video-width", 0);
			videoHeight = getIntProperty(properties, "video-height", 0);
			aspectRatios = getRatiosProperty(properties,"aspect-ratios",null);
			autoPadding = getBooleanProperty(properties, "auto-padding", false);
			paddingMod = getIntProperty(properties, "padding-mod", 1);
			
			disableFrameRate = getBooleanProperty(properties, "video-disable-frame-rate", false);
			
			audioMinBitrate = getIntProperty(properties,"audio-min-bitrate",0);
			audioMaxBitrate = getIntProperty(properties,"audio-max-bitrate",0);
			audioMaxSampleRate = getIntProperty(properties,"audio-max-sample-rate",0);
			audioMaxChannels = getIntProperty(properties,"audio-max-channels",0);
			audioArgs = getStringProperty(properties, "audio-args", null);
			audioAllowedSampleRates = getIntListProperty(properties, "audio-allowed-sample-rates", null);
			audioAllowedBitrates = getIntListProperty(properties, "audio-allowed-bitrates", null);
			audioAllowedChannels = getIntListProperty(properties, "audio-allowed-channels", null);
			
			format = getStringProperty(properties,"container-format",null);
			
			streamable = getStringProperty(properties,"streamable","no").equalsIgnoreCase( "yes" );
			
			device = getStringProperty(properties,"device","generic");

			Map<Object,Object> sorted_raw = 
				new TreeMap<Object, Object>(properties );
					
			for ( Map.Entry<Object,Object> entry: sorted_raw.entrySet()){
				
				String	key 	= ((String)entry.getKey()).trim();
				String	value 	= ((String)entry.getValue()).trim();
				
				if ( key.startsWith( "raw" )){
					
					if ( raw_options == null ){
						
						raw_options = new ArrayList<String>();
					}
					
					raw_options.add( value );
				}
			}
			
			v2Supported = getBooleanProperty( properties,"v2-supported", false );
			
		}catch( TranscodingException e ){
			
			throw( e );
			
		} catch (IOException e) {
			throw new TranscodingException("Invalid Profile",e);
		} catch(Throwable t) {
			throw new TranscodingException("Invalid Profile",t);
		}
	}
	
	public int getIntProperty(Properties properties,String key,int defaultValue) {
		String value = properties.getProperty(key, null);
		if(value != null) {
			try {
				return Integer.parseInt(value);
			} catch (Throwable t) {
			
			}
		}
		
		return defaultValue;
	}
	
	public long getLongProperty(Properties properties,String key,long defaultValue) {
		String value = properties.getProperty(key, null);
		if(value != null) {
			try {
				return Long.parseLong(value);
			} catch (Throwable t) {
			
			}
		}
		
		return defaultValue;
	}
	
	public double getDoubleProperty(Properties properties,String key,double defaultValue) {
		String value = properties.getProperty(key, null);
		if(value != null) {
			try {
				return Double.parseDouble(value);
			} catch (Throwable t) {
			
			}
		}
		
		return defaultValue;
	}
	
	public boolean getBooleanProperty(Properties properties,String key,boolean defaultValue) {
		String value = properties.getProperty(key,null);
		if(value != null) {
			try {
				return Boolean.parseBoolean(value);
			} catch (Exception e) {
				return defaultValue;
			}
		}
		return defaultValue;
	}
	
	public AspectRatio[] getRatiosProperty(Properties properties,String key,AspectRatio[] defaultValue) {
		String[] aspectRatios = getListProperty(properties, key, null);
		if(aspectRatios != null) {
			List<AspectRatio> result = new ArrayList<AspectRatio>(aspectRatios.length);
			for(String aspectRatio : aspectRatios) {
				try {
					AspectRatio ar = new AspectRatio();
					StringTokenizer st = new StringTokenizer(aspectRatio,":");
					if(st.countTokens() == 2) {
						ar.numerator = Integer.parseInt(st.nextToken());
						ar.denominator = Integer.parseInt(st.nextToken());
						result.add(ar);
					}
				} catch (Exception e) {
					
				}
			}
			if(result.size() > 0) {
				AspectRatio[] results = result.toArray(new AspectRatio[result.size()]);
				Arrays.sort(results);
				return results;
			}
		}
		return defaultValue;
	}
	
	public String getStringProperty(Properties properties,String key,String defaultValue) {
		String value = properties.getProperty(key, defaultValue);
		if ( value != null ){
			value = StringInterner.intern( value );
		}
		return value;
	}
	
	
	public String[] getListProperty(Properties properties,String key,String[] defaultValue) {
		String value = properties.getProperty(key, null);
		if(value == null) {
			return defaultValue;
		}
		return value.split(",");
		
	}
	
	public int[] getIntListProperty(Properties properties,String key,int[] defaultValue) {
		String[] values = getListProperty(properties, key, null);
		if(values == null) {
			return defaultValue;
		}
		int[] result = new int[values.length];
		for(int i = 0 ; i < values.length ; i++) {
			try {
				result[i] = Integer.parseInt(values[i]);
			} catch (Throwable t) {
				
			}
		}
		
		return result;
	}

	public String getName() {
		return name;
	}
	public String getDescription() {
		return description;
	}
	public String getIconURL() {
		return icon_url;
	}
	public int
	getIconIndex()
	{
		return( icon_index );
	}
	
	public String getExtension() {
		return extension;
	}

	public String
	getFormat()
	{
		return( format );
	}
	
	public String
	getDevice()
	{
		return( device );
	}
	
	public boolean
	isStreamable()
	{
		return( streamable );
	}
	
	public boolean
	isV2Supported()
	{
		return( v2Supported );
	}
	
	public long getEstimatedTranscodeSize(int durationSecs) {
		long size = ((long) durationSecs) * overallBitrateHint / 8;
		if(maxSize != 0 && size > maxSize) {
			size = maxSize;
		}
		
		return size;
	}

	/**
	 * @return null if no display name
	 */
	public String getDisplayName() {
		return display_name;
	}
}
