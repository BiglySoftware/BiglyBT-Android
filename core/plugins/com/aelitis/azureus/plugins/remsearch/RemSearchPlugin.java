/*
 * Created on Sep 12, 2008
 * Created by Paul Gardner
 * 
 * Copyright 2008 Vuze, Inc.  All rights reserved.
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


package com.aelitis.azureus.plugins.remsearch;

import java.io.IOException;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.Average;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginListener;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.ui.config.IntParameter;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pif.utils.LocaleUtilities;
import com.biglybt.pif.utils.UTTimer;
import com.biglybt.pif.utils.UTTimerEvent;
import com.biglybt.pif.utils.UTTimerEventPerformer;

import com.biglybt.core.metasearch.*;
import com.biglybt.core.util.CopyOnWriteList;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;

public class 
RemSearchPlugin
	implements Plugin
{
	private static final int		PORT_DEFAULT					= 8888;
	private static final int		SEARCH_TIMEOUT_DEFAULT			= 45*1000;
	private static final int		MAX_SEARCHES_DEFAULT			= 100;
	private static final int		MAX_RESULTS_PER_ENGINE_DEFAULT	= 50;
	private static final String		URL_PREFIX_DEFAULT				= "/psearch";
			
	private static final int		TICK_PERIOD						= 15*1000;
	private static final int		HISTORY_CLEAR_PERIOD			= 300*1000;
	private static final int		HISTORY_CLEAR_PER_TICK			= RemSearchPluginEngine.ENGINE_HISTORY_SIZE * TICK_PERIOD / HISTORY_CLEAR_PERIOD;
			
	private IntParameter		search_timeout;
	private IntParameter		max_searches;
	private IntParameter		max_results_per_engine;
		
	private PluginInterface	plugin_interface;
	
	private LoggerChannel	logger;
	
	private String	url_prefix;
	
	private RemSearchPluginStats		stats = new RemSearchPluginStats( this );
		
	private static final int	STATS_PERIOD		= 1000;
	private static final int 	STATS_DURATION_SECS	= 30;			// 30 second average

	private Average	search_average 		= Average.getInstance(STATS_PERIOD,STATS_DURATION_SECS);
	
	private static final int BLOOM_FILTER_SIZE		= 256*1024;
	private static final int BLOOM_ROTATION_PERIOD 	= 1*60*1000 / 2;	// two blooms in rotation so 1 minute life = 2*30 secs
	private static final int BLOOM_ROTATION_TICKS 	= BLOOM_ROTATION_PERIOD / TICK_PERIOD;
	private static final int BLOOM_MAX_HITS_PER_PERIOD	= 15;	// 

	private Object		bloom_lock	= new Object();
	private BloomFilter bloom_one 	= BloomFilterFactory.createAddRemove8Bit( BLOOM_FILTER_SIZE );
	private BloomFilter bloom_two 	= BloomFilterFactory.createAddRemove8Bit( BLOOM_FILTER_SIZE );

	private String	host_name = "relay";

	private CopyOnWriteList	listeners = new CopyOnWriteList();
	
	private RemSearchPluginPageGenerator	generator;
	
	public void
	load(
		PluginInterface	pi )
	{
		COConfigurationManager.setParameter( "Tracker IP", "127.0.0.1" );
		
		COConfigurationManager.setParameter( 
				"Tracker Port", 
				COConfigurationManager.getIntParameter( "Plugin.aeremsearch.aeremsearch.config.http_port", PORT_DEFAULT ));
		
		COConfigurationManager.setParameter( "Tracker Port Enable", true );
		
		COConfigurationManager.setParameter( "Tracker TCP NonBlocking", true );
		
		COConfigurationManager.setParameter( "Tracker TCP NonBlocking Immediate Close", true );
		
		COConfigurationManager.setParameter( "Tracker TCP NonBlocking Restrict Request Types", false );
	}
	
	@Override
	public void
	initialize(
		PluginInterface 	_plugin_interface )
	
		throws PluginException 
	{
		plugin_interface	= _plugin_interface;
		
		logger				= plugin_interface.getLogger().getChannel( "RemoteSearch" ); 

		logger.setDiagnostic();
		
		LocaleUtilities loc_utils = plugin_interface.getUtilities().getLocaleUtilities();

		loc_utils.integrateLocalisedMessageBundle( "com.aelitis.azureus.plugins.remsearch.internat.Messages" );

		BasicPluginConfigModel config_model = 
			plugin_interface.getUIManager().createBasicPluginConfigModel( "aeremsearch.name" );

		config_model.addIntParameter2( "aeremsearch.config.http_port", "aeremsearch.config.http_port", PORT_DEFAULT );

		
		search_timeout 			= config_model.addIntParameter2( "aeremsearch.config.search_timeout", "aeremsearch.config.search_timeout", SEARCH_TIMEOUT_DEFAULT );
		max_searches			= config_model.addIntParameter2( "aeremsearch.config.max_searches", "aeremsearch.config.max_searches", MAX_SEARCHES_DEFAULT );
		max_results_per_engine	= config_model.addIntParameter2( "aeremsearch.config.max_results_per_engine", "aeremsearch.config.max_results_per_engine", MAX_RESULTS_PER_ENGINE_DEFAULT );

		url_prefix 	= config_model.addStringParameter2( "aeremsearch.config.url_prefix", "aeremsearch.config.url_prefix", URL_PREFIX_DEFAULT ).getValue();
		
		if ( url_prefix.endsWith( "/" )){
			
			url_prefix = url_prefix.substring( 0, url_prefix.length()-1);
		}
			
		String host = System.getProperty( "az.hostname" );
		
		if ( host != null && host.length() > 0 ){
			
			host_name = host;
		}
		
		generator =
			new RemSearchPluginPageGenerator(
				new RemSearchPluginPageGeneratorAdaptor()
				{
					@Override
					public void
					searchReceived(
						String originator )
							
						throws IOException 
					{
						byte[]	bloom_key = originator.getBytes();
						
						synchronized( bloom_lock ){
												
							bloom_two.add( bloom_key );
							
							if ( bloom_one.add( bloom_key ) > BLOOM_MAX_HITS_PER_PERIOD ){
								
								throw( new IOException( "Too many recent searches from " + originator ));
							}				
						}
					}
						
					@Override
					public void
					searchCreated(
						RemSearchPluginSearch search )
					{
						Iterator<RemSearchPluginListener> it = listeners.iterator();
						
						while( it.hasNext()){
						
							try{
								it.next().searchCreated(search);
								
							}catch( Throwable e ){
								
								e.printStackTrace();
							}
						}
					}
					
					@Override
					public void
					log(
						String str )
					{
						RemSearchPlugin.this.log( str );
					};
					
					@Override
					public void
					log(
						String 		str,
						Throwable 	e )
					{
						RemSearchPlugin.this.log( str, e );
					};
				},
				url_prefix,
				host_name,
				max_searches.getValue(),
				max_results_per_engine.getValue(),
				true );
		
		plugin_interface.addListener(
			new PluginListener()
			{
				@Override
				public void
				initializationComplete()
				{
					MetaSearchManager meta_search 		= MetaSearchManagerFactory.getSingleton();
					
					if ( !meta_search.isAutoMode()){
						
						try{
							meta_search.setSelectedEngines( new long[0], true );
							
						}catch( Throwable e ){
							
							log( "Failed to set auto-mode", e );
						}
					}

					plugin_interface.getTracker().addPageGenerator( generator ); 

					UTTimer timer = plugin_interface.getUtilities().createTimer( "Search timer", true );
					
					// stats
					
					timer.addPeriodicEvent(
							STATS_PERIOD,
							new UTTimerEventPerformer()
							{
								private long last_total_searches;
								
								@Override
								public void
								perform(
									UTTimerEvent event ) 
								{
										// track searches per minute
									
									long ts = generator.getTotalSearches();
									
									search_average.addValue( ts - last_total_searches );

									last_total_searches = ts;
								}
							});

					
					timer.addPeriodicEvent(
							TICK_PERIOD,
							new UTTimerEventPerformer()
							{
								private int	tick_count;
								
								@Override
								public void
								perform(
									UTTimerEvent event ) 
								{
									tick_count++;
									
										// timeouts
									
									int	timeout = search_timeout.getValue();
																		
									Map<String,RemSearchPluginSearch> searches = generator.getSearches();
								
									Iterator<RemSearchPluginSearch> it = searches.values().iterator();
										
									while( it.hasNext()){
									
										RemSearchPluginSearch search = it.next();
										
										if ( search.getAge() > timeout ){
																							
											log( "Timeout: " + search.getString());
											
											search.destroy();
										}
									}
									
									String	history_str = "";
									
									Map<Engine, RemSearchPluginEngine> engine_map = generator.getEngineMap();
									
									for (Map.Entry<Engine,RemSearchPluginEngine> entry: engine_map.entrySet()){
											
										Engine 					engine		= entry.getKey();
										RemSearchPluginEngine	p_engine 	= entry.getValue();
										
										LinkedList<Boolean>	history = p_engine.getHistory();
										
										int	good 		= 0;
										int	first_good	= -1;
										int	bad			= 0;
										int	first_bad	= -1;
										
										int	pos = 0;
										
										for ( boolean success: history ){
												
											pos++;
											
											if ( success ){
												
												good++;
												
												if ( first_good == -1 ){
													
													first_good = pos;
												}
											}else{
												
												bad++;
												
												if ( first_bad == -1 ){
													
													first_bad = pos;
												}
											}
										}
										
										history_str += history_str.length()==0?"":"; ";
										
										history_str += 
											engine.getName() + 
											"={h:" + history.size() + 
											",g=" + good +
											",b=" + bad +
											",fg=" + first_good +
											",fb=" + first_bad + 
											",rta=" + p_engine.getResponseTimeAverage() + "}";
										
										if ( history.size() <= HISTORY_CLEAR_PER_TICK ){
											
											history.clear();
											
										}else{
											
											for (int i=0;i<HISTORY_CLEAR_PER_TICK;i++){
												
												history.removeLast();
											}
										}
									}
										
									log( "History: search/sec=" + search_average.getDoubleAverageAsString(3) + ", " + history_str );

									if ( tick_count % BLOOM_ROTATION_TICKS == 0 ){
										
										synchronized( bloom_lock ){
										
											log( "Bloom rotate: one=" + bloom_one.getEntryCount() + ", two=" + bloom_two.getEntryCount());
											
											bloom_one	= bloom_two;
											
											bloom_two 	= BloomFilterFactory.createAddRemove8Bit( BLOOM_FILTER_SIZE );
										}
									}									
								}								
							});		
				}
				
				@Override
				public void
				closedownInitiated()
				{
					
				}
				
				@Override
				public void
				closedownComplete()
				{
					
				}
			});
	}
	
	public RemSearchPluginEngine[]
	getEngines()
	{
		return( generator.getEngines());
	}
	
	public RemSearchPluginStats
	getStats()
	{
		return( stats );
	}
	
	public long
	getTotalSearches()
	{
		return( generator.getTotalSearches());
	}
	
	public long
	getTotalSearchesFailed()
	{
		return( generator.getTotalSearchesFailed());
	}
	
	public long
	getTotalEnginesFailed()
	{
		return( generator.getTotalEnginesFailed());
	}	

	
	public void
	addListener(
		RemSearchPluginListener		listener )
	{
		listeners.add( listener );
	}
	
	public void
	removeListener(
		RemSearchPluginListener		listener )
	{
		listeners.remove( listener );
	}
	
	protected void
	log(
		String		str )
	{
		logger.log( str );
	}
	
	protected void
	log(
		String		str,
		Throwable 	e )
	{
		logger.log( str, e );
	}
}
