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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.SecureRandom;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;
import com.biglybt.core.util.TimeFormatter;
import org.gudy.bouncycastle.crypto.agreement.srp.SRP6Client;
import org.gudy.bouncycastle.crypto.digests.SHA256Digest;
import org.gudy.bouncycastle.util.encoders.Hex;
import org.json.simple.JSONObject;

import com.biglybt.util.JSONUtils;

public class 
XMRPCClientTunnel 
	implements XMRPCClient
{
	private static BigInteger 
    fromHex(
    	String hex )
    {
        return new BigInteger(1, Hex.decode( hex.replaceAll( " ", "" )));
    }
    
    private static final BigInteger N_3072 = fromHex(
		    "FFFFFFFF FFFFFFFF C90FDAA2 2168C234 C4C6628B 80DC1CD1 29024E08" +
		    "8A67CC74 020BBEA6 3B139B22 514A0879 8E3404DD EF9519B3 CD3A431B" +
		    "302B0A6D F25F1437 4FE1356D 6D51C245 E485B576 625E7EC6 F44C42E9" +
		    "A637ED6B 0BFF5CB6 F406B7ED EE386BFB 5A899FA5 AE9F2411 7C4B1FE6" +
		    "49286651 ECE45B3D C2007CB8 A163BF05 98DA4836 1C55D39A 69163FA8" +
		    "FD24CF5F 83655D23 DCA3AD96 1C62F356 208552BB 9ED52907 7096966D" +
		    "670C354E 4ABC9804 F1746C08 CA18217C 32905E46 2E36CE3B E39E772C" +
		    "180E8603 9B2783A2 EC07A28F B5C55DF0 6F4C52C9 DE2BCBF6 95581718" +
		    "3995497C EA956AE5 15D22618 98FA0510 15728E5A 8AAAC42D AD33170D" +
		    "04507A33 A85521AB DF1CBA64 ECFB8504 58DBEF0A 8AEA7157 5D060C7D" +
		    "B3970F85 A6E1E4C7 ABF5AE8C DB0933D7 1E8C94E0 4A25619D CEE3D226" +
		    "1AD2EE6B F12FFA06 D98A0864 D8760273 3EC86A64 521F2B18 177B200C" +
		    "BBE11757 7A615D6C 770988C0 BAD946E2 08E24FA0 74E5AB31 43DB5BFC" +
		    "E0FD108E 4B82D120 A93AD2CA FFFFFFFF FFFFFFFF" );
	
    private static final BigInteger G_3072 = BigInteger.valueOf(5);
	    
    private static SecureRandom rand = new SecureRandom();

	private XMRPCClientUtils	utils = new XMRPCClientUtils();

    private String		tunnel_server;
    
    private String		basic_user = "vuze";
    private String		access_code;
    private String		username;
    private String		password;
    
    private final long				my_tunnel_id	= rand.nextLong();
    
    private	SecretKeySpec		_secret;
    private String				_tunnel_url;
    
    private int		calls_ok;
    private boolean	destroyed;
    
    private int		calls_active;
    
	public
	XMRPCClientTunnel(
		String		ts,
		String		ac,
		String		tunnel_user,
		String		tunnel_password )
	{
		tunnel_server	= ts;
		access_code		= ac;
		username		= tunnel_user;
		password		= tunnel_password;
	}
	
	private Object[]
	getCurrentTunnel(
		boolean		for_destroy )
	
		throws XMRPCClientException
	{
		synchronized( this ){
					
			if ( for_destroy ){
				
				destroyed = true;

				if ( _tunnel_url == null ){
					
					return( null );
				}
				
				Object[]	result = new Object[]{ _tunnel_url, _secret };
				
				_secret			= null;
				_tunnel_url	 = null;
				
				return( result );
				
			}else if ( destroyed ){
				
				throw( new XMRPCClientException( "Tunnel has been destroyed" ));
			}
			
			if ( _tunnel_url == null ){
								
			    try{
				    byte[] I = username.getBytes( "UTF-8" );
				    byte[] P = password.getBytes( "UTF-8" );

					String str = utils.getFromURL( tunnel_server + "pairing/tunnel/create?ac=" + access_code + "&sid=" + SID );
					
					System.out.println( "create result: " + str );

					JSONObject map = (JSONObject)JSONUtils.decodeJSON( str );
					
					JSONObject error = (JSONObject)map.get( "error" );

					if ( error != null ){
						
						long 	code 	= (Long)error.get( "code" );
						String	msg 	= (String)error.get( "msg" );
						
							// 1, 2, 3 -> bad code/not registered
						
						if ( code == 1 ){
							
							throw( new XMRPCClientException( XMRPCClientException.ET_BAD_ACCESS_CODE, msg ));
							
						}else if ( code == 2 || code == 3 ){
							
							throw( new XMRPCClientException( XMRPCClientException.ET_NO_BINDING, msg ));
							
						}else if ( code == 5 ){
							
							throw( new XMRPCClientException( XMRPCClientException.ET_FEATURE_DISABLED, msg ));

						}else{
							
							throw( new XMRPCClientException( "Uknown error creating tunnel: " + str ));
						}
					}
					
					JSONObject result1 = (JSONObject)map.get( "result" );
										
					byte[]		salt 	= Base32.decode((String)result1.get( "srp_salt" ));
					BigInteger	B		= new BigInteger( Base32.decode((String)result1.get( "srp_b" )));
					
					String url		= (String)result1.get( "url" );
										
					SRP6Client client = new SRP6Client();
					
			        client.init( N_3072, G_3072, new SHA256Digest(), rand );
			        
			        BigInteger A = client.generateClientCredentials( salt, I, P );
			       
			        BigInteger client_secret = client.calculateSecret( B );
	
					byte[] key = new byte[16];
					
					System.arraycopy( client_secret.toByteArray(), 0, key, 0, 16 );
					
					_secret 	= new SecretKeySpec( key, "AES");
					_tunnel_url	= url;
					
					Cipher encipher = Cipher.getInstance ("AES/CBC/PKCS5Padding");
					
					encipher.init( Cipher.ENCRYPT_MODE, _secret);
					
					AlgorithmParameters params = encipher.getParameters ();
					
					byte[] IV = params.getParameterSpec(IvParameterSpec.class).getIV();
			
					JSONObject activate = new JSONObject();
					
					activate.put( "url", url );
					activate.put( "endpoint", "/transmission/rpc?tunnel_format=h" );
					activate.put( "rnd", rand.nextLong());
			
					byte[] activate_bytes = JSONUtils.encodeToJSON( activate ).getBytes( "UTF-8" );
			        
					byte[] enc = encipher.doFinal( activate_bytes );
					
					String str2 = 
						utils.getFromURL( url + 
							"?srp_a=" + Base32.encode( A.toByteArray()) + 
							"&enc_data=" + Base32.encode( enc )+ 
							"&enc_iv=" + Base32.encode( IV ) + 
							"&ac=" + access_code );
			
					JSONObject map2 = (JSONObject)JSONUtils.decodeJSON( str2 );
			
					JSONObject error2 = (JSONObject)map2.get( "error" );

					if ( error2 != null ){

						String msg = (String)error2.get( "msg" );
						
						XMRPCClientException e =  new XMRPCClientException( "Authentication failed: " + msg );
						
						e.setType( XMRPCClientException.ET_CRYPTO_FAILED );
						
						throw( e );
					}
					
					JSONObject result2 = (JSONObject)map2.get( "result" );
			
					System.out.println( result2 );
					
				}catch( XMRPCClientException e ){
					
					throw( e );
					
				}catch( Throwable e ){
					
					throw( new XMRPCClientException( "Failed to create tunnel", e ));
				}
			}
			
			return( new Object[]{ _tunnel_url, _secret });
		}
	}
	
	@Override
	public JSONObject
	call(
		JSONObject		request )
	
		throws XMRPCClientException
	{	
		String	method 	= (String)request.get( "method" );
		Map		args 	= (Map)request.get( "arguments" );
		
		try{			
			synchronized( this ){
				
				calls_active++;
			}

			String json = JSONUtils.encodeToJSON( request );
			
			long	start_time = SystemTime.getMonotonousTime();
			
			System.out.println( TimeFormatter.milliStamp() + "-> " + method + (args==null?"":( ": " + args )) + " (len=" + json.length() + ", active=" + calls_active + ")" );
			
			byte[] req_bytes = json.getBytes( "UTF-8" );
	
			JSONObject request_headers = new JSONObject();
						
			request_headers.put( "Accept-Encoding", "gzip" );
			
			CallResult temp = call( request_headers, req_bytes );
			
			JSONObject	reply_headers		= temp.getHeaders();
			byte[]		reply_bytes			= temp.getBytes();
			int			reply_bytes_offset	= temp.getBytesOffset();
			int			reply_bytes_length	= temp.getBytesLength();
			
			
			String	http_status = (String)reply_headers.get( "HTTP-Status" );

			if ( http_status != null && !http_status.equals( "200") ){
				
				throw( new XMRPCClientException( "Request failed: HTTP status " + http_status ));
			}
			
			String	encoding = (String)reply_headers.get( "Content-Encoding" );
			
			String	reply_str;
			
			if ( encoding != null && encoding.equals( "gzip" )){
				
				GZIPInputStream gis = new GZIPInputStream( new ByteArrayInputStream( reply_bytes, reply_bytes_offset,reply_bytes_length ));
				
				ByteArrayOutputStream baos = new ByteArrayOutputStream( reply_bytes_length );
				
				byte[]	buffer = new byte[128*1024];
				
				while( true ){
					
					int	len = gis.read( buffer );
					
					if ( len <= 0 ){
						
						break;
					}
					
					baos.write( buffer, 0, len );
				}
				
				reply_str = new String( baos.toByteArray(), "UTF-8" );
								
			}else{
						
				reply_str = new String( reply_bytes, reply_bytes_offset,reply_bytes_length , "UTF-8" );
			}
			
			//Thread.sleep( 20000 );
			
			int reply_len = reply_str.length();
			
			JSONObject reply = new JSONObject();

			reply.putAll( JSONUtils.decodeJSON( reply_str ));

			System.out.println( TimeFormatter.milliStamp() + "<- " + method + " (len=" + reply_bytes_length + "/" + reply_len + ", elapsed=" + (SystemTime.getMonotonousTime() - start_time) + ")" );
			
			//System.out.println( "Received reply: " + (reply_str.length()>256?(reply_str.substring(0,256)+"... (" + reply_str.length() + ")"):reply_str) );
		
			return( reply );
			
		}catch( XMRPCClientException e ){
			
			System.out.println( "<- " + method + ": " + Debug.getNestedExceptionMessage( e ));

			throw( e );
			
		}catch( Throwable e ){
			
			System.out.println( "<- " + method + ": " + Debug.getNestedExceptionMessage( e ));

			throw( new XMRPCClientException( "Failed to use tunnel", e ));
			
		}finally{

			synchronized( this ){
				
				calls_active--;
			}
		}
	}
	
	private CallResult
	call(
		JSONObject	request_headers,
		byte[]		request )
	
		throws XMRPCClientException
	{
		if ( request == null ){
			
			request = new byte[0];
		}
		
		Object[] tunnel = getCurrentTunnel( false );
		
		String			url		= (String)tunnel[0];
		SecretKeySpec	secret 	= (SecretKeySpec)tunnel[1];
				
		try{	
			request_headers.put( "X-XMRPC-Tunnel-ID", String.valueOf( my_tunnel_id ));

			String header_json = JSONUtils.encodeToJSON( request_headers );

			byte[] header_bytes = header_json.getBytes( "UTF-8" );
			
			int	header_len = header_bytes.length;
			
			byte[] header_len_bytes = new byte[]{ (byte)(header_len>>8), (byte)header_len };
			
			byte[] encrypted;
			
			{
				Cipher encipher = Cipher.getInstance ("AES/CBC/PKCS5Padding");
					
				encipher.init( Cipher.ENCRYPT_MODE, secret );
					
				AlgorithmParameters params = encipher.getParameters ();
					
				byte[] IV = params.getParameterSpec(IvParameterSpec.class).getIV();
					
				byte[][] enc_buffers = {
					encipher.update( header_len_bytes ),
					encipher.update( header_bytes ),
					encipher.doFinal( request )};

				int	enc_len = 0;
				
				for ( byte[] b: enc_buffers ){
					if ( b != null ){
						enc_len += b.length;
					}
				}
				
				encrypted = new byte[ IV.length + enc_len ];
				
				System.arraycopy( IV, 0, encrypted, 0, IV.length );
				
				int	enc_pos = IV.length;
				
				for ( byte[] b: enc_buffers ){
					if ( b != null ){
						System.arraycopy( b, 0, encrypted, enc_pos, b.length );
						
						enc_pos += b.length;
					}
				}
			}
		
			byte[]	reply_bytes = utils.postToURL( url + "?client=true", encrypted, basic_user, access_code );
						
			byte[]	decrypted;
			
			try{
				byte[]	IV = new byte[16];
				
				System.arraycopy( reply_bytes, 0, IV, 0, IV.length );
				
				Cipher decipher = Cipher.getInstance ("AES/CBC/PKCS5Padding");
	
				decipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec( IV ));
				
				decrypted = decipher.doFinal( reply_bytes, 16, reply_bytes.length-16 );
				
			}catch( Throwable e ){
				
				XMRPCClientException error = new XMRPCClientException( "decrypt failed: " + new String( reply_bytes, 0, reply_bytes.length>256?256:reply_bytes.length ), e );
									
				error.setType( XMRPCClientException.ET_CRYPTO_FAILED );
				
				throw( error );
			}
			
			calls_ok++;
			
			int	reply_header_len = ((decrypted[0]<<8)&0x0000ff00) | (decrypted[1]&0x000000ff);
			
			String	reply_json_str = new String( decrypted, 2, reply_header_len, "UTF-8" );
			
			JSONObject	reply_headers = new JSONObject();
			
			reply_headers.putAll( JSONUtils.decodeJSON( reply_json_str ));
			
			return( new CallResult( reply_headers, decrypted, reply_header_len + 2 ));
			
		}catch( XMRPCClientException e ){
			
			throw( e );
			
		}catch( Throwable e ){
			
			throw( new XMRPCClientException( "Failed to use tunnel", e ));
		}
	}
		
	@Override
	public HTTPResponse
	call(
		String 					method, 
		String 					url,
		Map<String, String> 	headers, 
		byte[] 					data)
	
		throws XMRPCClientException 
	{
		System.out.println( "Sending request: " + method + "" + url + ": " + headers + " - " + data );
		
		try{	
			JSONObject request_headers = new JSONObject();
			
			request_headers.putAll( headers );
			
			request_headers.put( "HTTP-Method", method );
			request_headers.put( "HTTP-URL", url );
			request_headers.put( "Accept-Encoding", "gzip" );
			
			CallResult temp = call( request_headers, data );
			
			JSONObject	reply_headers		= temp.getHeaders();
			byte[]		reply_bytes			= temp.getBytes();
			int			reply_bytes_offset	= temp.getBytesOffset();
			int			reply_bytes_length	= temp.getBytesLength();
			
			
			String	http_status = (String)reply_headers.get( "HTTP-Status" );

			if ( http_status != null && !http_status.equals( "200") ){
				
				throw( new XMRPCClientException( "Request failed: HTTP status " + http_status ));
			}
			
			String	encoding = (String)reply_headers.get( "Content-Encoding" );
			
			byte[]	reply_data;
			int		reply_data_offset;
			
			if ( encoding != null && encoding.equals( "gzip" )){
				
				GZIPInputStream gis = new GZIPInputStream( new ByteArrayInputStream( reply_bytes, reply_bytes_offset,reply_bytes_length ));
				
				ByteArrayOutputStream baos = new ByteArrayOutputStream( reply_bytes_length );
				
				byte[]	buffer = new byte[128*1024];
				
				while( true ){
					
					int	len = gis.read( buffer );
					
					if ( len <= 0 ){
						
						break;
					}
					
					baos.write( buffer, 0, len );
				}
				
				reply_data 			= baos.toByteArray();
				reply_data_offset	= 0;
			}else{
						
				reply_data 			= reply_bytes;
				reply_data_offset 	= reply_bytes_offset;
			}
			
			System.out.println( "Received reply: " + reply_headers );
		
			return( utils.createHTTPResponse((Map<String,String>)(Object)reply_headers, reply_data, reply_data_offset ));
			
		}catch( XMRPCClientException e ){
			
			throw( e );
			
		}catch( Throwable e ){
			
			throw( new XMRPCClientException( "Failed to use tunnel", e ));
		}	
	}
	
	@Override
	public void
	destroy()
	{
		try{
			Object[] tunnel = getCurrentTunnel( true );

			if ( tunnel != null ){
		
				utils.postToURL( (String)tunnel[0] + "?client=true&close=true", new byte[0], basic_user, access_code );
			}		
		}catch( Throwable e ){	
		}
	}
	
	private static class
	CallResult
	{
		private JSONObject		headers;
		private byte[]			buffer;
		private int				offset;
		
		private
		CallResult(
			JSONObject		_headers,
			byte[]			_bytes,
			int				_offset )
		{
			headers	= _headers;
			buffer	= _bytes;
			offset	= _offset;
		}
		
		private JSONObject
		getHeaders()
		{
			return( headers );
		}
		
		private byte[]
		getBytes()
		{
			return( buffer );
		}
		
		private int
		getBytesOffset()
		{
			return( offset );
		}
		
		private int
		getBytesLength()
		{
			return( buffer.length - offset );
		}
	}
}
