/*
 * Created on Jan 30, 2014
 * Created by Paul Gardner
 * 
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
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

import com.vuze.client.plugins.utp.UTPProviderException;


public interface 
UTPTranslated 
{
	public static final int UTP_STATE_CONNECT		= 1;
	public static final int UTP_STATE_WRITABLE		= 2;
	public static final int UTP_STATE_EOF			= 3;
	public static final int UTP_STATE_DESTROYING	= 4;

	public void 
	UTP_CheckTimeouts();
		
	public boolean
	isValidPacket(
		byte[]					buffer,
		int						len );
	
	public UTPSocket 
	UTP_Create(
		SendToProc 				send_to_proc, 
		Object 					send_to_userdata, 
		InetSocketAddress 		addr )
	
		throws UTPProviderException;
	
	public boolean 
	UTP_IsIncomingUTP(
		UTPGotIncomingConnection 	incoming_proc,
		SendToProc 					send_to_proc, 
		Object 						send_to_userdata,
		byte[] 						buffer, 
		int 						len, 
		InetSocketAddress 			addr )
	
		throws UTPProviderException;
	
	public void
	UTP_Connect(
		UTPSocket				conn )
	
		throws UTPProviderException;
	
	public void 
	UTP_SetCallbacks(
		UTPSocket 				conn, 
		UTPFunctionTable 		funcs, 
		Object 					userdata )
	
		throws UTPProviderException;

	public void 
	UTP_GetPeerName(
		UTPSocket 				conn, 
		InetSocketAddress[] 	addr_out);
	
	public int
	UTP_GetSocketConnectionID(
		UTPSocket				conn );
	
	public boolean
	UTP_Write(
		UTPSocket				conn,
		int						bytes );
		
	public void 
	UTP_RBDrained(
		UTPSocket				 conn );
	
	public void
	UTP_Close(
		UTPSocket				conn );
		
		// version 2
	
	public void
	UTP_IncomingIdle();

	public UTPSocket
	UTP_Create()
	
		throws UTPProviderException;
	
	public void
	UTP_SetUserData(
		UTPSocket				conn,
		Object					user_data )
	
		throws UTPProviderException;
	
	public void
	UTP_Connect(
		UTPSocket				conn,
		InetSocketAddress		address )
				
		throws UTPProviderException;
	
	public boolean
	UTP_Write(
		UTPSocket				conn,
		ByteBuffer[]			buffers,
		int						start,
		int						len )
	
		throws UTPProviderException;
	
	public int
	UTP_GetOption(
		int						option );
	
	public void
	UTP_SetOption(
		int						option,
		int						value );
	
		//

	public interface 
	SendToProc
	{
		public void 
		send_to_proc(
			Object				user_data,
			byte[]				data,
			InetSocketAddress	addr );
	}

	public interface 
	UTPGotIncomingConnection
	{
		public void
		got_incoming_connection(
			Object		user_data,
			UTPSocket	socket );
	}
	
	public interface 
	UTPFunctionTable
	{
		public void 
		on_read(
			Object 		userdata,
			ByteBuffer 	bytes, 
			int 		count );
		
		public void 
		on_write(
			Object 		userdata, 
			byte[] 		bytes, 
			int 		offset, 
			int 		count );
		
		public int  
		get_rb_size(
			Object 		userdata );
		
		public void 
		on_state(
			Object 		userdata, 
			int 		state);
		
		public void 
		on_error(
			Object 		userdata, 
			int 		errcode );
		
		public void 
		on_overhead(
			Object 		userdata, 
			boolean 	send, 
			int 		count, 
			int 		type );
	}
}
