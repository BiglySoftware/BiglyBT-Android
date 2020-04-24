/*
 * Created on 18-Apr-2006
 * Created by Paul Gardner
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.azureus.plugins.upnpmediaserver;

import java.util.*;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Average;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.utils.PooledByteBuffer;

import com.biglybt.core.networkmanager.VirtualChannelSelector;
import com.biglybt.core.networkmanager.VirtualChannelSelector.VirtualSelectorListener;

public class 
UPnPMediaChannel 
{
	private static final int	READ_TIMEOUT	= 30*1000;

	private final static VirtualChannelSelector read_selector	= new VirtualChannelSelector( "UPnPMediaServer", VirtualChannelSelector.OP_READ, false );
	private final static VirtualChannelSelector write_selector 	= new VirtualChannelSelector( "UPnPMediaServer", VirtualChannelSelector.OP_WRITE, false );

	private static final int	BUFFER_LIMIT	= 3;
	private static final int	BLOCK_SIZE		= 128*1024;
	
	private static final List<pendingWriteSelect>	pending_write_resumes	= new ArrayList<pendingWriteSelect>();
	
	static{
		write_selector.setRandomiseKeys( true );
		
		new AEThread2( "UPnPMediaChannel:writeSelector", true )
			{
				@Override
				public void
				run()
				{
					Thread.currentThread().setPriority( Thread.MAX_PRIORITY );
					
					selectLoop( write_selector, true );
				}
			}.start();
			
		new AEThread2( "UPnPMediaChannel:readSelector", true )
			{
				@Override
				public void
				run()
				{
					Thread.currentThread().setPriority( Thread.MAX_PRIORITY );

					selectLoop( read_selector, false );
				}
			}.start();
	}
	
	static volatile boolean	idle = true;
	
	protected static void
	setIdle(
		boolean	i )
	{
		if ( idle != i ){
					
			idle	= i;
		}
	}
	
	static void
	selectLoop(
		VirtualChannelSelector	selector,
		boolean					is_write )
	{
		while( true ){
			
			if ( is_write ){
				
				long	now = SystemTime.getMonotonousTime();
				
				synchronized( pending_write_resumes ){
					
					Iterator<pendingWriteSelect> it = pending_write_resumes.iterator();
					
					while( it.hasNext()){
						
						pendingWriteSelect pws = it.next();
						
						if ( now >= pws.when ){
							
							it.remove();
							
							write_selector.resumeSelects( pws.channel );
						}
					}
				}
			}
			
			selector.select( idle?250:25 );
		}
	}
	
	private static Average 	data_write_speed = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
	private static long		data_write_total;
	
	public static long
	getAverageUpSpeed()
	{
		return( data_write_speed.getAverage());
	}
	
	public static long
	getTotalUp()
	{
		return( data_write_total );
	}
	
	private final Object	read_lock	= new Object();
	private final Object	write_lock	= new Object();
	
	private SocketChannel	channel;
		
	private List<Byte>	pending_read_bytes = new ArrayList<Byte>();
	
	private List<writeBuffer>	write_buffers = new ArrayList<writeBuffer>();
	private IOException			write_error;
	
	private channelListener		listener;
	
	private long				data_written;
	
	protected
	UPnPMediaChannel(
		Socket				socket )
	
		throws IOException
	{
		channel	= socket.getChannel();
		
		channel.configureBlocking( false );
		
		try{			
			socket.setSendBufferSize( 256*1024 );
			
		}catch ( SocketException e ){
		}
	}
	
	protected void
	setListener(
		channelListener		_listener )
	{
		listener	= _listener;
	}
	
	public void
	read(
		byte[]		buffer )
	
		throws IOException
	{
		read( ByteBuffer.wrap( buffer ));
	}
	
	public void
	read(
		final ByteBuffer	buffer )
	
		throws IOException
	{
		try{
			synchronized( read_lock ){
				
				Iterator<Byte>	it = pending_read_bytes.iterator();
				
				while( it.hasNext() && buffer.hasRemaining()){
				
					buffer.put(it.next().byteValue());
					
					it.remove();
				}
				
				if ( !buffer.hasRemaining()){
					
					return;
				}
				
				if ( channel.read( buffer ) < 0 ){
					
					throw( new IOException( "End of stream" ));
				}
				
				if ( buffer.hasRemaining()){
			
					final IOException[]	error = { null };
					
					final AESemaphore	sem = new AESemaphore( "UPnPMediaChannel::read" );
					
					read_selector.register( 
							channel, 
							new VirtualSelectorListener()
							{
								private int	consec_zlrs;
								
								@Override
								public boolean
								selectSuccess(
									VirtualChannelSelector 	selector, 
									SocketChannel 			sc,
									Object 					attachment)
								{
									try{
										int	len = channel.read( buffer );
										
										if ( !buffer.hasRemaining()){
											
											read_selector.cancel( channel );
											
											sem.release();
										}
										
										if ( len < 0 ){
											
											//read_selector.cancel( channel );
											
											//return( true );
											throw( new IOException( "End of stream" ));
										}
										
										if ( len == 0 ){
											
											if ( write_error != null ){
												
												throw( write_error );
											}
											
											consec_zlrs++;
									
											if ( consec_zlrs > 10 ){
												
												throw( new IOException( "Too many consecutive zero length reads" ));
											}
										}else{
											
											consec_zlrs = 0;
										}
										
										return( len > 0 );
										
									}catch( IOException e ){
										
										error[0] = e;
										
										read_selector.cancel( channel );

										sem.release();
										
										return( false );
									}
								}
			
								@Override
								public void
								selectFailure(
									VirtualChannelSelector 	selector, 
									SocketChannel 			sc, 
									Object 					attachment, 
									Throwable 				msg )
								{
									error[0] = msg instanceof IOException?(IOException)msg:new IOException(msg.getMessage());

									read_selector.cancel( channel );
									
									sem.release();
								}
							},
							null );
						
					
					if ( !sem.reserve( READ_TIMEOUT )){
					
						throw( new SocketTimeoutException( "Read timeout" ));
					}
					
					if ( error[0] != null ){
						
						throw( error[0] );
					}
				}
			}
		}catch( IOException e ){
	
			close();
			
			throw( e );
		}
	}
	
	public void
	write(
		long		offset,
		byte[]		buffer )
	
		throws IOException
	{
		writeSupport( new writeBuffer( offset, ByteBuffer.wrap( buffer )));
	}
	
	public void
	write(
		long					offset,
		PooledByteBuffer		buffer )
	
		throws IOException
	{
		writeSupport( new writeBuffer( offset, buffer ));
	}
	
	protected void
	writeSupport(
		writeBuffer	buffer_thing )
	
		throws IOException
	{
		try{
			boolean add_to_pending = false;
			
			synchronized( write_lock ){
				
				int	permitted = BLOCK_SIZE;

				if ( write_buffers.size() == 0 ){
					
					ByteBuffer buffer;
					
					Object	buffer_object = buffer_thing.buffer;
					
					if ( buffer_object instanceof ByteBuffer ){
						
						buffer = (ByteBuffer)buffer_object;
						
					}else{
						
						buffer = ((PooledByteBuffer)buffer_object).toByteBuffer();
						
					}
										
					if ( listener != null ){
						
						permitted = Math.min( permitted, listener.getAvailableBytes());
					}
					
					if ( permitted > 0 ){
						
						int	limit = buffer.limit();
						
						if ( buffer.remaining() > permitted ){
							
							buffer.limit( buffer.position() + permitted );
						}
						
						try{
							int	pos = buffer.position();
							
							int len = channel.write( buffer );
							
							if ( len > 0 ){
								
								if (  listener != null && buffer_thing.offset >= 0 ){
								
									listener.wrote( buffer_thing.offset + pos, len );
								}
								
								data_written += len;
								
								data_write_total += len;
								
								data_write_speed.addValue( len );
							}
						}finally{
							
							buffer.limit( limit );
						}
						
						if ( !buffer.hasRemaining()){
							
							if ( buffer_object instanceof PooledByteBuffer ){
								
								((PooledByteBuffer)buffer_object).returnToPool();
							}
							
							return;
						}
					}
				}
				
				write_buffers.add( buffer_thing );
				
				// System.out.println( "buffers = " + write_buffers.size());
				
				if ( write_error != null ){
					
					throw( write_error );
				}
				
				if ( write_buffers.size() == 1 ){
												
					write_selector.register( 
						channel, 
						new VirtualSelectorListener()
						{
							private int consec_zlws;
							
							@Override
							public boolean
							selectSuccess(
								VirtualChannelSelector 	selector, 
								SocketChannel 			sc,
								Object 					attachment)
							{
								long	total_written	= 0;
							
								while( true ){
							
									int	permitted = BLOCK_SIZE;
									
									if ( listener != null ){
										
										permitted = Math.min( permitted, listener.getAvailableBytes());
									}
									
									if ( permitted <= 0 ){
									
										write_selector.pauseSelects( channel );
										
										synchronized( pending_write_resumes ){
										
											pending_write_resumes.add( new pendingWriteSelect( channel ));
										}
										
										return( true);
									}
									
									writeBuffer		current_buffer;
									ByteBuffer		buffer;
									
									synchronized( write_lock ){
										
										if ( write_buffers.size() == 0 ){
											
											write_selector.cancel( channel );
											
											return( false );
										}
										
										current_buffer = write_buffers.get(0);
										
										Object o = current_buffer.buffer;
										
										if ( o instanceof ByteBuffer ){
											
											buffer = (ByteBuffer)o;
											
										}else{
											
											buffer = ((PooledByteBuffer)o).toByteBuffer();
											
										}
									}
									
									try{
										int	limit = buffer.limit();
										
										if ( buffer.remaining() > permitted ){
											
											buffer.limit( buffer.position() + permitted );
										}
										
										try{											
		
											int	pos = buffer.position();
											
											int len = channel.write( buffer );
											
											if ( len > 0 ){
												
												if ( listener != null && current_buffer.offset >= 0 ){
												
													listener.wrote( current_buffer.offset + pos, len );
												}
												
												data_written += len;
												
												data_write_total += len;
												
												data_write_speed.addValue( len );
												
												total_written += len;
											}
										}finally{
											
											buffer.limit( limit );
										}
											
										if ( buffer.hasRemaining()){
											
											break;
										}
										
										synchronized( write_lock ){

											write_buffers.remove( current_buffer);
											
											if ( current_buffer.buffer instanceof PooledByteBuffer ){
												
												((PooledByteBuffer)current_buffer.buffer).returnToPool();
											}
											
											if ( write_buffers.size() == 0 ){
												
												write_selector.cancel( channel );
												
												break;
												
											}else if( write_buffers.size() == BUFFER_LIMIT - 1 ){
												
												write_lock.notify();
											}
										}
									}catch( IOException e ){
										
										write_selector.cancel( channel );

										synchronized( write_lock ){
											
											write_error = e;

											write_lock.notifyAll();
										}
										
										return( false );
									}
								}
								
								if ( total_written <= 0 ){
									
									consec_zlws++;
									
								}else{
									
									consec_zlws = 0;
								}
								
								return( total_written > 0 );
							}
		
							@Override
							public void
							selectFailure(
								VirtualChannelSelector 	selector, 
								SocketChannel 			sc, 
								Object 					attachment, 
								Throwable 				msg )
							{
								write_selector.cancel( channel );

								synchronized( write_lock ){
									
									write_error = msg instanceof IOException?(IOException)msg:new IOException(msg.getMessage());

									write_lock.notifyAll();
								}
							}
						},
						null );
						
					if ( permitted == 0 ){
						
						add_to_pending = true;
					}
					
				}else if ( write_buffers.size() == BUFFER_LIMIT ){
										
					try{
						write_lock.wait();
	
						if ( write_error != null ){
							
							throw( write_error );
						}
	
					}catch( InterruptedException e ){
						
						throw( new IOException( "interrupted" ));
					}
				}
			}
			
			if ( add_to_pending ){
				
				write_selector.pauseSelects( channel );
				
				synchronized( pending_write_resumes ){
				
					pending_write_resumes.add( new pendingWriteSelect( channel ));
				}
			}
		}catch( IOException e ){
	
			close();
			
			throw( e );
		}
	}
	
	public void
	flush()
	
		throws IOException
	{
		try{
	
			while( true ){
			
				synchronized( write_lock ){
					
					if ( write_error != null ){
						
						throw( write_error );
					}
					
					if ( write_buffers.size() == 0 ){
						
						break;
					}
				}
				
				try{
					Thread.sleep(10);
					
				}catch( Throwable e ){
					
					throw( new IOException( "interrupted" ));
				}
			}
		}catch( IOException e ){
			
			close();
			
			throw( e );
		}
	}
	
	protected long
	getChannelUp()
	{
		return( data_written );
	}
	
	public boolean
	isClosed()
	{
		try{
			synchronized( read_lock ){
				
					// bah, the only way I can find to pick up a channel that has died it to try and
					// read a byte from it (zero byte ops do nothing, as do selects)
					// of course we then have to deal with the fact that we might actually read a byte...
				
				byte[]	buffer = new byte[1];
				
				int len = channel.read( ByteBuffer.wrap( buffer ));
				
				if ( len == 1 ){
			
					pending_read_bytes.add( new Byte(buffer[0]));
					
				}else if ( len < 0 ){
					
					throw( new IOException( "End of stream" ));
				}
				
				return( false );
			}
		}catch( IOException e ){
	
			write_error = e;
			
			close();
			
			return( true );
		}
	}
	
	public void
	close()
	{
		try{
			channel.close();
			
		}catch( Throwable e ){
			
			Debug.printStackTrace(e);
		}
		
		synchronized( write_lock ){

			if ( write_error == null ){
				
				write_error = new IOException( "channel closed" );
			}
			
			write_lock.notifyAll();
			
			read_selector.cancel( channel );
			write_selector.cancel( channel );
			
			Iterator<writeBuffer>	it = write_buffers.iterator();
			
			while( it.hasNext()){
				
				writeBuffer	buffer = it.next();
		
				Object	 o = buffer.buffer;
				
				if ( o instanceof PooledByteBuffer ){
					
					((PooledByteBuffer)o).returnToPool();
				}
				
				it.remove();
			}
		}
	}
	
	protected static class
	pendingWriteSelect
	{
		private SocketChannel		channel;
		private long				when;
		
		protected
		pendingWriteSelect(
			SocketChannel		_channel )
		{
			channel		= _channel;
			when		= SystemTime.getMonotonousTime() + 25;
		}
	}
	
	protected static class
	writeBuffer
	{
		private long		offset;
		private Object		buffer;
		
		protected
		writeBuffer(
			long				_offset,
			PooledByteBuffer	_buffer )
		{
			offset	= _offset;
			buffer	= _buffer;
		}
		
		protected
		writeBuffer(
			long				_offset,
			ByteBuffer			_buffer )
		{
			offset	= _offset;
			buffer	= _buffer;
		}
	}
	
	protected interface
	channelListener
	{
		public void
		wrote(
			long		offset,
			int			bytes );
		
		public int
		getAvailableBytes();
	}
}
