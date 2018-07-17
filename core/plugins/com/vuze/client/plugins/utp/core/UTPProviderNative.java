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



package com.vuze.client.plugins.utp.core;

import java.io.File;
import java.nio.ByteBuffer;

import com.biglybt.core.util.Debug;

import com.vuze.client.plugins.utp.UTPPlugin;
import com.vuze.client.plugins.utp.UTPProvider;
import com.vuze.client.plugins.utp.UTPProviderCallback;
import com.vuze.client.plugins.utp.UTPProviderException;

public class 
UTPProviderNative
	implements UTPProvider
{
	public
	UTPProviderNative()
	{
		this( false );
	}
	
	public
	UTPProviderNative(
		boolean		test_mode )
	{	
	}
	
	public int
	getVersion()
	{
		return( 1 );
	}
	
	public boolean
	load(
		final UTPProviderCallback			callback )
	{
		return( 
			UTPInterface.load( 
				new UTPCallback()
				{
					public File
					getPluginUserDir()
					{
						return( callback.getPluginUserDir());
					}
					
					public File
					getPluginInstallDir()
					{
						return( callback.getPluginInstallDir());
					}
					
					public void
					log(
						String		str,
						Throwable 	error )
					{
						callback.log(str, error);
					}
					
					public int
					getRandom()
					{
						return( callback.getRandom());
					}
					
					public long
					getMilliseconds()
					{
						return( callback.getMilliseconds());
					}
					
					public long
					getMicroseconds()
					{
						return( callback.getMicroseconds());
					}
					
					public void
					incomingConnection(
						String		address,
						int			port,
						long		utp_socket,
						long		con_id )
					{
						callback.incomingConnection(address, port, utp_socket, con_id);
					}
					
					public boolean
					send(
						String		address,
						int			port,
						byte[]		buffer,
						int			length )
					{
						return( callback.send(address, port, buffer, length));
					}
	
					public void
					read(
						long		utp_socket,
						byte[]		data )
					{
						callback.read(utp_socket, data);
					}
					
					public void
					write(
						long		utp_socket,
						byte[]		data )
					{
						callback.write( utp_socket, data, 0, data.length );
					}
					
					public int
					getReadBufferSize(
						long		utp_socket )
					{
						return( callback.getReadBufferSize(utp_socket));
					}
					
					public void
					setState(
						long		utp_socket,
						int			state )
					{
						callback.setState(utp_socket, state);
					}
					
					public void
					error(
						long		utp_socket,
						int			error )
					{
						callback.error(utp_socket, error);
					}
					
					public void
					overhead(
						long		utp_socket,
						boolean		send,
						int			size,
						int			type )
					{
						callback.overhead(utp_socket, send, size, type);
					}
				}));
	}
	
	public  void
	checkTimeouts()
	{
		UTPInterface.checkTimeouts();
	}
	
	public void
	incomingIdle()
	{
	}
	
	public boolean
	isValidPacket(
		byte[]		data,
		int			length )
	{
		return( true );
	}
	
	public long[] 
	connect(
		String		to_address,
		int			to_port )
	
		throws UTPProviderException
	{
		return( UTPInterface.connect(to_address, to_port));
	}
	
	public boolean
	receive(
		String		from_address,
		int			from_port,
		byte[]		data,
		int			length )
	
		throws UTPProviderException
	{
		return( UTPInterface.receive(from_address, from_port, data, length));
	}
	
	public boolean
	write(
		long		utp_socket,
		int			avail_bytes )
	
		throws UTPProviderException
	{
		return( UTPInterface.write(utp_socket, avail_bytes));
	}
	
	public boolean
	write(
		long			utp_socket,
		ByteBuffer[]	buffers,
		int				start,
		int				len )
	
		throws UTPProviderException
	{
		throw( new UTPProviderException( "Not Supported" ));
	}
	
	public void
	receiveBufferDrained(
		long		utp_socket )
	
		throws UTPProviderException
	{
		UTPInterface.receiveBufferDrained(utp_socket);
	}
		
	public void
	close(
		long		utp_socket )
	
		throws UTPProviderException
	{
		UTPInterface.close(utp_socket);
	}
	
	public void
	setSocketOptions(
		long		fd )
	
		throws UTPProviderException
	{
		UTPInterface.setSocketOptions(fd);	
	}
	
	public void
	setOption(
		int		option,
		int		value )
	{
		Debug.out( "Not Supported" );
	}
}
