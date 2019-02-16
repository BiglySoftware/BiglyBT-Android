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
import java.util.HashMap;
import java.util.Map;

import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.util.Debug;

import com.biglybt.core.networkmanager.impl.TransportHelper;



public class 
UTPTransportHelper
	implements TransportHelper
{
	private static final LogIDs LOGID = LogIDs.NET;
	
	public static final int READ_TIMEOUT		= 30*1000;
	public static final int CONNECT_TIMEOUT		= 20*1000;
	
	final private UTPConnectionManager	manager;
	final private UTPSelector			selector;
	
	final private InetSocketAddress		address;
	final private int					local_port;
	
	private UTPTransport			transport;
	
	private boolean					incoming;
	
	private UTPConnection			connection;
	
	private selectListener		read_listener;
	private Object				read_attachment;
	private boolean 			read_selects_paused;
	
	private selectListener		write_listener;
	private Object				write_attachment;
	private boolean 			write_selects_paused	= true;	// default is paused

	private boolean				connected;
	private boolean				closed;
	private IOException			failed;
	
	private ByteBuffer[]		pending_partial_writes;
		
	private Map<Object,Object>	user_data;
	
	public
	UTPTransportHelper(
		UTPConnectionManager	_manager,
		int						_local_port,
		InetSocketAddress		_address,
		UTPTransport			_transport )
	
		throws IOException
	{
		manager		= _manager;
		local_port	= _local_port;
		address 	= _address;
		transport	= _transport;
		
		incoming	= false;
		
		connection 	= manager.connect( address, this );
		
		selector	= connection.getSelector();
	}
	
	public
	UTPTransportHelper(
		UTPConnectionManager	_manager,
		int						_local_port,
		InetSocketAddress		_address, 
		UTPConnection			_connection )
	{
			// incoming
			
		manager		= _manager;
		local_port	= _local_port;
		address 	= _address;
		connection 	= _connection;
	
		connected	= true;
		incoming	= true;
		
		selector	= connection.getSelector();
	}
	
	protected int
	getLocalPort()
	{
		return( local_port );
	}
	
	protected void
	setTransport(
		UTPTransport	_transport )
	{
		transport	= _transport;
	}

	protected UTPTransport
	getTransport()
	{
		return( transport );
	}
	
	protected int
	getMss()
	{
		if ( transport == null ){
			
			return( UTPNetworkManager.getUdpMssSize());
		}
		
		return( transport.getMssSize());
	}
	
	public boolean
	minimiseOverheads()
	{
		return( UTPNetworkManager.MINIMISE_OVERHEADS );
	}
	
	public int
	getConnectTimeout()
	{
		return( CONNECT_TIMEOUT );
	}
	
	public int
	getReadTimeout()
	{
		return( READ_TIMEOUT );
	}
	
	public InetSocketAddress
	getAddress()
	{
		return( address );
	}
	
	public String
	getName(
		boolean verbose )
	{
		return( "uTP" );
	}
	
	public boolean
	isIncoming()
	{
		return( incoming );
	}
	
	protected UTPConnection
	getConnection()
	{
		return( connection );
	}
	
	protected void
	setConnected()
	{
		synchronized( this ){
			
			if ( connected ){
				
				return;
			}
			
			connected = true;
		}
  				
		transport.connected();
	}
	
	public boolean 
	delayWrite(
		ByteBuffer buffer) 
	{
		if ( pending_partial_writes == null ){
			
			pending_partial_writes = new ByteBuffer[]{ buffer };
			
			return( true );
		}
		
		return( false );
	}
	
	public boolean
	hasDelayedWrite()
	{
		return( pending_partial_writes != null );
	}
	
	public int 
	write( 
		ByteBuffer 	buffer, 
		boolean		partial_write )
	
		throws IOException
	{
		synchronized( this ){
			
			if ( failed != null ){
				
				throw( failed );
			}
			
			if ( closed ){
				
				throw( new IOException( "Transport closed" ));
			}
		}
		
		int	buffer_rem = buffer.remaining();
		
		if ( partial_write && buffer_rem < UTPConnectionManager.MIN_WRITE_PAYLOAD ){
			
			if ( pending_partial_writes == null ){
				
				pending_partial_writes = new ByteBuffer[1];
								
				ByteBuffer	copy = ByteBuffer.allocate( buffer_rem );
				
				copy.put( buffer );
				
				copy.position( 0 );
				
				pending_partial_writes[0] = copy;
				
				return( buffer_rem );
				
			}else{
				
				int	queued = 0;
				
				for ( int i=0;i<pending_partial_writes.length;i++){
					
					queued += pending_partial_writes[i].remaining();
				}
				
				if ( queued + buffer_rem <= UTPConnectionManager.MAX_BUFFERED_PAYLOAD ){
					
					ByteBuffer[] new_ppw = new ByteBuffer[ pending_partial_writes.length+1 ];
					
					for (int i=0;i<pending_partial_writes.length;i++){
						
						new_ppw[i] = pending_partial_writes[i];
					}
					
					ByteBuffer	copy = ByteBuffer.allocate( buffer_rem );
					
					copy.put( buffer );
					
					copy.position( 0 );
					
					new_ppw[pending_partial_writes.length] = copy;
					
					pending_partial_writes = new_ppw;
					
					return( buffer_rem );
				}
			}
		}
		
		if ( pending_partial_writes != null ){
			
			int	ppw_len = pending_partial_writes.length;
			int	ppw_rem	= 0;
			
			ByteBuffer[]	buffers2 = new ByteBuffer[ppw_len+1];
			
			for ( int i=0;i<ppw_len;i++){
				
				buffers2[i] = pending_partial_writes[i];
				
				ppw_rem += buffers2[i].remaining();
			}
			
			buffers2[ppw_len] = buffer;
			
			try{
				int written = connection.write( buffers2, 0, buffers2.length );
				
				if ( written >= ppw_rem ){
					
					return( written - ppw_rem );
					
				}else{
					
					return( 0 );
				}
				
			}finally{
				
				ppw_rem	= 0;
				
				for ( int i=0;i<ppw_len;i++){
										
					ppw_rem += buffers2[i].remaining();
				}
				
				if ( ppw_rem == 0 ){
					
					pending_partial_writes = null;
				}
			}
		}else{
			
			return( connection.write( new ByteBuffer[]{ buffer }, 0, 1 ));
		}
	}

    public long 
    write( 
    	ByteBuffer[] 	buffers, 
    	int 			array_offset, 
    	int 			length ) 
    
    	throws IOException
    {
		synchronized( this ){
			
			if ( failed != null ){
				
				throw( failed );
			}
			
			if ( closed ){
				
				throw( new IOException( "Transport closed" ));
			}
		}
		
		if ( pending_partial_writes != null ){
			
			int	ppw_len = pending_partial_writes.length;
			int	ppw_rem	= 0;
			
			ByteBuffer[]	buffers2 = new ByteBuffer[length+ppw_len];
			
			for ( int i=0;i<ppw_len;i++){
				
				buffers2[i] = pending_partial_writes[i];
				
				ppw_rem += buffers2[i].remaining();
			}
			
			int	pos = ppw_len;

			for (int i=array_offset;i<array_offset+length;i++){
				
				buffers2[pos++] = buffers[i];
			}
			
			try{
				int written = connection.write( buffers2, 0, buffers2.length );
				
				if ( written >= ppw_rem ){
					
					return( written - ppw_rem );
					
				}else{
					
					return( 0 );
				}
				
			}finally{
				
				ppw_rem	= 0;
				
				for ( int i=0;i<ppw_len;i++){
										
					ppw_rem += buffers2[i].remaining();
				}
				
				if ( ppw_rem == 0 ){
					
					pending_partial_writes = null;
				}
			}
		}else{
		
			return( connection.write( buffers, array_offset, length ));
		}
    }

    public int 
    read( 
    	ByteBuffer buffer ) 
    
    	throws IOException
    {
		synchronized( this ){
			
			if ( failed != null ){
				
				throw( failed );
			}
			
			if ( closed ){
				
				throw( new IOException( "Transport closed" ));
			}
		}
		
    	return( connection.read( buffer ));
    }

    public long 
    read( 
    	ByteBuffer[] 	buffers, 
    	int 			array_offset, 
    	int 			length ) 
    
    	throws IOException
    {
		synchronized( this ){
			
			if ( failed != null ){
				
				throw( failed );
			}
			
			if ( closed ){
				
				throw( new IOException( "Transport closed" ));
			}
		}
		
    	long	total = 0;
    	
    	for (int i=array_offset;i<array_offset+length;i++){
    		
    		ByteBuffer	buffer = buffers[i];
    		
    		int	max = buffer.remaining();
    		
    		int	read = connection.read( buffer );
    		
    		total += read;
    		
    		if ( read < max ){
    		
    			break;
    		}
    	}
    	//System.out.println( "total = " + total );
    	return( total );
    }

    protected void
    canRead()
    {
    	fireReadSelect();
    }
    
    protected void
    canWrite()
    {
    	fireWriteSelect();
    }
    
    public synchronized void
    pauseReadSelects()
    {
    	if ( read_listener != null ){
    		
    		selector.cancel( this, read_listener );
    	}
    	
    	read_selects_paused	= true;
    }
    
    public synchronized void
    pauseWriteSelects()
    {
    	if ( write_listener != null ){
    		
    		selector.cancel( this, write_listener );
    	}
    	
    	write_selects_paused = true;
    }
 
    public synchronized void
    resumeReadSelects()
    {
    	read_selects_paused = false;
    	
    	fireReadSelect();
    }
    
    public synchronized void
    resumeWriteSelects()
    {
    	write_selects_paused = false;
    	    	
    	fireWriteSelect();
    }
    
    public void
    registerForReadSelects(
    	selectListener	listener,
    	Object			attachment )
    {
    	synchronized( this ){
    		
	    	read_listener		= listener;
	    	read_attachment		= attachment;
    	}
    	
    	resumeReadSelects();
    }
    
    public void
    registerForWriteSelects(
    	selectListener	listener,
    	Object			attachment )
    {
    	synchronized( this ){
    		
	      	write_listener		= listener;
	    	write_attachment	= attachment;  
    	} 
    	
    	resumeWriteSelects();
    }
    
    public synchronized void
    cancelReadSelects()
    {
    	selector.cancel( this, read_listener );
    	
    	read_selects_paused	= true;
      	read_listener		= null;
    	read_attachment		= null;
    }
    
    public synchronized void
    cancelWriteSelects()
    {
       	selector.cancel( this, write_listener );
        
    	write_selects_paused	= true;
     	write_listener			= null;
    	write_attachment		= null;
    }
    
    protected void
    fireReadSelect()
    {
     	synchronized( this ){
     		 
	   		if ( read_listener != null && !read_selects_paused ){
	   			
	   			if ( failed != null  ){
	   				
	   	 			selector.ready( this, read_listener, read_attachment, failed );
	   	 		  
	   			}else if ( closed ){
	   				
	   	   			selector.ready( this, read_listener, read_attachment, new Throwable( "Transport closed" ));
	   	   		 
	   			}else if ( connection.canRead()){
	   				
	   	 			selector.ready( this, read_listener, read_attachment );
	   			}
	   		}
     	}
    }
    
    protected void
    fireWriteSelect()
    {
      	synchronized( this ){
       	    
	   		if ( write_listener != null && !write_selects_paused ){
	   			
	   			if ( failed != null  ){
	   				
	   				write_selects_paused	= true;
	   				
	   	 			selector.ready( this, write_listener, write_attachment, failed );
	   	 		  
	   			}else if ( closed ){
	   				
	   				write_selects_paused	= true;

	   	   			selector.ready( this, write_listener, write_attachment, new Throwable( "Transport closed" ));

	   			}else if ( connection.canWrite()){
	   				
	   				write_selects_paused	= true;
	   					   				
	   	 			selector.ready( this, write_listener, write_attachment );
	   			}
	   		}
    	}
    }
    
    public void
    failed(
    	Throwable	reason )
    {
    	close( Debug.getNestedExceptionMessage( reason ));
    }
    
	public boolean
	isClosed()
	{
		synchronized( this ){
		
			return( closed );
		}
	}
	
    public void
    close(
    	String	reason )
    {
    	synchronized( this ){
    		
       		closed	= true;
       		
    		fireReadSelect();
      		fireWriteSelect();
      	}
    	
    	if ( transport != null ){
    		
    		transport.closed();
    	}
    	
    	if ( connection != null ){
    	
    		connection.closeSupport( reason );
    	}
    }
    
	protected void
	poll()
	{
	   	synchronized( this ){
	   		
	   		fireReadSelect();
	   		
	   		fireWriteSelect();
	   	}
	}
	
	public synchronized void
	setUserData(
		Object	key,
		Object	data )
	{
		if ( user_data == null ){

			user_data = new HashMap<Object, Object>();
		}

		user_data.put( key, data );
	}

	public synchronized Object
	getUserData(
		Object	key )
	{
		if ( user_data == null ){

			return( null );
		}

		return( user_data.get( key ));
	}
	
	public void
	setTrace(
		boolean	on )
	{
	}
	
	public void 
	setScatteringMode(
		long forBytes) 
	{	
	}
}
