/*
 * Created on Aug 28, 2010
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



package com.aelitis.azureus.core.networkmanager.impl.utp;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import com.biglybt.core.util.AddressUtils;

import com.biglybt.core.networkmanager.ConnectionEndpoint;
import com.biglybt.core.networkmanager.ProtocolEndpoint;
import com.biglybt.core.networkmanager.ProtocolEndpointFactory;
import com.biglybt.core.networkmanager.ProtocolEndpointHandler;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.networkmanager.Transport.ConnectListener;

public class 
ProtocolEndpointUTP
	implements ProtocolEndpoint
{
	private static UTPConnectionManager	manager;
	
	public static void
	register(
		UTPConnectionManager	_manager )
	{
		manager	= _manager;
		
		ProtocolEndpointFactory.registerHandler(
			new ProtocolEndpointHandler()
			{
				public int
				getType()
				{
					return( ProtocolEndpoint.PROTOCOL_UTP );
				}
				
				public ProtocolEndpoint
				create(
					InetSocketAddress		address )
				{
					return( new ProtocolEndpointUTP( address ));
				}
				
				public ProtocolEndpoint
				create(
					ConnectionEndpoint		connection_endpoint,
					InetSocketAddress		address )
				{
					return( new ProtocolEndpointUTP( connection_endpoint, address ));
				}
			});
	}
	
	private ConnectionEndpoint		ce;
	private InetSocketAddress		address;
	
	private
	ProtocolEndpointUTP(
		ConnectionEndpoint		_ce,
		InetSocketAddress		_address )
	{
		ce		= _ce;
		address	= _address;
		
		ce.addProtocol( this );
	}
	
	private
	ProtocolEndpointUTP(
		InetSocketAddress		_address )
	{
		ce			= new ConnectionEndpoint(_address );
		address		= _address;
		
		ce.addProtocol( this );
	}
	
	public void
	setConnectionEndpoint(
		ConnectionEndpoint		_ce )
	{
		ce	= _ce;
		
		ce.addProtocol( this );
	}
	
	public int
	getType()
	{
		return( PROTOCOL_UTP );
	}
	
	public InetSocketAddress
	getAddress()
	{
		return( address );
	}
	
	public InetSocketAddress 
	getAdjustedAddress(
		boolean to_lan )
	{
		return( AddressUtils.adjustTCPAddress( address, to_lan ));
	}
	
	public ConnectionEndpoint
	getConnectionEndpoint()
	{
		return( ce );
	}
	
	public Transport
	connectOutbound(
		boolean				connect_with_crypto, 
		boolean 			allow_fallback, 
		byte[][]			shared_secrets,
		ByteBuffer			initial_data,
		int					priority,
		ConnectListener 	listener )
	{
		UTPTransport t = new UTPTransport( manager, this, connect_with_crypto, allow_fallback, shared_secrets );
		
		t.connectOutbound( initial_data, listener, priority );
		
		return( t );
	}
	
	public String
	getDescription()
	{
		return( address.toString());
	}
}
