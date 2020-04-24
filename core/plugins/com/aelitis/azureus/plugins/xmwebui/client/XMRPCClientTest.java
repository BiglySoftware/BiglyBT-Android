/*
 * Created on Sep 16, 2009
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

package com.aelitis.azureus.plugins.xmwebui.client;

import java.util.*;
import java.net.*;
import java.io.*;

import com.aelitis.azureus.plugins.xmwebui.TransmissionVars;
import com.biglybt.core.util.UrlUtils;
import org.json.simple.JSONObject;

import com.aelitis.azureus.plugins.xmwebui.client.rpc.XMRPCClient;
import com.aelitis.azureus.plugins.xmwebui.client.rpc.XMRPCClientFactory;
import com.biglybt.util.JSONUtils;

public class 
XMRPCClientTest 
{
	private static void
	test1(
		String	access_code )
	
		throws Exception
	{
		int retry_count = 0;
	
		String session_id = null;
	
		while( true ){
				
			HttpURLConnection connection;
			
			if ( false ){
				
				connection = (HttpURLConnection)new URL( "http://127.0.0.1:9091/transmission/rpc" ).openConnection();
				
				connection.setRequestMethod( "POST" );
				
				connection.setDoOutput( true );
				
				PrintWriter pw = new PrintWriter( new OutputStreamWriter( connection.getOutputStream(), "UTF-8" ));
				
				Map request = new JSONObject();
				
				/*
				request.put( "method", "torrent-get" );
				
				Map	arg_map = new HashMap();
				
				request.put( "arguments", arg_map );
				
				List fields = new ArrayList();
				
				arg_map.put( "fields", fields );
				
				fields.add( "addedDate" );
				fields.add( "announceURL" );
				fields.add( "comment" );
				fields.add( "creator" );
				fields.add( "dateCreated" );
				fields.add( "downloadedEver" );
				fields.add( "error" );
				fields.add( "errorString" );
				fields.add( "eta" );
				fields.add( "hashString" );
				fields.add( "haveUnchecked" );
				fields.add( "haveValid" );
				fields.add( "id" );
				fields.add( "isPrivate" );
				fields.add( "leechers" );
				fields.add( "leftUntilDone" );
				fields.add( "name" );
				fields.add( "peersConnected" );
				fields.add( "peersGettingFromUs" );
				fields.add( "peersSendingToUs" );
				fields.add( "rateDownload" );
				fields.add( "rateUpload" );
				fields.add( "seeders" );
				fields.add( "sizeWhenDone" );
				fields.add( "status" );
				fields.add( "swarmSpeed" );
				fields.add( "totalSize" );
				fields.add( "uploadedEver" );
	
				
				request.put( "tag", "1234" );
				*/
				
				// {"method":"torrent-add","arguments":{"paused":"true","filename":"http://www.mininova.org/get/2963304"}}
				
				request.put( "method", "torrent-add" );
				
				Map	arg_map = new HashMap();
				
				request.put( "arguments", arg_map );
	
				File f = new File( "C:\\temp\\b.torrent" );
				
				String url = f.toURL().toExternalForm();
				
				System.out.println( "Adding " + url );
				
				arg_map.put( "paused", "true");
				arg_map.put( "filename", url );
				
				pw.println( JSONUtils.encodeToJSON( request ));
				
				pw.flush();
			}else if ( false ){
				Map request = new JSONObject();
	
				request.put( "method", "torrent-start-all" );
	
				String url = "http://vuze:" + access_code + "@127.0.0.1:9091/vuze/rpc?json=" + UrlUtils.encode( JSONUtils.encodeToJSON( request ));
				
				System.out.println( url );
				
				connection = (HttpURLConnection)new URL( url).openConnection();
	
			}else if ( false ){
				Map request = new JSONObject();
	
				request.put( "method", "session-set" );
	
				Map	arg_map = new HashMap();
				
				request.put( "arguments", arg_map );
	
				arg_map.put(TransmissionVars.TR_PREFS_KEY_PEER_PORT, 4444 );
				
				String json = JSONUtils.encodeToJSON( request );
				
				String url = "http://vuze:" + access_code + "@127.0.0.1:9091/vuze/rpc?json=" + UrlUtils.encode( json );
				
				System.out.println( json + " -> " + url );
				
				connection = (HttpURLConnection)new URL( url).openConnection();
		
			}else{
				String str = "{\"arguments\":{ \"download-dir\":\"G::\\.....\\Downloads\"," +
					"\"ratio-limit-enabled\":true, \"ratio-limit\":2," +
					"\"speed-limit-down-enabled\":true, \"speed-limit-down\":5," +
					"\"speed-limit-up-enabled\":true, \"speed-limit-up\":5}," +
					"\"method\":\"session-set\"}"; 
					
				connection = (HttpURLConnection)new URL( "http://127.0.0.1:9091/transmission/rpc" ).openConnection();
					
				if ( session_id != null ){
					
					connection.setRequestProperty( "X-Transmission-Session-Id", session_id );
				}
				
				connection.setRequestMethod( "POST" );
					
				connection.setDoOutput( true );
					
				PrintWriter pw = new PrintWriter( new OutputStreamWriter( connection.getOutputStream(), "UTF-8" ));
	
				pw.println( str );
				
				pw.flush();
			}
			
			if ( connection.getResponseCode() == 409 ){
					
				session_id = connection.getHeaderField( "x-transmission-session-id" );
				
				retry_count++;
				
				if ( retry_count < 2 ){
				
					continue;
				}
			}
			
			LineNumberReader lnr = new LineNumberReader( new InputStreamReader( connection.getInputStream(), "UTF-8" ));
			
			StringBuffer	request_json_str = new StringBuffer(2048);
			
			while( true ){
				
				String	line = lnr.readLine();
				
				if ( line == null ){
					
					break;
				}
				
				request_json_str.append( line );
			}
			
			System.out.println( request_json_str.toString());	
			
			break;
		}
	}
	
	//public static final String PAIRING_URL 	= "https://pair.vuze.com/";
	//public static final String PAIRING_URL 	= "http://127.0.0.1:9091/";

	
	private static void
	test2(
		String	code,
		String	password )
	
		throws Exception
	{
		XMRPCClient client = XMRPCClientFactory.createDirect( true, "127.0.0.1", 9091, "vuze", password );
		
		//XMRPCClient client = XMRPCClientFactory.createIndirect( code );
		//XMRPCClient client = XMRPCClientFactory.createTunnel( "http://127.0.0.1:9091/", code, "vuze", password );
		//XMRPCClient client = XMRPCClientFactory.createTunnel( "https://pair.vuze.com/", code, "vuze", password );
		
		try{
			{
				JSONObject request = new JSONObject();

				request.put( "method", TransmissionVars.METHOD_SESSION_SET);

				Map	arg_map = new HashMap();
				
				request.put( "arguments", arg_map );

				arg_map.put(TransmissionVars.TR_PREFS_KEY_USPEED_ENABLED, true );
				arg_map.put(TransmissionVars.TR_PREFS_KEY_USPEED_KBps, 5 );
				
				JSONObject reply = client.call( request );
				
				System.out.println( reply );
			}
			{
				JSONObject	request = new JSONObject();
			
				request.put( "method", TransmissionVars.METHOD_SESSION_GET);
			
				JSONObject reply = client.call( request );
			
				System.out.println( reply );
			}
			
		}finally{
			
			client.destroy();
		}
	}
	
	public static void
	main(
		String[]		args )
	{
		try{
	
			test1(args[0]);
			
			//test2(args[0], args[1]);
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
}
