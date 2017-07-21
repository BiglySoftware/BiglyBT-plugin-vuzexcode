package com.vuze.transcoder;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.StringInterner;

import com.biglybt.core.devices.*;
import com.biglybt.core.devices.impl.DeviceMediaRendererManual;
import com.vuze.plugins.transcoder.TranscoderPlugin;

public class TranscoderProfileCloner
{
	private static boolean DEBUG = true;

	final static String H264_1024x600 = "h264 1024x600";

	final static String H264_960x540 = "h264 960x540";

	final static String H264_856x480 = "h264 856x480";

	final static String H264_800x480 = "h264 800x480";

	final static String H264_800x480_LQ = "h264 800x480 LQ";

	final static String H264_640x360 = "h264 640x360";
	
	final static String H264_480x320 = "h264 480x320";

	final static String H264_400x240 = "h264 400x240";

	final static String H264_320x240 = "h264 320x240";

	final static String H264_220x176 = "h264 220x176";

	final static String H264_160x128 = "h264 160x128";

	final static String MP4_480x320 = "Generic MP4";
	

	final String[] defaultProfileNames = {
		"Generic 1080p h.264",
		"Generic 720p h.264",
		H264_1024x600,
		H264_960x540,
		H264_856x480,
		H264_800x480,
		H264_800x480_LQ,
		H264_640x360,
		H264_480x320,
		H264_400x240,
		H264_320x240,
		H264_220x176,
		H264_160x128,
		MP4_480x320,
		"Generic 480p h.264",
	};

	final String[] genericProfileNames = {
		"Generic 1080p h.264",
		"Generic 720p h.264",
		"Generic 480p h.264",
		"Generic 1080p MPEG-2",
		"Generic 720p MPEG-2",
		H264_1024x600,
		H264_960x540,
		H264_856x480,
		H264_800x480,
		H264_800x480_LQ,
		H264_640x360,
		H264_480x320,
		H264_400x240,
		H264_320x240,
		MP4_480x320,
	};

	private Map<String, File> mapKeyToProfileFile;

	private List<String> listProfilesToHide;

	private TranscoderProfileProvider profileProvider;

	private TranscoderPlugin plugin;

	private List<String> roots;

	private DeviceManagerListener			dm_listener;
	
	private List<Device> handledDevices = new ArrayList<Device>();

		// linked hash map to preserve order
	
	private Map<String, DeviceDefinition> mapDeviceClassificationToProfileClones = new LinkedHashMap<String, DeviceDefinition>();

	private boolean checked;
	private boolean	checking;
	
	public void initialize(TranscoderPlugin _plugin,
			TranscoderProfileProvider _profileProvider,
			Map<String, File> _mapKeyToProfileFile, List<String> _roots) {
		this.plugin = _plugin;
		this.roots = _roots;

		this.profileProvider = _profileProvider;
		this.mapKeyToProfileFile = _mapKeyToProfileFile;

		/*
		// does the same as TranscoderProfileProvider initializer does, except
		// uses "templates" folder and DOES NOT add to list of avail profiles
		for ( String root: roots ){
			File profileDirectory = new File(root, "profiles" + File.separator + "templates");
			if(profileDirectory.exists() && profileDirectory.isDirectory()) {
				File[] files = profileDirectory.listFiles();
				for(File f: files) {
					if(f.getName().endsWith(".properties")) {
						
						TranscodeProfile profile = profileProvider.loadProfile(f );
						
						if ( profile != null ){
							
							String	key = profile.getName().toLowerCase();
							
							if ( !mapKeyToProfileFile.containsKey(key)){
							
								mapKeyToProfileFile.put(key, f);
							}
						}
					}
				}
			}
		}
		*/

		
		listProfilesToHide = new ArrayList<String>();
		listProfilesToHide.add("^.*\\.reader\\..*");
		listProfilesToHide.add("^.*\\.0424$"); // Standard Microsystems Corp. (Card Readers)
		listProfilesToHide.add("^.*\\.0bda$"); // Realtek Semiconductor Corp.
		listProfilesToHide.add("^.*\\.0781$"); // SanDisk (Clip Drives and MP3 Players)
		listProfilesToHide.add("^.*\\.020[01]\\..*\\.0644$"); // TEAC Corp (& Dell)


		loadCloneInfo(mapDeviceClassificationToProfileClones);

		final DeviceManager deviceManager = plugin.getDeviceManager();
		
		if ( deviceManager != null ){
			
			dm_listener = new DeviceManagerListener() {
	
				public void deviceRemoved(Device device) {
					synchronized (handledDevices) {
						handledDevices.remove(device);
					}
				}
	
				public void deviceManagerLoaded() {
					
					Device[] devices = deviceManager.getDevices();
					for (Device device : devices) {
						synchronized (handledDevices) {
							if ( handledDevices.contains(device)){
								continue;
							}
						}
						fixupBlackBerry(device);
						fixupName(device, true);
					}
				}
	
				public void deviceChanged(final Device device) {
					fixupName(device, false);
				}
	
				public void deviceAttentionRequest(Device device) {
				}
	
				public void deviceAdded(final Device device) {
					fixupBlackBerry(device);
					fixupName(device, true);
				}
			};
			
			deviceManager.addListener( dm_listener );
		}
	}

