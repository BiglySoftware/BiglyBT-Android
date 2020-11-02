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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.*;

import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import org.json.simple.JSONObject;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.Result;
import com.biglybt.core.metasearch.ResultListener;
import com.biglybt.core.subs.SubscriptionResult;

public class 
RemSearchPluginSearch 
	implements ResultListener
{
	private RemSearchPluginPageGenerator		generator;
	private String								request_headers;
	private String								originator;
	private String								expression;
	
	private boolean								json_output;
	
	private String		sid;
	private long		create_time;
	
	private Map<String,engineResult>	engine_results = new HashMap<String,engineResult>();

	private boolean		destroyed;
		
	protected
	RemSearchPluginSearch(
		RemSearchPluginPageGenerator		_generator,
		String								_request_headers,
		String								_originator,
		String								_expression,
		boolean								_json_output )
	{
		generator			= _generator;
		request_headers		= _request_headers;
		originator			= _originator;
		expression 			= _expression;
		
		json_output	= _json_output;
		
		byte[]	bytes = new byte[16];
		
		RandomUtils.nextSecureBytes( bytes );
		
		sid = Base32.encode( bytes );
		
		create_time = SystemTime.getMonotonousTime();
	}
	
	public String
	getRequestHeaders()
	{
		return( request_headers );
	}
	
	public String
	getOriginator()
	{
		return( originator );
	}
	
	public String
	getExpression()
	{
		return( expression );
	}
	
	public long
	getAge()
	{
		return( SystemTime.getMonotonousTime() - create_time );
	}
	
	protected String
	getSID()
	{
		return( sid );
	}
	
	protected void
	setEngines(
		RemSearchPluginEngine[]		engines )
	{
		for (int i=0;i<engines.length;i++){
			
			RemSearchPluginEngine	engine = engines[i];
			
			getResult( engine ).setEngine( engine );
		}
	}
	
	protected void
	handleResultReceiver(
		String						eid,
		TrackerWebPageResponse		request )
	
		throws IOException
	{
		getResultByEID( eid ).setResultReceiver( request );
	}
	
	protected engineResult
	getResult(
		RemSearchPluginEngine		engine )
	{				
		synchronized( engine_results ){
			
			engineResult result = engine_results.get( engine.getUID());
			
			if ( result == null ){
				
				result = new engineResult( engine );
				
				engine_results.put( engine.getUID(), result );
				
				if ( destroyed ){
					
					result.destroy();
				}
			}
		
			return( result );
		}
	}
	
	protected engineResult
	getResultByEID(
		String		eid )
	
		throws IOException
	{
		synchronized( engine_results ){
			
			engineResult result = engine_results.get( eid );
			
			if ( result == null ){
				
				throw( new IOException( "Engine '" + eid + "' not found" ));
			}
			
			return( result );
		}
	}
	
	@Override
	public void
	contentReceived(
		Engine engine, 
		String content ) 
	{
		// no interest
	}
	
	@Override
	public void
	matchFound(
		Engine 		engine, 
		String[] 	fields ) 
	{
		// no interest
	}
	
	@Override
	public void
	engineFailed(
		Engine 		engine, 
		Throwable 	e )
	{
		getResult( generator.getEngine( engine )).setFailed( e );
	}
	
	@Override
	public void
	engineRequiresLogin(
		Engine 		engine, 
		Throwable	e ) 
	{
		engineFailed( engine, e );
	}
	
	@Override
	public void
	resultsComplete(
		Engine engine ) 
	{
		getResult( generator.getEngine( engine )).setComplete();
	}
	
	@Override
	public void
	resultsReceived(
		Engine 		engine,
		Result[] 	results) 
	{
		ResultWrapper[]	rw = new ResultWrapper[results.length];
		
		for ( int i=0;i<results.length;i++ ){
			
			rw[i] = new ResultWrapper( results[i] );
		}
		
		getResult( generator.getEngine( engine )).addResults( rw );
	}
	
	public void 
	resultsReceived(
		Engine 					engine,
		SubscriptionResult[] 	results) 
	{
		ResultWrapper[]	rw = new ResultWrapper[results.length];
		
		for ( int i=0;i<results.length;i++ ){
			
			rw[i] = new ResultWrapper( results[i] );
		}
		
		getResult( generator.getEngine( engine )).addResults( rw );
	}
	
	protected void
	checkCompleteness()
	{
		List<engineResult>	ok 		= new ArrayList<engineResult>();
		List<engineResult>	failed 	= new ArrayList<engineResult>();
		
		synchronized( engine_results ){

			for ( engineResult result: engine_results.values()){

				if ( !result.isDone()){
				
					return;
				}
				
				if ( result.succeeded()){
					
					ok.add( result );
					
				}else{
					
					failed.add( result );
				}
			}
		}
		
		generator.complete( this, ok, failed );
	}
	
	public void
	destroy()
	{
		List<engineResult>	ok 		= new ArrayList<engineResult>();
		List<engineResult>	failed 	= new ArrayList<engineResult>();

		synchronized( engine_results ){

			destroyed	= true;
			
			for ( engineResult result: engine_results.values()){
				
				result.destroy();
				
				if ( result.succeeded()){
					
					ok.add( result );
					
				}else{
					
					failed.add( result );
				}
			}
		}
	
		generator.complete( this, ok, failed );
	}
	
	public String
	getString()
	{
		String e_str = "";
		
		synchronized( engine_results ){

			for ( Map.Entry<String,engineResult> entry: engine_results.entrySet()){

				e_str += (e_str.length()==0?"":", ") + entry.getKey() + "=" + (entry.getValue().isDone()?"Y":"N");
			}
		}
		
		return( getSID() + " - " + e_str );
	}
	
	private class
	ResultWrapper
	{
		private Map		map;
		
		private 
		ResultWrapper(
			Result	result )
		{
			map	= result.toJSONMap();
		}
		
		private 
		ResultWrapper(
			SubscriptionResult	result )
		{
			map	= result.toJSONMap();
		}
		
		public Map 
		toJSONMap() 
		{
			return( map );
		}
	}
	
	protected class
	engineResult
	{
			// TODO: timeout async receivers properly?
		
		private RemSearchPluginEngine		engine;
		private TrackerWebPageResponse		receiver;
		
		private List<ResultWrapper>	results = new ArrayList<ResultWrapper>();
		
		private Throwable	failure;
		private boolean		complete;
		
		private boolean		done;
		
		private long		mt_search_end = -1;
				
		private AESemaphore	wait_sem;

		private boolean		can_go_async	= true;
		private boolean		went_async;
		
		protected
		engineResult(
			RemSearchPluginEngine		_engine )
		{
			engine	= _engine;
			
			if ( !generator.supportsAsync()){
				
				wait_sem = new AESemaphore( "RSPS:waiter" );
			}
		}
		
		protected void
		setEngine(
			RemSearchPluginEngine	_engine )
		{
			synchronized( this ){
				
				engine	= _engine;
			}
			
			checkDone();
		}
		
		protected RemSearchPluginEngine
		getEngine()
		{
			return( engine );
		}
		
		protected void
		setResultReceiver(
			TrackerWebPageResponse		_receiver )
		
			throws IOException
		{
			synchronized( this ){
				
				if ( receiver != null ){
					
					throw( new IOException( "Results for engine already returned" ));
				}
				
				receiver	= _receiver;
			}
				
			if ( !checkDone()){
					
				if ( wait_sem != null ){
										
					if ( !wait_sem.reserve( 60*1000 )){
						
						throw( new IOException( "timeout waiting for complete" ));
					}
				}else{
									
					synchronized( engineResult.this ){
						
						if ( can_go_async ){
							
							went_async = true;
							
							receiver.setAsynchronous( true );
						}
					}
				}
			}
		}
		
		protected void
		addResults(
			ResultWrapper[]	_results )
		{
			if ( mt_search_end == -1 ){
				
				mt_search_end = SystemTime.getMonotonousTime();
			}
			
			results.addAll( Arrays.asList( _results ));
		}
		
		protected long
		getSearchElapsedTime()
		{
			if ( mt_search_end == -1 ){
				
				return( -1 );
			}
			
			return( mt_search_end - create_time );
		}
		
		protected void
		setFailed(
			Throwable	e )
		{
			synchronized( this ){
				
				failure = e;
			}
			
			checkDone();
		}
		
		protected void
		setComplete()
		{
			synchronized( this ){
				
				complete	= true;
			}
			
			checkDone();
		}
		
		protected void
		destroy()
		{
			setFailed( new Throwable( "Search destroyed" ));
		}
		
		protected boolean
		checkDone()
		{
			synchronized( this ){
				
				if ( done ){
					
					return( true );
				}
			
				if ( engine == null || receiver == null ){
				
					return( false );
				}
			
				if ( complete || failure != null ){
					
					done = true;
					
				}else{
					
					return( false );
				}
			}
			
			try{
				PrintWriter pw = new PrintWriter( new OutputStreamWriter( receiver.getOutputStream(), "UTF-8" ));
				
				if ( complete ){
						
					Map<String,Object> result_map = (Map<String,Object>)new JSONObject();
					
					RemSearchPluginPageGenerator.getEngineDetails( engine, result_map );
					
					result_map.put("sid", sid);

					List<Map> result_list = new ArrayList<Map>(results.size());
					
					for ( ResultWrapper result: results ){
						
						result_list.add( result.toJSONMap());
					}
					
					result_map.put( "results", result_list );
					
					Map<String,Object> complete_map = (Map<String,Object>)new JSONObject();
					
					RemSearchPluginPageGenerator.getEngineDetails( engine, complete_map );
					
					complete_map.put("sid", sid);

					if ( json_output ){
						
						result_map.putAll( complete_map );
						
						pw.println(  result_map.toString());

					}else{
					
						pw.println( "webSearch.loadResults( " + result_map.toString() + "); webSearch.engineCompleted(" + complete_map.toString() + ")" );
					}
				}else{
					
					Map<String,Object> error_map = (Map<String,Object>)new JSONObject();
					
					RemSearchPluginPageGenerator.getEngineDetails( engine, error_map );
					
					error_map.put( "error", Debug.getNestedExceptionMessage( failure ));
					
					error_map.put("sid", sid);
					
					if ( json_output ){
						
						pw.println( error_map.toString());
						
					}else{
					
						pw.println( "webSearch.engineFailed(" + error_map.toString() + ")" );
					}
				}
				
				pw.flush();
					
			}catch( Throwable e ){
				
				Debug.printStackTrace( e );
								
			}finally{
					
				if ( wait_sem != null ){
										
					wait_sem.releaseForever();
					
				}else{
					
					boolean	async;
					
					synchronized( engineResult.this ){
						
						can_go_async = false;
						
						async = went_async;
					}
					
					if ( async ){
						
						try{						
							receiver.setAsynchronous( false );
							
						}catch( Throwable e ){
							
							Debug.printStackTrace( e );
						}
					}
				}
				
				checkCompleteness();
			}
			
			return( true );
		}
		
		protected boolean
		isDone()
		{
			synchronized( this ){
			
				return( done );
			}
		}
		
		protected boolean
		succeeded()
		{
			synchronized( this ){
			
				return( complete );
			}
		}
	}
}
