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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;

import com.biglybt.util.JSONUtils;

public class 
XMRPCClientIndirect 
	implements XMRPCClient
{
	private XMRPCClientUtils	utils = new XMRPCClientUtils();

	private String	pair_server;
	private String 	access_code;
	
	private String binding_url;
	
	public
	XMRPCClientIndirect(
		String		ps,
		String		ac )
	{
		pair_server		= ps;
		access_code		= ac;
	}
	
	private String
	getCurrentBinding()
	
		throws XMRPCClientException
	{
		if ( binding_url == null ){
			
			String str = utils.getFromURL( pair_server + "pairing/remote/getBinding?ac=" + access_code + "&sid=" + SID );
			
			System.out.println( "Binding result: " + str );
			
			Map map = JSONUtils.decodeJSON( str );
			
			JSONObject error = (JSONObject)map.get( "error" );

			if ( error != null ){
				
				long code = (Long)error.get( "code" );
				
					// 1, 2, 3 -> bad code/not registered
				
				if ( code == 1 ){
					
					throw( new XMRPCClientException( XMRPCClientException.ET_BAD_ACCESS_CODE ));
					
				}else if ( code == 2 || code == 3 ){
					
					throw( new XMRPCClientException( XMRPCClientException.ET_NO_BINDING ));
				}else{
					
					throw( new XMRPCClientException( "Uknown error creating tunnel: " + str ));
				}
			}
			
			Map result = (Map)map.get( "result" );
			
			String protocol = (String)result.get( "protocol" );
			String ip		= (String)result.get( "ip" );
			String port		= (String)result.get( "port" );
			
			binding_url = protocol + "://" + ip + ":" + port;
		}
		
		return( binding_url );
	}
	
	@Override
	public JSONObject
	call(
		JSONObject		request )
	
		throws XMRPCClientException
	{
		try{
			String url = getCurrentBinding();
			
			url += "/transmission/rpc";
			
			String json = JSONUtils.encodeToJSON( request );
			
			byte[] reply = utils.postToURL( url , json.getBytes( "UTF-8" ), "vuze", access_code );
			
			Map m = JSONUtils.decodeJSON( new String( reply, "UTF-8" ));
			
			JSONObject result = new JSONObject();
			
			result.putAll( m );
			
			return( result );
			
		}catch( IOException e ){
			
			throw( new XMRPCClientException( "unexpected" ));
		}
	}
	
	@Override
	public HTTPResponse
	call(
		String 					method, 
		String 					url_suffix,
		Map<String, String> 	headers_in, 
		byte[] 					data)
	
		throws XMRPCClientException 
	{
		if ( method.equals( "GET" )){
			
			String url = getCurrentBinding();
			
			url += url_suffix;
	
			Map<String,String>	headers_out = new HashMap<String, String>();
			
			byte[] output_data = utils.getFromURL( url, headers_in, headers_out, "vuze", access_code );
			
			return( utils.createHTTPResponse( headers_out, output_data, 0 ));
			
		}else{
			
			throw( new XMRPCClientException( "Not supported" ));
		}
	}
	
	@Override
	public void
	destroy() 
	{
	}
}
