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

import com.biglybt.core.util.SystemTime;

class
RecentlyRemovedData
{
	private final long			id;
	private final long			create_time = SystemTime.getMonotonousTime();
	//private final Set<String>	sessions = new HashSet<String>();
	
	RecentlyRemovedData(
		long		_id )
	{
		id	= _id;
	}
	
	long
	getID()
	{
		return( id );
	}
	
	long
	getCreateTime()
	{
		return( create_time );
	}
	
	boolean
	hasSession(
		String		session )
	{
		/*
		 * Actually it seems the webui doesn't consistently handle the removed-ids so just
		 * return the ID for a time period to ensure that it is processed.
		 * Update - might be that multiple clients in the same browser are using the same session id
		 * so going to go with reporting 'recently-removed' for a time period instead of just once
		 * per session 
		 * 
		synchronized( sessions ){
			
			if ( sessions.contains( session )){
				
				return( true );
				
			}else{
				
				sessions.add( session );
				
				return( false );
			}
		}
		*/
		return( false );
	}
}
