/*
 * Created on Oct 25, 2010
 * Created by Paul Gardner
 * 
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.vuze.transcoder;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;

import com.biglybt.core.devices.*;
import com.biglybt.core.devices.TranscodeProfile;
import com.biglybt.core.util.UUIDGenerator;
import com.vuze.plugins.transcoder.TranscoderPlugin;

public class 
TranscoderDeviceDiscoverer 
{
	private final static boolean ADD_DLNA_UPNP_DEVICES = true;
	private static String TV_ICON = "samsung";
	private TranscoderPlugin				plugin;
	private DeviceManagerDiscoveryListener	dm_discovery_listener;
	
	private Set<String>		logged_addresses = new HashSet<String>();
	
	
	public
	TranscoderDeviceDiscoverer(
		TranscoderPlugin		_plugin )
	{
		plugin	= _plugin;
		
		final DeviceManager deviceManager = plugin.getDeviceManager();

		if ( deviceManager != null ){
			
			dm_discovery_listener = new 
				DeviceManagerDiscoveryListener()
				{
					public boolean
					browseReceived(
						TrackerWebPageRequest		request,
						Map<String,Object>			browser_args )
					{
						InetSocketAddress client_address = request.getClientAddress2();
	
						boolean	log;
						
						synchronized( logged_addresses ){
						
							String	ip = client_address.getAddress().getHostAddress();
							
							log = !logged_addresses.contains( ip );
							
							if ( log ){
								
								logged_addresses.add( ip );
							}
						}
						
						Map headers = request.getHeaders();
	
						if ( log ){
							
							plugin.log( client_address + ": browseReceived: " + headers );
						}
									
							// handle sony headers
						
						String client_info 	= (String)headers.get( "x-av-client-info" );
		
						if ( client_info != null ){
							
							client_info = client_info.toLowerCase();
							
							if ( client_info.contains( "sony" )){
								
								if ( client_info.contains( "blu-ray" )){
								
									handleSonyBluRay( deviceManager, client_address );
								
									return( true );
									
								}else if ( client_info.contains( "internet tv box" )){
									
									handleSonyInternetTV( deviceManager, client_address );
									
									return( true );
								}
							}
						}
								// {friendlyname.dlna.org=LG DLNA DMP DEVICE, host=192.168.0.10:50900, user-agent=Linux/2.6.31-1.0 UPnP/1.0 DLNADOC/1.50 INTEL_NMPR/2.0 LGE_DLNA_SDK/1.5.0, status=GET /RootDevice.xml HTTP/1.1,
							
						String dlna_friendly_name = (String)headers.get( "friendlyname.dlna.org" );
							
						if ( dlna_friendly_name != null ){
								
							dlna_friendly_name = dlna_friendly_name.toLowerCase();
								
							if ( dlna_friendly_name.startsWith( "lg dlna dmp" )){
									
								handleLG( deviceManager, client_address );
									
								return( true );
							}
						}
							
						String user_agent = (String)headers.get( "user-agent" );
						
						if ( user_agent != null ){
							
							String lc_agent = user_agent.toLowerCase();
	
							if ( lc_agent.startsWith( "platinum/" )){
								
								// boxee
							
								handleBoxee( deviceManager, client_address );
								
								return( true );
							}
	
							// Keeping "motorola" and "sec_hhp" from previous versions in order
							// to maintain the classification
							if ( lc_agent.contains( "motorola")) {
								// Linux/2.6.29 UPnP/1.0 Motorola-DLNA-Stack-DLNADOC/1.50
								handleGeneric(deviceManager, client_address, "motorola", "Motorola DLNA", null, false);
	
								return true;
	
							}else if ( lc_agent.contains( "sec_hhp")) {
								Matcher match = Pattern.compile("SEC_HHP_(.*)/").matcher(user_agent);
								if (match.find()) {
									String name = match.group(1);
									handleGeneric(deviceManager, client_address, "SEC_HPP_" + name, name, null, false);
									return true;
								}
	
							}else if ( lc_agent.contains( "playstation 4")){
								
									// media player update 1.5 switched user agent from generic 'UPnP/x.y' to something including 'PlayStation 4'
								
								handleGeneric(deviceManager, client_address, "sony.ps4", "PS4", "1", true );

								return( true );
								
							} else if (ADD_DLNA_UPNP_DEVICES && request.getURL().contains("RootDevice.xml")) {
								// filter only on RootDevice.xml requests, because some
								// devices send two different user agents -- one when discovering
								// our device, and one when doing SOAP actions
	
								/**
								System.out.println(request.getClientAddress2().getPort());
								for (Object key : headers.keySet()) {
									Object val = headers.get(key);
									System.out.println(key + "=" + val);
								}
								try {
									System.out.println(
									FileUtil.readInputStreamAsString(request.getInputStream(), 50000)
									);
								} catch (IOException e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
								}
								
								/**/
	
								//Wybox/0.95 UPnP/1.0 DLNADOC/1.50 Portable SDK for UPnP devices/1.4.6
								if (lc_agent.matches("Wybox/[0-9.]+ UPnP/1.0 DLNADOC/1.50 Portable SDK for UPnP devices/[0-9.]+".toLowerCase())) {
									handleGeneric(deviceManager, client_address, "dlna." + user_agent, "Iomega ScreenPlay", null, true);
									return true;
								}
	
								Matcher match;
								match = Pattern.compile("^([^ ]+) .*DLNADOC/[0-9.]+", Pattern.CASE_INSENSITIVE).matcher(user_agent);
								if (match.find()) {
									String hostName = NetBiosCache.getNetBiosName(client_address.getAddress().getHostAddress());
									String classification = "dlna." + user_agent;
									if (hostName.matches("^[^0-9].*")) {
										classification += "." + hostName;
									}
									String name = hostName.length() > 0 ? hostName + " (" + match.group(1) + ")" : match.group(1);
									handleGeneric(deviceManager, client_address, classification, name, "dlna", false);
									return true;
								}
									
								match = Pattern.compile("^(.*) UPnP/[0-9.]+", Pattern.CASE_INSENSITIVE).matcher(user_agent);
								if (match.find()) {
									String hostName = NetBiosCache.getNetBiosName(client_address.getAddress().getHostAddress());
									String classification = "upnp." + user_agent;
									if (hostName.matches("^[^0-9].*")) {
										classification += "." + hostName;
									}
									String name = hostName.length() > 0 ? hostName + " (" + match.group(1) + ")" : match.group(1);
									handleGeneric(deviceManager, client_address, classification, name, "upnp", false);
									return true;
								}
								match = Pattern.compile("UPnP/[0-9.]+", Pattern.CASE_INSENSITIVE).matcher(user_agent);
								if (match.find()) {
										// PS4 has no identifying features :(
										// However it should have TCP port 9295 open to support 'remote play' so see if we can detect this
									
									try{
										String host = client_address.getAddress().getHostAddress();
										
										URL url = new URL( "http://" + host + ":9295" );
										
										HttpURLConnection conn = (HttpURLConnection)url.openConnection();

										conn.setRequestMethod("HEAD");

										UrlUtils.setBrowserHeaders( conn, null );

										UrlUtils.connectWithTimeouts(conn, 1500, 5000);

										String remote_play_version = conn.getHeaderField("RP-Version");

										if ( remote_play_version != null ){
											
												// image id "1" is for the ps3 which works
											
											handleGeneric(deviceManager, client_address, "sony.ps4", "PS4", "1", true );
											
											return true;
										}								
									}catch( Throwable e ){
										e.printStackTrace();
									}
									
									String hostName = NetBiosCache.getNetBiosName(client_address.getAddress().getHostAddress());
									String classification = "upnp." + user_agent;
									if (hostName.matches("^[^0-9].*")) {
										classification += "." + hostName;
									}
									String name = hostName.length() > 0 ? hostName + " (Unknown)" : "Unknown";
									handleGeneric(deviceManager, client_address, classification, name, "upnp", false);
									return true;
								}
							}
	
						}
						
						return( false );
					}
				};
				
			deviceManager.addDiscoveryListener( dm_discovery_listener );
		}
	}
	
	protected void
	handleSonyBluRay(
		DeviceManager		device_manager,
		InetSocketAddress	address )
	{
		handleGeneric( device_manager, address, "sony.brdp", "Sony Blu-Ray Player", TV_ICON, false );
	}
	
	protected void
	handleSonyInternetTV(
		DeviceManager		device_manager,
		InetSocketAddress	address )
	{
		handleGeneric( device_manager, address, "sony.intv", "Sony Internet TV", TV_ICON, true );
	}
	
	protected void
	handleLG(
		DeviceManager		device_manager,
		InetSocketAddress	address )
	{
		handleGeneric( device_manager, address, "lg.dmp", "LG Device", TV_ICON, true );
	}

	protected void
	handleBoxee(
		DeviceManager		device_manager,
		InetSocketAddress	address )
	{
		handleGeneric( device_manager, address, "boxee.v1", "Boxee", "boxee", true );
	}
	
	protected DeviceMediaRenderer
	handleGeneric(
		DeviceManager		device_manager,
		InetSocketAddress	address,
		String				classification,
		String				display_name,
		String				image_id,
		boolean				never_xcode_default )
	{
		String uid;
		
		synchronized( this ){

			uid = COConfigurationManager.getStringParameter( "devices.upnp.uid." + classification, "" );
			
			if ( uid.length() == 0 ){
				
				uid = UUIDGenerator.generateUUIDString();

				COConfigurationManager.setParameter( "devices.upnp.uid." + classification, uid );
				
				COConfigurationManager.save();
			}
		}
		
		DeviceTemplate[] templates = device_manager.getDeviceTemplates( Device.DT_MEDIA_RENDERER );
		
		for ( DeviceTemplate template: templates ){
			
			if ( template.getClassification().equals( classification )){
				
				try{
					DeviceMediaRenderer device = (DeviceMediaRenderer)template.createInstance( display_name, uid, false );
					
					device.setAddress( address.getAddress());
					
					device.alive();		
					
					if (image_id != null && device.getImageID() == null) {
						device.setImageID( image_id );
					}
					
					TranscodeProfile[] profiles = device.getTranscodeProfiles();
					
					if ( never_xcode_default || profiles.length == 0 ){
						
						device.setTranscodeRequirement( TranscodeTarget.TRANSCODE_NEVER );
						
					}else{
						
						device.setTranscodeRequirement( TranscodeTarget.TRANSCODE_WHEN_REQUIRED );
					}

					return device;
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		}
		
		DeviceMediaRenderer device;
		try {
			device = (DeviceMediaRenderer) device_manager.addInetDevice(
					Device.DT_MEDIA_RENDERER, uid, classification, display_name,
					address.getAddress());
			
			device.alive();

			if (image_id != null && device.getImageID() == null) {
				device.setImageID( image_id );
			}
			
			if ( never_xcode_default ){
				
				device.setTranscodeRequirement( TranscodeTarget.TRANSCODE_NEVER );
				
			}else{
				
				device.setTranscodeRequirement( TranscodeTarget.TRANSCODE_WHEN_REQUIRED );
			}
			
			return device;
			
		} catch (Throwable e) {
			Debug.out( e );
		}
		
		return null;
	}
	
	public void
	destroy()
	{
		DeviceManager deviceManager = plugin.getDeviceManager();
		
		if ( deviceManager != null ){
			
			deviceManager.removeDiscoveryListener( dm_discovery_listener );
		}
	}
}
