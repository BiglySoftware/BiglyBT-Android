/*
 * Created on Feb 28, 2013
 * Created by Paul Gardner
 * 
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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

package com.aelitis.azureus.plugins.xmwebui.client.rpc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.biglybt.core.util.Debug;
import org.gudy.bouncycastle.util.encoders.Base64;

import com.aelitis.azureus.plugins.xmwebui.client.rpc.XMRPCClient.HTTPResponse;

public class 
XMRPCClientUtils 
{
	public static byte[]
   	getFromURLBasic(
   		String					url )
	
   		throws XMRPCClientException
   	{
   		try{
			HttpURLConnection connection = (HttpURLConnection)new URL( url ).openConnection();
						       				
			connection.setConnectTimeout( 30*1000 );
			connection.setReadTimeout( 30*1000 );

			InputStream is = connection.getInputStream();
				
			ByteArrayOutputStream baos = new ByteArrayOutputStream( 10000 );
			
			byte[]	buffer = new byte[8*1024];
			
			while( true ){
			
				int len = is.read( buffer );
				
				if ( len <= 0  ){
					
					break;
				}
				
				baos.write( buffer, 0, len );
			}
			
			return( baos.toByteArray());
				   			
   		}catch( IOException e ){
   			
   			throw( new XMRPCClientException( "HTTP GET for " + url + " failed", e ));
   		}
   	}

	private String session_id;
	
	protected String
	getFromURL(
		String					url ) 
	
		throws XMRPCClientException
	{
		try{
			byte[] bytes = getFromURL( url, null, null, null, null );
			
			String	result = new String(bytes, "UTF-8" );
			
			System.out.println( url + " -> " + result );
			
			return( result );

		}catch( IOException e ){
			
			throw( new XMRPCClientException( "HTTP GET for " + url + " failed", e ));
		}
	}
	
	protected byte[]
	getFromURL(
		String					url,
		Map<String,String>		headers_in,
		Map<String,String>		headers_out,
		String					username,
		String					password )
	
		throws XMRPCClientException
	{
		try{
			for ( int i=0; i<2; i++ ){

				HttpURLConnection connection = (HttpURLConnection)new URL( url ).openConnection();
				
				connection.setRequestProperty( "Connection", "Keep-Alive" );
				
				if ( headers_in != null ){
					
					for ( Map.Entry<String, String> entry: headers_in.entrySet()){
						
						connection.setRequestProperty( entry.getKey(), entry.getValue());
					}
				}
				
				if ( username != null ){
					
					String login = username + ":" + password;
					
					String encodedLogin = new String( Base64.encode( login.getBytes( "UTF-8" )));
					
					connection.setRequestProperty("Authorization", "Basic " + encodedLogin);
				}
				
				if ( session_id != null ){
					
					connection.setRequestProperty( "X-Transmission-Session-Id", session_id );
				}
				
				connection.setConnectTimeout( 30*1000 );
				connection.setReadTimeout( 30*1000 );

				try{
					InputStream is = connection.getInputStream();
					
					if ( headers_out != null ){
						
						Map<String,List<String>> temp = connection.getHeaderFields();
						
						for ( Map.Entry<String,List<String>> entry: temp.entrySet()){
							
							String			key 	= entry.getKey();
							List<String>	values 	= entry.getValue();
							
							if ( values.size() > 0 ){
								
								headers_out.put( key, values.get(0));
							}
						}
					}
					
					ByteArrayOutputStream baos = new ByteArrayOutputStream( 10000 );
					
					byte[]	buffer = new byte[8*1024];
					
					while( true ){
					
						int len = is.read( buffer );
						
						if ( len <= 0  ){
							
							break;
						}
						
						baos.write( buffer, 0, len );
					}
					
					return( baos.toByteArray());
					
				}catch( IOException e ){
			
					if ( connection.getResponseCode() == 409 ){
						
						String str = connection.getHeaderField( "x-transmission-session-id" );
						
						if ( str != null && ( session_id == null || !session_id.equals( str ))){
							
							session_id = str;
							
							continue;
						}
					}
					
					throw( e );
				}
			}
			
			return( null );
			
		}catch( IOException e ){
			
			throw( new XMRPCClientException( "HTTP GET for " + url + " failed", e ));
		}
	}
	
	protected byte[]
  	postToURL(
  		String		url,
  		byte[]		payload )
  	
  		throws XMRPCClientException
  	{
		return( postToURL( url, payload, null, null ));
  	}
	
	protected byte[]
	postToURL(
		String		url,
		byte[]		payload,
		String		username,
		String		password )
	
		throws XMRPCClientException
	{
		try{
			for ( int i=0; i<2; i++ ){
				
				HttpURLConnection connection = (HttpURLConnection)new URL( url ).openConnection();
				
				connection.setRequestMethod( "POST" );
				
				connection.setRequestProperty( "Connection", "Keep-Alive" );
				
				if ( username != null ){
					
					String login = username + ":" + password;
					
					String encodedLogin = new String( Base64.encode( login.getBytes( "UTF-8" )));
					
					connection.setRequestProperty("Authorization", "Basic " + encodedLogin);
				}
				
				if ( session_id != null ){
					
					connection.setRequestProperty( "X-Transmission-Session-Id", session_id );
				}
				
				connection.setConnectTimeout( 30*1000 );
				connection.setReadTimeout( 30*1000 );
				
				connection.setDoOutput( true );
				
				connection.getOutputStream().write( payload );
				
				try{
					InputStream is = connection.getInputStream();
					
					ByteArrayOutputStream baos = new ByteArrayOutputStream( 10000 );
					
					byte[]	buffer = new byte[8*1024];
					
					while( true ){
					
						int len = is.read( buffer );
						
						if ( len <= 0  ){
							
							break;
						}
						
						baos.write( buffer, 0, len );
					}
					
					return( baos.toByteArray());
					
				}catch( IOException e ){
					
					if ( connection.getResponseCode() == 409 ){
						
						String str = connection.getHeaderField( "x-transmission-session-id" );
						
						if ( str != null && ( session_id == null || !session_id.equals( str ))){
							
							session_id = str;
							
							continue;
						}
					}else if ( e instanceof ProtocolException ){
						
						String msg = connection.getResponseMessage();
						
						String e_msg = Debug.getNestedExceptionMessage( e );
						
						if ( !e_msg.contains( msg )){
							
							throw( new IOException( msg + ": " + e_msg ));
						}
					}
					
					throw( e );
				}
			}
			
			return( null );
			
		}catch( IOException e ){
			
			throw( new XMRPCClientException( "HTTP POST for " + url + " failed", e ));
		}
	}
	
	protected HTTPResponse
	createHTTPResponse(
		final Map<String,String>	headers,
		final byte[]				data,
		final int					offset )
	{
		return(
			new HTTPResponse()
			{
				@Override
				public Map<String,String>
				getHeaders()
				{
					return( headers );
				}
				
				@Override
				public byte[]
				getDataBuffer()
				{
					return( data );
				}
				
				@Override
				public int
				getDataBufferOffset() 
				{
					return( offset );
				}
			});
	}
}
