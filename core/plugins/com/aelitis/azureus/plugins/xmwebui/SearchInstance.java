/*
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

import java.util.*;

import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.RandomUtils;
import com.biglybt.core.util.SystemTime;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.metasearch.Result;
import com.biglybt.core.metasearch.ResultListener;

class
SearchInstance
	implements ResultListener
{
	/**
	 *
	 */
	private final XMWebUIPlugin xmWebUIPlugin;
	private String	sid;
	private long	start_time = SystemTime.getMonotonousTime();
	private long	last_got_results_on = -1;
	private boolean	all_searches_complete = false;
	
	private Map<String,List>	engine_results = new HashMap<String, List>();
	
	SearchInstance(
		XMWebUIPlugin xmWebUIPlugin, Engine[]	engines )
	{
		this.xmWebUIPlugin = xmWebUIPlugin;
		byte[]	bytes = new byte[16];
		
		RandomUtils.nextSecureBytes( bytes );
		
		sid = Base32.encode( bytes );
		
		for ( Engine e: engines ){
			
			engine_results.put( e.getUID(), new ArrayList());
		}
	}
	
	String
	getSID()
	{
		return( sid );
	}
	
	void
	setEngines(
		Engine[]		engines )
	{
			// trim back in case active engines changed
		
		Set<String>	active_engines = new HashSet<String>();
		
		for ( Engine e: engines ){
			
			active_engines.add( e.getUID());
		}
		
		synchronized( this ){
		
			Iterator<String>	it = engine_results.keySet().iterator();
			
			while( it.hasNext()){
				
				if ( !active_engines.contains( it.next())){
					
					it.remove();
				}
			}
		}
	}
	
	long
	getLastResultsAgo() {
		return last_got_results_on > 0 ? SystemTime.getMonotonousTime() - last_got_results_on : -1;
	}
	
	boolean isComplete() {
		return all_searches_complete;
	}
	
	boolean
	getResults(
		Map	result )
	{
		last_got_results_on = SystemTime.getMonotonousTime();
		
		result.put( "sid", sid );
		
		List<Map>	engines = new ArrayList<Map>();
		
		result.put( "engines", engines );
		
		synchronized( this ){
			
			boolean	all_complete = true;
			
			for ( Map.Entry<String, List> entry: engine_results.entrySet()){
				
				Map m = new HashMap();
				
				engines.add( m );
				
				m.put( "id", entry.getKey());
				
				List results = entry.getValue();
				
				Iterator<Object>	it = results.iterator();
				
				boolean	engine_complete = false;
				
				while( it.hasNext()){
					
					Object obj = it.next();
					
					if ( obj instanceof Boolean ){
						
						engine_complete = true;
						
						break;
						
					}else if ( obj instanceof Throwable ){
													
						m.put( "error", Debug.getNestedExceptionMessage((Throwable)obj));
						
					}else{
						
						it.remove();
						
						Result[] sr = (Result[])obj;
						
						List l_sr = (List)m.get( "results" );
						
						if ( l_sr == null ){
							
							l_sr = new ArrayList();
							
							m.put( "results", l_sr );
						}
						
						for ( Result r: sr ){
							
							l_sr.add( r.toJSONMap());
						}
					}
				}
				
				if (!engine_complete && all_searches_complete) {
					engine_complete = true;
					if (!m.containsKey("error")) {
						m.put("error", "Timeout");
					}
				}
  			
 				m.put( "complete", engine_complete );
				
				if ( !engine_complete ){
					
					all_complete = false;
				}
			}
			
			all_searches_complete = all_complete;

			result.put( "complete", all_complete );
			
			return( all_complete );
		}
	}
	
	long
	getAge()
	{
		return( SystemTime.getMonotonousTime() - start_time );
	}
	
	@Override
	public void
	contentReceived(
		Engine 		engine, 
		String 		content )
	{
	}
	
	@Override
	public void
	matchFound( 
		Engine 		engine, 
		String[] 	fields )
	{
	}
	
	@Override
	public void
	resultsReceived(
		Engine 		engine,
		Result[] 	results)
	{
		if ( this.xmWebUIPlugin.trace_param.getValue() ){
			this.xmWebUIPlugin.log( "results: " + engine.getName() + " - " + results.length );
		}
		
		synchronized( this ){

			List list = engine_results.get( engine.getUID());
			
			if ( list != null ){
				
				if ( list.size() > 0 && list.get( list.size()-1) instanceof Boolean ){
					
				}else{
				
					list.add( results );
				}
			}
		}
	}
	
	@Override
	public void
	resultsComplete(
		Engine 	engine)
	{
		if ( this.xmWebUIPlugin.trace_param.getValue() ){
			this.xmWebUIPlugin.log( "comp: " + engine.getName()); 
		}
		
		synchronized( this ){

			List list = engine_results.get( engine.getUID());
			
			if ( list != null ){
				
				if ( list.size() > 0 && list.get( list.size()-1) instanceof Boolean ){

				}else{
					
					list.add( true );
				}
			}
		}
	}
	
	@Override
	public void
	engineFailed(
		Engine 		engine, 
		Throwable 	cause )
	{
		if ( this.xmWebUIPlugin.trace_param.getValue() ){
			this.xmWebUIPlugin.log( "fail: " + engine.getName()); 
		}
		
		synchronized( this ){

			List list = engine_results.get( engine.getUID());
			
			if ( list != null ){
				
				if ( list.size() > 0 && list.get( list.size()-1) instanceof Boolean ){

				}else{
					
					list.add( cause );
					list.add( true );
				}
			}
		}
	}
		
	@Override
	public void
	engineRequiresLogin(
		Engine 		engine, 
		Throwable 	cause )
	{
		engineFailed( engine, cause );
	}
	
	String
	getString()
	{
		return( sid );
	}

	public void failWithTimeout() {
		all_searches_complete = true;
	}
}