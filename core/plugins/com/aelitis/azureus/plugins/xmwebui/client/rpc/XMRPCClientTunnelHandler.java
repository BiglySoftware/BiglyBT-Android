/*
 * Created on Mar 28, 2013
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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.util.SystemTime;
import org.json.simple.JSONObject;


public class 
XMRPCClientTunnelHandler
	implements XMRPCClient
{
	private String	tunnel_server;
	private String	access_code;
	private String	tunnel_user;
	private String	tunnel_password;
	
	private XMRPCClientTunnel	current_tunnel;
	
	private AtomicInteger	active_calls = new AtomicInteger();
	
	private boolean					destroyed;
	private long					wait_until;
	private int						consecutive_fails;
	private XMRPCClientException	last_error;
	private long					last_create;
	
	public
	XMRPCClientTunnelHandler(
		String	_tunnel_server,
		String	_access_code,
		String	_tunnel_user,
		String	_tunnel_password )
	{
		tunnel_server	= _tunnel_server;
		access_code		= _access_code;
		tunnel_user		= _tunnel_user;
		tunnel_password	= _tunnel_password;
	
		createTunnel();
	}
	
	private void
	createTunnel()
	{
		if ( current_tunnel != null ){
			
			current_tunnel.destroy();
		}
		
		last_create = SystemTime.getMonotonousTime();
		
		current_tunnel = new XMRPCClientTunnel( tunnel_server, access_code, tunnel_user, tunnel_password );
	}
	
	private void
	callOK()
	{
		synchronized( this ){
			
			wait_until			= 0;
			consecutive_fails	= 0;
			last_error			= null;
		}
	}
	
	private boolean
	callFailed(
		XMRPCClientException		exception )
	{		
		synchronized( this ){
			
			consecutive_fails++;
			
			last_error = exception;
			
			long	now = SystemTime.getCurrentTime();
			
			int type = exception.getType();
				
			if ( type == XMRPCClientException.ET_FEATURE_DISABLED ){
				
				return( false );
				
			}else if ( 	type == XMRPCClientException.ET_BAD_ACCESS_CODE || 
						type == XMRPCClientException.ET_NO_BINDING ){
				
				int delay = consecutive_fails * 30*1000;
				
				if ( delay > 2*60*1000 ){
					
					delay =  2*60*1000;
				}
				
				wait_until = now + delay;
				
				createTunnel();
				
				return( false );
				
			}else{
				
				if ( consecutive_fails < 2 ){
					
						// retry the message in case transient issue
					
					return( true );
					
				}else if ( consecutive_fails == 2 ){
					
						// try re-establising the tunnel
					
					createTunnel();
					
					return( true );
					
				}else{
					
					
					int delay = 1000;
					
					for ( int i=1;i<consecutive_fails; i++ ){
					
						delay <<= 2;
					
						if ( delay > 2*60*1000 ){
							
							delay = 2*60*1000;
							
							break;
						}
					}
					
					wait_until = now + delay;
					
					createTunnel();
					
					return( consecutive_fails == 3 );
				}
			}
		}
	}
	
	private void
	isCallPermitted()
	
		throws XMRPCClientException
	{
		synchronized( this ){
			
			long	now = SystemTime.getCurrentTime();
			
			long	rem = wait_until - now;
			
			if ( rem > 0 ){
				
				rem = rem/1000;
				
				if ( rem == 0 ){
					
					rem = 1;
				}
				
				throw( new XMRPCClientException( "Tunnel unavailable for a further " + rem + "s due to failure", last_error ));
			}
			
			if ( consecutive_fails > 5 ){
				
				if ( active_calls.get() > 0 ){
					
					throw( new XMRPCClientException( "Tunnel under construction - request refused", last_error ));
				}
			}
		}
	}
	
	@Override
	public JSONObject
	call(
		JSONObject		request )
	
		throws XMRPCClientException
	{
		while( true ){
			
			isCallPermitted();
			
			try{
				active_calls.incrementAndGet();
				
				JSONObject result = current_tunnel.call(request);
				
				callOK();
				
				return( result );
				
			}catch( XMRPCClientException e ){
				
				if ( !callFailed( e )){
					
					throw( e );
				}
			}finally{
				
				active_calls.decrementAndGet();
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
		while( true ){
			
			isCallPermitted();
			
			try{
				active_calls.incrementAndGet();
				
				HTTPResponse result = current_tunnel.call(method, url, headers, data );
				
				callOK();
				
				return( result );
				
			}catch( XMRPCClientException e ){
				
				if ( !callFailed( e )){
					
					throw( e );
				}
			}finally{
				
				active_calls.decrementAndGet();
			}
		}
	}
	
	@Override
	public void
	destroy()
	{
		if ( destroyed ){
			
			return;
		}
		
		destroyed = true;
		
		if ( current_tunnel != null ){
		
			current_tunnel.destroy();
			
			current_tunnel = null;
		}
	}
}
