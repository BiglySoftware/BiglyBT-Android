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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SystemTime;



public class 
UTPConnection 
{
	private UTPConnectionManager	manager;
	private long					utp_socket;
	private InetSocketAddress		remote_address;
	private long					con_id;
	private UTPTransportHelper		transport;
	
	private List<ByteBuffer>	read_buffers = new LinkedList<ByteBuffer>();
		
	private volatile boolean	connected	= true;
	private boolean				is_incoming;
	private volatile boolean	is_writable;
	
	private volatile boolean	is_unusable;
	
	private long				total_received;
	private long				total_sent;
	
	private int					last_read_buff	= -1;
	private int					last_read		= -1;
	
	private int					last_write_buff	= -1;
	private int					last_write		= -1;
	
	private long				close_time;
	
	protected
	UTPConnection(
		UTPConnectionManager	_manager,
		InetSocketAddress		_remote_address,
		UTPTransportHelper		_transport,		// null for incoming
		long					_utp_socket,
		long					_con_id )
		
	{
		manager			= _manager;
		remote_address	= _remote_address;
		transport		= _transport;
		utp_socket		= _utp_socket;
		con_id			= _con_id;
		
		if ( transport == null ){
			
			is_writable = true;
			
			is_incoming	= true;
		}
	}
	
	protected InetSocketAddress
	getRemoteAddress()
	{
		return( remote_address );
	}
	
	protected long
	getSocket()
	{
		return( utp_socket );
	}
	
	protected long
	getConnectionID()
	{
		return( con_id );
	}
	
	protected UTPSelector
	getSelector()
	{
		return( manager.getSelector());
	}
	
	
	public boolean
	isIncoming()
	{
		return( is_incoming );
	}
	
	protected void
	setTransport(
		UTPTransportHelper	_transport )
	{
		transport	= _transport;
	}
	
	protected UTPTransportHelper
	getTransport()
	{
		return( transport );
	}
	
	protected void
	setConnected()
	{
		if ( transport != null ){
			
			transport.setConnected();
		}
	}
	
	protected void
	receive(
		ByteBuffer		data )
	
		throws IOException
	{
			// packets reach us using 8K space regardless of content - trim this back for small protocol
			// messages to save memory
		
		int	rem = data.remaining();
	
		total_received += rem;
		
		if ( rem < 256 ){
			
			byte[]	temp = new byte[rem];
			
			data.get( temp );
			
			data = ByteBuffer.wrap( temp );
		}
				
		if ( !connected ){
			
			throw( new IOException( "Transport closed" ));
		}
		
		boolean	was_empty = false;
	
		synchronized( read_buffers ){
		
			was_empty = read_buffers.size() == 0;
			
			read_buffers.add( data );
		}
				
		if ( was_empty ){
			
			transport.canRead();
		}
	}
	
	protected int
	getReceivePendingSize()
	{
		synchronized( read_buffers ){

			int	res = 0;
			
			for ( ByteBuffer b: read_buffers ){
			
				res += b.remaining();
			}
			
			return( res );
		}
	}
	
	protected boolean
	canRead()
	{
		synchronized( read_buffers ){

			return( read_buffers.size() > 0 );
		}
	}
	
	protected void
	setCanWrite(
		boolean	b )
	{
		if ( is_writable != b ){
			
			is_writable = b;
			
			if ( is_writable ){
				
				transport.canWrite();
			}
		}
	}
	
	protected boolean
	canWrite()
	{
		return( is_writable );
	}
	
	protected int 
	write( 
		ByteBuffer[] 	buffers,
		int				offset,
		int				length )
	
		throws IOException
	{
		int	max = 0;
		
		for ( int i=offset;i<offset+length;i++){
			
			max += buffers[i].remaining();
		}
		
		last_write_buff	= max;
		
		if ( !is_writable ){
			
			last_write  = 0;
			
			return( 0 );
		}
		
		int	written = manager.write( this, buffers, offset, length );
		
		total_sent += written;
		
		last_write	= written;
		
		// System.out.println( "Connection(" + getID() + ") - write -> " + written );
		
		return( written );
	}
	
	protected int
	read(
		ByteBuffer	buffer )
	
		throws IOException
	{
		int	total = 0;
		
		boolean	drained = false;
		
		last_read_buff = buffer.remaining();
		
		synchronized( read_buffers ){

			while( read_buffers.size() > 0 ){
				
				int	rem = buffer.remaining();
				
				if ( rem == 0 ){
					
					break;
				}

				ByteBuffer	b = (ByteBuffer)read_buffers.get(0);
								
				int	old_limit = b.limit();
				
				if ( b.remaining() > rem ){
					
					b.limit( b.position() + rem );
				}
				
				buffer.put( b );
				
				b.limit( old_limit );
				
				total += rem - buffer.remaining();
				
				if ( b.hasRemaining()){
					
					break;
					
				}else{
					
					read_buffers.remove(0);
				}
			}
			
			drained = read_buffers.size() == 0;
		}
		
		if ( drained ){
			
			manager.readBufferDrained( this );
		}
		
		last_read = total;
		
		// System.out.println( "Connection(" + getID() + ") - read -> " +total );

		return( total );
	}
	
	protected void
	close(
		String	reason )
	{
		if ( transport != null ){
			
			transport.close( reason );
			
		}else{
			
			closeSupport( reason );
		}
	}
	
	protected void
	closeSupport(
		String	reason )
	{
		connected	= false;
				
		manager.close( this, reason );
	}
	
	protected boolean
	isConnected()
	{
		return( connected );
	}
	
	protected void
	setUnusable()
	{
		if ( !is_unusable ){
		
			is_unusable	= true;
			
			close_time	= SystemTime.getMonotonousTime();
		}
	}
	
	protected boolean
	isUnusable()
	{
		return( is_unusable );
	}
	
	protected long
	getCloseTime()
	{
		return( close_time );
	}
	
	protected void
	poll()
	{
		if ( transport != null ){
			
			transport.poll();
		}
	}
	
	protected String
	getState()
	{
		return( "sent=" + DisplayFormatters.formatByteCountToKiBEtc( total_sent ) + 
				", received=" + DisplayFormatters.formatByteCountToKiBEtc( total_received ) + 
				", writable=" + is_writable + 
				", last_w_buff=" + last_write_buff + 
				", last_w=" + last_write +
				", last_r_buff=" + last_read_buff + 
				", last_r=" + last_read +
				", read_pend=" + getReceivePendingSize());
	}
	
	public String
	getString()
	{
		return( remote_address + ", socket=" + utp_socket + ", con_id" + con_id + " - " + getState());
	}
}
