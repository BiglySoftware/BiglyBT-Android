/*
 * Created on Feb 18, 2009
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.biglybt.core.content.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadManager;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;

import com.biglybt.core.content.ContentDirectory;


public class 
UPnPMediaServerContentDirectory 
{
	public static final int		CT_UNKNOWN			= -1;
	public static final int		CT_DEFAULT			= 1;
	public static final int		CT_XBOX				= 2;
	public static final int		CT_MEDIA_PLAYER		= 3;
	

	private static final String	CONTENT_UNKNOWN	= "object.item";
	
	protected static final String	CONTENT_CONTAINER	= "object.container.storageFolder";
	
	//private static final String	CONTENT_VIDEO	= "object.item.videoItem";
	protected static final String	CONTENT_VIDEO	= "object.item.videoItem.movie";
	protected static final String	CONTENT_AUDIO	= "object.item.audioItem.musicTrack";
	protected static final String	CONTENT_IMAGE	= "object.item.imageItem.photo";

	private static final String	XBOX_CONTENT_VIDEO	= "object.item.videoItem";
	private static final String	XBOX_CONTENT_AUDIO	= "object.item.audioItem.musicTrack";
	private static final String	XBOX_CONTENT_IMAGE	= "object.item.imageItem.photo";

	
	
	private static final String[][]	mime_mappings = {
		
			// Microsoft
		
		{ "asf", "video/x-ms-asf",				CONTENT_VIDEO },
		{ "asx", "video/x-ms-asf",				CONTENT_VIDEO },
		{ "nsc", "video/x-ms-asf",				CONTENT_VIDEO },
		{ "wax", "audio/x-ms-wax",				CONTENT_AUDIO },
		{ "wm",  "video/x-ms-wm",				CONTENT_VIDEO },
		{ "wma", "audio/x-ms-wma",				CONTENT_AUDIO },
		{ "wmv", "video/x-ms-wmv",				CONTENT_VIDEO },
		{ "wmx", "video/x-ms-wmx",				CONTENT_VIDEO },
		{ "wvx", "video/x-ms-wvx",				CONTENT_VIDEO },
		
			// other video
		
		// { "avi",  "video/x-ms-video",			CONTENT_VIDEO },
		{ "avi",  "video/avi",					CONTENT_VIDEO },
		{ "mp2",  "video/mpeg", 				CONTENT_VIDEO },
		{ "mpa",  "video/mpeg", 				CONTENT_VIDEO },
		{ "mpe",  "video/mpeg", 				CONTENT_VIDEO },
		{ "mpeg", "video/mpeg", 				CONTENT_VIDEO },
		{ "mpg",  "video/mpeg", 				CONTENT_VIDEO },
		{ "mpv2", "video/mpeg", 				CONTENT_VIDEO },
		{ "vob",  "video/mpeg", 				CONTENT_VIDEO },
		{ "mov",  "video/quicktime", 			CONTENT_VIDEO },
		{ "qt",   "video/quicktime", 			CONTENT_VIDEO },
		{ "lsf",  "video/x-la-asf", 			CONTENT_VIDEO },
		{ "lsx",  "video/x-la-asf", 			CONTENT_VIDEO },
		{ "movie","video/x-sgi-movie", 			CONTENT_VIDEO },
		{ "mkv",  "video/x-matroska", "video/x-mkv", CONTENT_VIDEO },
		{ "mp4",  "video/mp4", 					CONTENT_VIDEO },
		{ "mpg4", "video/mp4", 					CONTENT_VIDEO },
		{ "flv",  "video/x-flv", 				CONTENT_VIDEO },
		{ "ts",   "video/MP2T", 				CONTENT_VIDEO },
		{ "m4v",  "video/m4v", "video/mp4",	CONTENT_VIDEO },
		{ "mts",  "video/MP2T", 				CONTENT_VIDEO },
		{ "m2ts", "video/MP2T", 				CONTENT_VIDEO },
		
			// audio
		
		{ "au",   "audio/basic",				CONTENT_AUDIO },
		{ "snd",  "audio/basic", 				CONTENT_AUDIO },
		{ "mid",  "audio/mid",  				CONTENT_AUDIO },
		{ "rmi",  "audio/mid", 					CONTENT_AUDIO },
		{ "mp3",  "audio/mpeg" ,				CONTENT_AUDIO },
		{ "aif",  "audio/x-aiff", 				CONTENT_AUDIO },
		{ "aifc", "audio/x-aiff", 				CONTENT_AUDIO },
		{ "aiff", "audio/x-aiff", 				CONTENT_AUDIO },
		{ "m3u",  "audio/x-mpegurl", 			CONTENT_AUDIO },
		{ "ra",   "audio/x-pn-realaudio",		CONTENT_AUDIO },
		{ "ram",  "audio/x-pn-realaudio",		CONTENT_AUDIO },
		{ "wav",  "audio/x-wav", 				CONTENT_AUDIO },
		{ "flac", "audio/flac",					CONTENT_AUDIO },
		{ "mka",  "audio/x-matroska",			CONTENT_AUDIO },
		{ "m4a",  "audio/mp4",                  CONTENT_AUDIO },
		
			// image
		
		{ "bmp",  "image/bmp", 					CONTENT_IMAGE },
		{ "cod",  "image/cis-cod",				CONTENT_IMAGE }, 
		{ "gif",  "image/gif", 					CONTENT_IMAGE },
		{ "ief",  "image/ief", 					CONTENT_IMAGE },
		{ "jpe",  "image/jpeg", 				CONTENT_IMAGE },
		{ "jpeg", "image/jpeg", 				CONTENT_IMAGE },
		{ "jpg",  "image/jpeg", 				CONTENT_IMAGE },
		{ "png",  "image/png", 					CONTENT_IMAGE },
		{ "jfif", "image/pipeg",		 		CONTENT_IMAGE },
		{ "tif",  "image/tiff", 				CONTENT_IMAGE },
		{ "tiff", "image/tiff", 				CONTENT_IMAGE },
		{ "ras",  "image/x-cmu-raster", 		CONTENT_IMAGE },
		{ "cmx",  "image/x-cmx", 				CONTENT_IMAGE },
		{ "ico",  "image/x-icon", 				CONTENT_IMAGE },
		{ "pnm",  "image/x-portable-anymap", 	CONTENT_IMAGE }, 
		{ "pbm",  "image/x-portable-bitmap", 	CONTENT_IMAGE },
		{ "pgm",  "image/x-portable-graymap",	CONTENT_IMAGE },
		{ "ppm",  "image/x-portable-pixmap", 	CONTENT_IMAGE },
		{ "rgb",  "image/x-rgb", 				CONTENT_IMAGE },
		{ "xbm",  "image/x-xbitmap", 			CONTENT_IMAGE },
		{ "xpm",  "image/x-xpixmap", 			CONTENT_IMAGE },
		{ "xwd",  "image/x-xwindowdump", 		CONTENT_IMAGE },
		
			// other
		
		{ "ogg",   "application/ogg",			CONTENT_AUDIO },
		{ "ogm",   "application/ogg",			CONTENT_VIDEO },
				
	};
	
	private static Map<String,String[]>	ext_lookup_map = new HashMap<String,String[]>();
	
	static{
		for (int i=0;i<mime_mappings.length;i++){
			
			ext_lookup_map.put( mime_mappings[i][0], mime_mappings[i] );
		}
	}
	
	private static SimpleDateFormat upnp_date_format = new SimpleDateFormat( "yyyy-MM-dd" );
	
	private UPnPMediaServer	media_server;
	
	private TorrentAttribute	ta_unique_name;

	private int		next_oid;

	private Random	random = new Random();
	
	private int		system_update_id	= random.nextInt( Integer.MAX_VALUE );

	private Map<Integer,content>		content_map 	= new HashMap<Integer,content>();
	private Map<String,content>		resourcekey_map 	= new HashMap<String,content>();

	private Map<String,Long>			config;
	private boolean						config_dirty;
	
	private Object					lock = new Object();
	
	private contentContainer		root_container;
	private contentContainer		downloads_container;
	private contentContainer		movies_container;
	private contentContainer		music_container;
	private contentContainer		pictures_container;

	protected
	UPnPMediaServerContentDirectory(
		UPnPMediaServer		_media_server )
	{
		media_server = _media_server;

		ta_unique_name	= media_server.getPluginInterface().getTorrentManager().getPluginAttribute( "unique_name");
		
		root_container = new contentContainer( null, "BiglyBT" );
		
		music_container 	= new contentContainer( root_container, "Music", "M" );
				
		pictures_container 	= new contentContainer( root_container, "Pictures", "P" );
		
		movies_container 	= new contentContainer( root_container, "Movies", "V" );
		
		downloads_container 	= new contentContainer( root_container, "Downloads" );
	}
	
	protected void
	addContent(
		final Download			download )
	{
		Torrent torrent = download.getTorrent();
		
		if ( torrent == null ){
			
			return;
		}
		
		HashWrapper hash = new HashWrapper(torrent.getHash());

		DiskManagerFileInfo[]	files = download.getDiskManagerFileInfo();

		synchronized( lock ){
				
			if ( files.length == 1 ){
						
				String title = getUniqueName( download, hash, files[0].getFile().getName());
				
				contentItem	item = new contentItem( downloads_container, getACF( files[0]), download.getTorrent().getHash(), title );
				
				addToFilters( item );
				
			}else{
				
				contentContainer container = 
					new contentContainer( 
							downloads_container,
							getACD( download ),
							getUniqueName( download, hash, download.getName()));
								
				Set<String>	name_set = new HashSet<String>();
				
				boolean	duplicate = false;
				
				for (int j=0;j<files.length;j++){

					DiskManagerFileInfo	file = files[j];
									
					String	title = file.getFile().getName();
					
					if ( name_set.contains( title )){
						
						duplicate = true;
						
						break;
					}
					
					name_set.add( title );
				}
				
				for (int j=0;j<files.length;j++){
					
					DiskManagerFileInfo	file = files[j];
							
						// keep these unique within the container
					
					String	title = file.getFile().getName();
					
					if ( duplicate ){
						
						title =  ( j + 1 ) + ". " + title;
					}
					
					new contentItem( container, getACF( file ), hash.getBytes(), title );
				}
				
				addToFilters( container );
			}
		}
	}
	
	protected void
	addContent(
		ContentFile content_file )
	{
		synchronized( lock ){
	
			DiskManagerFileInfo file = content_file.getFile();
			
			try{
				byte[]	hash = file.getDownloadHash();
				
				
				String title;
				
				Object otitle = content_file.getProperty(ContentFile.PT_TITLE);
				if (otitle instanceof String) {
					title = getUniqueName( null, null, (String) otitle);
				} else {
					title = getUniqueName( null, new HashWrapper( hash ), file.getFile().getName());
				}
					
				contentItem	item = new contentItem( downloads_container, content_file, hash, title );
								
				addToFilters( item );
				
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
	}
	
	protected void
	removeContent(
		ContentFile file )
	{
		removeContent( file.getFile());
	}
	
	protected void
	removeContent(
		DiskManagerFileInfo			file )
	{
		synchronized( lock ){
			
			try{
				byte[]	hash = file.getDownloadHash();
				
				String unique_name = getUniqueName( null, new HashWrapper( hash ), file.getFile().getName());
											
				content container = downloads_container.removeChild( unique_name );
	
				removeUniqueName( new HashWrapper( hash ));
				
				if ( container != null ){
					
					removeFromFilters( container );
				}
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
	}
	
	protected void
	removeContent(
		Download			download )
	{
		Torrent torrent = download.getTorrent();
		
		if ( torrent == null ){
			
			return;
		}
		
		HashWrapper hash = new HashWrapper(torrent.getHash());

		DiskManagerFileInfo[]	files = download.getDiskManagerFileInfo();

		synchronized( lock ){
			
			String	unique_name;
			
			if ( files.length == 1 ){

				unique_name = getUniqueName( download, hash, files[0].getFile().getName());
								
			}else{
				
				unique_name = getUniqueName( download, hash, download.getName());
			}
			
			content container = downloads_container.removeChild( unique_name );

			removeUniqueName( hash );
			
			if ( container != null ){
				
				removeFromFilters( container );
			}
		}
	}
	
	protected void
	invalidate()
	{
		root_container.invalidate();
	}
	
	private String
	findPrimaryContentType(
		content		con )
	{
		if ( con instanceof contentItem ){
			
			return(((contentItem)con).getContentClass());
			
		}else{
	
			String	type = CONTENT_UNKNOWN;
			
			contentContainer container = (contentContainer)con;
			
			List<content> kids = container.getChildren();
			
			for (int i=0;i<kids.size();i++){
				
				String	t = findPrimaryContentType(kids.get(i));
				
				if ( t == CONTENT_VIDEO ){
					
					return( t );
				}
				
				if ( t == CONTENT_AUDIO ){
					
					type = t;
					
				}else if ( t == CONTENT_IMAGE && type == CONTENT_UNKNOWN ){
					
					type = t;
					
				}
			}
			
			return( type );
		}
	}
	
	private void
	addToFilters(
		content		con )
	{
		String type = findPrimaryContentType( con );
		
		if ( media_server.useCategories() || media_server.useTags()){
			
			String[]	categories 	= media_server.useCategories()?con.getCategories():new String[0];
			String[]	tags		= media_server.useTags()?con.getTags():new String[0];
			
			if ( categories.length == 0 && tags.length == 0 ){
				
				if ( type == CONTENT_VIDEO ){
					
					movies_container.addLink( con );
					
				}else if ( type == CONTENT_AUDIO ){
					
					music_container.addLink( con );
			
				}else if ( type == CONTENT_IMAGE ){
					
					pictures_container.addLink( con );
				}
			}else{
				
				for ( String[] entry: new String[][]{ categories, tags }){
					
					for ( String cat_or_tag: entry ){
						
						contentContainer parent = null;
						
						if ( type == CONTENT_VIDEO ){
							
							parent = movies_container;
							
						}else if ( type == CONTENT_AUDIO ){
							
							parent = music_container;
					
						}else if ( type == CONTENT_IMAGE ){
							
							parent = pictures_container;
						}
										
						if ( parent != null ){
							
							content node = null;
		
							int		num = 0;
							String	name;
							
							while( 	(! ( node instanceof contentContainer )) ||
									((contentContainer)node).getACD() != null ){
																			
								name = num++==0?cat_or_tag:( num + ". " + cat_or_tag );
									
								node = parent.getChild( name );
								
								if ( node == null ){
									
									node = new contentContainer( parent, name );
								}
							}
							
							((contentContainer)node).addLink( con );
						}
					}
				}
			}
		}else{
			
			if ( type == CONTENT_VIDEO ){
				
				movies_container.addLink( con );
				
			}else if ( type == CONTENT_AUDIO ){
				
				music_container.addLink( con );
		
			}else if ( type == CONTENT_IMAGE ){
				
				pictures_container.addLink( con );
			}
		}
	}
	
	private void
	removeFromFilters(
		content		con )
	{
		contentContainer[] parents = { movies_container, pictures_container, music_container };
		
		for ( contentContainer parent: parents ){
			
			parent.removeLink( con.getName());
			
			if ( media_server.useCategories() || media_server.useTags()){
				
				List<content> kids = parent.getChildren();
				
				for ( content k: kids ){
					
					if ( k instanceof contentContainer && ((contentContainer)k).getACD() == null ){
						
						((contentContainer)k).removeLink( con.getName());
					}
				}
			}
		}
	}
	
	protected void
	contentChanged(
		ContentFile acf )
	{
		if ( media_server.useCategories() || media_server.useTags()){

			synchronized( lock ){
			
				contentChanged( downloads_container, acf );
			}
		}
	}
	
	protected boolean
	contentChanged(
		contentContainer	content,
		ContentFile acf )
	{			
		for ( content c: content.getChildren()){
			
			if ( c instanceof contentItem ){
				
				if (((contentItem)c).getACF() == acf ){
				
					removeFromFilters( c );
					
					addToFilters( c );
					
					return( true );
				}		
			}else{
				
				if ( contentChanged((contentContainer)c, acf )){
					
					return( true );
				}
			}
		}
		
		return( false );
	}
	
	private Map<HashWrapper,String> unique_name_map	= new HashMap<HashWrapper, String>();
	private Set<String> 			unique_names 	= new HashSet<String>();
	
	protected String
	getUniqueName(
		Download		dl,
		HashWrapper		hw,
		String			name )
	{
		synchronized( unique_names ){
			
			String result = hw == null ? null : (String)unique_name_map.get( hw );
	
			if ( result != null ){
				
				return( result );
			}
				// ensure that we have a unique name for the download
			
			result = dl==null?null:dl.getAttribute( ta_unique_name );
			
			if ( result == null || result.length() == 0 ){
				
				result = name;
			}
			
			int	num = 1;
			
			while( unique_names.contains( result )){
				
				result = (num++) + ". " + name; 
			}
		
				// if we had to make one up, record it persistently
			
			if ( num > 1 && dl != null ){
			
				dl.setAttribute( ta_unique_name, result );
			}
			
			unique_names.add( result );
			
			if (hw != null) {
				unique_name_map.put( hw, result );
			}
			
			return( result );
		}
	}
	
	protected void
	removeUniqueName(
		HashWrapper			hash )
	{
		synchronized( unique_names ){
			
			String name = unique_name_map.remove( hash );
			
			if ( name != null ){
				
				unique_names.remove( name );
			}
		}
	}
	
	
	protected void
	ensureStarted()
	{
		media_server.ensureStarted();
	}
	
	protected contentContainer
	getRootContainer()
	{
		ensureStarted();
		
		return( root_container );
	}
	
	protected contentContainer
	getMoviesContainer()
	{
		ensureStarted();
		
		return( movies_container );
	}
	
	protected contentContainer
	getMusicContainer()
	{
		ensureStarted();
		
		return( music_container );
	}
	
	protected contentContainer
	getPicturesContainer()
	{
		ensureStarted();
		
		return( pictures_container );
	}
	
	protected content
	getContentFromID(
		int		id )
	{
		ensureStarted();
		
		synchronized( content_map ){
			
			return( content_map.get( new Integer( id )));
		}
	}
	
	protected contentContainer
	getContainerFromID(
		int		id )
	{
		ensureStarted();
		
		synchronized( content_map ){
			
			content c = content_map.get( new Integer( id ));
			
			if ( c instanceof contentContainer ){
				
				return((contentContainer)c);
			}
		}
		
		return( null );
	}
		
	protected contentItem
	getContentFromResourceID(
		String		id )
	{
		return( getContentFromResourceIDSupport( id, false ));
	}
	
	protected contentItem
	getContentFromResourceIDSupport(
		String		id,
		boolean		is_peek )
	{
		if ( !is_peek ){
			
			ensureStarted();
		}

		int pos = id.indexOf( "." );
		
		if ( pos != -1 ){
			
			id = id.substring( 0, pos );
		}

		content content = resourcekey_map.get(id);
		if ( content instanceof contentItem ) {
			return (contentItem) content;
		}

		return null;
	}
	
	protected contentItem
	getContentFromHash(
		byte[]		hash )
	{
		ensureStarted();
		
		contentItem	item = getExistingContentFromHash( hash );
		
		if ( item == null ){
			
			ContentDirectory[]	directories = media_server.getAzureusContentDirectories();
			
			Map<String,byte[]>	lookup = new HashMap<String,byte[]>();
			
			lookup.put( ContentDirectory.AT_BTIH, hash );
			
			for (int i=0;i<directories.length;i++){
				
				ContentDirectory directory = directories[i];
				
				Content content = directory.lookupContent( lookup );
				
				if ( content != null ){
					
					Torrent	torrent = content.getTorrent();
					
					if ( torrent != null ){
						
						DownloadManager	dm = media_server.getPluginInterface().getDownloadManager();
						
							// hmm, when to resume...
						
						dm.pauseDownloads();
						
						try{
							Download download = dm.addDownload( torrent );
							
							addContent( download );
									
							item = getExistingContentFromHash( hash );

							int	sleep 	= 100;
							int	max		= 20000;
							
								// need to wait for things to get started else file might not
								// yet exist and we bork
							
							for (int j=0;j<max/sleep;j++){
								
								int	state = download.getState();
								
								if ( 	state == Download.ST_DOWNLOADING || 
										state == Download.ST_SEEDING ||
										state == Download.ST_ERROR ||
										state == Download.ST_STOPPED ){
									
									break;
								}
								
								Thread.sleep(sleep);
							}
							
							break;
							
						}catch( Throwable e ){
							
							log( "Failed to add download", e );
						}
					}
				}
			}
		}
		
		return( item );
	}
	
	protected ContentFile
	getACF(
		final DiskManagerFileInfo		file )
	{
		try{
			byte[] hash = file.getDownloadHash();
			
			ContentDirectory[]	directories = media_server.getAzureusContentDirectories();
				
			Map<String,Object>	lookup = new HashMap<String,Object>();
				
			lookup.put( ContentDirectory.AT_BTIH, hash );
			lookup.put( ContentDirectory.AT_FILE_INDEX, new Integer( file.getIndex()));
				
			for (int i=0;i<directories.length;i++){
					
				ContentDirectory directory = directories[i];
					
				ContentFile acf = directory.lookupContentFile( lookup );
				
				if ( acf != null ){
					
					return( acf );
				}
			}
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
		}
		
		return( 
				new ContentFile()
				{
					@Override
					public DiskManagerFileInfo
					getFile()
					{
						return( file );
					}
					
					@Override
					public Object
					getProperty(
						String		name )
					{
						return( null );
					}
				});
	}
	
	protected ContentDownload
	getACD(
		final Download		download )
	{
		try{
			byte[] hash = download.getTorrent().getHash();
		
			ContentDirectory[]	directories = media_server.getAzureusContentDirectories();
				
			Map<String,Object>	lookup = new HashMap<String,Object>();
				
			lookup.put( ContentDirectory.AT_BTIH, hash );
				
			for (int i=0;i<directories.length;i++){
					
				ContentDirectory directory = directories[i];
					
				ContentDownload acf = directory.lookupContentDownload( lookup );
				
				if ( acf != null ){
					
					return( acf );
				}
			}
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
		}
		
		return( 
				new ContentDownload()
				{
					@Override
					public Download
					getDownload()
					{
						return( download );
					}
					
					@Override
					public Object
					getProperty(
						String		name )
					{
						return( null );
					}
				});
	}
	
	protected contentItem
	getExistingContentFromHash(
		byte[]		hash )
	{
		ensureStarted();
		
		return( getExistingContentFromHashAndFileIndex( hash, 0 ));
	}
	
	protected contentItem
	getExistingContentFromHashAndFileIndex(
		byte[]		hash,
		int			file_index )
	{
		ensureStarted();
		
		String resourceKey = UPnPMediaServer.getContentResourceKey(hash, file_index);
		content content = resourcekey_map.get(resourceKey);
		if ( content instanceof contentItem ) {
			return (contentItem) content;
		}

		return( null );
	}
	
	protected String
	getDIDL(
		content						con,
		String						host,
		int							client_type,
		List<ContentFilter>	filters,
		Map<String,Object>			filter_args )	
	{
		if ( con instanceof contentContainer ){
			
			contentContainer	child_container = (contentContainer)con;
			
			List<content> kids = child_container.getChildren();
			
			int		child_count;
			long	storage_used;
			
			if ( filters.size() == 0 ){
				
				child_count 	= kids.size();
				storage_used	= child_container.getStorageUsed();
			}else{
			
				child_count 	= 0;
				storage_used	= 0;
				
				for ( content kid: kids ){
					
					if ( media_server.isVisible( kid, filters, filter_args )){
						
						child_count++;
						storage_used	+= kid.getStorageUsed();
					}
				}
			}
			
			
			String didl = 
				"<container id=\"" + child_container.getID() + "\" parentID=\"" + child_container.getParentID() + "\" childCount=\"" + child_count + "\" restricted=\"false\" searchable=\"true\">" +
				
					"<dc:title>" + escapeXML(child_container.getName()) + "</dc:title>" +
					"<upnp:class>" + CONTENT_CONTAINER + "</upnp:class>" +
					"<upnp:storageUsed>" + storage_used + "</upnp:storageUsed>" +
					"<upnp:writeStatus>WRITABLE</upnp:writeStatus>";
			if (child_container.mediaClass != null) {
				didl += "<av:mediaClass xmlns:av=\"urn:schemas-sony-com:av\">" + child_container.mediaClass + "</av:mediaClass>";
			}
			didl += "</container>";
			return didl;
		
		}else{
			contentItem	child_item = (contentItem)con;
			
			return( 
				"<item id=\"" + child_item.getID() + "\" parentID=\"" + child_item.getParentID() + "\" restricted=\"false\">" +
					child_item.getDIDL( host, client_type ) + 
				"</item>" );
		}
	}
	

	protected int
	getSystemUpdateID()
	{
		return( system_update_id );
	}
	
	protected String
	escapeXML(
		String	str )
	{
		return( media_server.escapeXML(str));
	}

	protected int
	getPersistentContainerID(
		String		name )
	{
		synchronized( lock ){

			Long res = readConfig().get( name );
			
			if ( res != null ){
				
				return( res.intValue());
			}
		}
		
		return( -1 );
	}
	
	protected void
	persistentContainerIDAdded()
	{
		synchronized( lock ){
			
			if ( config_dirty  ){
				
				return;
			}
			
			config_dirty = true;
			
			new DelayedEvent( 
				"UPnPMS:CD:dirty",
				10*1000,
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						writeConfig();
					}
				});
		}
	}
	
	protected Map<String,Long>
	readConfig()
	{
		synchronized( lock ){

			if ( config != null ){
				
				return( config );
			}
			
			File file = media_server.getPluginInterface().getPluginconfig().getPluginUserFile( "cd.dat" );
			
			// System.out.println( "Reading " + file );
			
			Map<String,Object> map = FileUtil.readResilientFile( file );
			
			Map<String,Long> id_map2 = (Map<String,Long>)map.get( "id_map2" );
			
			if ( id_map2 != null ){
				
				config = new HashMap<String, Long>();
				
				try{					
					for ( Map.Entry<String,Long>	entry: id_map2.entrySet()){
						
						config.put( new String( Base32.decode( entry.getKey()), "UTF-8" ), entry.getValue());
					}
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}else{
				
				config = (Map<String,Long>)map.get( "id_map" );
			}
			
			if ( config == null ){
				
				config = new HashMap<String, Long>();
			}
			
			new DelayedEvent( 
				"UPnPMS:CD:dirty",
				30*1000,
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						synchronized( lock ){
							
							config = null;
						}
					}
				});
			
			return( config );
		}
	}
	
	protected void
	writeConfig()
	{
		synchronized( lock ){
			
			if ( !config_dirty ){
				
				return;
			}
			
			File file = media_server.getPluginInterface().getPluginconfig().getPluginUserFile( "cd.dat" );

			// System.out.println( "Writing " + file );

			Map<String,Object> map = new HashMap<String, Object>();
			
			Map<String,Long> old_id_map = new HashMap<String, Long>();
			
			addPersistentContainerIDs( old_id_map, root_container );
			
			// we can stop writing this potentially bad map as migration completed to id_map2
			// map.put( "id_map", old_id_map );
			
				// issues with non-ascii keys here...
			
			try{
				Map<String,Long> id_map2 = new HashMap<String, Long>();
				
				for ( Map.Entry<String,Long>	entry: old_id_map.entrySet()){
					
					id_map2.put( Base32.encode( entry.getKey().getBytes( "UTF-8" )), entry.getValue());
				}
				
				map.put( "id_map2", id_map2 );
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
			
			FileUtil.writeResilientFile( file, map );

			config_dirty = false;
		}
	}
	
	protected void
	addPersistentContainerIDs(
		Map<String,Long>		map,
		contentContainer		container )
	{
		String	full_name  = container.getFullName( container.getName());
		
		map.put( full_name, new Long(container.getID()));
		
		List<content> kids = container.getChildren();
		
		for ( content kid: kids ){
			
			if ( kid instanceof contentContainer ){
				
				addPersistentContainerIDs( map, (contentContainer)kid );
			}
		}
	}
	
	protected void
	destroy()
	{
		writeConfig();
	}
	
	protected void
	print()
	{
		root_container.print( "" );
	}
	protected void
	log(
		String	str )
	{
		media_server.log( str );
	}
	
	protected void
	log(
		String		str,
		Throwable 	e )
	{
		media_server.log( str, e );
	}
	
	protected abstract class
	content
		implements Cloneable
	{
		private int						id;
		private contentContainer		parent;
		
		protected
		content(
			contentContainer		_parent )
		{
			this( _parent, null );
		}
			
		protected
		content(
			contentContainer		_parent,
			String					_name )
		{
			parent	= _parent;
			
			int	existing_oid = -1;
			
			if ( _name != null ){
				
				String	full_name = getFullName( _name );
				
				existing_oid = getPersistentContainerID( full_name );
				
				// System.out.println( "Container: " + full_name + " -> " + existing_oid );
			}
					
			synchronized( content_map ){
								
					// root is always 0 whatever, set up for subsequent
				
				if ( next_oid == 0 ){
					
					id			= 0;
					next_oid 	= (int)( SystemTime.getCurrentTime()/1000 );
					
				}else{
				
					if ( existing_oid != -1 ){
						
						id = existing_oid;
						
					}else{
						
						id = next_oid++;
					}
				}

				Integer	i_id = new Integer( id );
				
				while( content_map.containsKey( i_id )){
					
					i_id = id = next_oid++;
					
					existing_oid = -1;
				}
				
				content_map.put( i_id, this );
			}
			
			if ( _name != null && existing_oid == -1 ){
				
				persistentContainerIDAdded();
			}
		}
		
		protected int
		getID()
		{
			return( id );
		}
		
		protected String
		getFullName(
			String		name )
		{
			String	full_name = name;
				
			contentContainer current = parent;
				
			while( current != null ){
				
				full_name = current.getName() + "::" + full_name;
				
				current = current.getParent();
			}
	
			return( full_name );
		}
		
		protected int
		getParentID()
		{
			if ( parent == null ){
				
				return( -1 );
				
			}else{
				
				return( parent.getID());
			}
		}
		
		/*
		protected void
		setParent(
			contentContainer	_parent )
		{
			parent	= _parent;
		}
		*/
		
		protected contentContainer
		getParent()
		{
			return( parent );
		}

		protected abstract content
		getCopy(
			contentContainer	parent );

		protected abstract String
		getName();
		
		protected abstract String
		getContentClass();
		
		protected abstract long
		getDateMillis();
		
		protected abstract String[]
		getCategories();
		
		protected abstract String[]
		getTags();
		
		protected void
		deleted(
			boolean	is_link )
		{
			if ( !is_link ){
			
				synchronized( content_map ){
				
					if ( content_map.remove( new Integer( id )) == null ){
						
						Debug.out( "Content item with id " + id + " not found" );
					}
				}
			}
		}
		
		protected void
		invalidate()
		{
		}
		
		protected abstract long
		getStorageUsed();
		
		protected abstract void
		print(
			String	indent );
	}
	
	protected class
	contentContainer
		extends content
	{
		private ContentDownload download;
		private String					name;
		private List<content>			children 	= new ArrayList<content>();
		private int						update_id	= random.nextInt( Integer.MAX_VALUE );
		private String mediaClass;
		
		protected
		contentContainer(
			contentContainer	_parent,
			String				_name )
		{
			super( _parent, _name );
			
			name	= _name;
			
			if ( _parent != null ){
				
				_parent.addChildz( this );
			}
		}
		
		protected
		contentContainer(
			contentContainer			_parent,
			ContentDownload _download,
			String						_name )
		{		
			super( _parent, _name );
			
			download	= _download;
			name		= _name;
			
			if ( _parent != null ){
				
				_parent.addChildz( this );
			}
		}
		
		public contentContainer(contentContainer _parent, String _name,
				String _mediaClass) {
			this(_parent, _name);
			mediaClass = _mediaClass;
		}

		protected ContentDownload
		getACD()
		{
			return( download );
		}
		
		@Override
		protected content
		getCopy(
			contentContainer	parent )
		{
			contentContainer copy = new contentContainer( parent, download, name );
			
			for (int i=0;i<children.size();i++){
				
				children.get(i).getCopy( copy );
			}
			
			return( copy );
		}
		
		protected void
		addLink(
			content		child )
		{
			//logger.log( "Container: adding link '" + child.getName() + "' to '" + getName() + "'" );
				
			child.getCopy( this );
									
			updated();
		}
		
		protected content
		removeLink(
			String	child_name )
		{
			//logger.log( "Container: removing link '" + child_name + "' from '" + getName() + "'" );

			Iterator<content>	it = children.iterator();
				
			while( it.hasNext()){
				
				content	con = it.next();
				
				String	c_name = con.getName();
				
				if ( c_name.equals( child_name )){
						
					it.remove();
						
					updated();
						
					con.deleted( true );
					
					return( con );
				}
			}
			
			return( null );
		}
		
		protected void
		addChildz(
			content		child )
		{
			//logger.log( "Container: adding child '" + child.getName() + "' to '" + getName() + "'" );
						
			children.add( child );
			
			updated();
		}
		
		protected content
		removeChild(
			String	child_name )
		{
			//logger.log( "Container: removing child '" + child_name + "' from '" + getName() + "'" );

			Iterator<content>	it = children.iterator();
				
			while( it.hasNext()){
				
				content	con = it.next();
				
				String	c_name = con.getName();
				
				if ( c_name.equals( child_name )){
						
					it.remove();
						
					updated();
						
					con.deleted( false );
					
					return( con );
				}
			}
			
			log( "    child not found" );
			
			return( null );
		}
		
		protected content
		getChild(
			String	child_name )
		{
			Iterator<content>	it = children.iterator();
			
			while( it.hasNext()){
				
				content	con = it.next();
				
				String	c_name = con.getName();
				
				if ( c_name.equals( child_name )){

					return( con );
				}
			}
			
			return( null );
		}
		
		protected void
		updated()
		{
			update_id++;
		
			if ( update_id < 0 ){
				
				update_id = 0;
			}
			
			media_server.contentContainerUpdated( this );

			if ( getParent() != null ){
					
				getParent().updated();
				
			}else{
				
				system_update_id++;
				
				if ( system_update_id < 0 ){
					
					system_update_id = 0;
				}
			}
		}
		
		@Override
		protected void
		invalidate()
		{
			update_id++;
			
			if ( update_id < 0 ){
				
				update_id = 0;
			}
			
			media_server.contentContainerUpdated( this );

			if ( getParent() == null ){
									
				system_update_id++;
				
				if ( system_update_id < 0 ){
					
					system_update_id = 0;
				}
			}
			
			Iterator<content>	it = children.iterator();
			
			while( it.hasNext()){

				content	con = it.next();
				
				con.invalidate();
			}
		}
		
		@Override
		protected void
		deleted(
			boolean	is_link )
		{
			super.deleted( is_link );
			
			Iterator<content>	it = children.iterator();
			
			while( it.hasNext()){

				it.next().deleted( is_link );
			}
		}
		
		@Override
		protected String
		getName()
		{
			return( name );
		}
		
		@Override
		protected String
		getContentClass()
		{
			return( CONTENT_CONTAINER );
		}
		
		protected List<content>
		getChildren()
		{
			return( children );
		}
		
		protected int
		getUpdateID()
		{
			return( update_id );
		}
		
		@Override
		protected long
		getStorageUsed()
		{
			long	res = 0;
			
			Iterator<content>	it = children.iterator();
			
			while( it.hasNext()){

				content	con = it.next();
				
				res += con.getStorageUsed();
			}
			
			return( res );
		}
		
		@Override
		protected long
		getDateMillis()
		{
			if ( download == null || children.size() == 0 ){
				
				return( 0 );
			}
			
			return( children.get(0).getDateMillis());
		}
		
		@Override
		protected String[]
		getCategories()
		{
			if ( download == null || children.size() == 0 ){
				
				return( new String[0] );
			}
			
			return( children.get(0).getCategories());
		}
		
		@Override
		protected String[]
		getTags()
		{
			if ( download == null || children.size() == 0 ){
				
				return( new String[0] );
			}
			
			return( children.get(0).getTags());
		}
		
		@Override
		protected void
		print(
			String	indent )
		{
			log( indent + name + ", id=" + getID());
			
			indent += "    ";
			
			Iterator<content>	it = children.iterator();
			
			while( it.hasNext()){

				content	con = it.next();

				con.print( indent );
			}
		}
	}
	
	protected class
	contentItem
		extends content
		implements Cloneable
	{
		private final ContentFile content_file;
		private final byte[]					hash;
		private final String					title;
		
		private boolean		valid;
				
		private String[]		content_types;
		private String		item_class;
		private String resource_key;
		
		protected 
		contentItem(
			contentContainer		_parent,
			ContentFile _content_file,
			byte[]					_hash,
			String					_title )
		{
			super( _parent );
			
			try{
				content_file	= _content_file;
				hash			= _hash;
				title			= StringInterner.intern( _title );
				
				DiskManagerFileInfo		file = content_file.getFile();
										
				String	file_name = file.getFile().getName();
				String extension = FileUtil.getExtension(file_name);
										
				if (extension.length() > 1) {
					
					String[]	entry = ext_lookup_map.get( extension.substring(1).toLowerCase( MessageText.LOCALE_ENGLISH ));
				
					if ( entry != null ){
						
						int num_types = entry.length - 2;
						
						content_types	= new String[num_types];
						System.arraycopy(entry, 1, content_types, 0, num_types);

						item_class		= entry[entry.length - 1];
						
						valid	= true;
					}
				}
				
				if ( !valid ){
					
					content_types	= new String[] { "unknown/unknown" };
					item_class		= CONTENT_UNKNOWN;
				}
				
				resource_key = UPnPMediaServer.getContentResourceKey(hash, content_file.getFile().getIndex());
				resourcekey_map.put(resource_key, this);
			}finally{
				
				_parent.addChildz( this );
			}
		}
		
		public String getResourceKey() {
			return resource_key;
		}
		
		@Override
		protected content
		getCopy(
			contentContainer	parent )
		{
			try{
				content res = (content)clone();
				
				res.parent = parent;
				
				parent.addChildz( res );
				
				return( res );
				
			}catch( Throwable e ){
				
				e.printStackTrace();
				
				return( null );
			}
		}
		
		protected ContentFile
		getACF()
		{
			return( content_file );
		}
		
		public DiskManagerFileInfo
		getFile()
		{
			return( content_file.getFile());
		}
				
		protected byte[]
		getHash()
		{
			return( hash );
		}
				
		protected String
		getTitle()
		{
			return( title );
		}
		
		protected String
		getDisplayTitle()
		{
			String t = getTitle();
			
			String str_percent = null;
			
			if ( media_server.showPercentDone()){
				
				long percent_done = getPercentDone();
				
				if ( percent_done >= 0 && percent_done < 1000 ){
					
					str_percent = DisplayFormatters.formatPercentFromThousands((int)percent_done);
				}
			}
			
			String	str_eta = null;
			
			if ( media_server.showETA()){
				
				long eta = getETA();
				
				if ( eta > 0 ){
				
					str_eta = TimeFormatter.format( eta );
				}
			}
			
			if ( str_percent != null || str_eta != null ){
				
				if ( str_percent == null ){
					
					t += " (" + str_eta + ")";
					
				}else if ( str_eta == null ){
					
					t += " (" + str_percent + ")";
					
				}else{
					
					t += " (" + str_eta + " - " + str_percent + ")";
				}
			}
			
			return( t );
		}
		
		protected String
		getCreator()
		{
			return( getStringProperty( ContentFile.PT_CREATOR, "Unknown" ));
		}
		
		protected long
		getSize()
		{
			return( getFile().getLength());
		}
		
		protected long
		getAverageBitRate()
		{
			long	duration = getDurationMillis();
			
			if ( duration <= 0 ){
				
				return( 0 );
			}
			
			long	size = getSize();
			
			if ( size <= 0 ){
				
				return( 0 );
			}
			
			return( size*8*1000/duration );
		}
		
		protected int
		getPercentDone()
		{
			return( (int)getLongProperty( ContentFile.PT_PERCENT_DONE, -1 ));
		}
		
		protected long
		getETA()
		{
			return( getLongProperty( ContentFile.PT_ETA, -1 ));
		}
		
		protected long
		getDurationMillis()
		{
			return( getLongProperty( ContentFile.PT_DURATION, 0 ));
		}
		
		protected String
		getDuration(
			long		def )
		{			
			long 	millis = getLongProperty( ContentFile.PT_DURATION, def );
			
				// default value of < 0 means return null if no value available
			
			if ( millis < 0 ){
				
				return( null );
			}
			
			long	secs = millis/1000;
			
			String	result = TimeFormatter.formatColon( secs );
			
			String ms = String.valueOf( millis%1000 );
			
			while( ms.length() < 3 ){
				
				ms = "0" + ms;
			}
			
			return( result + "." + ms );
		}

		protected String
		getResolution()
		{
			long 	width 	= getLongProperty( ContentFile.PT_VIDEO_WIDTH, 0 );
			long 	height 	= getLongProperty( ContentFile.PT_VIDEO_HEIGHT, 0 );

			if ( width > 0 && height > 0 ){
				
				return( width + "x" + height );
			}
			
				// default
			
			return( "640x480" );
		}
		
		@Override
		protected long
		getDateMillis()
		{
			return( getLongProperty( ContentFile.PT_DATE, 0 ));
		}
		
		protected String
		getDate()
		{
			long date_millis = getDateMillis();
			
			if ( date_millis == 0 ){
				
				return( null );
			}
			
			return( upnp_date_format.format(new Date( date_millis )));
		}
		
		@Override
		protected String[]
		getCategories()
		{
			try{
				String[] cats = (String[])content_file.getProperty( ContentFile.PT_CATEGORIES );
				
				if ( cats != null ){
					
					return( cats );
				}
			}catch( Throwable e ){
			}
			
			return( new String[0] );
		}
			
		@Override
		protected String[]
		getTags()
		{
			try{
				String[] tags = (String[])content_file.getProperty( ContentFile.PT_TAGS );
				
				if ( tags != null ){
					
					return( tags );
				}
			}catch( Throwable e ){
			}
			
			return( new String[0] );
		}
		
		protected String
		getProtocolInfo(
			String content_type,
			String	attributes )
		{
			return( "http-get:*:" + content_type + ":" + attributes );
		}
		
		protected String
		getURI(
			String	host,
			int		stream_id )
		{
			return UPnPMediaServer.getContentResourceURI(getFile(), host, 
					media_server.getContentServer().getPort(), stream_id);
		}
		
		protected String
		getResources(
			String		host,
			int			client_type )
		{
			String res = getResource( host, client_type, "*" );
			
			if ( client_type != CT_XBOX ){
				
				try{
					String attr = null;

					DiskManagerFileInfo		file = content_file.getFile();
					
					String	file_name = file.getFile().getName();

					String	lc_fn = file_name.toLowerCase();
					
					if ( item_class.equals( CONTENT_VIDEO )){
										
						if ( lc_fn.endsWith( ".vob" ) || lc_fn.endsWith( ".mpeg" ) || lc_fn.endsWith( ".mpg" )){
					
							attr = "DLNA.ORG_PN=MPEG_PS_NTSC;DLNA.ORG_OP=01;DLNA.ORG_CI=1;DLNA.ORG_FLAGS=01700000000000000000000000000000";							
						} else if (lc_fn.endsWith("mkv")) {
							attr = "DLNA.ORG_PN=MATROSKA;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01500000000000000000000000000000";
						} else if (lc_fn.endsWith("avi")) {
							attr = "DLNA.ORG_PN=AVI;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01500000000000000000000000000000";
						} else if (lc_fn.endsWith("wmv")) {
							attr = "DLNA.ORG_PN=WMVHIGH_FULL;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01500000000000000000000000000000";
						}
					}else if ( item_class.equals( CONTENT_AUDIO )){
												
						if ( lc_fn.endsWith( ".mp3" )){
							
							attr = "DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000";
							
						}else if ( lc_fn.toLowerCase().endsWith( ".wma" )){
							
							attr = "DLNA.ORG_PN=WMABASE;DLNA.ORG_OP=11;DLNA.ORG_FLAGS=01700000000000000000000000000000";
						}
			
					}else if ( item_class.equals( CONTENT_IMAGE )){
													
						if ( lc_fn.endsWith( ".jpg" ) || lc_fn.endsWith( ".jpeg" )){
							
							attr = "DLNA.ORG_PN=JPEG_MED;DLNA.ORG_CI=1;DLNA.ORG_FLAGS=00f00000000000000000000000000000";
							
						}else if ( lc_fn.endsWith( ".png" )){
							
							attr = "DLNA.ORG_PN=PNG_LRG;DLNA.ORG_CI=1;DLNA.ORG_FLAGS=00f00000000000000000000000000000";
								
						}else if ( lc_fn.endsWith( ".gif" )){
							
							attr = "DLNA.ORG_PN=GIF_LRG;DLNA.ORG_CI=1;DLNA.ORG_FLAGS=00f00000000000000000000000000000";
						}
					}
					
					if ( attr != null ){
						
						res += getResource( host, client_type, attr );
					}
				}catch( Throwable e ){
					
				}
			}
			
			return( res );
		}
		
		protected String
		getResource(
			String		host,
			int			client_type,
			String		protocol_info_attributes )
		{
			String	resource = "";

			for (String content_type : content_types) {
			LinkedHashMap<String,String>	attributes = new LinkedHashMap<String,String>();
			
				
			attributes.put( "protocolInfo", getProtocolInfo( content_type, protocol_info_attributes ));
			attributes.put( "size", String.valueOf( getFile().getLength()));
			
			if ( item_class.equals( CONTENT_VIDEO )){

					// hack for XBOX as it always needs a duration
				
				String duration_str = getDuration( client_type==CT_XBOX?1*60*1000:-1 );
				
				if ( duration_str != null ){
				
					attributes.put( "duration", duration_str );
				}
				
				attributes.put( "resolution", getResolution());

			}else if ( item_class.equals( CONTENT_AUDIO )){

					// hack for XBOX as it always needs a duration
					
				String duration_str = getDuration( client_type==CT_XBOX?1*60*1000:-1 );
				
				if ( duration_str != null ){
				
					attributes.put( "duration", duration_str );
				}
			}
			
				// experiment
			
			/*
			if ( item_class.equals( CONTENT_VIDEO )){
				attributes.put( "duration", "00:02:00.000" );
				attributes.put( "resolution", "480x270" );
				attributes.put( "bitsPerSample", "16" );
				attributes.put( "nrAudioChannels", "2" );
				attributes.put( "bitrate", "77355" );
				attributes.put( "sampleFrequency", "44100" );
			}
			*/
			
			if ( client_type == CT_XBOX ){
				
				if ( item_class.equals( CONTENT_VIDEO )){
				
					attributes.put( "bitrate", "500000" );
					
				}else if ( item_class.equals( CONTENT_AUDIO )){
					
					attributes.put( "nrAudioChannels", "2" );
					attributes.put( "bitrate", "4000" );
					attributes.put( "bitsPerSample", "16" );
					attributes.put( "sampleFrequency", "44100" );
					
				}else if ( item_class.equals( CONTENT_IMAGE )){
					
					attributes.put( "resolution", "100x100" );
				}
			}
			
			resource += "<res ";
			
			Iterator<String>	it = attributes.keySet().iterator();
			
			while( it.hasNext()){
				
				String	key = it.next();
				
				resource += key + "=\"" + attributes.get(key) + "\"" + (it.hasNext()?" ":"");
			}
			
			resource += ">" + getURI( host, -1 ) + "</res>";
			}
			
			return( resource );
		}
		
		protected String
		getDIDL(
			String		host,
			int			client_type )
		{
				// for audio: dc:date 2005-11-07
				//			upnp:genre Rock/Pop
				//			upnp:artist
				// 			upnp:album
				//			upnp:originalTrackNumber
				
			String	hacked_class;
			
			if ( client_type == CT_XBOX ){
				
				if ( item_class.equals( CONTENT_VIDEO )){
					
					hacked_class = XBOX_CONTENT_VIDEO;
					
				}else if ( item_class.equals( CONTENT_AUDIO )){
					
					hacked_class = XBOX_CONTENT_AUDIO;
					
				}else if ( item_class.equals( CONTENT_IMAGE )){
					
					hacked_class = XBOX_CONTENT_IMAGE;
					
				}else{
					
					hacked_class = item_class;
				}
			}else{
				
				hacked_class = item_class;
			}
			
			String	didle = 
				"<dc:title>" + escapeXML( getDisplayTitle()) + "</dc:title>" +
				"<dc:creator>" +  escapeXML(getCreator()) + "</dc:creator>";
			// These two work for thumbnail on SonyBluRay
			//didle += "<res protocolInfo=\"http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_TN;DLNA.ORG_OP=00;DLNA.ORG_CI=1;DLNA.ORG_FLAGS=00D00000000000000000000000000000\" >http://192.168.2.62:65246/Vuze120x120.jpg</res>";
			//didle += "<upnp:albumArtURI xmlns:dlna=\"urn:schemas-dlna-org:metadata-1-0/\" dlna:profileID=\"JPEG_TN\"></upnp:albumArtURI>";
			
			String date = getDate();
			
			if ( date != null ){
				
				didle += "<dc:date>" + date + "</dc:date>";
			}
			
			didle +=
				
				"<upnp:class>" + hacked_class + "</upnp:class>" +
				//"<upnp:icon>" + escapeXML( "http://" + host + ":" + media_server.getContentServer().getPort() + "/blah" ) + "</upnp:icon>" +
				//"<albumArtURI>" + escapeXML( "http://" + host + ":" + media_server.getContentServer().getPort() + "/blah" ) + "</albumArtURI>" +
				getResources( host, client_type );
			
			if ( client_type == CT_XBOX ){
				
				if ( item_class.equals( CONTENT_AUDIO )){
					
					didle += 	"<upnp:genre>Unknown</upnp:genre>" +
								"<upnp:artist>Unknown</upnp:artist>" +
								"<upnp:album>Unknown</upnp:album>";
				}
			}

			return( didle );
		}
		
		@Override
		protected String
		getName()
		{
			return( getTitle());
		}
		
		@Override
		protected String
		getContentClass()
		{
			return( item_class );
		}
		
		protected String[]
		getContentTypes()
		{
			return( content_types );
		}
		
		@Override
		protected void
		deleted(
			boolean	is_link )
		{
			super.deleted( is_link );
			resourcekey_map.remove(resource_key);
		}
		
		@Override
		protected long
		getStorageUsed()
		{
			return( getFile().getLength());
		}
		
		protected String
		getStringProperty(
			String		name,
			String		def )
		{
			String	result = (String)content_file.getProperty( name );
			
			if ( result == null ){
				
				result	= def;
			}
			
			return( result );
		}
		
		protected long
		getLongProperty(
			String		name,
			long		def )
		{
			Long	result = (Long)content_file.getProperty( name );
			
			if ( result == null ){
				
				result	= def;
			}
			
			return( result );
		}
		
		@Override
		protected void
		print(
			String	indent )
		{
			log( indent + getTitle() + ", id=" + getID() + ", class=" + item_class + ", type=" + Arrays.toString(content_types) );
		}
	}

}