	private void fixupBlackBerry(Device device) {
		if (device instanceof DeviceMediaRendererManual) {
			DeviceMediaRendererManual renderer = (DeviceMediaRendererManual) device;

			if (device.getClassification().contains("blackberry.")) {
				if (device.isNameAutomatic()
						&& device.getClassification().contains(".sd.")) {
					device.setName("BlackBerry SD Card", true);
				} else {
					device.setName("BlackBerry", true);
				}
				if (renderer.getDefaultTranscodeProfile() == null) {
					com.biglybt.core.devices.TranscodeProfile[] profiles = renderer.getTranscodeProfiles();
					for (com.biglybt.core.devices.TranscodeProfile profile : profiles) {
						if (profile.getName().equalsIgnoreCase("BlackBerry 320x240")) {
							renderer.setDefaultTranscodeProfile(profile);
							break;
						}
					}
				}
			}
		}
	}
	
	private void fixupName(Device device, boolean added) {
		synchronized (handledDevices) {
			boolean alreadyHandled = handledDevices.contains(device);
			if (!alreadyHandled) {
				handledDevices.add(device);
			}
			//System.out.println( "fixup: n=" + device.getName() + "/c=" + device.getClassification() + "/id=" + device.getID() + ", added=" + added + ", exist=" + alreadyHandled + ";" + device.getClass().getName() );
			String classification = device.getClassification();
			if (device instanceof DeviceMediaRenderer) {
				DeviceMediaRenderer renderer = (DeviceMediaRenderer) device;

//				// If we are never transcoding, pretend there's a profile so we don't add our own
//				int numProfiles = (renderer.getTranscodeRequirement() == DeviceMediaRenderer.TRANSCODE_NEVER)
//						? 1 : renderer.getDirectTranscodeProfiles().length;
				int numProfiles = renderer.getDirectTranscodeProfiles().length;

				if (DEBUG) {
					if ( !alreadyHandled ){
						plugin.log("TPP: " + numProfiles + " profiles for " + classification
							+ " (" + device.getName() + ")");
					}
				}

				boolean matchedRegex = false;
				for (String matchString : mapDeviceClassificationToProfileClones.keySet()) {
					try {
						Matcher matcher = Pattern.compile(matchString,
								Pattern.CASE_INSENSITIVE).matcher(classification);
						if (!matcher.find()) {
							continue;
						}

						matchedRegex = true;

						DeviceDefinition deviceDefinition = mapDeviceClassificationToProfileClones.get(matchString);

						if (deviceDefinition.imageID != null && deviceDefinition.imageID.length() > 0) {
  						String oldImageID = device.getImageID();
  						if (oldImageID == null || !oldImageID.startsWith("http")) {
  							device.setImageID(deviceDefinition.imageID);
  						}
						}
						device.setGenericUSB(deviceDefinition.generic);

						if (alreadyHandled) {
							return;
						}

						if (device.isNameAutomatic()) {
							String deviceName = device.getName();

							// Need to figure out why we did:
							//	deviceName.substring(1, deviceName.length() - 1));
							String newName = deviceDefinition.nameTemplate.replaceAll("%s",
									deviceName);

							for (int i = 0; i < matcher.groupCount(); i++) {
								newName = newName.replaceAll("%" + (i + 1),
										matcher.group(i + 1));
							}
							if (DEBUG) {
								plugin.log("  TPP: matched " + matchString + ". New name "
										+ newName);
							}

							device.setName(newName, true);
						}

						if (numProfiles == 0) {
							boolean addedProfile = false;
							String defaultProfileNewDisplayName = null;
							StringBuffer log = null;
							if (DEBUG) {
								log = new StringBuffer("  TPP: added profile");
							}
							int i = 0;
							for (String name : deviceDefinition.profileNameKeys) {
								ProfileCloneInfo cloneInfo = new ProfileCloneInfo(name, "%s;"
										+ classification, "%s", classification);
								addedProfile |= cloneProfile(mapKeyToProfileFile, cloneInfo,
										classification, i++);
								if (deviceDefinition.defaultProfile != null
										&& deviceDefinition.defaultProfile.equalsIgnoreCase(name)) {
									defaultProfileNewDisplayName = cloneInfo.displayName;
								}
								if (DEBUG) {
									log.append(", ");
									log.append(name);
								}
							}
							if (DEBUG) {
								plugin.log(log.toString());
							}
							if (addedProfile) {
								profileProvider.triggerProfilesChanged();

								if (defaultProfileNewDisplayName != null
										&& renderer.getDefaultTranscodeProfile() == null) {
									com.biglybt.core.devices.TranscodeProfile[] coreProfiles = renderer.getDirectTranscodeProfiles();
									for (com.biglybt.core.devices.TranscodeProfile coreProfile : coreProfiles) {
										if (coreProfile.getName().equalsIgnoreCase(
												defaultProfileNewDisplayName)) {
											if (DEBUG) {
												plugin.log("  TPP: Defaulting to profile "
														+ defaultProfileNewDisplayName);
											}
											renderer.setDefaultTranscodeProfile(coreProfile);
											break;
										}
									}
								}
							}
						}

						break;
					} catch (Exception e) {
						plugin.log("Clone Error", e);
					}
				}

				if (!alreadyHandled && !matchedRegex) {
					if (numProfiles == 0 && device.isGenericUSB()) {
						if (DEBUG) {
							plugin.log("  TPP: cloning generic USB profiles");
						}
						boolean cloned = false;
						String defaultProfileNewDisplayName = null;
						int i = 0;
						for (String name : genericProfileNames) {
							ProfileCloneInfo cloneInfo = new ProfileCloneInfo(name, "%s;"
									+ classification, "%s", classification);
							cloned |= cloneProfile(mapKeyToProfileFile, cloneInfo,
									classification, i++);
							if (H264_480x320.equalsIgnoreCase(name)) {
								defaultProfileNewDisplayName = cloneInfo.displayName;
							}
						}
						if (cloned) {
							profileProvider.triggerProfilesChanged();

							com.biglybt.core.devices.TranscodeProfile defaultTranscodeProfile = null;
							try {
								defaultTranscodeProfile = renderer.getDefaultTranscodeProfile();
							} catch (TranscodeException e) {
							}
							if (defaultProfileNewDisplayName != null
									&& defaultTranscodeProfile == null) {
								com.biglybt.core.devices.TranscodeProfile[] coreProfiles = renderer.getDirectTranscodeProfiles();
								for (com.biglybt.core.devices.TranscodeProfile coreProfile : coreProfiles) {
									if (coreProfile.getName().equalsIgnoreCase(
											defaultProfileNewDisplayName)) {
										if (DEBUG) {
											plugin.log("  TPP: Defaulting to profile "
													+ defaultProfileNewDisplayName);
										}
										renderer.setDefaultTranscodeProfile(coreProfile);
										break;
									}
								}
							}
						}
						numProfiles = renderer.getTranscodeProfiles().length;
						if (DEBUG) {
							plugin.log("  TPP: cloning generic USB profiles resulted in "
									+ numProfiles);
						}
					}

					if (added && device.isNameAutomatic()) {
						for (String matchToHide : listProfilesToHide) {
							Matcher matcher = Pattern.compile(matchToHide,
									Pattern.CASE_INSENSITIVE).matcher(classification);
							if (matcher.matches()) {
								if (DEBUG) {
									plugin.log("  TPP: hiding " + classification);
								}
								device.setHidden(true);
								break;
							}
						}
					}
				}
			}
		}
	}
	
