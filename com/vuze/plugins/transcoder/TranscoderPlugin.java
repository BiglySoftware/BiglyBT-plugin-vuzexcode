package com.vuze.plugins.transcoder;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import com.biglybt.core.util.*;
import com.biglybt.pif.*;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ipc.IPCException;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.logging.LoggerChannelListener;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.config.*;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.tables.TableRow;
import com.biglybt.pif.utils.LocaleUtilities;
import com.biglybt.ui.swt.TextViewerWindow;
import com.biglybt.ui.swt.Utils;

import com.biglybt.core.devices.DeviceManager;
import com.biglybt.core.devices.DeviceManagerFactory;
import com.biglybt.core.devices.TranscodeException;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.proxy.AEProxySelectorFactory;
import com.biglybt.core.util.average.Average;
import com.biglybt.core.util.average.AverageFactory;
import com.vuze.transcoder.*;
import com.vuze.transcoder.devices.DeviceSpecification;
import com.vuze.transcoder.devices.DeviceSpecificationFinder;
import com.vuze.transcoder.media.*;

public class 
TranscoderPlugin 
	implements UnloadablePlugin
{
	private TranscoderProfileProvider provider;
	
	private LoggerChannel			logger;
	private BasicPluginViewModel	view_model;
	private BasicPluginConfigModel	config_model;
	
	private TableContextMenuItem itemTranscodeFiles;
	private TableContextMenuItem itemTranscodeMyTorrentsBig;
	private TableContextMenuItem itemTranscodeMyTorrentsComplete;
	
	private List<TableContextMenuItem> itemMediaInfos = new ArrayList<>();

	private IntParameter		analysis_start_chunk;
	private IntParameter		analysis_end_chunk;
	private IntParameter		http_timeout_mins;
	
	private int					threads;
	
	private String ffmpegPathOld;
	private String ffmpegPathNew;
	private String mediaInfoPath;
	
	private File	temp_dir;
	
	private Set<analysisContext> 	active_analysis 	= new HashSet<analysisContext>();
	private Set<analysisContext2> 	active_analysis2 	= new HashSet<analysisContext2>();
	private Set<transcodeContext> 	active_transcode 	= new HashSet<transcodeContext>();
	
	private boolean	plugin_unloading;
	private boolean	plugin_closing;
	
	private TranscoderDeviceDiscoverer	discoverer;
	private DeviceManager				device_manager;
	
	public static PluginInterface pluginInterface;
	
	public void 
	initialize(
		PluginInterface _pluginInterface )
	
			throws PluginException 
	{	
		pluginInterface = _pluginInterface;
		
		/*
		 * People complaining - reworked to handle missing device manager
		boolean isAZ3 = COConfigurationManager.getStringParameter("ui").equals("az3");

		if ( !isAZ3 ){
			
			throw( new PluginException( "Plugin only supported with the Vuze UI" ));
		}
		*/
		
		logger				= pluginInterface.getLogger().getTimeStampedChannel( "BiglyBTTranscoder" ); 
		
		logger.setDiagnostic();
		
		pluginInterface.addListener(
			new PluginListener()
			{
				public void
				initializationComplete()
				{
				}
				
				public void
				closedownInitiated()
				{
					Set<analysisContext> 	analysis;
					Set<analysisContext2> 	analysis2;
					Set<transcodeContext> 	transcode;

					synchronized( TranscoderPlugin.this ){
						
						plugin_closing	= true;
						
						analysis 	= new HashSet<analysisContext>( active_analysis );
						analysis2 	= new HashSet<analysisContext2>( active_analysis2 );
						transcode	= new HashSet<transcodeContext>( active_transcode );
					}
					
					for ( analysisContext a: analysis ){
						
						a.cancel();
					}
					
					for ( analysisContext2 a: analysis2 ){
						
						a.cancel();
					}
					
					for ( transcodeContext t: transcode ){
						
						t.cancel();
					}
				}
				
				public void
				closedownComplete()
				{	
				}
			});
		
		final LocaleUtilities loc_utils = pluginInterface.getUtilities().getLocaleUtilities();

		UIManager	ui_manager = pluginInterface.getUIManager();

		config_model = ui_manager.createBasicPluginConfigModel( "vuzexcode.name" );
		
		BooleanParameter enable_menus = config_model.addBooleanParameter2( "vuzexcode.enable_menus", "vuzexcode.enable_menus", false );
		
		analysis_start_chunk 	= config_model.addIntParameter2( "vuzexcode.analysis_start", "vuzexcode.analysis_start", 128, 32, Integer.MAX_VALUE );
		analysis_end_chunk 		= config_model.addIntParameter2( "vuzexcode.analysis_end", "vuzexcode.analysis_end", 128, 32, Integer.MAX_VALUE );

		int procs = Runtime.getRuntime().availableProcessors();
		
		if ( procs > 1 ){
			
			String[]	 values = new String[procs];
			
			for ( int i=0;i<procs;i++ ){
				
				values[i] = String.valueOf(i+1);
			}
			
			final StringListParameter thread_param = config_model.addStringListParameter2( "vuzexcode.threads", "vuzexcode.threads", values, String.valueOf( procs ));
			
			thread_param.addListener(
				new ParameterListener()
				{
					public void 
					parameterChanged(
						Parameter param  )
					{
						setThreads( thread_param.getValue());
					}
				});
			
			setThreads( thread_param.getValue());
			
		}else{
			
			threads = 1;
		}
		
		http_timeout_mins 		= config_model.addIntParameter2( "vuzexcode.analysis_timeout", "vuzexcode.analysis_timeout", 3, 1, Integer.MAX_VALUE );

		
		BooleanParameter legacy_xcoder= config_model.addBooleanParameter2( "vuzexcode.legacy_xcoder", "vuzexcode.legacy_xcoder", false );

		boolean v1_supported = Constants.isOSX || Constants.isWindows;
		boolean v2_supported = !legacy_xcoder.getValue();
	
		provider = new TranscoderProfileProvider(this, pluginInterface, v1_supported, v2_supported );
		
		try{
		
			discoverer = new TranscoderDeviceDiscoverer( this );
			
		}catch( Throwable e ){
			// migration
		}
		
		String plugin_dir = pluginInterface.getPluginDirectoryName();
		
		if ( Constants.isWindows ){
			
			ffmpegPathOld = new File(plugin_dir,"ffmpeg.exe").getAbsolutePath();
			ffmpegPathNew = new File(plugin_dir,"ffmpeg2.exe").getAbsolutePath();
			mediaInfoPath = new File(plugin_dir,"mediainfo.exe").getAbsolutePath();
			
		}else if( Constants.isOSX ){
			
			ffmpegPathOld = new File(plugin_dir,"ffmpeg").getAbsolutePath();
			ffmpegPathNew = new File(plugin_dir,"ffmpeg2").getAbsolutePath();
			mediaInfoPath = new File(plugin_dir,"mediainfo").getAbsolutePath();
			
			chmod("+x",mediaInfoPath);
			chmod("+x",ffmpegPathOld);
			chmod("+x",ffmpegPathNew);

		}else{
			
			ffmpegPathOld = "";	// no old one supported
			ffmpegPathNew = new File(plugin_dir,"ffmpeg2").getAbsolutePath();
			mediaInfoPath = new File(plugin_dir,"mediainfo").getAbsolutePath();
			
			chmod("+x",mediaInfoPath);
			chmod("+x",ffmpegPathNew);
		}
		
		temp_dir = new File( plugin_dir, "tmp" );
		
		temp_dir.mkdirs();
		
		File[]	temp_files = temp_dir.listFiles();
		
		if ( temp_files != null ){
			
			for ( File f: temp_files ){
				
				f.delete();
			}
		}
				
		view_model = ui_manager.createBasicPluginViewModel( loc_utils.getLocalisedMessageText("vuzexcode.name") );
		
		view_model.getActivity().setVisible( false );
		view_model.getProgress().setVisible( false );
		
		logger.addListener(
				new LoggerChannelListener()
				{
					public void
					messageLogged(
						int		type,
						String	content )
					{
						view_model.getLogArea().appendText( content + "\n" );
					}
					
					public void
					messageLogged(
						String		str,
						Throwable	error )
					{
						if ( str.length() > 0 ){
							view_model.getLogArea().appendText( str + "\n" );
						}
						
						StringWriter sw = new StringWriter();
						
						PrintWriter	pw = new PrintWriter( sw );
						
						error.printStackTrace( pw );
						
						pw.flush();
						
						view_model.getLogArea().appendText( sw.toString() + "\n" );
					}
				});		
	
		view_model.setConfigSectionID( "vuzexcode.name" );
				

		
		if ( enable_menus.getValue()){
			
			itemTranscodeFiles = pluginInterface.getUIManager().getTableManager().addContextMenuItem(TableManager.TABLE_TORRENT_FILES, "vuzexcode.transcode");
			itemTranscodeFiles.setStyle(MenuItem.STYLE_MENU);
			itemTranscodeFiles.setHeaderCategory(MenuItem.HEADER_CONTENT);
			
			itemTranscodeMyTorrentsBig = pluginInterface.getUIManager().getTableManager().addContextMenuItem(TableManager.TABLE_MYTORRENTS_ALL_BIG, "vuzexcode.transcode");
			itemTranscodeMyTorrentsBig.setStyle(MenuItem.STYLE_MENU);
			itemTranscodeMyTorrentsBig.setHeaderCategory(MenuItem.HEADER_CONTENT);
			
			itemTranscodeMyTorrentsComplete = pluginInterface.getUIManager().getTableManager().addContextMenuItem(TableManager.TABLE_MYTORRENTS_COMPLETE, "vuzexcode.transcode");
			itemTranscodeMyTorrentsComplete.setStyle(MenuItem.STYLE_MENU);
			itemTranscodeMyTorrentsComplete.setHeaderCategory(MenuItem.HEADER_CONTENT);
		
			TableContextMenuItem[] items = new TableContextMenuItem[] {itemTranscodeFiles,itemTranscodeMyTorrentsBig,itemTranscodeMyTorrentsComplete};
			
			
			for (TableContextMenuItem item : items) {
				item.addFillListener(new MenuItemFillListener() {
					public void menuWillBeShown(MenuItem menu, Object data) {
						buildTranscodeToMenu((TableContextMenuItem) menu);
					}
				});
			}
		}
	
		TableContextMenuItem itemMediaInfo = pluginInterface.getUIManager().getTableManager().addContextMenuItem(
				TableManager.TABLE_TORRENT_FILES, "vuzexcode.menu.file.mediainfo");

		itemMediaInfos.add( itemMediaInfo );
		
		itemMediaInfo.addFillListener(
			new MenuItemFillListener(){
				
				@Override
				public void menuWillBeShown(MenuItem menu, Object data){
				
					TableRow[] rows = (TableRow[])data;
					
					int	 num_ok = 0;
					
					for ( TableRow row: rows ){
						
						DiskManagerFileInfo file_info = (DiskManagerFileInfo)row.getDataSource();
						
						if ( file_info.getIndex() >= 0 ){
							
							num_ok++;
						}
					}
					
					menu.setEnabled( num_ok > 0 );
				}
			});
		
		itemMediaInfo.addMultiListener(
			new MenuItemListener(){
				
				@Override
				public void selected(MenuItem menu, Object target){
					
					runMediaAnalyser( target );
				}
			});
		
		for ( String tid: TableManager.TABLE_MYTORRENTS_ALL ){
			
			TableContextMenuItem itemMediaInfo2 = 
					pluginInterface.getUIManager().getTableManager().addContextMenuItem(
							tid, "vuzexcode.menu.file.mediainfo");

			itemMediaInfos.add( itemMediaInfo2 );
			
			itemMediaInfo2.setHeaderCategory(MenuItem.HEADER_CONTENT);
			
			itemMediaInfo2.addFillListener(
					new MenuItemFillListener(){
						
						@Override
						public void menuWillBeShown(MenuItem menu, Object data){
						
							TableRow[] rows = (TableRow[])data;
							
							int	 num_ok = 0;
							
							for ( TableRow row: rows ){
								
								Download dl = (Download)row.getDataSource();
								
								if ( dl.getPrimaryFile() != null ){
									
									num_ok++;
								}
							}
							
							menu.setEnabled( num_ok > 0 );
						}
					});
			
			itemMediaInfo2.addMultiListener(
					new MenuItemListener(){
						
						@Override
						public void selected(MenuItem menu, Object target){
							
							runMediaAnalyser( target );
						}
					});
				
		}
	}
	
	private void
	runMediaAnalyser(
		Object		target )
	{
			// removed modality - seems to work fine :)
		
		TextViewerWindow viewer =
				new TextViewerWindow(
						MessageText.getString( "vuzexcode.analysis.title" ),
						null, "", false, false );

		viewer.setNonProportionalFont();
		
		viewer.setEditable( false );

		viewer.setOKEnabled( false );

		boolean[] closed = { false };
			
		analysisContext2[]	current = { null };
		
		viewer.addListener(
			new TextViewerWindow.TextViewerWindowListener(){
				
				@Override
				public void closed(){
				
					synchronized( closed ){
						
						closed[0] = true;
						
						if ( current[0] != null ){
							
							current[0].cancel();
						}
					}
				}
			});
		
		new AEThread2( "MediaAnalyser" )
		{
			@Override
			public void
			run()
			{
				try{
					TableRow[] rows = (TableRow[])target;
					
					for ( TableRow row: rows ){
						
						Object	ds = row.getDataSource();
						
						DiskManagerFileInfo file_info;
						
						if ( ds instanceof Download ){
							
							file_info = ((Download)ds).getPrimaryFile();
							
							if ( file_info == null ){
								
								continue;
							}
						}else{
						
							file_info = (DiskManagerFileInfo)ds;
							
							if ( file_info.getIndex() < 0 ){
							
								continue;		// fake entry, e.g. from FilesView tree node
							}
						}
						
						log( "Analysing " + file_info.getFile(true).getName() + "\r\n");
						log( "----\r\n\r\n" );
						
						try{
							analysisContext2 context = getMediaInfo( file_info );
							
							while( true ){
								
								try{
									Thread.sleep( 250 );
									
								}catch( Throwable e ){
								}
								
								synchronized( closed ){
									
									if ( closed[0] ){
										
										context.cancel();
										
										return;
										
									}else{
										
										current[0] = context;
									}
								}
								
								Map<String,Object> status = context.getStatus();
								
								long state = (Long)status.get( "state" );
								
								if ( state == 0 ){
									
								}else if ( state == 1 ){
									
									log( "Cancelled" );
									
									break;
									
								}else if ( state == 2 ){
									
									log( "Error: " + status.get( "error" ));
									
									break;
									
								}else{
									
									log((String)status.get( "result" ));
									
									break;
								}
							}
						}catch( Throwable e ){
							
							e.printStackTrace();
						}
					}
					
				}finally{
					
					Utils.execSWTThread(
						new Runnable()
						{
							public void
							run()
							{
								if ( !viewer.isDisposed()){
								
									viewer.setOKEnabled( true );
									
									viewer.setCancelEnabled( false );
								}
							}
						});
				}
			}
			
			private void
			log(
				String	str )
			{
				Utils.execSWTThread(
					new Runnable()
					{
						public void
						run()
						{
							if ( !viewer.isDisposed()){
								
								viewer.append( str );
							}
						}
					});
			}
		}.start();
		
		//viewer.goModal();
	}
	
	protected void buildTranscodeToMenu(TableContextMenuItem parent) {
		parent.removeAllChildItems();
		TableManager menuManager = pluginInterface.getUIManager().getTableManager();
		final LocaleUtilities loc_utils = pluginInterface.getUtilities().getLocaleUtilities();

		MenuItemListener listener = new MenuItemListener() {
			public void selected(MenuItem menu, Object target) {
				String fileName = null;
				try {

					TableRow row = (TableRow) target;
					Object data = row.getDataSource();
					if (data instanceof Download) {
						Download d = (Download) data;
						String file = d.getSavePath();
						File f = new File(file);
						if (f.isFile()) {
							fileName = file;
						}
					}
					if (data instanceof DiskManagerFileInfo) {
						DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) data;
						fileName = fileInfo.getFile(true).getAbsolutePath();
					}
				} catch (Throwable t) {

				}
				if (fileName != null) {
					TranscodeProfile profile = (TranscodeProfile) menu.getData();
					FileDialog fd = new FileDialog(Utils.findAnyShell(), SWT.SAVE);
					String extension = "";
					if (profile.getExtension() != null) {
						extension = profile.getExtension();
						fd.setFilterExtensions(new String[] {
							extension
						});
					}
					fd.setText(loc_utils.getLocalisedMessageText("vuzexcode.save_to"));
					fd.setFileName(fileName + extension);
					String saveTo = fd.open();
					if (saveTo != null) {
						TranscodeWindow window = new TranscodeWindow(fileName, saveTo,
								profile, new String[]{ ffmpegPathOld,ffmpegPathNew }, mediaInfoPath);
					}
				}
			}
		};

		MenuItemFillListener fill_listener = new MenuItemFillListener() {
			public void menuWillBeShown(MenuItem menu, Object target) {
				menu.removeAllChildItems();

				Object obj = null;

				if (target instanceof TableRow) {

					obj = ((TableRow) target).getDataSource();

				} else {

					TableRow[] rows = (TableRow[]) target;

					if (rows.length == 1) {

						obj = rows[0].getDataSource();

					}
				}

				if (obj == null) {

					menu.setEnabled(false);

					return;
				}

				Download download;
				DiskManagerFileInfo file;

				if (obj instanceof Download) {

					download = (Download) obj;

					if (!download.isComplete()) {

						menu.setEnabled(false);

						return;
					}

				} else {

					file = (DiskManagerFileInfo) obj;

					if (file.getDownloaded() != file.getLength()) {

						menu.setEnabled(false);

						return;
					}
				}

				menu.setEnabled(true);
			}
		};

		TranscodeProfile[] profiles = provider.getBaseProfiles();
		Arrays.sort(profiles, new Comparator<TranscodeProfile>() {
			// @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
			public int compare(TranscodeProfile o1, TranscodeProfile o2) {
				int i = compareStrings(o1.getName() , o2.getName());
				return i;
			}
		});
		for (TranscodeProfile profile : profiles) {

				TableContextMenuItem item = menuManager.addContextMenuItem(parent,
						"item");
				item.setText(profile.getName());
				item.setData(profile);
				item.addListener(listener);
				item.addFillListener(fill_listener);
		}

	}
	
	protected int
	compareStrings(
		String	s1,
		String	s2 )
	{
		if ( s1 == null && s2 == null ){
			return(0);
		}else if ( s1 == null ){
			return(-1);
		}else if ( s2 == null ){
			return( 1 );
		}else{
			return( s1.compareToIgnoreCase(s2));
		}
	}


	protected void
	setThreads(
		String	str )
	{
		int procs = Runtime.getRuntime().availableProcessors();

		try{			
			threads = Integer.parseInt( str );
			
			if ( threads > procs ){
				
				threads = procs;
			}
		}catch( Throwable e ){
			
			Debug.out( e );
			
			threads = 1;
		}	
	}
	
	private void chmod(String flags,String path) {
		String[] args = {"/bin/chmod",flags,path.replaceAll(" ", "\\ ")};
		try {
			Runtime.getRuntime().exec(args);
		} catch (Exception e) {
			log("Error while allowing execution of " + path,e);
		}
	}

	public void
	log(
		String		str )
	{
		logger.log( str );
	}
	
	public void
	log(
		String		str,
		Throwable 	e )
	{
		logger.log( str + ": " + Debug.getNestedExceptionMessage(e));
	}
	
	public void 
	unload() 
		throws PluginException 
	{
		synchronized( this ){
			
			if ( active_analysis.size() > 0 || active_analysis2.size() > 0 || active_transcode.size() > 0 ){
				
				throw( new PluginException( "Unload prohibited, active transcodes" ));
			}
			
			plugin_unloading = true;
		}
		
		if ( config_model != null ){
		
			config_model.destroy();
		}
		
		if ( view_model != null ){
		
			view_model.destroy();
		}
		
		if ( itemTranscodeFiles != null ){
			
			itemTranscodeFiles.remove();
			itemTranscodeFiles = null;
		}
		
		if ( itemTranscodeMyTorrentsBig != null ){
			
			itemTranscodeMyTorrentsBig.remove();	
			itemTranscodeMyTorrentsBig = null;
		}
		
		if ( itemTranscodeMyTorrentsComplete != null ){
		
			itemTranscodeMyTorrentsComplete.remove();
			itemTranscodeMyTorrentsComplete = null;
		}
		
		for ( TableContextMenuItem cmi: itemMediaInfos ){
			
			cmi.remove();
		}
		
		itemMediaInfos.clear();
		
		if ( provider != null ){
			
			provider.destroy();
		}
		
		if ( discoverer != null ){
			
			discoverer.destroy();
		}
	}
	
	private Object	dm_lock			= new Object();
	private boolean	tried_to_get_dm = false;
	
	public DeviceManager
	getDeviceManager()
	{
		synchronized( dm_lock ){
			
				// not available with classic UI
			
			if ( device_manager == null && !tried_to_get_dm ){
			
				tried_to_get_dm = true;
				
				device_manager = DeviceManagerFactory.getSingleton();
			}
			
			return( device_manager );
		}
	}

		// IPC methods
	
	public Map<String,Map>
	getProfiles()
	{
		TranscodeProfile[]	profiles = provider.getProfiles();
		
		Map<String,Map> res = new HashMap<String, Map>();

		for ( TranscodeProfile profile: profiles ){
			
			Map	map = new HashMap();
			
			res.put( profile.getName(), map );
			
			map.put( "display-name", profile.getDisplayName());
			map.put( "device", profile.getDevice());
			map.put( "desc", profile.getDescription());
			map.put( "streamable", profile.isStreamable()?"yes":"no" );
			map.put( "icon-url", profile.getIconURL());
			map.put( "icon-index", new Long(profile.getIconIndex()));
			map.put( "file-ext", profile.getExtension());
		}
		
		
		return( res );
	}
	
	public String
	addProfile(
		File		file )
	{
		TranscodeProfile profile = provider.addProfile( file );
		
		if ( profile == null ){
			
			return( null );
		}
		
		return( profile.getName());
	}
	
	public void
	addProfileListChangedListener(Runnable runnable) {
		if (provider == null) {
			return;
		}
		provider.addProfilesChangedListener(runnable);
	}
	
	private analysisContext2
	getMediaInfo(
		DiskManagerFileInfo		input )
	
		throws TranscodeException
	{
		try{

			URL 				source_url		= null;
			File				source_file	 	= null;

			long	input_length = input.getLength();

			if ( input_length > 0 && input_length == input.getDownloaded()){

				File file = input.getFile();

				if ( file.exists() && file.length() == input_length ){

					source_file = file;
				}
			}

			TranscodePipeStreamSource		pipe = null;

			if ( source_file == null ){

					// race condition here on auto-transcodes due to downloadadded listeners - can add the xcode to queue
					// and schedule before added to upnpms - simple hack is to hang about a bit

				for ( int i=0; i<10; i++ ){

					PluginInterface av_pi = pluginInterface.getPluginManager().getPluginInterfaceByID( "azupnpav" );

					if ( av_pi == null ){

						throw( new TranscodeException( "Media Server plugin not found" ));
					}

					IPCInterface av_ipc = av_pi.getIPC();

					String url_str = (String)av_ipc.invoke( "getContentURL", new Object[]{ input });

					if ( url_str != null && url_str.length() > 0 ){

						source_url = new URL( url_str );

						pipe = new TranscodePipeStreamSource( source_url.getHost(), source_url.getPort());

						source_url = UrlUtils.setHost( source_url, "127.0.0.1" );

						source_url = UrlUtils.setPort( source_url, pipe.getPort2());
					}

					if ( source_url != null ){

						break;

					}else{

						try{
							Thread.sleep(1000);

						}catch( Throwable e ){

							break;
						}
					}
				}
			}

			if ( source_file == null && source_url == null ){

				throw( new TranscodeException( "File doesn't exist" ));
			}

			final TranscodePipeStreamSource f_pipe = pipe;

			try{

				final analysisContext2 analysis_context;

				if ( source_url != null ){

					analysis_context = new analysisContext2( source_url );
					
				}else{
					
					analysis_context = new analysisContext2( source_file );
				}


				new AEThread2( "analysisStatus", true )
				{
					@Override
					public void
					run()
					{
						try{
							while( true ){

								Map status = analysis_context.getStatus();

								long	state = (Long)status.get( "state" );

								if ( state == 0 ){

										// running

									try{
										Thread.sleep( 250 );
										
									}catch( Throwable e ){
										
									}
								}else{
									
									break;
								}
							}
						}finally{

							if ( f_pipe != null ){

								f_pipe.destroy2();
							}
						}
					}
				}.start();

				return( analysis_context );

			}catch( Throwable e ){

				if ( pipe != null ){

					pipe.destroy2();
				}

				throw( e );
			}
		}catch( TranscodeException e ){

			throw( e );

		}catch( Throwable e ){

			throw( new TranscodeException( "analysis failed", e ));
		}
	}
		
		// analysis
		
	
	public Object
	analyseContent(
		URL			input,
		String		profile_name )
	
		throws IPCException
	{
		log( "Analysing " + input + "/" + profile_name );
		
		TranscodeProfile	profile = provider.getProfile( profile_name );
		
		if ( profile == null ){
			
			throw( new IPCException( "Unknown profile '" + profile_name + "'" ));
		}
						
		return( new analysisContext( input, profile));
	}
	
	public Object
	analyseContent(
		File		input,
		String		profile_name )
	
		throws IPCException
	{
		log( "Analysing " + input + "/" + profile_name );

		TranscodeProfile	profile = provider.getProfile( profile_name );
		
		if ( profile == null ){
			
			throw( new IPCException( "Unknown profile '" + profile_name + "'" ));
		}
						
		return( new analysisContext( input, profile));
	}
	
	public Map
	getAnalysisStatus(
		Object		_context )
	{
		analysisContext	context = (analysisContext)_context;

		return( context.getStatus());
	}
	
	public void
	cancelAnalysis(
		Object		_context )
	{
		analysisContext	context = (analysisContext)_context;
		
		context.cancel();
	}
	
	protected class
	analysisContext
	{
		private URL						input_url;
		private File					input_file;
		
		private TranscodeProfile		profile;
		private String					device;
		private File					temp_file;
				
		private AESemaphore sem = new AESemaphore( "Analysis" );
		
		private MediaAnalyser 			mediaAnalyser;
		
		private Map<String,Object>	result = new HashMap<String, Object>();
		
		private volatile InputStream	http_is;
		
		private volatile boolean	done;
		private volatile Throwable	error;
		private volatile boolean	cancelled;
		
		protected
		analysisContext(
			URL					_input_url,
			TranscodeProfile	_profile)
		{
			input_url		= _input_url;
			profile 		= _profile;
			device			= _profile.getDevice();
			
			String[]	file_name = input_url.getPath().split( "/" );
			
			temp_file = new File( temp_dir, file_name[file_name.length-1] );

			initialise();
		}
		
		protected
		analysisContext(
			File				_input_file,
			TranscodeProfile	_profile)
		{
			input_file		= _input_file;
			profile 		= _profile;
			device			= _profile.getDevice();
			
			initialise();
		}
		
		protected void
		initialise()
		{
			new AEThread2( "Analysis", true )
			{
				public void
				run()
				{
					try{
						sem.releaseForever();

						synchronized( TranscoderPlugin.this ){
							
							if ( plugin_closing || plugin_unloading ){
								
								error = new Throwable( "Plugin closing/unloading" );
								
								log( "Analysis failed", error );
								
								return;
							}
							
							active_analysis.add( analysisContext.this );
						}
									
						if ( cancelled ){
							
							return;
						}
						
						result.put( "xcode_required", true );
						
						int	http_timeout = http_timeout_mins.getValue() * 60* 1000;
						
						try{
							MediaInformation info = null;
						
							long	source_length = -1;
							
							if ( input_url != null ){
																	
								RandomAccessFile 	o_raf = null;
								
								final int INITIAL_START_OF_FILE_CHUNK 	= analysis_start_chunk.getValue()*1024;
								final int INITIAL_END_OF_FILE_CHUNK 	= analysis_end_chunk.getValue()*1024;

								int START_OF_FILE_CHUNK = INITIAL_START_OF_FILE_CHUNK;
								int END_OF_FILE_CHUNK 	= INITIAL_END_OF_FILE_CHUNK;
								
								boolean done_whole_file = false;
								
								while( !cancelled ){
									
									log( "Downloading first " + DisplayFormatters.formatByteCountToKiBEtc( START_OF_FILE_CHUNK ) + " for analysis" );
									
									try{
										AEProxySelectorFactory.getSelector().startNoProxy();
										
										if ( source_length == -1 ){
											
											HttpURLConnection connection = (HttpURLConnection)input_url.openConnection();
										
											connection.setReadTimeout( http_timeout );
											
											connection.setRequestMethod( "HEAD" );
											
											connection.connect();
																			
											long	length = -1;
											
											try{											
												length = Long.parseLong( connection.getHeaderField( "content-length" ));
											
											}catch( Throwable e ){
												
												e.printStackTrace();
											}
											
											source_length = length;
										}
										
										HttpURLConnection connection = (HttpURLConnection)input_url.openConnection();
		
										connection.setReadTimeout( http_timeout );
										
										if ( source_length > 0 && source_length > START_OF_FILE_CHUNK ){
											
											connection.setRequestProperty( "range", "bytes=0-" + (START_OF_FILE_CHUNK-1));
											
										}else{
											
											if ( done_whole_file ){
												
												log( "Analysis failed, whole file scanned" );
												
												break;
												
											}else{
												
												done_whole_file = true;
											}
										}
										
										http_is = connection.getInputStream();
		
										o_raf = new RandomAccessFile( temp_file, "rw" );
																	
										byte[]	buffer = new byte[64*1024];
										
										long	total_written = 0;
										
										while( !cancelled ){
											
											int	len = http_is.read( buffer );
											
											if ( len <= 0 ){
												
												if ( source_length >= 0 && total_written == 0 ){
													
													throw( new FileNotFoundException());
												}
												
												break;
											}
											
											o_raf.write( buffer, 0, len );
											
											total_written += len;
											
											if ( total_written >= START_OF_FILE_CHUNK ){
												
												break;
											}
										}
																	
										o_raf.close();
										
										o_raf = null;
										
										if ( cancelled ){
										
											break;
										}
										
										mediaAnalyser = new MediaAnalyser( TranscoderPlugin.this, mediaInfoPath );
								
										info = mediaAnalyser.analyse( temp_file.getAbsolutePath());
									
										mediaAnalyser = null;
									
										if (	info.hasGeneralStream() &&
												info.getAudioStreams().size() > 0 &&
												info.getVideoStreams().size() > 0 ){
													
											log( "Analysis of initial chunk succeeded" );
											
											break;
										}

										
										if ( done_whole_file ){
											
											log( "Analysis failed, whole file scanned" );
											
											break;
											
										}else if (	source_length > START_OF_FILE_CHUNK &&
													source_length - END_OF_FILE_CHUNK > 0 ){
												
											log( "Downloading last " + DisplayFormatters.formatByteCountToKiBEtc( END_OF_FILE_CHUNK ) + " for analysis" );
	
												// looks like it didn't work, grab end of file and retry
											
											o_raf = new RandomAccessFile( temp_file, "rw" );
											
												// check that we have enough space to allocate for the end
											
											o_raf.seek( source_length );
											
											o_raf.seek( source_length - END_OF_FILE_CHUNK );
											
											http_is.close();
											
											connection = (HttpURLConnection)input_url.openConnection();
											
											connection.setReadTimeout( http_timeout );
											
											connection.setRequestProperty( "range", "bytes=" + (source_length-END_OF_FILE_CHUNK) + "-" );
											
											http_is = connection.getInputStream();
											
											total_written = 0;
											
											while( !cancelled ){
												
												int	len = http_is.read( buffer );
												
												if ( len <= 0 ){
													
													break;
												}
												
												o_raf.write( buffer, 0, len );
												
												total_written += len;
												
												if ( total_written >= END_OF_FILE_CHUNK ){
													
													break;
												}
											}
																		
											o_raf.close();
											
											o_raf = null;
											
											if ( cancelled ){
												
												break;
											}
											
											mediaAnalyser = new MediaAnalyser( TranscoderPlugin.this, mediaInfoPath );
												
											info = mediaAnalyser.analyse( temp_file.getAbsolutePath());

											mediaAnalyser = null;

											if ( 	info.hasGeneralStream() &&
													info.getAudioStreams().size() > 0 &&
													info.getVideoStreams().size() > 0 ){
													
												log( "Analysis of final chunk succeeded" );
												
												break;	
											}
										}else{
											
											log( "Analysis failed, input exhausted" );
											
											break;
										}
							
										if ( 	START_OF_FILE_CHUNK >= 16*1024*1024 &&
												END_OF_FILE_CHUNK 	>= 16*1024*1024 ){
											
											log( "Analysis failed, limits exceeded" );
											
											break;
										}

										START_OF_FILE_CHUNK = START_OF_FILE_CHUNK*2;
										END_OF_FILE_CHUNK	= END_OF_FILE_CHUNK*2;
										
									}finally{
										
										AEProxySelectorFactory.getSelector().endNoProxy();
										
										try{
											if ( o_raf != null ){
												
												o_raf.close();
											}
										}catch( Throwable e ){
										}
										
										try{
											if ( http_is != null ){
												
												http_is.close();
												
												http_is = null;
											}
										}catch( Throwable e ){
										}
									}
								}
							}else{
								
								source_length = input_file.length();
								
								mediaAnalyser = new MediaAnalyser( TranscoderPlugin.this, mediaInfoPath );
								
								info = mediaAnalyser.analyse( input_file.getAbsolutePath());
							
								mediaAnalyser = null;
							}
							
							if ( !cancelled && info != null ){
								
								result.put( "source_size", source_length );
								
								result.put( "media_info", info );
								
								List<VideoStream> videos = info.getVideoStreams();
								
								for ( VideoStream video: videos ){
									
									Long vid_duration = new Long(video.getDurationSecs()*1000 );
									
									result.put( "video_duration_millis", vid_duration );
									
									result.put( "duration_millis", vid_duration );	// legacy
									
									result.put( "estimated_transcoded_size", new Long(profile.getEstimatedTranscodeSize(video.getDurationSecs())));
									
									result.put( "video_width", new Long(video.getWidth()));
									
									result.put( "video_height", new Long(video.getHeight()));
																	
									break;
								}
								
								List<AudioStream> audios = info.getAudioStreams();
								
								for ( AudioStream audio: audios ){
									
									Long audio_duration = new Long(audio.getDurationSecs()*1000 );
									
									result.put( "audio_duration_millis", audio_duration );
								}
								
								DeviceSpecification deviceSpecification = DeviceSpecificationFinder.getDeviceSpecificationByDeviceName( TranscoderPlugin.this, device );
													
								if ( deviceSpecification != null && info != null ){
									
									result.put("device_specification", deviceSpecification);
								
									if ( !deviceSpecification.needsTranscoding( info )){
										
										result.put( "xcode_required", false );
									}
								}
							}
							
							log( "Analysis result: " + result );
													
							done	= true;
							
						}catch( Throwable e ){
							
							log( "Analysis failed", e );
							
							error = e;
						}
					}finally{
							
						if ( temp_file != null ){
						
							temp_file.delete();
						}
						
						synchronized( TranscoderPlugin.this ){
							
							active_analysis.remove( analysisContext.this );
						}
					}
				}
			}.start();
		}
		
		protected void
		cancel()
		{
			sem.reserve();
				
			log( "Analysis cancelled" );
			
			cancelled = true;
			
			if ( http_is != null ){
				
				try{
					http_is.close();
					
				}catch( Throwable e ){
					
				}
			}
			
			MediaAnalyser a = mediaAnalyser;
			
			if ( a != null ){
				
				a.cancel();
			}
		}
				
		public Map<String,Object>
		getStatus()
		{
			Map<String,Object>	status = new HashMap<String,Object>();
			
			int	state = 0;
			
			if ( cancelled ){
				
				state = 1;
				
			}else if ( error != null ){
				
				state = 2;
				
			}else if ( done ){
				
				state = 3;
			}
			
			status.put( "state", new Long( state ));
			
			if ( error != null ){
				
				status.put( "error", error );
			}
			
			if ( done ){
				
				status.put( "result", result );
			}
						
			return( status );
		}
	}
	
	protected class
	analysisContext2
	{
		private URL						input_url;
		private File					input_file;
		

		private File					temp_file;
				
		private AESemaphore sem = new AESemaphore( "Analysis2" );
		
		private MediaAnalyser 			mediaAnalyser;
				
		private volatile InputStream	http_is;
		
		private volatile boolean	done;
		private volatile Throwable	error;
		private volatile boolean	cancelled;
		
		String		result;
		
		protected
		analysisContext2(
			URL					_input_url )
		{
			input_url		= _input_url;
			
			String[]	file_name = input_url.getPath().split( "/" );
			
			temp_file = new File( temp_dir, file_name[file_name.length-1] );

			initialise();
		}
		
		protected
		analysisContext2(
			File				_input_file )
		{
			input_file		= _input_file;
			
			initialise();
		}
		
		protected void
		initialise()
		{
			new AEThread2( "Analysis", true )
			{
				public void
				run()
				{
					try{
						sem.releaseForever();

						synchronized( TranscoderPlugin.this ){
							
							if ( plugin_closing || plugin_unloading ){
								
								error = new Throwable( "Plugin closing/unloading" );
								
								log( "Analysis failed", error );
								
								return;
							}
							
							active_analysis2.add( analysisContext2.this );
						}
									
						if ( cancelled ){
							
							return;
						}
												
						int	http_timeout = http_timeout_mins.getValue() * 60* 1000;
						
						try{
							String info = null;
						
							long	source_length = -1;
							
							if ( input_url != null ){
																	
								RandomAccessFile 	o_raf = null;
								
								final int INITIAL_START_OF_FILE_CHUNK 	= analysis_start_chunk.getValue()*1024;
								final int INITIAL_END_OF_FILE_CHUNK 	= analysis_end_chunk.getValue()*1024;

								int START_OF_FILE_CHUNK = INITIAL_START_OF_FILE_CHUNK;
								int END_OF_FILE_CHUNK 	= INITIAL_END_OF_FILE_CHUNK;
								
								boolean done_whole_file = false;
								
								while( !cancelled ){
									
									log( "Downloading first " + DisplayFormatters.formatByteCountToKiBEtc( START_OF_FILE_CHUNK ) + " for analysis" );
									
									try{
										AEProxySelectorFactory.getSelector().startNoProxy();
										
										if ( source_length == -1 ){
											
											HttpURLConnection connection = (HttpURLConnection)input_url.openConnection();
										
											connection.setReadTimeout( http_timeout );
											
											connection.setRequestMethod( "HEAD" );
											
											connection.connect();
																			
											long	length = -1;
											
											try{											
												length = Long.parseLong( connection.getHeaderField( "content-length" ));
											
											}catch( Throwable e ){
												
												e.printStackTrace();
											}
											
											source_length = length;
										}
										
										HttpURLConnection connection = (HttpURLConnection)input_url.openConnection();
		
										connection.setReadTimeout( http_timeout );
										
										if ( source_length > 0 && source_length > START_OF_FILE_CHUNK ){
											
											connection.setRequestProperty( "range", "bytes=0-" + (START_OF_FILE_CHUNK-1));
											
										}else{
											
											if ( done_whole_file ){
												
												log( "Analysis failed, whole file scanned" );
												
												break;
												
											}else{
												
												done_whole_file = true;
											}
										}
										
										http_is = connection.getInputStream();
		
										o_raf = new RandomAccessFile( temp_file, "rw" );
																	
										byte[]	buffer = new byte[64*1024];
										
										long	total_written = 0;
										
										while( !cancelled ){
											
											int	len = http_is.read( buffer );
											
											if ( len <= 0 ){
												
												if ( source_length >= 0 && total_written == 0 ){
													
													throw( new FileNotFoundException());
												}
												
												break;
											}
											
											o_raf.write( buffer, 0, len );
											
											total_written += len;
											
											if ( total_written >= START_OF_FILE_CHUNK ){
												
												break;
											}
										}
																	
										o_raf.close();
										
										o_raf = null;
										
										if ( cancelled ){
										
											break;
										}
										
										mediaAnalyser = new MediaAnalyser( TranscoderPlugin.this, mediaInfoPath );
								
										info = mediaAnalyser.getCompleteMediaInformation( temp_file.getAbsolutePath());
									
										mediaAnalyser = null;
									
										if ( info != null ){
													
											log( "Analysis of initial chunk succeeded" );
											
											break;
										}

										
										if ( done_whole_file ){
											
											log( "Analysis failed, whole file scanned" );
											
											break;
											
										}else if (	source_length > START_OF_FILE_CHUNK &&
													source_length - END_OF_FILE_CHUNK > 0 ){
												
											log( "Downloading last " + DisplayFormatters.formatByteCountToKiBEtc( END_OF_FILE_CHUNK ) + " for analysis" );
	
												// looks like it didn't work, grab end of file and retry
											
											o_raf = new RandomAccessFile( temp_file, "rw" );
											
												// check that we have enough space to allocate for the end
											
											o_raf.seek( source_length );
											
											o_raf.seek( source_length - END_OF_FILE_CHUNK );
											
											http_is.close();
											
											connection = (HttpURLConnection)input_url.openConnection();
											
											connection.setReadTimeout( http_timeout );
											
											connection.setRequestProperty( "range", "bytes=" + (source_length-END_OF_FILE_CHUNK) + "-" );
											
											http_is = connection.getInputStream();
											
											total_written = 0;
											
											while( !cancelled ){
												
												int	len = http_is.read( buffer );
												
												if ( len <= 0 ){
													
													break;
												}
												
												o_raf.write( buffer, 0, len );
												
												total_written += len;
												
												if ( total_written >= END_OF_FILE_CHUNK ){
													
													break;
												}
											}
																		
											o_raf.close();
											
											o_raf = null;
											
											if ( cancelled ){
												
												break;
											}
											
											mediaAnalyser = new MediaAnalyser( TranscoderPlugin.this, mediaInfoPath );
												
											info = mediaAnalyser.getCompleteMediaInformation( temp_file.getAbsolutePath());

											mediaAnalyser = null;

											if ( info != null ){
													
												log( "Analysis of final chunk succeeded" );
												
												break;	
											}
										}else{
											
											log( "Analysis failed, input exhausted" );
											
											break;
										}
							
										if ( 	START_OF_FILE_CHUNK >= 16*1024*1024 &&
												END_OF_FILE_CHUNK 	>= 16*1024*1024 ){
											
											log( "Analysis failed, limits exceeded" );
											
											break;
										}

										START_OF_FILE_CHUNK = START_OF_FILE_CHUNK*2;
										END_OF_FILE_CHUNK	= END_OF_FILE_CHUNK*2;
										
									}finally{
										
										AEProxySelectorFactory.getSelector().endNoProxy();
										
										try{
											if ( o_raf != null ){
												
												o_raf.close();
											}
										}catch( Throwable e ){
										}
										
										try{
											if ( http_is != null ){
												
												http_is.close();
												
												http_is = null;
											}
										}catch( Throwable e ){
										}
									}
								}
							}else{
								
								source_length = input_file.length();
								
								mediaAnalyser = new MediaAnalyser( TranscoderPlugin.this, mediaInfoPath );
								
								info = mediaAnalyser.getCompleteMediaInformation( input_file.getAbsolutePath());
							
								mediaAnalyser = null;
							}
							
							if ( !cancelled && info != null ){
								
								result = info;
							}
							
							log( "Analysis result: " + result );
													
							done	= true;
							
						}catch( Throwable e ){
							
							log( "Analysis failed", e );
							
							error = e;
						}
					}finally{
							
						if ( temp_file != null ){
						
							temp_file.delete();
						}
						
						synchronized( TranscoderPlugin.this ){
							
							active_analysis2.remove( analysisContext2.this );
						}
					}
				}
			}.start();
		}
		
		protected void
		cancel()
		{
			sem.reserve();
				
			log( "Analysis cancelled" );
			
			cancelled = true;
			
			if ( http_is != null ){
				
				try{
					http_is.close();
					
				}catch( Throwable e ){
					
				}
			}
			
			MediaAnalyser a = mediaAnalyser;
			
			if ( a != null ){
				
				a.cancel();
			}
		}
				
		public Map<String,Object>
		getStatus()
		{
			Map<String,Object>	status = new HashMap<String,Object>();
			
			int	state = 0;
			
			if ( cancelled ){
				
				state = 1;
				
			}else if ( error != null ){
				
				state = 2;
				
			}else if ( done ){
				
				state = 3;
			}
			
			status.put( "state", new Long( state ));
			
			if ( error != null ){
				
				status.put( "error", error );
			}
			
			if ( done ){
				
				status.put( "result", result );
			}
						
			return( status );
		}
	}
	
		// transcode
	
	public Object
	transcodeToFile(
		URL					input,
		String				profile_name,
		File				output_file )
	
		throws IPCException
	{
		return( transcodeToFile( new HashMap<String,Object>(), input, profile_name, output_file ));
	}
	
	public Object
	transcodeToFile(
		Map<String,Object>	analysis_result,
		URL					input,
		String				profile_name,
		File				output_file )
	
		throws IPCException
	{
		log( "Transcoding " + input + "/" + profile_name + " -> " + output_file );
		
		try{
			String	input_str = input.toExternalForm();
			
			try{
				File file = new File(input.toURI());
				
				if ( file.exists()){
					
					input_str = file.getAbsolutePath();
				}
			}catch( Throwable e ){
			}
			
			
			TranscodeProfile	profile = provider.getProfile( profile_name );
			
			if ( profile == null ){
				
				throw( new IPCException( "Unknown profile '" + profile_name + "'" ));
			}
						
			transcodeContext	context = new transcodeContext( analysis_result, input_str, profile, output_file );
			
			return( context );
			
		}catch( Throwable e ){
			
			log( "Transcode failed", e);
			
			throw( new IPCException( "operation failed", e ));
		}
	}
	
	public Object
	transcodeToTCP(
		URL					input,
		String				profile_name,
		int					output_port )
	
		throws IPCException
	{
		return( transcodeToTCP(  new HashMap<String,Object>(), input, profile_name, output_port ));
	}
	
	public Object
	transcodeToTCP(
		Map<String,Object>	analysis_result,
		URL					input,
		String				profile_name,
		int					output_port )
	
		throws IPCException
	{
		log( "Transcoding " + input + "/" + profile_name + " -> port " + output_port );

		try{
			String	input_str = input.toExternalForm();
			
			try{
				File file = new File(input.toURI());
				
				if ( file.exists()){
					
					input_str = file.getAbsolutePath();
				}
			}catch( Throwable e ){
			}
						
			TranscodeProfile	profile = provider.getProfile( profile_name );
			
			if ( profile == null ){
				
				throw( new IPCException( "Unknown profile '" + profile_name + "'" ));
			}
						
			transcodeContext	context = new transcodeContext( analysis_result, input_str, profile, output_port );
			
			return( context );
			
		}catch( Throwable e ){
			
			log( "Transcode failed", e);
			
			throw( new IPCException( "operation failed", e ));
		}
	}
	
	public Map
	getTranscodeStatus(
		Object		_context )
	{
		transcodeContext	context = (transcodeContext)_context;

		return( context.getStatus());
	}
	
	public void
	cancelTranscode(
		Object		_context )
	{
		transcodeContext	context = (transcodeContext)_context;
		
		context.cancel();
	}
	
	protected class
	transcodeContext
		implements TranscodeListener
	{
		private Map<String,Object>		analysis_result;
		private String					input_str;
		private TranscodeProfile		profile;
		private int						output_port;
		private File					output_file;
		
		private volatile Transcoder				transcoder;
		private volatile TranscodeOperation		operation;
		
		private AESemaphore sem = new AESemaphore( "Transcode:async" );
		
		private double	percent_done;
		private int		eta_latest	= Integer.MAX_VALUE;
		private int		ETA_AVERAGE_BUCKETS	= 20;
		private Average	eta_average = AverageFactory.MovingImmediateAverage( ETA_AVERAGE_BUCKETS );
		private int		new_width;
		private int		new_height;
		
		private volatile boolean	done;
		private volatile Throwable	error;
		private volatile boolean	error_is_perm;
		private volatile boolean	cancelled;
		
		private long	start = SystemTime.getMonotonousTime();
		
		protected
		transcodeContext(
			Map<String,Object>	_analysis_result,
			String				_input_str,
			TranscodeProfile	_profile,
			int					_output_port )
		{
			analysis_result	= _analysis_result;
			input_str		= _input_str;
			profile			= _profile;
			output_port		= _output_port;
			
			initialise();
		}
		
		protected
		transcodeContext(
			Map<String,Object>	_analysis_result,
			String				_input_str,
			TranscodeProfile	_profile,
			File				_output_file )
		{
			analysis_result	= _analysis_result;
			input_str		= _input_str;
			profile			= _profile;
			output_file		= _output_file;
			
			initialise();
		}
		
		protected void
		initialise()
		{
			new AEThread2( "Transcode:async", true )
			{
				public void
				run()
				{
					try{
						sem.releaseForever();
						
						synchronized( TranscoderPlugin.this ){
							
							if ( plugin_closing || plugin_unloading ){
								
								error = new Throwable( "Plugin closing/unloading" );
								
								log( "Transcode failed", error );
								
								return;
							}
							
							active_transcode.add( transcodeContext.this );
						}
						
						if ( cancelled ){
							
							return;
						}
						
							// possibly null during migration to use this although won't
							// be null for release
						
						MediaInformation	media_info = (MediaInformation)analysis_result.get( "media_info" );
						DeviceSpecification deviceSpecification = (DeviceSpecification) analysis_result.get( "device_specification" );
						
						Boolean	force_b = (Boolean)analysis_result.get( "force_xcode" );
						
						try{
							transcoder = new Transcoder( pluginInterface, new String[]{ ffmpegPathOld, ffmpegPathNew }, input_str );
						
							if ( !cancelled ){
								
								TranscodeContext	context = transcoder.getContext();
								
								context.setMediaInformation(media_info);
								
								context.setDeviceSpecification(deviceSpecification);
								
								if ( force_b != null && force_b ){
								
									context.setForceTranscode( true );
								}
								
								context.setThreads(threads);
							}
							
							if ( !cancelled ){
							
								String	target;
								
								if ( output_file != null ){
									
									target = output_file.getAbsolutePath();
									
								}else{
									
									target = "tcp://127.0.0.1:" + output_port;
								}
								
								operation = transcoder.transcode( transcodeContext.this, profile, target );
							}
							
							if ( cancelled ){
								
								operation.cancel();
							}
						}catch( Throwable e ){
													
							reportFailed( e, false );
						}
					}finally{
						
						if ( cancelled ){
							
							synchronized( TranscoderPlugin.this ){

								active_transcode.remove( transcodeContext.this );
							}
						}
					}
				}
			}.start();
		}
		
		protected void
		cancel()
		{
			log( "Transcode cancelled" );
			
			sem.reserve();
							
			cancelled = true;
			
			if ( operation != null ){
				
				operation.cancel();
			}
		}
		
		public void 
		reportProgress(
			TranscodeContext 	context, 
			int 				currentFrame,
			int 				nbFrames,
			int 				framesPerSec )
		{
			synchronized( this ){
				
				percent_done = 100d*currentFrame / nbFrames;
				
				//eta	= operation.getETA();
				
				long now = SystemTime.getMonotonousTime();
				
				long elapsed = now - start;
				
				if ( percent_done > 0 ){
					
					long estimated_complete = (long)( start + 100*elapsed/percent_done );
					
					double d_eta_latest = ((float)( estimated_complete - now ))/1000;
					
					eta_latest = (int)d_eta_latest;
					
						// give more weight to values towards the end of process to handle
						// the fairly common case of the xcode frame rate increasing at the
						// end of the content
					
					int	update_count;
					int WEIGHT_START_SECS = 20;
					
					if ( eta_latest > WEIGHT_START_SECS ){
						
						update_count = 1;
						
					}else if ( eta_latest <= 0 ){
						
						eta_latest = 0;
						
						update_count = ETA_AVERAGE_BUCKETS;
						
					}else{
						
						update_count = ETA_AVERAGE_BUCKETS * ( WEIGHT_START_SECS - eta_latest )/WEIGHT_START_SECS;
						
						if ( update_count < 1 ){
							
							update_count = 1;
						}
					}
					
					for ( int i=0;i<update_count;i++ ){
						
						eta_average.update( d_eta_latest );
					}
				}
			}
		}
	
		public void 
		reportDone()
		{
			log( "Transcode complete" );

			done		= true;
			
			synchronized( TranscoderPlugin.this ){

				active_transcode.remove( transcodeContext.this );
			}
		}
	
		public void 
		reportFailed(
			Throwable 	e,
			boolean		perm )
		{
			log( "Transcode failed", e );

			error			= e;
			error_is_perm	= perm;
			
			synchronized( TranscoderPlugin.this ){

				active_transcode.remove( transcodeContext.this );
			}
		}
		
		public void
		reportNewDimensions(
			int	width,
			int height )
		{
			synchronized( this ){
				
				new_width	= width;
				new_height	= height;
			}
		}
		
		public void 
		log(
			String str) 
		{
			TranscoderPlugin.this.log( str );
		}
		
		public void 
		log(
			String 		str,
			Throwable 	e )
		{
			TranscoderPlugin.this.log( str, e );
		}
		
		public Map
		getStatus()
		{
			synchronized( this ){
				
				Map	status = new HashMap();
				
				int	state = 0;
				
				if ( cancelled ){
					
					state = 1;
					
				}else if ( error != null ){
					
					state = 2;
				}
				
				status.put( "state", new Long( state ));
				
				if ( error != null ){
					
					status.put( "error", error );
					
					status.put( "error_is_perm", error_is_perm );
				}
				
				status.put( "percent", new Integer( done?100:Math.min( (int)percent_done, 99 )));
				
				if ( eta_latest == Integer.MAX_VALUE ){
					
					status.put( "eta_secs", new Integer( eta_latest ));
				}else{
				
					status.put( "eta_secs", new Integer( done?0:Math.max((int)Math.ceil( eta_average.getAverage()), 0 )));
				}
				
				status.put( "new_width", new Integer( new_width ));
				
				status.put( "new_height", new Integer( new_height ));
				
				return( status );
			}
		}
	}
}
