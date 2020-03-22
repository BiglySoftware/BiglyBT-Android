/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.aelitis.azureus.plugins.xmwebui;

import java.net.URL;
import java.net.URLDecoder;
import java.util.*;

import com.biglybt.core.util.*;

import com.biglybt.pif.download.*;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;

class
MagnetDownload
	implements DownloadStub
{
	private URL magnet_url;
	private String			name;
	private byte[]			hash;
	private long			create_time;
	
	private Map<TorrentAttribute,Long> attributes = new HashMap<>();
	
	private String temp_dir = AETemporaryFileHandler.getTempDirectory().getAbsolutePath();
	
	private Throwable error;
	
	MagnetDownload(
			XMWebUIPlugin plugin,
			URL _magnet,
			String friendlyName)
	{
		create_time	= SystemTime.getCurrentTime();
		
		magnet_url	= _magnet;
		
		String	str = magnet_url.toExternalForm();
		
		int	pos = str.indexOf( '?' );
		
		if ( pos != -1 ){
			
			str = str.substring( pos+1 );
		}

		List<String> args = StaticUtils.fastSplit(str, '&');

		Map<String,String>	arg_map = new HashMap<>();
		
		for ( String arg: args ){

			List<String> bits = StaticUtils.fastSplit(arg, '=');
			
			if ( bits.size() == 2 ){
				
				try{
					String lhs = bits.get(0).trim().toLowerCase( Locale.US );
					String rhs = URLDecoder.decode( bits.get(1).trim(), Constants.DEFAULT_ENCODING);
					
					if ( lhs.equals( "xt" )){
						
						if ( rhs.toLowerCase( Locale.US ).startsWith( "urn:btih:" )){
							
							arg_map.put( lhs, rhs );
							
						}else{
							
							String existing = arg_map.get( "xt" );
							
							if ( 	existing == null ||
									( !existing.toLowerCase( Locale.US ).startsWith( "urn:btih:" )  && rhs.startsWith( "urn:sha1:" ))){
								
								arg_map.put( lhs, rhs );
							}
						}
					}else{
						
						arg_map.put( lhs, rhs );
					}
				}catch( Throwable e ){
				}
			}
		}
		
		hash	= new byte[0];

		String hash_str = arg_map.get( "xt" );
		
		if ( hash_str != null ){
			
			hash_str = hash_str.toLowerCase( Locale.US );
			
			if ( hash_str.startsWith( "urn:btih:" ) || hash_str.startsWith( "urn:sha1" )){
				
				hash = UrlUtils.decodeSHA1Hash( hash_str.substring( 9 ));
			}
		}
		
		name	= arg_map.get( "dn" );
		
		if ( name == null ){
			
			if ( friendlyName != null ){

				name = friendlyName;

			} else if ( hash == null ){
				
				name = magnet_url.toExternalForm();
				
			}else{
				
				name = Base32.encode( hash );
			}
		}
		
		name = "Magnet download for '" + name + "'";

		plugin.getID( this, true );
	}
	
	long
	getCreateTime()
	{
		return( create_time );
	}
	
	URL
	getMagnetURL()
	{
		return( magnet_url );
	}
	
	@Override
	public boolean
	isStub()
	{
		return( true );
	}
	
	@Override
	public Download
	destubbify()
	
		throws DownloadException
	{
		throw( new DownloadException( "Not supported" ));
	}
	
	@Override
	public String
	getName()
	{
		return( name );
	}
	
	@Override
	public byte[]
	getTorrentHash()
	{
		return( hash );
	}
	
	@Override
	public Torrent
	getTorrent() 
	{
		return( null );
	}
	
	@Override
	public long
	getTorrentSize()
	{
		return( 16*1024 );	// dont know the size
	}
	
	@Override
	public String
	getSavePath()
	{
		return( temp_dir );
	}
	
	void
	setError(
		Throwable e )
	{
		error	= e;
	}
	
	Throwable
	getError()
	{
		return( error );
	}
	
	@Override
	public DownloadStubFile[]
	getStubFiles()
	{
		return( new DownloadStubFile[0]);
	}
	
	@Override
	public long
	getLongAttribute(
		TorrentAttribute 	attribute )
	{
		Long l = attributes.get( attribute );
		
		return( l==null?0:l );
	}
	  
	@Override
	public void
	setLongAttribute(
		TorrentAttribute 	attribute, 
		long 				value)
	{
		attributes.put( attribute, value );
	}
	  
	@Override
	public void
	remove()
	
		throws DownloadException, DownloadRemovalVetoException
	{
		
	}
}
