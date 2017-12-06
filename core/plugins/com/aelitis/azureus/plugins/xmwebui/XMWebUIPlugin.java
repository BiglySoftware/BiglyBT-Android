/*
 * Created on Sep 16, 2009
 * Created by Paul Gardner
 * 
 * Copyright 2009 Vuze, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


package com.aelitis.azureus.plugins.xmwebui;

import static com.aelitis.azureus.plugins.xmwebui.TransmissionVars.*;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.gudy.bouncycastle.util.encoders.Base64;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.aelitis.azureus.plugins.remsearch.RemSearchPluginPageGenerator;
import com.aelitis.azureus.plugins.remsearch.RemSearchPluginPageGeneratorAdaptor;
import com.aelitis.azureus.plugins.remsearch.RemSearchPluginSearch;
import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.ipfilter.impl.IpFilterAutoLoaderImpl;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.metasearch.*;
import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.pairing.PairingManager;
import com.biglybt.core.pairing.PairingManagerFactory;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.peer.PEPeerStats;
import com.biglybt.core.peer.util.PeerUtils;
import com.biglybt.core.security.CryptoManager;
import com.biglybt.core.subs.*;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.client.TRTrackerAnnouncerResponse;
import com.biglybt.core.tracker.client.TRTrackerScraper;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.*;
import com.biglybt.core.versioncheck.VersionCheckClient;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.pif.*;
import com.biglybt.pif.config.ConfigParameter;
import com.biglybt.pif.config.ConfigParameterListener;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.*;
import com.biglybt.pif.download.DownloadStub.DownloadStubFile;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.torrent.TorrentDownloader;
import com.biglybt.pif.torrent.TorrentManager;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.update.Update;
import com.biglybt.pif.update.UpdateCheckInstance;
import com.biglybt.pif.update.UpdateCheckInstanceListener;
import com.biglybt.pif.update.UpdateManager;
import com.biglybt.pif.utils.Utilities;
import com.biglybt.pif.utils.Utilities.JSONServer;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderAdapter;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.utils.resourcedownloader.ResourceDownloaderFactoryImpl;
import com.biglybt.plugin.dht.DHTPlugin;
import com.biglybt.plugin.startstoprules.defaultplugin.DefaultRankCalculator;
import com.biglybt.plugin.startstoprules.defaultplugin.StartStopRulesDefaultPlugin;
import com.biglybt.ui.webplugin.WebPlugin;
import com.biglybt.util.JSONUtils;
import com.biglybt.util.MapUtils;
import com.biglybt.util.PlayUtils;

@SuppressWarnings({
	"unchecked",
	"rawtypes"
})
public class 
XMWebUIPlugin
	extends WebPlugin
	implements UnloadablePlugin, DownloadManagerListener
{	
	
	/**
	 * 5: No longer xml escapes strings when user agent does not start with "Mozilla/"
	 */
	private static final int VUZE_RPC_VERSION = 5;
	
	private static Download
	destubbify(
		DownloadStub	stub )
	
		throws DownloadException
	{
		return( stub.destubbify());
	}
	
		// end stuff
	
    public static final int DEFAULT_PORT    = 9091;

    private static Properties defaults = new Properties();

    static{
    	System.setProperty( "az.xmwebui.skip.ssl.hack", "true" );
    	
        defaults.put( WebPlugin.PR_DISABLABLE, new Boolean( true ));
        defaults.put( WebPlugin.PR_ENABLE, new Boolean( true ));
        defaults.put( WebPlugin.PR_PORT, new Integer( DEFAULT_PORT ));
        defaults.put( WebPlugin.PR_ROOT_DIR, "transmission/web" );
        defaults.put( WebPlugin.PR_ENABLE_KEEP_ALIVE, new Boolean(true));
        defaults.put( WebPlugin.PR_HIDE_RESOURCE_CONFIG, new Boolean(true));
        defaults.put( WebPlugin.PR_PAIRING_SID, "xmwebui" );
    }

    private static final String	SEARCH_PREFIX 	= "/psearch";
    private static final int	SEARCH_TIMEOUT	= 60*1000;

		protected static final long SEARCH_AUTOREMOVE_TIMEOUT = 60 * 1000 * 60l;
    
    private boolean	view_mode;
    
    BooleanParameter trace_param;
    private BooleanParameter hide_ln_param;
    
    private TorrentAttribute	t_id;
    
    private Map<Long,RecentlyRemovedData>	recently_removed 	= new HashMap<Long,RecentlyRemovedData>();
    
    private Set<Long>	stubbifying			= new HashSet<Long>();
    
    private Map<String, String> ip_to_session_id = new HashMap<String, String>();
    
    private RemSearchPluginPageGenerator search_handler;
    private TimerEventPeriodic				search_timer;
    
    private boolean							check_ids_outstanding = true;
    
    private Map<String,Map<Long,String>>	session_torrent_info_cache = new HashMap<String,Map<Long,String>>();
    
    private Map<String,SearchInstance>	active_searches = new HashMap<String, SearchInstance>();
    private Map<String,TagSearchInstance>	active_tagsearches = new HashMap<String, TagSearchInstance>();
    
    private Object			lifecycle_lock = new Object();
    private int 			lifecycle_state = 0;
    private boolean			update_in_progress;
    
    private List<MagnetDownload>		magnet_downloads = new ArrayList<XMWebUIPlugin.MagnetDownload>();
    
    private Object json_rpc_client;	// Object during transition to core support
    
    private Object							json_server_method_lock 	= new Object();
    private transient Map<String,Object>	json_server_methods 		= new HashMap<String, Object>();		// Object during transition to core support

		private BooleanParameter logtofile_param;

		private LoggerChannel log;

		private long lastVerserverCheck;
    
    public
    XMWebUIPlugin()
    {
    	super( defaults );
    	
    	search_handler = 
			new RemSearchPluginPageGenerator(
					new RemSearchPluginPageGeneratorAdaptor()
					{
						@Override
						public void
						searchReceived(
							String originator )
								
							throws IOException 
						{
						}
							
						@Override
						public void
						searchCreated(
							RemSearchPluginSearch search )
						{
						}
						
						@Override
						public void
						log(
							String str )
						{
							XMWebUIPlugin.this.log( str );
						};
						
						@Override
						public void
						log(
							String 		str,
							Throwable 	e )
						{
							XMWebUIPlugin.this.log( str, e );
						};
					},
					SEARCH_PREFIX,
					null,
					16,
					100,
					false );
    }
    
	@Override
	public void
	initialize(
		PluginInterface _plugin_interface )
	
		throws PluginException
	{	
		
		plugin_interface	= _plugin_interface;

		log = plugin_interface.getLogger().getChannel( "xmwebui" );
		defaults.put(PR_LOG, log);

		PluginConfig pluginconfig = plugin_interface.getPluginconfig();
		
		if (	!PairingManagerFactory.getSingleton().isEnabled() && 
				!pluginconfig.hasPluginParameter("Password")
				&& !pluginconfig.hasPluginParameter("Password Enable")){
			
			pluginconfig.setPluginParameter("Config Migrated", true);
			
			pluginconfig.setPluginParameter("Password Enable", true);
			
			SHA1Hasher hasher = new SHA1Hasher();
			
			pluginconfig.setPluginParameter("Password",
					hasher.calculateHash(RandomUtils.nextSecureHash()));
		}

		super.initialize( _plugin_interface );

		plugin_interface.getUtilities().getLocaleUtilities().integrateLocalisedMessageBundle(
				"com.aelitis.azureus.plugins.xmwebui.internat.Messages" );
		
		t_id = plugin_interface.getTorrentManager().getPluginAttribute( "xmui.dl.id" );
		
		BasicPluginConfigModel	config = getConfigModel();
			
        int port = pluginconfig.getPluginIntParameter( WebPlugin.CONFIG_PORT, CONFIG_PORT_DEFAULT );

		config.addLabelParameter2( "xmwebui.blank" );

		config.addHyperlinkParameter2( "xmwebui.openui", "http://127.0.0.1:" + port + "/" );
		
		config.addLabelParameter2( "xmwebui.blank" );

		hide_ln_param = config.addBooleanParameter2( "xmwebui.hidelownoise", "xmwebui.hidelownoise", true );

		trace_param = config.addBooleanParameter2( "xmwebui.trace", "xmwebui.trace", false );
		
		logtofile_param = config.addBooleanParameter2( "xmwebui.logtofile", "xmwebui.logtofile", false );

		changeLogToFile(logtofile_param.getValue());
		logtofile_param.addConfigParameterListener(new ConfigParameterListener() {
			@Override
			public void configParameterChanged(ConfigParameter param) {
				changeLogToFile(logtofile_param.getValue());
			}
		});
		
		ConfigParameter mode_parameter = pluginconfig.getPluginParameter( WebPlugin.CONFIG_MODE );

		if ( mode_parameter == null ){

			view_mode = true;

			checkViewMode();

		}else{

			mode_parameter.addConfigParameterListener(
				new ConfigParameterListener()
				{
					@Override
					public void
					configParameterChanged(
						ConfigParameter param )
					{
						setViewMode();
					}
				});

			setViewMode();
		}
				
		plugin_interface.getDownloadManager().addListener( this );
		
		plugin_interface.getDownloadManager().addDownloadStubListener(
			new DownloadStubListener()
			{	
				@Override
				public void
				downloadStubEventOccurred(
					DownloadStubEvent event )
						
					throws DownloadException 
				{
					int	event_type = event.getEventType();

					List<DownloadStub> stubs = event.getDownloadStubs();

					synchronized( recently_removed ){

						if ( event_type == DownloadStubEvent.DSE_STUB_WILL_BE_ADDED ){
																	
							for ( DownloadStub stub: stubs ){
								
								try{
									long id = destubbify( stub ).getLongAttribute( t_id );
									
									stubbifying.add( id );
									
									stub.setLongAttribute( t_id, id );
									
								}catch( Throwable e ){
									
									Debug.out( e );
								}
							}
					
						}else if ( event_type == DownloadStubEvent.DSE_STUB_ADDED ||  event_type == DownloadStubEvent.DSE_STUB_WILL_BE_REMOVED ){

							for ( DownloadStub stub: stubs ){
								
								long id = stub.getLongAttribute( t_id );
									
								stubbifying.remove( id );
							}
						}
					}
				}
			}, false );
		
		search_timer = SimpleTimer.addPeriodicEvent(
			"XMSearchTimeout",
			30*1000,
			new TimerEventPerformer()
			{
				@Override
				public void
				perform(
					TimerEvent event ) 
				{
					Map<String,RemSearchPluginSearch> searches = search_handler.getSearches();
					
					Iterator<RemSearchPluginSearch> it1 = searches.values().iterator();
						
					while( it1.hasNext()){
					
						RemSearchPluginSearch search = it1.next();
						
						if ( search.getAge() > SEARCH_TIMEOUT ){
																			
							log( "Timeout: " + search.getString());
							
							search.destroy();
						}
					}
					
					synchronized( active_searches ){
						
						Iterator<SearchInstance> it2 = active_searches.values().iterator();
						
						while( it2.hasNext()){
							
							SearchInstance search = it2.next();
							
							if (search.isComplete() && search.getLastResultsAgo() > SEARCH_AUTOREMOVE_TIMEOUT) {
								it2.remove();
							}
							
							if ( !search.isComplete() && search.getAge() > SEARCH_TIMEOUT ){
								
								log( "Timeout: " + search.getString());
								
								search.failWithTimeout();
							}	
						}
					}
				}
			});
		
		plugin_interface.addListener(
			new PluginAdapter()
			{
				@Override
				public void 
				initializationComplete() 
				{
					synchronized ( lifecycle_lock ){
					
						if ( lifecycle_state == 0 ){
					
							lifecycle_state = 1;
						}
					}
				}
			});
		
		plugin_interface.getUIManager().addUIListener(
			new UIManagerListener()
			{
				@Override
				public void
				UIAttached(
					UIInstance		instance )
				{
					if ( instance.getUIType().equals(UIInstance.UIT_SWT) ){
						
						try{
							Class.forName( "com.aelitis.azureus.plugins.xmwebui.swt.XMWebUIPluginView").getConstructor(
								new Class[]{ XMWebUIPlugin.class, UIInstance.class }).newInstance(
									new Object[]{ XMWebUIPlugin.this, instance } );
														
						}catch( Throwable e ){
							e.printStackTrace();
						}
					}
				}
				
				@Override
				public void
				UIDetached(
					UIInstance		instance )
				{
				}
			});
					
		json_rpc_client = 
			new Utilities.JSONClient() {
				
				@Override
				public void
				serverRegistered(
					JSONServer server ) 
				{
					List<String> methods = server.getSupportedMethods();

					//System.out.println( "Registering methods: " + server.getName() + " -> " + methods );

					synchronized( json_server_method_lock ){
						
						Map<String,Object> new_methods = new HashMap<String, Object>( json_server_methods );
													
						for ( String method: methods ){
							
							new_methods.put( method, server );
						}
						
						json_server_methods = new_methods;
					}
				}
				
				@Override
				public void
				serverUnregistered(
					JSONServer server ) 
				{
					List<String> methods = server.getSupportedMethods();
					
					//System.out.println( "Unregistering methods: " + server.getName() + " -> " + methods );

					synchronized( json_server_method_lock ){
						
						Map<String,Object> new_methods = new HashMap<String, Object>( json_server_methods );
													
						for ( String method: methods ){
							
							new_methods.remove( method );
						}
						
						json_server_methods = new_methods;
					}
				}
			};
			
		plugin_interface.getUtilities().registerJSONRPCClient((Utilities.JSONClient)json_rpc_client );
	}
	
	protected void changeLogToFile(boolean logToFile) {
		if (log != null) {
			if (logToFile) {
				log.setDiagnostic(1024l * 1024l, true);
			} else {
				// no way of turning off :(
			}
		}
	}

	private void
	checkViewMode()
	{
		if ( view_mode ){
			
			return;
		}
		
		PluginConfig pc = plugin_interface.getPluginconfig();
		
		{
			String 	data_dir 	= pc.getCoreStringParameter( PluginConfig.CORE_PARAM_STRING_DEFAULT_SAVE_PATH );
	
			boolean	data_bad = false;
			
			if ( data_dir == null || data_dir.length() == 0 ){
				
				data_bad = true;
				
			}else{
				
				File dir = new File( data_dir );
				
				if ( !dir.exists()){
					
					dir.mkdirs();
				}
				
				data_bad = !dir.canWrite();
			}
				
			if ( data_bad ){
				
				Logger.log(
					new LogAlert(
						true,
						LogAlert.AT_ERROR,
						MessageText.getString( "xmwebui.error.data_path" )));	
			}
		}
		
		if ( !pc.getUnsafeBooleanParameter( "Save Torrent Files" )){
			
			Logger.log(
					new LogAlert(
						true,
						LogAlert.AT_ERROR,
						MessageText.getString( "xmwebui.error.torrent_path" )));
		}else{
			
			String 	torrent_dir 	= pc.getUnsafeStringParameter( "General_sDefaultTorrent_Directory" );

			boolean torrent_bad = false;
			
			if ( torrent_dir == null || torrent_dir.length() == 0 ){
				
				torrent_bad = true;
				
			}else{
				
				File dir = new File( torrent_dir );
				
				if ( !dir.exists()){
					
					dir.mkdirs();
				}
				
				torrent_bad = !dir.canWrite();
			}
				
			if ( torrent_bad ){		
				
				Logger.log(
					new LogAlert(
						true,
						LogAlert.AT_ERROR,
						MessageText.getString( "xmwebui.error.torrent_path" )));	
			}
		}
	}
	
	@Override
	protected void
	setupServer()
	{
		PluginManager pm = plugin_interface.getPluginManager();
		
		if ( pm.isInitialized()){
			
			super.setupServer();
			
		}else{
			
				// defer the creation of the server as there are a bunch of features of the
				// rpc that require things to be reasonably well initialised to function
				// correctly (e.g. tracker peer sources logic needs other plugins to
				// have initialised)
			
			plugin_interface.addEventListener(
					new PluginEventListener()
					{
						@Override
						public void
						handleEvent(
							PluginEvent 	ev ) 
						{
							if ( ev.getType() == PluginEvent.PEV_ALL_PLUGINS_INITIALISED ){
								
								plugin_interface.removeEventListener( this );

								XMWebUIPlugin.super.setupServer();
							}
						}
					});
		}
	}
	
	@Override
	public void
	unload() 
		
		throws PluginException 
	{
		if ( search_timer != null ){
			
			search_timer.cancel();
			
			search_timer = null;
		}
		
		plugin_interface.getDownloadManager().removeListener( this );

		if ( json_rpc_client != null ){
		
			plugin_interface.getUtilities().unregisterJSONRPCClient((Utilities.JSONClient)json_rpc_client);
			
			json_rpc_client = null;
		}
		
		json_server_methods.clear();
		
		super.unloadPlugin();
	}
	
	protected void
	setViewMode()
	{
		String mode_str = plugin_interface.getPluginconfig().getPluginStringParameter( WebPlugin.CONFIG_MODE, WebPlugin.CONFIG_MODE_DEFAULT );

		view_mode = !mode_str.equalsIgnoreCase( WebPlugin.CONFIG_MODE_FULL );
		
		checkViewMode();
	}
	   
	public File
	getResourceDir()
	{
		return( new File( plugin_interface.getPluginDirectoryName(), "transmission" + File.separator + "web" ));
	}
	
	@Override
	public void
	downloadAdded(
		Download	download )
	{
	}
	
	// @see com.biglybt.pif.download.DownloadManagerListener#downloadRemoved(com.biglybt.pif.download.Download)
	@Override
	public void
	downloadRemoved(
		Download	download )
	{
		addRecentlyRemoved( download );
	}
	
	private void
	addRecentlyRemoved(
		DownloadStub	download )
	{
		synchronized( recently_removed ){			
			
			long id = getID( download, false );
			
			if ( id > 0 && !stubbifying.contains( id )){
			
				if ( !recently_removed.containsKey( id )){
					
					recently_removed.put( id, new RecentlyRemovedData( id ));
				}
			}
		}
	}
	
	private boolean
	handleRecentlyRemoved(
		String	session_id,
		Map		args,
		Map		result )
	{
		Object	ids = args.get( "ids" );
		
		if ( ids != null && ids instanceof String && ((String)ids).equals( "recently-active" )){
						
			synchronized( recently_removed ){
				
				if ( recently_removed.size() > 0 ){
					
					long now = SystemTime.getMonotonousTime();

					Iterator<RecentlyRemovedData> it = recently_removed.values().iterator();
					
					List<Long>	removed = new ArrayList<Long>();
					
					while( it.hasNext()){
					
						RecentlyRemovedData rrd = it.next();
							
						if ( !rrd.hasSession( session_id )){
							
							removed.add( rrd.getID());
						}
						
						if ( now - rrd.getCreateTime() > 60*1000 ){
							
							it.remove();
						}
					}
					
					if ( removed.size() > 0 ){

						//System.out.println( "Reporting removed to " + session_id + ": " + removed );
						
						result.put( "removed", removed );
					}
				}
			}
			
			return( true );
			
		}else{
			
			return( false );
		}
	}
	
	

	@Override
	public boolean
	generateSupport(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response )
	
		throws IOException
	{
		boolean logit = trace_param.getValue();

		if (request.getInputStream().available() == 0 && "chunked".equals(request.getHeaders().get("transfer-encoding"))) {
			response.setReplyStatus( 415 );
			return true;
		}
		
		try{
			String session_id = getSessionID( request );
			
			// Set cookie just in case client is looking for one..
			response.setHeader( "Set-Cookie", "X-Transmission-Session-Id=" + session_id + "; path=/; HttpOnly" );
			// This is the actual spec for massing session-id
			response.setHeader("X-Transmission-Session-Id", session_id );

			if (!isSessionValid(request)) {
				log("Header:\n" + request.getHeader());
				LineNumberReader lnr;
				if ("gzip".equals(request.getHeaders().get("content-encoding"))) {
					GZIPInputStream gzipIS = new GZIPInputStream(request.getInputStream());
					lnr = new LineNumberReader( new InputStreamReader( gzipIS, "UTF-8" ));
				} else {
					lnr = new LineNumberReader( new InputStreamReader( request.getInputStream(), "UTF-8" ));
				}
				while( true ){
					String	line = lnr.readLine();
					if ( line == null ){
						break;
					}
					log("409: " + line);
				}
				response.setReplyStatus( 409 );
				response.getOutputStream().write("You_didn_t_set_the_X-Transmission-Session-Id".getBytes());
				return true;
			}
			
			String session_id_plus = session_id;

			// XXX getHeaders() keys are lowercase.. this line always null?
			String tid = (String)request.getHeaders().get( "X-XMRPC-Tunnel-ID" );
			
			if ( tid != null ){
				
				session_id_plus += "/" + tid;
			}
			
			String	url = request.getURL();
		
			//System.out.println( "Header: " + request.getHeader() );
			
			if ( url.equals( "/transmission/rpc" )){
				
				LineNumberReader lnr;
				if ("gzip".equals(request.getHeaders().get("content-encoding"))) {
					GZIPInputStream gzipIS = new GZIPInputStream(request.getInputStream());
					lnr = new LineNumberReader( new InputStreamReader( gzipIS, "UTF-8" ));
				} else {
					lnr = new LineNumberReader( new InputStreamReader( request.getInputStream(), "UTF-8" ));
				}
					
				StringBuffer	request_json_str = new StringBuffer(2048);
				
				while( true ){
					
					String	line = lnr.readLine();
					
					if ( line == null ){
						
						break;
					}
					
					request_json_str.append( line );
				}
				
				if ( logit ){
				
					log( "-> " + request_json_str );
				}
				
				Map request_json = JSONUtils.decodeJSON( request_json_str.toString());
								
				Map response_json = processRequest( request, session_id_plus, request_json );

				String response_json_str = JSONUtils.encodeToJSON( response_json );
				
				if ( logit ){
				
					log( "<- " + response_json_str.length() );
					log( "<- " + response_json_str );
				}

				PrintWriter pw =new PrintWriter( new OutputStreamWriter( response.getOutputStream(), "UTF-8" ));
			
				pw.println( response_json_str );
				
				pw.flush();

				response.setContentType( "application/json; charset=UTF-8" );
				
				response.setGZIP( true );
				
				return( true );
			
			}else if ( 	url.startsWith( "/transmission/rpc?json=" ) ||
						url.startsWith( "/vuze/rpc?json=" )){
											
				StringBuffer	request_json_str = new StringBuffer(2048);
				
				request_json_str.append( UrlUtils.decode( url.substring( url.indexOf('?') + 6 )));
							
				if ( logit ){
				
					log( "-> " + request_json_str );
				}
				
				Map response_json;
				Object object = JSONValue.parse(request_json_str.toString());
				if (object instanceof Map) {
					Map request_json = JSONUtils.decodeJSON( request_json_str.toString());
					response_json = processRequest( request, session_id_plus, request_json );
				} else {
					response_json = new HashMap();
					response_json.put( "result", "error: Bad or missing JSON string");
					response_json.put("request", request_json_str);
				}

				
				String response_json_str = JSONUtils.encodeToJSON( response_json );
				
				if ( logit ){
				
					log( "<- " + response_json_str );
				}
				
				PrintWriter pw =new PrintWriter( new OutputStreamWriter( response.getOutputStream(), "UTF-8" ));
			
				pw.println( response_json_str );
				
				pw.flush();
				
				response.setContentType( "application/json; charset=UTF-8" );
				
				response.setGZIP( true );
				
				return( true );
				
				/* example code to relay a stream
			}else if ( url.startsWith( "/vuze/test.mkv" )){
				
				Map headers = request.getHeaders();
								
				OutputStream os = response.getRawOutputStream();
				
				Socket sock = new Socket( "127.0.0.1", 46409 );
				
				OutputStream sos = sock.getOutputStream();
				
				String req = "GET /Content/test.mkv HTTP/1.1\r\n";
				
				String range = (String)headers.get( "range" );
				
				if ( range != null ){
					
					req += "Range: " + range + "\r\n";
				}
				
				req += "\r\n";
				
				sos.write( req.getBytes( "ISO-8859-1"));
				
				sos.flush();
				
				InputStream is = sock.getInputStream();
				
				byte[]	buffer = new byte[256*1024];
				
				while( true ){
				
					int	len = is.read( buffer );
					
					if ( len <= 0 ){
						
						break;
					}
					
					os.write( buffer, 0, len );
				}
				
				return( true );
				*/
				
			}else if ( url.startsWith( "/vuze/resource?json=" )){

				Map request_json = JSONUtils.decodeJSON( UrlUtils.decode( url.substring( url.indexOf( '?' ) + 6 )));
				
				return( processResourceRequest( request, response, request_json ));
				
			}else if ( url.startsWith( "/transmission/upload" )){
					
				if ( logit ){
					
					log( "upload request" );
				}
				
				checkUpdatePermissions();
				
				boolean	add_stopped = url.endsWith( "paused=true" );
				
				String	content_type = (String)request.getHeaders().get( "content-type" );
				
				if ( content_type == null ){
					
					throw( new IOException( "Content-Type missing" ));
				}
				
				int bp = content_type.toLowerCase().indexOf( "boundary=" );
				
				if ( bp == -1 ){
					
					throw( new IOException( "boundary missing" ));
				}
				
				String	boundary = content_type.substring(bp+9).trim();
				
				int	ep = boundary.indexOf( ';' );
				
				if ( ep != -1 ){
					
					boundary = boundary.substring(0,ep).trim();
				}
				
				MultiPartDecoder.FormField[] fields = new MultiPartDecoder().decode(boundary, request.getInputStream());
				
				try{
					
					int	num_found = 0;
					
					for ( MultiPartDecoder.FormField field: fields ){
						
						String field_name = field.getName();
						
						if ( field_name.equalsIgnoreCase( "torrent_file" ) || field_name.equalsIgnoreCase( "torrent_files[]" )){
					
							num_found++;
							
							String torrent_file_name = field.getAttribute( "filename" );
							
							if ( torrent_file_name == null ){
								
								throw( new IOException( "upload filename missing" ));
							}
							
							InputStream tis = field.getInputStream();
							
							Torrent torrent;
							
							try{
								torrent = plugin_interface.getTorrentManager().createFromBEncodedInputStream( tis );
							
								torrent.setDefaultEncoding();
								
							}catch( Throwable e ){
								
								throw( new IOException( "Failed to deserialise torrent file: " + Debug.getNestedExceptionMessage(e)));
							}
							
							try{
								Download download = addTorrent( torrent, null, add_stopped, null );
								
								response.setContentType( "text/xml; charset=UTF-8" );
								
								response.getOutputStream().write( "<h1>200: OK</h1>".getBytes());
								
								return( true );
								
							}catch( Throwable e ){
								
								throw( new IOException( "Failed to add torrent: " + Debug.getNestedExceptionMessage(e)));
	
							}
						}
					}
					
					if ( num_found == 0 ){
						
						log( "No torrents found in upload request" );
					}
					
					return( true );
					
				}finally{
					
					for ( MultiPartDecoder.FormField field: fields ){
						
						field.destroy();
					}
				}
			}else if ( url.startsWith( "/transmission/web")){
					
				response.setReplyStatus( 301 );
					
				response.setHeader( "Location", "/" );
					
				return( true );
				
			}else if ( url.startsWith( SEARCH_PREFIX )){
				
				return( search_handler.generate( request, response ));
			
			}else{
			
				return( false );
			}
		}catch( PermissionDeniedException e ){
			
			response.setReplyStatus( 401 );

			return( true );
			
		}catch( IOException e ){
			
			if ( logit ){
			
				log( "Processing failed", e );
				e.printStackTrace();
			}
			
			throw( e );
			
		}catch( Throwable e ){
			
			if ( logit ){
				
				log( "Processing failed", e );
				e.printStackTrace();
			}
			
			throw( new IOException( "Processing failed: " + Debug.getNestedExceptionMessage( e )));
		}
	}
	
	private String
	getCookie(
		String		cookies,
		String		cookie_id)
	{
		if ( cookies == null ){
			
			return null;
		}
		
		String[] cookie_list = cookies.split( ";" );
		
		for ( String cookie: cookie_list ){
			
			String[] bits = cookie.split( "=" );
			
			if ( bits.length == 2 ){
				
				if ( bits[0].trim().equals( cookie_id )){
					
					return bits[1].trim();
				}
			}
		}
		
		return null;
	}

	private boolean 
	isSessionValid(
			TrackerWebPageRequest request) 
 {
		if (!request.getURL().startsWith("/transmission/")) {
			return true;
		}

		Map headers = request.getHeaders();
		
			// tunnel requests are already strongly authenticated and session based
		String tunnel = (String)headers.get( "x-vuze-is-tunnel" );
		
		if ( tunnel != null && tunnel.equalsIgnoreCase( "true" )){
			return true;
		}
		String session_id = getSessionID(request);
		String header_session_id = (String) headers.get(
				"X-Transmission-Session-Id");

		if (header_session_id == null) {
			header_session_id = (String) headers.get(
					"x-transmission-session-id");
		}
		if (header_session_id == null) {
			header_session_id = getCookie(
					(String) headers.get("cookie"),
					"X-Transmission-Session-Id");
		}

		//System.out.println("header_session_id=" + header_session_id);

		if (header_session_id == null) {
			return false;
		}

		return (header_session_id.equals(session_id));
	}

	private String 
	getSessionID(
			TrackerWebPageRequest request) 
	{
		String clientAddress = request.getClientAddress();
		
		synchronized (ip_to_session_id) {
			String session_id = ip_to_session_id.get(clientAddress);
			if (session_id == null) {
				session_id = Double.toHexString(Math.random());
				ip_to_session_id.put(clientAddress, session_id);
			}
			
			return session_id;
		}
	}

	private static Object add_torrent_lock = new Object();
	
	protected Download
	addTorrent(
		final Torrent		torrent,
		File download_dir,
		boolean		add_stopped,
		final DownloadWillBeAddedListener listener)
	
		throws DownloadException
	{
		synchronized( add_torrent_lock ){

			
			final com.biglybt.pif.download.DownloadManager dm = plugin_interface.getDownloadManager();
			
			
			Download download = dm.getDownload( torrent );
			
			if ( download == null ){

				if (listener != null) {
  				dm.addDownloadWillBeAddedListener(new DownloadWillBeAddedListener() {
  					@Override
					  public void initialised(Download dlAdding) {
  						boolean b = Arrays.equals(dlAdding.getTorrent().getHash(), torrent.getHash());
  						if (b) {
  							dm.removeDownloadWillBeAddedListener(this);
  							listener.initialised(dlAdding);
  						}
  					}
  				});
				}

				if ( add_stopped ){
					
					download = dm.addDownloadStopped( torrent, null, download_dir );
					
				}else{
					
					download = dm.addDownload( torrent, null, download_dir );
				}
			
			
					// particularly necessary for the android client as untidy closedown is common
			
				CoreFactory.getSingleton().saveState();
			}
			
			return( download );
		}
	}
	
	protected void
	checkUpdatePermissions()
		
		throws IOException
	{
		if ( view_mode ){
			
			log( "permission denied" );
			
			throw( new PermissionDeniedException());
		}
	}
	
	protected Map
	processRequest(
		TrackerWebPageRequest		wp_request,
		String						session_id,
		Map							request )
	
		throws IOException
	{
		Map	response = new HashMap();

		if (request == null) {
			response.put( "result", "error: Bad or missing JSON string");
			return response;
		}

		String method = (String)request.get( "method" );
		
		if ( method == null ){
			
			throw( new IOException( "'method' missing" ));
		}

		Map	args = (Map)request.get( "arguments" );
		
		if ( args == null ){
			
			args = new HashMap();
		}

		try{
			Map	result = processRequest( wp_request, session_id, method, args );

			if ( result == null ){
				
				result = new HashMap();
			}
				
			response.put( "arguments", result );
			
			response.put( "result", "success" );
			
		}catch( PermissionDeniedException e ){
			
			throw( e );
			
		}catch( TextualException e ){

			response.put( "result", e.getMessage());
		
		}catch( Throwable e ){
			log("processRequest", e);
			response.put( "result", "error: " + Debug.getNestedExceptionMessage( e ));
		}
		
		Object	tag = request.get( "tag" );

		if ( tag != null ){
			
			response.put( "tag", tag );
		}
		
		return( response );
	}

	static Number getNumber(
			Object val)
	{
		return getNumber(val, 0);
	}

	static Number getNumber(
			Object val,
			Number defaultNumber)
	{
		if (val instanceof Number) {
			return (Number) val;
		}
		if (val instanceof String) {
			NumberFormat format = NumberFormat.getInstance();
			try {
				Number number = format.parse((String) val);
				return number;
			} catch (ParseException e) {
				return defaultNumber;
			}
		}
		return defaultNumber;
	}
	
	protected Map
	processRequest(
		TrackerWebPageRequest		request,
		String						session_id,
		String						method,
		Map							args )
	
		throws Exception
	{
		boolean	save_core_state = false;
		
		try{
			Map	result = new HashMap();
					
				// https://trac.transmissionbt.com/browser/trunk/extras/rpc-spec.txt
			
				// to get 271 working with this backend change remote.js RPC _Root to be
				// _Root                   : './transmission/rpc',
	
			if ( method.equals( "session-set" )){
	
				try{
					method_Session_Set(args, result);
					
				}finally{
					
						// assume something important was changed and persist it now 
					
					COConfigurationManager.save();
				}
	
			} else if ( method.equals( "session-get" ) ){
	
				method_Session_Get(args, result);
				
			}else if ( method.equals( "session-stats" )){
	
				method_Session_Stats(args, result);
				
			}else if ( method.equals( "torrent-add" )){
				String agent = MapUtils.getMapString(request.getHeaders(), "User-Agent", "");
				boolean xmlEscape = agent.startsWith("Mozilla/");

				method_Torrent_Add(args, result, xmlEscape);
				
				// this is handled within the torrent-add method: save_core_state = true;
				
			}else if ( method.equals( "torrent-start-all" )){
	
				checkUpdatePermissions();
				
				plugin_interface.getDownloadManager().startAllDownloads();
			
				save_core_state = true;
				
			}else if ( method.equals( "torrent-stop-all" )){
	
				checkUpdatePermissions();
				
				plugin_interface.getDownloadManager().stopAllDownloads();
				
				save_core_state = true;
				
			}else if ( method.equals( "torrent-start" )){
	
				method_Torrent_Start(args, result);
	
				save_core_state = true;
				
			}else if ( method.equals( "torrent-start-now" )){
				// RPC v14
	
				method_Torrent_Start_Now(args, result);
	
				save_core_state = true;
				
			}else if ( method.equals( "torrent-stop" )){
	
				method_Torrent_Stop(args, result);
				
				save_core_state = true;
				
			}else if ( method.equals( "torrent-verify" )){
	
				method_Torrent_Verify(args, result);
	
			}else if ( method.equals( "torrent-remove" )){
				// RPC v3
	
				method_Torrent_Remove(args, result);
	
				save_core_state = true;
				
			}else if ( method.equals( "torrent-set" )){
				
				method_Torrent_Set( session_id, args, result);
				
			}else if ( method.equals( "torrent-get" )){
	
				method_Torrent_Get(request, session_id, args, result);
	
			}else if ( method.equals( "torrent-reannounce" )){
				// RPC v5
	
				method_Torrent_Reannounce(args, result);
				
			}else if ( method.equals( "torrent-set-location" )){
				// RPC v6
				
				method_Torrent_Set_Location(args, result);
	
			}else if ( method.equals( "blocklist-update" )){
				// RPC v5
				
				method_Blocklist_Update(args, result);
	
			}else if ( method.equals( "session-close" )){
				// RPC v12
				//TODO: This method tells the transmission session to shut down.
	
			}else if ( method.equals( "queue-move-top" )){
				// RPC v14
				method_Queue_Move_Top(args, result);
	
			}else if ( method.equals( "queue-move-up" )){
				// RPC v14
				method_Queue_Move_Up(args, result);
	
			}else if ( method.equals( "queue-move-down" )){
				// RPC v14
				method_Queue_Move_Down(args, result);
	
			}else if ( method.equals( "queue-move-bottom" )){
				// RPC v14
				method_Queue_Move_Bottom(args, result);
	
			}else if ( method.equals( "free-space" )){
				// RPC v15
				method_Free_Space(args, result);
	
			}else if ( method.equals( "torrent-rename-path" )){
				// RPC v15
				method_Torrent_Rename_Path(args, result);
	
			}else if ( method.equals( "tags-get-list" )){
				// Vuze RPC v3
				method_Tags_Get_List(args, result);

			}else if ( method.equals( "tags-lookup-start" )){

				method_Tags_Lookup_Start(args, result);

			}else if ( method.equals( "tags-lookup-get-results" )){

				method_Tags_Lookup_Get_Results(args, result);

			}else if ( method.equals( "subscription-get" )){
				
				method_Subscription_Get(args, result);
				
			}else if ( method.equals( "subscription-add" )){
				
				method_Subscription_Add(args, result);
				
			}else if ( method.equals( "subscription-set" )){
				
				method_Subscription_Set(args, result);
				
			}else if ( method.equals( "subscription-remove" )){
				
				method_Subscription_Remove(args, result);
				
			}else if ( method.equals( "vuze-search-start" )){
				
				method_Vuze_Search_Start(args, result);
				
			}else if ( method.equals( "vuze-search-get-results" )){
				
				method_Vuze_Search_Get_Results(args, result);

			}else if ( method.equals( "vuze-config-set" )){
				
				method_Vuze_Config_Set(args, result);

			}else if ( method.equals( "vuze-config-get" )){
				
				method_Vuze_Config_Get(args, result);

			}else if ( method.equals( "vuze-plugin-get-list" )){
				
				method_Vuze_Plugin_Get_List(args, result);

			}else if ( method.equals( "vuze-lifecycle" )){
	
				processVuzeLifecycle( args, result );
				
			}else if ( method.equals( "vuze-pairing" )){
						
				processVuzePairing( args, result );
				
			}else if ( method.equals( "vuze-torrent-get" )){
	
				processVuzeTorrentGet( request, args, result );
		
			}else if ( method.equals( "vuze-file-add" )){
				
				processVuzeFileAdd( args, result );
	
			}else{
			
				Utilities.JSONServer server = (Utilities.JSONServer)json_server_methods.get( method );
				
				if ( server != null ){
					
					return( server.call( method, args ));
				}
				
				if ( trace_param.getValue() ){
					log( "unhandled method: " + method + " - " + args );
				}
			}
	
			return( result );
			
		}finally{
			
			if ( save_core_state ){
				
					// particularly necessary for the android client as untidy closedown is common
				
				CoreFactory.getSingleton().saveState();
			}
		}
	}
	
	private void method_Vuze_Plugin_Get_List(Map args, Map result) {
		String sep = System.getProperty("file.separator");

		File fUserPluginDir = FileUtil.getUserFile("plugins");
		String sUserPluginDir;
		try {
			sUserPluginDir = fUserPluginDir.getCanonicalPath();
		} catch (Throwable e) {
			sUserPluginDir = fUserPluginDir.toString();
		}
		if (!sUserPluginDir.endsWith(sep)) {
			sUserPluginDir += sep;
		}

		File fAppPluginDir = FileUtil.getApplicationFile("plugins");
		String sAppPluginDir;
		try {
			sAppPluginDir = fAppPluginDir.getCanonicalPath();
		} catch (Throwable e) {
			sAppPluginDir = fAppPluginDir.toString();
		}
		if (!sAppPluginDir.endsWith(sep)) {
			sAppPluginDir += sep;
		}

		List pluginIFs = Arrays.asList(
				CoreFactory.getSingleton().getPluginManager().getPlugins());
		for (Iterator iter = pluginIFs.iterator(); iter.hasNext();) {
			PluginInterface pi = (PluginInterface) iter.next();

			Map mapPlugin = new HashMap();
			result.put(pi.getPluginID(), mapPlugin);
			mapPlugin.put("name", pi.getPluginName());

			String sDirName = pi.getPluginDirectoryName();
			String sKey;

			if (sDirName.length() > sUserPluginDir.length()
					&& sDirName.substring(0, sUserPluginDir.length()).equals(
							sUserPluginDir)) {
				sKey = "perUser";

			} else if (sDirName.length() > sAppPluginDir.length()
					&& sDirName.substring(0, sAppPluginDir.length()).equals(
							sAppPluginDir)) {
				sKey = "shared";
			} else {
				sKey = "builtIn";
			}

			mapPlugin.put("type", sKey);
			mapPlugin.put("version", pi.getPluginVersion());
			PluginState pluginState = pi.getPluginState();
			mapPlugin.put("isBuiltIn", pluginState.isBuiltIn());
			mapPlugin.put("isDisabled", pluginState.isDisabled());
			mapPlugin.put("isInitialisationComplete",
					pluginState.isInitialisationComplete());
			mapPlugin.put("isLoadedAtStartup", pluginState.isLoadedAtStartup());
			mapPlugin.put("isMandatory", pluginState.isMandatory());
			mapPlugin.put("isOperational", pluginState.isOperational());
			mapPlugin.put("isShared", pluginState.isShared());
			mapPlugin.put("isUnloadable", pluginState.isUnloadable());
			mapPlugin.put("isUnloaded", pluginState.isUnloaded());
		}
	}

	private void method_Vuze_Config_Get(Map args, Map result) {
		List listKeys = MapUtils.getMapList(args, "keys", Collections.EMPTY_LIST);
		for (Object key : listKeys) {
			String keyString = key.toString();
			if (ignoreConfigKey(keyString)) {
				continue;
			}
			Object val = COConfigurationManager.getParameter(keyString);
			if (val instanceof byte[]) {
				// Place parsed string in key's value, B64 of bytes in key + ".B64"
				String valString;
				byte[] bytes = (byte[]) val;
				try {
					valString = new String(bytes, Constants.DEFAULT_ENCODING);
				} catch (Throwable e) {
					valString = new String(bytes);
				}

				result.put(key, valString);
				try {
					result.put(key + ".B64", new String(Base64.encode(bytes), "utf8"));
				} catch (UnsupportedEncodingException e) {
				}
			} else {
				result.put(key, val);
			}
		}

	}

	private void method_Vuze_Config_Set(Map args, Map result) {
		Map mapDirect = MapUtils.getMapMap(args, "direct", Collections.EMPTY_MAP);
		for (Object key : mapDirect.keySet()) {
			String keyString = key.toString();
			if (ignoreConfigKey(keyString)) {
				result.put(keyString, "key ignored");
				continue;
			}
			Object val = mapDirect.get(key);
			boolean changed;
			if (val instanceof String) {
				changed = COConfigurationManager.setParameter(keyString, (String) val);
			} else if (val instanceof Boolean) {
				changed = COConfigurationManager.setParameter(keyString, (Boolean) val);
			} else if (val instanceof Float) {
				changed = COConfigurationManager.setParameter(keyString, (Float) val);
			} else if (val instanceof Double) {
				changed = COConfigurationManager.setParameter(keyString,
						((Number) val).floatValue());
			} else if (val instanceof Number) {
				changed = COConfigurationManager.setParameter(keyString,
						((Number) val).longValue());
			} else if (val instanceof Map) {
				changed = COConfigurationManager.setParameter(keyString, (Map) val);
			} else {
				result.put(keyString, "error");
				continue;
			}
			result.put(keyString, changed);
		}

		Map mapByteArray = MapUtils.getMapMap(args, "byteArray.B64",
				Collections.EMPTY_MAP);
		for (Object key : mapByteArray.keySet()) {
			String keyString = key.toString();
			if (ignoreConfigKey(keyString)) {
				result.put(keyString, "key ignored");
				continue;
			}
			Object val = mapByteArray.get(key);
			if (val instanceof String) {
				byte[] decode = Base64.decode((String) val);
				boolean changed = COConfigurationManager.setParameter(keyString,
						decode);
				result.put(keyString, changed);
			} else {
				result.put(keyString, "error");
			}
		}

		COConfigurationManager.save();
	}
	
	private boolean
	ignoreConfigKey(
		String		key )
	{
		String lc_key = key.toLowerCase(Locale.US);

		if (key.startsWith(CryptoManager.CRYPTO_CONFIG_PREFIX)
				|| lc_key.equals("id") || lc_key.equals("azbuddy.dchat.optsmap")
				|| lc_key.endsWith(".privx") || lc_key.endsWith(".user")
				|| lc_key.contains("password") || lc_key.contains("username")
				|| lc_key.contains("session key")) {

			return (true);
		}

		Object value = COConfigurationManager.getParameter(key);

		if (value instanceof byte[]) {

			try {
				value = new String((byte[]) value, "UTF-8");

			} catch (Throwable e) {

			}
		}

		if (value instanceof String) {

			if (((String) value).toLowerCase(Locale.US).endsWith(".b32.i2p")) {

				return (true);
			}
		}

		return (false);
	}


	private void method_Tags_Lookup_Start(Map args, Map result) {
		Object ids = args.get("ids");
		
		TagSearchInstance tagSearchInstance = new TagSearchInstance();

		try {
			List<String> listDefaultNetworks = new ArrayList<String>();
			for (int i = 0; i < AENetworkClassifier.AT_NETWORKS.length; i++) {

				String nn = AENetworkClassifier.AT_NETWORKS[i];

				String config_name = "Network Selection Default." + nn;
				boolean enabled = COConfigurationManager.getBooleanParameter(
						config_name, false);
				if (enabled) {
					listDefaultNetworks.add(nn);
				}
			}

			com.biglybt.pif.download.DownloadManager dlm = plugin_interface.getDownloadManager();
			String[] networks;
			if (ids instanceof List) {
				List idList = (List) ids;
				for (Object id : idList) {
					if (id instanceof String) {
						String hash = (String) id;
						byte[] hashBytes = ByteFormatter.decodeString(hash);
						Download download = dlm.getDownload(hashBytes);
						
						DownloadManager dm = PluginCoreUtils.unwrap(download);
						if (dm != null) {
							networks = dm.getDownloadState().getNetworks();
						} else {
							networks = listDefaultNetworks.toArray(new String[0]);
						}
						
						tagSearchInstance.addSearch(hash, hashBytes, networks);
						synchronized (active_tagsearches) {
							active_tagsearches.put(tagSearchInstance.getID(), tagSearchInstance);
						}
					}
				}

			}
		} catch (Throwable t) {

		}

		result.put("id", tagSearchInstance.getID());
	}

	private void method_Tags_Lookup_Get_Results(Map args, Map result)
			throws IOException {
		String id = (String) args.get("id");

		if (id == null) {
			throw (new IOException("ID missing"));
		}

		synchronized (active_tagsearches) {
			TagSearchInstance search_instance = active_tagsearches.get(id);

			if (search_instance != null) {
				if (search_instance.getResults(result)) {
					active_tagsearches.remove(id);
				}
			} else {
				throw (new IOException("ID not found - already complete?"));
			}
		}
	}


	private void method_Vuze_Search_Get_Results(Map args, Map result)
			throws IOException {
		String sid = (String) args.get("sid");

		if (sid == null) {
			throw (new IOException("SID missing"));
		}

		synchronized (active_searches) {
			SearchInstance search_instance = active_searches.get(sid);

			if (search_instance != null) {
				if (search_instance.getResults(result)) {
					active_searches.remove(sid);
				}
			} else {
				throw (new IOException("SID not found - already complete?"));
			}
		}
	}

	private void method_Vuze_Search_Start(Map args, Map result)
			throws IOException {
		String expression = (String) args.get("expression");
		
		if (expression == null) {
			throw (new IOException("Search expression missing"));
		}

		MetaSearchManager ms_manager = MetaSearchManagerFactory.getSingleton();
		MetaSearch ms = ms_manager.getMetaSearch();
		List<SearchParameter> sps = new ArrayList<SearchParameter>();

		sps.add(new SearchParameter("s", expression));

		SearchParameter[] parameters = sps.toArray(new SearchParameter[sps.size()]);

		Map<String, String> context = new HashMap();
		context.put(Engine.SC_SOURCE, "xmwebui");
		context.put(Engine.SC_REMOVE_DUP_HASH, "true");

		Engine[] engines = ms_manager.getMetaSearch().getEngines(true, true);

		if (engines.length == 0) {
			throw (new IOException("No search templates available"));
		}

		SearchInstance search_instance = new SearchInstance(this, engines);

		engines = ms.search(engines, search_instance, parameters, null, context,
				100);

		if (engines.length == 0) {
			throw (new IOException("No search templates available"));
		}

		synchronized (active_searches) {
			active_searches.put(search_instance.getSID(), search_instance);
		}

		search_instance.setEngines(engines);

		result.put("sid", search_instance.getSID());

		List<Map> l_engines = new ArrayList<Map>();
		result.put("engines", l_engines);

		for (Engine engine : engines) {
			JSONObject map = new JSONObject();

			l_engines.add(map);

			map.put("name", engine.getName());
			map.put("id", engine.getUID());
			map.put("favicon", engine.getIcon());
			map.put("dl_link_css", engine.getDownloadLinkCSS());
			map.put("selected", Engine.SEL_STATE_STRINGS[engine.getSelectionState()]);
			map.put("source", Engine.ENGINE_SOURCE_STRS[engine.getSource()]);
			int type = engine.getType();
			map.put("type", type < Engine.ENGINE_TYPE_STRS.length ? Engine.ENGINE_TYPE_STRS[type] : type);
		}
	}

	private void method_Subscription_Add(Map args, Map result) throws MalformedURLException, SubscriptionException {
		String url = MapUtils.getMapString(args, "rss-url", null);
		String name = MapUtils.getMapString(args, "name",
				"Subscription " + DateFormat.getInstance().toString());
		boolean anonymous = MapUtils.getMapBoolean(args, "anonymous", false);
		

		if (url != null) {
			Subscription subRSS = SubscriptionManagerFactory.getSingleton().createRSS(
					name, new URL(url), SubscriptionHistory.DEFAULT_CHECK_INTERVAL_MINS, anonymous,
					null);
			
			result.put("subscription", subRSS.getJSON());
		}
	}

	
	/*
	 * {
	 *   <Subscription List ID> :
	 *   {
	 *     <field> : value,
	 *     "results" : {
	 *       <field> : value,
	 *       etc
	 *     },
	 *     etc
	 *   },
	 *   etc
	 * }
	 */
	private void method_Subscription_Set(Map args, Map result)
			throws SubscriptionException, IOException {
		Object oIDs = args.get("ids");

		if (oIDs == null) {
			throw new IOException("ids missing");
		}

		if (!(oIDs instanceof Map)) {
			throw new IOException("ids not map");
		}

		Map mapSubscriptionIDs = (Map) oIDs;
		SubscriptionManager subMan = SubscriptionManagerFactory.getSingleton();
		for (Object oSubscriptionID : mapSubscriptionIDs.keySet()) {
			Subscription subs = subMan.getSubscriptionByID((String) oSubscriptionID);
			if (subs == null) {
				result.put(oSubscriptionID, "Error: Not Found");
				continue;
			}
			Object oVal = mapSubscriptionIDs.get(oSubscriptionID);
			if (!(oVal instanceof Map)) {
				continue;
			}
			Map mapSubscriptionFields = (Map) oVal;
			// could change name, subscribed state, etc

			int numChanged = 0;

			for (Object oSubscriptionFieldName : mapSubscriptionFields.keySet()) {
				String subscriptionFieldName = (String) oSubscriptionFieldName;
				Object oSubscriptionFieldValue = mapSubscriptionFields.get(
						subscriptionFieldName);
				if (subscriptionFieldName.equals(FIELD_SUBSCRIPTION_NAME)) {
					subs.setName((String) oSubscriptionFieldValue);
					numChanged++;

				} else if (subscriptionFieldName.equals(FIELD_SUBSCRIPTION_AUTO_DOWNLOAD)
						&& (oSubscriptionFieldValue instanceof Boolean)) {
					subs.getHistory().setAutoDownload((Boolean) oSubscriptionFieldValue);
					numChanged++;
				
				} else if (subscriptionFieldName.equals(FIELD_SUBSCRIPTION_SUBSCRIBED)
						&& (oSubscriptionFieldValue instanceof Boolean)) {
					subs.setSubscribed((Boolean) oSubscriptionFieldValue);
					numChanged++;
				
				} else if (subscriptionFieldName.equals(FIELD_SUBSCRIPTION_RESULTS)
						&& (oSubscriptionFieldValue instanceof Map)) {
					
					Map map = new HashMap();

					Map mapResults = (Map) oSubscriptionFieldValue;
					SubscriptionResult[] results = subs.getResults(false);
					for (Object oResultKey : mapResults.keySet()) {
						String subs_id = (String) oResultKey;
						Map mapResultEntries = (Map) mapResults.get(oResultKey);

						for (SubscriptionResult entry : results) {
							if (entry.getID().equals(subs_id)) {
								Boolean isRead = (Boolean) mapResultEntries.get(
										FIELD_SUBSCRIPTION_RESULT_ISREAD);
								if (isRead != null) {
									numChanged++;
									entry.setRead(isRead);
								}
								break;
							}
						}
					}
				}
				
				if (numChanged > 0) {
					Map<String, Object> map = buildSubscriptionMap(subs, null, null, true);
					result.put(oSubscriptionID, map);
				}
			}

		}
	}

	private void method_Subscription_Remove(Map args, Map result) throws IOException {
		Object oID = args.get("ids");

		if (oID == null) {
			throw new IOException("ID missing");
		}
		
		String[] ids = new String[0];
		if (oID instanceof String) {
			ids = new String[] { (String) oID };
		} else if (oID instanceof List) {
			ids = (String[]) ((List) oID).toArray(new String[0]);
		} else if (oID instanceof Object[]) {
			Object[] oIDS = (Object[]) oID; 
			ids = new String[oIDS.length];
			for (int i = 0; i < oIDS.length; i++) {
				ids[i] = oIDS[i].toString();
			}
		}
		
		SubscriptionManager subMan = SubscriptionManagerFactory.getSingleton();
		for (String id : ids) {
			Subscription subs = subMan.getSubscriptionByID(id);
			if (subs == null) {
				result.put(id, "Error: Not Found");
			} else {
				subs.remove();
				result.put(id, "Removed");
			}
		}
	}

	
	
	/*
	 * For non-torrent specific:
	 * 
	 * Subscriptions : 
	 * {
	 *   SubscriptionID : 
	 *   {
	 *   	Field:Value,
	 *   },
	 *   SubscriptionID : 
	 *   {
	 *   	Field:Value,
	 *   },
	 * }
	 * 
	 * For torrent specific:
	 * Subscriptions :
	 * {
	 *   SubscriptionID: {
	 *     torrentId: #,
	 * 	   Field:Value,
	 *   },
	 *   etc
	 * }
	 */
	private void method_Subscription_Get(Map args, Map result)
			throws IOException {

		boolean subscribedOnly = MapUtils.getMapBoolean(args, "subscribed-only",
				true);

		Map<Object, Map<String, Object>> mapSubcriptions = new HashMap<Object, Map<String, Object>>();

		SubscriptionManager subMan = SubscriptionManagerFactory.getSingleton();

		List fields = (List) args.get("fields");
		boolean all = fields == null || fields.size() == 0;
		if (!all) {
			// sort so we can't use Collections.binarySearch
			Collections.sort(fields);
		}

		Object oTorrentHashes = args.get("torrent-ids");
		if (oTorrentHashes != null) {
			List<DownloadStub> downloads = getDownloads(oTorrentHashes, false);
			for (DownloadStub stub : downloads) {
				Subscription[] subs = subMan.getKnownSubscriptions(
						stub.getTorrentHash());
				if (subs != null) {
					for (Subscription sub : subs) {
						Map<String, Object> map = buildSubscriptionMap(sub, args, fields, all);
						map.put("torrentID", getID(stub, false));

						mapSubcriptions.put(sub.getID(), map);
					}
				}
			}
		} else {
			Subscription[] subscriptions;

			Object oID = args.get("ids");

			String[] ids = new String[0];
			if (oID instanceof String) {
				ids = new String[] {
					(String) oID
				};
			} else if (oID instanceof List) {
				ids = (String[]) ((List) oID).toArray(new String[0]);
			} else if (oID instanceof Object[]) {
				Object[] oIDS = (Object[]) oID;
				ids = new String[oIDS.length];
				for (int i = 0; i < oIDS.length; i++) {
					ids[i] = oIDS[i].toString();
				}
			}

			if (ids.length == 0) {
				subscriptions = subMan.getSubscriptions(subscribedOnly);
			} else {
				List<Subscription> list = new ArrayList<Subscription>();
				for (String id : ids) {
					Subscription subscriptionByID = subMan.getSubscriptionByID(id);
					if (subscriptionByID == null) {
						mapSubcriptions.put(id, Collections.EMPTY_MAP);
					} else {
						list.add(subscriptionByID);
					}
				}
				subscriptions = list.toArray(new Subscription[0]);
			}

			for (Subscription sub : subscriptions) {
				Map<String, Object> map = buildSubscriptionMap(sub, args, fields, all);

				mapSubcriptions.put(sub.getID(), map);
			}
		}

		result.put("subscriptions", mapSubcriptions);
	}



	private Map<String, Object> buildSubscriptionMap(Subscription sub, Map args,
			List fields, boolean all) {
		Map<String, Object> map = new HashMap<String, Object>();
		
		if (all || Collections.binarySearch(fields,
				"json") >= 0) {
			try {
				Map mapJSON = JSONUtils.decodeJSON(sub.getJSON());
				mapJSON.remove("engines");
				map.put("json", mapJSON);
			} catch (SubscriptionException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		if (all
				|| Collections.binarySearch(fields, FIELD_SUBSCRIPTION_NAME) >= 0) {
			map.put(FIELD_SUBSCRIPTION_NAME, sub.getName());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_ADDEDON) >= 0) {
			map.put(FIELD_SUBSCRIPTION_ADDEDON, sub.getAddTime());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_ASSOCIATION_COUNT) >= 0) {
			map.put(FIELD_SUBSCRIPTION_ASSOCIATION_COUNT,
					sub.getAssociationCount());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_POPULARITY) >= 0) {
			map.put(FIELD_SUBSCRIPTION_POPULARITY, sub.getCachedPopularity());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_CATEGORY) >= 0) {
			addNotNullToMap(map, FIELD_SUBSCRIPTION_CATEGORY, sub.getCategory());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_CREATOR) >= 0) {
			addNotNullToMap(map, FIELD_SUBSCRIPTION_CREATOR, sub.getCreatorRef());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_ENGINE) >= 0) {
			try {
				Engine engine = sub.getEngine();
				if (engine != null) {
					Map mapEngine = new HashMap();
					map.put(FIELD_SUBSCRIPTION_ENGINE, mapEngine);

					if (all || Collections.binarySearch(fields,
							FIELD_SUBSCRIPTION_ENGINE_NAME) >= 0) {
						mapEngine.put("name", engine.getName());
					}
					if (all || Collections.binarySearch(fields,
							FIELD_SUBSCRIPTION_ENGINE_NAMEX) >= 0) {
						mapEngine.put(FIELD_SUBSCRIPTION_ENGINE_NAMEX,
								engine.getNameEx());
					}
					if (all || Collections.binarySearch(fields,
							FIELD_SUBSCRIPTION_ENGINE_TYPE) >= 0) {
						map.put(FIELD_SUBSCRIPTION_ENGINE_TYPE, engine.getType());
						int type = engine.getType();
						mapEngine.put("type", type < Engine.ENGINE_TYPE_STRS.length
								? Engine.ENGINE_TYPE_STRS[type] : type);
					}

					if (all || Collections.binarySearch(fields,
							FIELD_SUBSCRIPTION_ENGINE_NAMEX) >= 0) {
						mapEngine.put(FIELD_SUBSCRIPTION_ENGINE_NAMEX,
								engine.getNameEx());
					}
					//engine.getAutoDownloadSupported() same as sub.getAutoDownloadSupported

					if (all || Collections.binarySearch(fields,
							FIELD_SUBSCRIPTION_ENGINE_SOURCE) >= 0) {
						mapEngine.put("source",
								Engine.ENGINE_SOURCE_STRS[engine.getSource()]);
					}
					if (all || Collections.binarySearch(fields,
							FIELD_SUBSCRIPTION_ENGINE_LASTUPDATED) >= 0) {
						mapEngine.put(FIELD_SUBSCRIPTION_ENGINE_LASTUPDATED,
								engine.getLastUpdated());
					}

					mapEngine.put("id", engine.getUID());
					addNotNullToMap(mapEngine, "favicon", engine.getIcon());
					mapEngine.put("dl_link_css", engine.getDownloadLinkCSS());
					mapEngine.put("selected",
							Engine.SEL_STATE_STRINGS[engine.getSelectionState()]);
					mapEngine.put("class", engine.getClass().getSimpleName());

					if (engine instanceof WebEngine) {
						WebEngine web_engine = (WebEngine) engine;
						if (all || Collections.binarySearch(fields,
								FIELD_SUBSCRIPTION_ENGINE_URL) >= 0) {
							mapEngine.put(FIELD_SUBSCRIPTION_ENGINE_URL,
									web_engine.getSearchUrl(true));
						}
						if (all || Collections.binarySearch(fields,
								FIELD_SUBSCRIPTION_ENGINE_AUTHMETHOD) >= 0) {
							mapEngine.put(FIELD_SUBSCRIPTION_ENGINE_AUTHMETHOD,
									web_engine.getAuthMethod());
						}
					}
				}
			} catch (SubscriptionException e) {
			}
		}
		
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_HIGHEST_VERSION) >= 0) {
			map.put(FIELD_SUBSCRIPTION_HIGHEST_VERSION, sub.getHighestVersion());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_NAME_EX) >= 0) {
			map.put(FIELD_SUBSCRIPTION_NAME_EX, sub.getNameEx());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_QUERY_KEY) >= 0) {
			map.put(FIELD_SUBSCRIPTION_QUERY_KEY, sub.getQueryKey());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_REFERER) >= 0) {
			map.put(FIELD_SUBSCRIPTION_REFERER, sub.getReferer());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_TAG_UID) >= 0) {
			map.put(FIELD_SUBSCRIPTION_TAG_UID, sub.getTagID());
		}
		if (all
				|| Collections.binarySearch(fields, FIELD_SUBSCRIPTION_URI) >= 0) {
			map.put(FIELD_SUBSCRIPTION_URI, sub.getURI());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_ANONYMOUS) >= 0) {
			map.put(FIELD_SUBSCRIPTION_ANONYMOUS, sub.isAnonymous());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_AUTO_DL_SUPPORTED) >= 0) {
			map.put(FIELD_SUBSCRIPTION_AUTO_DL_SUPPORTED,
					sub.isAutoDownloadSupported());
		}
		if (all
				|| Collections.binarySearch(fields, FIELD_SUBSCRIPTION_MINE) >= 0) {
			map.put(FIELD_SUBSCRIPTION_MINE, sub.isMine());
		}
		if (all
				|| Collections.binarySearch(fields, FIELD_SUBSCRIPTION_PUBLIC) >= 0) {
			map.put(FIELD_SUBSCRIPTION_PUBLIC, sub.isPublic());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_IS_SEARCH_TEMPLATE) >= 0) {
			map.put(FIELD_SUBSCRIPTION_IS_SEARCH_TEMPLATE, sub.isSearchTemplate());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_SUBSCRIBED) >= 0) {
			map.put(FIELD_SUBSCRIPTION_SUBSCRIBED, sub.isSubscribed());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_UPDATEABLE) >= 0) {
			map.put(FIELD_SUBSCRIPTION_UPDATEABLE, sub.isUpdateable());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_SHAREABLE) >= 0) {
			map.put(FIELD_SUBSCRIPTION_SHAREABLE, sub.isShareable());
		}
		if (all || Collections.binarySearch(fields,
				FIELD_SUBSCRIPTION_RESULTS_COUNT) >= 0) {
			map.put(FIELD_SUBSCRIPTION_RESULTS_COUNT, sub.getResults(false).length);
		}

		SubscriptionHistory history = sub.getHistory();
		if (history != null) {
			
			if (all || Collections.binarySearch(fields,
					"newResultsCount") >= 0) {
				map.put("newResultsCount", history.getNumUnread());
			}
			if (all || Collections.binarySearch(fields,
					"nextScanTime") >= 0) {
				map.put("nextScanTime", history.getNextScanTime());
			}
			if (all || Collections.binarySearch(fields,
					"checkFrequency") >= 0) {
				map.put("checkFrequency", history.getCheckFrequencyMins());
			}
			if (all || Collections.binarySearch(fields,
					"consecutiveFails") >= 0) {
				map.put("consecutiveFails", history.getConsecFails());
			}
			if (all || Collections.binarySearch(fields,
					FIELD_SUBSCRIPTION_AUTO_DOWNLOAD) >= 0) {
				map.put(FIELD_SUBSCRIPTION_AUTO_DOWNLOAD, history.isAutoDownload());
			}
			if (all || Collections.binarySearch(fields,
					"authFail") >= 0) {
				map.put("authFail", history.isAuthFail());
			}

			if (all || Collections.binarySearch(fields,
					"error") >= 0) {
				addNotNullToMap(map, "error", history.getLastError());
			}

			if (fields != null && Collections.binarySearch(fields,
					FIELD_SUBSCRIPTION_RESULTS) >= 0) {
				
				List<Map> listResults = new ArrayList();

				SubscriptionResult[] results = sub.getHistory().getResults(false);

				List fieldsResults = args == null ? null : (List) args.get("results-fields");
				boolean allResults = fieldsResults == null || fieldsResults.size() == 0;
				
				for (int i = 0; i < results.length; i++) {
					SubscriptionResult r = results[i];

					listResults.add(buildSubscriptionResultMap(r, fieldsResults, allResults));
				}

				map.put(FIELD_SUBSCRIPTION_RESULTS, listResults);
			}
		}
		return map;
	}

	private Map buildSubscriptionResultMap(SubscriptionResult r,
			List fieldsResults, boolean allResults) {

		Map jsonMap = r.toJSONMap();
		if (!allResults) {
			jsonMap.keySet().retainAll(fieldsResults);
		}

		return jsonMap;
	}

	private void addNotNullToMap(Map<String, Object> map,
			String id, Object o) {
		if (o == null) {
			return;
		}
		map.put(id, o);
	}

	private void method_Tags_Get_List(Map args, Map result) {
		List fields = (List) args.get("fields");
		boolean all = fields == null || fields.size() == 0;
		if (!all) {
			// sort so we can't use Collections.binarySearch
			Collections.sort(fields);
		}

		List<SortedMap<String, Object>> listTags = 
				new ArrayList<SortedMap<String,Object>>();

		TagManager tm = TagManagerFactory.getTagManager();

		List<TagType> tagTypes = tm.getTagTypes();
		
		for (TagType tagType : tagTypes) {
			List<Tag> tags = tagType.getTags();
			
			for (Tag tag : tags) {
				SortedMap<String, Object> map = new TreeMap<String, Object>();
				if (all || Collections.binarySearch(fields, FIELD_TAG_NAME) >= 0) {
					map.put(FIELD_TAG_NAME, tag.getTagName(true));
				}

				//map.put("taggableTypes", tag.getTaggableTypes()); // com.aelitis.azureus.core.tag.Taggable
				if (all || Collections.binarySearch(fields, FIELD_TAG_COUNT) >= 0) {
					map.put(FIELD_TAG_COUNT, tag.getTaggedCount());
				}
				if (all || Collections.binarySearch(fields, FIELD_TAG_TYPE) >= 0) {
					map.put(FIELD_TAG_TYPE, tag.getTagType().getTagType());
				}
				if (all || Collections.binarySearch(fields, FIELD_TAG_TYPENAME) >= 0) {
					map.put(FIELD_TAG_TYPENAME, tag.getTagType().getTagTypeName(true));
				}

				if (all
						|| Collections.binarySearch(fields, FIELD_TAG_CATEGORY_TYPE) >= 0) {
					if (tag instanceof Category) {
						map.put(FIELD_TAG_CATEGORY_TYPE, ((Category) tag).getType());
					}
				}
				if (all || Collections.binarySearch(fields, FIELD_TAG_UID) >= 0) {
					map.put(FIELD_TAG_UID, tag.getTagUID());
				}
				if (all || Collections.binarySearch(fields, FIELD_TAG_ID) >= 0) {
					map.put(FIELD_TAG_ID, tag.getTagID());
				}
				if (all || Collections.binarySearch(fields, FIELD_TAG_COLOR) >= 0) {
					int[] color = tag.getColor();
					if (color != null) {
						String hexColor = "#";
						for (int c : color) {
							if (c < 0x10) {
								hexColor += "0";
							}
							hexColor += Integer.toHexString(c);
						}
						map.put(FIELD_TAG_COLOR, hexColor);
					}
				}
				if (all
						|| Collections.binarySearch(fields, FIELD_TAG_CANBEPUBLIC) >= 0) {
					map.put(FIELD_TAG_CANBEPUBLIC, tag.canBePublic());
				}
				if (all || Collections.binarySearch(fields, FIELD_TAG_PUBLIC) >= 0) {
					map.put(FIELD_TAG_PUBLIC, tag.isPublic());
				}
				if (all || Collections.binarySearch(fields, FIELD_TAG_VISIBLE) >= 0) {
					map.put(FIELD_TAG_VISIBLE, tag.isVisible());
				}
				if (all || Collections.binarySearch(fields, FIELD_TAG_GROUP) >= 0) {
					map.put(FIELD_TAG_GROUP, tag.getGroup());
				}
				if (all || Collections.binarySearch(fields, FIELD_TAG_AUTO_ADD) >= 0
						|| Collections.binarySearch(fields, FIELD_TAG_AUTO_REMOVE) >= 0) {
					boolean[] auto = tag.isTagAuto();
					if (all
							|| Collections.binarySearch(fields, FIELD_TAG_AUTO_ADD) >= 0) {
						map.put(FIELD_TAG_AUTO_ADD, auto[0]);
					}
					if (all
							|| Collections.binarySearch(fields, FIELD_TAG_AUTO_REMOVE) >= 0) {
						map.put(FIELD_TAG_AUTO_REMOVE, auto[1]);
					}
				}
				
				listTags.add(map);
			}
		}

		String hc = Long.toHexString(longHashSimpleList(listTags));
		result.put("tags-hc", hc);
		
		String oldHC = MapUtils.getMapString(args, "tags-hc", null);
		if (!hc.equals(oldHC)) {
			result.put("tags", listTags);
		}
	}

	private void method_Free_Space(Map args, Map result) {
		// RPC v15
/*
   This method tests how much free space is available in a
   client-specified folder.

   Method name: "free-space"

   Request arguments:

   string      | value type & description
   ------------+----------------------------------------------------------
   "path"      | string  the directory to query

   Response arguments:

   string      | value type & description
   ------------+----------------------------------------------------------
   "path"      | string  same as the Request argument
   "size-bytes"| number  the size, in bytes, of the free space in that directory
 */
		Object oPath = args.get("path");
		if (!(oPath instanceof String)) {
			return;
		}
		
		File file = new File((String) oPath);
		while (file != null && !file.exists()) {
			file = file.getParentFile();
		}
		if (file == null) {
			result.put("path", oPath);
			result.put("size-bytes", 0);
			return;
		}
		long space = FileUtil.getUsableSpace(file);
		result.put("path", oPath);
		result.put("size-bytes", space);
	}

	private void method_Queue_Move_Bottom(Map args, Map result) {
		// RPC v14
/*
	string      | value type & description
	------------+----------------------------------------------------------
	"ids"       | array   torrent list, as described in 3.1.
*/
		Object	ids = args.get( "ids" );

		Core core = CoreFactory.getSingleton();
		GlobalManager gm = core.getGlobalManager();

		List<DownloadManager>	dms = getDownloadManagerListFromIDs( gm, ids );

		gm.moveEnd(dms.toArray(new DownloadManager[0]));
	}

	private void method_Queue_Move_Down(Map args, Map result) {
		// RPC v14
/*
	string      | value type & description
	------------+----------------------------------------------------------
	"ids"       | array   torrent list, as described in 3.1.
*/
		Object ids = args.get("ids");

		Core core = CoreFactory.getSingleton();
		GlobalManager gm = core.getGlobalManager();

		List<DownloadManager>	dms = getDownloadManagerListFromIDs( gm, ids );
    Collections.sort(dms, new Comparator<DownloadManager>() {
			@Override
			public int compare(DownloadManager a, DownloadManager b) {
        return b.getPosition() - a.getPosition();
			}
    });
    for (DownloadManager dm : dms) {
			gm.moveDown(dm);
		}
	}

	private void method_Queue_Move_Up(Map args, Map result) {
		// RPC v14
/*
	string      | value type & description
	------------+----------------------------------------------------------
	"ids"       | array   torrent list, as described in 3.1.
*/
		Object ids = args.get("ids");

		Core core = CoreFactory.getSingleton();
		GlobalManager gm = core.getGlobalManager();

		List<DownloadManager>	dms = getDownloadManagerListFromIDs( gm, ids );
    Collections.sort(dms, new Comparator<DownloadManager>() {
			@Override
			public int compare(DownloadManager a, DownloadManager b) {
        return a.getPosition() - b.getPosition();
			}
    });
    for (DownloadManager dm : dms) {
			gm.moveUp(dm);
		}
	}

	private void method_Queue_Move_Top(Map args, Map result) {
		// RPC v14
/*
	string      | value type & description
	------------+----------------------------------------------------------
	"ids"       | array   torrent list, as described in 3.1.
*/
		Object	ids = args.get( "ids" );

		Core core = CoreFactory.getSingleton();
		GlobalManager gm = core.getGlobalManager();

		List<DownloadManager>	dms = getDownloadManagerListFromIDs( gm, ids );

		gm.moveTop(dms.toArray(new DownloadManager[0]));
	}

	
	private void method_Session_Get(Map args, Map result) {
		
		PluginConfig pc = plugin_interface.getPluginconfig();
		
		String 	save_dir 	= pc.getCoreStringParameter( PluginConfig.CORE_PARAM_STRING_DEFAULT_SAVE_PATH );
		int		tcp_port 	= pc.getCoreIntParameter( PluginConfig.CORE_PARAM_INT_INCOMING_TCP_PORT );
		//int		up_limit_normal 	= pc.getCoreIntParameter( PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC );
		//int		up_limit_seedingOnly 	= pc.getCoreIntParameter( PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC );
		int up_limit = pc.getCoreIntParameter( TransferSpeedValidator.getActiveUploadParameter(CoreFactory.getSingleton().getGlobalManager()));
		int		down_limit 	= pc.getCoreIntParameter( PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC );
		int		glob_con	= pc.getCoreIntParameter( PluginConfig.CORE_PARAM_INT_MAX_CONNECTIONS_GLOBAL );
		int		tor_con 	= pc.getCoreIntParameter( PluginConfig.CORE_PARAM_INT_MAX_CONNECTIONS_PER_TORRENT );
		
		
		//boolean auto_speed_on = pc.getCoreBooleanParameter( PluginConfig.CORE_PARAM_BOOLEAN_AUTO_SPEED_ON ) ||
		//						pc.getCoreBooleanParameter( PluginConfig.CORE_PARAM_BOOLEAN_AUTO_SPEED_SEEDING_ON );
					
		
		boolean require_enc = COConfigurationManager.getBooleanParameter("network.transport.encrypted.require");
		
		
		float stop_ratio = COConfigurationManager.getFloatParameter( "Stop Ratio" );
		
		IpFilter ipFilter = IpFilterManagerFactory.getSingleton().getIPFilter();
		String filter_url = COConfigurationManager.getStringParameter("Ip Filter Autoload File", "");
		final PluginInterface dht_pi = 
				plugin_interface.getPluginManager().getPluginInterfaceByClass(
							DHTPlugin.class );
		DHTPlugin dht = dht_pi == null ? null : (DHTPlugin)dht_pi.getPlugin();
		
		PluginInterface piUTP = plugin_interface.getPluginManager().getPluginInterfaceByID("azutp");
		boolean hasUTP = piUTP != null && piUTP.getPluginState().isOperational() && piUTP.getPluginconfig().getPluginBooleanParameter("utp.enabled", true);
		

    result.put(TransmissionVars.TR_PREFS_KEY_BLOCKLIST_ENABLED, ipFilter.isEnabled() );
    result.put(TransmissionVars.TR_PREFS_KEY_BLOCKLIST_URL, filter_url );
		// RPC v5, but no constant!
		result.put( "blocklist-size", ipFilter.getNbRanges());           	// number     number of rules in the blocklist
    result.put(TransmissionVars.TR_PREFS_KEY_MAX_CACHE_SIZE_MB, 0 );  // TODO
    result.put(TransmissionVars.TR_PREFS_KEY_DHT_ENABLED, dht != null && (dht.isInitialising() || dht.isEnabled()) );
    result.put(TransmissionVars.TR_PREFS_KEY_UTP_ENABLED, hasUTP );
    result.put(TransmissionVars.TR_PREFS_KEY_LPD_ENABLED, false );
    result.put(TransmissionVars.TR_PREFS_KEY_DOWNLOAD_DIR, save_dir);
    // RPC 12 to 14
    result.put("download-dir-free-space", -1);

    result.put(TransmissionVars.TR_PREFS_KEY_DSPEED_KBps, down_limit > 0 ? down_limit : pc.getUnsafeIntParameter("config.ui.speed.partitions.manual.download.last"));
    result.put(TransmissionVars.TR_PREFS_KEY_DSPEED_ENABLED, down_limit != 0 );
    result.put(TransmissionVars.TR_PREFS_KEY_ENCRYPTION, require_enc?"required":"preferred" );               		// string     "required", "preferred", "tolerated"
    result.put(TransmissionVars.TR_PREFS_KEY_IDLE_LIMIT, 30 ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_IDLE_LIMIT_ENABLED, false );//TODO
    result.put(TransmissionVars.TR_PREFS_KEY_INCOMPLETE_DIR, save_dir );
    result.put(TransmissionVars.TR_PREFS_KEY_INCOMPLETE_DIR_ENABLED, false );//TODO
    //result.put(TransmissionVars.TR_PREFS_KEY_MSGLEVEL, TR_MSG_INF ); // Not in Spec
    result.put(TransmissionVars.TR_PREFS_KEY_DOWNLOAD_QUEUE_SIZE, 5 );//TODO
    result.put(TransmissionVars.TR_PREFS_KEY_DOWNLOAD_QUEUE_ENABLED, true ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_PEER_LIMIT_GLOBAL, glob_con );
    result.put(TransmissionVars.TR_PREFS_KEY_PEER_LIMIT_TORRENT, tor_con );
    result.put(TransmissionVars.TR_PREFS_KEY_PEER_PORT, tcp_port );
    result.put(TransmissionVars.TR_PREFS_KEY_PEER_PORT_RANDOM_ON_START, false ); //TODO
    //result.put(TransmissionVars.TR_PREFS_KEY_PEER_PORT_RANDOM_LOW, 49152 ); // Not in Spec
    //result.put(TransmissionVars.TR_PREFS_KEY_PEER_PORT_RANDOM_HIGH, 65535 ); // Not in Spec
    //result.put(TransmissionVars.TR_PREFS_KEY_PEER_SOCKET_TOS, TR_DEFAULT_PEER_SOCKET_TOS_STR ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_PEX_ENABLED, true ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_PORT_FORWARDING, false ); //TODO
    //result.put(TransmissionVars.TR_PREFS_KEY_PREALLOCATION, TR_PREALLOCATE_SPARSE ); //TODO
    //result.put(TransmissionVars.TR_PREFS_KEY_PREFETCH_ENABLED, DEFAULT_PREFETCH_ENABLED ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_QUEUE_STALLED_ENABLED, true ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_QUEUE_STALLED_MINUTES, 30 ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_RATIO, 2.0 ); //TODO (wrong key?)
    result.put(TransmissionVars.TR_PREFS_KEY_RATIO_ENABLED, false ); //TODO (wrong key?)
    result.put(TransmissionVars.TR_PREFS_KEY_RENAME_PARTIAL_FILES, true ); //TODO
    //result.put(TransmissionVars.TR_PREFS_KEY_RPC_AUTH_REQUIRED, false ); // Not in Spec
    //String bindIP = pc.getPluginStringParameter(WebPlugin.CONFIG_BIND_IP);
    //result.put(TransmissionVars.TR_PREFS_KEY_RPC_BIND_ADDRESS, bindIP == null || bindIP.length() == 0 ? "0.0.0.0" : bindIP );
    //result.put(TransmissionVars.TR_PREFS_KEY_RPC_ENABLED, false ); // Not in Spec
    //result.put(TransmissionVars.TR_PREFS_KEY_RPC_PASSWORD, "" ); // Not in Spec
    //result.put(TransmissionVars.TR_PREFS_KEY_RPC_USERNAME, "" ); // Not in Spec
    //result.put(TransmissionVars.TR_PREFS_KEY_RPC_WHITELIST, TR_DEFAULT_RPC_WHITELIST ); // Not in Spec
    //result.put(TransmissionVars.TR_PREFS_KEY_RPC_WHITELIST_ENABLED, true ); // Not in Spec
    //result.put(TransmissionVars.TR_PREFS_KEY_RPC_PORT, atoi( TR_DEFAULT_RPC_PORT_STR ) ); // Not in Spec
    //result.put(TransmissionVars.TR_PREFS_KEY_RPC_URL, TR_DEFAULT_RPC_URL_STR ); // Not in Spec
    //result.put(TransmissionVars.TR_PREFS_KEY_SCRAPE_PAUSED_TORRENTS, true ); // Not in Spec
    result.put(TransmissionVars.TR_PREFS_KEY_SCRIPT_TORRENT_DONE_FILENAME, "" ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_SCRIPT_TORRENT_DONE_ENABLED, false ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_SEED_QUEUE_SIZE, 10 ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_SEED_QUEUE_ENABLED, false ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_ALT_SPEED_ENABLED, false ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_ALT_SPEED_UP_KBps, 50 );  //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_ALT_SPEED_DOWN_KBps, 50 );  //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_ALT_SPEED_TIME_BEGIN, 540 ); /* 9am */  //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_ALT_SPEED_TIME_ENABLED, false ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_ALT_SPEED_TIME_END, 1020 ); /* 5pm */ //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_ALT_SPEED_TIME_DAY, TransmissionVars.TR_SCHED_ALL ); //TODO
    result.put(TransmissionVars.TR_PREFS_KEY_USPEED_KBps, up_limit > 0 ? up_limit :pc.getUnsafeIntParameter("config.ui.speed.partitions.manual.upload.last"));
    result.put(TransmissionVars.TR_PREFS_KEY_USPEED_ENABLED, up_limit != 0);
    //result.put(TransmissionVars.TR_PREFS_KEY_UMASK, 022 ); // Not in Spec
    result.put(TransmissionVars.TR_PREFS_KEY_UPLOAD_SLOTS_PER_TORRENT, 14 ); //TODO
    //result.put(TransmissionVars.TR_PREFS_KEY_BIND_ADDRESS_IPV4, TR_DEFAULT_BIND_ADDRESS_IPV4 ); //TODO
    //result.put(TransmissionVars.TR_PREFS_KEY_BIND_ADDRESS_IPV6, TR_DEFAULT_BIND_ADDRESS_IPV6 ); //TODO

    result.put("config-dir", "" ); //TODO

    boolean startStopped = COConfigurationManager.getBooleanParameter("Default Start Torrents Stopped");
    result.put(TransmissionVars.TR_PREFS_KEY_START, !startStopped ); //TODO
    
		boolean renamePartial = COConfigurationManager.getBooleanParameter("Rename Incomplete Files");
    result.put(TransmissionVars.TR_PREFS_KEY_RENAME_PARTIAL_FILES, renamePartial); 
		

    result.put(TransmissionVars.TR_PREFS_KEY_TRASH_ORIGINAL, false ); //TODO

    String az_version = Constants.AZUREUS_VERSION;
    
    try{
    		// get the actual version instead of cached constant; since 5001
    	
    	az_version = Constants.getCurrentVersion();
    	
    }catch( Throwable e ){
    }

		result.put( "port", new Long( tcp_port ) );                	// number     port number
		result.put( "rpc-version", new Long( 15 ));              	// number     the current RPC API version
		result.put( "rpc-version-minimum", new Long( 6 ));      	// number     the minimum RPC API version supported
		result.put( "seedRatioLimit", new Double(stop_ratio) );          	// double     the default seed ratio for torrents to use
		result.put( "seedRatioLimited", stop_ratio>0 );         			// boolean    true if seedRatioLimit is honored by default

		String version = plugin_interface.getPluginVersion();
		result.put( "version",  version == null ? "Source" : version);           // string     
		result.put( "az-rpc-version", VUZE_RPC_VERSION);
		result.put( "az-version", "5.7.5.0" );                  // string
		result.put( "biglybt-version", az_version );                  // string
		result.put( "rpc-i2p-address", pc.getPluginStringParameter("webui.i2p_dest"));
		result.put( "rpc-tor-address", pc.getPluginStringParameter("webui.tor_dest"));
		result.put( "az-content-port", getMediaServerActivePort());
		
		List listSupports = new ArrayList();
		Collections.addAll(listSupports, "rpc:receive-gzip", "field:files-hc",
				"method:tags-get-list", "field:torrent-set-name", 
				"method:subscription-get", "method:subscription-add", 
				"method:subscription-remove", "method:subscription-set",
				"method:vuze-plugin-get-list", "method:tags-lookup-start",
				"method:tags-lookup-get-results", "method:vuze-search-start",
				"method:vuze-search-get-results", "torrent-add:torrent-duplicate");

		synchronized( json_server_method_lock ){
  		for (String key : json_server_methods.keySet()) {
  			listSupports.add("method:" + key);
  		}
		}

		result.put("rpc-supports", listSupports);
		
		if (lastVerserverCheck == 0 || SystemTime.getCurrentTime() - lastVerserverCheck > 864000l) {
			lastVerserverCheck = SystemTime.getCurrentTime();
  		Map decoded = VersionCheckClient.getSingleton().getVersionCheckInfo("xmw");
  		String userMessage = getUserMessage(decoded);
  		if (userMessage != null) {
  			result.put("az-message", userMessage);
  		}
		}
		//result.put("az-message", "This is a test message with a  <A HREF=\"http://www.vuze.com\">Link</a>");
		
	}
	
	
	public int getMediaServerActivePort() {
		return plugin_interface.getPluginconfig().getUnsafeIntParameter(
				"Plugin.azupnpav.content_port", -1);
	}

  private String 
  getUserMessage( 
	Map reply ) 
	{
		try {
			byte[] message_bytes = MapUtils.getMapByteArray(reply, "xmwebui_message",
					null);
			if (message_bytes == null || message_bytes.length == 0) {
				return null;
			}

			String message;

			try {
				message = new String(message_bytes, "UTF-8");

			} catch (Throwable e) {

				message = new String(message_bytes);
			}

			byte[] signature = MapUtils.getMapByteArray(reply, "xmwebui_message_sig",
					null);

			if (signature == null) {

				log("Signature missing from message");

				return null;
			}

			try {
				AEVerifier.verifyData(message, signature);

			} catch (Throwable e) {

				log("Message signature check failed", e);

				return null;
			}

			return message;
		} catch (Throwable e) {
			log("Failed get message", e);

			Debug.printStackTrace(e);
		}
		return null;
	} 


	private void method_Session_Set(Map args, Map result)
			throws IOException {

		checkUpdatePermissions();

		PluginConfig pc = plugin_interface.getPluginconfig();
/*
 "download-queue-size"            | number     | max number of torrents to download at once (see download-queue-enabled)
 "download-queue-enabled"         | boolean    | if true, limit how many torrents can be downloaded at once
 "dht-enabled"                    | boolean    | true means allow dht in public torrents
 "encryption"                     | string     | "required", "preferred", "tolerated"
 "idle-seeding-limit"             | number     | torrents we're seeding will be stopped if they're idle for this long
 "idle-seeding-limit-enabled"     | boolean    | true if the seeding inactivity limit is honored by default
 "incomplete-dir"                 | string     | path for incomplete torrents, when enabled
 "incomplete-dir-enabled"         | boolean    | true means keep torrents in incomplete-dir until done
 "lpd-enabled"                    | boolean    | true means allow Local Peer Discovery in public torrents
 "peer-limit-global"              | number     | maximum global number of peers
 "peer-limit-per-torrent"         | number     | maximum global number of peers
 "pex-enabled"                    | boolean    | true means allow pex in public torrents
 "peer-port"                      | number     | port number
 "peer-port-random-on-start"      | boolean    | true means pick a random peer port on launch
 "port-forwarding-enabled"        | boolean    | true means enabled
 "queue-stalled-enabled"          | boolean    | whether or not to consider idle torrents as stalled
 "queue-stalled-minutes"          | number     | torrents that are idle for N minuets aren't counted toward seed-queue-size or download-queue-size
 "rename-partial-files"           | boolean    | true means append ".part" to incomplete files
 "script-torrent-done-filename"   | string     | filename of the script to run
 "script-torrent-done-enabled"    | boolean    | whether or not to call the "done" script
 "seedRatioLimit"                 | double     | the default seed ratio for torrents to use
 "seedRatioLimited"               | boolean    | true if seedRatioLimit is honored by default
 "seed-queue-size"                | number     | max number of torrents to uploaded at once (see seed-queue-enabled)
 "seed-queue-enabled"             | boolean    | if true, limit how many torrents can be uploaded at once
 "speed-limit-down"               | number     | max global download speed (KBps)
 "speed-limit-down-enabled"       | boolean    | true means enabled
 "speed-limit-up"                 | number     | max global upload speed (KBps)
 "speed-limit-up-enabled"         | boolean    | true means enabled
 "start-added-torrents"           | boolean    | true means added torrents will be started right away
 "trash-original-torrent-files"   | boolean    | true means the .torrent file of added torrents will be deleted
 "utp-enabled"                    | boolean    | true means allow utp
 */
		for (Map.Entry<String, Object> arg : ((Map<String, Object>) args).entrySet()) {

			String key = arg.getKey();
			Object val = arg.getValue();
			try {
				if (key.startsWith("alt-speed")) {
					// TODO:
          // "alt-speed-down"                 | number     | max global download speed (KBps)
          // "alt-speed-enabled"              | boolean    | true means use the alt speeds
          // "alt-speed-time-begin"           | number     | when to turn on alt speeds (units: minutes after midnight)
          // "alt-speed-time-enabled"         | boolean    | true means the scheduled on/off times are used
          // "alt-speed-time-end"             | number     | when to turn off alt speeds (units: same)
          // "alt-speed-time-day"             | number     | what day(s) to turn on alt speeds (look at tr_sched_day)
          // "alt-speed-up"                   | number     | max global upload speed (KBps)

				} else if (key.equals("blocklist-url")) {
					// "blocklist-url"                  | string     | location of the blocklist to use for "blocklist-update"
					IpFilter ipFilter = IpFilterManagerFactory.getSingleton().getIPFilter();
					COConfigurationManager.setParameter("Ip Filter Autoload File",
							(String) val);
					COConfigurationManager.setParameter(
							IpFilterAutoLoaderImpl.CFG_AUTOLOAD_LAST, 0);
					try {
						ipFilter.reload();
					} catch (Exception e) {
						e.printStackTrace();
					}
					
				} else if (key.equals("blocklist-enabled")) {
					// "blocklist-enabled"              | boolean    | true means enabled
					plugin_interface.getIPFilter().setEnabled(getBoolean(val));
					
				} else if (key.equals("cache-size-mb")) {
					// "cache-size-mb"                  | number     | maximum size of the disk cache (MB)
					// umm.. not needed

				} else if (key.equals("download-dir")) {
					// "download-dir"                   | string     | default path to download torrents

					String dir = (String) val;

					String save_dir = pc.getCoreStringParameter(PluginConfig.CORE_PARAM_STRING_DEFAULT_SAVE_PATH);
					if (!save_dir.equals(dir)) {

						save_dir = dir;

						pc.setCoreStringParameter(
								PluginConfig.CORE_PARAM_STRING_DEFAULT_SAVE_PATH, dir);
					}

				} else if (key.equals("")) {

				} else if (key.equals("")) {
				} else if (key.equals("")) {
				} else if (key.equals("")) {
				} else if (key.equals("")) {
				} else if (key.equals("")) {
				} else if (key.equals("")) {
				} else if (key.equals(TransmissionVars.TR_PREFS_KEY_START)) {

					COConfigurationManager.setParameter("Default Start Torrents Stopped", !getBoolean(val));
					
				} else if (key.equals(TransmissionVars.TR_PREFS_KEY_RENAME_PARTIAL_FILES)) {
					
					COConfigurationManager.setParameter("Rename Incomplete Files", getBoolean(val));

				} else if (key.equals("speed-limit-down-enabled")
						|| key.equals("downloadLimited")) {

					int down_limit = pc.getCoreIntParameter(PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC);
					
					boolean enable = getBoolean(val);

					if (!enable && down_limit != 0) {

						down_limit = 0;

						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC,
								down_limit);
					} else if (enable && down_limit == 0) {
						int lastRate = pc.getUnsafeIntParameter("config.ui.speed.partitions.manual.download.last");
						if (lastRate <= 0) {
							lastRate = 10;
						}
						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC,
								lastRate);
					}
				} else if (key.equals("speed-limit-down")
						|| key.equals("downloadLimit")) {

					int down_limit = pc.getCoreIntParameter(PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC);

					int limit = getNumber(val).intValue();

					if (limit != down_limit) {

						down_limit = limit;

						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC,
								limit);
					}
				} else if (key.equals("speed-limit-up-enabled")
						|| key.equals("uploadLimited")) {
					boolean enable = getBoolean(val);

					// turn off auto speed for both normal and seeding-only mode
					// this will reset upload speed to what it was before it was on
					pc.setCoreBooleanParameter(
							PluginConfig.CORE_PARAM_BOOLEAN_AUTO_SPEED_ON, false);
					pc.setCoreBooleanParameter(
							PluginConfig.CORE_PARAM_BOOLEAN_AUTO_SPEED_SEEDING_ON, false);

					int up_limit = pc.getCoreIntParameter(PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC);
					int up_limit_seeding = pc.getCoreIntParameter(PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC);

					if (!enable) {
						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC, 0);
						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC,
								0);
					} else if (enable && (up_limit == 0 || up_limit_seeding == 0)) {
						int lastRate = pc.getUnsafeIntParameter("config.ui.speed.partitions.manual.upload.last");
						if (lastRate <= 0) {
							lastRate = 10;
						}
						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC, lastRate);
						pc.setCoreIntParameter(
								PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC,
								lastRate);
					}
				} else if (key.equals("speed-limit-up") || key.equals("uploadLimit")) {

					// turn off auto speed for both normal and seeding-only mode
					// this will reset upload speed to what it was before it was on
					pc.setCoreBooleanParameter(
							PluginConfig.CORE_PARAM_BOOLEAN_AUTO_SPEED_ON, false);
					pc.setCoreBooleanParameter(
							PluginConfig.CORE_PARAM_BOOLEAN_AUTO_SPEED_SEEDING_ON, false);

					int limit = getNumber(val).intValue();

					pc.setCoreIntParameter(
							PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC,
							limit);
					pc.setCoreIntParameter(
							PluginConfig.CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC,
							limit);
				} else if (key.equals("peer-port") || key.equals("port")) {

					int port = getNumber(val).intValue();

					pc.setCoreIntParameter(PluginConfig.CORE_PARAM_INT_INCOMING_TCP_PORT,
							port);
				} else if (key.equals("encryption")) {

					String value = (String) val;

					boolean required = value.equals("required");

					COConfigurationManager.setParameter(
							"network.transport.encrypted.require", required);
				} else if (key.equals("seedRatioLimit")) {
					// RPC v5

					float ratio = getNumber(val).floatValue();

					COConfigurationManager.setParameter("Stop Ratio", ratio);

				} else if (key.equals("seedRatioLimited")) {
					// RPC v5

					boolean limit = getBoolean(val);
					
					float ratio;
					if (limit) {
						// 2f is made up; sharing is caring
						if (args.containsKey("seedRatioLimit")) {
							ratio = getNumber(args.get("seedRatioLimit"), 2f).floatValue();
						} else {
							ratio = 2f;
						}
					} else {
						ratio = 0f;
					}

					COConfigurationManager.setParameter("Stop Ratio", ratio);

				} else {

					if ( trace_param.getValue() ){
						log("Unhandled session-set field: " + key);
					}
				}
			} catch (Throwable t) {
				Debug.out(key + ":" + val, t);
			}
		}
	}

	private void method_Blocklist_Update(Map args, Map result) {
		// TODO
		log("blocklist-update not supported");
	}
	
	private void
	method_Torrent_Rename_Path(
			Map args, 
			Map result)
	{
		/*
   Request arguments:

   string                           | value type & description
   ---------------------------------+-------------------------------------------------
   "ids"                            | array      the torrent torrent list, as described in 3.1
                                    |            (must only be 1 torrent)
   "path"                           | string     the path to the file or folder that will be renamed
   "name"                           | string     the file or folder's new name

   Response arguments: "path", "name", and "id", holding the torrent ID integer
		 */
		if ( trace_param.getValue() ){
			log( "unhandled method: torrent-rename-path - " + args );
		}
	}



	private void 
	method_Torrent_Set_Location(
			Map args, 
			Map result)
	throws IOException, DownloadException
	{
		/*
 Request arguments:

 string                     | value type & description
 ---------------------------+-------------------------------------------------
 "ids"                      | array      torrent list, as described in 3.1
 "location"                 | string     the new torrent location
 "move"                     | boolean    if true, move from previous location.
                            |            otherwise, search "location" for files
                            |            (default: false)

 Response arguments: none
		 */
		checkUpdatePermissions();
		
		Object	ids = args.get( "ids" );

		boolean	moveData = getBoolean( args.get( "move" ));
		String sSavePath = (String) args.get("location");
		
		List<DownloadStub>	downloads = getDownloads( ids, false );

		File fSavePath = new File(sSavePath);

		for ( DownloadStub download_stub: downloads ){
			
			Download download = destubbify( download_stub );
			
			if (moveData) {
				Torrent torrent = download.getTorrent();
				if (torrent == null || torrent.isSimpleTorrent()
						|| fSavePath.getParentFile() == null) {
					download.moveDataFiles(fSavePath);
				} else {
					download.moveDataFiles(fSavePath.getParentFile(), fSavePath.getName());
				}
			} else {
  			DownloadManager dm = PluginCoreUtils.unwrap(download);
  			
  			// This is copied from TorrentUtils.changeDirSelectedTorrent
  			
  			int state = dm.getState();
  			if (state == DownloadManager.STATE_STOPPED) {
  				if (!dm.filesExist(true)) {
  					state = DownloadManager.STATE_ERROR;
  				}
  			}
  
  			if (state == DownloadManager.STATE_ERROR) {
  				
  				dm.setTorrentSaveDir(sSavePath);
  				
  				boolean found = dm.filesExist(true);
  				if (!found && dm.getTorrent() != null
  						&& !dm.getTorrent().isSimpleTorrent()) {
  					String parentPath = fSavePath.getParent();
  					if (parentPath != null) {
  						dm.setTorrentSaveDir(parentPath);
  						found = dm.filesExist(true);
  						if (!found) {
  							dm.setTorrentSaveDir(sSavePath);
  						}
  					}
  				}
  
  
  				if (found) {
  					dm.stopIt(DownloadManager.STATE_STOPPED, false, false);
  
  					dm.setStateQueued();
  				}
  			}
			}
		}
	}

	private void 
	method_Session_Stats(
			Map args, 
			Map result) 
	{
		/*
   string                     | value type
   ---------------------------+-------------------------------------------------
   "activeTorrentCount"       | number
   "downloadSpeed"            | number
   "pausedTorrentCount"       | number
   "torrentCount"             | number
   "uploadSpeed"              | number
   ---------------------------+-------------------------------+
   "cumulative-stats"         | object, containing:           |
                              +------------------+------------+
                              | uploadedBytes    | number     | tr_session_stats
                              | downloadedBytes  | number     | tr_session_stats
                              | filesAdded       | number     | tr_session_stats
                              | sessionCount     | number     | tr_session_stats
                              | secondsActive    | number     | tr_session_stats
   ---------------------------+-------------------------------+
   "current-stats"            | object, containing:           |
                              +------------------+------------+
                              | uploadedBytes    | number     | tr_session_stats
                              | downloadedBytes  | number     | tr_session_stats
                              | filesAdded       | number     | tr_session_stats
                              | sessionCount     | number     | tr_session_stats
                              | secondsActive    | number     | tr_session_stats
		 */
		
		List<String> fields = (List<String>) args.get("fields");
		boolean all = fields == null || fields.size() == 0;
		if (!all) {
			// sort so we can't use Collections.binarySearch
			Collections.sort(fields);
		}

		Core core = CoreFactory.getSingleton();
		GlobalManager gm = core.getGlobalManager();
		GlobalManagerStats stats = gm.getStats();

		TagManager tm = TagManagerFactory.getTagManager();

		// < RPC v4
		if (all
				|| Collections.binarySearch(fields,
						TR_SESSION_STATS_ACTIVE_TORRENT_COUNT) >= 0) {
			// Could use 7 or "tag.type.ds.act" for tag_active
			Tag tag = tm.getTagType(TagType.TT_DOWNLOAD_STATE).getTag(7);
			result.put(TR_SESSION_STATS_ACTIVE_TORRENT_COUNT,
					tag == null ? 0 : tag.getTaggedCount());
		}

		if (all
				|| Collections.binarySearch(fields, TR_SESSION_STATS_DOWNLOAD_SPEED) >= 0) {
			result.put(TR_SESSION_STATS_DOWNLOAD_SPEED,
					stats.getDataAndProtocolReceiveRate());
		}

		if (all
				|| Collections.binarySearch(fields,
						TR_SESSION_STATS_PAUSED_TORRENT_COUNT) >= 0) {
			// Could use 8 or "tag.type.ds.pau" for tag_pause
			Tag tag = tm.getTagType(TagType.TT_DOWNLOAD_STATE).getTag(8);
			result.put(TR_SESSION_STATS_PAUSED_TORRENT_COUNT,
					tag == null ? 0 : tag.getTaggedCount());
		}

		if (all
				|| Collections.binarySearch(fields, TR_SESSION_STATS_TORRENT_COUNT) >= 0) {
			// XXX: This is size with low-noise torrents, which aren't normally shown
			result.put(TR_SESSION_STATS_TORRENT_COUNT,
					gm.getDownloadManagers().size());
		}

		if (all
				|| Collections.binarySearch(fields, TR_SESSION_STATS_UPLOAD_SPEED) >= 0) {
			result.put(TR_SESSION_STATS_UPLOAD_SPEED,
					stats.getDataAndProtocolSendRate());
		}

		// RPC v4
		if (all || Collections.binarySearch(fields, TR_SESSION_STATS_CURRENT) >= 0) {
			Map current_stats = new HashMap();

			result.put(TR_SESSION_STATS_CURRENT, current_stats);

			current_stats.put("uploadedBytes", stats.getTotalDataBytesSent());
			current_stats.put("downloadedBytes", stats.getTotalDataBytesReceived());

			long sent = stats.getTotalDataBytesSent();
			long received = stats.getTotalDataBytesReceived();

			float ratio;

			if (received == 0) {

				ratio = (sent == 0 ? 1 : Float.MAX_VALUE);

			} else {

				ratio = ((float) sent) / received;
			}

			current_stats.put("ratio", ratio);
			current_stats.put("secondsActive", 0); // TODO
		}


		if (all
				|| Collections.binarySearch(fields, TR_SESSION_STATS_CUMULATIVE) >= 0) {
			// RPC v4
			Map cumulative_stats = new HashMap();
			result.put("cumulative-stats", cumulative_stats);

			// TODO: ALL!
			cumulative_stats.put("uploadedBytes", 0);
			cumulative_stats.put("downloadedBytes", 0);
			cumulative_stats.put("ratio", 0);
			cumulative_stats.put("secondsActive", 0);
			cumulative_stats.put("sessionCount", 0);
		}
	}

	private void 
	method_Torrent_Set(
		String		session_id,
		Map 		args, 
		Map 		result) 
	{
		Object	ids = args.get( "ids" );
		
		handleRecentlyRemoved( session_id, args, result );
		
		List<DownloadStub>	downloads = getDownloads( ids, false );
		
		// RPC v5
		// Not used: Number bandwidthPriority = getNumber("bandwidthPriority", null);

		Number speed_limit_down = getNumber(
				args.get("downloadLimit"),
				getNumber(args.get("speed-limit-down"),
						getNumber(args.get("speedLimitDownload"))));
		Boolean downloadLimited = getBoolean("downloadLimited", null);

		List files_wanted 		= (List)args.get( "files-wanted" );
		List files_unwanted 	= (List)args.get( "files-unwanted" );

		// RPC v5
		/** true if session upload limits are honored */
		// Not Used: Boolean honorsSessionLimits = getBoolean("honorsSessionLimits", null);

		
		// "location"            | string     new location of the torrent's content
		String location = (String) args.get("location");

		// Not Implemented: By default, Vuze automatically adjusts mac connections per torrent based on bandwidth and seeding state
		// "peer-limit"          | number     maximum number of peers
		
		List priority_high		= (List)args.get( "priority-high" );
		List priority_low		= (List)args.get( "priority-low" );
		List priority_normal	= (List)args.get( "priority-normal" );

		List file_infos 		= (List)args.get( "files" );
		
		// RPC v14
		// "queuePosition"       | number     position of this torrent in its queue [0...n)
		Number queuePosition = getNumber("queuePosition", null);

		// RPC v10
		// "seedIdleLimit"       | number     torrent-level number of minutes of seeding inactivity

		// RPC v10: Not used, always TransmissionVars.TR_IDLELIMIT_GLOBAL
		// "seedIdleMode"        | number     which seeding inactivity to use.  See tr_inactvelimit (OR tr_idlelimit and TR_IDLELIMIT_*)

		// RPC v5: Not Supported
		// "seedRatioLimit"      | double     torrent-level seeding ratio

		// RPC v5: Not Supported
		// "seedRatioMode"       | number     which ratio to use.  See tr_ratiolimit

		// RPC v10
		// "trackerAdd"          | array      strings of announce URLs to add
		List trackerAddList = (List) args.get("trackerAdd");

		// RPC v10: TODO
		// "trackerRemove"       | array      ids of trackers to remove
		// List trackerRemoveList = (List) args.get("trackerRemove");

		// RPC v10: TODO
		// "trackerReplace"      | array      pairs of <trackerId/new announce URLs>

		// "uploadLimit"         | number     maximum upload speed (KBps)
		Number speed_limit_up = getNumber(
				args.get("uploadLimit"),
				getNumber(args.get("speed-limit-up"),
						getNumber(args.get("speedLimitUpload"))));

		// "uploadLimited"       | boolean    true if "uploadLimit" is honored
		Boolean uploadLimited = getBoolean("uploadLimited", null);
		
		// RPC Vuze
		// "tagAdd"             | array       array of tags to add to torrent
		List tagAddList = (List) args.get("tagAdd");
		List tagRemoveList = (List) args.get("tagRemove");

		Long	l_uploaded_ever		= (Long)args.get( "uploadedEver" );
		Long	l_downloaded_ever 	= (Long)args.get( "downloadedEver" );
		
		long	uploaded_ever 	= l_uploaded_ever==null?-1:l_uploaded_ever.longValue();
		long	downloaded_ever = l_downloaded_ever==null?-1:l_downloaded_ever.longValue();

		String name = (String) args.get("name");


		for ( DownloadStub download_stub: downloads ){
						
			try{
				Download	download = destubbify( download_stub );
				
				Torrent t = download.getTorrent();
				
				if ( t == null ){
					
					continue;
				}
	
				if (location != null) {
					File file = new File(location);
					if (!file.isFile()) {
						try {
							download.moveDataFiles(file);
						} catch (DownloadException e) {
							Debug.out(e);
						}
					}
				}
				
				if (name != null) {
					DownloadManager core_download = PluginCoreUtils.unwrap(download);
					core_download.getDownloadState().setDisplayName(name);
				}
				
				if (queuePosition != null) {
					download.moveTo(queuePosition.intValue());
				}
				
				if (trackerAddList != null) {
					for (Object oTracker : trackerAddList) {
						if (oTracker instanceof String) {
							String aTracker = (String) oTracker;
							TorrentUtils.announceGroupsInsertFirst(PluginCoreUtils.unwrap(t), aTracker);
						}
					}
				}
				
				
				if ( speed_limit_down != null && Boolean.TRUE.equals(downloadLimited) ){
					
					download.setDownloadRateLimitBytesPerSecond( speed_limit_down.intValue());
				} else if (Boolean.FALSE.equals(downloadLimited)) {
	
					download.setDownloadRateLimitBytesPerSecond(0);
				}
				
				if ( speed_limit_up != null && Boolean.TRUE.equals(uploadLimited) ){
					
					download.setUploadRateLimitBytesPerSecond( speed_limit_up.intValue());
				} else if (Boolean.FALSE.equals(uploadLimited)) {
	
					download.setUploadRateLimitBytesPerSecond(0);
				}			
				
				if (tagAddList != null) {
					TagManager tm = TagManagerFactory.getTagManager();

					if (tm.isEnabled()) {

						TagType tt = tm.getTagType(TagType.TT_DOWNLOAD_MANUAL);

						for (Object oTagToAdd : tagAddList) {
							if (oTagToAdd != null) {
								addTagToDownload(download, oTagToAdd, tt);
							}

						}
					}
				}
				
				if (tagRemoveList != null) {
					TagManager tm = TagManagerFactory.getTagManager();

					if (tm.isEnabled()) {

						TagType ttManual = tm.getTagType(TagType.TT_DOWNLOAD_MANUAL);
						TagType ttCategory = tm.getTagType(TagType.TT_DOWNLOAD_CATEGORY);

						for (Object oTagToAdd : tagRemoveList) {
							if (oTagToAdd instanceof String) {
								Tag tag = ttManual.getTag((String) oTagToAdd, true);
								if (tag != null) {
									tag.removeTaggable(PluginCoreUtils.unwrap(download));
								}
								tag = ttCategory.getTag((String) oTagToAdd, true);
								if (tag != null) {
									tag.removeTaggable(PluginCoreUtils.unwrap(download));
								}
							} else if (oTagToAdd instanceof Number) {
								int uid = ((Number) oTagToAdd).intValue();
								Tag tag = ttManual.getTag(uid);
								if (tag != null) {
									tag.removeTaggable(PluginCoreUtils.unwrap(download));
								}
								tag = ttCategory.getTag(uid);
								if (tag != null) {
									tag.removeTaggable(PluginCoreUtils.unwrap(download));
								}
							}

						}
					}
				}
									
				DiskManagerFileInfo[] files = download.getDiskManagerFileInfo();
					
				if ( files_unwanted != null ){
					
					for ( int i=0;i<files_unwanted.size();i++){
						
						int	index = ((Long)files_unwanted.get( i )).intValue();
						
						if ( index >= 0 && index <= files.length ){
							
							files[index].setSkipped( true );
						}
					}
				}
				
				if ( files_wanted != null ){
					
					for ( int i=0;i<files_wanted.size();i++){
						
						int	index = ((Long)files_wanted.get( i )).intValue();
						
						if ( index >= 0 && index <= files.length ){
							
							files[index].setSkipped( false );
						}
					}
				}
				
				if ( priority_high != null ){
					
					for ( int i=0;i<priority_high.size();i++){
						
						int	index = ((Long)priority_high.get( i )).intValue();
						
						if ( index >= 0 && index <= files.length ){
							
							files[index].setNumericPriority( DiskManagerFileInfo.PRIORITY_HIGH );
						}
					}
				}
				
				if ( priority_normal != null ){
					
					for ( int i=0;i<priority_normal.size();i++){
						
						int	index = ((Long)priority_normal.get( i )).intValue();
						
						if ( index >= 0 && index <= files.length ){
							
							files[index].setNumericPriority( DiskManagerFileInfo.PRIORITY_NORMAL );
						}
					}
				}
				
				if ( priority_low != null ){
					
					for ( int i=0;i<priority_low.size();i++){
						
						int	index = ((Long)priority_low.get( i )).intValue();
						
						if ( index >= 0 && index <= files.length ){
							
							files[index].setNumericPriority( DiskManagerFileInfo.PRIORITY_LOW );
						}
					}
				}
				
				if ( uploaded_ever != -1 || downloaded_ever != -1 ){
					
						// new method in 4511 B31
					
					try{
						download.getStats().resetUploadedDownloaded( uploaded_ever, downloaded_ever );
						
					}catch( Throwable e ){
					}
				}
				
				if ( file_infos != null ){
					
					boolean	paused_it = false;
					
					try{
						for ( int i=0;i<file_infos.size();i++){
							
							Map file_info = (Map)file_infos.get( i );
							
							int index = ((Number)file_info.get( "index" )).intValue();
							
							if ( index < 0 || index >= files.length ){
							
								throw( new IOException( "File index '" + index + "' invalid for '" + download.getName()+ "'" ));
							}
							
							//String	path 	= (String)file_info.get( "path" ); don't support changing this yet
							
							String  new_name	= (String)file_info.get( "name" );		// terminal name of the file (NOT the whole relative path+name)
							
							if ( new_name == null || new_name.trim().length() == 0 ){
								
								throw( new IOException( "'name' is mandatory"));
							}
							
							new_name = new_name.trim();
							
							DiskManagerFileInfo file = files[index];
							
							File existing = file.getFile( true );
							
							if ( existing.getName().equals( new_name )){
								
								continue;
							}
							
							if ( !download.isPaused()){
								
								download.pause();
								
								paused_it = true;
							}
							
							File new_file = new File( existing.getParentFile(), new_name );
							
							if ( new_file.exists()){
								
								throw( new IOException( "new file '" + new_file + "' already exists" ));
							}
							
							file.setLink( new_file );
						}
					}finally{
						
						if ( paused_it ){
							
							download.resume();
						}
					}
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}

	private void addTagToDownload(Download download, Object tagToAdd, TagType tt) {
		Tag tag = null;
		if (tagToAdd instanceof String) {
			String tagNameToAdd = (String) tagToAdd;
			tagToAdd = tagNameToAdd.trim();

			if (tagNameToAdd.length() == 0) {
				return;
			}
			tag = tt.getTag(tagNameToAdd, true);

			if (tag == null) {
				try {
					tag = tt.createTag(tagNameToAdd, true);
				} catch (Throwable e) {
					Debug.out(e);
				}
			}
		} else if (tagToAdd instanceof Number) {
			tag = tt.getTag(((Number) tagToAdd).intValue());
		}

		if (tag != null) {
			tag.addTaggable(PluginCoreUtils.unwrap(download));
		}
	}

	private void
	method_Torrent_Reannounce(
			Map args, 
			Map result)
	throws IOException
	{
		checkUpdatePermissions();
		
		Object	ids = args.get( "ids" );

		List<DownloadStub>	downloads = getDownloads( ids, false );

		for ( DownloadStub download_stub: downloads ){
						
			try{
				destubbify( download_stub ).requestTrackerAnnounce();

			}catch( Throwable e ){
				
				Debug.out( "Failed to reannounce '" + download_stub.getName() + "'", e );
			}
		}
	}


	private void 
	method_Torrent_Remove(
			Map args, 
			Map result) 
	throws IOException 
	{
		/*
 Request arguments:

 string                     | value type & description
 ---------------------------+-------------------------------------------------
 "ids"                      | array      torrent list, as described in 3.1
 "delete-local-data"        | boolean    delete local data. (default: false)

 Response arguments: none
		 */
		checkUpdatePermissions();
		
		Object	ids = args.get( "ids" );

		boolean	delete_data = getBoolean( args.get( "delete-local-data" ));
		
		List<DownloadStub>	downloads = getDownloads( ids, true );

		for ( DownloadStub download_stub: downloads ){
			
			try{
				if ( download_stub instanceof MagnetDownload ){
					
					synchronized( magnet_downloads ){
						
						magnet_downloads.remove( download_stub );
					}
					
					addRecentlyRemoved( download_stub );
					
				}else{
					Download download = destubbify( download_stub );
					
					int	state = download.getState();
					
					if ( state != Download.ST_STOPPED ){
					
						download.stop();
					}
					
					if ( delete_data ){
						
						download.remove( true, true );
						
					}else{
						
						download.remove();	
					}
				
					addRecentlyRemoved( download );
				}
			}catch( Throwable e ){
				
				Debug.out( "Failed to remove download '" + download_stub.getName() + "'", e );
			}
		}
	}

	private void 
	method_Torrent_Verify(
			Map args, 
			Map result)
	throws IOException 
	{
		checkUpdatePermissions();
		
		Object	ids = args.get( "ids" );

		List<DownloadStub>	downloads = getDownloads( ids, false );

		for ( DownloadStub download_stub: downloads ){
			
			try{
				Download download = destubbify( download_stub );
				
				int	state = download.getState();
				
				if ( state != Download.ST_STOPPED ){
				
					download.stop();
				}
				
				download.recheckData();
				
			}catch( Throwable e ){
			}
		}
	}

	private void 
	method_Torrent_Stop(
			Map args, 
			Map result)
	throws IOException 
	{
		checkUpdatePermissions();
		
		Object	ids = args.get( "ids" );

		List<DownloadStub>	downloads = getDownloads( ids, false );

		for ( DownloadStub download_stub: downloads ){
			
			if ( !download_stub.isStub()){
				
				try{
					Download download = destubbify( download_stub );
					
					int	state = download.getState();
					
					if ( state != Download.ST_STOPPED ){
					
						download.stop();
					}
				}catch( Throwable e ){
				}
			}
		}
	}

	private void 
	method_Torrent_Start(
			Map args, 
			Map result) 
	throws IOException 
	{
		checkUpdatePermissions();
		
		Object	ids = args.get( "ids" );

		List<DownloadStub>	downloads = getDownloads( ids, false );

		for ( DownloadStub download_stub: downloads ){
			
			try{
				Download download = destubbify( download_stub );
				
				int	state = download.getState();
				if (state == Download.ST_ERROR) {
					// Stop on an error torrent should stop it immediately
					download.stop();
				}
				
				if ( state != Download.ST_DOWNLOADING && state != Download.ST_SEEDING ){
				
					download.restart();
				}
			}catch( Throwable e ){
			}
		}
	}

	private void 
	method_Torrent_Start_Now(
			Map args, 
			Map result) 
	throws IOException 
	{
		checkUpdatePermissions();
		
		Object	ids = args.get( "ids" );

		List<DownloadStub>	downloads = getDownloads( ids, false );

		for ( DownloadStub download_stub: downloads ){
			
			try{
				Download download = destubbify( download_stub );
				
				download.startDownload(true);

			}catch( Throwable e ){
			}
		}
	}

	private void
	processVuzeFileAdd(
		final 	Map args, 
		Map 	result )
	
		throws IOException, TextualException
	{
		checkUpdatePermissions();

		VuzeFileHandler vfh = VuzeFileHandler.getSingleton();

		VuzeFile vf = null;
		
		String url = (String) args.get( "filename" );

		Throwable last_error = null;
				
		if ( url != null ){

			try{
				File f = new File( new URI( url ));
				
				if ( f.exists()){
					
					vf = vfh.loadVuzeFile( f );
					
					if ( vf == null ){
						
						throw( new TextualException( "Decode failed - invalid Vuze file" ));
					}
				}
			}catch( Throwable e ){
				
				last_error = e;
			}
			
			if ( vf == null && last_error == null ){
				
				try{
					vf = vfh.loadVuzeFile( new ResourceDownloaderFactoryImpl().create( new URL( url )).download());
					
				}catch( Throwable e ){
					
					last_error = e;
				}
			}
		}
		
		if ( vf == null && last_error == null ){
			
			try{
				String metainfoString = (String) args.get("metainfo");
	
				byte[]	metainfoBytes = null;
			
				if ( metainfoString != null ){
			
					metainfoBytes = Base64.decode( metainfoString.replaceAll("[\r\n]+", "") );
							
					vf = vfh.loadVuzeFile( metainfoBytes );
				
					if ( vf == null ){
						
						throw( new TextualException( "Decode failed - invalid Vuze file" ));
					}
				}else{
					
					throw( new TextualException( "Missing parameter" ));
				}
			}catch( Throwable e ){
				
				last_error = e;
			}
		}
		
		if ( vf != null ){
			
  			VuzeFileComponent[] comps = vf.getComponents();

			for ( VuzeFileComponent comp: comps ){
				
				if ( comp.getType() != VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE ){
					
					throw( new TextualException( "Unsupported Vuze File component type: " + comp.getTypeName()));
				}
			}
			
  			vfh.handleFiles( new VuzeFile[]{ vf }, VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE );
  					  			
  			String added_templates = "";
  					
  			for ( VuzeFileComponent comp: comps ){
  				
  				if ( comp.isProcessed()){
  				
  					Engine e = (Engine)comp.getData( Engine.VUZE_FILE_COMPONENT_ENGINE_KEY );
  					
  					if ( e != null ){
  						
  						added_templates += (added_templates==""?"":", ") + e.getName();
  					}
  				}
  			}
  			
  			result.put( "msg", "Search templates added: " + added_templates );
  			
		}else{
			
			if ( last_error == null ){
				
				throw( new TextualException( "Unspecified error occurred" ));
				
			}else{
				
				if ( last_error instanceof TextualException ){
					
					throw((TextualException)last_error);
					
				}else{
				
					throw( new TextualException( "Vuze file addition failed: " + Debug.getNestedExceptionMessage( last_error )));
				}
			}
		}
	}
	
	private void 
	method_Torrent_Add(
		final Map args, 
		Map result,
		boolean xmlEscape) 
				
		throws IOException, DownloadException, TextualException
 {
		/*
		   Request arguments:

		   key                  | value type & description
		   ---------------------+-------------------------------------------------
		   "cookies"            | string      pointer to a string of one or more cookies.
		   "download-dir"       | string      path to download the torrent to
		   "filename"           | string      filename or URL of the .torrent file
		   "metainfo"           | string      base64-encoded .torrent content
		   "paused"             | boolean     if true, don't start the torrent
		   "peer-limit"         | number      maximum number of peers
		   "bandwidthPriority"  | number      torrent's bandwidth tr_priority_t 
		   "files-wanted"       | array       indices of file(s) to download
		   "files-unwanted"     | array       indices of file(s) to not download
		   "priority-high"      | array       indices of high-priority file(s)
		   "priority-low"       | array       indices of low-priority file(s)
		   "priority-normal"    | array       indices of normal-priority file(s)

		   Either "filename" OR "metainfo" MUST be included.
		   All other arguments are optional.

		 	additional vuze specific parameters
			
			 "vuze_category"	| string (optional category name)
			 "vuze_tags"		| array  (optional list of tags)
			 "name"	        | string (optional friendly name to use instead of url/hash)
			 
		   The format of the "cookies" should be NAME=CONTENTS, where NAME is the
		   cookie name and CONTENTS is what the cookie should contain.
		   Set multiple cookies like this: "name1=content1; name2=content2;" etc. 
		   <http://curl.haxx.se/libcurl/c/curl_easy_setopt.html#CURLOPTCOOKIE>

		   Response arguments: on success, a "torrent-added" object in the
		                       form of one of 3.3's tr_info objects with the
		                       fields for id, name, and hashString.
		 */
		checkUpdatePermissions();

		String metainfoString = (String) args.get("metainfo");

		byte[]	metainfoBytes = null;
		
		if ( metainfoString != null ){
		
			metainfoBytes = Base64.decode( metainfoString.replaceAll("[\r\n]+", "") );
			//metainfoBytes = Base64.decode( metainfoString );
		
			VuzeFileHandler vfh = VuzeFileHandler.getSingleton();
	
			if ( vfh != null ){
				
				VuzeFile vf = vfh.loadVuzeFile( metainfoBytes );
	
				if ( vf != null ){
					
		  			VuzeFileComponent[] comps = vf.getComponents();

					for ( VuzeFileComponent comp: comps ){
						
						if ( comp.getType() != VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE ){
							
							throw( new TextualException( "Unsupported Vuze File component type: " + comp.getTypeName()));
						}
					}
					
		  			vfh.handleFiles( new VuzeFile[]{ vf }, VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE );
		  					  			
		  			String added_templates = "";
		  					
		  			for ( VuzeFileComponent comp: comps ){
		  				
		  				if ( comp.isProcessed()){
		  				
		  					Engine e = (Engine)comp.getData( Engine.VUZE_FILE_COMPONENT_ENGINE_KEY );
		  					
		  					if ( e != null ){
		  						
		  						added_templates += (added_templates==""?"":", ") + e.getName();
		  					}
		  				}
		  			}
		  			
		  			if ( added_templates.length() == 0 ){
		  				
		  				throw( new TextualException( "No search template(s) added" ));
		  				
		  			}else{
		  				
		  				throw( new TextualException( "Installed search template(s): " + added_templates ));
		  			}
				}
			}
		}
		
		Torrent 		torrent = null;
		DownloadStub	download = null;
		
		String url = (String) args.get("filename");
				
		final boolean add_stopped = getBoolean(args.get("paused"));
		
		String download_dir = (String) args.get("download-dir");
		
		final File file_Download_dir = download_dir == null ? null : new File(download_dir);

		// peer-limit not used
		//getNumber(args.get("peer-limit"), 0);

		// bandwidthPriority not used
		//getNumber(args.get("bandwidthPriority"), TransmissionVars.TR_PRI_NORMAL);
				
		final DownloadWillBeAddedListener add_listener =
			new DownloadWillBeAddedListener() {
				@Override
				public void initialised(Download download) {
					int numFiles = download.getDiskManagerFileCount();
					List files_wanted = getList(args.get("files-wanted"));
					List files_unwanted = getList(args.get("files-unwanted"));
	
					boolean[] toDelete = new boolean[numFiles]; // all false
	
					int numWanted = files_wanted.size();
					if (numWanted != 0 && numWanted != numFiles) {
						// some wanted -- so, set all toDelete and reset ones in list
						Arrays.fill(toDelete, true);
						for (Object oWanted : files_wanted) {
							int idx = getNumber(oWanted, -1).intValue();
							if (idx >= 0 && idx < numFiles) {
								toDelete[idx] = false;
							}
						}
					}
					for (Object oUnwanted : files_unwanted) {
						int idx = getNumber(oUnwanted, -1).intValue();
						if (idx >= 0 && idx < numFiles) {
							toDelete[idx] = true;
						}
					}
	
					for (int i = 0; i < toDelete.length; i++) {
						if (toDelete[i]) {
							download.getDiskManagerFileInfo(i).setDeleted(true);
						}
					}
	
					List priority_high = getList(args.get("priority-high"));
					for (Object oHighPriority : priority_high) {
						int idx = getNumber(oHighPriority, -1).intValue();
						if (idx >= 0 && idx < numFiles) {
							download.getDiskManagerFileInfo(idx).setNumericPriority(
									DiskManagerFileInfo.PRIORITY_HIGH);
						}
					}
					List priority_low = getList(args.get("priority-low"));
					for (Object oLowPriority : priority_low) {
						int idx = getNumber(oLowPriority, -1).intValue();
						if (idx >= 0 && idx < numFiles) {
							download.getDiskManagerFileInfo(idx).setNumericPriority(
									DiskManagerFileInfo.PRIORITY_LOW);
						}
					}
					// don't need priority-normal if they are normal by default.
					
					// handle initial categories/tags
					
					try{
						String vuze_category = (String)args.get( "vuze_category" );
	
						if ( vuze_category != null ){
							
							vuze_category = vuze_category.trim();
							
							if ( vuze_category.length() > 0 ){
								
								TorrentAttribute	ta_category	= plugin_interface.getTorrentManager().getAttribute(TorrentAttribute.TA_CATEGORY);
								
								download.setAttribute( ta_category, vuze_category );
							}
						}
						
						List<String>	vuze_tags = (List<String>)args.get( "vuze_tags" );
						
						if ( vuze_tags != null ){
							
							TagManager tm = TagManagerFactory.getTagManager();
							
							if ( tm.isEnabled()){
								
								TagType tt = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL );
								
								for ( String tag_name: vuze_tags ){
									
									addTagToDownload(download, tag_name, tt);
								}
							}
						}				
					}catch( Throwable e ){
						
						e.printStackTrace();
					}
				}
			};
		
		
		TorrentManager torrentManager = plugin_interface.getTorrentManager();
		
		boolean duplicate = false;

		if ( metainfoBytes != null ){
			try {
				torrent = torrentManager.createFromBEncodedData( metainfoBytes);
				
				com.biglybt.pif.download.DownloadManager dm = plugin_interface.getDownloadManager();
				download = dm.getDownload( torrent );
				duplicate = download != null;
				
			} catch (Throwable e) {

				e.printStackTrace();
				
				//System.err.println("decode of " + new String(Base64.encode(metainfoBytes), "UTF8"));

				throw (new IOException("torrent download failed: "
						+ Debug.getNestedExceptionMessage(e)));
			}
		} else if (url == null) {

			throw (new IOException("url missing"));

		} else {

			url = url.trim().replaceAll(" ", "%20");

			// hack due to core bug - have to add a bogus arg onto magnet uris else they fail to parse

			String lc_url = url.toLowerCase( Locale.US );
			
			if ( lc_url.startsWith("magnet:")) {

				url += "&dummy_param=1";
				
			} else if (!lc_url.startsWith("http")) {
				
				url = UrlUtils.parseTextForURL(url, true, true);
			}
			
			byte[] hashFromMagnetURI = getHashFromMagnetURI(url);
			if (hashFromMagnetURI != null) {
				com.biglybt.pif.download.DownloadManager dm = plugin_interface.getDownloadManager();
				download = dm.getDownload(hashFromMagnetURI);
				duplicate = download != null;
			}

			if (download == null) {
			URL torrent_url;
			try {
			 torrent_url = new URL(url);
			} catch (MalformedURLException mue) {
				throw new TextualException("The torrent URI was not valid");
			}
			
			try{
				final TorrentDownloader dl = torrentManager.getURLDownloader(torrent_url, null, null);

				Object cookies = args.get("cookies");
				
				if ( cookies != null ){
					
					dl.setRequestProperty("URL_Cookie", cookies);
				}

				boolean is_magnet = torrent_url.getProtocol().equalsIgnoreCase( "magnet" );
				
				if ( is_magnet ){
						
					TimerEvent	magnet_event = null;

					final Object[]	f_result = { null };
					
					try{
						final AESemaphore sem = new AESemaphore( "magnetsem" );
						final URL f_torrent_url = torrent_url;
						final String f_name = (String) args.get("name");
						magnet_event = SimpleTimer.addEvent(
							"magnetcheck",
							SystemTime.getOffsetTime( 10*1000 ),
							new TimerEventPerformer() 
							{
								@Override
								public void
								perform(
									TimerEvent event )
								{
									synchronized( f_result ){
										
										if ( f_result[0] != null ){
											
											return;
										}
										
										MagnetDownload magnet_download = new MagnetDownload( f_torrent_url, f_name );
										
										byte[]	hash = magnet_download.getTorrentHash();
										
										synchronized( magnet_downloads ){
											
											boolean	duplicate = false;
											
											Iterator<MagnetDownload> it =  magnet_downloads.iterator();
											
											while( it.hasNext()){
												
												MagnetDownload md = it.next();
												
												if ( hash.length > 0 && Arrays.equals( hash, md.getTorrentHash())){
													
													if ( md.getError() == null ){
														
														duplicate = true;
													
														magnet_download = md;
													
														break;
														
													}else{
														
														it.remove();
														
														addRecentlyRemoved( md );
													}
												}
											}
											
											if ( !duplicate ){
											
												magnet_downloads.add( magnet_download );
											}
										}
										
										f_result[0] = magnet_download;
									}
							
									sem.release();
								}
							});
											
						new AEThread2( "magnetasync" )
						{
							@Override
							public void
							run()
							{
								try{
									Torrent torrent = dl.download(Constants.DEFAULT_ENCODING);
									
									synchronized( f_result ){
										
										if ( f_result[0] == null ){
										
											f_result[0] = torrent;
											
										}else{
											
											MagnetDownload md = (MagnetDownload)f_result[0];										
											
											boolean	already_removed;
											
											synchronized( magnet_downloads ){
												
												already_removed = !magnet_downloads.remove( md );
											}
											
											if ( !already_removed ){
												
												addRecentlyRemoved( md );
											
												addTorrent( torrent, file_Download_dir, add_stopped, add_listener );
											}
										}
									}
								}catch( Throwable e ){
									
									synchronized( f_result ){
										
										if ( f_result[0] == null ){
										
											f_result[0] = e;
										
										}else{
										
											MagnetDownload md = (MagnetDownload)f_result[0];
											
											md.setError( e );
										}
									}
								}finally{
									
									sem.release();
								}
							}
						}.start();
						
						sem.reserve();
						
						Object res;
						
						synchronized( f_result ){
							
							res = f_result[0];
						}
						
						if ( res instanceof Torrent ){
							
							torrent = (Torrent)res;
							
						}else if ( res instanceof Throwable ){
							
							throw((Throwable)res);
							
						}else{
							
							download 	= (MagnetDownload)res;
							torrent		= null;
						}
					}finally{
						
						if ( magnet_event != null ){
							
							magnet_event.cancel();
						}
					}
				}else{
					
					torrent = dl.download(Constants.DEFAULT_ENCODING);
				}
			}catch( Throwable e ){

				e.printStackTrace();

				throw( new IOException( Debug.getNestedExceptionMessage( e )));
			}
			}
		}

		if ( download == null ){

			download = addTorrent( torrent, file_Download_dir, add_stopped, add_listener );
		}
		
		Map<String, Object> torrent_details = new HashMap<String, Object>();

		torrent_details.put("id", new Long(getID(download, true)));
		torrent_details.put("name", xmlEscape ? escapeXML(download.getName()) : download.getName());
		torrent_details.put(TransmissionVars.FIELD_TORRENT_HASH,
				ByteFormatter.encodeString(download.getTorrentHash()));

		result.put(duplicate ? "torrent-duplicate" : "torrent-added", torrent_details);
	}
	
	private byte[] getHashFromMagnetURI(String magnetURI) {
		Pattern patXT = Pattern.compile("xt=urn:(?:btih|sha1):([^&]+)");
		Matcher matcher = patXT.matcher(magnetURI);
		if (matcher.find()) {
			return UrlUtils.decodeSHA1Hash(matcher.group(1));
		}
		return null;
	}

	private Map
	method_Torrent_Get(
		TrackerWebPageRequest request,
		String		session_id,
		Map 		args,
		Map 		result)
	{
		
		// When "file_indexes" key is present, returns:
		// NOTE: Array position does not equal file index!  Use "index" key!
		// {
		// 	torrents : [
		//               {
		//                 <key> : <value>, 
		//                 files : 
		//                         [ 
		//                           { 
		//                             "index": <file-index>, 
		//                             <other-fields>: <other-values>
		//                           },
		//                          <more file maps>
		//                         ]
		//                },
		//               <more keys> : <move values>
		//             ]
		// }

		List<String>	fields = (List<String>)args.get( "fields" );
		
		if ( fields == null ){
			
			fields = new ArrayList();
		}
		
		Object	ids = args.get( "ids" );
		
		boolean is_recently_active = handleRecentlyRemoved( session_id, args, result );
		
		List<DownloadStub>	downloads = getDownloads( ids, true );
		
		List<String> file_fields = (List<String>) args.get("file-fields");
		if (file_fields != null) {
			Collections.sort(file_fields);
		}
				
		Map<Long,Map>	torrent_info = new LinkedHashMap<Long, Map>();
		
		String agent = MapUtils.getMapString(request.getHeaders(), "User-Agent", "");
		boolean xmlEscape = agent.startsWith("Mozilla/");

		for ( DownloadStub download_stub: downloads ){
			if (download_stub.isStub()) {
				method_Torrent_Get_Stub(request, args, fields, torrent_info,
						download_stub, file_fields, xmlEscape);
			} else {
				method_Torrent_Get_NonStub(request, args, fields, torrent_info,
						(Download) download_stub, file_fields, xmlEscape);
			}
		} // for downloads
		
		if ( is_recently_active ){
			
				// just return the latest diff for this session
				// we could possibly, in theory, update the cache for all calls to this method, not just the 'recently active' calls
				// but I don't trust the client enough atm to behave correctly
			
			synchronized( session_torrent_info_cache ){
				
				if ( session_torrent_info_cache.size() > 8 ){
					
					session_torrent_info_cache.clear();
				}
				
				Map<Long,String> torrent_info_cache = session_torrent_info_cache.get( session_id );
				
				if ( torrent_info_cache == null ){
					
					torrent_info_cache = new HashMap<Long, String>();
					
					session_torrent_info_cache.put( session_id, torrent_info_cache );
				}
				
				List<Long>	same = new ArrayList<Long>();
				
				for ( Map.Entry<Long,Map> entry: torrent_info.entrySet()){
					
					long	id 		= entry.getKey();
					Map		torrent = entry.getValue();
					
					String current = JSONUtils.encodeToJSON( torrent );
					
					String prev = torrent_info_cache.get( id );
					
					if ( prev != null && prev.equals( current )){
						
						same.add( id );
						
					}else{
						
						torrent_info_cache.put( id, current );
					}
				}
				
				if ( same.size() > 0 ){
					
						// System.out.println( "same info: " + same.size() + " of " + torrent_info.size());
					
					for ( long id: same ){
						
						torrent_info.remove( id );
					}
				}
			}
		}
		
		List<Map>	torrents = new ArrayList<Map>();
		
		result.put( "torrents", torrents );

		torrents.addAll( torrent_info.values());
				
		return result;
	}

	private void method_Torrent_Get_NonStub(
			TrackerWebPageRequest request,
			Map args,
			List<String> fields,
			Map<Long, Map> torrent_info,
			Download download,
			List<String> file_fields,
			boolean xmlEscape)
	{

		Torrent t = download.getTorrent();

		if (t == null) {
			// Can't do this.. download is a nullstate, which doesn't store
			// Attributes, and getID relies on that
			//t = new TorrentBlank(download);
			return;
		}

		long download_id = getID(download, true);

		DownloadManager core_download = PluginCoreUtils.unwrap(download);

		PEPeerManager pm = core_download.getPeerManager();

		DownloadStats stats = download.getStats();

		Map torrent = new HashMap(fields.size() + 8);

		torrent_info.put(download_id, torrent);

		int peers_from_us = 0;
		int peers_to_us = 0;

		if (pm != null) {

			List<PEPeer> peers = pm.getPeers();

			for (PEPeer peer : peers) {

				PEPeerStats pstats = peer.getStats();

				if (pstats.getDataReceiveRate() > 0) {

					peers_to_us++;
				}

				if (pstats.getDataSendRate() > 0) {

					peers_from_us++;
				}
			}
		}

		for (String field : fields) {

			Object value = null;

			if (field.equals("activityDate")) {
				// RPC v0
				// activityDate                | number                      | tr_stat
				value = torrentGet_activityDate(core_download, false);

			} else if (field.equals("activityDateRelative")) {
				// RPC v0
				// activityDate                | number                      | tr_stat
				value = torrentGet_activityDate(core_download, true);

			} else if (field.equals("addedDate")) {
				// RPC v0
				// addedDate                   | number                      | tr_stat
				/** When the torrent was first added. */
				value = core_download.getDownloadState().getLongParameter(
						DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME) / 1000;

			} else if (field.equals("announceURL")) {
				// Removed in RPC v7

				value = t.getAnnounceURL().toExternalForm();

			} else if (field.equals("bandwidthPriority")) {
				// RPC v5: Not Supported
				// bandwidthPriority           | number                      | tr_priority_t
				/** torrent's bandwidth priority. */
				value = TransmissionVars.TR_PRI_NORMAL;

			} else if (field.equals("comment")) {
				// RPC v0
				// comment                     | string                      | tr_info

				value = t.getComment();

			} else if (field.equals("corruptEver")) {
				// RPC v0 TODO: Do we want just hash fails?
				// corruptEver                 | number                      | tr_stat
				/** 
				 * Byte count of all the corrupt data you've ever downloaded for
				 * this torrent. If you're on a poisoned torrent, this number can
				 * grow very large. 
				 */
				value = stats.getDiscarded() + stats.getHashFails();

			} else if (field.equals("creator")) {
				// RPC v0
				// creator                     | string                      | tr_info
				value = t.getCreatedBy();

			} else if (field.equals("dateCreated")) {

				// RPC v0
				// dateCreated                 | number                      | tr_info
				value = t.getCreationDate();

			} else if (field.equals("desiredAvailable")) {
				// RPC v0 TODO: stats.getRemainingAvailable() ?
				// desiredAvailable            | number                      | tr_stat
				/** 
				 * Byte count of all the piece data we want and don't have yet,
				 * but that a connected peer does have. [0...leftUntilDone] 
				 */
				value = core_download.getStats().getRemainingExcludingDND();

			} else if (field.equals("doneDate")) {
				// RPC v0
				// doneDate                    | number                      | tr_stat
				/** When the torrent finished downloading. */
				if (core_download.isDownloadComplete(false)) {
					value = core_download.getDownloadState().getLongParameter(
							DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME) / 1000;
				} else {
					// TODO: Verify what value to send when not complete
					value = 0;
				}

			} else if (field.equals("downloadDir")) {
				// RPC v4
				// downloadDir                 | string                      | tr_torrent

				if (t.isSimpleTorrent()) {
					value = new File(download.getSavePath()).getParent();
				} else {
					value = download.getSavePath();
				}

			} else if (field.equals("downloadedEver")) {
				// RPC v0
				// downloadedEver              | number                      | tr_stat

				/** 
				 * Byte count of all the non-corrupt data you've ever downloaded
				 * for this torrent. If you deleted the files and downloaded a second
				 * time, this will be 2*totalSize.. 
				 */
				value = stats.getDownloaded();

			} else if (field.equals("downloadLimit")
					|| field.equals("speed-limit-down")) {
				// RPC v5 (alternate is from 'set' prior to v5 -- added for rogue clients)
				// downloadLimit               | number                      | tr_torrent

				/** maximum download speed (KBps) */
				value = download.getMaximumDownloadKBPerSecond();

			} else if (field.equals("downloadLimited")
					|| field.equals("speed-limit-down-enabled")) {
				// RPC v5 (alternate is from 'set' prior to v5 -- added for rogue clients)
				// downloadLimited             | boolean                     | tr_torrent

				/** true if "downloadLimit" is honored */
				value = download.getDownloadRateLimitBytesPerSecond() > 0;

			} else if (field.equals("error")) {
				// RPC v0
				// error                       | number                      | tr_stat
				/** Defines what kind of text is in errorString. TR_STAT_* */

				value = torrentGet_error(core_download, download);

			} else if (field.equals("errorString")) {
				// RPC v0
				// errorString                 | string                      | tr_stat

				value = torrentGet_errorString(core_download, download);

			} else if (field.equals("eta")) {
				// RPC v0
				// eta                         | number                      | tr_stat

				value = torrentGet_eta(core_download, download, stats);

			} else if (field.equals("etaIdle")) {
				// RPC v15
				/** If seeding, number of seconds left until the idle time limit is reached. */
				// TODO: No idea what etaIdle description means! What happens at idle time?

				value = TransmissionVars.TR_ETA_UNKNOWN;

			} else if (field.equals("files")) {
				// RPC v0

				String host = (String)request.getHeaders().get( "host" );

				value = torrentGet_files(host, download, download_id, file_fields, args);
				
				// One hash for all files.  This won't work when our file list is a partial
				//if (value instanceof Collection) {
				//	torrent.put("files-hc", longHashSimpleList((Collection<?>) value));
				//}

			} else if (field.equals("fileStats")) {
				// RPC v5

				value = torrentGet_fileStats(download, file_fields, args);

			} else if (field.equals(TransmissionVars.FIELD_TORRENT_HASH)) {
				// RPC v0
				// hashString                  | string                      | tr_info
				value = ByteFormatter.encodeString(t.getHash());

			} else if (field.equals("haveUnchecked")) {
				// haveUnchecked               | number                      | tr_stat
				/** Byte count of all the partial piece data we have for this torrent.
				As pieces become complete, this value may decrease as portions of it
				are moved to `corrupt' or `haveValid'. */
				// TODO: set when ST_CHECKING?
				value = 0;

			} else if (field.equals("haveValid")) {
				// haveValid                   | number                      | tr_stat
				/** Byte count of all the checksum-verified data we have for this torrent.
				  */
				value = stats.getDownloaded();

			} else if (field.equals("honorsSessionLimits")) {
				// TODO RPC v5
				// honorsSessionLimits         | boolean                     | tr_torrent
				/** true if session upload limits are honored */
				value = false;

			} else if (field.equals("id")) {
				// id                          | number                      | tr_torrent
				value = download_id;

			} else if (field.equals("isFinished")) {
				// RPC v9: TODO
				// isFinished                  | boolean                     | tr_stat
				/** A torrent is considered finished if it has met its seed ratio.
				As a result, only paused torrents can be finished. */

				value = false;

			} else if (field.equals("isPrivate")) {
				// RPC v0
				// isPrivate                   | boolean                     | tr_torrent
				value = t.isPrivate();

			} else if (field.equals("isStalled")) {
				// RPC v14
				// isStalled                   | boolean                     | tr_stat

				value = torrentGet_isStalled(download);

			} else if (field.equals("leechers")) {
				// Removed in RPC v7
				value = pm == null ? 0 : pm.getNbPeers();

			} else if (field.equals("leftUntilDone")) {
				// RPC v0
				// leftUntilDone               | number                      | tr_stat

				/** Byte count of how much data is left to be downloaded until we've got
				all the pieces that we want. [0...tr_info.sizeWhenDone] */

				value = core_download.getStats().getRemainingExcludingDND();

			} else if (field.equals("magnetLink")) {
				// TODO RPC v7
				// magnetLink                  | number                      | n/a
				// NOTE: I assume spec is wrong and it's a string..

				value = UrlUtils.getMagnetURI(download);

			} else if (field.equals("manualAnnounceTime")) {
				// manualAnnounceTime          | number                      | tr_stat
				// spec is time_t, although it should be relative time. :(

				value = torrentGet_manualAnnounceTime(core_download);

			} else if (field.equals("maxConnectedPeers")) {
				// maxConnectedPeers           | number                      | tr_torrent
				// TODO: Some sort of Peer Limit (tr_torrentSetPeerLimit )

			} else if (field.equals("metadataPercentComplete")) {
				// RPC v7: TODO
				// metadataPercentComplete     | double                      | tr_stat
				/** 
				 * How much of the metadata the torrent has.
				 * For torrents added from a .torrent this will always be 1.
				 * For magnet links, this number will from from 0 to 1 as the metadata is downloaded.
				 * Range is [0..1] 
				 */
				// RPC v7
				value = 1.0f;

			} else if (field.equals("name")) {

				value = download.getName();

			} else if (field.equals("peer-limit")) {
				// peer-limit                  | number                      | tr_torrent
				// TODO
				/** how many peers this torrent can connect to */
				value = -1;

			} else if (field.equals("peers")) {
				// RPC v2

				value = torrentGet_peers(core_download);

			} else if (field.equals("peersConnected")) {
				// peersConnected              | number                      | tr_stat

				/** Number of peers that we're connected to */
				value = pm == null ? 0 : pm.getNbPeers() + pm.getNbSeeds();

			} else if (field.equals("peersFrom")) {

				value = torrentGet_peersFrom(pm);

			} else if (field.equals("peersGettingFromUs")) {
				// peersGettingFromUs          | number                      | tr_stat

				value = peers_from_us;

			} else if (field.equals("peersSendingToUs")) {
				// peersSendingToUs            | number                      | tr_stat

				value = peers_to_us;

			} else if (field.equals("percentDone")) {
				// RPC v5
				// percentDone                 | double                      | tr_stat
				/** 
				 * How much has been downloaded of the files the user wants. This differs
				 * from percentComplete if the user wants only some of the torrent's files.
				 * Range is [0..1]
				 */
				
				value = core_download.getStats().getPercentDoneExcludingDND() / 1000.0f;

			} else if (field.equals("pieces")) {
				// RPC v5
				value = torrentGet_pieces(core_download);
			} else if (field.equals("pieceCount")) {
				// pieceCount                  | number                      | tr_info
				value = t.getPieceCount();

			} else if (field.equals("pieceSize")) {
				// pieceSize                   | number                      | tr_info
				value = t.getPieceSize();

			} else if (field.equals("priorities")) {

				value = torrentGet_priorities(download);

			} else if (field.equals("queuePosition")) {
				// RPC v14
				// "queuePosition"       | number     position of this torrent in its queue [0...n)

				value = core_download.getPosition();

			} else if (field.equals("rateDownload")) {
				// rateDownload (B/s)          | number                      | tr_stat
				value = stats.getDownloadAverage();

			} else if (field.equals("rateUpload")) {
				// rateUpload (B/s)            | number                      | tr_stat
				value = stats.getUploadAverage();

			} else if (field.equals("recheckProgress")) {
				// recheckProgress             | double                      | tr_stat
				value = torrentGet_recheckProgress(core_download, stats);

			} else if (field.equals("secondsDownloading")) {
				// secondsDownloading          | number                      | tr_stat
				/** Cumulative seconds the torrent's ever spent downloading */
				value = stats.getSecondsDownloading();

			} else if (field.equals("secondsSeeding")) {
				// secondsSeeding              | number                      | tr_stat
				/** Cumulative seconds the torrent's ever spent seeding */
				// TODO: Want "only seeding" time, or seeding time (including downloading time)? 
				value = stats.getSecondsOnlySeeding();

			} else if (field.equals("seedIdleLimit")) {
				// RPC v10
				// "seedIdleLimit"       | number     torrent-level number of minutes of seeding inactivity
				value = (int) stats.getSecondsSinceLastUpload() / 60;

			} else if (field.equals("seedIdleMode")) {
				// RPC v10: Not used, always TransmissionVars.TR_IDLELIMIT_GLOBAL
				// "seedIdleMode"        | number     which seeding inactivity to use.  See tr_inactvelimit
				value = TransmissionVars.TR_IDLELIMIT_GLOBAL;

			} else if (field.equals("seedRatioLimit")) {
				// RPC v5
				// "seedRatioLimit"      | double     torrent-level seeding ratio

				value = COConfigurationManager.getFloatParameter("Stop Ratio");

			} else if (field.equals("seedRatioMode")) {
				// RPC v5: Not used, always Global
				// seedRatioMode               | number                      | tr_ratiolimit
				value = TransmissionVars.TR_RATIOLIMIT_GLOBAL;

			} else if (field.equals("sizeWhenDone")) {
				// sizeWhenDone                | number                      | tr_stat
				/** 
				 * Byte count of all the piece data we'll have downloaded when we're done,
				 * whether or not we have it yet. This may be less than tr_info.totalSize
				 * if only some of the torrent's files are wanted.
				 * [0...tr_info.totalSize] 
				 **/
				value = core_download.getStats().getSizeExcludingDND();

			} else if (field.equals("startDate")) {
				/** When the torrent was last started. */
				value = stats.getTimeStarted() / 1000;

			} else if (field.equals("status")) {

				value = torrentGet_status(download);

			} else if (field.equals("trackers")) {

				String agent = MapUtils.getMapString(request.getHeaders(), "User-Agent", "");
				boolean hack = agent.contains("httpok"); // Torrnado
				value = torrentGet_trackers(core_download, hack);

			} else if (field.equals("trackerStats")) {
				// RPC v7

				value = torrentGet_trackerStats(core_download);

			} else if (field.equals("totalSize")) {

				value = t.getSize();

			} else if (field.equals("torrentFile")) {
				// torrentFile                 | string                      | tr_info
				/** Path to torrent **/
				value = core_download.getTorrentFileName();

			} else if (field.equals("uploadedEver")) {
				// uploadedEver                | number                      | tr_stat
				value = stats.getUploaded();

			} else if (field.equals("uploadLimit") || field.equals("speed-limit-up")) {
				// RPC v5 (alternate is from 'set' prior to v5 -- added for rogue clients)

				/** maximum upload speed (KBps) */
				int bps = download.getUploadRateLimitBytesPerSecond();
				value = bps <= 0 ? bps : (bps < 1024 ? 1 : bps / 1024);

			} else if (field.equals("uploadLimited")
					|| field.equals("speed-limit-up-enabled")) {
				// RPC v5 (alternate is from 'set' prior to v5 -- added for rogue clients)

				/** true if "uploadLimit" is honored */
				value = download.getUploadRateLimitBytesPerSecond() > 0;

			} else if (field.equals("uploadRatio")) {
				// uploadRatio                 | double                      | tr_stat
				int shareRatio = stats.getShareRatio();
				value = shareRatio <=0 ? shareRatio : stats.getShareRatio() / 1000.0;

			} else if (field.equals("wanted")) {

				value = torrentGet_wanted(download);

			} else if (field.equals("webseeds")) {
				value = torrentGet_webSeeds(t);

			} else if (field.equals("webseedsSendingToUs")) {
				value = torrentGet_webseedsSendingToUs(core_download);

			} else if (field.equals("trackerSeeds")) {
				// Vuze Specific?
				DownloadScrapeResult scrape = download.getLastScrapeResult();
				value = new Long(scrape == null ? 0 : scrape.getSeedCount());

			} else if (field.equals("trackerLeechers")) {
				// Vuze Specific?
				DownloadScrapeResult scrape = download.getLastScrapeResult();
				value = new Long(scrape == null ? 0 : scrape.getNonSeedCount());

			} else if (field.equals("speedLimitDownload")) {
				// Vuze Specific?
				value = new Long(download.getDownloadRateLimitBytesPerSecond());
			} else if (field.equals("speedLimitUpload")) {
				// Vuze Specific?
				value = new Long(download.getUploadRateLimitBytesPerSecond());
			} else if (field.equals("seeders")) {
				// Removed in RPC v7
				value = pm == null ? -1 : pm.getNbSeeds();

			} else if (field.equals("swarmSpeed")) {
				// Removed in RPC v7
				value = new Long(core_download.getStats().getTotalAveragePerPeer());
			} else if (field.equals("announceResponse")) {
				// Removed in RPC v7

				TRTrackerAnnouncer trackerClient = core_download.getTrackerClient();
				if (trackerClient != null) {
					value = trackerClient.getStatusString();
				} else {
					value = "";
				}

			} else if (field.equals("lastScrapeTime")) {
				// Unsure of wanted format
				// Removed in v7

				value = core_download.getTrackerTime();

			} else if (field.equals("scrapeURL")) {
				// Removed in v7
				value = "";
				TRTrackerScraperResponse trackerScrapeResponse = core_download.getTrackerScrapeResponse();
				if (trackerScrapeResponse != null) {
					URL url = trackerScrapeResponse.getURL();
					if (url != null) {
						value = url.toString();
					}
				}

			} else if (field.equals("nextScrapeTime")) {
				// Removed in v7

				// Unsure of wanted format
				TRTrackerAnnouncer trackerClient = core_download.getTrackerClient();
				if (trackerClient != null) {
					value = trackerClient.getTimeUntilNextUpdate();
				} else {
					value = 0;
				}

			} else if (field.equals("nextAnnounceTime")) {
				// Removed in v7

				// Unsure of wanted format
				TRTrackerAnnouncer trackerClient = core_download.getTrackerClient();
				if (trackerClient != null) {
					value = trackerClient.getTimeUntilNextUpdate();
				} else {
					value = 0;
				}

			} else if (field.equals("downloadLimitMode")
					|| field.equals("uploadLimitMode")) {
				// RPC < v5 -- Not supported -- ignore

			} else if (field.equals("downloaders")
					|| field.equals("lastAnnounceTime") || field.equals("lastScrapeTime")
					|| field.equals("scrapeResponse") || field.equals("timesCompleted")) {
				// RPC < v7 -- Not Supported -- ignore

			} else if (field.equals("peersKnown")) {
				// RPC < v13 -- Not Supported -- ignore

			} else if (field.equals("fileCount")) {
				// azRPC

				value = core_download.getNumFileInfos();

			} else if (field.equals("speedHistory")) {
				// azRPC
				
				DownloadManagerStats core_stats = core_download.getStats();
				core_stats.setRecentHistoryRetention(true);
				
				// TODO
				// [0] send [1] receive [2] swarm
				int[][] recentHistory = core_stats.getRecentHistory();
				long now = SystemTime.getCurrentTime();
				
				long sinceSecs = getNumber(args.get("speedHistorySinceSecs"), 0).longValue();
				
				long since = now - (sinceSecs * 1000);
				
				long curEntryTime = now - (recentHistory.length *1000);
				
				List listHistory = new ArrayList();
				for (int i = 0; i < recentHistory.length; i++) {
					if (curEntryTime > since) {
  					int[] entry = recentHistory[i];
  					Map mapHistory = new HashMap(3);
  					mapHistory.put("upload", entry[0]);
  					mapHistory.put("download", entry[1]);
  					mapHistory.put("swarm", entry[2]);
					
  					listHistory.add(entry);
					}
					
					curEntryTime += 1000;
				}
				
				value = listHistory;
				
				/*
				 * [
				 *   {
				 *   	upload: <upload speed>
				 *   	download: <dl speed>
				 *   	swarm: <swarm avg speed>
				 *   }
				 * }
				 */
			} else if (field.equals("tag-uids")) {
				// azRPC
				List<Long> listTags = new ArrayList<Long>();
				
				TagManager tm = TagManagerFactory.getTagManager();
				
				List<Tag> tags = tm.getTagsForTaggable(core_download);
				if (tags == null || tags.isEmpty()) {
					Category catAll = CategoryManager.getCategory(Category.TYPE_ALL);
					if (catAll != null) {
						listTags.add(catAll.getTagUID());
					}
					Category catUncat = CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED);
					if (catUncat != null) {
						listTags.add(catUncat.getTagUID());
					}
				} else {
  				for (Tag tag : tags) {
  					listTags.add(tag.getTagUID());
  				}
				}
				
				value = listTags;

			} else {
				if ( trace_param.getValue() ){
					log("Unhandled get-torrent field: " + field);
				}
			}

			if (value != null) {

				if (xmlEscape && (value instanceof String)) {

					value = escapeXML((String) value);
				}
				torrent.put(field, value);
			}
		} // for fields		
	}

	private void method_Torrent_Get_Stub(
			TrackerWebPageRequest request,
			Map args, 
			List<String> fields,
			Map<Long, Map> torrent_info,
			DownloadStub download_stub,
			List<String> file_fields,
			boolean xmlEscape) 
	{
		
		Map torrent = new HashMap();
		
		long download_id = getID( download_stub, true );
		
		torrent_info.put( download_id, torrent );

		boolean	is_magnet_download = download_stub instanceof MagnetDownload;
		
		long 	status		= 0;
		long 	error 		= TransmissionVars.TR_STAT_OK;
		String 	error_str 	= "";
		String	created_by	= "";
		long	create_date	= 0;
		float	md_comp		= 1.0f;
		
		if ( is_magnet_download ){
			
			MagnetDownload md = (MagnetDownload)download_stub;
			
			TagManager tm = TagManagerFactory.getTagManager();
			
			Throwable e = md.getError();
			
			if ( e == null ){
				
				status = 4;
				
				md_comp		= 0.0f;

				if (fields.contains("tag-uids")) {
  				List listTags = new ArrayList();
  				Tag tag = getTagFromState(Download.ST_DOWNLOADING, false);
  				if (tag != null) {
  					listTags.add(tag.getTagUID());
  				}
  				// 7, "tag.type.ds.act"
  				tag = tm.getTagType(TagType.TT_DOWNLOAD_STATE).getTag(7);
  				if (tag != null) {
  					listTags.add(tag.getTagUID());
  				}
  				
  				torrent.put("tag-uids", listTags);
				}

			}else{
				
				status		= 0;
				
				error 		= TransmissionVars.TR_STAT_LOCAL_ERROR;
				
				Throwable temp = e;
				
				while( temp.getCause() != null ){
					
					temp = temp.getCause();
				}
				
				String last_msg = temp.getMessage();
				
				if ( last_msg != null && last_msg.length() > 0 ){
					
					error_str = last_msg;
					
				}else{
					
					error_str = Debug.getNestedExceptionMessage( e );
				}
				
				String magnet_url = md.getMagnetURL().toExternalForm();
				
				int	pos = error_str.indexOf( magnet_url );
				
				// tidy up the most common error messages by removing magnet uri from them and
				// trimming prefix of 'Error:'
				
				if ( pos != -1 ){
					
					error_str = error_str.substring(0,pos) + error_str.substring( pos+magnet_url.length());
				}
				
				error_str = error_str.trim();
				
				pos = error_str.indexOf( "rror:");
				
				if ( pos != -1 ){
					
					error_str = error_str.substring( pos+5 ).trim();
				}
				
				if ( error_str.length() > 0 ){
					
						// probably not great for right-left languages but derp
					
					error_str = Character.toUpperCase( error_str.charAt(0)) + error_str.substring(1);
				}

				if (fields.contains("tag-uids")) {
					List listTags = new ArrayList();
					Tag tag = getTagFromState(Download.ST_STOPPED, true);
					if (tag != null) {
						listTags.add(tag.getTagUID());
					}
					
					torrent.put("tag-uids", listTags);
				}
			}
			
			created_by	= "Vuze";
			create_date	= md.getCreateTime()/1000;

		}
		
		long	size = download_stub.getTorrentSize();
			
		
		//System.out.println( fields );
		
		Object[][] stub_defs = {
		{ "activityDate", 0 },
		{ "activityDateRelative",0 },
		{ "addedDate", is_magnet_download?create_date:0 },
		{ "comment", is_magnet_download?"Metadata Download": "Download Archived" },
		{ "corruptEver", 0 },
		{ "creator", created_by },
		{ "dateCreated", create_date },
		{ "desiredAvailable", 0 },
		//{ "downloadDir", "" },
		{ "downloadedEver", 0 },
		{ "error", error },
		{ "errorString", error_str },
		{ "eta", TransmissionVars.TR_ETA_NOT_AVAIL },
		//{ "fileStats", "" },
		//{ "files", "" },
		//{ TransmissionVars.FIELD_TORRENT_HASH, "" },
		{ "haveUnchecked", 0 },
		//{ "haveValid", "" },
		//{ "id", "" },
		{ "isFinished", is_magnet_download?false:true },
		{ "isPrivate", false },
		{ "isStalled", false },
		{ "leftUntilDone", is_magnet_download?size:0 },	// leftUntilDone is used to mark downloads as incomplete
		{ "metadataPercentComplete",md_comp },
		//{ "name", "" },
		{ "peers", new ArrayList() },
		{ "peersConnected", 0 },
		{ "peersGettingFromUs", 0 },
		{ "peersSendingToUs", "" },
		{ "percentDone", is_magnet_download?0.0f:100.0f },
		{ "pieceCount", 1 },
		{ "pieceSize", size==0?1:size },
		{ "queuePosition", 0 },
		{ "rateDownload", 0 },
		{ "rateUpload", 0 },
		{ "recheckProgress", 0.0f },
		{ "seedRatioLimit", 1.0f },
		{ "seedRatioMode", TransmissionVars.TR_RATIOLIMIT_GLOBAL },
		//{ "sizeWhenDone", "" },
		{ "startDate", is_magnet_download?create_date:0 },
		{ "status", status },
		//{ "totalSize", "" },
		{ "trackerStats", new ArrayList() },
		{ "trackers", new ArrayList() },
		{ "uploadRatio", 0.0f },
		{ "uploadedEver", 0 },
		{ "webseedsSendingToUs", 0 },
		{ "torrentFile", "" }
		};
		
		
		
		Map<String,Object>	stub_def_map = new HashMap<String, Object>();
		
		for ( Object[] d: stub_defs ){
			stub_def_map.put( (String)d[0], d[1] );
		}
		
		for ( String field: fields ){
			
			Object	value = stub_def_map.get( field );
			
			if ( field.equals( "id" )){
				
				value = download_id;
				
			}else if ( field.equals( "downloadDir" )){
									
				value = download_stub.getSavePath();
					
			}else if ( field.equals( "files" )){

				String host = (String)request.getHeaders().get( "host" );

				value = torrentGet_files_stub(host, download_stub, download_id,
						file_fields, args);

			}else if ( field.equals( "fileCount" )){

				value = download_stub.getStubFiles().length;

			}else if ( field.equals( "fileStats" )){
				// RPC v5

				value = torrentGet_fileStats_stub(download_stub, file_fields, args);

			}else if ( field.equals( TransmissionVars.FIELD_TORRENT_HASH )){
				
				value = ByteFormatter.encodeString( download_stub.getTorrentHash());
				
			}else if ( field.equals( "haveValid" )){

				value = is_magnet_download?0:size;
			
			}else if ( field.equals( "name" )){

				value = download_stub.getName();
				
			}else if ( field.equals( "sizeWhenDone" )){

				value = size;
				
			}else if ( field.equals( "totalSize" )){

				value = size;

			} else if (field.equals("tag-uids")) {
				// azRPC
				List listTags = MapUtils.getMapList(torrent, field, new ArrayList());
				
				TagManager tm = TagManagerFactory.getTagManager();

				if (listTags.size() == 0) {
					Tag tag = getTagFromState(Download.ST_STOPPED, !is_magnet_download);
					if (tag != null) {
						listTags.add(tag.getTagUID());
					}

					//  9, "tag.type.ds.inact"
					tag = tm.getTagType(TagType.TT_DOWNLOAD_STATE).getTag(9);
					if (tag != null) {
						listTags.add(tag.getTagUID());
					}
				}
				
				// 11, "tag.type.ds.incomp"
				// 10, incomplete
				Tag tag = tm.getTagType(TagType.TT_DOWNLOAD_STATE).getTag(is_magnet_download ? 11 : 10);
				if (tag != null) {
					listTags.add(tag.getTagUID());
				}
				
				Category catAll = CategoryManager.getCategory(Category.TYPE_ALL);
				if (catAll != null) {
					listTags.add(catAll.getTagUID());
				}
				Category catUncat = CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED);
				if (catUncat != null) {
					listTags.add(catUncat.getTagUID());
				}
				
				value = listTags;
			}

			
			if ( value != null ){
			
				if (xmlEscape && (value instanceof String)) {
				
					value = escapeXML((String)value);
				}
				
				torrent.put( field, value );
				
			}else{
				if ( trace_param.getValue() ){
					log( "Unknown stub field: " + field );
				}
			}
		}
	}
	
	private Tag getTagFromState(int state, boolean complete) {
		/*
		 	tag_initialising		= new MyTag( 0, "tag.type.ds.init",
			tag_downloading			= new MyTag( 1, "tag.type.ds.down",
			tag_seeding				= new MyTag( 2, "tag.type.ds.seed", 
			tag_queued_downloading	= new MyTag( 3, "tag.type.ds.qford"
			tag_queued_seeding		= new MyTag( 4, "tag.type.ds.qfors", 
			tag_stopped				= new MyTag( 5, "tag.type.ds.stop", 
			tag_error				= new MyTag( 6, "tag.type.ds.err", 
	 */
		int id = 0;

		switch (state) {
			case Download.ST_DOWNLOADING:
				id = 1;
				break;
			case Download.ST_SEEDING:
				id = 2;
				break;
			case Download.ST_QUEUED:
				id = complete ? 4 : 3;
				break;
			case Download.ST_STOPPED:
			case Download.ST_STOPPING:
				id = 5;
				break;
			case Download.ST_ERROR:
				id = 6;
				break;
		}
		TagManager tm = TagManagerFactory.getTagManager();
		return tm.getTagType(TagType.TT_DOWNLOAD_STATE).getTag(id);
	}
	
	/** Number of webseeds that are sending data to us. */
  private Object torrentGet_webseedsSendingToUs(DownloadManager core_download) {
  	PEPeerManager peerManager = core_download.getPeerManager();
  	if (peerManager == null) {
  		return 0;
  	}
		int numWebSeedsConnected = 0;
		List<PEPeer> peers = peerManager.getPeers();
		for (PEPeer peer : peers) {
			if (peer.getProtocol().toLowerCase().startsWith( "http" )){
				numWebSeedsConnected++;
			}
		}
		return numWebSeedsConnected;
	}

	private Object torrentGet_webSeeds(Torrent t) {
    // webseeds           
    // | an array of strings:                 |
    // +-------------------------+------------+
    // | webseed                 | string     | tr_info
		List getright = BDecoder.decodeStrings(getURLList(t, "url-list"));
		List webseeds = BDecoder.decodeStrings(getURLList(t, "httpseeds"));

		List list = new ArrayList();
		for (List l : new List[] {
			getright,
			webseeds
		}) {

			for (Object o : l) {
				if (o instanceof String) {
					list.add(o);
				}
			}
		}
		return list;
	}

	/** 
   * When tr_stat.activity is TR_STATUS_CHECK or TR_STATUS_CHECK_WAIT,
   * this is the percentage of how much of the files has been 
   * verified. When it gets to 1, the verify process is done.
   * Range is [0..1]
   **/
	private Object torrentGet_recheckProgress(DownloadManager core_download,
			DownloadStats stats) {
		double x = 1;
		
		if ( core_download.getState() == DownloadManager.STATE_CHECKING ){
			
			DiskManager dm = core_download.getDiskManager();
			
			if ( dm != null ){
				
				x = ((double)stats.getCompleted())/1000;
			}
		}
		
		return x;
	}

	private Object torrentGet_priorities(Download download) {
    // | an array of tr_info.filecount        | tr_info
    // | numbers. each is the tr_priority_t   |
    // | mode for the corresponding file.     |
		List list = new ArrayList();
		
		DiskManagerFileInfo[] fileInfos = download.getDiskManagerFileInfo();
		
		for (DiskManagerFileInfo fileInfo : fileInfos) {
			int priority = fileInfo.getNumericPriority();
			long newPriority = TransmissionVars.convertVuzePriority(priority);
			list.add(newPriority);
		}
		
		return list;
	}

	private Object torrentGet_pieces(DownloadManager core_download) {
  	Object value = null;

		// TODO: No idea if this works
		// pieces | string             
		// | A bitfield holding pieceCount flags  | tr_torrent
		// | which are set to 'true' if we have   |
		// | the piece matching that position.    |
		// | JSON doesn't allow raw binary data,  |
		// | so this is a base64-encoded string.  |

		DiskManager dm = core_download.getDiskManager();
		
		if ( dm != null ){
			DiskManagerPiece[] pieces = dm.getPieces();
			byte[] bits = new byte[ (int) Math.ceil(pieces.length / 8.0f)];
			int pieceNo = 0;
			int bitPos = 0;
			while (pieceNo < pieces.length) {
				
				bits[bitPos] = 0;
				for (int i = 0; pieceNo < pieces.length && i < 8; i++) {
					boolean done = pieces[pieceNo].isDone();
					
					if (done) {
						bits[bitPos] |= (byte)(1 << i);
					}
					
					pieceNo++;
				}
				
				bitPos++;
			}
			try {
				value = new String( Base64.encode(bits), "UTF8");
			} catch (UnsupportedEncodingException e) {
			}
		}
		return value;
	}

	private Object torrentGet_peersFrom(PEPeerManager pm) {
    // peersFrom          | an object containing:                |
    // +-------------------------+------------+
    // | fromCache               | number     | tr_stat
    // | fromDht                 | number     | tr_stat
    // | fromIncoming            | number     | tr_stat
    // | fromLpd                 | number     | tr_stat
    // | fromLtep                | number     | tr_stat
    // | fromPex                 | number     | tr_stat
    // | fromTracker             | number     | tr_stat

		Map<String, Long> mapPeersFrom = new HashMap<String, Long>();

		if (pm == null) {
			return mapPeersFrom;
		}
			
		List<PEPeer> peers = pm.getPeers();
		
		for ( PEPeer peer: peers ){
			
			String peerSource = peer.getPeerSource();
			if (peerSource != null) {
				if (peerSource.equals(PEPeerSource.PS_BT_TRACKER)) {
					peerSource = "fromTracker";
				} else if (peerSource.equals(PEPeerSource.PS_DHT)) {
					peerSource = "fromDht";
				} else if (peerSource.equals(PEPeerSource.PS_INCOMING)) {
					peerSource = "fromIncoming";
				} else if (peerSource.equals(PEPeerSource.PS_OTHER_PEER)) {
					peerSource = "fromPex";
				} else if (peerSource.equals(PEPeerSource.PS_PLUGIN)) {
					// TODO: better cat?
					peerSource = "fromCache";
				} else {
					peerSource = "fromCache";
				} // missing: from Ltep
				if (!mapPeersFrom.containsKey(peerSource)) {
					mapPeersFrom.put(peerSource, 1l);
				} else {
					mapPeersFrom.put(peerSource, mapPeersFrom.get(peerSource) + 1);
				}
			}
		}

		return mapPeersFrom;
	}

	/** 
   * time when one or more of the torrent's trackers will
   * allow you to manually ask for more peers,
   * or 0 if you can't 
   */
	private Object torrentGet_manualAnnounceTime(DownloadManager manager) {
		// See ScrapeInfoView's updateButton logic
		Object value;
		TRTrackerAnnouncer trackerClient = manager.getTrackerClient();
		if (trackerClient != null) {

			value = Math.max(SystemTime.getCurrentTime() / 1000,
					trackerClient.getLastUpdateTime() + TRTrackerAnnouncer.REFRESH_MINIMUM_SECS);
					
		} else {
			// Technically the spec says "ask for more peers" which suggests
			// we don't need to handle scrape -- but let's do it anyway

			TRTrackerScraperResponse sr = manager.getTrackerScrapeResponse();
			
			if ( sr == null ){
				
				value = 0;
				
			}else{
				
				value = Math.max(SystemTime.getCurrentTime() / 1000,
						sr.getScrapeStartTime() / 1000 + TRTrackerScraper.REFRESH_MINIMUM_SECS);
			}
		}

		return value;
	}

	/** 
	 * If downloading, estimated number of seconds left until the torrent is done.
	 * If seeding, estimated number of seconds left until seed ratio is reached. 
	 */
	private Object torrentGet_eta(DownloadManager core_download, Download download, DownloadStats stats ){
		Object value;

		int state = download.getState();
		if (state == Download.ST_DOWNLOADING) {
			long eta_secs = core_download.getStats().getSmoothedETA();
			//long eta_secs = stats.getETASecs();
			
			if (eta_secs == -1) {
				value = TransmissionVars.TR_ETA_NOT_AVAIL;
			} else if (eta_secs >= 315360000000L) {
				value = TransmissionVars.TR_ETA_UNKNOWN;
			} else {
				value = eta_secs;
			}
		} else if (state == Download.ST_SEEDING) {
			// TODO: secs left until SR met
			value = TransmissionVars.TR_ETA_NOT_AVAIL;
		} else {
			value = TransmissionVars.TR_ETA_NOT_AVAIL;
		}
		
		return value;
	}
	
	private Object torrentGet_trackers(DownloadManager core_download, boolean hack) {
		List	trackers = new ArrayList();

		List<TrackerPeerSource> trackerPeerSources = core_download.getTrackerPeerSources();
		
		if (trackerPeerSources == null) {
			return trackers;
		}

		for (TrackerPeerSource tps : trackerPeerSources) {
	    String statusString = tps.getStatusString();
	    if (statusString == null) {
	    	statusString = "";
	    }

	    Map<String, Object> map = new HashMap<String, Object>();
      //trackers           | array of objects, each containing:   |
      //+-------------------------+------------+
      //| announce                | string     | tr_tracker_info
      //| id                      | number     | tr_tracker_info
      //| scrape                  | string     | tr_tracker_info
      //| tier                    | number     | tr_tracker_info

	    String name = "";
	    try {
		    name = tps.getName();
	    } catch (Exception e) {
	    	name = tps.getClass().getSimpleName();
	    	// NPE at com.aelitis.azureus.pif.extseed.ExternalSeedPlugin$5.getName(ExternalSeedPlugin.java:561
	    }
	    
	    if (hack && !name.contains("://")) {
	    	name = "://" + name;
	    }
	    
	    map.put("id", tps.hashCode());
	    /* the full announce URL */
	    map.put("announce", name); // TODO
	    /* the full scrape URL */
	    map.put("scrape", name); // TODO
	    /* which tier this tracker is in */
	    map.put("tier", 0); // TODO: int);


	    
	    trackers.add(map);
		}

		return trackers;
	}

	private Object torrentGet_trackerStats(DownloadManager core_download) {
		List	tracker_stats = new ArrayList();

		List<TrackerPeerSource> trackerPeerSources = core_download.getTrackerPeerSources();
		
		if (trackerPeerSources == null) {
			return tracker_stats;
		}

		for (TrackerPeerSource tps : trackerPeerSources) {
	    String statusString = tps.getStatusString();
	    if (statusString == null) {
	    	statusString = "";
	    }

	    Map<String, Object> map = new HashMap<String, Object>( 64 );
			
	    /* how many downloads this tracker knows of (-1 means it does not know) */
	    map.put("downloadCount", -1); // TODO

	    /* whether or not we've ever sent this tracker an announcement */
	    map.put("hasAnnounced", tps.getPeers() >= 0); // TODO

	    /* whether or not we've ever scraped to this tracker */
	    map.put("hasScraped", false); // todo: bool);

	    String name = "";
	    try {
		    name = tps.getName();
	    } catch (Exception e) {
	    	// NPE at com.aelitis.azureus.pif.extseed.ExternalSeedPlugin$5.getName(ExternalSeedPlugin.java:561
	    }

	    /* human-readable string identifying the tracker */
    	map.put("host", name); // TODO

	    /* the full announce URL */
	    map.put("announce", name); // TODO

	    /* the full scrape URL */
	    map.put("scrape", name); // TODO

	    /* Transmission uses one tracker per tier,
	     * and the others are kept as backups */
	    map.put("isBackup", false); // TODO

	    /* is the tracker announcing, waiting, queued, etc */
	    int status = tps.getStatus();
	    int state;
	    if (status == tps.ST_AVAILABLE || status == tps.ST_ONLINE) {
	    	state = TransmissionVars.TR_TRACKER_WAITING;
	    } else if (status == tps.ST_UPDATING) {
	    	state = TransmissionVars.TR_TRACKER_ACTIVE; 
	    } else if (status == tps.ST_QUEUED) {
	    	state = TransmissionVars.TR_TRACKER_QUEUED; 
	    } else {
	    	state = TransmissionVars.TR_TRACKER_INACTIVE;
	    }
	    map.put("announceState", state);

	    /* is the tracker scraping, waiting, queued, etc */
	    map.put("scrapeState", state);

	    /* number of peers the tracker told us about last time.
	     * if "lastAnnounceSucceeded" is false, this field is undefined */
	    map.put("lastAnnouncePeerCount", tps.getPeers());

	    /* human-readable string with the result of the last announce.
	       if "hasAnnounced" is false, this field is undefined */
	    if (statusString != null) {
	    	map.put("lastAnnounceResult", statusString);
	    }

	    /* when the last announce was sent to the tracker.
	     * if "hasAnnounced" is false, this field is undefined */
	    map.put("lastAnnounceStartTime", 0); // TODO: time_t);

	    /* whether or not the last announce was a success.
	       if "hasAnnounced" is false, this field is undefined */
	    map.put("lastAnnounceSucceeded", tps.getPeers() >= 0);

	    /* whether or not the last announce timed out. */
	    map.put("lastAnnounceTimedOut", false); // TODO

	    /* when the last announce was completed.
	       if "hasAnnounced" is false, this field is undefined */
	    map.put("lastAnnounceTime", 0); // TODO: time_t);

	    /* human-readable string with the result of the last scrape.
	     * if "hasScraped" is false, this field is undefined */
	    if (statusString != null) {
	    	map.put("lastScrapeResult", statusString);
	    }

	    /* when the last scrape was sent to the tracker.
	     * if "hasScraped" is false, this field is undefined */
	    map.put("lastScrapeStartTime", 0); // TODO: time_t);

	    /* whether or not the last scrape was a success.
	       if "hasAnnounced" is false, this field is undefined */
	    map.put("lastScrapeSucceeded", tps.getPeers() >= 0);

	    /* whether or not the last scrape timed out. */
	    map.put("lastScrapeTimedOut", false); // TODO: bool);

	    /* when the last scrape was completed.
	       if "hasScraped" is false, this field is undefined */
	    map.put("lastScrapeTime", 0); // TODO: time_t);

	    /* number of leechers this tracker knows of (-1 means it does not know) */
	    map.put("leecherCount", tps.getLeecherCount());

	    /* when the next periodic announce message will be sent out.
	       if announceState isn't TR_TRACKER_WAITING, this field is undefined */
	    map.put("nextAnnounceTime", 0); // TODO: time_t);

	    /* when the next periodic scrape message will be sent out.
	       if scrapeState isn't TR_TRACKER_WAITING, this field is undefined */
	    map.put("nextScrapeTime", 0); // TODO: time_t);

	    /* number of seeders this tracker knows of (-1 means it does not know) */
	    map.put("seederCount", tps.getSeedCount());

	    /* which tier this tracker is in */
	    map.put("tier", 0); // TODO: int);

	    /* used to match to a tr_tracker_info */
	    map.put("id", tps.hashCode());
	    
	    tracker_stats.add(map);
		}

		return tracker_stats;
	}

	private Object torrentGet_status(Download download) {
		// 1 - waiting to verify
		// 2 - verifying
		// 4 - downloading
		// 5 - queued (incomplete)
		// 8 - seeding
		// 9 - queued (complete)
		// 16 - paused
	
		// 2.71 - these changed!

	    //TR_STATUS_STOPPED        = 0, /* Torrent is stopped */
	    //TR_STATUS_CHECK_WAIT     = 1, /* Queued to check files /*
	    //TR_STATUS_CHECK          = 2, /* Checking files */
	    //TR_STATUS_DOWNLOAD_WAIT  = 3, /* Queued to download */
	    //TR_STATUS_DOWNLOAD       = 4, /* Downloading */
	    //TR_STATUS_SEED_WAIT      = 5, /* Queued to seed */
	    //TR_STATUS_SEED           = 6  /* Seeding */
	   
  	final int CHECK_WAIT;
  	final int CHECKING;
  	final int DOWNLOADING;
  	final int QUEUED_INCOMPLETE;
  	final int QUEUED_COMPLETE;
  	final int STOPPED;
  	final int SEEDING;
  	final int ERROR;

  	boolean	RPC_14_OR_HIGHER = true; // fields.contains( "percentDone" );
  	
  	if ( RPC_14_OR_HIGHER ){
  		
  		CHECK_WAIT			= 1;
  		CHECKING			= 2;
  		DOWNLOADING			= 4;
  		QUEUED_INCOMPLETE	= 3;
  		QUEUED_COMPLETE		= 5;
  		STOPPED				= 0;
  		SEEDING				= 6;
  		ERROR				= STOPPED;
  	}else{
  		CHECK_WAIT			= 1;
  		CHECKING			= 2;
  		DOWNLOADING			= 4;
  		QUEUED_INCOMPLETE	= 5;
  		QUEUED_COMPLETE		= 9;
  		STOPPED				= 16;
  		SEEDING				= 8;
  		ERROR				= 0;
  	}
  	
  	int	status_int;
  	
		int state = download.getState();
		
		if ( state == Download.ST_DOWNLOADING ){
			
			status_int = DOWNLOADING;
			
		}else if ( state == Download.ST_SEEDING ){
			
			status_int = SEEDING;
			
		}else if ( state == Download.ST_QUEUED ){

			if ( download.isComplete()){
				
				status_int = QUEUED_COMPLETE;
				
			}else{
				
				status_int = QUEUED_INCOMPLETE;
			}
		}else if ( state == Download.ST_STOPPED || state == Download.ST_STOPPING ){
			
			status_int = STOPPED;
			
		}else if ( state == Download.ST_ERROR ){
			
			status_int = ERROR;
			
		}else{
			
			DownloadManager	core_download = PluginCoreUtils.unwrap( download );
			if ( core_download.getState() == DownloadManager.STATE_CHECKING ){
			
				status_int = CHECKING;
				
			}else{
				
				status_int = CHECK_WAIT;
			}
		}
  	
  	return status_int;
	}

	private Object torrentGet_wanted(Download download) {
    // wanted             
    // | an array of tr_info.fileCount        | tr_info
    // | 'booleans' true if the corresponding |
    // | file is to be downloaded.            |
		List<Object> list = new ArrayList<Object>();
		
		DiskManagerFileInfo[] files = download.getDiskManagerFileInfo();
		
		for ( DiskManagerFileInfo file: files ){
			list.add( !file.isSkipped() );
		}
		
		return list;
	}

	private Object torrentGet_fileStats(
			Download download, 
			List<String> file_fields,
			Map args) {
		// | a file's non-constant properties.    |
    // | array of tr_info.filecount objects,  |
    // | each containing:                     |
    // +-------------------------+------------+
    // | bytesCompleted          | number     | tr_torrent
    // | wanted                  | boolean    | tr_info
    // | priority                | number     | tr_info
		List<Map> stats_list = new ArrayList<Map>();
		
		DiskManagerFileInfo[] files = download.getDiskManagerFileInfo();
		
		for (int i = 0; i < files.length; i++) {
			DiskManagerFileInfo file = files[i];
			
			TreeMap map = new TreeMap();

			stats_list.add(map);

			torrentGet_fileStats(map, file_fields, file);
		}
		
		return stats_list;
	}

	private void torrentGet_fileStats(Map map, List<String> sortedFields,
			DiskManagerFileInfo file) {
		boolean all = sortedFields == null || sortedFields.size() == 0;

		if (all
				|| Collections.binarySearch(sortedFields,
						FIELD_FILESTATS_BYTES_COMPLETED) >= 0) {
			map.put(FIELD_FILESTATS_BYTES_COMPLETED, file.getDownloaded());
		}
		if (all
				|| Collections.binarySearch(sortedFields, FIELD_FILESTATS_WANTED) >= 0) {
			map.put(FIELD_FILESTATS_WANTED, !file.isSkipped());
		}

		if (all
				|| Collections.binarySearch(sortedFields, FIELD_FILESTATS_PRIORITY) >= 0) {
			map.put(FIELD_FILESTATS_PRIORITY,
					TransmissionVars.convertVuzePriority(file.getNumericPriority()));
		}
	}

	private Object torrentGet_fileStats_stub(
			DownloadStub download_stub,
			List<String> file_fields,
			Map args)
	{
		DownloadStubFile[] stubFiles = download_stub.getStubFiles();
		
		List<Map>	stats_list = new ArrayList<Map>();
	
		for (int i = 0; i < stubFiles.length; i++) {
			
			TreeMap	map = new TreeMap();
			stats_list.add(map);

			DownloadStubFile sf = stubFiles[i];
			torrentGet_fileStats_stub(map, null, sf);
		}
		
		return stats_list;
	}

	private void torrentGet_fileStats_stub(Map map, List<String> sortedFields,
			DownloadStubFile sf) {
		long len = sf.getLength();

		boolean all = sortedFields == null || sortedFields.size() == 0;

		if (all
				|| Collections.binarySearch(sortedFields,
						FIELD_FILESTATS_BYTES_COMPLETED) >= 0) {

			long downloaded = len < 0 ? 0 : len;

			map.put(FIELD_FILESTATS_BYTES_COMPLETED, downloaded);
		}
		if (all
				|| Collections.binarySearch(sortedFields, FIELD_FILESTATS_WANTED) >= 0) {
			map.put(FIELD_FILESTATS_WANTED, len >= 0);
		}

		if (all
				|| Collections.binarySearch(sortedFields, FIELD_FILESTATS_PRIORITY) >= 0) {
			map.put(FIELD_FILESTATS_PRIORITY, TransmissionVars.convertVuzePriority(0));
		}
	}

	private Object torrentGet_files(
			String host,
			Download download, 
			long download_id,
			List<String> file_fields,
			Map args)
	{
		// | array of objects, each containing:   |
    // +-------------------------+------------+
    // | bytesCompleted          | number     | tr_torrent
    // | length                  | number     | tr_info
    // | name                    | string     | tr_info
		// Vuze, when file_indexes present:
		// | index                   | number
		// | hc                      | number     | hashcode to be later used to supress return of file map

		List<Map> file_list = new ArrayList<Map>();

		// Skip files that match these hashcodes
		List listHCs = MapUtils.getMapList(args, "files-hc-" + download_id, null);
		
		DiskManagerFileInfo[] files = download.getDiskManagerFileInfo();
		
		int[] file_indexes = getFileIndexes(args, download_id);
		
		String baseURL = MapUtils.getMapString(args, "base-url", null);
		
		if (file_indexes == null || file_indexes.length == 0) {
			for (int i = 0; i < files.length; i++) {
				DiskManagerFileInfo file = files[i];
  			
				TreeMap map = new TreeMap();
  			
				map.put("index", i);

  			torrentGet_files(map, file_fields, host, baseURL, download, file);
  			if (file_fields != null && file_fields.size() > 0) {
  				torrentGet_fileStats(map, file_fields, file);
  			}

  			hashAndAdd(map, file_list, listHCs, i);
  		}
		} else {
			for (int i = 0; i < file_indexes.length; i++) {
				int file_index = file_indexes[i];
				if (file_index < 0 || file_index >= files.length) {
					continue;
				}

				TreeMap<String, Object> map = new TreeMap<String, Object>();

				map.put("index", file_index);
				DiskManagerFileInfo fileInfo = files[file_index];
				torrentGet_fileStats(map, file_fields, fileInfo);
				torrentGet_files(map, file_fields, host, baseURL, download, fileInfo);

  			hashAndAdd(map, file_list, listHCs, i);
			}
		}
		
		return file_list;
	}

	private void torrentGet_files(Map obj, List<String> sortedFields,
			String host, String baseURL, Download download, DiskManagerFileInfo file) {
		boolean all = sortedFields == null || sortedFields.size() == 0;

		if (all
				|| Collections.binarySearch(sortedFields,
						FIELD_FILESTATS_BYTES_COMPLETED) >= 0) {
			obj.put(FIELD_FILESTATS_BYTES_COMPLETED, file.getDownloaded()); // this must be a spec error...
		}
		if (all || Collections.binarySearch(sortedFields, FIELD_FILES_LENGTH) >= 0) {
			obj.put(FIELD_FILES_LENGTH, file.getLength());
		}
		if (all || Collections.binarySearch(sortedFields, FIELD_FILES_NAME) >= 0) {
			String absolutePath = file.getFile(true).getAbsolutePath();
			String savePath = download.getSavePath();
			Torrent torrent = download.getTorrent();
			boolean simpleTorrent = torrent == null ? false : torrent.isSimpleTorrent();
			if (simpleTorrent) {
				obj.put(FIELD_FILES_NAME, file.getFile().getName());
			} else {
				if (absolutePath.startsWith(savePath)) {
					// TODO: .dnd_az parent..
		    	//String dnd_sf = dm.getDownloadState().getAttribute( DownloadManagerState.AT_DND_SUBFOLDER );

					// + 1 to remove the dir separator
					obj.put(FIELD_FILES_NAME, absolutePath.substring(savePath.length() + 1));
				} else {
					obj.put(FIELD_FILES_NAME, absolutePath);
				}
			}
		}

		// Vuze specific, don't clutter transmission clients with these (they don't
		// have sortedFields param)
		if (sortedFields != null) {
			boolean showAllVuze = sortedFields.size() == 0;

			if (showAllVuze
					|| Collections.binarySearch(sortedFields, FIELD_FILES_CONTENT_URL) >= 0) {
				URL f_stream_url = PlayUtils.getMediaServerContentURL(file);
				if (f_stream_url != null) {
					String s = adjustURL(host, f_stream_url);
					if (baseURL != null && s.startsWith(baseURL)) {
						s = s.substring(baseURL.length(), s.length());
					}
					obj.put(FIELD_FILES_CONTENT_URL, s);
				}
			}

			if (showAllVuze
					|| Collections.binarySearch(sortedFields, FIELD_FILES_FULL_PATH) >= 0) {
				obj.put(FIELD_FILES_FULL_PATH, file.getFile().toString());
			}
		}
	}

	private Object torrentGet_files_stub(
			String host,
			DownloadStub download_stub,
			long download_id,
			List<String> file_fields,
			Map args)
	{
		DownloadStubFile[] stubFiles = download_stub.getStubFiles();
		
		List<Map>	file_list = new ArrayList<Map>();

		// Skip files that match these hashcodes
		List listHCs = MapUtils.getMapList(args, "files-hc" + download_id, null);

		int[] file_indexes = getFileIndexes(args, download_id);
		
		if (file_indexes == null || file_indexes.length == 0) {
  		
			for (int i = 0; i < stubFiles.length; i++) {
				DownloadStubFile sf = stubFiles[i];
  		
				TreeMap	map = new TreeMap();
  			
				map.put("index", i);

  			torrentGet_file_stub(map, file_fields, host, sf);
  			if (file_fields != null && file_fields.size() > 0) {
  				torrentGet_fileStats_stub(map, file_fields, sf);
  			}

  			hashAndAdd(map, file_list, listHCs, i);
			}
		} else {
			for (int i = 0; i < file_indexes.length; i++) {
				int file_index = file_indexes[i];
				if (file_index < 0 || file_index >= stubFiles.length) {
					continue;
				}

				TreeMap<String, Object> map = new TreeMap<String, Object>();

				file_list.add(map);

				map.put("index", file_index);
				DownloadStubFile file = stubFiles[file_index];
				torrentGet_fileStats_stub(map, file_fields, file);
				torrentGet_file_stub(map, file_fields, host, file);

  			hashAndAdd(map, file_list, listHCs, i);
			}
		}
		
		return file_list;				
	}

	private int[] getFileIndexes(Map args, long download_id) {
		Object file_ids = args.get("file-indexes-" + download_id);
		int[] file_indexes = null;
		if (file_ids instanceof Number) {
			file_indexes = new int[] {
				((Number) file_ids).intValue()
			};
		} else if (file_ids instanceof List) {
			List listFileIDs = (List) file_ids;
			file_indexes = new int[listFileIDs.size()];
			for (int i = 0; i < listFileIDs.size(); i++) {
				Object o = listFileIDs.get(i);
				if (o instanceof Number) {
					file_indexes[i] = ((Number) o).intValue();
				}
			}
		}
		return file_indexes;
	}

	private void torrentGet_file_stub(Map map,List<String> sortedFields, String host, DownloadStubFile sf) {
		long len = sf.getLength();

		long downloaded;

		if (len < 0) {

			downloaded = 0;
			len = -len;

		} else {

			downloaded = len;
		}

		boolean all = sortedFields == null || sortedFields.size() == 0;

		if (all
				|| Collections.binarySearch(sortedFields,
						FIELD_FILESTATS_BYTES_COMPLETED) >= 0) {
			map.put(FIELD_FILESTATS_BYTES_COMPLETED, downloaded); // this must be a spec error...
		}
		if (all || Collections.binarySearch(sortedFields, FIELD_FILES_LENGTH) >= 0) {
			map.put(FIELD_FILES_LENGTH, len);
		}
		if (all || Collections.binarySearch(sortedFields, FIELD_FILES_NAME) >= 0) {
			map.put(FIELD_FILES_NAME, sf.getFile().getName());
		}
		if (sortedFields != null
				&& Collections.binarySearch(sortedFields, FIELD_FILES_FULL_PATH) >= 0) {
			map.put(FIELD_FILES_FULL_PATH, sf.getFile().toString());
		}
	}

	private Object torrentGet_errorString(DownloadManager core_download,
			Download download) {
		Object value;

		String str = download.getErrorStateDetails();
		
		if ( str != null && str.length() > 0 ){
			value = str;
		}else{
			value = "";
			TRTrackerAnnouncer tracker_client = core_download.getTrackerClient();
			
			if ( tracker_client != null ){
				TRTrackerAnnouncerResponse x = tracker_client.getBestAnnouncer().getLastResponse();
				if ( x != null ){
					if ( x.getStatus() == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR ){
						value = x.getStatusString();
					}
				}
			}else{
				DownloadScrapeResult x = download.getLastScrapeResult();
				if ( x != null ){
					if ( x.getResponseType() == DownloadScrapeResult.RT_ERROR ){
						value = x.getStatus();
					}
				}
			}
		}
		return value;
	}

	/** Defines what kind of text is in errorString. TR_STAT_* */
	private Object torrentGet_error(DownloadManager core_download, Download download) {
		Object value;
		/** Defines what kind of text is in errorString. */
		String str = download.getErrorStateDetails();
		
		if ( str != null && str.length() > 0 ){
			value = TransmissionVars.TR_STAT_LOCAL_ERROR;
		}else{
			value = 0;
			TRTrackerAnnouncer tracker_client = core_download.getTrackerClient();
			
			if ( tracker_client != null ){
				TRTrackerAnnouncerResponse x = tracker_client.getBestAnnouncer().getLastResponse();
				if ( x != null ){
					if ( x.getStatus() == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR ){
						value = TransmissionVars.TR_STAT_TRACKER_ERROR;
					}
				}
			}else{
				DownloadScrapeResult x = download.getLastScrapeResult();
				if ( x != null ){
					if ( x.getResponseType() == DownloadScrapeResult.RT_ERROR ){
						String status = x.getStatus();
						
						if ( status != null && status.length() > 0 ){
						
							value = TransmissionVars.TR_STAT_TRACKER_ERROR;
						}
					}
				}
			}
		}
		return value;
	}

	/** 
	 * The last time we uploaded or downloaded piece data on this torrent. 
	 */
	private Object torrentGet_activityDate(DownloadManager download, boolean relative) {
		int state = download.getState();
		if (state == DownloadManager.STATE_SEEDING || state == DownloadManager.STATE_DOWNLOADING) {
			int r = download.getStats().getTimeSinceLastDataReceivedInSeconds();
			int s = download.getStats().getTimeSinceLastDataSentInSeconds();
			long l;
			if (r > 0 && s > 0) {
				l = Math.min(r, s);
			} else if (r < 0) {
				l = s;
			} else {
				l = r;
			}
			if (relative) {
				return -l;
			}
			return (SystemTime.getCurrentTime() / 1000) - l;
		}

		DownloadManagerState downloadState = download.getDownloadState();
		long timestamp = downloadState.getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_LAST_ACTIVE_TIME);
		if (timestamp != 0) {
			return timestamp / 1000;
		}

		timestamp = downloadState.getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);
		if (timestamp != 0) {
			return timestamp / 1000;
		}

		timestamp = downloadState.getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME);
		if (timestamp != 0) {
			return timestamp / 1000;
		}

		return 0;
	}

	/** 
	 * True if the torrent is running, but has been idle for long enough
	 * to be considered stalled.
	 */
	private Object torrentGet_isStalled(Download download) {
		Object value = false;
		int state = download.getState();
		if (state == Download.ST_SEEDING || state == Download.ST_DOWNLOADING) {
    	DefaultRankCalculator calc = StartStopRulesDefaultPlugin.getRankCalculator(download);
    	if (calc != null) {
				value = (state == Download.ST_SEEDING && !calc.getActivelySeeding())
						|| (state == Download.ST_DOWNLOADING && !calc.getActivelyDownloading());
    	}
		}
		return value;
	}

	private List torrentGet_peers(DownloadManager core_download) {
  	// peers              | array of objects, each containing:   |
  	// +-------------------------+------------+
  	// | address                 | string     | tr_peer_stat
  	// | clientName              | string     | tr_peer_stat
  	// | clientIsChoked          | boolean    | tr_peer_stat
  	// | clientIsInterested      | boolean    | tr_peer_stat
  	// | flagStr                 | string     | tr_peer_stat
  	// | isDownloadingFrom       | boolean    | tr_peer_stat
  	// | isEncrypted             | boolean    | tr_peer_stat
  	// | isIncoming              | boolean    | tr_peer_stat
  	// | isUploadingTo           | boolean    | tr_peer_stat
  	// | isUTP                   | boolean    | tr_peer_stat
  	// | peerIsChoked            | boolean    | tr_peer_stat
  	// | peerIsInterested        | boolean    | tr_peer_stat
  	// | port                    | number     | tr_peer_stat
  	// | progress                | double     | tr_peer_stat
  	// | rateToClient (B/s)      | number     | tr_peer_stat
  	// | rateToPeer (B/s)        | number     | tr_peer_stat

		List peers = new ArrayList();
		
		if (core_download == null) {
			return peers;
		}
		
		PEPeerManager pm = core_download.getPeerManager();
		if (pm == null) {
			return peers;
		}
		List<PEPeer> peerList = pm.getPeers();
		for (PEPeer peer : peerList) {
			Map map = new HashMap();
			peers.add(map);
			
			boolean isDownloadingFrom = peer.isDownloadPossible() && peer.getStats().getDataReceiveRate() > 0;
			
			map.put("address", peer.getIp());
			map.put("clientName", peer.getClient());
			map.put("clientIsChoked", peer.isChokedByMe());
			map.put("clientIsInterested", peer.isInterested());

			// flagStr
      // "O": "Optimistic unchoke"
      // "D": "Downloading from this peer"
      // "d": "We would download from this peer if they'd let us"
      // "U": "Uploading to peer"
      // "u": "We would upload to this peer if they'd ask"
      // "K": "Peer has unchoked us, but we're not interested"
      // "?": "We unchoked this peer, but they're not interested"
      // "E": "Encrypted Connection"
      // "H": "Peer was discovered through Distributed Hash Table (DHT)"
      // "X": "Peer was discovered through Peer Exchange (PEX)"
      // "I": "Peer is an incoming connection"
      // "T": "Peer is connected via uTP"
			//TODO
			StringBuffer flagStr = new StringBuffer();
			if (isDownloadingFrom) {
				flagStr.append('D');
			}
			map.put("flagStr", flagStr.toString());

			// code, name
			String[] countryDetails = PeerUtils.getCountryDetails(peer);
			if (countryDetails != null && countryDetails.length > 0) {
				map.put("cc", countryDetails[0]);
			}
			
			map.put("isDownloadingFrom", isDownloadingFrom);
			// peer.connection.getTransport().isEncrypted
			map.put("isEncrypted", !"None".equals(peer.getEncryption()));  // TODO FIX
			map.put("isIncoming", peer.isIncoming());
			map.put("isUploadingTo", peer.getStats().getDataSendRate() > 0);
			// RPC v13
			map.put("isUTP", peer.getProtocol().equals("uTP"));
			map.put("peerIsChoked", peer.isChokingMe());
			map.put("peerIsInterested", peer.isInteresting());
			// RPC v3
			map.put("port", peer.getPort());
			map.put("progress", peer.getPercentDoneInThousandNotation() / 1000.0);
			map.put("rateToClient", peer.getStats().getDataReceiveRate());
			map.put("rateToPeer", peer.getStats().getDataSendRate());
		}
		
		return peers;
	}

	protected List<DownloadStub>
	getAllDownloads(
		boolean	include_magnet_dowloads )
	{
		Download[] 		downloads1 = plugin_interface.getDownloadManager().getDownloads();
		DownloadStub[] 	downloads2 = plugin_interface.getDownloadManager().getDownloadStubs();
		
		MagnetDownload[] 	downloads3;
		
		if ( include_magnet_dowloads ){
			
			synchronized( magnet_downloads ){
				
				downloads3 = magnet_downloads.toArray( new MagnetDownload[magnet_downloads.size()]);
			}
		}else{
			
			downloads3 = new MagnetDownload[0];
		}
		
		List<DownloadStub>	result = new ArrayList<DownloadStub>( downloads1.length + downloads2.length + downloads3.length );
		
		result.addAll( Arrays.asList( downloads1 ));
		result.addAll( Arrays.asList( downloads2 ));
		result.addAll( Arrays.asList( downloads3 ));

		
		return( result );
	}
	
	protected List<DownloadStub>
	getDownloads(
		Object		ids,
		boolean		include_magnet_dowloads )
	{
		List<DownloadStub>	downloads = new ArrayList<DownloadStub>();
		
		List<DownloadStub> 	all_downloads = getAllDownloads( include_magnet_dowloads );

		List<Long>		selected_ids 	= new ArrayList<Long>();
		List<String>	selected_hashes = new ArrayList<String>();
		
		if ( ids == null ){
			
		}else if ( ids instanceof String ){
			
			ids = null;
			
		}else if ( ids instanceof Number ){
			
			selected_ids.add(((Number)ids).longValue());
			
		}else if ( ids instanceof List ){
			
			List l = (List)ids;
			
			for (Object o: l ){
				
				if ( o instanceof Number ){
					
					selected_ids.add(((Number)o).longValue());
					
				}else if ( o instanceof String ){
					
					selected_hashes.add((String)o);
				}
			}
		}
		
		boolean hide_ln = hide_ln_param.getValue();
		
		for( DownloadStub download_stub: all_downloads ){
			
			if ( download_stub.isStub()){
				
				if ( ids == null ){
					
					downloads.add( download_stub );
					
				}else{
					
					long	id = getID( download_stub, true );
					
					if ( selected_ids.contains( id )){
						
						downloads.add( download_stub );
						
					}else{
						
						if ( selected_hashes.contains( ByteFormatter.encodeString( download_stub.getTorrentHash()))){
								
							downloads.add( download_stub );
						}
					}
				}
			}else{
				try{
					Download download = destubbify( download_stub );
				
					if ( hide_ln && download.getFlag( Download.FLAG_LOW_NOISE )){
						
						continue;
					}
					
					if ( download.getFlag( Download.FLAG_METADATA_DOWNLOAD )){
						
						continue;
					}
					
					if ( ids == null ){
						
						downloads.add( download );
						
					}else{
						
						long	id = getID( download, true );
						
						if ( selected_ids.contains( id )){
							
							downloads.add( download );
							
						}else{
							
							Torrent t = download.getTorrent();
							
							if ( t != null ){
								
								if ( selected_hashes.contains( ByteFormatter.encodeString( t.getHash()))){
									
									downloads.add( download );
								}
							}
						}
					}
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		}
		
		Collections.sort(
			downloads,
			new Comparator<DownloadStub>()
			{
				@Override
				public int
				compare(
					DownloadStub arg0, 
					DownloadStub arg1 )
				{
					long res = getID( arg0, true ) - getID( arg1, true );
					
					if ( res < 0 ){
						return( -1 );
					}else if ( res > 0 ){
						return( 1 );
					}else{
						return( 0 );
					}
				}		
			});
		
		return( downloads );
	}
	
	public List<DownloadManager>
	getDownloadManagerListFromIDs(
			GlobalManager gm,
			Object		ids )
	{
		List<DownloadStub> downloads = getDownloads(ids,false);
		
		ArrayList<DownloadManager> list = new ArrayList<DownloadManager>(downloads.size());

		for ( DownloadStub downloadStub: downloads ){
			
			try{
				Download download = destubbify( downloadStub );
				if (download != null) {
    			DownloadManager dm = PluginCoreUtils.unwrap(download);
    			if (dm != null) {
    				list.add(dm);
    			}
				}

			}catch( Throwable e ){
				
				Debug.out( "Failed to get dm '" + downloadStub.getName() + "'", e );
			}
		}

		return list;
	}

	protected List
	getList(
		Object	o )
	{
		if ( o instanceof List ) {
			return (List) o;
		} else {
			return new ArrayList();
		}
	}
	
	protected boolean
	getBoolean(
		Object	o )
	{
		return getBoolean(o, false);
	}

	protected Boolean
	getBoolean(
		Object	o,
		Boolean defaultVal )
	{
		if ( o instanceof Boolean ){
			
			return((Boolean)o);
						
		}else if ( o instanceof String ){
			
			return( ((String)o).equalsIgnoreCase( "true" ));
			
		}else if ( o instanceof Number ){
			
			return(((Number)o).intValue()!=0);
			
		}else{
			
			return( defaultVal );
		}
	}
	
	protected long
	getID(
		DownloadStub		download_stub,
		boolean				allocate_if_new )
	{
		synchronized( this ){
			
			if ( check_ids_outstanding ){
				
				check_ids_outstanding = false;
				
				List<DownloadStub> all_downloads = getAllDownloads( true );

				Set<Long>	all_ids = new HashSet<Long>();
				
				List<DownloadStub>	dups = new ArrayList<DownloadStub>();
				
				long	max_id = 0;
				
				for( DownloadStub d: all_downloads ){
					
					long	id = getID( d, false );
					
					if ( id <= 0 ){
						
						continue;
					}
					
					max_id = Math.max( max_id, id );
					
					if ( all_ids.contains( id )){
					
						dups.add( d );
						
					}else{
						
						all_ids.add( id );
					}
				}
				
				PluginConfig config = plugin_interface.getPluginconfig();
					
				long	next_id = max_id + 1;
				
				for ( DownloadStub d: dups ){
					
					//System.out.println( "Fixed duplicate id " + getID( d, false ) + " for " + d.getName());
					
					d.setLongAttribute( t_id, next_id++ );
				}
				
				config.setPluginParameter( "xmui.next.id", next_id );

			}
		}
			
			// I was trying to be clever and allocate unique ids for downloads. however,
			// the webui assumes they are consecutive and give a queue index. ho hum
			
		// return( d.getIndex());
		
		long id = download_stub.getLongAttribute( t_id );
			
		if ( id == 0 && allocate_if_new ){
		
			synchronized( this ){
				
				PluginConfig config = plugin_interface.getPluginconfig();
			
				id = config.getPluginLongParameter( "xmui.next.id", 1 );
				
				config.setPluginParameter( "xmui.next.id", id + 1 );
			}
			
			download_stub.setLongAttribute( t_id, id );
		}
		
		//System.out.println( download.getName() + " -> " + id );
		
		return( id );
	}
	
	private String
	getAZMode()
	{
		return "trial";
	}
	
	private void
	processVuzeTorrentGet(
		TrackerWebPageRequest		request,
		Map 						args,
		Map 						result)
	{		
		Object	ids = args.get( "ids" );
				
		List<DownloadStub>	downloads = getDownloads( ids, true );
				
		List<Map>	torrents = new ArrayList<Map>( downloads.size());
		
		result.put( "torrents", torrents );
		
		List<Number> requested_files 		= (List<Number>)args.get( "files" );

		String host = (String)request.getHeaders().get( "host" );
		
		for ( DownloadStub download_stub: downloads ){
						
			Map<String,Object>	torrent = new HashMap<String, Object>();
			
			torrents.add( torrent );
			
			long id = getID( download_stub, true );

			torrent.put( "id", id );
			
			if ( download_stub.isStub()){
				
				continue;
			}
			
			try{
				Download download = download_stub.destubbify();
				
				DownloadManager dm = PluginCoreUtils.unwrap( download );

				if ( dm == null ){
					
					continue;
				}
									
	
				DiskManagerFileInfo file = null;
				
				try{
					file = PluginCoreUtils.wrap(dm.getDownloadState().getPrimaryFile());
					
				}catch( DownloadException e ){
					
					continue;
				}
					
				if ( file == null ){
						
					continue;
				}
														
				URL stream_url = PlayUtils.getMediaServerContentURL( file );

				if ( stream_url != null ){
					
					torrent.put( "contentURL", adjustURL( host, stream_url ));
				}
				
				TOTorrent to_torrent = dm.getTorrent();
				
				if ( to_torrent != null ){
					
					String url = PlatformTorrentUtils.getContentThumbnailUrl( to_torrent );
					
					if ( url != null ){
						
						torrent.put( "thumbnailURL", url );
						
					}else{
				
						byte[] data = PlatformTorrentUtils.getContentThumbnail( to_torrent );
						
						if ( data != null ){
							
							torrent.put( "thumbnailURL", getThumbnailResourceURL( id ));
						}
					}
				}
				
				if ( requested_files != null ){
					
					List<Map> file_info = new ArrayList<Map>();
						
					torrent.put( "files", file_info );
					
					DiskManagerFileInfo[] files = download.getDiskManagerFileInfo();
					
					if ( requested_files.size() == 0 ){
						
						for ( DiskManagerFileInfo f: files ){
							
							Map f_map = new HashMap();
							
							file_info.add( f_map );
							
							f_map.put( "index", f.getIndex());
							
							URL f_stream_url = PlayUtils.getMediaServerContentURL( f );

							if ( f_stream_url != null ){
								
								f_map.put( "contentURL", adjustURL( host, f_stream_url ));
							}
						}
					}else{
						
						for ( Number num: requested_files ){
							
							int	index = num.intValue();
							
							if ( index >= 0 && index < files.length ){
								
								DiskManagerFileInfo f = files[index];
								
								Map f_map = new HashMap();
								
								file_info.add( f_map );
								
								f_map.put( "index", f.getIndex());
								
								URL f_stream_url = PlayUtils.getMediaServerContentURL( f );

								if ( f_stream_url != null ){
									
									f_map.put( "contentURL", adjustURL( host, f_stream_url ));
								}
							}
						}
					}
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	private static final int RT_THUMBNAIL	= 0;
	
	private String
	getThumbnailResourceURL(
		long	id )
	{
		Map map = new HashMap();
		
		map.put( "type", RT_THUMBNAIL );
		map.put( "id", id );
		
		String json = JSONUtils.encodeToJSON( map );
		
		return( "/vuze/resource?json=" + UrlUtils.encode( json ));
	}
	
	private boolean
	processResourceRequest(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response,
		Map							request_json )
	
		throws IOException
	{
		int	type = ((Number)request_json.get( "type" )).intValue();
		
		if ( type == RT_THUMBNAIL ){
			
			long id = ((Number)request_json.get( "id" )).longValue();
			
			List<DownloadStub> list = getDownloads( id, false );
			
			if ( list == null || list.size() != 1 ){
				
				throw( new IOException( "Unknown download id: " + id ));
			}
			
			try{
				Download download = list.get(0).destubbify();
			
				Torrent torrent = download.getTorrent();
				
				byte[] data = PlatformTorrentUtils.getContentThumbnail( PluginCoreUtils.unwrap( torrent ));
				
					// TODO: handle image types
				
				response.setContentType( "image/jpeg" );
				
				response.getOutputStream().write( data );

			}catch( Throwable e ){
				
				throw( new IOException( "Failed to get thumbnail: " + Debug.getNestedExceptionMessage( e )));
			}
			
			return( true );
			
		}else{
			
			throw( new IOException( "Unknown resource type: " + type ));
		}
	}
	
	private String
	adjustURL(
		String		host,
		URL			url )
	{
		if ( host == null || host.length() == 0 ){
			
			return( url.toExternalForm());
		}
		
		int	pos = host.indexOf( ':' );
		
		if ( pos != -1 ){
		
			host = host.substring( 0, pos ).trim();
		}
		
		return( UrlUtils.setHost( url, host ).toExternalForm());
	}
	
	private void
	processVuzeLifecycle(
		Map<String,Object>	args,
		Map<String,Object>	result )
	
		throws IOException
	{
		checkUpdatePermissions();
		
		String	cmd = (String)args.get( "cmd" );
		
		if ( cmd == null ){
			
			throw( new IOException( "cmd missing" ));
		}
		
		try{
			if ( cmd.equals( "status" )){
				
				synchronized( lifecycle_lock ){
				
					result.put( "state", lifecycle_state );
				}
				
			}else if ( cmd.equals( "close" )){
				
				synchronized( lifecycle_lock ){
				
					if ( lifecycle_state < 2 ){
					
						lifecycle_state	= 2;
						
					}else{
						
						return;
					}
				}
				
				PluginManager.stopClient();
				
			}else if ( cmd.equals( "restart" )){
				
				synchronized( lifecycle_lock ){
					
					if ( lifecycle_state < 2 ){
					
						lifecycle_state	= 3;
						
					}else{
						
						return;
					}
				}
								
				PluginManager.restartClient();
				
			}else if ( cmd.equals( "update-check" )){
				
				synchronized( lifecycle_lock ){

					if ( lifecycle_state != 1 ){
						
						throw( new IOException( "update check can't currently be performed" ));
					}
					
					if ( update_in_progress ){
						
						throw( new IOException( "update operation in progress" ));
					}
					
					update_in_progress = true;
				}
				
				try{
					UpdateManager update_manager = plugin_interface.getUpdateManager();
					
					final UpdateCheckInstance	checker = update_manager.createUpdateCheckInstance();
					
					final List<String>	l_updates = new ArrayList<String>();
					
					final AESemaphore sem = new AESemaphore( "uc-wait" );
					
					checker.addListener(
						new UpdateCheckInstanceListener()
						{
							@Override
							public void
							cancelled(
								UpdateCheckInstance		instance )
							{
								sem.release();
							}
							
							@Override
							public void
							complete(
								UpdateCheckInstance		instance )
							{		
								try{
									Update[] 	updates = instance.getUpdates();
									
									for (int i=0;i<updates.length;i++){
										
										Update	update = updates[i];
																			
										l_updates.add( "Update available for '" + update.getName() + "', new version = " + update.getNewVersion());
												
										/*
										String[]	descs = update.getDescription();
										
										for (int j=0;j<descs.length;j++){
											
											out.println( "\t" + descs[j] );
										}
										
										if ( update.isMandatory()){
											
											out.println( "**** This is a mandatory update, other updates can not proceed until this is performed ****" );
										}
										*/
									}
									
										// need to cancel this otherwise it sits there blocking other installer operations
									
									checker.cancel();
									
								}finally{
									
									sem.release();
								}
							}
						});
					
					checker.start();
					
					sem.reserve();
					
					result.put( "updates", l_updates );
				
				}finally{
					
					synchronized( lifecycle_lock ){
						
						update_in_progress = false;
					}
				}
			}else if ( cmd.equals( "update-apply" )){
				
				synchronized( lifecycle_lock ){

					if ( lifecycle_state != 1 ){
						
						throw( new IOException( "update check can't currently be performed" ));
					}
					
					if ( update_in_progress ){
						
						throw( new IOException( "update operation in progress" ));
					}
					
					update_in_progress = true;
				}
				
				try{
					UpdateManager update_manager = plugin_interface.getUpdateManager();
					
					final UpdateCheckInstance	checker = update_manager.createUpdateCheckInstance();
					
					final AESemaphore sem = new AESemaphore( "uc-wait" );
	
					final Throwable[] 	error 		= { null };
					final boolean[]		restarting 	= { false };
					
					checker.addListener(
						new UpdateCheckInstanceListener()
						{
							@Override
							public void
							cancelled(
								UpdateCheckInstance		instance )
							{
								sem.release();
							}
							
							@Override
							public void
							complete(
								UpdateCheckInstance		instance )
							{
								Update[] 	updates = instance.getUpdates();
														
								try{
			
									for ( Update update: updates ){
										
										for ( ResourceDownloader rd: update.getDownloaders()){
											
											rd.addListener(
				 								new ResourceDownloaderAdapter()
				 								{
				 									@Override
												  public void
				 									reportActivity(
				 										ResourceDownloader	downloader,
				 										String				activity )
				 									{	
				 									}
				 									
				 									@Override
												  public void
				 									reportPercentComplete(
				 										ResourceDownloader	downloader,
				 										int					percentage )
				 									{			 												
				 									}
				 								});
											
											rd.download();
										}
									}
									
									boolean	restart_required = false;
									
									for (int i=0;i<updates.length;i++){
	
										if ( updates[i].getRestartRequired() == Update.RESTART_REQUIRED_YES ){
											
											restart_required = true;
										}
									}
									
									if ( restart_required ){
										
										synchronized( lifecycle_lock ){
											
											if ( lifecycle_state < 2 ){
										
												lifecycle_state	= 3;
												
											}else{
												
												return;
											}
										}
										
										PluginManager.restartClient();
										
										restarting[0] = true;
									}
								}catch( Throwable e ){
									
									error[0] = e;
									
								}finally{
									
									sem.release();
								}
							}
						});
					
					checker.start();
					
					sem.reserve();
	
					if ( error[0] != null ){
						
						throw( new IOException( "Failed to apply updates: " + Debug.getNestedExceptionMessage( error[0] )));
					}
					
					result.put( "restarting", restarting[0] );
					
				}finally{
					
					synchronized( lifecycle_lock ){
						
						update_in_progress = false;
					}
				}
			}else{
				
				throw( new IOException( "Unknown cmd: " + cmd ));
			}
		}catch( PluginException e ){
			
			throw( new IOException( "Lifecycle command failed: " + Debug.getNestedExceptionMessage(e)));
		}
	}
	
	private void
	processVuzePairing(
		Map<String,Object>	args,
		Map<String,Object>	result )
	
		throws IOException
	{
		checkUpdatePermissions();
				
		try{
			String	cmd = (String)args.get( "cmd" );
			
			if ( cmd == null ){
				
				throw( new IOException( "cmd missing" ));
			}
			
			PairingManager pm = PairingManagerFactory.getSingleton();

			if ( cmd.equals( "status" )){
				
				result.put( "status",  pm.getStatus());
				
				boolean enabled = pm.isEnabled();
				
				result.put( "enabled", enabled );
				
				if ( enabled ){
				
					result.put( "access_code", pm.peekAccessCode());
				}
				
				boolean srp_enabled = pm.isSRPEnabled();
				
				result.put( "srp_enabled", srp_enabled );
				
				if ( srp_enabled ){
					
					result.put( "srp_status", pm.getSRPStatus());
				}
			}else if ( cmd.equals( "set-enabled" )){
				
				boolean	enabled = (Boolean)args.get( "enabled" );
				
				if ( enabled != pm.isEnabled()){
					
					pm.setEnabled( enabled );
				}
			}else if ( cmd.equals( "set-srp-enabled" )){
				
				boolean	enabled = (Boolean)args.get( "enabled" );
				
				if ( enabled != pm.isSRPEnabled()){
					
					if ( enabled ){
						
						String	pw = (String)args.get( "password" );
						
						if ( pw == null ){
							
							throw( new IOException( "Password required when enabling SRP" ));
						}
						
						pm.setSRPEnabled( true );
						
						pm.setSRPPassword( pw.toCharArray());
						
					}else{
					
						pm.setSRPEnabled( false );
					}
				}
			}else{
				
				throw( new IOException( "Unknown cmd: " + cmd ));
			}
		}catch( IOException e ){
			
			throw( e );
		}
	}
	
	protected class
	PermissionDeniedException
		extends IOException
	{
		private static final long serialVersionUID = -344396020759893604L;		
	}
	
	protected String
	escapeXML(
		String	str )
	{
		if ( str == null ){
			
			return( "" );
			
		}
		str = str.replaceAll( "&", "&amp;" );
		str = str.replaceAll( ">", "&gt;" );
		str = str.replaceAll( "<", "&lt;" );
		str = str.replaceAll( "\"", "&quot;" );
		str = str.replaceAll( "--", "&#45;&#45;" );
		
		return( str );
	}
	
	private Number getTrackerID(TrackerPeerSource source) {
		return Long.valueOf((source.getName().hashCode() << 4l) + source.getType());
	}

	// Copy of RelatedContentManager.getURLList, except with Torrent (not TOTorrent)
	protected List
	getURLList(
		Torrent	torrent,
		String		key )
	{
		Object obj = torrent.getAdditionalProperty( key );
		
		if ( obj instanceof byte[] ){
			
            List l = new ArrayList();
            
	        l.add(obj);
	        
	        return( l );
	        
		}else if ( obj instanceof List ){
			
			return (List)BEncoder.clone(obj);
			
		}else{
			
			return( new ArrayList());
		}
	}

	private void hashAndAdd(SortedMap map, List<Map> addToList, List hcMatchList,
			int i) {
		long hashCode = longHashSimpleMap(map);
		// hex string shorter than long in json, even with quotes
		String hc = Long.toHexString(hashCode);

		boolean remove = hcMatchList != null && i < hcMatchList.size()
				&& hc.equals(hcMatchList.get(i));
		if (!remove) {
			map.put("hc", hc);
			addToList.add(map);
		}
	}

	/**
	 * Very simple 64 bit hash of a map's keys (assumed String, esp on JSON map),
	 * and values (JSON types -- String, Number, Map (object), List (array), Boolean.
	 */
	private long longHashSimpleMap(SortedMap<?, ?> map) {
		long hash = 0;
		
		Object hc = map.get("hc");
		if (hc instanceof String) {
			return Long.parseLong((String) hc, 16);
		}

		for (Object key : map.keySet()) {
			Object value = map.get(key);
			hash = (hash * 31) + hash(key.toString());
			if (value instanceof String) {
				hash = (hash * 31) + hash((String) value);
			} else if (value instanceof Number) {
				// not sure about this
				hash = (hash * 31) + ((Number) value).hashCode();
			} else if (value instanceof SortedMap) {
				hash = (hash * 31) + longHashSimpleMap((SortedMap) value);
			} else if (value instanceof Collection) {
				hash = (hash * 31) + longHashSimpleList((List) value);
			} else if (value instanceof Boolean) {
				hash = (hash * 31) + ((Boolean) value ? 1231 : 1237);
			} else {
				// else skip all other values since we can't be sure how they hash
				//System.out.println("Warning: Unhashed Value. key '" + key + "' Value: " + value);
			}
		}
		return hash;
	}

	private long longHashSimpleList(Collection<?> list) {
		long hash = 0;
		for (Object value : list) {
			if (value instanceof String) {
				hash = (hash * 31) + hash((String) value);
			} else if (value instanceof Number) {
				// not sure about this
				hash = (hash * 31) + ((Number) value).hashCode();
			} else if (value instanceof SortedMap) {
				hash = (hash * 31) + longHashSimpleMap((SortedMap) value);
			} else if (value instanceof Collection) {
				hash = (hash * 31) + longHashSimpleList((Collection) value);
			} else if (value instanceof Boolean) {
				hash = (hash * 31) + ((Boolean) value ? 1231 : 1237);
			} // else skip all other values since we can't be sure how they hash
		}
		return hash;
	}

	// FROM http://stackoverflow.com/questions/1660501/what-is-a-good-64bit-hash-function-in-java-for-textual-strings
	//adapted from String.hashCode()
	public static long hash(String string) {
		long h = 1125899906842597L; // prime
		int len = string.length();

		for (int i = 0; i < len; i++) {
			h = 31 * h + string.charAt(i);
		}
		return h;
	}

	//////////////////////////////////////////////////////////////////////////////
	
	private class
	RecentlyRemovedData
	{
		private final long			id;
		private final long			create_time = SystemTime.getMonotonousTime();
		//private final Set<String>	sessions = new HashSet<String>();
		
		private
		RecentlyRemovedData(
			long		_id )
		{
			id	= _id;
		}
		
		private long
		getID()
		{
			return( id );
		}
		
		private long
		getCreateTime()
		{
			return( create_time );
		}
		
		private boolean
		hasSession(
			String		session )
		{
			/*
			 * Actually it seems the webui doesn't consistently handle the removed-ids so just
			 * return the ID for a time period to ensure that it is processed.
			 * Update - might be that multiple clients in the same browser are using the same session id
			 * so going to go with reporting 'recently-removed' for a time period instead of just once
			 * per session 
			 * 
			synchronized( sessions ){
				
				if ( sessions.contains( session )){
					
					return( true );
					
				}else{
					
					sessions.add( session );
					
					return( false );
				}
			}
			*/
			return( false );
		}
	}
	
	private class
	MagnetDownload
		implements DownloadStub
	{
		private URL				magnet_url;
		private String			name;
		private byte[]			hash;
		private long			create_time;
		
		private Map<TorrentAttribute,Long>	attributes = new HashMap<TorrentAttribute, Long>();
		
		private String temp_dir = AETemporaryFileHandler.getTempDirectory().getAbsolutePath();
		
		private Throwable error;
		
		private
		MagnetDownload(
			URL		_magnet,
			String friendlyName )
		{
			create_time	= SystemTime.getCurrentTime();
			
			magnet_url	= _magnet;
			
			String	str = magnet_url.toExternalForm();
			
			int	pos = str.indexOf( '?' );
			
			if ( pos != -1 ){
				
				str = str.substring( pos+1 );
			}
			
			String[]	args = str.split( "&" );

			Map<String,String>	arg_map = new HashMap<String,String>();
			
			for ( String arg: args ){
				
				String[] bits = arg.split( "=" );
				
				if ( bits.length == 2 ){
					
					try{
						String lhs = bits[0].trim().toLowerCase( Locale.US );
						String rhs = URLDecoder.decode( bits[1].trim(), Constants.DEFAULT_ENCODING);
						
						if ( lhs.equals( "xt" )){
							
							if ( rhs.toLowerCase( Locale.US ).startsWith( "urn:btih:" )){
								
								arg_map.put( lhs, rhs );
								
							}else{
								
								String existing = arg_map.get( "xt" );
								
								if ( 	existing == null ||
										( !existing.toLowerCase( Locale.US ).startsWith( "urn:btih:" )  && rhs.startsWith( "urn:sha1:" ))){
									
									arg_map.put( lhs, rhs );
								}
							}
						}else{
							
							arg_map.put( lhs, rhs );
						}
					}catch( Throwable e ){
					}
				}
			}
			
			hash	= new byte[0];

			String hash_str = arg_map.get( "xt" );
			
			if ( hash_str != null ){
				
				hash_str = hash_str.toLowerCase( Locale.US );
				
				if ( hash_str.startsWith( "urn:btih:" ) || hash_str.startsWith( "urn:sha1" )){
					
					hash = UrlUtils.decodeSHA1Hash( hash_str.substring( 9 ));
				}
			}
			
			name	= arg_map.get( "dn" );
			
			if ( name == null ){
				
				if ( friendlyName != null ){

					name = friendlyName;

				} else if ( hash == null ){
					
					name = magnet_url.toExternalForm();
					
				}else{
					
					name = Base32.encode( hash );
				}
			}
			
			name = "Magnet download for '" + name + "'";
			
			getID( this, true );
		}
		
		private long
		getCreateTime()
		{
			return( create_time );
		}
		
		private URL
		getMagnetURL()
		{
			return( magnet_url );
		}
		
		@Override
		public boolean
		isStub()
		{
			return( true );
		}
		
		@Override
		public Download
		destubbify()
		
			throws DownloadException
		{
			throw( new DownloadException( "Not supported" ));
		}
		
		@Override
		public String
		getName()
		{
			return( name );
		}
		
		@Override
		public byte[]
		getTorrentHash()
		{
			return( hash );
		}
		
		@Override
		public Torrent
		getTorrent() 
		{
			return( null );
		}
		
		@Override
		public long
		getTorrentSize()
		{
			return( 16*1024 );	// dont know the size
		}
		
		@Override
		public String
		getSavePath()
		{
			return( temp_dir );
		}
		
		private void
		setError(
			Throwable e )
		{
			error	= e;
		}
		
		private Throwable
		getError()
		{
			return( error );
		}
		
		@Override
		public DownloadStubFile[]
		getStubFiles()
		{
			return( new DownloadStubFile[0]);
		}
		
		@Override
		public long
		getLongAttribute(
			TorrentAttribute 	attribute )
		{
			Long l = attributes.get( attribute );
			
			return( l==null?0:l );
		}
		  
		@Override
		public void
		setLongAttribute(
			TorrentAttribute 	attribute, 
			long 				value)
		{
			attributes.put( attribute, value );
		}
		  
		@Override
		public void
		remove()
		
			throws DownloadException, DownloadRemovalVetoException
		{
			
		}
	}
	
	@Override
	protected void log(String str) {
		// TODO Auto-generated method stub
		super.log(str);
	}
}
