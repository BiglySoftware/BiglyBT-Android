/*
 * Created on Jan 23, 2013
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



package com.vuze.client.plugins.utp.loc;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

import com.biglybt.core.util.Debug;
import com.biglybt.core.util.HostNameToIPResolver;

import com.vuze.client.plugins.utp.UTPProvider;
import com.vuze.client.plugins.utp.UTPProviderCallback;
import com.vuze.client.plugins.utp.UTPProviderException;
//import com.vuze.client.plugins.utp.loc.v1.UTPTranslatedV1;
import com.vuze.client.plugins.utp.loc.v2.UTPTranslatedV2;


public class 
UTPProviderLocal
	implements UTPProvider, 
	UTPTranslated.SendToProc, 
	UTPTranslated.UTPGotIncomingConnection, 
	UTPTranslated.UTPFunctionTable
{
	private static final int version = 2;
	
	private boolean				test_mode;
	private UTPTranslated		impl;
	
	private UTPProviderCallback		callback;
	
	private long					socket_id_next;
	
	private Map<Long,UTPSocket>	socket_map = new HashMap<Long, UTPSocket>();
	
	private Map<Integer,Integer>	pending_options = new HashMap<Integer, Integer>();
	
	public 
	UTPProviderLocal()
	{
		this( false );
	}
	
	public 
	UTPProviderLocal(
		boolean	_test_mode )
	{
		test_mode	= _test_mode;
	}
	
	public boolean
	load(
		UTPProviderCallback			_callback )
	{
		synchronized( pending_options ){
			
			if ( impl != null ){
				
				Debug.out( "Already loaded ");
				
				return( false );
			}
			
			callback	= _callback;
			
			if ( version == 1 ){
			
				//impl = new UTPTranslatedV1( callback, test_mode );
				
			}else{
			
				impl = new UTPTranslatedV2( callback, this, this, this, test_mode );
			}
			
			if ( pending_options.size() > 0 ){
				
				for ( Map.Entry<Integer,Integer> entry: pending_options.entrySet()){
					
					setOption( entry.getKey(), entry.getValue());
				}
				
				pending_options.clear();
			}
			
			return( true );
		}
	}
	
	public int
	getVersion()
	{
		return( version );
	}
	
	public boolean
	isValidPacket(
		byte[]		data,
		int			length )
	{
		return( impl.isValidPacket( data, length ));
	}
	
		// callbacks from implementation
	
	public void 
	send_to_proc(
		Object				user_data,
		byte[]				data,
		InetSocketAddress	addr )
	{
		callback.send( addr, data, data.length );
	}
	
	public void
	got_incoming_connection(
		Object		user_data,
		UTPSocket	socket )
	{
		long socket_id;
		
		synchronized( socket_map ){
		
			socket_id = socket_id_next++;
			
			socket_map.put( socket_id, socket );
			
			//System.out.println( "socket_map: " + socket_map.size());
		}
		
		InetSocketAddress[] addr_out = {null};
		
		impl.UTP_GetPeerName( socket, addr_out );
		
		callback.incomingConnection( addr_out[0], socket_id, impl.UTP_GetSocketConnectionID( socket ) & 0x0000ffffL );
		
		try{
			if ( version == 1 ){
			
				impl.UTP_SetCallbacks( socket, this, new Object[]{ socket_id, socket });
			}else{
				
				impl.UTP_SetUserData( socket, new Object[]{ socket_id, socket });
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	public void 
	on_read(
		Object 		user_data,
		byte[]	 	bytes, 
		int 		count )
	{
		long socket_id = (Long)((Object[])user_data)[0];

		callback.read( socket_id, bytes );
	}
	
	public void 
	on_read(
		Object 		user_data,
		ByteBuffer 	bytes, 
		int 		count )
	{
		long socket_id = (Long)((Object[])user_data)[0];

		callback.read( socket_id, bytes );
	}
	
	public void 
	on_write(
		Object		user_data, 
		byte[] 		bytes, 
		int 		offset,
		int			length )
	{
		long socket_id = (Long)((Object[])user_data)[0];

		callback.write( socket_id, bytes, offset, length );
	}
	
	public int  
	get_rb_size(
		Object 		user_data )
	{
		long socket_id = (Long)((Object[])user_data)[0];

		return( callback.getReadBufferSize( socket_id ));
	}
	
	public void 
	on_state(
		Object 		user_data, 
		int 		state )
	{
		long socket_id = (Long)((Object[])user_data)[0];
		
		callback.setState(socket_id, state);
		
		if ( state == UTPTranslated.UTP_STATE_DESTROYING ){
			
			synchronized( socket_map ){
				
				socket_map.remove( socket_id);
				
				//System.out.println( "socket_map: " + socket_map.size());
			}
		}
	}
	
	public void 
	on_error(
		Object 		user_data, 
		int 		errcode )
	{
		long socket_id = (Long)((Object[])user_data)[0];
		
		callback.error( socket_id, errcode );
	}
	
	public void 
	on_overhead(
		Object user_data, 
		boolean send, 
		int count, 
		int type)
	{
		long socket_id = (Long)((Object[])user_data)[0];
		
		callback.overhead( socket_id, send, count, type );
	}
	
	
		// incoming calls from Vuze
	
	public  void
	checkTimeouts()
	{
		impl.UTP_CheckTimeouts();
	}
	
	public void
	incomingIdle()
	{
		impl.UTP_IncomingIdle();
	}
	
	public long[] 
	connect(
		String		to_address,
		int			to_port )
	
		throws UTPProviderException
	{
		try{
			UTPSocket 	socket;
			long		socket_id;
			
			if ( version == 1 ){
				
				socket = impl.UTP_Create( this, "", new InetSocketAddress( HostNameToIPResolver.syncResolve( to_address), to_port ));
				
				if ( socket == null ){
					
					throw( new UTPProviderException( "Failed to create socket" ));
				}
								
				synchronized( socket_map ){
				
					socket_id = socket_id_next++;
					
					socket_map.put( socket_id, socket );
					
					//System.out.println( "socket_map: " + socket_map.size());
				}
				
				impl.UTP_SetCallbacks( socket, this, new Object[]{ socket_id, socket });
				
				impl.UTP_Connect( socket );
				
			}else{
				
				socket = impl.UTP_Create();
				
				if ( socket == null ){
					
					throw( new UTPProviderException( "Failed to create socket" ));
				}
				
				synchronized( socket_map ){
					
					socket_id = socket_id_next++;
					
					socket_map.put( socket_id, socket );
					
					//System.out.println( "socket_map: " + socket_map.size());
				}
				
				impl.UTP_SetUserData( socket, new Object[]{ socket_id, socket });
				
				impl.UTP_Connect( socket, new InetSocketAddress( HostNameToIPResolver.syncResolve( to_address), to_port ));
			}
			
			return( new long[]{ socket_id, impl.UTP_GetSocketConnectionID( socket ) & 0x0000ffffL });
			
		}catch( UTPProviderException e ){
			
			throw( e );
			
		}catch( Throwable e ){
			
			throw( new UTPProviderException( "connect failed", e ));
		}
	}
	
	public boolean
	receive(
		String		from_address,
		int			from_port,
		byte[]		data,
		int			length )
	
		throws UTPProviderException
	{
		try{
			return( impl.UTP_IsIncomingUTP(this, this, "", data, length, new InetSocketAddress( HostNameToIPResolver.syncResolve( from_address), from_port )));
			
		}catch( Throwable e ){
			
			throw( new UTPProviderException( "receive failed", e ));
		}
	}
	
	public boolean
	write(
		long		utp_socket,
		int			avail_bytes )
	
		throws UTPProviderException
	{
		UTPSocket socket;
		
		synchronized( socket_map ){
			
			socket = socket_map.get( utp_socket );
		}
		
		if ( socket != null ){
			
			return( impl.UTP_Write( socket, avail_bytes ));
		}
		
		throw( new UTPProviderException( "Unknown socket" ));
	}
	
	public boolean
	write(
		long			utp_socket,
		ByteBuffer[]	buffers,
		int				start,
		int				len )
	
		throws UTPProviderException
	{
		UTPSocket socket;
		
		synchronized( socket_map ){
			
			socket = socket_map.get( utp_socket );
		}
		
		if ( socket != null ){
			
			return( impl.UTP_Write( socket, buffers, start, len ));
		}
		
		throw( new UTPProviderException( "Unknown socket" ));
	}
	
	public void
	receiveBufferDrained(
		long		utp_socket )
	
		throws UTPProviderException
	{
		UTPSocket socket;
		
		synchronized( socket_map ){
			
			socket = socket_map.get( utp_socket );
		}
		
		if ( socket != null ){
			
			impl.UTP_RBDrained( socket );
			
		}else{
		
			throw( new UTPProviderException( "Unknown socket" ));
		}
	}
		
	public void
	close(
		long		utp_socket )
	
		throws UTPProviderException
	{
		UTPSocket socket;
		
		synchronized( socket_map ){
			
			socket = socket_map.remove( utp_socket );
			
			//System.out.println( "socket_map: " + socket_map.size());
		}
		
		if ( socket != null ){
			
			impl.UTP_Close( socket );
		}
	}
	
	
	public void
	setSocketOptions(
		long		fd )
	
		throws UTPProviderException
	{
		// this is supposed to be a native method to enable/disable fragmentation
	}
	
	public void
	setOption(
		int		option,
		int		value )
	{
		synchronized( pending_options ){
				
			if ( impl == null ){
				
				pending_options.put( option, value );
				
			}else{
		
				impl.UTP_SetOption( option, value );
			}
		}
	}
}
