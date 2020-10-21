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



package com.vuze.client.plugins.utp;

import java.nio.ByteBuffer;

public interface 
UTPProvider 
{
	public static final int DEFAULT_RECV_BUFFER_KB	= 1024;
	public static final int DEFAULT_SEND_BUFFER_KB	= 1024;
	
	public static final int OPT_RECEIVE_BUFFER	= 1;
	public static final int OPT_SEND_BUFFER		= 2;
	
	public int
	getVersion();
	
	public boolean
	load(
		UTPProviderCallback			callback );
	
	public boolean
	isValidPacket(
		byte[]		data,
		int			length );
	
	public  void
	checkTimeouts();
	
	public void
	incomingIdle();
	
	public long[] 
	connect(
		String		to_address,
		int			to_port )
	
		throws UTPProviderException;
	
	public boolean
	receive(
		String		from_address,
		int			from_port,
		byte[]		data,
		int			length )
	
		throws UTPProviderException;
	
	public boolean
	write(
		long		utp_socket,
		int			avail_bytes )
	
		throws UTPProviderException;
	
	public boolean
	write(
		long			utp_socket,
		ByteBuffer[]	buffers,
		int				start,
		int				len )
	
		throws UTPProviderException;
	
	public void
	receiveBufferDrained(
		long		utp_socket )
	
		throws UTPProviderException;
		
	public void
	close(
		long		utp_socket )
	
		throws UTPProviderException;
	
	
	public void
	setSocketOptions(
		long		fd )
	
		throws UTPProviderException;
	
	public void
	setOption(
		int		option,
		int		value );
}