	protected void
	check()
	{
		synchronized( handledDevices ){
				// meh, get recursion here under some circumstances due to fixupName hitting
				// Device:getTranscodeProfiles and this calling TranscoderPriverVuze:getProfiles
				// and that calling TranscoderPlugin.getProfiles and back to here...
			
			if ( checked || checking ){
			
				return;
			}
			
			checking = true;
		}
		
		DeviceManager deviceManager = plugin.getDeviceManager();

		if ( deviceManager != null ){
			
			Device[] devices = deviceManager.getDevices();
			
			for (Device device : devices) {
				synchronized (handledDevices) {
					if ( handledDevices.contains(device)){
						continue;
					}
				}
				
				fixupName(device, true);
			}
		}
		
		synchronized( handledDevices ){
			
			checked = true;
			
			checking = false;
		}
	}
	
	protected void
	destroy()
	{
		DeviceManager deviceManager = plugin.getDeviceManager();

		if ( dm_listener != null ){		

			deviceManager.removeListener( dm_listener );
		}
	}
	
	private void loadCloneInfo(
			Map<String, DeviceDefinition> mapDeviceClassificationToProfileClones) {
		String root = profileProvider.plugin_interface.getPluginDirectoryName();
		File f = new File(root, "Profiles" + File.separatorChar + "user.devicelist.csv");
		loadCloneInfo(mapDeviceClassificationToProfileClones, f);

		f = new File(root, "Profiles" + File.separatorChar + "devicelist.csv");
		loadCloneInfo(mapDeviceClassificationToProfileClones, f);
	}

