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

import java.util.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AsyncDispatcher;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.PluginInterface;

import com.biglybt.core.networkmanager.ConnectionEndpoint;
import com.biglybt.core.networkmanager.ProtocolEndpoint;
import com.biglybt.core.networkmanager.ProtocolEndpointFactory;
import com.biglybt.core.networkmanager.impl.IncomingConnectionManager;
import com.biglybt.core.networkmanager.impl.ProtocolDecoder;
import com.biglybt.core.networkmanager.impl.TransportCryptoManager;
import com.biglybt.core.networkmanager.impl.TransportHelperFilter;
import com.biglybt.net.udp.uc.PRUDPPacketHandler;
import com.vuze.client.plugins.utp.UTPPlugin;
import com.vuze.client.plugins.utp.UTPProvider;
import com.vuze.client.plugins.utp.UTPProviderCallback;
import com.vuze.client.plugins.utp.UTPProviderFactory;


public class 
UTPConnectionManager 
{
	private static final int MIN_MSS	= 256;
	private static final int MAX_HEADER	= 128;
	
	public static final int MIN_WRITE_PAYLOAD		= MIN_MSS - MAX_HEADER;
	public static final int MAX_BUFFERED_PAYLOAD	= 512;

	private static final int CLOSING_TIMOUT			= 2*60*1000;
	private static final int UTP_PROVIDER_TIMEOUT	= 30*1000;
	
	private static final LogIDs LOGID = LogIDs.NET;
	
	private boolean		initialised;
	
	private UTPPlugin				plugin;
	private PRUDPPacketHandler		packet_handler;
	private int						local_port;
	
	private IncomingConnectionManager	incoming_manager = IncomingConnectionManager.getSingleton();

	private AsyncDispatcher	dispatcher = new AsyncDispatcher();
	
	private UTPSelector		selector;
	
	private List<UTPConnection>							connections 			= new ArrayList<UTPConnection>();
	private Map<InetAddress,List<UTPConnection>>		address_connection_map 	= new HashMap<InetAddress, List<UTPConnection>>();
	private Map<Long,UTPConnection>						socket_connection_map 	= new HashMap<Long, UTPConnection>();
	
	private Set<UTPConnection>							closing_connections		= new HashSet<UTPConnection>();
	
		// provider version 1 only
	
	private UTPConnection		active_write;
	private ByteBuffer[]		active_write_buffers;
	private int					active_write_start;
	private int					active_write_len;
	
	private static final long	MAX_INCOMING_QUEUED			= 4*1024*1024;
	private static final long	MAX_INCOMING_QUEUED_LOG_OK	= MAX_INCOMING_QUEUED - 256*1024;
	
	public static final int	DEFAULT_RECV_BUFFER_KB		= UTPProvider.DEFAULT_RECV_BUFFER_KB;
	public static final int	DEFAULT_SEND_BUFFER_KB		= UTPProvider.DEFAULT_SEND_BUFFER_KB;
	
	private long				total_incoming_queued;
	private int					total_incoming_queued_log_state;
	
	private boolean	available;
	
	private boolean	hack_worked;
	private long	last_hack_attempt;
	private Object	last_hack;
	
	private boolean	prefer_utp;
	
	private UTPProvider	utp_provider = UTPProviderFactory.createProvider();
	
	private volatile AESemaphore poll_waiter;
	
	public
	UTPConnectionManager(
		UTPPlugin		_plugin )
	{
		plugin		= _plugin;
		
		dispatcher.setPriority( Thread.MAX_PRIORITY - 1 );
	}
	
	public int
	getProviderVersion()
	{
		return( utp_provider.getVersion());
	}
	
	public void
	activate(
		PRUDPPacketHandler		_handler )
	{
		packet_handler		= _handler;
		local_port			= packet_handler.getPort();
		
		synchronized( this){
			
			if ( initialised ){
			
				return;
			}
		
			initialised = true;
		}
				
		final AESemaphore	init_sem = new AESemaphore( "uTP:init" );
		
		PluginInterface pi = plugin.getPluginInterface();
		
		final File plugin_user_dir 	= pi.getPluginconfig().getPluginUserFile( "plugin.properties" ).getParentFile();

		File plugin_install_dir	= new File( pi.getPluginDirectoryName());
		
		if ( plugin_install_dir == null || !plugin_install_dir.exists()){
			
			plugin_install_dir = plugin_user_dir;
		}
		
		final File f_plugin_install_dir = plugin_install_dir;
		
		try{
			available = utp_provider.load( 
					new UTPProviderCallback()
					{
						public File
						getPluginUserDir()
						{
							return( plugin_user_dir );
						}
		
						public File
						getPluginInstallDir()
						{
							return( f_plugin_install_dir );
						}
						
						public void
						log(
							String		str,
							Throwable	error )
						{
							plugin.log(str,error);
						}
						
						public int
						getRandom()
						{
							return( UTPUtils.UTP_Random());
						}
						
						public long
						getMilliseconds()
						{
							return( UTPUtils.UTP_GetMilliseconds());
						}
						
						public long
						getMicroseconds()
						{
							return( UTPUtils.UTP_GetMicroseconds());
						}
						
						public void
						incomingConnection(
							String		host,
							int			port,
							long		utp_socket,
							long		con_id )
						{
							init_sem.reserve();
							
							accept( new InetSocketAddress( host, port),	utp_socket, con_id );
						}
						
						public void
						incomingConnection(
							InetSocketAddress	adress,
							long				utp_socket,
							long				con_id )
						{
							init_sem.reserve();
							
							accept( adress,	utp_socket, con_id );
						}
						
						public boolean
						send(
							String		address,
							int			port,
							byte[]		buffer,
							int			length )
						{
							return( plugin.send( new InetSocketAddress( address, port ), buffer, length ));
						}
						
						public boolean
						send(
							InetSocketAddress	adress,
							byte[]				buffer,
							int					length )
						{
							return( plugin.send( adress, buffer, length ));
						}
						public void
						read(
							long		utp_socket,
							byte[]		data )
						{
							UTPConnection connection;
							
							synchronized( UTPConnectionManager.this ){
								
								connection = socket_connection_map.get( utp_socket );
							}
							
							if ( connection == null ){
								
								Debug.out( "read: unknown socket!" );
								
							}else{
								
								try{
									connection.receive( ByteBuffer.wrap( data ));
									
								}catch( Throwable e ){
																	
									connection.close( Debug.getNestedExceptionMessage(e));
								}
							}
						}
						
						public void
						read(
							long			utp_socket,
							ByteBuffer		bb )
						{
							UTPConnection connection;
							
							synchronized( UTPConnectionManager.this ){
								
								connection = socket_connection_map.get( utp_socket );
							}
							
							if ( connection == null ){
								
								Debug.out( "read: unknown socket!" );
								
							}else{
								
								try{
									connection.receive( bb );
									
								}catch( Throwable e ){
																	
									connection.close( Debug.getNestedExceptionMessage(e));
								}
							}
						}
						
						public void
						write(
							long		utp_socket,
							byte[]		data,
							int			offset,
							int			length )
						{
							UTPConnection connection;
							
							synchronized( UTPConnectionManager.this ){
								
								connection = socket_connection_map.get( utp_socket );
							}
							
							if ( connection == null ){
								
								Debug.out( "write: unknown socket!" );
								
							}else{
								
								try{
									if ( utp_provider.getVersion() != 1 ){
										
										throw( new Exception( "Invalid flow control" ));
									}
									
									if ( active_write != connection ){
										
										throw( new Exception( "Write for incorrect connection!" ));
									}
																	
									int	pos = offset;
									int	rem	= length;
									
									for ( int i=active_write_start; i<active_write_start+active_write_len && rem > 0 ;i++){
										
										ByteBuffer b = active_write_buffers[i];
										
										int	remaining	= b.remaining();
										
										if ( remaining > 0 ){
										
											int	to_read = Math.min( rem, remaining );
											
											b.get( data, pos, to_read );
											
											pos	+= to_read;
											rem -= to_read;
										}
									}
									
									if ( rem != 0 ){
										
										throw( new Exception( "insufficient data available for write operation" ));
									}
								}catch( Throwable e ){
																	
									connection.close( Debug.getNestedExceptionMessage(e));
								}
							}
						}
						
						public int
						getReadBufferSize(
							long		utp_socket )
						{
							UTPConnection connection;
							
							synchronized( UTPConnectionManager.this ){
								
								connection = socket_connection_map.get( utp_socket );
							}
							
							if ( connection == null ){
								
									// can get this during socket shutdown
								
								return( 0 );
								
							}else{
								
								int res = connection.getReceivePendingSize();
									
									// we lie here if we have a fair bit queued as this allows
									// us to control the receive window
								
								if ( res > 512*1024 ){
									
										// forces us to advertize a window of 0 bytes
										// to prevent peer from sending us mroe data until
										// we've managed to flush this to disk
									
									res = Integer.MAX_VALUE;
								}
								
								return( res );
							}
						}
						
						public void
						setState(
							long		utp_socket,
							int			state )
						{
							UTPConnection connection;
							
							synchronized( UTPConnectionManager.this ){
								
								connection = socket_connection_map.get( utp_socket );
							}
							
							if ( connection == null ){
								
									// can get this during socket shutdown
								
							}else{
																
								if ( state == STATE_CONNECT ){
									
									connection.setConnected();
								}
								
								if ( state == STATE_CONNECT || state == STATE_WRITABLE ){
								
									connection.setCanWrite( true );
									
								}else if ( state == STATE_EOF ){
									
									connection.close( "EOF" );
									
								}else if ( state == STATE_DESTROYING ){
									
									connection.setUnusable();
									
									connection.close( "Connection destroyed" );
									
									if ( closing_connections.remove( connection )){
										
										removeConnection( connection );
									}
								}
							}
						}
						
						public void
						error(
							long		utp_socket,
							int			error )
						{		
							UTPConnection connection;
							
							synchronized( UTPConnectionManager.this ){
								
								connection = socket_connection_map.get( utp_socket );
							}
							
							if ( connection == null ){
								
								// can get this during socket shutdown
								
							}else{
								
								connection.close( "Socket error: code=" + error );
							}
						}
						
						public void
						overhead(
							long		utp_socket,
							boolean		send,
							int			size,
							int			type )
						{
							// System.out.println( "overhead( " + send + "," + size + "," + type + " )" );
						}
					});
			
			if ( available ){
			
				hackHandler();
				
				selector = new UTPSelector( this );
				
				ProtocolEndpointUTP.register( this );
			}
		}finally{
			
			init_sem.releaseForever();
		}
	}
	
	public void
	deactivate()
	{
		// TODO:
	}
	
	public UTPConnection
	connect(
		final InetSocketAddress		target,
		final UTPTransportHelper	transport )
	
		throws IOException
	{
		if ( target.isUnresolved()){
			
			throw( new UnknownHostException( target.getHostName()));
		}
		
		final Object[] result = { null };
	
		final AESemaphore sem = new AESemaphore( "uTP:connect" );
		
		dispatcher.dispatch(
				new AERunnable()
				{
					public void
					runSupport()
				  	{
						try{
							long[] x = utp_provider.connect( target.getAddress().getHostAddress(), target.getPort());
						
							if ( x != null ){
						
								result[0] = addConnection( target, transport, x[0], x[1] );
							}else{
								
								result[0] = new IOException( "Connect failed" );
							}
						}catch( Throwable e ){
							
							e.printStackTrace();
							
							result[0] = new IOException( "Connect failed: " + Debug.getNestedExceptionMessage(e));
							
						}finally{
							
							sem.release();
						}
				  	}
				});
		
		if ( !sem.reserve( UTP_PROVIDER_TIMEOUT )){
			
			Debug.out( "Deadlock probably detected" );
			
			throw( new IOException( "Deadlock" ));
		}
		
		if ( result[0] instanceof UTPConnection ){
			
			return((UTPConnection)result[0]);
			
		}else{
			
			throw((IOException)result[0]);
		}
	}
	
	public boolean
	receive(
		InetSocketAddress		from,
		byte[]					data,
		int						length )
	{	
		if ( !available ){
			
			return( false );
		}
		
		InetAddress address = from.getAddress();
		
		if ( address instanceof Inet4Address ){
			
			if ( length >= 20 ){
				
				byte first_byte = data[0];

				// System.out.println( "UDP: " + ByteFormatter.encodeString( data, 0, length ) + " from " + from  + " - " + new String( data, 0, length ));

				if ( 	first_byte == 0x41 &&		// SYN + version 1 
						data[8] == 0 && data[9] == 0 && data[10] == 0 && data[11] == 0 &&	// time diff = 0 
						// data[16] == 0 && data[17] == 1 ){	// seq = 1
						data[18] == 0 && data[19] == 0 ){	// ack = 0
					
					/* 4102C5F60499238B00000000003800000001000000080000000000000000
						4102 CDF2 	// SYN, ver 1, ext 2, con id CDF2
						6A39693A	// usec
						00000000	// rep micro
						00380000	wnd = 3.5MB
						00010000	seq = 1, ack = 0
		
						00080000	ext len = 8, no more ext
						00000000
						0000
					*/

						// then modified to use random initial sequence number
					
					// 4102e5331fb2e61900000000003800003aee000000080000000000000000

									
					// System.out.println( "Looks like uTP incoming connection from " + from );
	
					return( doReceive( address.getHostAddress(), from.getPort(), data, length ));
											
				}else if ( (first_byte&0x0f)==0x01 ){
					
					/* 0100B5621AE099301AD4C472003800000002482213426974546F7272656E742070726F746F636F6C0000000000100005A
						0100		// (x+1) + ext type
						B562		// con id
						1AE09930	// usec
						1AD4C472	// rep micro
						00380000	// recv win bytes
						0002		// seq
						4822		// ack
						13426974546F7272656E742070726F746F636F6C0000000000100005A
					*/
					
					// 210063CB1EFC51C01BA91F010003200036B56BFD
					
					int type = (data[0]>>>4)&0x0f;
					
					if ( type >= 0 && type <= 4 ){
						
						int	con_id = ((data[2]<<8)&0xff00) | (data[3]&0x00ff);
					
						UTPConnection connection = null;
						
						synchronized( this ){
							
							List<UTPConnection> l = address_connection_map.get( address );
							
							if ( l != null ){
								
								for ( UTPConnection c:l ){
									
									if ( c.getConnectionID() == con_id ){
										
										connection = c;
										
										break;
									}
								}
							}
							
							/*
							if ( connection == null ){
								
								String existing = "";
								
								for ( Map.Entry<InetAddress, List<UTPConnection>> entry: address_connection_map.entrySet()){
									
									String str = entry.getKey() + "->";
									
									for (UTPConnection u: entry.getValue()){
										
										str += u.getConnectionID() + ",";
									}
									
									existing += str + " ";
								}
								
								System.out.println( "Connection not found for " + from + "/" + con_id + ": " + existing );
							}
							*/
						}
						
						if ( connection != null ){
							
							// System.out.println( "Looks like uTP incoming data from " + from );
								
							return( doReceive( address.getHostAddress(), from.getPort(), data, length ));
								
						}else{
							
							// System.out.println( "No match from " + from  + ": " + ByteFormatter.encodeString( data, 0, length ));
						}
					}
				}
			}
		}
		
		return( false );
	}
	
	private boolean
	doReceive(
		final String		from_address,
		final int			from_port,
		final byte[]		data,
		final int			length )
	{
		if ( !utp_provider.isValidPacket( data, length )){
			
			return( false );
		}
		
		synchronized( this ){
			
			if ( total_incoming_queued > MAX_INCOMING_QUEUED ){
				
				if ( total_incoming_queued_log_state == 0 ){
					
					Debug.out( "uTP pending packet queue too large, discarding..." );
					
					total_incoming_queued_log_state = 1;
				}
				
				return( true );
			}
			
			if ( total_incoming_queued_log_state == 1 ){
				
				if ( total_incoming_queued < MAX_INCOMING_QUEUED_LOG_OK ){

					Debug.out( "uTP pending packet queue emptied, processing..." );
				
					total_incoming_queued_log_state	= 0;
				}
			}
			
			total_incoming_queued += length;
		}
		
		dispatcher.dispatch(
			new AERunnable()
			{
				public void
				runSupport()
			  	{
					synchronized( UTPConnectionManager.this ){
						
						total_incoming_queued -= length;
					}
					
					//System.out.println( "recv " + from_address + ":" + from_port + " - " + ByteFormatter.encodeString( data, 0, length ));
					
					try{
						if ( !utp_provider.receive( from_address, from_port, data, length )){
							
							if ( Constants.IS_CVS_VERSION ){
							
								Debug.out( "Failed to process uTP packet: " + ByteFormatter.encodeString( data, 0, length ) + " from " + from_address);
							}
						}
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
			});
		
		return( true );
	}
	
	private void
	accept(
		final InetSocketAddress	remote_address,
		long					utp_socket,
		long					con_id )
	{		
		final UTPConnection	new_connection = addConnection( remote_address, null, utp_socket, con_id );
		
		final UTPTransportHelper	helper = new UTPTransportHelper( this, remote_address, new_connection );

		if ( !UTPNetworkManager.UTP_INCOMING_ENABLED ){
			
			helper.close( "Incoming uTP connections disabled" );
			
			return;
		}
		
		log( "Incoming connection from " + remote_address );

		try{
			new_connection.setTransport( helper );
			
			TransportCryptoManager.getSingleton().manageCrypto( 
				helper, 
				null, 
				true, 
				null,
				new TransportCryptoManager.HandshakeListener() 
				{
					public void 
					handshakeSuccess( 
						ProtocolDecoder	decoder,
						ByteBuffer		remaining_initial_data ) 
					{
						TransportHelperFilter	filter = decoder.getFilter();
						
						ConnectionEndpoint	co_ep = new ConnectionEndpoint( remote_address);
	
						ProtocolEndpointUTP	pe_utp = (ProtocolEndpointUTP)ProtocolEndpointFactory.createEndpoint( ProtocolEndpoint.PROTOCOL_UTP, co_ep, remote_address );
	
						UTPTransport transport = new UTPTransport( UTPConnectionManager.this, pe_utp, filter );
								
						helper.setTransport( transport );
						
						incoming_manager.addConnection( local_port, filter, transport );
						
						log( "Connection established to " + helper.getAddress());
	        		}
	
					public void 
					handshakeFailure( 
	            		Throwable failure_msg ) 
					{
						if (Logger.isEnabled()){
							Logger.log(new LogEvent(LOGID, "incoming crypto handshake failure: " + Debug.getNestedExceptionMessage( failure_msg )));
						}
	 
						log( "Failed to established connection to " + helper.getAddress() + ": " + Debug.getNestedExceptionMessage(failure_msg) );
						
						new_connection.close( "handshake failure: " + Debug.getNestedExceptionMessage(failure_msg));
					}
	            
					public void
					gotSecret(
						byte[]				session_secret )
					{
					}
					
					public int
					getMaximumPlainHeaderLength()
					{
						return( incoming_manager.getMaxMinMatchBufferSize());
					}
	    		
					public int
					matchPlainHeader(
						ByteBuffer			buffer )
					{
						Object[]	match_data = incoming_manager.checkForMatch( helper, local_port, buffer, true );

						if ( match_data == null ){

							return( TransportCryptoManager.HandshakeListener.MATCH_NONE );

						}else{

							IncomingConnectionManager.MatchListener match = (IncomingConnectionManager.MatchListener)match_data[0];

							if ( match.autoCryptoFallback()){

								return( TransportCryptoManager.HandshakeListener.MATCH_CRYPTO_AUTO_FALLBACK );

							}else{

								return( TransportCryptoManager.HandshakeListener.MATCH_CRYPTO_NO_AUTO_FALLBACK );

							}
						}
					}
	        	});
			
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
			
			helper.close( Debug.getNestedExceptionMessage(e));
		}
	}
	
	private UTPConnection
	addConnection(
		InetSocketAddress		remote_address,
		UTPTransportHelper		transport_helper,			// null for incoming
		long					utp_socket,
		long					con_id )
	{
		List<UTPConnection>	to_destroy = null;
		
		final UTPConnection 	new_connection = new UTPConnection( this, remote_address, transport_helper, utp_socket, con_id );
		  
		synchronized( this ){
		
			List<UTPConnection> l = address_connection_map.get( remote_address.getAddress());
			
			if ( l != null ){
				
				for ( UTPConnection c: l ){
					
					if ( c.getConnectionID() == con_id ){
						
						if ( to_destroy == null ){
							
							to_destroy = new ArrayList<UTPConnection>();
						}
						
						to_destroy.add( c );
						
						l.remove( c );
						
						connections.remove( c );
						
						break;
					}
				}
			}else{
				
				l = new ArrayList<UTPConnection>();
				
				address_connection_map.put( remote_address.getAddress(), l );
			}
			
			l.add( new_connection );
			
			connections.add( new_connection );
			
			UTPConnection existing = socket_connection_map.put( utp_socket, new_connection );
			
			// System.out.println( "Add connection " + remote_address + ": total=" + connections.size() + "/" + address_connection_map.size() + "/" + socket_connection_map.size());

			if ( existing != null ){
				
				Debug.out( "Existing socket found at same address!!!!" );
				
				if ( to_destroy == null ){
					
					to_destroy = new ArrayList<UTPConnection>();
				}
				
				to_destroy.add( existing );
			}
		}
		
		if ( to_destroy != null ){
			
			for ( UTPConnection c: to_destroy ){
			
				c.close( "Connection replaced" );
			}
		}
		
		AESemaphore sem = poll_waiter;
		
		if ( sem != null ){
			
			poll_waiter = null;
			
			sem.release();
		}
		
		return( new_connection );
	}
	
	private void
	removeConnection(
		UTPConnection		c )
	{		
		synchronized( this ){
			
			connections.remove( c );
	
			List<UTPConnection> l = address_connection_map.get( c.getRemoteAddress().getAddress());
			
			if ( l != null ){
				
				l.remove( c );
				
				if ( l.size() == 0 ){
					
					address_connection_map.remove( c.getRemoteAddress().getAddress());
				}
			}
			
			UTPConnection existing = socket_connection_map.get( c.getSocket());
			
			if ( existing == c ){
				
				socket_connection_map.remove( c.getSocket());
			}
			
			// System.out.println( "Remove connection: " + c.getRemoteAddress() + ": total=" + connections.size() + "/" + address_connection_map.size() + "/" + socket_connection_map.size());
		}
	}
	
	protected UTPSelector
	getSelector()
	{
		return( selector );
	}
	
	protected int
	poll(
		AESemaphore		wait_sem,
		long			now )
	{
		if ( hack_worked && now - last_hack_attempt > 60*1000 ){
			
			last_hack_attempt = now;
			
			hackHandler();
		}
		
		dispatcher.dispatch(
			new AERunnable()
			{
				public void
				runSupport()
				{
					utp_provider.checkTimeouts();
					
					if ( closing_connections.size() > 0 ){
						
						long	now = SystemTime.getMonotonousTime();
						
						Iterator<UTPConnection> it = closing_connections.iterator();
						
						while( it.hasNext()){
						
							UTPConnection c = it.next();
							
							long 	close_time = c.getCloseTime();
							
							if ( close_time > 0 ){
								
								if ( now - close_time > CLOSING_TIMOUT ){
									
									it.remove();
									
									removeConnection( c );
									
									log( "Removing " + c.getString() + " due to close timeout" );
								}
							}
							
						}
					}
				}
			});
		
		int result =  connections.size();
		
		if ( result == 0 ){
			
			poll_waiter = wait_sem;
		}
		
		return( result );
	}
			
	protected int
	write(
		final UTPConnection		c,
		final ByteBuffer[]		buffers,
		final int				start,
		final int				len )
	
		throws IOException
	{
		final AESemaphore sem = new AESemaphore( "uTP:write" );
		
		final Object[] result = {null};
		
		dispatcher.dispatch(
			new AERunnable()
			{
				public void
				runSupport()
				{
					boolean	log_error = true;

					try{						
						if ( c.isUnusable()){

							log_error = false;
							
							throw( new Exception( "Connection is closed" ));
							
						}else if ( !c.isConnected()){

							log_error = false;
							
							throw( new Exception( "Connection is closed" ));
								
						}else if ( !c.canWrite()){
							
							Debug.out( "Write operation on non-writable connection" );
							
							result[0] = 0;						
							
						}else{
							
							if ( utp_provider.getVersion() == 1 ){
								
								int	pre_total = 0;
								
								for (int i=start;i<start+len;i++){
									
									pre_total += buffers[i].remaining();
								}
								
								try{
									active_write			= c;
									active_write_buffers	= buffers;
									active_write_start		= start;
									active_write_len		= len;
									
									boolean still_writable = utp_provider.write( c.getSocket(), pre_total );
								
									c.setCanWrite( still_writable );
									
								}finally{
									
									active_write			= null;
									active_write_buffers	= null;
								}
								
								int	post_total = 0;
								
								for (int i=start;i<start+len;i++){
									
									post_total += buffers[i].remaining();
								}
								
								result[0] = pre_total - post_total;
								
							}else{
								
								int	pre_total = 0;
								
								for (int i=start;i<start+len;i++){
									
									pre_total += buffers[i].remaining();
								}
																	
								boolean still_writable = utp_provider.write( c.getSocket(), buffers, start, len );
								
								c.setCanWrite( still_writable );
								
								int	post_total = 0;
								
								for (int i=start;i<start+len;i++){
									
									post_total += buffers[i].remaining();
								}
								
								result[0] = pre_total - post_total;
							}
						}
					}catch( Throwable e ){
						
						if ( log_error ){
						
							Debug.out( e );
						}
						
						c.close( Debug.getNestedExceptionMessage(e));
						
						result[0] = new IOException( "Write failed: " + Debug.getNestedExceptionMessage(e));
						
					}finally{
					
						sem.release();
					}
				}
			});
		
		if ( !sem.reserve( UTP_PROVIDER_TIMEOUT )){
			
			Debug.out( "Deadlock probably detected" );
			
			throw( new IOException( "Deadlock" ));
		}
		
		if ( result[0] instanceof Integer ){
			
			return((Integer)result[0]);
		}
		
		throw((IOException)result[0]);
	}
	
	private AERunnable inputIdleDispatcher =
		new AERunnable()
		{
			public void
			runSupport()
			{
				try{
					utp_provider.incomingIdle();
						
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		};
		
	protected void
	inputIdle()
	{
		dispatcher.dispatch( inputIdleDispatcher );
	}
	
	protected void
	readBufferDrained(
		final UTPConnection		c )
	{
		dispatcher.dispatch(
			new AERunnable()
			{
				public void
				runSupport()
				{
					if ( !c.isUnusable()){
						
						try{
							utp_provider.receiveBufferDrained( c.getSocket());
							
						}catch( Throwable e ){
							
							Debug.out( e );
						}
					}
				}
			});
	}
	
	protected void
	close(
		final UTPConnection		c,
		final String			r )
	{
		dispatcher.dispatch(
			new AERunnable()
			{
				public void
				runSupport()
				{
					closeSupport( c, r );
				}
			});
	}
	
	private void
	closeSupport(
		UTPConnection	c,
		String			r )
	{		
		boolean	async_close = false;
		
		try{
			if ( !c.isUnusable()){
				
				log( "Closed connection to " + c.getRemoteAddress() + ": " + r + " (" + c.getState() + ")" );

				try{
					c.setUnusable();

					utp_provider.close( c.getSocket() );
					
						// wait for the destroying callback
					
					async_close = true;
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		}finally{
			
			if ( async_close ){

				synchronized( closing_connections ){
				
					closing_connections.add( c );
				}
			}else{
				
				synchronized( closing_connections ){
					
					if ( closing_connections.contains( c )){
						
						return;
					}
				}
				
				removeConnection( c );
			}
		}
	}
	
	public void
	preferUTP(
		boolean		b )
	{
		prefer_utp = b;
	}
	
	protected boolean
	preferUTP()
	{
		return( prefer_utp );
	}
	
	public void
	setReceiveBufferSize(
		int		size )
	{
		utp_provider.setOption( UTPProvider.OPT_RECEIVE_BUFFER, size==0?DEFAULT_RECV_BUFFER_KB:size );
	}
	
	public void
	setSendBufferSize(
		int		size )
	{
		utp_provider.setOption( UTPProvider.OPT_SEND_BUFFER, size==0?DEFAULT_SEND_BUFFER_KB:size );
	}
	
	protected void
	log(
		String		str )
	{
		plugin.log( str );
	}
	
	
	private void
	hackHandler()
	{
		try{
			Class cla = packet_handler.getClass();
			
			Field f_socket = cla.getDeclaredField( "socket" );
			
			f_socket.setAccessible( true );
			
			Object dg_sock = f_socket.get( packet_handler );
			
			if ( last_hack == dg_sock ){
				
				return;
			}
			
			last_hack = dg_sock;
			
			Field f_impl = dg_sock.getClass().getDeclaredField( "impl" );
				
			f_impl.setAccessible( true );

			Object dg_impl = f_impl.get( dg_sock );
			
			Class dg_class = dg_impl.getClass();

			int	hacked = 0;
			
			while( dg_class != null ){
				
				String[]	field_names = { "fd", "fd1", "fd2" };
				
				for ( String field_name: field_names ){
					
					try{
						Field f_fd = dg_class.getDeclaredField( field_name );
						
						f_fd.setAccessible( true );
						
						Object fd = f_fd.get( dg_impl );
						
						if ( fd != null ){
							
							Field f_fd_fd = fd.getClass().getDeclaredField( "fd" );
				
							f_fd_fd.setAccessible( true );
							
							Object fd_fd = f_fd_fd.get( fd );
										
							utp_provider.setSocketOptions(((Number)fd_fd).longValue());
							
							hacked++;
						}
					}catch( Throwable e ){	
					}
				}
				
				dg_class = dg_class.getSuperclass();
			}
			
			hack_worked = hacked > 0;
			
			log( "Set options on " + hacked + " socket(s)" );
			
		}catch( Throwable e ){
			
			hack_worked = false;
			
			log( "Failed to set socket options: " + Debug.getNestedExceptionMessage(e));
		}
	}
}
