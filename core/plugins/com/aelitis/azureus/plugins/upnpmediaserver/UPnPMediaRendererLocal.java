/*
 * Created on 16 Aug 2006
 * Created by Paul Gardner
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.azureus.plugins.upnpmediaserver;

import com.biglybt.core.util.Debug;
import com.biglybt.pif.ipc.IPCInterface;

import com.aelitis.azureus.plugins.upnpmediaserver.UPnPMediaServerContentDirectory.contentItem;

public class 
UPnPMediaRendererLocal 
	implements UPnPMediaRenderer
{
	private static int	next_id	= 0;
	
	private UPnPMediaServer		server;
	private IPCInterface		callback;
	private int					id;
	
	protected
	UPnPMediaRendererLocal(
		UPnPMediaServer	_server,
		IPCInterface	_callback )
	{
		server		= _server;
		callback	= _callback;
		
		synchronized( UPnPMediaRendererLocal.class ){
			
			id = next_id++;
		}
	}
	
	protected int
	getID()
	{
		return( id );
	}
	
	@Override
	public boolean
	isBusy()
	{
		return( false );
	}
	
	@Override
	public void
	play(
		UPnPMediaServerContentDirectory.contentItem		item,
		int												stream_id )
	{
		try{
			callback.invoke(
				"mediaServerPlay",
				new Object[]{ item.getURI( server.getLocalIP(), stream_id ), new Integer( item.getID()), new Integer( stream_id )} );
			
		}catch( Throwable e ){
			
			Debug.printStackTrace(e);
		}
	}
	
	@Override
	public void
	play(
			contentItem item, 
			int stream_id,
			UPnPMediaServerErrorListener error_listener) 
	{
		play(item, stream_id);
	}
	
	@Override
	public void
	destroy() 
	{		
	}
}
