/*
 * Created on Jan 19, 2009
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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;

import com.biglybt.core.util.Debug;
import com.biglybt.pif.tracker.Tracker;
import com.biglybt.pif.tracker.web.TrackerAuthenticationListener;
import com.biglybt.pif.tracker.web.TrackerWebContext;
import com.biglybt.pif.tracker.web.TrackerWebPageGenerator;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;

import com.biglybt.core.content.ContentFilter;
import com.aelitis.azureus.plugins.upnpmediaserver.UPnPMediaServerContentDirectory.*;

public class 
UPnPMediaServerHTTP 
	implements TrackerWebPageGenerator
{
	private static final String	NL = "\r\n";
	
	private UPnPMediaServer		plugin;
	private int					port;
	
	private TrackerWebContext	web_context;
	
	// private UPnPMediaServerTranscoder	current_transcoder;
	
	protected
	UPnPMediaServerHTTP(
		UPnPMediaServer		_plugin,
		int					_port )
	{
		plugin		= _plugin;
		port		= _port;
		
		try{
			web_context = 
					plugin.getPluginInterface().getTracker().createWebContext(
							"UPnP Media Server: HTTP",
							port,
							Tracker.PR_HTTP );
			
			web_context.addAuthenticationListener(
				new TrackerAuthenticationListener()
				{
					@Override
					public boolean
					authenticate(
						URL			resource,
						String		user,
						String		password )
					{
						return( plugin.doHTTPAuth( user, password ));
					}
					
					@Override
					public byte[]
					authenticate(
						URL			resource,
						String		user )
					{
						return( null );
					}
				});
			
			web_context.addPageGenerator( this );	
			
		}catch( Throwable e ){
			
			plugin.logAlert( "Failed to create HTTP server on port " + port + ": " + Debug.getNestedExceptionMessage(e));
		}
	}
	
	@Override
	public boolean
	generate(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response )
	
		throws IOException
	{
		// System.out.println( request.getHeaders());
		
		String	host = (String)request.getHeaders().get( "host" );
		
		if ( host != null ){
			
			host = host.split(":")[0];
		}
		
		String	url = request.getURL();
	
		Map<String,String>	args = new HashMap<String,String>();
		
		int pos = url.indexOf('?');
		
		if ( pos != -1 ){
			
			String[]	bits = url.substring( pos+1 ).split( "&" );
			
			for ( String bit: bits ){
				
				String[] x = bit.split( "=" );
				
				if ( x.length == 1 ){
					
					args.put( bit, null );
					
				}else{
					
					args.put( x[0], URLDecoder.decode( x[1], "UTF-8" ));
				}
			}
		}
		
		List<ContentFilter> filters = plugin.receivedBrowse( request, true );
		
		if ( url.startsWith( "/basic" )){
			
			return( generateBasic( host, url.substring( 6 ), args, filters, response ));
			
		}else{
			
				// treat as basic for all urls for the moment
			
			return( generateBasic( host, url, args, filters, response ));
		}
	}
	
	protected boolean
	generateBasic(
		String						host,
		String						url,
		Map<String,String>			args,
		List<ContentFilter>	filters,
		TrackerWebPageResponse		response )
	
		throws IOException
	{
		plugin.log( "HTTP: " + url );
		
		if ( url.startsWith( "/" ) && url.length() > 1 ){
			
			return( useResource( url, new HashMap<String, String>(), response ));
			
		}else if ( args.get( "iid" ) != null ){
			
			contentItem item = (contentItem)plugin.getContentDirectory().getContentFromID( Integer.parseInt(args.get( "iid" )));
			
			if ( item == null ){
				
				return( false );
			}
			
			String	name = item.getDisplayTitle();

			String stream_uri = item.getURI( host, -1 );
			
			Map<String,String>	map = new HashMap<String,String>();
			
			map.put( "TITLE", escape( item.getName()));
			map.put( "URL", stream_uri );
			
			if ( name.endsWith( ".flv" )){
			
				return( useResource( "/flv_player.html", map, response ));
				
			}else{
				
				return( useResource( "/ts_player.html", map, response ));
			}
		}else{
			
			response.setContentType( "text/html; charset=utf-8" );
			
			OutputStream  os = response.getOutputStream();
			
			PrintWriter pw = new PrintWriter( new OutputStreamWriter( os, "UTF-8" ));
			
			pw.println( "<head>" );
			pw.println( "<title>" + escape( "HTTP Server for '" + plugin.getServiceName() + "'" ) + "</title>" );
			pw.println( "</head>" );
			pw.println( "<body>" );
			
			String	container_id = args.get( "cid" );
			
			UPnPMediaServerContentDirectory content_directory = plugin.getContentDirectory();
			
			contentContainer	container = null;
			
			if ( container_id != null ){
			
				container = content_directory.getContainerFromID( Integer.parseInt( container_id ));
			}
			
			if ( container == null ){
				
				container = content_directory.getRootContainer();
			}
			
			if ( container == null ){
				
				return( false );
			}
			
			pw.println(escape( container.getName()));
			
			List<content> kids = container.getChildren();
			
			plugin.sortContent( kids );
			
			Map<String,Object> filter_args = new HashMap<String, Object>();

			for ( content kid: kids ){
				
				if ( plugin.isVisible( kid, filters, filter_args )){
					
					if ( kid instanceof contentContainer ){
						
						String kid_url = "/basic?cid=" + kid.getID();
						
						pw.println( "<ul><a href=" + kid_url + ">" + escape( kid.getName()) + "</a></ul>" );
						
					}else{
						
						contentItem item = (UPnPMediaServerContentDirectory.contentItem)kid;
							
						String	name = item.getDisplayTitle();
						
						if ( name.endsWith( ".flv" ) || name.endsWith( ".ts" )){
							
							pw.println( "<li><a href=/basic?iid=" + kid.getID() + ">" + escape( name ) + "</a></li>" );
							
							/*
						}else if ( name.endsWith( ".mkv" )){
							
							name = name.substring( 0, name.lastIndexOf('.')) + ".flv";
							
							pw.println( "<li><a href=/basic?transcode=Wii&iid=" + kid.getID() + ">" + escape( name ) + "</a></li>" );
							*/
							
						}else{
						
							String kid_url = item.getURI( host, -1 );
	
							pw.println( "<li><a href=" + kid_url + ">" + escape( name ) + "</a></li>" );
						}
					}
				}
			}
			
			pw.println( "</body>" );
	
			pw.flush();
			
			return( true );
		}
	}
	
	protected boolean
	useResource(
		String						url,
		Map<String,String>			substitutions,
		TrackerWebPageResponse		response )
	
		throws IOException
	{
		url = url.trim();
		
		if ( url.indexOf( ".." ) == -1 && !url.endsWith( "/" )){
		
				// second we don't support nested resources
			
			int	 pos = url.lastIndexOf("/");
			
			if ( pos != -1 ){
				
				url = url.substring( pos );
			}
			
			String resource = "/com/aelitis/azureus/plugins/upnpmediaserver/resources/http" + url;

			InputStream stream = getClass().getResourceAsStream( resource );
			
			if ( stream != null ){
				
				if ( substitutions.size() > 0 ){
					
					byte[]	buffer = new byte[1024];
					
					String	content = "";
					
					while( true ){
						
						int	len = stream.read( buffer );
						
						if ( len <= 0 ){
							
							break;
						}
						
						content += new String( buffer, 0, len, "UTF-8" );
					}
					
					for (Map.Entry<String,String> entry: substitutions.entrySet()){
					
						
						content = content.replaceAll( "%" + entry.getKey() + "%", Matcher.quoteReplacement(entry.getValue()));
					}
										
					stream.close();
					
					stream = new ByteArrayInputStream( content.getBytes( "UTF-8" ));
				}
				
				try{
					pos = url.lastIndexOf( '.' );
					
					String	file_type = pos==-1?"?":url.substring(pos+1);
					
					response.useStream( file_type, stream );
				
					return( true );
					
				}finally{
						
					stream.close();
				}
			}
		}
		
		return( false );
	}
	
	
	protected String
	escape(
		String	str )
	{
		return( plugin.escapeXML( str ));
	}
	
	protected void
	destroy()
	{
		if ( web_context != null ){
		
			web_context.destroy();
		}
		
		/*
		if ( current_transcoder != null ){
			
			current_transcoder.destroy();
		}
		*/
	}
}
