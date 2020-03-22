/*
 * Created on 29-Mar-2006
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

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.io.*;
import java.util.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Average;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.ThreadPool;
import com.biglybt.core.util.ThreadPoolTask;
import com.biglybt.core.util.TimeFormatter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.disk.DiskManagerChannel;
import com.biglybt.pif.disk.DiskManagerEvent;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.disk.DiskManagerListener;
import com.biglybt.pif.disk.DiskManagerRequest;
import com.biglybt.pif.utils.PooledByteBuffer;

import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.networkmanager.admin.NetworkAdminNetworkInterface;
import com.biglybt.core.networkmanager.admin.NetworkAdminNetworkInterfaceAddress;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.core.util.average.MovingImmediateAverage;
import org.gudy.bouncycastle.util.encoders.Base64;


public class 
UPnPMediaServerContentServer 
{
	private static final String	NL			= "\r\n";

	private static final int	MAX_CONNECTIONS_PER_ENDPOINT	= 16;
	
	private UPnPMediaServer	plugin;
	private int				port;
	private InetAddress		first_bind_ip;
	
	private ThreadPool		thread_pool;
	private PluginInterface	plugin_interface;
	
	private List<UPnPMediaChannel>		close_queue			= new ArrayList<UPnPMediaChannel>();
	
	private List<processor>				active_processors	= new ArrayList<processor>();
	
	private Map<Integer, processor>		stream_map			= new HashMap<Integer, processor>();
	
	private List<ServerSocket>	server_sockets = new ArrayList<ServerSocket>();
	
	private volatile boolean	destroyed;
	
	private static final int					MAX_OMS 		= 8;
	private static final int					OM_MAX_WRITES	= 128;
	private static final int					OM_TIMEOUT		= 10*1000;
	
	private static final int					OM_SLEEP_PERIOD	= 25;
	private static final int					OM_STATS_PERIOD	= 1000;
	private static final int					OM_STATS_TICKS	= OM_STATS_PERIOD/OM_SLEEP_PERIOD;

	private Map<String,overWriteMonitor>		overwrite_monitors = new HashMap<String, overWriteMonitor>();
	private AEThread2							om_update_thread;
	
	protected
	UPnPMediaServerContentServer(
		UPnPMediaServer		_plugin )
	
		throws IOException
	{
		plugin	= _plugin;
		
		plugin_interface = plugin.getPluginInterface();
		
		thread_pool = new ThreadPool( "UPnPMediaServer:processor", 64 );
		
		Random	random = new Random();
		
		port	= plugin.getContentPort();
		
		ServerSocketChannel	ssc	= null;
		
		if ( port == 0 ){
			
			port = random.nextInt(20000) + 40000;
		}
		
		InetAddress[] bind_ips = plugin.getApplyBindIPs()?NetworkAdmin.getSingleton().getMultiHomedServiceBindAddresses(true):new InetAddress[0]; 
		
		if (	bind_ips.length == 0 || 
				bind_ips.length == 1 && bind_ips[0].isAnyLocalAddress()){
			
				// nothing explicit - let's see if wildcard works
				// null = dual ipv4+v6 if present
			
			if ( canBind( null )){

				bind_ips = new Inet4Address[]{ null };
				
			}else{
				
				bind_ips =  getBindableAddresses();
					
				if ( bind_ips.length == 0 ){
					
					bind_ips = new InetAddress[]{ InetAddress.getByName( "127.0.0.1" )};
				}
			}
		}
		
		boolean	warned = false;
		
		InetAddress	selected_bind_ip = null;
		
outer:
		for ( int i=0;i<1024;i++ ){
			
			try{
				IOException fail = null;
				
				for ( int j=0;j<bind_ips.length;j++){
				
					ssc = ServerSocketChannel.open();

					try{
						bind( ssc, bind_ips[j], port );

						selected_bind_ip = bind_ips[j];
					
						break outer;
						
					}catch( IOException e ){
					
						fail = e;
						
						ssc.close();
						
						ssc = null;
					}
				}
				
				if ( fail != null ){
					
					throw( fail );
				}
			}catch( Throwable e ){
				
				if ( ssc != null ){
					
					try{
						ssc.close();
						
					}catch( Throwable f ){
						
						Debug.printStackTrace(e);
					}
					
					ssc = null;
				}
				
				if ( plugin.isUserSelectedContentPort()){
					
					if ( !warned ){
						
						plugin.logAlert( "Unable to bind to user selected stream port " + port + "; reverting to random port");
						
						warned = true;
					}
				}
				
				port = random.nextInt(20000) + 40000;
			}
		}
			
		if ( ssc == null ){
			
			IOException fail = null;
			
			for ( int i=0;i<bind_ips.length;i++){
				
				ssc = ServerSocketChannel.open();

				try{
			
					bind( ssc, bind_ips[i], 0 );
		
					selected_bind_ip = bind_ips[i];
					
					port = ssc.socket().getLocalPort();
					
					fail = null;
					
					break;
					
				}catch( IOException e ){
					
					fail = e;
					
					ssc.close();
					
					ssc = null;
				}
			}
			
			if ( fail != null ){
				
				throw( fail );
			}
		}
		
		first_bind_ip	= selected_bind_ip;
		
			// ok, we're bound to one - set up accepters and bind to any others as
			// required
		
		for (int i=0;i<bind_ips.length;i++){
			
			ServerSocket ss;
			
			if ( bind_ips[i] == selected_bind_ip ){
				
				ss = ssc.socket();
				
				plugin.setContentPort( port );

			}else{
				
				ServerSocketChannel ssc2 = null;
				
				try{
					ssc2 = ServerSocketChannel.open();
				
					bind( ssc2, bind_ips[i], port );
					
					ss = ssc2.socket();
					
				}catch( Throwable e ){
					
					if ( ssc2 != null ){
						
						try{
							ssc2.close();
							
						}catch( Throwable f ){					
						}
					}
					
					continue;
				}
			}
			
			ss.setReuseAddress(true);
					
			server_sockets.add( ss );

			final ServerSocket f_ss = ss;
						
			new AEThread2( "UPnPMediaServer:accepter", true )
				{
					@Override
					public void
					run()
					{
						int	processor_num = 0;
						
						try{
	
							
							long	successfull_accepts = 0;
							long	failed_accepts		= 0;
							
							while( !destroyed ){
								
								try{				
									Socket socket = f_ss.accept();
										
									successfull_accepts++;
									
									String	ip = socket.getInetAddress().getHostAddress();			
																			
									processor	proc = new processor( ip, socket, processor_num++ );
									
									thread_pool.run( proc );
										
								}catch( Throwable e ){
									
									if ( !destroyed ){
										
										if ( failed_accepts == 0 ){
											
											plugin.log( "Accept failed", e );
										}
										
										failed_accepts++;
										
										plugin.log( "listener failed on port " + getPort(), e ); 
										
										if ( failed_accepts > 100 && successfull_accepts == 0 ){
					
												// looks like its not going to work...
												// some kind of socket problem
					
											plugin.log( "    too many listen fails, giving up" );
									
											break;
										}
									}
								}
							}
						}catch( Throwable e ){
						}
					}
				}.start();
		}
		
		new AEThread2( "UPnPMediaServer:closer", true )
		{
			@Override
			public void
			run()
			{
				List	pending	= new ArrayList();
				
				while( !( destroyed && active_processors.size() == 0 )){
					
					try{
						Thread.sleep(10*1000);
					
					}catch( Throwable e ){
						
						e.printStackTrace();
						
						break;
					}
					
					tidyOMS();
					
					Iterator	it = pending.iterator();
					
					while( it.hasNext()){
					
						try{
							
							((UPnPMediaChannel)it.next()).close();
							
						}catch( Throwable e ){
							
							it.remove();
						}
					}
					
					synchronized( close_queue ){
						
						pending.addAll( close_queue );
						
						close_queue.clear();
					}
					
					synchronized( active_processors ){
						
						Map	conn_map = new HashMap();
						
						for (int i=0;i<active_processors.size();i++){
							
							processor	proc = (processor)active_processors.get(i);
															
							DiskManagerRequest	req = proc.getActiveRequest();
								
							if ( req != null ){
									
								UPnPMediaChannel	channel = proc.getChannel();
														
								if ( channel.isClosed()){

									// System.out.println( "Cancelling active request on closed socket" );
								
									req.cancel();
									
								}else{
									
									List	conns = (List)conn_map.get( proc.getIP());
									
									if ( conns == null ){
										
										conns = new ArrayList();
										
										conn_map.put( proc.getIP(), conns );
									}
									
									conns.add( proc );
								}
							}
						}
						
							// some devices don't close down connections properly and we end up with a
							// load of CLOSE_WAIT sockets. Limit the number of open sockets per end point
							// to put an upper limit on this
						
						it = conn_map.values().iterator();
						
						while( it.hasNext()){
						
							List	conns = (List)it.next();
							
							for (int i=0;i<conns.size()-MAX_CONNECTIONS_PER_ENDPOINT;i++){
								
								processor	proc = (processor)conns.get(i);
								
								DiskManagerRequest	req = proc.getActiveRequest();
								
								if ( req != null ){
		
									// System.out.println( "Cancelling active request - client has too many open connections" );
									
									req.cancel();
								}
							}
						}
					}
				}
			}
		}.start();
	}
	
	protected void
	bind(
		ServerSocketChannel	ssc,
		InetAddress			address,
		int					port )
	
		throws IOException
	{
		// System.out.println( "binding to " + (address==null?"null":address.getHostAddress()));
		
		if ( address == null ){
			
			ssc.socket().bind( new InetSocketAddress( port ), 1024 );
			
		}else{
			
			ssc.socket().bind( new InetSocketAddress( address, port ), 1024 );
		}
	}
	
	protected InetAddress[]
	getBindableAddresses()
	{
		List<InetAddress>	bindable = new ArrayList<InetAddress>();
		
		NetworkAdminNetworkInterface[] interfaces = NetworkAdmin.getSingleton().getInterfaces();
		
		for ( NetworkAdminNetworkInterface intf: interfaces ){
			
			NetworkAdminNetworkInterfaceAddress[] addresses = intf.getAddresses();
			
			for ( NetworkAdminNetworkInterfaceAddress address: addresses ){

				InetAddress a = address.getAddress();
				
				if ( canBind( a )){
					
					bindable.add( a );
				}
			}
		}
		
		return( bindable.toArray( new InetAddress[ bindable.size()]));
	}
	
	protected boolean
	canBind(
		InetAddress	bind_ip )
	{
		ServerSocketChannel ssc = null;
		
		try{
			ssc = ServerSocketChannel.open();
		
			ssc.socket().bind( bind_ip==null?new InetSocketAddress(0):new InetSocketAddress( bind_ip, 0), 1024 );
			
			return( true );
			
		}catch( Throwable e ){
			
			return( false );
			
		}finally{
			
			if ( ssc != null ){
	
				try{
					ssc.close();
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		}
	}
	
	protected void
	destroy()
	{
		destroyed	= true;
		
		for ( ServerSocket server_socket: server_sockets ){
		
			try{
				server_socket.close();
			
			}catch( Throwable e ){
			}
		}
	}
	
	protected streamInfo
	getStreamInfo(
		int		stream_id )
	{
		synchronized( active_processors ){
				
			return((processor)stream_map.get( new Integer( stream_id )));
		}
	}
	
	protected int
	getPort()
	{
		return( port );
	}
	
	protected InetAddress
	getBindIP()
	{
		return( first_bind_ip );
	}
	
	protected int
	getConnectionCount()
	{
		return( active_processors.size());
	}
	
	protected class
	processor
		extends ThreadPoolTask
		implements streamInfo
	{
		private String				ip;
		private Socket				socket;
		private UPnPMediaChannel	channel;
		
		private int			processor_num;
		
		private StringBuilder	write_buffer = new StringBuilder(512);
		
		private long		last_write_time;
		private long		last_write_offset;
		
		private long		last_blocked_offset;
		
		private int			stream_id	= -1;
		
		private boolean		action_is_download;
		
		private volatile DiskManagerRequest	active_request;
		private UPnPMediaRendererRemote remoteRenderer;
		
		protected
		processor(
			String					_ip,
			Socket					_socket,
			int						_processor_num )
		{			
			ip				= _ip;
			socket			= _socket;
			processor_num	= _processor_num;
			
			last_write_time	= plugin_interface.getUtilities().getCurrentSystemTime();

			// Note: We may want to refind renderer later, just in case the it isn't
			// registered yet
			remoteRenderer = plugin.findRendererByIP(ip);
		}

		protected String
		getIP()
		{
			return( ip );
		}
		
		protected long
		getLastWriteTime()
		{
			return( last_write_time );
		}
		
		protected long
		getLastWriteOffset()
		{
			return( last_write_offset );
		}
		
		@Override
		public long
		getPosition()
		{
			return( getLastWriteOffset());
		}
		
		@Override
		public long
		getAvailableBytes()
		{
			DiskManagerRequest request = active_request;
			
			if ( request == null ){
				
				return( -1 );
			}
			
			return( request.getAvailableBytes());
		}
		
		@Override
		public long
		getRemaining()
		{
			DiskManagerRequest request = active_request;
			
			if ( request == null ){
				
				return( -1 );
			}
			
			return( request.getRemaining());
		}
		
		protected int
		getStreamID()
		{
			return( stream_id );
		}
		
		protected UPnPMediaChannel
		getChannel()
		{
			return( channel );
		}
		
		protected DiskManagerRequest
		getActiveRequest()
		{
			return( active_request );
		}
		
		protected void
		log(
			String		str )
		{
			plugin.log( "[" + processor_num + "] " + str );
		}
		
		@Override
		public void
		runSupport()
		{
			boolean	close_now	= false;
		
			try{
		
				synchronized( active_processors ){
					
					if ( active_processors.size() == 0 ){
						
						UPnPMediaChannel.setIdle( false );
					}
					
					active_processors.add( this );
				}
			
				// System.out.println( "Processor " + processor_num  + " starts" );
				
				setTaskState( "entry" );
				
				channel	= new UPnPMediaChannel( socket );
				
				process();

				//System.out.println( "closing media server channel now" );
				// Mplayer on OSX needs the stream to be closed when complete otherwise we
				// get a hang at end of content
				
				Thread.sleep(100);
				
				close_now = true;
				
			}catch( Throwable e ){
		
				close_now	= true;
				
				if ( ! (e instanceof SocketTimeoutException )){
					
					e.printStackTrace();
				}
				
			}finally{
				
				synchronized( active_processors ){
					
					active_processors.remove( this );
					
					if ( active_processors.size() == 0 ){
						
						UPnPMediaChannel.setIdle( true );
					}
					
					if ( stream_id != -1 ){
						
						if ( getStreamInfo( stream_id ) == this ){
							
							stream_map.remove( new Integer( stream_id ));
						}
					}
				}
				
				// System.out.println( "Processor " + processor_num  + " ends" );
				
				if ( close_now ){
				
					try{
						channel.close();
					
					}catch( Throwable f ){
					}
				}else{
				
					synchronized( close_queue ){
						
						close_queue.add( channel );
					}
				}
			}
		}
		
		protected void
		process()
		
			throws IOException
		{
			try{
				int	loop_count = 0;
				
				boolean	close_connection = false;
				
				while( !close_connection ){
				
					String	command	= null;
					
					Map<String,String>		headers	= new HashMap<String,String>();
	
					try{
						while( true ){
			
							String	line = "";
						
							while( !line.endsWith( NL )){
								
								byte[]	buffer = new byte[1];
								
								channel.read( buffer );
								
								line += new String( buffer );
							}
						
							line = line.trim();
							
							if ( line.length() == 0 ){
								
								break;
							}
							
							if ( command == null ){
								
								command	= line;
								
							}else{
								
								int	pos = line.indexOf(':');
								
								if ( pos == -1 ){
									
									return;
								}
								
								String	lhs = line.substring(0,pos).trim().toLowerCase( MessageText.LOCALE_ENGLISH );
								String	rhs = line.substring(pos+1).trim();
								
								headers.put( lhs, rhs );
							}
						}
					}catch ( IOException e ){
					
							// failed to read header
						
						return;
					}
					
					if ( plugin.authContentPort( ip )){
						
						String	auth = headers.get( "authorization" );
						
						boolean	ok = false;
						
						if ( auth != null ){
							
							int	pos = auth.indexOf( ' ' );
							
							auth = auth.substring( pos+1 ).trim();
							
							String decoded = new String( Base64.decode(auth));

								// username:password
											
							int	cp = decoded.indexOf(':');
							
							String	user = decoded.substring(0,cp);
							String  pw	 = decoded.substring(cp+1);

							ok = plugin.doContentAuth( ip, user, pw );
						}
						
						if ( !ok ){
							
							writeb( "HTTP/1.1 401 BAD" + NL );
							writeb( "WWW-Authenticate: Basic realm=\"BiglyBT Media Server\"" + NL );						
							writeb( "Connection: close" + NL + NL );
							
							writeb( "Access Denied" + NL );
	
							writef();
							
							close_connection = true;
							
							continue;
						}
					}
										
					String	connection_header = headers.get( "connection" );
									
					if ( command.endsWith( "1.0" )){
						
							// default for 1.0 is close, keep alive if specified
						
						close_connection = connection_header == null || !connection_header.equalsIgnoreCase( "keep-alive" );
						
					}else{
						
							// default for 1.1 is keep alive unless explicitly close
						
						close_connection = connection_header != null && connection_header.equalsIgnoreCase( "close" );
					}
					
					// log( "command: " + command + ", headers=" + headers + ", close=" + close_connection );
	
					String	url;
					boolean	head	= false;
					
					if ( command.startsWith( "GET " )){
						
						url = command.substring( 4 );
	
					}else if ( command.startsWith( "HEAD " )){
						
						url = command.substring( 5 );
						
						head	= true;
						
					}else{
						
						log( "Unhandled HTTP request: " + command );
						
						return;
					}
					
					if ( url.startsWith("http")){
						
						url = url.replaceFirst("^http://[^/]+", "");
					}
					
					int	pos = url.indexOf( ' ' );
					
					if ( pos == -1 ){
						
						return;
					}
					
					String	http_version = "HTTP/1.1";	// always return this url.substring( pos ).trim();
									
					url = URLDecoder.decode( url.substring(0,pos), "ISO8859-1" );
					
					UPnPMediaServerContentDirectory.contentItem	item			= null;
					
					action_is_download = false;
					
					if ( url.startsWith( "/Platform" )){
											
						int	q_pos = url.indexOf('?');
						
						String	content_id = null;
						
						if ( q_pos != -1 ){
	
							StringTokenizer	tok = new StringTokenizer( url.substring( q_pos+1 ), "&" );
							
							while( tok.hasMoreTokens()){
								
								String	token = tok.nextToken();
								
								int	e_pos = token.indexOf('=');
								
								if ( e_pos != -1 ){
								
									String	lhs = token.substring( 0, e_pos );
									String	rhs = token.substring( e_pos+1 );
									
									if ( lhs.equals( "cid" )){
										
										content_id = rhs;
										
										break;
									}
								}
							}
						}
						
						if ( content_id != null ){
							
							byte[]	hash = Base32.decode( content_id );
							
							item = plugin.getContentDirectory().getContentFromHash( hash );
						}
					}else if ( url.startsWith( "/Content/" )){
											
						String	content = url.substring( 9 );
						
						int	q_pos = content.indexOf('?');
						
						if ( q_pos != -1 ){
	
							String	params = content.substring(q_pos+1);
							
							content = content.substring(0,q_pos);
							
							StringTokenizer tok = new StringTokenizer( params, "&" );
							
							while( tok.hasMoreTokens()){
								
								String	param = tok.nextToken();
								
								int	e_pos = param.indexOf('=');
								
								if ( e_pos != -1 ){
									
									String	lhs = param.substring(0,e_pos);
									String	rhs = param.substring(e_pos+1);
									
									if ( lhs.equals( "sid" )){
										
										try{
											stream_id = Integer.parseInt( rhs );
											
											synchronized( active_processors ){
												
												stream_map.put( new Integer( stream_id ), this );
											}
											
										}catch( Throwable  e){
										}
									}else if ( lhs.equals( "action" )){
										
										if ( rhs.equals( "download" )){
											
											action_is_download = true;
										}
									}
								}
							}
						}
						
						item = plugin.getContentDirectory().getContentFromResourceID( content );
					}
					
					if ( item == null ){
								
						plugin.log( "Unknown content: " + url );
						
						writeb( http_version + " 404 Not Found" + NL );
						writeb( "Connection: close" + NL + NL );
						
						writef();
						
						close_connection = true;
						
					}else{
																			
						try{
							if ( !process( head, http_version, headers, item, close_connection )){
								
								return;
							}
						}catch( Throwable e ){
							
								// IOException not interesting as we get when stream closed
														
							if ( !( e instanceof IOException )){
							 
								e.printStackTrace();
							}
							
							return;
							
						}						
					}
								
					loop_count++;
				}
			}finally{
				
				// log( "exit" );
			}
		}
		
		protected boolean
		process(
			boolean											head,
			String											http_version,
			Map<String,String>								headers,
			UPnPMediaServerContentDirectory.contentItem		content_item,
			boolean											close_connection )
		
			throws Throwable
		{
			/*
				Iterator	it = headers.entrySet().iterator();
				
				while( it.hasNext()){
					
					Map.Entry	entry = (Map.Entry)it.next();
					
					System.out.println( "    "  + entry.getKey() + " -> "  + entry.getValue());
				}
			*/
			
			// final long	hp_start = SystemTime.getHighPrecisionCounter();
			
			String	user_agent = headers.get( "user-agent" );

			DiskManagerFileInfo	file = content_item.getFile();
							
			DiskManagerChannel	disk_channel = null;

			final long[] 	bytes_queued = { 0 };
			
			long		bytes_delivered	= 0;
			
			boolean	is_partial_req_to_end = false;
			
			try{
				String	ranges = headers.get( "range" );
								
					// file_length is -1 if streaming+transcoding on the fly 
				
				final long	file_length = file.getLength();
				
				long	request_start;
				long	request_length;
				
				String originator = user_agent + "/" + ip;
				
				if ( ranges == null ){
					
					log( "Streaming starts for " + content_item.getName() + "  [complete file] - " + originator );
					
					writeb( http_version + " 200 OK" + NL );
										
					writeBoilerPlate( content_item, true, close_connection );
					
					if ( file_length >= 0 ){
												
						writeb( "Content-Range: bytes 0-" + (file_length-1) + "/" + file_length + NL );
						
						writeb( "Content-Length: " + file_length + NL );
					}
											
					request_start 	= 0;
					request_length	= file_length;
										
				}else{
						
					log( "Streaming starts for " + content_item.getName() + "[" + ranges + "] - " + originator );
					
						// When streaming a transcoding file TiVo requires a certain behaviour at end of stream as we have to give
						// it an estimated file size at start
					
					boolean tivo_eos_read = false;
					
					if ( !ranges.startsWith( "bytes=0-" )){
						
						String	ua = headers.get( "user-agent" );
						
						if ( ua != null ){
						
							if ( ua.toLowerCase().indexOf( "TvHttpClient".toLowerCase()) != -1 ){
								
								tivo_eos_read = true;
							}
						}
					}
					
					if ( tivo_eos_read ){
						
						log( "    TiVo range request received - assuming eos" );
						
						is_partial_req_to_end = true;
						
							// for a 0- with content who's length we don't yet know we reply with a 200 (required for TiVo for example)
													
						writeb( http_version + " 206 Partial content" + NL );
											
						writeBoilerPlate( content_item, false, close_connection );

						writeb( "Transfer-Encoding: chunked" + NL );
						
						writeb( NL );
						
						writeb( "0" + NL );
						
						writef();
						
						return( true );
						
					}else{
						
						long[] result = parseRange( ranges, file_length );
								
						if ( result == null ){
							
							writeb( http_version + " 416 Requested Range Not Satisfiable" + NL );
							writeb( "Content-Range: bytes */" + ( file_length<0?"*":file_length ) + NL );
							writeBoilerPlate( null, true, close_connection );
							writeb( NL );
							
							writef();
							
							return( true );
							
						}else{
							
							request_start 	= result[0];
							request_length	= result[2];
																			
							long	request_end				= result[1];
	
							is_partial_req_to_end = request_end == file_length-1;
	
							writeb( http_version + " 206 Partial content" + NL );
																			
							writeBoilerPlate( content_item, true, close_connection );
							
							if ( request_length >= 0 ){
								
								writeb( "Content-Range: bytes " + request_start + "-" + request_end + "/" +  ( file_length<0?"*":file_length) + NL );
								
								writeb( "Content-Length: " + request_length + NL );
							}	
						}
					}
				}
					
				writeb( NL );

				writef();
				
				if ( head ){
					
					return( true );
				}
				
				final Throwable[]	error = { null };
				
				disk_channel = file.createChannel();
				
				final DiskManagerRequest 	request = disk_channel.createRequest();

				request.setType( DiskManagerRequest.REQUEST_READ );
				request.setOffset( request_start );
				request.setLength( request_length );

				if ( request_length > 0 ){
				
					request.setMaximumReadChunkSize((int)Math.min( 256*1024L, request_length ));
				}
				
				last_write_offset	= request_start;
				
				request.setUserAgent( user_agent );

				final long	piece_size = file.getPieceSize();
								
				request.addListener(
					new DiskManagerListener()
					{
						private Average	write_speed = Average.getInstance(1000,10);
						private long	start_time	= plugin_interface.getUtilities().getCurrentSystemTime();
						private long	last_log	= start_time;
						
						private long	total_written;
						
						@Override
						public void
						eventOccurred(
							DiskManagerEvent	event )
						{
							int	type = event.getType();
							
							if ( type == DiskManagerEvent.EVENT_TYPE_FAILED ){
								
								error[0]	= event.getFailure();
								
									// we need to close the channel here as we might be stuck trying to write 
									// to it below and thus without closing the channel we won't pick up
									// this error and terminate 
								
								channel.close();
								
							}else if ( type == DiskManagerEvent.EVENT_TYPE_SUCCESS ){
								
								PooledByteBuffer	buffer = null;
								
								//System.out.println( "[" + processor_num + "] writing at " + event.getOffset() + ", length " + event.getLength());

								try{
									buffer	= event.getBuffer();
										
									int	length = event.getLength();
									
									ByteBuffer	bb = buffer.toByteBuffer();
									
									bb.position( 0 );
									
									channel.write( event.getOffset(), buffer );
																		
									write_speed.addValue( length );
									
									if ( total_written == 0 && length > 0 ){
										
										// System.out.println( "Request latency: " + (SystemTime.getHighPrecisionCounter() - hp_start )/1000000);
									}
									
									total_written += length;
										
									bytes_queued[0] += length;
									
									last_write_time		= plugin_interface.getUtilities().getCurrentSystemTime();
									last_write_offset	= event.getOffset();
									
									if ( last_write_time - last_log > 5000 ){
										
										// System.out.println( "[" + processor_num + "] write speed = " + write_speed.getAverage() + ", total = " + total_written );
											
										last_log = last_write_time;
									}
										
										// bit crap this - if this is a local renderer then limit speed for
										// the first second to prevent vlc from overbufferring
								
									if ( stream_id != -1 ){
										
										if ( last_write_time - start_time < 1000 ){
											
											Thread.sleep(100);
											
										}else{
											
											start_time = 0;	// prevent recur on clock change
										}
									}

									// System.out.println( "[" + processor_num + "] wrote " + event.getLength() + " bytes, total = " + total_written );
									
								}catch( Throwable e ){
									
									request.cancel();
									
									error[0] = e;
									
								}
							}else if ( type == DiskManagerEvent.EVENT_TYPE_BLOCKED ){
								
								//System.out.println( "[" + processor_num + "] blocked at " + event.getOffset());
								
								long	offset = event.getOffset();
								
								if ( offset != last_blocked_offset ){
									
									last_blocked_offset	= offset;
									
									long	piece_num 		= offset/piece_size;
									long	piece_offset	= offset - (piece_num * piece_size );
									
									log( "Blocked reading data at piece " + piece_num + ", offset " + piece_offset );
								}
							}
						}
					});
				
				int	priority = Thread.currentThread().getPriority();
				
				overWriteMonitor	om = null;

				if ( is_partial_req_to_end ){
					
					final String key = content_item.getID() + ":" + originator;
										
					synchronized( overwrite_monitors ){
						
						om = overwrite_monitors.get( key );
						
						if ( om == null ){
														
							om = new overWriteMonitor( content_item );
							
							overwrite_monitors.put( key, om );
							
							if ( overwrite_monitors.size() > MAX_OMS ){
								
								tidyOMS();
							}
						}
						
						if ( om_update_thread == null ){
							
							om_update_thread = 
								new AEThread2( "OMUpdate", true )
								{
									private int tick_count;
									
									@Override
									public void
									run()
									{
										while( true ){
												
											tick_count++;
											
											synchronized( overwrite_monitors ){
												
												if ( overwrite_monitors.size() == 0 ){
													
													om_update_thread = null;
													
													break;
												}
												
												for ( overWriteMonitor om: overwrite_monitors.values()){
													
													om.updateStats( tick_count );
												}
											}
											
											try{
												Thread.sleep( OM_SLEEP_PERIOD );
												
											}catch( Throwable e ){
												
												Debug.out( e );
												
												break;
											}
										}
									}
								};
								
							om_update_thread.start();
						}
					}
					
					final overWriteMonitor	f_om = om;
					
					channel.setListener(
						new UPnPMediaChannel.channelListener()
						{
							long	last_update = 0;
							
							private int	avail = 0;
							
							@Override
							public void
							wrote(
								long		offset, 
								int 		bytes ) 
							{
								f_om.addWrite( offset, bytes );
							}
							
							@Override
							public int
							getAvailableBytes() 
							{
								return( f_om.getAvailableBytes());
							}
						});
				}
				
				long	delivered_start = channel.getChannelUp();
				
				try{
					if ( om != null ){
						
						om.addActive();
					}
					
					active_request 	= request;
				
					Thread.currentThread().setPriority( Thread.MAX_PRIORITY );
					
					request.run();

					channel.flush();
					
				}finally{
					
					Thread.currentThread().setPriority( priority );
					
					active_request	= null;
					
					if ( om != null ){
						
						om.removeActive();
					}
					
					bytes_delivered = channel.getChannelUp() - delivered_start;
				}

				if ( error[0] != null ){
					
					throw( error[0] );
				}
				
			}finally{
												
				log( "Streaming ends for " + content_item.getName() + ": read " + 
						DisplayFormatters.formatByteCountToKiBEtc( bytes_queued[0] ) + ", delivered " + 
						DisplayFormatters.formatByteCountToKiBEtc( bytes_delivered ));
				
				if ( disk_channel != null ){
					
					disk_channel.destroy();
				}
			}
			
			return( true );
		}
		
		protected void
		writeBoilerPlate(
			UPnPMediaServerContentDirectory.contentItem		item,
			boolean											does_ranges,
			boolean											close_connection )
		{
			writeb( "Server: BiglyBT Media Server 1.0" + NL ); 
			if ( does_ranges ){
				writeb( "Accept-Ranges: bytes" + NL );
			}
			writeb( "Connection: " + (close_connection?"Close":"Keep-Alive") + NL );
			writeb( "Cache-Control: no-cache" + NL );
			writeb( "Expires: 0" + NL );
			
				// item can be null for error cases
			
			if ( item != null ){

				if ( action_is_download ){
					
					writeb( "Content-Type: application/octet-stream" + NL );
					writeb( "Content-Transfer-Encoding: binary" + NL );
					writeb( "Content-Disposition: attachment; filename=\"" + item.getFile().getFile( true ).getName() + "\"" + NL );
					
					return;
				}
				
				/* DLNA.ORG_CI: conversion indicator parameter (integer)
				 *     0 not transcoded
				 *     1 transcoded
				 */
				
				/* DLNA.ORG_OP: operations parameter (string)
				 *     "00" (or "0") neither time seek range nor range supported
				 *     "01" range supported
				 *     "10" time seek range supported
				 *     "11" both time seek range and range supported
				 */
				
				/* DLNA.ORG_FLAGS, padded with 24 trailing 0s
				 *     80000000  31  senderPaced
				 *     40000000  30  lsopTimeBasedSeekSupported
				 *     20000000  29  lsopByteBasedSeekSupported
				 *     10000000  28  playcontainerSupported
				 *      8000000  27  s0IncreasingSupported
				 *      4000000  26  sNIncreasingSupported
				 *      2000000  25  rtspPauseSupported
				 *      1000000  24  streamingTransferModeSupported
				 *       800000  23  interactiveTransferModeSupported
				 *       400000  22  backgroundTransferModeSupported
				 *       200000  21  connectionStallingSupported
				 *       100000  20  dlnaVersion15Supported
				 *
				 *     Example: (1 << 24) | (1 << 22) | (1 << 21) | (1 << 20)
				 *       DLNA.ORG_FLAGS=01700000[000000000000000000000000] // [] show padding
				 */
				
				String	PN;
				
					// don't have a mapping set yet so just random
				
				if ( item.getContentClass() == UPnPMediaServerContentDirectory.CONTENT_IMAGE ){
					
					PN = "JPEG";
					
				}else if ( item.getContentClass() == UPnPMediaServerContentDirectory.CONTENT_AUDIO ){
					
					PN = "MP3";
					
				}else{
					
					PN = "MPEG_PS_NTSC";
				}
				
				writeb( "contentFeatures.dlna.org: DLNA.ORG_PN=" + PN + ";DLNA.ORG_OP=01;DLNA.ORG_CI=1;DLNA.ORG_FLAGS=01700000000000000000000000000000" + NL );
				writeb( "transferMode.dlna.org: Streaming" + NL );

				String[] content_types = item.getContentTypes();
				String content_type = null;
				if (content_types.length > 1 && remoteRenderer != null) {
					content_type = remoteRenderer.calculateContentType(content_types);
				}
				if (content_type == null) {
					content_type = content_types[0];
				}
				
				writeb( "Content-Type: " + content_type + NL );

				long date = item.getDateMillis();
				
				if ( date > 0 ){
					
					String str = TimeFormatter.getHTTPDate( date );
					
					writeb( "Date: " + str + NL );
					writeb( "Last-Modified: " + str + NL );
				}
			}
		}
		
		protected void
		writeb(
			String			str )
		{
			write_buffer.append( str );
		}

		protected void
		writef()
		
			throws IOException
		{
			channel.write( -1, write_buffer.toString().getBytes());
			
			write_buffer.setLength(0);
		}
		
		@Override
		public void
		interruptTask()
		{
		}
	}
	
	protected long[]
	parseRange(
		String	ranges,
		long	file_length )
	
		throws IOException
	{
		ranges = ranges.toLowerCase( MessageText.LOCALE_ENGLISH );
		
		if ( !ranges.startsWith("bytes=")){
			
			throw( new IOException( "invalid range: " + ranges ));
		}
		
		ranges = ranges.substring( 6 );
		
		StringTokenizer	tok = new StringTokenizer( ranges, "," );
		
		if ( tok.countTokens() != 1 ){
			
			throw( new IOException( "invalid range - only single supported: " + ranges ));
		}
		
		String	range = tok.nextToken();
		
		int pos	= range.indexOf('-');
		
		long	start;
		long	end;
			
		long	request_length;
		
		if ( file_length >= 0 ){
			
			if ( pos < range.length()-1 ){
				
				end = Long.parseLong( range.substring(pos+1));
				
			}else{
				
				end = file_length-1;
			}
			
			if ( pos > 0 ){
				
				start = Long.parseLong( range.substring(0,pos));
				
			}else{
					// -500 = last 500 bytes of file
				
				start 	= file_length-end;
				end		= file_length-1;
			}

			request_length = ( end - start ) + 1;
			
				// prevent seeking too far
			
			if ( request_length < 0 ){
		
				return( null );		
			}
		}else{
			
				// no length known
			
			if ( pos < range.length()-1 ){
				
				end = Long.parseLong( range.substring(pos+1));
				
			}else{
				
				end = -1;
			}
			
			if ( pos > 0 ){
				
				start = Long.parseLong( range.substring(0,pos));
				
			}else{
					// -500 = last 500 bytes of file
								
				return( null );
			}

			if ( end == -1 ){
				
				request_length = -1;
				
			}else{
				
				request_length = ( end - start ) + 1;
			
					// prevent seeking too far
			
				if ( request_length < 0 ){
					
					return( null );
				}
			}
		}
		
		return( new long[]{ start, end, request_length });
	}
	
	protected void
	tidyOMS()
	{
		long	now = SystemTime.getMonotonousTime();
		
		synchronized( overwrite_monitors ){
			
			Iterator<overWriteMonitor> it = overwrite_monitors.values().iterator();
			
			while( it.hasNext()){
				
				overWriteMonitor om = it.next();
				
				if ( !om.isActive() && now - om.getLastUpdate() > OM_TIMEOUT ){
					
					it.remove();
				}
			}
		}
	}
	
	interface 
	streamInfo
	{
		public long
		getPosition();
		
		public long
		getAvailableBytes();
		
		public long
		getRemaining();
	}
	
	protected class
	overWriteMonitor
	{
		private UPnPMediaServerContentDirectory.contentItem		item;
		
		
		private long	last_update = SystemTime.getMonotonousTime();
		
		private writeEntry[]			history = new writeEntry[32];
		private int						history_pos = 0;
		
		private LinkedList<writeEntry>	writes = new LinkedList<writeEntry>();
		
		private Average 	write_speed 	= Average.getInstance(500, 3); 
		private Average 	overlap_speed 	= Average.getInstance(500, 3); 

		private MovingImmediateAverage	percent_average = AverageFactory.MovingImmediateAverage( 3 );
		
		private int			sample_count;
		
		private int			stats_count;
		
		private int			bytes_available = Integer.MAX_VALUE;
		
		private static final int BYTE_PERIODS = 1000/OM_SLEEP_PERIOD;
		
		private int			bytes_per_sec		= Integer.MAX_VALUE;
		private int			bytes_per_period 	= 0;
		
		private int			active_count;
		
		private boolean		logged_error;
		
		protected
		overWriteMonitor(
			UPnPMediaServerContentDirectory.contentItem		_item )
		{
			item		= _item;
		}
		
		protected void
		addWrite(
			long	this_start,
			int		this_len )
		{
			addWrite( this_start, this_len, true );
		}
		
		protected void
		addWrite(
			long	this_start,
			int		this_len,
			boolean	update_stats )
		{	
			try{
				long	current_start	= this_start;
				int		current_len		= this_len;
				
				long	current_end = current_start + current_len;
					
				writeEntry current_entry = new writeEntry( current_start, current_len );
				
				int	total_overlap = 0;
				
				synchronized( this ){
					
					history[(history_pos++)%history.length] = current_entry;
	
					boolean	added = false;
	
					ListIterator<writeEntry> it = writes.listIterator();
					
					while( it.hasNext()){
						
						writeEntry	entry = it.next();
						
						long	entry_start	= entry.offset;				
						long	entry_end 	= entry_start + entry.length;
						
						if ( current_start > entry_end ){
							
								// not got to relevant position yet
							
							continue;
							
						}else{
													
							if ( current_end < entry_start ){
								
									// new entry comes before existing one, no overlap
								
								if ( it.hasPrevious()){
									
									it.previous();
									
									it.add( current_entry );
									
								}else{
									
									writes.addFirst( current_entry );
								}
								
								added = true;
								
								break;
	
							}else{
								
									// overlap, merge and then insert merged result if needed
								
								if ( 	current_start >= entry_start &&
										current_end <= entry_end ){
										
										// current fits within existing
										
									total_overlap += current_len;
										
									added = true;
										
									break;
	
								}else{
									
									it.remove();
									
									long	overlap_start 	= Math.max( current_start, entry_start );
									long	overlap_end 	= Math.min( current_end, entry_end );
								
									long	overlap = overlap_end - overlap_start;
									
									
									if ( overlap < 0 ){
										
										Debug.out( "inconsistent" );
										
									}else{
										
										total_overlap += overlap;
									}
									
									current_start 		= Math.min( current_start, entry_start );
									current_end		 	= Math.max( current_end, entry_end );
									
									current_len			= (int)(current_end - current_start );
									
									if ( current_len <= 0 ){
										
										Debug.out( "inconsistent" );
									}
									
									current_entry = new writeEntry( current_start, current_len );
								}
							}
						}
					}
					
					if ( !added ){
						
						writes.add( current_entry );
					}
					
					if ( writes.size() > OM_MAX_WRITES ){
						
						writes.removeFirst();
					}
				}
				
				if ( update_stats ){
				
					sample_count++;
					
					last_update = SystemTime.getMonotonousTime();
					
					write_speed.addValue( this_len );
	
					if ( total_overlap > 0 ){
						
						overlap_speed.addValue( total_overlap );
					}
					
					if ( bytes_available < Integer.MAX_VALUE ){
						
						bytes_available -= this_len;
						
						if ( bytes_available < 0 ){
							
							bytes_available = 0;
						}
					}
					
					/*
					System.out.println( "update: " + this_start + "/" + this_len + " -> " + total_overlap + ", e=" + writes.size() + ": " +
							"ws=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( write_speed.getAverage()) +
							"os=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( overlap_speed.getAverage()));
					*/			
				}
								
			}catch( Throwable e ){
				
				if ( !logged_error ){
					
					logged_error = true;
				
					Debug.out( e );
				}
			}
		}
		
		protected void
		updateStats(
			int		tick_count )
		{
			if ( tick_count % OM_STATS_TICKS == 0 ){
				
				stats_count++;
				
				long	ws 	= write_speed.getAverage();
				long	os	= overlap_speed.getAverage();
				
				if ( os > ws ){
					
					os = ws;
				}
				
				long	percent;
				
				if ( os == 0 ){
					
					percent = 0;
					
				}else{
					
					percent = 100*os/ws;
				}
				
				long ave = (long)percent_average.update( percent );
				
				// System.out.println( "overwrite percent=" + ave + " - ws=" + ws + ",os=" + os + ",samp=" + sample_count);
				
				if ( stats_count % 5 == 0 ){
					
					synchronized( this ){
	
						writes.clear();
						
						int pos = history_pos;
						
						for (int i=0;i<history.length;i++){
							
							writeEntry we = history[pos++%history.length];
							
							if ( we != null ){
								
								addWrite( we.offset, we.length, false );
							}
						}
					}
				}
				
				if ( ave > 50 && sample_count >= 5 ){
											
					if ( bytes_per_sec == Integer.MAX_VALUE ){
						
						int	limit_bytes_per_sec = Integer.MAX_VALUE;
						
						long average_bitrate = item.getAverageBitRate();
						
						if ( average_bitrate > 0 ){
						
							int mult = plugin.getAverageBitRateMultiplier();
							
							if ( mult > 0 ){
								
								limit_bytes_per_sec = (int)( ( average_bitrate * mult )/8 );
							}
						}
						
						int	min = plugin.getMinBytesPerSecond();
						
						int	max = plugin.getMaxBytesPerSecond();
						
						if ( max > 0 ){
							
							limit_bytes_per_sec = Math.min( max, limit_bytes_per_sec );
						}
						
						if ( limit_bytes_per_sec == Integer.MAX_VALUE ){
							
							plugin.log( "Wasted bandwidth limit exceeded but no user-defined limits to use - please configure them" );
							
							limit_bytes_per_sec = 100*1024*1024;	// set to something high!
							
						}else{
							
							if ( limit_bytes_per_sec < min ){
								
								limit_bytes_per_sec = min;
							}
							
							plugin.log( "Wasted bandwidth threshold reached: limiting stream to " + DisplayFormatters.formatByteCountToKiBEtcPerSec( limit_bytes_per_sec ));
						}
						
						bytes_per_sec		= limit_bytes_per_sec;
						bytes_per_period 	= limit_bytes_per_sec / BYTE_PERIODS;
						bytes_available		= 0;
					}
				}
			}
			
			if ( bytes_per_period > 0 ){
				
				bytes_available += bytes_per_period;
				
				if ( bytes_available > 2*bytes_per_sec ){
						
					bytes_available = 2*bytes_per_sec;
				}
			}else{
				
				bytes_available = Integer.MAX_VALUE;
			}
		}
		
		public int 
		getAvailableBytes() 
		{	
			int	avail = bytes_available;
			
			return( avail<0?0:avail );
		}
		
		protected long
		getLastUpdate()
		{
			return( last_update );
		}
		
		protected void
		addActive()
		{
			synchronized( this ){
				
				active_count++;
			}
		}
		
		protected void
		removeActive()
		{
			synchronized( this ){
				
				active_count--;
			}
		}
		
		protected boolean
		isActive()
		{
			synchronized( this ){
				
				return( active_count > 0 );
			}
		}
	}
	
	protected static class
	writeEntry
	{
		private long	offset;
		private int		length;
			
		protected 
		writeEntry(
			long		_offset,
			int			_len )
		{
			offset		= _offset;
			length		= _len;
		}
	}
}
