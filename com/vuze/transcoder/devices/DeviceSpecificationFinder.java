package com.vuze.transcoder.devices;

import java.util.*;

import com.vuze.plugins.transcoder.TranscoderPlugin;

public class 
DeviceSpecificationFinder
{
	private static Set<String>	logged = new HashSet<String>();
	
	public static DeviceSpecification 
	getDeviceSpecificationByDeviceName(
		TranscoderPlugin	plugin,
		String 				deviceName )
	{	
		try{
			Class<? extends DeviceSpecification> clazz = 
				Class.forName("com.vuze.transcoder.devices." + deviceName).asSubclass( DeviceSpecification.class );
			
			return clazz.newInstance();
			
		}catch( ClassNotFoundException e ){
			
			synchronized( logged ){
				
				if ( logged.contains( deviceName )){
					
					return ( null );
				}
				
				logged.add(deviceName);
			}
			
			plugin.log( "Custom device specification for '" + deviceName + "' not available" );
			
		}catch (Throwable e){
			
			e.printStackTrace();
		}
		
		return null;	
	}
}
