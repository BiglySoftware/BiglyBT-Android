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

import java.nio.ByteBuffer;
import java.util.List;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.impl.PEPeerControl;
import com.biglybt.core.util.AEGenericCallback;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AsyncDispatcher;
import com.biglybt.core.util.Debug;

import com.biglybt.core.networkmanager.NetworkConnection;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.TransportEndpoint;
import com.biglybt.core.networkmanager.impl.ProtocolDecoder;
import com.biglybt.core.networkmanager.impl.TransportCryptoManager;
import com.biglybt.core.networkmanager.impl.TransportHelperFilter;
import com.biglybt.core.networkmanager.impl.TransportHelperFilterTransparent;
import com.biglybt.core.networkmanager.impl.TransportImpl;


public class 
UTPTransport
	extends TransportImpl
{
	private static final LogIDs LOGID = LogIDs.NET;
	
	private static AsyncDispatcher	dispatcher = new AsyncDispatcher( "utp:condisp" );
	
	private UTPConnectionManager	manager;
	private ProtocolEndpointUTP		endpoint;
	private boolean					connect_with_crypto;
	private boolean					fallback_allowed;
	private byte[][]				shared_secrets;
	
	private int						fallback_count;
	
	private int transport_mode = TRANSPORT_MODE_NORMAL;
	
	private boolean				connected;
	private boolean 			cp_pending;
	private ByteBuffer			cp_initial_data;
	private ConnectListener		cp_listener;
	
	private volatile boolean	closed;
	
	protected 
	UTPTransport( 
		UTPConnectionManager	_manager,
		ProtocolEndpointUTP		_endpoint, 
		boolean 				_use_crypto, 
		boolean 				_allow_fallback, 
		byte[][] 				_shared_secrets ) 
	{
		manager					= _manager;
		endpoint				= _endpoint;  
		connect_with_crypto 	= _use_crypto;
		shared_secrets			= _shared_secrets;
		fallback_allowed  		= _allow_fallback;
	}

	protected
	UTPTransport(
		UTPConnectionManager	_manager,
		ProtocolEndpointUTP		_endpoint,
		TransportHelperFilter	_filter )
	{
		manager			= _manager;
		endpoint		= _endpoint;
	
		setFilter( _filter );
	}
	
	public boolean 
	isTCP()
	{ 
		return( false );
	}
	
	public String
	getProtocol()
	{
		return( "uTP" );
	}
	
	public TransportEndpoint 
	getTransportEndpoint()
	{
		return( new TransportEndpointUTP( endpoint ));
	}
	  
	public int
	getMssSize()
	{
	  return( UTPNetworkManager.getUdpMssSize());
	}
	 
	public String 
	getDescription()
	{
		return( endpoint.getAddress().toString());
	}
	
	public void 
	setTransportMode( 
		int mode )
	{
		transport_mode	= mode;
	}
	 
	public int 
	getTransportMode()
	{
		return( transport_mode );
	}
	
	public void
	connectOutbound(
		final ByteBuffer		initial_data,
		final ConnectListener 	listener,
		final int				priority )
	{
		if ( !UTPNetworkManager.UTP_OUTGOING_ENABLED ){
			
			listener.connectFailure( new Throwable( "Outgoing uTP connections disabled" ));
			
			return;
		}
		
		if ( closed ){
			
			listener.connectFailure( new Throwable( "Connection already closed" ));
			
			return;
		}
		    
		if( getFilter() != null ){
		     
			listener.connectFailure( new Throwable( "Already connected" ));
			
			return;
		}
	    
		if ( COConfigurationManager.getBooleanParameter( "Proxy.Data.Enable" )){
			
			listener.connectFailure( new Throwable( "uTP proxy connection not supported" ));
			
			return;
		}
		
		int time = listener.connectAttemptStarted( -1 );
		
		if ( time != -1 ){
			
			Debug.out( "uTP connect time override not supported" );
		}
		
		UTPTransportHelper helper = null;
		
		try{
			helper = new UTPTransportHelper( manager, endpoint.getAddress(), this );
		
			final UTPTransportHelper f_helper = helper;
			
		  	if ( connect_with_crypto ){
		  		
		    	TransportCryptoManager.getSingleton().manageCrypto( 
		    		helper, 
		    		shared_secrets, 
		    		false, 
		    		initial_data, 
		    		new TransportCryptoManager.HandshakeListener() 
		    		{
		    			public void 
		    			handshakeSuccess( 
		    				ProtocolDecoder decoder, 
		    				ByteBuffer 		remaining_initial_data ) 
		    			{    			
			    			TransportHelperFilter filter = decoder.getFilter();
			    			
			    			setFilter( filter );
			    						    			
			    			connectedOutbound( remaining_initial_data, listener );         
		    			}
	
		    			public void 
		    			handshakeFailure( 
		    				Throwable failure_msg ) 
		    			{        	
		    				if ( 	fallback_allowed && 
		    						NetworkManager.OUTGOING_HANDSHAKE_FALLBACK_ALLOWED && 
		    						!closed  ){
	
		    					if ( Logger.isEnabled() ){
		    						Logger.log(new LogEvent(LOGID, "crypto handshake failure [" +failure_msg.getMessage()+ "], attempting non-crypto fallback." ));
		    					}
	
		    					fallback_count++;
		    					
		    					connect_with_crypto = false;
	
		    					close( f_helper, "Handshake failure and retry" );
	
		    					closed = false;
	
		    					if ( initial_data != null ){
	
		    						initial_data.position( 0 );
		    					}
		    					
		    					connectOutbound( initial_data, listener, priority );
		    					
		    				}else{
		    					
		    					close( f_helper, "Handshake failure" );
		    					
		    					listener.connectFailure( failure_msg );
		    				}
		    			}
	
		    			public void
		    			gotSecret(
		    				byte[]				session_secret )
		    			{
		    			}
	
		    			public int
		    			getMaximumPlainHeaderLength()
		    			{
		    				throw( new RuntimeException());	// this is outgoing
		    			}
	
		    			public int
		    			matchPlainHeader(
		    					ByteBuffer			buffer )
		    			{
		    				throw( new RuntimeException());	// this is outgoing
		    			}
		    		});
	  		}else{
	  			
		  		setFilter( new TransportHelperFilterTransparent( helper, false ));

		  			// wait until we are actually connected before reporting this
		  		
		  		boolean	already_connected = true;
		  		
		  		synchronized( this ){
		  			
		  			already_connected = connected;
		  			
		  			if ( !already_connected ){
		  		
		  				cp_pending		= true;
		  				cp_initial_data	= initial_data;
		  				cp_listener		= listener;
		  			}
		  		}

		  		if ( already_connected ){
		  			
		  			connectedOutbound( initial_data, listener );
		  		}
		  	}
		}catch( Throwable e ){
			
			Debug.printStackTrace(e);
			
			if ( helper != null ){
			
				helper.close( Debug.getNestedExceptionMessage( e ));
			}
				
			listener.connectFailure( e );
		}
	}
	
	protected void
	connected()
	{
		final ByteBuffer		initial_data;
		final ConnectListener	listener;
		
		synchronized( this ){
			
			connected = true;
							
			if ( cp_pending ){
				
				initial_data	= cp_initial_data;
				listener		= cp_listener;
				
				cp_pending 		= false;
				cp_initial_data = null;
				cp_listener		= null;
				
			}else{
				
				return;
			}
		}
		
			// need to get off of this thread due to deadlock potential
		
		dispatcher.dispatch(
			new AERunnable()
			{
				public void
				runSupport()
				{
					connectedOutbound( initial_data, listener );
				}
			});
	}
	
	protected void
	closed()
	{
		final ConnectListener	listener;
		
		synchronized( this ){
			
			if ( cp_pending ){
				
				cp_pending = false;
				
				listener = cp_listener;
				
				cp_listener = null;
				
			}else{
				
				return;
			}
		}
		
		if ( listener != null ){
			
			// need to get off of this thread due to deadlock potential
			
			dispatcher.dispatch(
				new AERunnable()
				{
					public void
					runSupport()
					{
						listener.connectFailure( new Throwable( "Connection closed" ));
					}
				});
		}
	}
	
	protected void
	connectedOutbound(
		ByteBuffer			remaining_initial_data,
		ConnectListener		listener )
	{
		TransportHelperFilter	filter = getFilter();

		if ( Logger.isEnabled()){
			Logger.log(new LogEvent(LOGID, "Outgoing uTP stream to " + endpoint.getAddress() + " established, type = " + (filter==null?"<unknown>":filter.getName(false))));
		}

		if ( closed ){

			if ( filter != null ){

				filter.getHelper().close( "Connection closed" );

				setFilter( null );
			}

			listener.connectFailure( new Throwable( "Connection closed" ));

		}else{

			connectedOutbound();

			listener.connectSuccess( this, remaining_initial_data );
		}
	}
	 
	private void
	close(
		UTPTransportHelper		helper,
		String					reason )
	{
		helper.close( reason );
		
		close( reason );
	}
	  
	public void 
	close(
		String	reason )
	{
		closed	= true;
		
		readyForRead( false );
		readyForWrite( false );

		TransportHelperFilter	filter = getFilter();
		
		if ( filter != null ){
			
			filter.getHelper().close( reason );
			
			setFilter( null );
		}
		
		closed();
	}
	
	public boolean
	isClosed()
	{
		return( closed );
	}
	
	public void
	bindConnection(
		NetworkConnection	connection )
	{
		if ( manager.preferUTP()){
			
			final Object[] existing = { null };
		
			existing[0] = 
				connection.setUserData(
				"RoutedCallback",
				new AEGenericCallback()
				{
					public Object 
					invoke( 
						Object arg ) 
					{
						try{
							PEPeerControl control = (PEPeerControl)arg;
							
							List<PEPeer> peers = control.getPeers( endpoint.getAddress().getAddress().getHostAddress());
							
							for ( PEPeer peer: peers ){
								
								if ( 	!peer.isIncoming() &&
										peer.getTCPListenPort() == endpoint.getAddress().getPort()){
									
									manager.log( "Overriding existing connection to " + endpoint.getAddress());
									
									control.removePeer( peer, "Replacing outgoing with incoming uTP connection" );
								}
							}
							return( null );
							
						}finally{
							
							if ( existing[0] instanceof AEGenericCallback ){
								
								((AEGenericCallback)existing[0]).invoke( arg );
							}
						}
					}
				});
		}
	}
}
