/*
 * Created on Jan 27, 2010
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


package com.aelitis.azureus.plugins.remsearch;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.biglybt.core.util.Debug;
import com.biglybt.pif.tracker.web.TrackerWebPageGenerator;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import org.json.simple.JSONObject;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.MetaSearchManager;
import com.biglybt.core.metasearch.MetaSearchManagerFactory;
import com.biglybt.core.metasearch.SearchParameter;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionHistory;
import com.biglybt.core.subs.SubscriptionManagerFactory;
import com.biglybt.core.subs.SubscriptionResult;

public class 
RemSearchPluginPageGenerator
	implements TrackerWebPageGenerator
{
	private final boolean	MATURE_DEFAULT	= false;

	private final String SEARCH_PREFIX;
	private final String RESULTS_PREFIX;

	private final String	url_prefix;
	private final String	host_name;
	private final int		max_searches;
	private final int		max_results_per_engine;

	private RemSearchPluginPageGeneratorAdaptor		adapter;
	
	private Map<String,RemSearchPluginSearch>	searches = new HashMap<String, RemSearchPluginSearch>();

	private Map<Engine, RemSearchPluginEngine>	engine_map 		= new HashMap<Engine, RemSearchPluginEngine>();
	private Map<String, RemSearchPluginEngine>	engine_eid_map 	= new HashMap<String, RemSearchPluginEngine>();

	private long	total_searches;
	private long	total_fails;
	private long	total_engine_fails;
	private boolean	supports_async;
			
	private Random random = new Random();
	
	public
	RemSearchPluginPageGenerator(
		RemSearchPluginPageGeneratorAdaptor		_adapter,
		String									_url_prefix,
		String									_host_name,
		int										_max_searches,
		int										_max_results_per_engine,
		boolean									_supports_async )
	{
		adapter					= _adapter;
		url_prefix				= _url_prefix;
		host_name				= _host_name;
		max_searches			= _max_searches;
		max_results_per_engine	= _max_results_per_engine;
		supports_async			= _supports_async;
		
		SEARCH_PREFIX 	= _url_prefix + "/search?";
		RESULTS_PREFIX 	= _url_prefix + "/get-results?";
	}
	
	protected MetaSearchManager
	getMetaSearchManager()
	{
		return( MetaSearchManagerFactory.getSingleton());
	}
	
	protected boolean
	supportsAsync()
	{
		return( supports_async );
	}
	
	@Override
	public boolean
	generate(
		TrackerWebPageRequest			request,
		TrackerWebPageResponse			response )
	
		throws IOException
	{
		String	url = request.getURL();
		
		log( "HTTP request from " + request.getClientAddress());
			
		boolean		json_output = false;

		if ( url.startsWith( SEARCH_PREFIX )){
			
			String[]	args = url.substring( SEARCH_PREFIX.length()).trim().split("&");
			
			String 	term 	= null;
			boolean	mature	= MATURE_DEFAULT;
			
			for (int i=0;i<args.length;i++){
				
				String[] bits = args[i].split("=", 2 );
				
				if ( bits.length != 2 ){
					
					continue;
				}
				
				String	lhs = bits[0].toLowerCase();
				String	rhs = URLDecoder.decode( bits[1], "UTF-8" );
				
				if ( lhs.equals( "q" )){
					
					term = rhs;
					
				}else if ( lhs.equals( "mature" )){
					
					mature = rhs.equalsIgnoreCase( "true" );
					
				}else if ( lhs.equals( "format" )){
					
					if ( rhs.equalsIgnoreCase( "json" )){
						
						json_output = true;
					}
				}
			}
			
			if ( term == null ){
				
				term = "";
				//throw( new IOException( "search term missing" ));
			}
									
			String	mature_str	= mature?"true":"false";
			
			String	search_headers		= null;  // Note this is disctinct from request headers!!!!
			
			String client_ip = getOriginator( request );
			
			handleSearch( client_ip, term, mature_str, search_headers, request, response, json_output );
			
			return( true );
			
		}else if ( url.startsWith( RESULTS_PREFIX )){
				
			String[]	args = url.substring( RESULTS_PREFIX.length()).trim().split( "&" );
						
			String	sid = null;
			String	eid	= null;
			
			for (int i=0;i<args.length;i++){
				
				String[] bits = args[i].split("=");
				
				if ( bits.length != 2 ){
					
					continue;
				}
				
				String	lhs = bits[0].toLowerCase();
				String	rhs = URLDecoder.decode( bits[1], "UTF-8" );

				if ( lhs.equals( "sid" )){
					
					sid	= rhs;
					
				}else if ( lhs.equals( "eid" )){
					
					eid = rhs;
					
				}else if ( lhs.equals( "format" )){
					
					if ( rhs.equalsIgnoreCase( "json" )){
						
						json_output = true;
					}
				}
			}
			
			if ( sid == null || eid == null ){
				
				throw( new IOException( "sid or eid missing" ));
			}
			
			handleGetResult( sid, eid, response, json_output );
			
			return( true );
			
		}else{
			
			return( false );
		}
	}
	
	protected void
	handleSearch(
		String						originator,
		String						expr,
		String						mature,
		String						search_headers,
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response,
		boolean						json_output )
	{
		total_searches++;

		boolean	result_sent = false;
		
		if ( json_output ){
			
			response.setContentType( "application/json" );
			
		}else{
			
			response.setContentType( "application/javascript" );
		}
		
		OutputStream os = response.getOutputStream();
		
		PrintWriter pw = new PrintWriter( new OutputStreamWriter( os ));
		
		RemSearchPluginSearch search = null;

		try{
			adapter.searchReceived( originator );
				
			List<SearchParameter>	sps = new ArrayList<SearchParameter>();
						
			sps.add( new SearchParameter( "s", expr ));
			
			if ( mature != null ){
				
				sps.add( new SearchParameter( "m", mature.toString()));
			}
			
			SearchParameter[] parameters = sps.toArray( new SearchParameter[ sps.size()]);
					
			search = new RemSearchPluginSearch( this, request.getHeader(), originator, expr, json_output );

			adapter.searchCreated( search );
			
			synchronized( searches ){
				
				searches.put( search.getSID(), search );
				
				if ( searches.size() > max_searches ){
					
					throw( new IOException( "Too many active searches" ));
				}
			}
						
			String path = url_prefix;
			
			if ( path.length() == 0 ){
				
				path = "/";
			}
			
			if ( host_name != null && host_name.length() > 0 ){
			
				response.setHeader( "Set-Cookie", "JSESSIONID=" + search.getSID() + "." + host_name + "; path=" + path );
			}
			
			log( "Created: " + search.getSID() + ":  origin=" + originator );

			Map<String,String>	context = new HashMap();
			
			context.put( Engine.SC_SOURCE, 	"usearch" );
			
			context.put( Engine.SC_REMOVE_DUP_HASH, "true" );

			RemSearchPluginEngine[] plugin_engines = new RemSearchPluginEngine[0];
			
			boolean is_subs = expr.startsWith( "Subscription:" );
				
			if ( is_subs ){
			
				int	pos1 = expr.lastIndexOf( '(' );
				int pos2 = expr.indexOf( ')', pos1+1 );
				
				Subscription 	subscription 	= null;
				Engine			engine			= null;
				
				if ( pos1 != -1 && pos2 != -1 ){
					
					String sub_id = expr.substring( pos1+1, pos2 );
					
					subscription = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sub_id );
					
					if ( subscription != null ){
						
						RemSearchPluginEngine plugin_engine = getEngine( subscription );
						
						if ( plugin_engine != null ){
						
							engine = plugin_engine.getEngine();
							
							plugin_engines = new RemSearchPluginEngine[]{ getEngine( subscription ) };
						}
					}
				}
				
				search.setEngines( plugin_engines );

				if ( engine != null ){
					
					SubscriptionHistory history = subscription.getHistory();
					
					SubscriptionResult[] subs_results = history.getResults( false );
							
					if ( subs_results.length > max_results_per_engine ){
						
						SubscriptionResult[] trimmed = new SubscriptionResult[ max_results_per_engine ];
						
						System.arraycopy( subs_results, 0, trimmed, 0, max_results_per_engine );
						
						subs_results = trimmed; 
					}	
					
					search.resultsReceived( engine, subs_results );
					
					search.resultsComplete( engine );
				}
			}else{
				
				Engine[] engines = getEnginesToUse();
				
				if ( engines.length == 0 ){
					
					throw( new IOException( "No templates available for searching" ));
				}
				
				engines = getMetaSearchManager().getMetaSearch().search( engines, search, parameters, search_headers, context, max_results_per_engine );
					
				if ( engines.length == 0 ){
					
					throw( new IOException( "No templates available for searching" ));
				}
				
				plugin_engines = new RemSearchPluginEngine[ engines.length ];
				
				for ( int i=0;i<engines.length;i++ ){
					
					plugin_engines[i] = getEngine( engines[i] );
				}
				
				search.setEngines( plugin_engines );
			}
			
			JSONObject result = new JSONObject();
			
			result.put("sid", search.getSID());
					
			ArrayList<Map<String,Object>> engine_list = new ArrayList<Map<String,Object>>();
			
			for (int i=0;i<plugin_engines.length;i++){
				
				RemSearchPluginEngine	engine = plugin_engines[i];
							
				Map<String,Object> engine_map = (Map<String,Object>)new JSONObject();
				
				getEngineDetails( engine, engine_map );
			
				engine_list.add( engine_map  );		
			}
			
			result.put("engines", engine_list );
							
			result_sent = true;
			
			if ( json_output ){
				
				pw.println( result.toString());

			}else{
			
				pw.println( "webSearch.setup(" + result.toString() + ")" );
			}
			
			pw.flush();
			
		}catch( Throwable e ){
			
			log( "Search failed", e );
			
			total_fails++;
			
			if ( !result_sent ){
				
				JSONObject error_map = new JSONObject();
				
				if ( search != null ){
				
					error_map.put("sid", search.getSID());
				}
				
				error_map.put( "error", Debug.getNestedExceptionMessage(e));
				
				if ( json_output ){
					
					pw.println( error_map.toString());

				}else{
				
					pw.println( "webSearch.failed(" + error_map.toString() + ")" );
				}
				
				pw.flush();
			}
		}
	}
	
	protected Engine[]
 	getEnginesToUse()
 	{
 		Engine[] engines = getMetaSearchManager().getMetaSearch().getEngines( true, true );
 		
 		List<Engine>	engines_to_use = new ArrayList<Engine>();
 		
 		synchronized( searches ){

 			for ( Engine engine: engines ){
 				
 				LinkedList<Boolean>	history = getEngine( engine ).getHistory();
 				
 				if ( history.size() < 10 ){
 					
 					engines_to_use.add( engine );
 					
 				}else{
 					
 					int	bad		= 0;
 					int	good	= 0;
 					
 					int	first_bad	= -1;
 					int	first_good	= -1;
 					
 					int	pos = 0;
 					
 					boolean	add = false;
 					
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
 					
 						// don't let an intermittent fail stop engine use
 					
 					if ( first_good <= 10 ){
 						
 						add	= true;
 						
 					}else{
 						
 							// not looking so good
 						
 						int	rand;
 						
 						if ( first_good == -1 ){
 							
 							rand = history.size();
 							
 						}else{
 							
 							rand = bad;
 						}
 						
 						add = random.nextInt( rand ) == 0;
 					}
 					
 					if ( add ){
 						
 						engines_to_use.add( engine );
 					}
 				}
 			}
 		}
 		
 		return( engines_to_use.toArray( new Engine[ engines_to_use.size()]));
 	}
 	
 	protected void
 	handleGetResult(
 		String						sid,
 		String						eid,
 		TrackerWebPageResponse		response,
 		boolean						json_output )
 	{
 		RemSearchPluginSearch search;
 		
 		if ( json_output ){
			
			response.setContentType( "application/json" );
			
 		}else{
		
 			response.setContentType( "application/javascript" );
 		}
 		
 		synchronized( searches ){

 			search = searches.get( sid );
 		}
 		
 		try{
 			if ( search != null ){
 				
 				search.handleResultReceiver( eid, response );
 					
 			}else{
 				
 				throw( new IOException( "Search '" + sid + " not found" ));
 			}						
 		}catch( Throwable failure ){
 			
 			log( "Search " + sid + "/" + eid + ": " + Debug.getNestedExceptionMessage( failure ));
 			
 			RemSearchPluginEngine engine;
 			
 			synchronized( searches ){
 				
 				engine = engine_eid_map.get( eid );
 			}
 			
 			Map<String,Object> error_map = (Map<String,Object>)new JSONObject();
 			
 			error_map.put( "error", Debug.getNestedExceptionMessage( failure ));
 			
 			error_map.put("sid", sid);

 			if ( engine != null ){
 				
 				getEngineDetails( engine, error_map );
 				
 			}else{
 				
 				error_map.put("id", eid );
 			}
 			
 			try{
 				PrintWriter pw = new PrintWriter( new OutputStreamWriter( response.getOutputStream(), "UTF-8" ));

 				if ( json_output ){
 					
 					pw.println( error_map.toString());

 				}else{
 				
 					pw.println( "webSearch.engineFailed(" + error_map.toString() + ")" );
 				}
 				
 				pw.flush();
 				
 			}catch( Throwable e ){
 			}
 		}
 	}
 	
	protected void
	complete(
		RemSearchPluginSearch								search,
		List<RemSearchPluginSearch.engineResult>			ok,
		List<RemSearchPluginSearch.engineResult>			failed )
	{
		synchronized( searches ){

			if ( searches.remove( search.getSID()) == null ){
				
				return;
			}

			log( "Complete: " + search.getSID() + ", elapsed=" + search.getAge());

			if ( ok.size() == 0 ){
				
				total_fails++;
			}
			
			total_engine_fails += failed.size();
						
			for ( RemSearchPluginSearch.engineResult result: ok ){
				
				addEngineResult( result, true );
			}
			
			for ( RemSearchPluginSearch.engineResult result: failed ){
				
				addEngineResult( result, false );
			}
		}
	}
	
	public RemSearchPluginEngine[]
 	getEngines()
 	{
 		Engine[] engines = getMetaSearchManager().getMetaSearch().getEngines( true, true );

 		List<RemSearchPluginEngine> result = new ArrayList<RemSearchPluginEngine>();
 		
 		synchronized( searches ){
 			
 			for ( Engine engine: engines ){
 				
 				RemSearchPluginEngine e = engine_map.get( engine );
 				
 				if ( e == null ){
 					
 					e = new RemSearchPluginEngineReal( engine );
 					
 					engine_map.put( engine, e );
 					
 					engine_eid_map.put( engine.getUID(), e );
 				}
 				
 				result.add( e );
 			}
 		}
 		
 		return( result.toArray( new RemSearchPluginEngine[result.size()]));
 	}
 	
 	protected RemSearchPluginEngine
 	getEngine(
 		Engine		engine )
 	{
 		synchronized( searches ){
 			
 			RemSearchPluginEngine e = engine_map.get( engine );
 			
 			if ( e == null ){
 				
 				e = new RemSearchPluginEngineReal( engine );
 				
 				engine_map.put( engine, e );
 				
				engine_eid_map.put( engine.getUID(), e );
 			}
 			
 			return( e );
 		}
 	}
 	
 	protected RemSearchPluginEngine
 	getEngine(
 		Subscription		subscription )
 	{
 		synchronized( searches ){
 			
 			try{
	 			Engine engine = subscription.getEngine();
	 			
	 			RemSearchPluginEngine e = engine_map.get( engine );
	 			
	 			if ( e == null ){
	 				
	 				e = new RemSearchPluginEngineReal( engine );
	 				
	 				engine_map.put( engine, e );
	 				
					engine_eid_map.put( engine.getUID(), e );
	 			}
	 			
	 			return( e );
	 			
 			}catch( Throwable e ){
 				
 				Debug.out( e );
 				
 				return( null );
 			}
 		}
 	}
 	
 	protected void
 	addEngineResult(
 		RemSearchPluginSearch.engineResult		result,
 		boolean									ok )
 	{
 		result.getEngine().addHistory( ok, result.getSearchElapsedTime());
 	}

 	protected static void
 	getEngineDetails(
 		RemSearchPluginEngine			engine,
 		Map<String,Object>				map )
 	{
 		map.put("name", engine.getName() );			
 		map.put("id", engine.getUID());
 		map.put("favicon", engine.getIcon());
 		map.put("dl_link_css", engine.getDownloadLinkCSS());
 		map.put("selected", Engine.SEL_STATE_STRINGS[ engine.getSelectionState()]);
 		map.put("type", Engine.ENGINE_SOURCE_STRS[ engine.getSource()]);
 	}
 	
 	protected String
 	getOriginator(
 		TrackerWebPageRequest		request )
 	{
 		String input_header 			= request.getHeader();
 		String lowercase_input_header	= input_header.toLowerCase(); 
 			
 		String	client_ip 		= getRequestHeader( input_header, lowercase_input_header, "real-ip" );

 		if ( client_ip == null ){
 			
 			client_ip 		= getRequestHeader( input_header, lowercase_input_header, "client-ip" );
 		}
 		
 		if ( client_ip == null ){
 						
 			client_ip = getRequestHeader( input_header, lowercase_input_header, "forwarded-for" );	
 		}
 		
 		if ( client_ip == null ){
 			
 			return( request.getClientAddress());
 			
 		}else{
 			
 			int	pos = client_ip.indexOf(',');
 			
 			if ( pos != -1 ){
 				
 				client_ip = client_ip.substring(0,pos);
 			}
 			
 			try{
 				InetAddress originator_host = InetAddress.getByName( client_ip.trim());
 				
 				if ( 	originator_host.isLinkLocalAddress() ||
 						originator_host.isSiteLocalAddress() ||
 						originator_host.isLoopbackAddress()){
 					
 					log( "Bad client IP '" + client_ip + "'" );
 					
 					return( request.getClientAddress());
 					
 				}else{
 					
 					return( originator_host.getHostAddress());
 				}
 				
 			}catch( Throwable e ){
 				
 				log( "Bad client IP '" + client_ip + "'" );
 				
 				return( request.getClientAddress());
 			}
 		}
 	}
 	
 	protected String
 	getRequestHeader(
 		String	str,
 		String	lc_str,
 		String	name )
 	{
 			// pick up "name" and "x-name"
 		
 		int	pos1 = lc_str.indexOf( name );
 		
 		if ( pos1 == -1 ){
 			
 			return( null );
 		}
 		
 		int pos2 = lc_str.indexOf( "\r\n", pos1 );
 		
 		String entry;
 		
 		if ( pos2 == -1 ){
 			
 			entry = str.substring( pos1 );
 			
 		}else{
 			
 			entry = str.substring( pos1, pos2 );
 		}
 		
 		int	pos = entry.indexOf(':');
 		
 		if ( pos == -1 ){
 			
 			return( null );
 		}
 		
 		return( entry.substring( pos+1 ).trim());
 	}
 	
 	public Map<String,RemSearchPluginSearch>
 	getSearches()
 	{
 		synchronized( searches ){
 			
 			return( new HashMap<String,RemSearchPluginSearch>( searches ));
 		}
 	}
 	
 	protected Map<Engine, RemSearchPluginEngine>
 	getEngineMap()
 	{
 		synchronized( searches ){
 		
 			return( new HashMap<Engine, RemSearchPluginEngine>( engine_map ));
 		}
 	}
 	
 	protected long
 	getTotalSearches()
 	{
 		return( total_searches );
 	}
 	
 	protected long
	getTotalSearchesFailed()
	{
		return( total_fails);
	}
	
 	protected long
	getTotalEnginesFailed()
	{
		return( total_engine_fails );
	}	
	
 	protected void
 	log(
 		String		str )
 	{
 		adapter.log( str );
 	}
 	
 	protected void
 	log(
 		String		str,
 		Throwable	e )
 	{
 		adapter.log( str, e );
 	}
}
