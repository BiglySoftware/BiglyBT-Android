/*
 * Created on Jul 2, 2013
 * Created by Paul Gardner
 * 
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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


package com.aelitis.azureus.plugins.xmwebui.client.rpc;

import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import org.json.simple.JSONObject;

public class 
XMRPCClientCached 
	implements XMRPCClient
{
	private XMRPCClient		base;
	private int				cache_millis;
	private int				max_active_rpcs	= 32;
	
	private int				active_calls;
	private boolean			destroyed;
	
	private Map<String,CacheEntry>	cache = new HashMap<String, CacheEntry>();
	
	protected
	XMRPCClientCached(
		XMRPCClient		_base,
		int				_cache_millis )
	{
		base			= _base;
		cache_millis	= _cache_millis;
	}
	
	@Override
	public JSONObject
	call(
		JSONObject		request )
	
		throws XMRPCClientException
	{
		String 	method 	= (String)request.get( "method" );
		Map		args	= (Map)request.get( "arguments" );
		
		String	key = method + ": " + args;
				
		CacheEntry	entry = null;
		
		synchronized( cache ){
			
			if ( destroyed ){
				
				throw( new XMRPCClientException( "RPC has been destroyed" ));
			}
		
			if ( active_calls >= max_active_rpcs ){
				
				throw( new XMRPCClientException( "Too many active calls" ));
			}
			
			if ( cache.size() > 64 ){
				
				Debug.out( "Cache is too full, something borkified: " + cache.keySet());
				
				cache.clear();
			}
			
			if (	method.equals( "session-stats" ) ||
					method.equals( "session-get" ) ||
					method.equals( "torrent-get" )){
			
				entry = cache.get( key );
			
				if ( entry == null || entry.hasExpired()){
				
					entry = new CacheEntry();
					
					cache.put( key, entry );
				}
			}else{
				
				cache.clear();
			}
			
			active_calls++;
		}
		
		try{
			JSONObject result;
			
			if ( entry == null ){
				
				result = base.call( request );
				
			}else{
				
				result = entry.call( request );
			}
			
			return( result );
			
		}finally{
			
			synchronized( cache ){
				
				active_calls--;
			}
		}
	}
	
	@Override
	public HTTPResponse
	call(
		String				method,
		String				url,
		Map<String,String>	headers,
		byte[]				data )
	
		throws XMRPCClientException
	{
		synchronized( cache ){
			
			if ( destroyed ){
				
				throw( new XMRPCClientException( "RPC has been destroyed" ));
			}
		
			if ( active_calls >= max_active_rpcs ){
				
				throw( new XMRPCClientException( "Too many active calls" ));
			}
			
			active_calls++;
		}
		
		try{
			return( base.call( method, url, headers, data ));
			
		}finally{
			
			synchronized( cache ){
				
				active_calls--;
			}
		}
	}
	
	@Override
	public void
	destroy()
	{
		synchronized( cache ){
			
			destroyed = true;
			
			cache.clear();
		}
		
		base.destroy();
	}
	
	private class
	CacheEntry
	{
		private long		time	= -1;
		
		private AESemaphore				sem;
		private JSONObject				result;
		private XMRPCClientException	error;
		
		private
		CacheEntry()
		{
		}
		
		private boolean
		hasExpired()
		{
			return( time >= 0 && SystemTime.getMonotonousTime() - time > cache_millis );
		}
		
		private JSONObject
		call(
			JSONObject		request )
		
			throws XMRPCClientException
		{
			synchronized( this ){
				
				if ( sem == null ){
					
					sem = new AESemaphore( "rpccache:call");
					
					try{
						System.out.println( "    -> calling" );
						
						result = base.call( request );
						
					}catch( XMRPCClientException e ){
						
						error	= e;
						
					}catch( Throwable e ){
						
						error = new XMRPCClientException( "Call failed", e );
						
					}finally{
						
						time 	= SystemTime.getMonotonousTime();

						sem.releaseForever();
					}
				}else{
					
					System.out.println( "    -> using cache" );
				}
			}
			
			sem.reserve();
			
			if ( error != null ){
				
				throw( error );
			}
			
			return( result );
		}
	}
}
