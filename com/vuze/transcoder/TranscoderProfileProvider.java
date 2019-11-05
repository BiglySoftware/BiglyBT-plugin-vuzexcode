package com.vuze.transcoder;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.LightHashMap;
import com.biglybt.pif.PluginInterface;

import com.vuze.plugins.transcoder.TranscoderPlugin;

public class TranscoderProfileProvider {

	protected PluginInterface		plugin_interface;
	
	Map<String,TranscodeProfile> profiles;
	
	List<Runnable> listProfilesChangedListeners = new ArrayList<Runnable>(1);

	private final TranscoderPlugin plugin;
	private boolean v1_supported;
	private boolean	v2_supported;
	
	private TranscoderProfileCloner 	cloner;

	private TranscodeProfile[] baseProfiles;
	
	public TranscoderProfileProvider(TranscoderPlugin plugin, PluginInterface pi, boolean v1_supported, boolean v2_supported) {
		
		this.plugin = plugin;
		this.v1_supported = v1_supported;
		this.v2_supported = v2_supported;
		
		plugin_interface = pi;
		
		profiles = new HashMap<String,TranscodeProfile>();
		
		List<String> roots = new ArrayList<String>();
		
		String root1 = pi.getPluginDirectoryName();
		String root2 = pi.getPerUserPluginDirectoryName();
				
		if ( !root1.equals( root2 )){
			roots.add( root2 );
		}
		
		roots.add( root1 );

		boolean triggerProfilesChanged = false;
		Map<String, File> mapKeyToProfileFile = new LightHashMap<String, File>();
		for ( String root: roots ){
			File profileDirectory = new File(root,"profiles");
			if(profileDirectory.exists() && profileDirectory.isDirectory()) {
				File[] files = profileDirectory.listFiles();
				for(File f: files) {
					if(f.getName().endsWith(".properties")) {
						
						TranscodeProfile profile = loadProfile(f );
						
						if ( profile != null ){
							
							String	key = profile.getName().toLowerCase();
							
							if ( !profiles.containsKey(key)){
							
								mapKeyToProfileFile.put(key, f);
								profiles.put(key,profile);
								triggerProfilesChanged = true;
							}
						}
					}
				}
			}
		}
		
		baseProfiles = profiles.values().toArray(new TranscodeProfile[0]);
		
		if (triggerProfilesChanged) {
			triggerProfilesChanged();
		}
		
		cloner = new TranscoderProfileCloner();
		
		cloner.initialize(plugin, this, mapKeyToProfileFile, roots);
	}
	
	public TranscodeProfile[]
	getBaseProfiles() {
		return baseProfiles;
	}
	
	public void
	destroy()
	{
		cloner.destroy();
	}
	
	protected void triggerProfilesChanged() {
		Runnable[] runs;
		synchronized (listProfilesChangedListeners) {
			runs = listProfilesChangedListeners.toArray(new Runnable[0]);
		}
		for (Runnable runnable : runs) {
			try {
				runnable.run();
			} catch (Exception e) {
				Debug.out(e);
			}
		}
	}

	
	protected TranscodeProfile
	loadProfile(
		File		file )
	{
		if (file.length() == 0) {
			return null;
		}
		FileInputStream fis = null;
		
		try{
			fis = new FileInputStream(file);
			
			TranscodeProfile profile = new TranscodeProfile( fis, v2_supported );
			
			if ( !v1_supported && !profile.isV2Supported()){
				
				return( null );
			}
			return( profile );
			
		}catch( Throwable e ){
			
			Debug.out( file + " is not a valid profile", e );
			
		}finally{
			
			try{
				fis.close();
				
			}catch( Throwable e ){
			}
		}
		
		return( null );
	}
	
	public TranscodeProfile[] 
	getProfiles() 
	{
		try {
			cloner.check();
		} catch (Throwable t) {
			Debug.out(t);
		}
		
		return profiles.values().toArray(new TranscodeProfile[profiles.size()]);
	}

	public TranscodeProfile
	addProfile(
		File		file )
	{
		if ( !file.getName().endsWith( ".properties" )){
			
			Debug.out( "Transcode profile name must end in .properties" );
			
			return( null );
		}
		
		TranscodeProfile	profile = loadProfile( file );
		
		if ( profile == null ){
			
			return( null );
		}
		
		String root = plugin_interface.getPerUserPluginDirectoryName();
		
		File profile_dir = new File( root, "profiles" );

		if ( !profile_dir.exists()){
			
			profile_dir.mkdirs();
		}
		
		File stored_profile = new File( profile_dir, file.getName());
		
		if ( stored_profile.exists()){
			
			try{
				byte[] existing = FileUtil.readFileAsByteArray(stored_profile);
				
				byte[] current 	= FileUtil.readFileAsByteArray(file);
				
				if ( Arrays.equals( existing, current )){
					
					synchronized( profiles ){
					
						TranscodeProfile ep =  profiles.get( profile.getName().toLowerCase());
						
						if ( ep != null ){
							
							return( ep );
						}
					}
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
		
		FileUtil.copyFile( file, stored_profile );
		
		synchronized( profiles ){

			profiles.put( profile.getName().toLowerCase(), profile );
			triggerProfilesChanged();
		}
		
		return( profile );
	}
	
	public TranscodeProfile
	getProfile(
		String	name )
	{
		synchronized( profiles ){
			
			return( profiles.get( name.toLowerCase()));
		}
	}
	
	public void
	addProfilesChangedListener(Runnable run) {
		synchronized (listProfilesChangedListeners) {
			listProfilesChangedListeners.add(run);
		}
	}

	
	public void
	removeProfilesChangedListener(Runnable run) {
		synchronized (listProfilesChangedListeners) {
			listProfilesChangedListeners.remove(run);
		}
	}

	protected boolean addProfile(String key, TranscodeProfile profile) {
		synchronized( profiles ){
  		if ( !profiles.containsKey(key)){
  			
  			profiles.put(key,profile);
  			return true;
  		}
  		return false;
		}
	}

}