	private void loadCloneInfo(
			Map<String, DeviceDefinition> mapDeviceClassificationToProfileClones, File f) {
		if (!f.exists()) {
			return;
		}
		try {
			int numAdded = 0;
			String devicelist = FileUtil.readFileAsString(f, -1, "utf8");

			String[] deviceEntries = devicelist.split("[\r]?\n");
			for (String deviceDefLine : deviceEntries) {
				if (deviceDefLine.startsWith("#")) {
					continue;
				}
				String[] split = deviceDefLine.split("\t");
				if (split.length >= 4) {
					String classification = split[0];
					String nameTemplate = StringInterner.intern(split[1]);
					String imageID = StringInterner.intern(split[2]);
					String[] profileNameKeys;
					if (split[3].equalsIgnoreCase("default")) {
						profileNameKeys = defaultProfileNames;
					} else if (split[3].equalsIgnoreCase("generic")) {
						profileNameKeys = genericProfileNames;
					} else {
						profileNameKeys = split[3].split(",");
					}
					String defaultProfile = split.length > 4
							? StringInterner.intern(split[4]) : null;
					boolean keepGeneric = split.length > 5 ? split[5].matches("[1yY].*")
							: false;

					DeviceDefinition dd = new DeviceDefinition(nameTemplate, imageID,
							profileNameKeys, defaultProfile, keepGeneric);
					mapDeviceClassificationToProfileClones.put(classification, dd);
					numAdded++;
				}
			}

			if (DEBUG) {
				plugin.log("Loaded " + numAdded + " DeviceDefinitions from "
						+ f.toString());
			}
		} catch (IOException e) {
			plugin.log("reading devicelist", e);
		}
	}

	private boolean cloneProfile(Map<String, File> mapKeyToProfileFile,
			ProfileCloneInfo cloneInfo, String deviceClassification, int index) {
		File file = mapKeyToProfileFile.get(cloneInfo.oldNameKey.toLowerCase());
		if (file == null) {
			if (DEBUG) {
				plugin.log("TPP Warning: '" + cloneInfo.oldNameKey
						+ "' does not match a profile.");
			}
			return false;
		}

		TranscodeProfile profile = profileProvider.loadProfile(file);
		if (profile == null) {
			if (DEBUG) {
				plugin.log("TPP Warning: '" + file + "' does not exist.");
			}
			return false;
		}

		String oldName = profile.name;
		if (cloneInfo.newNameKey != null) {
			profile.name = cloneInfo.newNameKey = cloneInfo.newNameKey.replaceAll(
					"%s", oldName);
		} else {
			profile.name += " (" + deviceClassification + ")";
		}
		profile.device = deviceClassification;
		if (cloneInfo.displayName != null) {
			profile.display_name = cloneInfo.displayName = cloneInfo.displayName.replaceAll(
					"%s", oldName);
		}

		String key = profile.getName().toLowerCase();
		
		profile.icon_index = index;

		return profileProvider.addProfile(key, profile);
	}

	public class ProfileCloneInfo
	{
		String oldNameKey;

		String newNameKey;

		String displayName;

		String matchingClassification;

		public ProfileCloneInfo(String oldNameKey, String newNameKey,
				String displayName, String newClassification) {
			this.oldNameKey = oldNameKey;
			this.newNameKey = newNameKey;
			// Due to a bug in 4510
			// displayName MUST BE "%s", which translates into
			// oldName in cloneProfile()
			//this.displayName = displayName;
			this.displayName = "%s";
			this.matchingClassification = newClassification;
		}
	}

	public class DeviceDefinition
	{
		String nameTemplate;

		String imageID;

		String[] profileNameKeys;

		String defaultProfile;

		boolean generic;

		public DeviceDefinition(String nameTemplate, String imageID,
				String[] profileNameKeys, String defaultProfile, boolean generic) {
			this.defaultProfile = defaultProfile;
			this.generic = generic;
			this.nameTemplate = nameTemplate;
			this.imageID = imageID;
			this.profileNameKeys = profileNameKeys;
		}

		public DeviceDefinition(String nameTemplate, String imageID,
				String[] profileNameKeys, String defaultProfile) {
			this(nameTemplate, imageID, profileNameKeys, defaultProfile, false);
		}

		public DeviceDefinition(String nameTemplate, String imageID,
				String[] profileNameKeys) {
			this(nameTemplate, imageID, profileNameKeys, null, false);
		}
	}
}
