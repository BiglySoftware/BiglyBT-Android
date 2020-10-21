/*
 * Created on Aug 27, 2010
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



package com.vuze.client.plugins.utp.core;

import java.io.File;

public interface 
UTPCallback 
{
	public static final int STATE_CONNECT 		= 1;
	public static final int STATE_WRITABLE 		= 2;
	public static final int STATE_EOF 			= 3;
	public static final int STATE_DESTROYING 	= 4;

	public File
	getPluginUserDir();
	
	public File
	getPluginInstallDir();
	
	public void
	log(
		String		str,
		Throwable 	error );
	
	public int
	getRandom();
	
	public long
	getMilliseconds();
	
	public long
	getMicroseconds();
	
	public void
	incomingConnection(
		String		address,
		int			port,
		long		utp_socket,
		long		con_id );
	
	public boolean
	send(
		String		address,
		int			port,
		byte[]		buffer,
		int			length );

	public void
	read(
		long		utp_socket,
		byte[]		data );
	
	public void
	write(
		long		utp_socket,
		byte[]		data );
	
	public int
	getReadBufferSize(
		long		utp_socket );
	
	public void
	setState(
		long		utp_socket,
		int			state );
	
	public void
	error(
		long		utp_socket,
		int			error );
	
	public void
	overhead(
		long		utp_socket,
		boolean		send,
		int			size,
		int			type );
}
