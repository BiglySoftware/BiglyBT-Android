/*
 * Created on Aug 16, 2016
 * Created by Paul Gardner
 * 
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or 
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.plugins.rcmplugin;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.utils.search.SearchResult;

import com.biglybt.core.content.RelatedContent;
import com.biglybt.core.content.RelatedContentManager;

public class
SearchRelatedContent
	extends RelatedContent
{
	private int		rank;
	private boolean	unread	= true;
	
	protected
	SearchRelatedContent(
		SearchResult	sr )
	{
		super( 
			sr.getProperty(SearchResult.PR_VERSION )instanceof Long?((Long)sr.getProperty(SearchResult.PR_VERSION )).intValue():0,
			(String)sr.getProperty( SearchResult.PR_NAME ),
			(byte[])sr.getProperty( SearchResult.PR_HASH ),
			null,	// tracker
			(byte[])sr.getProperty( RelatedContentManager.RCM_SEARCH_PROPERTY_TRACKER_KEYS ),
			(byte[])sr.getProperty( RelatedContentManager.RCM_SEARCH_PROPERTY_WEB_SEED_KEYS ),
			(String[])sr.getProperty( RelatedContentManager.RCM_SEARCH_PROPERTY_TAGS ),
			convertNetworks((String[])sr.getProperty( RelatedContentManager.RCM_SEARCH_PROPERTY_NETWORKS )),
			(Long)sr.getProperty( SearchResult.PR_SIZE ),
			getDate( sr ),
			getSeedsLeechers( sr )
		);
		
		Long l_rank = (Long)sr.getProperty( SearchResult.PR_RANK );
		
		if ( l_rank != null ){
			
			rank = l_rank.intValue();
		}
	}
	
	protected void
	updateFrom(
		RelatedContent		other )
	{
		String[] old_tags 	= getTags();
		String[] new_tags	= other.getTags();
		
		if ( old_tags.length == 0 && new_tags.length == 0 ){
			
		}else if ( old_tags.length > 0 && new_tags.length == 0 ){
			
		}else if ( old_tags.length == 0 && new_tags.length > 0 ){
			
			setTags( new_tags );
			
		}else{
			
			Set<String>	tags = new HashSet<String>( Arrays.asList( old_tags ));
			
			tags.addAll( Arrays.asList( new_tags ));
			
			if ( tags.size() > old_tags.length ){
				
				setTags( tags.toArray( new String[ tags.size()]));
			}
		}
	}
	
	private static int
	getDate(
		SearchResult		sr )
	{
		Date date = (Date)sr.getProperty( SearchResult.PR_PUB_DATE );
		
		if ( date == null ){
			
			return(0);
		}
		
		return((int)(date.getTime()/(60*60*1000)));	
	}
	
	private static int
	getSeedsLeechers(
		SearchResult		sr )
	{
		int seeds 		= ((Long)sr.getProperty( SearchResult.PR_SEED_COUNT )).intValue();
		int leechers 	= ((Long)sr.getProperty( SearchResult.PR_LEECHER_COUNT )).intValue();
		
		return( seeds << 16 | leechers );
	}
	
	@Override
	public int
	getRank()
	{
		return( rank );
	}

	@Override
	public int
	getLevel() 
	{
		return( 0 );
	}
	
	@Override
	public boolean
	isUnread() 
	{
		return( unread );
	}
	
	@Override
	public void
	setUnread(
		boolean _unread )
	{
		unread	= _unread;
	}
	
	@Override
	public Download
	getRelatedToDownload()
	{
		return( null );
	}

	@Override
	public int
	getLastSeenSecs() 
	{
		return 0;
	}
	
	@Override
	public void
	delete() 
	{
	}
	
		// duplicated from RelatedContentManager - remove sometime!
		
	protected static final byte		NET_NONE	= 0x00;
	protected static final byte		NET_PUBLIC	= 0x01;
	protected static final byte		NET_I2P		= 0x02;
	protected static final byte		NET_TOR		= 0x04;
	
	protected static byte
	convertNetworks(
		String[]		networks )
	{
		byte	nets = NET_NONE;
	
		for ( int i=0;i<networks.length;i++ ){
			
			String n = networks[i];
			
			if (n.equalsIgnoreCase( AENetworkClassifier.AT_PUBLIC )){
				
				nets |= NET_PUBLIC;
				
			}else if ( n.equalsIgnoreCase( AENetworkClassifier.AT_I2P )){
				
				nets |= NET_I2P;
				
			}else if ( n.equalsIgnoreCase( AENetworkClassifier.AT_TOR )){
				
				nets |= NET_TOR;
			}
		}
		
		return( nets );
	}
}