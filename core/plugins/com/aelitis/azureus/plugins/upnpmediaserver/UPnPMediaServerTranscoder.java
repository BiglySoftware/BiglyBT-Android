/*
 * Created on Jan 22, 2009
 * Created by Paul Gardner
 * 
 * Copyright 2009 Vuze, Inc.  All rights reserved.
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



package com.aelitis.azureus.plugins.upnpmediaserver;

public class
UPnPMediaServerTranscoder 
{
	/*
	public static final String NL = "\r\n";
	
	private UPnPMediaServer		plugin;
	private IPCInterface		ipc;
	
	private Object				transcode;
	
	private ServerSocket		transcoder_server_socket;
	private ServerSocket		stream_server_socket;
	
	private Socket				current_transcoder;
	private Socket				current_client;
	
	private LinkedList<byte[]>	data_queue 		= new LinkedList<byte[]>();
	private AESemaphore			data_queue_sem	= new AESemaphore( "UPnPMSTranscoder:dq" );
	
	private volatile boolean	destroyed;
	
	protected
	UPnPMediaServerTranscoder(
		UPnPMediaServer		_plugin,
		URL					_target,
		String				_profile )
	
		throws IOException
	{
		plugin		= _plugin;
		
		PluginInterface pif = plugin.getPluginInterface().getPluginManager().getPluginInterfaceByID( "vuzexcode" );
		
		if ( pif == null ){
			
			String str = "Vuze transcoder plugin not found";
			
			plugin.log( str );
			
			throw( new IOException( str ));
		}
		
		ipc = pif.getIPC();
		
		transcoder_server_socket = new ServerSocket( 0, 50, InetAddress.getByName( plugin.getLocalIP() ));
		
		new AEThread2( "UPnPMSTranscoder", true )
		{
			public void
			run()
			{
				while( !destroyed ){
					
					try{
						final Socket	socket = transcoder_server_socket.accept();
						
						new AEThread2( "UPnPMSTranscoder:t", true )
						{
							public void
							run()
							{
								handleTranscoder( socket );
							}
						}.start();
						
					}catch( Throwable e ){
						
						if ( !destroyed ){
						
							plugin.log( "Transcode receiver failed", e );
						
							destroy();
						}
						
						break;
					}
				}
			}
		}.start();
			
		stream_server_socket = new ServerSocket( 0, 50 );
		
		new AEThread2( "UPnPMSTranscoderStreamer", true )
		{
			public void
			run()
			{
				while( !destroyed ){
					
					try{
						final Socket	socket = stream_server_socket.accept();
						
						new AEThread2( "UPnPMSTranscoder:c", true )
						{
							public void
							run()
							{
								handleClient( socket );
							}
						}.start();
						
					}catch( Throwable e ){
						
						if ( !destroyed ){
						
							plugin.log( "Transcode stream receiver failed", e );
						
							destroy();
						}
						
						break;
					}
				}
			}
		}.start();
				
		try{
			transcode = 
				ipc.invoke(
					"transcodeToTCP",
					new Object[]{ 
						_target,
						_profile,
						transcoder_server_socket.getLocalPort()
					});
			
		}catch( IPCException e ){
			
			plugin.log( "Failed to initiate transcode", e );
			
			throw( new IOException( "Failed to initiate transcode: " + Debug.getNestedExceptionMessage(e)));
		}
	}
	
	protected void
	handleTranscoder(
		Socket		socket )
	{
		synchronized( this ){
			
			if ( destroyed ){
				
				try{
					socket.close();
					
				}catch( Throwable e ){				
				}
				
				return;
			}
			
			if ( current_transcoder != null ){
				
				log( "Multiple transcoder streams not supported" );
				
				destroy();
			}
			
			current_transcoder = socket;
		}

		log( "Transcode: transcoder connected - " + socket.getInetAddress());

		try{
			InputStream	is	= socket.getInputStream();
		
			while( !destroyed ){
				
				byte[]	buffer = new byte[64*1024];
				
				int	len =  is.read( buffer );
				
				if ( len <= 0 ){
					
					break;
				}
				
				if ( len < buffer.length ){
					
					byte[]	copy = new byte[len];
					
					System.arraycopy( buffer, 0, copy, 0, len );
					
					buffer = copy;
				}
				
				synchronized( data_queue ){
					
					data_queue.add( buffer );
				}
				
				data_queue_sem.release();
			}
		}catch( Throwable e ){
			
			if ( !destroyed ){
				
				log( "Transcoder stream read failed", e );
			}
		}finally{
			
			try{
				socket.close();
				
			}catch( Throwable e ){
			}
		}
		
		log( "Transcode: transcoder complete" );

	}
	
	protected void
	handleClient(
		Socket		socket	)
	{
			// todo: limit incoming connect addresses?
		
		synchronized( this ){
			
			if ( destroyed ){
				
				try{
					socket.close();
					
				}catch( Throwable e ){				
				}
				
				return;
			}
			
			if ( current_client != null ){
				
				log( "Multiple client streams not supported" );
				
				destroy();
			}
			
			current_client = socket;
		}
		
		log( "Transcode: client connected - " + socket.getInetAddress());
		
		try{
			OutputStream	os = socket.getOutputStream();
			
			write( os, "HTTP/1.1 200 OK" + NL );
			write( os, "Content-Type: video/x-flv" + NL );
			write( os, NL );
			
			os.flush();
			
			while( !destroyed ){
			
				if ( !data_queue_sem.reserve(1000)){
					
					if ( destroyed || socket.isClosed() ){
						
						break;
					}
				}else{
					
					byte[]	buffer;
					
					synchronized( data_queue ){

						buffer = data_queue.removeFirst();
					}
					
					os.write( buffer );
				}
			}
		}catch( Throwable e ){
			
			if ( !destroyed ){
				
				log( "Client stream write failed", e );
			}
		}finally{
			
			try{
				socket.close();
				
			}catch( Throwable e ){
			}
		}
		
			// if client's gone kill the process
		
		destroy();
		
		log( "Transcode: client complete" );
	}
	
	protected void
	write(
		OutputStream		os,
		String				str )
	
		throws IOException
	{
		os.write( str.getBytes());
	}
	
	protected String
	getStreamURI(
		String		host )
	{
		String stream_uri = "http://" + host + ":" + stream_server_socket.getLocalPort() + "/";

		return( stream_uri );
	}
	
	protected void
	destroy()
	{
		synchronized( this ){
		
			if ( destroyed ){
				
				return;
			}
			
			destroyed = true;
		}
		
		try{
			ipc.invoke(
				"cancelTranscode",
				new Object[]{ transcode });
			
		}catch( Throwable e ){
		
			plugin.log( "Failed to destroy transcode", e );
		}
		
		try{
			transcoder_server_socket.close();
			
		}catch( Throwable e ){
			
		}
		
		try{
			stream_server_socket.close();
			
		}catch( Throwable e ){
			
		}
		
		try{
			current_transcoder.close();
			
		}catch( Throwable e ){
			
		}
		
		try{
			current_client.close();
			
		}catch( Throwable e ){
			
		}
	}
	
	protected void
	log(
		String		str )
	{
		plugin.log( str );
	}
	
	protected void
	log(
		String		str,
		Throwable	e )
	{
		plugin.log( str, e );
	}
	*/
}
