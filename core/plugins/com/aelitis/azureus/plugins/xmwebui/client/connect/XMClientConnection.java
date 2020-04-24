/*
 * Created on Jun 27, 2013
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


package com.aelitis.azureus.plugins.xmwebui.client.connect;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.pairing.PairingManagerFactory;
import com.biglybt.core.util.Debug;
import org.json.simple.JSONObject;

import com.aelitis.azureus.plugins.xmwebui.client.proxy.XMClientProxy;
import com.aelitis.azureus.plugins.xmwebui.client.rpc.XMRPCClient;
import com.aelitis.azureus.plugins.xmwebui.client.rpc.XMRPCClientException;
import com.aelitis.azureus.plugins.xmwebui.client.rpc.XMRPCClientFactory;
import com.aelitis.azureus.plugins.xmwebui.client.rpc.XMRPCClientUtils;
import com.biglybt.util.JSONUtils;
;

public class 
XMClientConnection 
{
	private static final String	CS_DISCONNECTED		= "Disconnected";
	private static final String	CS_BASIC			= "Basic";
	private static final String	CS_SECURE_DIRECT	= "Secure (Direct)";
	private static final String	CS_SECURE_PROXIED	= "Secure (Proxied)";

	private static final int	CACHE_MILLIS_BASIC			= 1*1000;
	private static final int	CACHE_MILLIS_SECURE_DIRECT	= 4*1000;
	private static final int	CACHE_MILLIS_SECURE_PROXY	= 9*1000;
	
	private XMClientAccount				account;
	private XMClientConnectionAdapter	adapter;
	
	private XMRPCClient			rpc;
	private XMClientProxy		proxy;
	
	private String				connection_state = CS_DISCONNECTED;
		
	public
	XMClientConnection(
		XMClientAccount				_ac,
		XMClientConnectionAdapter	_adapter )
	{
		account 	= _ac;
		adapter		= _adapter;
	}
	
	public String
	getUID()
	{
		return( account.getUID());
	}
	
	public String
	getAccessCode()
	{
		return( account.getAccessCode());
	}
	
	public void
	connect()
	{
		String	ac = account.getAccessCode();
		
		boolean	called_connect = false;
		
		if ( ac.contains( "." )){
			
			int	pos = ac.indexOf( ":" );
			
			String	host;
			int		port;
			
			if ( pos == -1 ){
				
				host	= ac;
				port	= 9091;
			}else{
				host	= ac.substring( 0, pos );
				port	= Integer.parseInt( ac.substring( pos+1 ).trim());
			}
			
			Map<String,String>	service_map = new HashMap<String,String>();
			
			service_map.put( "protocol", "http" );
			service_map.put( "ip", host );
			service_map.put( "port", String.valueOf( port ));
			
			log( "Explicit target - " + host + ":" + port );
			
			connect( service_map, false );

			called_connect = true;
			
		}else{
			try{
				URL service_url = PairingManagerFactory.getSingleton().getServiceURL();
				
				String url = service_url.toExternalForm() + "/remote/listBindings?ac=" + getAccessCode() + "&jsoncallback=";
				
				String reply = new String( XMRPCClientUtils.getFromURLBasic( url ), "UTF-8" );
				
					// hack to remove callback
				
				reply = reply.substring( 1, reply.length()-1 );
				
				Map json = JSONUtils.decodeJSON( reply );
				
				Map error = (Map)json.get( "error" );
				
				if ( error != null ){
					
					throw( new Exception((String)error.get( "msg" )));
				}
				
				List<Map<String,String>>	service_list = (List<Map<String,String>>)json.get( "result" );
				
				Map<String,String>		target_service 	= null;
				boolean					has_tunnel		= false;
				
				for ( Map<String,String> m: service_list ){
					
					String sid = (String)m.get( "sid" );
					
					if ( sid.equals( "tunnel" )){
						
						has_tunnel = true;
						
					}else if ( sid.equals(  "xmwebui" )){
						
						target_service = m;
					}
				}
				
				if ( target_service != null ){
						
					log( "Pairing details obtained for '" + ac + "', supports secure connections=" + has_tunnel );
	
					connect( target_service, has_tunnel );
					
					called_connect = true;
					
				}else{
				
					logError( "No binding found for Web Remote, '" + ac + "'" );
				}
				
			}catch( Throwable e ){
				
				logError( "Pairing details unavailable for '" + ac + "': " + Debug.getNestedExceptionMessage( e ));
			}
		}
		
		if ( !called_connect ){
			
			adapter.setConnected( this, false );
		}
	}
	
	private void
	connect(
		Map<String,String>		service_map,
		boolean					has_tunnel )
	{
		String	ac				= account.getAccessCode();
		int		cache_millis	= 0;
		
		try{			
			boolean http 	= service_map.get( "protocol" ).equals( "http" );
			String	host	= service_map.get( "ip" );
			int		port	= Integer.parseInt( service_map.get( "port" ));
			
			if ( account.isBasicEnabled() ){
				
				log( "Attempting direct basic connection" );
			
				cache_millis	= CACHE_MILLIS_BASIC;
				
				XMRPCClient rpc_direct = null;
				
				try{
					String	user 		= "vuze";
					String	password	= ac;
					
					if ( !account.isBasicDefaults()){
						
						user 		= account.getBasicUser();
						password	= account.getBasicPassword();
					}
					
					rpc_direct = XMRPCClientFactory.createDirect( http, host, port, user, password );
			
					logConnect( CS_BASIC, getRPCStatus( rpc_direct ));
					
					rpc = rpc_direct;
										
				}catch( Throwable e ){
					
					if ( rpc_direct != null ){
						
						rpc_direct.destroy();
					}
					
					logError( "    Failed: " + Debug.getNestedExceptionMessage( e ));
				}
			}
			
			if ( rpc == null ){
				
				if ( has_tunnel ){
					
					if ( rpc == null && !account.isForceProxy()){

						String	tunnel_server = (http?"http":"https") + "://" + host + ":" + port + "/";

						log( "Attempting direct secured connection: " + tunnel_server );
					
						cache_millis	= CACHE_MILLIS_SECURE_DIRECT;

						XMRPCClient rpc_tunnel = null;
						
						try{
							
							String user 		= account.getSecureUser();
							String password		= account.getSecurePassword();
							
							rpc_tunnel = XMRPCClientFactory.createTunnel( tunnel_server, ac, user, password );
					
							logConnect( CS_SECURE_DIRECT, getRPCStatus( rpc_tunnel ));
							
							rpc = rpc_tunnel;
																		
						}catch( Throwable e ){
							
							if ( rpc_tunnel != null ){
								
								rpc_tunnel.destroy();
							}
						
							logError( "    Failed: " + Debug.getNestedExceptionMessage( e ));
						}
					}
					
					if ( rpc == null ){
						
						String tunnel_server = PairingManagerFactory.getSingleton().getTunnelServer();
		
						log( "Attempting proxy secured connection: " + tunnel_server );
					
						cache_millis	= CACHE_MILLIS_SECURE_PROXY;

						XMRPCClient rpc_tunnel = null;
						
						try{
							String user 		= account.getSecureUser();
							String password		= account.getSecurePassword();
		
							rpc_tunnel = XMRPCClientFactory.createTunnel( tunnel_server, ac, user, password );
					
							logConnect( CS_SECURE_PROXIED, getRPCStatus( rpc_tunnel ));
							
							rpc = rpc_tunnel;
																		
						}catch( Throwable e ){
							
							if ( rpc_tunnel != null ){
								
								rpc_tunnel.destroy();
							}
						
							logError( "    Failed: " + Debug.getNestedExceptionMessage( e ));
						}
					}
				}else{
					
					logError( "    Failed - secure connections not enabled in remote client or client is still initialising" );
				}
			}
		}finally{
		
			if ( rpc == null ){
		
				connection_state = CS_DISCONNECTED;

				adapter.setConnected( this, false );
			
			}else{
				
				if ( cache_millis > 0 ){
				
					rpc = XMRPCClientFactory.createCached( rpc, cache_millis );
				}
				
				adapter.setConnected( this, true );
			}
		}
	}
	
	public String
	getConnectionStatus()
	{
		return( connection_state );
	}
	
	private void
	logConnect(
		String	type,
		Map		args )
	{
		connection_state = type;
		
		String	rem_bigly_version	= (String)args.get( "biglybt-version" );
		String	rem_az_version 		= (String)args.get( "az-version" );
		String	rem_plug_version 	= (String)args.get( "version" );
		
		log( "    Connected: Remote client version=" + (rem_bigly_version!=null?rem_bigly_version:rem_az_version) + ", plugin=" + rem_plug_version );

	}
	
	public URL
	getProxyURL()
	{
		if ( rpc == null ){
			
			return( null );
		}
		
		if ( proxy == null ){
		
			try{
				proxy = new XMClientProxy( account.getResourceDir(), rpc );
				
				log( "Created client proxy on port " + proxy.getPort());
				
			}catch( Throwable e ){
				
				logError( "Failed to create rpc proxy: " + Debug.getNestedExceptionMessage( e ));
				
				return( null );
			}
		}
		
		try{
			return( new URL( "http://" + proxy.getHostName() + ":" + proxy.getPort() + "/" ));
			
		}catch( Throwable e ){
			
			Debug.out( e );
			
			return( null );
		}
	}
	
	public boolean
	isConnected()
	{
		return( rpc != null );
	}
	
	public JSONObject
	call(
		JSONObject	request )
	
		throws XMRPCClientException
	{
		if ( rpc == null ){
			
			throw( new XMRPCClientException( "RPC not connected" ));
		}
		
		return( rpc.call(request));
	}
	
	public Map
	getRPCStatus()
	
		throws XMRPCClientException
	{
		if ( rpc == null ){
			
			throw( new XMRPCClientException( "RPC not connected" ));
		}
		
		return( getRPCStatus( rpc ));
	}
	
	private Map
	getRPCStatus(
		XMRPCClient		rpc )
	
		throws XMRPCClientException
	{
		JSONObject	request = new JSONObject();
		
		request.put( "method", "session-get" );
		
		JSONObject reply = rpc.call( request );
		
		String result = (String)reply.get( "result" );
		
		if ( result.equals( "success" )){
			
			Map	args = (Map)reply.get( "arguments" );
						
			return( args );
			
		}else{
			
			throw( new XMRPCClientException( "RPC call failed: " + result ));
		}
	}
	
	public void
	destroy()
	{
		if ( rpc != null ){
	
			String	ac = account.getAccessCode();

			if ( proxy != null ){
				
				proxy.destroy();
				
				proxy = null;
			}
						
			rpc.destroy();
			
			rpc = null;
		
			log( "Disconnected '" + ac + "'" );

			connection_state = CS_DISCONNECTED;
			
			adapter.setConnected( this, false );
		}
	}
	
	private void
	log(
		String	str )
	{
		adapter.log( str );
	}
	
	private void
	logError(
		String	str )
	{
		adapter.logError( str );
	}
}
