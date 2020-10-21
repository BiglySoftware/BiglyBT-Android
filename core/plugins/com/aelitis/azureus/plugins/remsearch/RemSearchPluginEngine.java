/*
 * Created on Nov 7, 2008
 * Created by Paul Gardner
 * 
 * Copyright 2008 Vuze, Inc.  All rights reserved.
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


package com.aelitis.azureus.plugins.remsearch;

import java.util.*;

import com.biglybt.core.metasearch.Engine;
import com.biglybt.core.util.average.Average;
import com.biglybt.core.util.average.AverageFactory;

public abstract class 
RemSearchPluginEngine 
{
	protected static final int		ENGINE_HISTORY_SIZE				= 100;

	private long		success_count;
	private long		fail_count;
	
	private LinkedList<Boolean>	history = new LinkedList<Boolean>();

	private Average response_time_average = AverageFactory.MovingImmediateAverage( 100 );
	private long	latest_rta;
	
	public abstract String
	getName();
	
	public abstract String
	getUID();
	
	public abstract String
	getIcon();
	
	public abstract String
	getDownloadLinkCSS();
	
	public abstract int
	getSelectionState();
	
	public abstract int
	getSource();
	
	public abstract Engine
	getEngine();
	
	public long
	getSuccessCount()
	{
		return( success_count );
	}
	
	public long
	getFailureCount()
	{
		return( fail_count );
	}
	
	public long
	getResponseTimeAverage()
	{
		return( latest_rta );
	}
	
	protected LinkedList<Boolean>
	getHistory()
	{
		return( history );
	}
	
	protected void
	addHistory(
		boolean		ok,
		long		elapsed )
	{	
			// no sync required as caller always holds monitor
		
		history.addFirst( ok );
		
		if ( history.size() > ENGINE_HISTORY_SIZE ){
			
			history.removeLast();
		}
		
		if ( ok ){
			
			success_count++;
			
			if ( elapsed > 0 ){
			
				latest_rta = (long)response_time_average.update( elapsed );
			}
		}else{
			
			fail_count++;
		}
	}
}
