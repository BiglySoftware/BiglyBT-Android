/*
 * Created on May 10, 2010
 * Created by Paul Gardner
 * 
 * Copyright 2010 Vuze, Inc.  All rights reserved.
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



package com.aelitis.plugins.rcmplugin;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.util.*;
import com.biglybt.core.util.protocol.azplug.AZPluginConnection;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.UnloadablePlugin;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ipc.IPCException;
import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pif.utils.LocaleUtilities;
import com.biglybt.pif.utils.Utilities;
import com.biglybt.pif.utils.search.*;

import com.biglybt.core.content.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.UserPrompterResultListener;


public class 
RCMPlugin 
	implements UnloadablePlugin
{
	protected static final int MIN_SEARCH_RANK_DEFAULT = 0;

	public static  final String PARAM_SOURCES_LIST = "Plugin.aercm.sources.setlist";

	public static  final String PARAM_SOURCES_ISDEFAULT = "Plugin.aercm.sources.isdefault";

	public static final String PARAM_FTUX_SHOWN = "rcm.ftux.shown2";

	public static final String POPULARITY_SEARCH_EXPR	= "(.)";

	private static final boolean	SEARCH_ENABLE_DEFAULT	= true;
	
	static{
		COConfigurationManager.setParameter( "rcm.persist", true );
		
		new RCMPatcher();
	}
	
	private PluginInterface			plugin_interface;
	
	private RelatedContentUI		ui;
	private SearchProvider 			search_provider;

	private boolean					destroyed;
	
	List<String>	source_map_defaults = new ArrayList<String>();
	private ParameterListener configSourcesListListener;

	{
		source_map_defaults.add( "vhdn.vuze.com" );
		source_map_defaults.add( "tracker.vodo.net" );
		source_map_defaults.add( "bt.archive.org" );
		source_map_defaults.add( "tracker.legaltorrents.com" );
		source_map_defaults.add( "tracker.mininova.org" );
		// End of original list
		source_map_defaults.add( "www.legaltorrents.com" );
		source_map_defaults.add( "torrent.ubuntu.com" );
		source_map_defaults.add( "torrents.freebsd.org" );
		source_map_defaults.add( "torrent.fedoraproject.org" );
		source_map_defaults.add( "tracker.opensuse.org" );
		source_map_defaults.add( "torrents.linuxmint.com" );
		source_map_defaults.add( "tracker.johncave.co.nz" ); // OpenMandriva
		source_map_defaults.add( "academictorrents.com" );
	}
	
	private ByteArrayHashMap<Boolean>	source_map 	= new ByteArrayHashMap<Boolean>();
	private boolean						source_map_wildcard;
	private byte[]						source_vhdn = compressDomain( "vhdn.vuze.com" );
	
	private RCM_JSONServer json_rpc_server;
	
	private byte[]
	compressDomain(
		String	host )
	{
		String[] bits = host.split( "\\." );
		
		int	len = bits.length;
		
		if ( len < 2 ){
			
			bits = new String[]{ bits[0], "com" };
		}
					
		String	end = bits[len-1];
							
		String dom = bits[len-2] + "." + end;
				
		int hash = dom.hashCode();
				
		byte[]	bytes = { (byte)(hash>>24), (byte)(hash>>16),(byte)(hash>>8),(byte)hash };

		return( bytes );
	}

	
	@Override
	public void
	initialize(
		final PluginInterface _plugin_interface )
	
		throws PluginException
	{
		plugin_interface = _plugin_interface;
			

		// ensure source list is up to date if marked as default
		boolean isDefaultList = COConfigurationManager.getBooleanParameter(PARAM_SOURCES_ISDEFAULT, false);
		if (isDefaultList) {
			setToDefaultSourcesList();
		} else {
  		// Upgrade source list if it's a subset of default list (ie. older version)
  		// Should be done before anything hooks PARAM_SOURCES_LIST, since
  		// it may fire a config change
  		List<String> list = getSourcesList();
  		if (source_map_defaults.containsAll(list)) {
  			setToDefaultSourcesList();
  		}
		}

		configSourcesListListener = new ParameterListener() {
			@Override
			public void
			parameterChanged(
					String name) {
				updateSourcesList();
			}
		};
		COConfigurationManager.addAndFireParameterListener(
				PARAM_SOURCES_LIST,
				configSourcesListListener);
		
		LocaleUtilities loc_utils = plugin_interface.getUtilities().getLocaleUtilities();

		loc_utils.integrateLocalisedMessageBundle( "com.aelitis.plugins.rcmplugin.internat.Messages" );

		hookSearch();
		
		updatePluginInfo();

		plugin_interface.getUIManager().addUIListener(
			new UIManagerListener()
			{
				@Override
				public void
				UIAttached(
					UIInstance instance) 
				{
					if ( instance.getUIType().equals(UIInstance.UIT_SWT) ){
						
						synchronized( RCMPlugin.this ){

							if ( destroyed ){

								return;
							}

							try{
								Class cla = RCMPlugin.class.forName( "com.aelitis.plugins.rcmplugin.RelatedContentUISWT" );
							
								ui = (RelatedContentUI)cla.getMethod( "getSingleton", PluginInterface.class, UIInstance.class, RCMPlugin.class ).invoke(
										null, plugin_interface, instance, RCMPlugin.this );
								
							}catch( Throwable e ){
								
								Debug.out( e );
							}
						}
					}
				}
				
				@Override
				public void
				UIDetached(
					UIInstance		instance )
				{
					if ( instance.getUIType().equals(UIInstance.UIT_SWT) && ui != null ){
						ui.destroy();
						ui = null;
					}

				}
			});
		
		json_rpc_server = new RCM_JSONServer(this);
			
		plugin_interface.getUtilities().registerJSONRPCServer(json_rpc_server);
	}

	protected void
	updatePluginInfo()
	{
		String plugin_info;
		
		if ( !hasFTUXBeenShown()){
			
			plugin_info = "f";
			
		}else if ( isRCMEnabled()){
		
			plugin_info = "e";
			
		}else{
			
			plugin_info = "d";
		}
		
		PluginConfig pc = plugin_interface.getPluginconfig();
		
		if ( !pc.getPluginStringParameter( "plugin.info", "" ).equals( plugin_info )){
			
			pc.setPluginParameter( "plugin.info", plugin_info );
		
			COConfigurationManager.save();
		}
	}
	
	protected boolean
	isRCMEnabled()
	{
		return( COConfigurationManager.getBooleanParameter( "rcm.overall.enabled", true ));
	}
	
	protected boolean
	setRCMEnabled(
		boolean	enabled )
	{
		if ( isRCMEnabled() != enabled ){
			
			COConfigurationManager.setParameter( "rcm.overall.enabled", enabled );
						
			hookSearch();
			
			updatePluginInfo();

			return true;
		}
		
		return false;
	}
	
	protected boolean
	hasFTUXBeenShown()
	{
		return( plugin_interface.getPluginconfig().getPluginBooleanParameter( PARAM_FTUX_SHOWN, false ));
	}

	protected void
	setFTUXBeenShown(
		boolean b )
	{
		plugin_interface.getPluginconfig().setPluginParameter( PARAM_FTUX_SHOWN, b );
				
		hookSearch();
		
		updatePluginInfo();
	}
	
	protected boolean
	isUIEnabled() {
		return( plugin_interface.getPluginconfig().getPluginBooleanParameter( "rcm.ui.enable", false ));
	}

	protected void
	setUIEnabled(
		boolean b )
	{
		plugin_interface.getPluginconfig().setPluginParameter( "rcm.ui.enable", b );
	}

	protected boolean
	isSearchEnabled()
	{
		return( plugin_interface.getPluginconfig().getPluginBooleanParameter( "rcm.search.enable", SEARCH_ENABLE_DEFAULT ));
	}


	protected void
	setSearchEnabled(
		boolean b )
	{
		plugin_interface.getPluginconfig().setPluginParameter( "rcm.search.enable", b );
	}

	protected int
	getMinimumSearchRank()
	{
		return( plugin_interface.getPluginconfig().getPluginIntParameter( "rcm.search.min_rank", MIN_SEARCH_RANK_DEFAULT ));
	}
	
	public SearchProvider
	getSearchProvider()
	{
		return( search_provider );
	}
	
	public PluginInterface
	getPluginInterface()
	{
		return( plugin_interface );
	}
	
	protected void
	hookSearch()
	{		
		boolean enable = isRCMEnabled() && isSearchEnabled() && ( SEARCH_ENABLE_DEFAULT || hasFTUXBeenShown());
		
		try{
				
			Utilities utilities = plugin_interface.getUtilities();
			
			if ( enable ){
			
				if ( search_provider == null ){
					
					search_provider = new RCM_SearchProvider( this );
						
					utilities.registerSearchProvider( search_provider );
				}
			}else{
				
				if ( search_provider != null ) {
					
					utilities.unregisterSearchProvider( search_provider );
					
					search_provider = null;
				}
			}
		}catch( Throwable e ){
			
			Debug.out( "Failed to register/unregister search provider", e );
		}
	}
	
	private void
	updateSourcesList()
	{
		List<String>	list = getSourcesList();
		
		source_map.clear();
		source_map_wildcard	= false;
		
		for( String host: list ){
			
			if ( host.equals( "*" )){
				
				source_map_wildcard = true;
				
			}else{
				
				source_map.put( compressDomain( host ), Boolean.TRUE );
			}
		}
		
		boolean isDefaultList = list.size() == source_map_defaults.size()
				&& list.containsAll(source_map_defaults);
		COConfigurationManager.setParameter(PARAM_SOURCES_ISDEFAULT, isDefaultList);
	}
	
	public List<String>
	getSourcesList()
	{
		List original_list = COConfigurationManager.getListParameter( PARAM_SOURCES_LIST, source_map_defaults );
		
		List<String>	list = BDecoder.decodeStrings( BEncoder.cloneList(original_list) );
		
		return( list );
	}
	
	public void setToDefaultSourcesList() {
		COConfigurationManager.setParameter(PARAM_SOURCES_LIST, source_map_defaults);
	}
	
	public void setToAllSources() {
		COConfigurationManager.setParameter(PARAM_SOURCES_LIST, Arrays.asList("*"));
	}
	
	public boolean isDefaultSourcesList() {
		return COConfigurationManager.getBooleanParameter(PARAM_SOURCES_ISDEFAULT, false);
	}
	
	public boolean isAllSources() {
		return source_map_wildcard;
	}
	
	public boolean showHitCounts()
	{
		return( source_map_wildcard || !hasFTUXBeenShown());
	}
	
	public boolean
	isVisible(
		byte[]	key_list )
	{
		if ( source_map_wildcard ){
			
			return( true );
		}
		
		if ( key_list != null ){
			
			for ( int i=0;i<key_list.length;i+=4 ){
				
				Boolean b = source_map.get( key_list, i, 4 );
				
				if ( b != null ){
					
					if ( b ){
						
						return( true );
					}
				}
			}
		}
		
		return( false );
	}
	
	public boolean
	isVisible(
		RelatedContent	related_content )
	{
		if ( source_map_wildcard ){
			
			return( true );
		}
		
		byte[] tracker_keys = related_content.getTrackerKeys();

		if ( isVisible( tracker_keys )){
			
			return( true );
		}
		
		byte[] ws_keys = related_content.getWebSeedKeys();
		
		return( isVisible( ws_keys ));
	}
	
	@Override
	public void
	unload() 
	
		throws PluginException 
	{
		synchronized( this ){
			
			if ( destroyed ){
				
				return;
			}
			
			destroyed = true;
		}
		
		if ( ui != null ){
			
			ui.destroy();
			
			ui = null;
		}
		
		if ( search_provider != null ){

			try{
				plugin_interface.getUtilities().unregisterSearchProvider( search_provider );

				search_provider = null;
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
		
		if ( json_rpc_server != null ){
			
			plugin_interface.getUtilities().unregisterJSONRPCServer(json_rpc_server);
			
			json_rpc_server.unload();
			
			json_rpc_server = null;
		}

		COConfigurationManager.removeParameterListener(PARAM_SOURCES_LIST,
				configSourcesListListener);
	}
	
		// IPC methods
	
	public void
	lookupByDownload(
		final Download	download )
	
		throws IPCException
	{
		RelatedContentUI current_ui = ui;
		
		if ( current_ui == null ){
			
			throw( new IPCException( "UI not bound" ));
		}
		
		if ( !hasFTUXBeenShown() || !isRCMEnabled()){
			
			current_ui.showFTUX( 
				new UserPrompterResultListener()
				{
					@Override
					public void
					prompterClosed(
						int result) 
					{
						if ( isRCMEnabled()){
							
							RelatedContentUI current_ui = ui;
							
							if ( current_ui != null ){
							
								current_ui.setUIEnabled( true );
								
								current_ui.addSearch( download );
							}
						}
					}
				});
		}else{
			
			current_ui.setUIEnabled( true );
			
			current_ui.addSearch( download );
		}
	}
	
	public void
	lookupBySize(
		final long	size )
	
		throws IPCException
	{
		lookupBySize( size, new String[]{ AENetworkClassifier.AT_PUBLIC });
	}
	
	public void
	lookupBySize(
		final long		size,
		final String[]	networks )
	
		throws IPCException
	{
		RelatedContentUI current_ui = ui;
		
		if ( current_ui == null ){
			
			throw( new IPCException( "UI not bound" ));
		}
		
		if ( !hasFTUXBeenShown() || !isRCMEnabled()){
			
			current_ui.showFTUX(
				new UserPrompterResultListener()
				{
					@Override
					public void
					prompterClosed(
						int result) 
					{
						if ( isRCMEnabled()){
							
							RelatedContentUI current_ui = ui;
							
							if ( current_ui != null ){
							
								current_ui.setUIEnabled( true );
								
								current_ui.addSearch( size, networks );
							}
						}
					}
				});
		}else{
			
			current_ui.setUIEnabled( true );
			
			current_ui.addSearch( size, networks );
		}
	}
	
	protected void
	searchReceived(
		Map<String,Object>	search_parameters )
	{
		if ( !hasFTUXBeenShown()){
			
				// before showing FTUX we show potential search results in the sidebar so that users
				// know they exist. Once FTUX has been shown the user has decided what they want to
				// see so this is no longer needed
			
			String[]	networks = (String[])search_parameters.get( SearchProvider.SP_NETWORKS );
	
			String	target_net = AENetworkClassifier.AT_PUBLIC;
	
			if ( networks != null ){
	
				for ( String net: networks ){
	
					if ( net == AENetworkClassifier.AT_PUBLIC ){
	
						target_net = AENetworkClassifier.AT_PUBLIC;
	
						break;
	
					}else if ( net == AENetworkClassifier.AT_I2P ){
	
						target_net = AENetworkClassifier.AT_I2P;
					}
				}
			}
			
			final String	term = (String)search_parameters.get( SearchProvider.SP_SEARCH_TERM );
	
			Map<String,Object> options = new HashMap<>();
			
			options.put( "No Focus", true );
			
			try{
				lookupByExpression( term, new String[]{ target_net }, options );
				
			}catch( Throwable e ){
				
			}
			
		}
	}
	
	public void
	lookupByExpression(
		String expression )
	
		throws IPCException
	{
		lookupByExpression( expression, new String[]{ AENetworkClassifier.AT_PUBLIC });
	}
	
	public void
	lookupByExpression(
		final String 	expression,
		final String[]	networks )
	
		throws IPCException
	{
		lookupByExpression( expression, networks, new HashMap<String,Object>());
	}
	
	public void
	lookupByExpression(
		final String 				expression,
		final String[]				networks,
		final Map<String,Object>	options )
	
		throws IPCException
	{
		RelatedContentUI current_ui = ui;
		
		if ( current_ui == null ){
			
			throw( new IPCException( "UI not bound" ));
		}
			
		Boolean	_no_focus = (Boolean)options.get( "No Focus" );
		
		boolean no_focus = _no_focus != null && _no_focus;
		
		final Runnable do_it = 
			new Runnable()
			{
				@Override
				public void
				run()
				{
					if ( isRCMEnabled()){

						Boolean b_is_subscription = (Boolean)options.get( "Subscription" );
						
						boolean is_subscription = b_is_subscription != null && b_is_subscription;
	
						if ( is_subscription ){
							
							Map<String,Object>	properties = new HashMap<String, Object>();
								
							String name = (String)options.get( "Name" );
							
							if ( name == null ){
								
								name = expression;
							}
							
							properties.put( SearchProvider.SP_SEARCH_NAME, name );
							properties.put( SearchProvider.SP_SEARCH_TERM, expression );
							properties.put( SearchProvider.SP_NETWORKS, networks );
							
							properties.put( "_frequency_", 10 );
							
							try{
								getPluginInterface().getUtilities().getSubscriptionManager().requestSubscription(
									getSearchProvider(),
									properties );
									
							}catch( Throwable e ){
									
								Debug.out( e );
							}
						}else{
							
							RelatedContentUI current_ui = ui;

							if ( current_ui != null ){
								
								current_ui.setUIEnabled( true );
							
								current_ui.addSearch( expression, networks, no_focus );
							}
						}
					}
				}
			};
			
		if ( no_focus ){
			
			do_it.run();
			
		}else{
			
			if ( !hasFTUXBeenShown() || !isRCMEnabled()){
				
				current_ui.showFTUX(
					new UserPrompterResultListener()
					{
						@Override
						public void
						prompterClosed(
							int result) 
						{									
							do_it.run();
						}
					});
			}else{
				
				do_it.run();
			}
		}
	}
	
	public void
	lookupByHash(
		final byte[]	hash,
		final String	name )
	
		throws IPCException
	{
		lookupByHash( hash, new String[]{ AENetworkClassifier.AT_PUBLIC }, name );
	}

	public void
	lookupByHash(
		final byte[]	hash,
		final String[]	networks,
		final String	name )
	
		throws IPCException
	{
		RelatedContentUI current_ui = ui;
		
		if ( current_ui == null ){
			
			throw( new IPCException( "UI not bound" ));
		}
		
		if ( !hasFTUXBeenShown() || !isRCMEnabled()){
			
			current_ui.showFTUX( 
				new UserPrompterResultListener()
				{
					@Override
					public void
					prompterClosed(
						int result) 
					{
						if ( isRCMEnabled()){
							
							RelatedContentUI current_ui = ui;
							
							if ( current_ui != null ){
							
								current_ui.setUIEnabled( true );
								
								current_ui.addSearch( hash, networks, name );
							}
						}
					}
				});
		}else{
			
			current_ui.setUIEnabled( true );
			
			current_ui.addSearch( hash, networks, name );
		}
	}
	
	public InputStream
	handleURLProtocol(
		AZPluginConnection			connection,
		String						arg_str )

		throws IPCException
	{
		try{
			String [] bits = arg_str.split( "&" );
			
			long			size 	= -1;
			byte[]			hash 	= null;
			List<String>	nets 	= new ArrayList<>();
			List<String>	exprs 	= new ArrayList<>();
			
			for ( String bit: bits ){
				
				String[] temp = bit.split( "=" );
				
				String lhs = temp[0];
				String rhs = UrlUtils.decode( temp[1] );
				
				if ( lhs.equals( "net" )){
					nets.add( AENetworkClassifier.internalise( rhs ));
				}else if ( lhs.equals( "expr" )){
					exprs.add( rhs );
				}else if ( lhs.equals( "size" )){
					size = Long.parseLong( rhs );
				}else if ( lhs.equals( "hash" )){
					hash = Base32.decode( rhs );
				}
			}
			
			if ( nets.isEmpty()){
				
				nets.add( AENetworkClassifier.AT_PUBLIC );
			}
			
			String[] networks = nets.toArray( new String[ nets.size() ]);
			
			if ( hash != null ){
				
				lookupByHash( hash, networks, MessageText.getString( "RCM.column.rc_hash" ) + ": " + ByteFormatter.encodeString( hash ));
				
			}else if ( size != -1 ){
				
				lookupBySize(	size, networks );
				
			}else if ( !exprs.isEmpty()){
				
					// meh, just pick first atm 
				
				lookupByExpression( exprs.get(0), networks );
			}
			
			return( new ByteArrayInputStream( VuzeFileHandler.getSingleton().create().exportToBytes() ));
			
		}catch( Throwable e ){

			throw( new IPCException( e ));
		}
	}
	
	public static String
	getNetworkString(
		String[]		networks )
	{
		if ( networks == null || networks.length == 0 ){
			
			return( "" );
			
		}else if ( networks.length == 1 ){
			
			if ( networks[0] != AENetworkClassifier.AT_PUBLIC ){
				
				return( " [" + networks[0] + "]" );
				
			}else{
				
				return( "" );
			}
		}else{
	
			String str = "";
			
			for ( String net: networks ){
				
				str += (str.length()==0?"":",") + net;
			}
			
			return( " [" + str + "]" );
		}
	}


	public static String
	getMagnetURI(
		RelatedContent		rc )
	{
		String uri = UrlUtils.getMagnetURI( rc.getHash(), rc.getTitle(), rc.getNetworks());
		
		String[] tags = rc.getTags();
		
		if ( tags != null ){
			
			for ( String tag: tags ){
				
				uri += "&tag=" + UrlUtils.encode( tag );
			}
		}
		
		return( uri );
	}
	
	public boolean isDestroyed() {
		return destroyed;
	}
}
