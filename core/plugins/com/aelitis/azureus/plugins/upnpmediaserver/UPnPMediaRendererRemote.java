/*
 * Created on 12-Apr-2006
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

import java.util.*;

import com.biglybt.net.upnp.*;
import com.biglybt.core.util.*;

public class 
UPnPMediaRendererRemote 
	implements UPnPMediaRenderer, UPnPRootDeviceListener
{
	private UPnPMediaServer		plugin;
	private UPnPRootDevice root;
	
	private UPnPService rendering_control;
	private UPnPService			connection_manager;
	private UPnPService			av_transport;
	
	private String	current_connection_id;
	private String	current_av_id = "0";
	
	private AESemaphore	action_sem	= new AESemaphore( "UPnPMediaRenderer:action", 1 );
	private String[] sinkProtocols;
	
	protected
	UPnPMediaRendererRemote(
		UPnPMediaServer	_plugin,
		UPnPRootDevice	_root )
	{
		plugin	= _plugin;
		root	= _root;
		
		log( "Found " + root.getDevice().getFriendlyName());
		
		UPnPService[]	services = root.getDevice().getServices();
		
		for (int i=0;i<services.length;i++){
			
			UPnPService	service = services[i];
			
				// SL50i seems to behave better if we force direct invokes
			
			service.setDirectInvocations( true );
			
			String	service_type = service.getServiceType();
						
			if ( service_type.equals( "urn:schemas-upnp-org:service:RenderingControl:1" )){
				
				log( "    found RenderingControl" );
				
				rendering_control = service;
				
			}else if ( service_type.equals( "urn:schemas-upnp-org:service:ConnectionManager:1" )){
				
				log( "    found ConnectionManager" );
				
				connection_manager = service;
		
				fillSinkProtocolList();
				
			}else if ( service_type.equals( "urn:schemas-upnp-org:service:AVTransport:1" )){
				
				log( "    found AVTransport" );
				
				av_transport = service;
			}
		}
		
		root.addListener( this );
	}
	
	private void fillSinkProtocolList() {
		try{
			action_sem.reserve();
		
			UPnPAction info = connection_manager.getAction( "GetProtocolInfo" );
			
			UPnPActionInvocation invoke = info.getInvocation();
			
			Map	res = invoke.invoke2();
			
			String	sink 	= (String)res.get( "Sink" );
			//String	source 	= (String)res.get( "Source" );
			
			sinkProtocols = sink.split(",");

		}catch( Throwable e ){
			
			sinkProtocols = new String[0];
			
			log( "getSupportedSourceProtocols failed: " + e.getMessage());
			
			Debug.printStackTrace(e);
			
		}finally{
			
			action_sem.release();
		}
		
	}

	protected UPnPRootDevice
	getDevice()
	{
		return( root );
	}
	
	@Override
	public void
	lost(
		UPnPRootDevice	root,
		boolean			replaced )
	{
		destroy();
	}

	@Override
	public void
	play(
		final UPnPMediaServerContentDirectory.contentItem	item,
		final int											stream_id )
	{
		play(item, stream_id, null);
	}

	@Override
	public void
	play(
		final UPnPMediaServerContentDirectory.contentItem	item,
		final int											stream_id,
		final UPnPMediaServerErrorListener error_listener)
	{
		new AEThread( "UPnPMediaRenderer:play", true )
		{
			@Override
			public void
			runSupport()
			{
				try {
					playSupport( item, stream_id );
				} catch (UPnPException e) {
					if (error_listener != null) {
						error_listener.upnpSoapException(e);
					}
				}
			}
		}.start();
	}
	
	protected void
	playSupport(
		UPnPMediaServerContentDirectory.contentItem		item,
		int												stream_id )
	throws UPnPException
	{
		try{
			action_sem.reserve();
			
			if ( current_connection_id != null ){
			
				try{
					log( "Getting status" );
					
					boolean	stop_required	= true;
					
					try{
						UPnPAction info = av_transport.getAction( "GetTransportInfo" );
						
						UPnPActionInvocation invoke = info.getInvocation();
						
						invoke.addArgument( "InstanceID", current_av_id );
	
						Map	res = invoke.invoke2();
						
						String	state 	= (String)res.get( "CurrentTransportState" );
						String	status	= (String)res.get( "CurrentTransportStatus" );
						
						stop_required	= !( state.equals("STOPPED") || state.equals( "NO_MEDIA_PRESENT" ));
					
						log( "    state=" + state + ", status = " + status );
						
					}catch( Throwable e ){
					}
					
					if ( stop_required ){
						
						log( "Stopping previous play" );
						
						try{
							UPnPAction stop = av_transport.getAction( "Stop" );
							
							UPnPActionInvocation invoke = stop.getInvocation();
							
							invoke.addArgument( "InstanceID", current_av_id );
		
							invoke.invoke();
						
						}catch( Throwable e ){
						}
					}
					
					log( "Completing previous connection" );

					try{
						UPnPAction	complete = connection_manager.getAction( "ConnectionComplete" );
	
						if ( complete != null ){
							
							UPnPActionInvocation invoke	= complete.getInvocation();
							
							invoke.addArgument( "ConnectionID", current_connection_id );
		
							invoke.invoke();
							
						}else{
							
							log( "    No complete action available" );
						}
					}catch( Throwable e ){
						
					}
					
				}finally{
					
					current_connection_id	= null;
					current_av_id			= null;
				}
			}
			
			String	name = root.getDevice().getFriendlyName();
			
			if ( connection_manager == null || av_transport == null ){
				
				log( "Can't play on '" + name + "' as no connection manager or av transport" );
				
				return;
			}
				
			log( "Preparing for connection to '" + name + "'" );
			
			UPnPAction	prepare = connection_manager.getAction( "PrepareForConnection" );
			
			if (prepare != null) {
				String[] contentTypes = item.getContentTypes();
				String contentType = calculateContentType(contentTypes);
				if (contentType != null) {
  				try {
      			UPnPActionInvocation	invoke = prepare.getInvocation();
      			
      			invoke.addArgument( "RemoteProtocolInfo", item.getProtocolInfo(contentType, "*" ));
      			invoke.addArgument( "PeerConnectionManager", "" );
      			invoke.addArgument( "PeerConnectionID", "-1" );
      			invoke.addArgument( "Direction", "Input" );
      
      			Map	res = invoke.invoke2();
      			
      			String	connection_id 	= (String)res.get( "ConnectionID" );
      			String	av_id			= (String)res.get( "AVTransportID" );
      			String	rcs_id			= (String)res.get( "RcsID" );
    
      			current_connection_id	= connection_id;
      			current_av_id			= av_id;
  				} catch (Exception e) {
  					log(e.toString());
  				}
				}
			} 
			if (current_connection_id == null) {
				UPnPAction actionConnectionIDs = connection_manager.getAction("GetCurrentConnectionIDs");
				if (actionConnectionIDs != null) {
					Map res = actionConnectionIDs.getInvocation().invoke2();
					
	  			String	connection_ids 	= (String)res.get( "ConnectionIDs" );

	  			if (connection_ids != null) {
	  				current_connection_id = connection_ids.split(",")[0]; 
	  			}
				}
			}

			if (current_connection_id == null) {
				current_connection_id = "0";
			}
			if (current_av_id == null) {
				current_av_id = "0";
			}

			
			log( "Setting transport URI" );

			UPnPAction set_uri = av_transport.getAction( "SetAVTransportURI" );
			
			UPnPActionInvocation invoke = set_uri.getInvocation();
			
			String	host = root.getLocalAddress().getHostAddress();
			
			invoke.addArgument( "InstanceID", current_av_id );
			invoke.addArgument( "CurrentURI", item.getURI( host, -1 ));
			//invoke.addArgument( "CurrentURIMetaData", "NOT_IMPLEMENTED");
			String didl = item.getDIDL(host, stream_id);
			invoke.addArgument(
					"CurrentURIMetaData",
					"&lt;DIDL-Lite xmlns=&quot;urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/&quot; xmlns:dc=&quot;http://purl.org/dc/elements/1.1/&quot; xmlns:upnp=&quot;urn:schemas-upnp-org:metadata-1-0/upnp/&quot;&gt;&lt;item id=&quot;0&quot;&gt;"
							+ plugin.escapeXML(didl) + "&lt;/item&gt;&lt;/DIDL-Lite&gt;");
			
			invoke.invoke();
			
			log( "Queueing" );

			UPnPAction play = av_transport.getAction( "Play" );
			
			invoke = play.getInvocation();
			
			invoke.addArgument( "InstanceID", current_av_id );
			invoke.addArgument( "Speed", "1" );

			invoke.invoke();

			log( "Playing" );
			
		}catch( Throwable e ){
			
			log( "Play operation failed: " + e.getMessage());
			
			Debug.printStackTrace(e);
			
			if (e instanceof UPnPException) {
				throw (UPnPException) e;
			}
			
		}finally{
			
			action_sem.release();
		}
	}
	
	public String[] getSupportedSourceProtocols() {
		if (sinkProtocols != null) {
			return sinkProtocols;
		}
		
		return new String[0];
	}
	
	@Override
	public boolean
	isBusy()
	{
		return( action_sem.getValue() == 0 );
	}
	
	@Override
	public void
	destroy()
	{
		root.removeListener( this );
		
		plugin.removeRenderer( this );
	}
	
	protected void
	log(
		String	str )
	{
		plugin.log( "Renderer: " + str );
	}

	public String calculateContentType(String[] contentTypes) {
		String[] protocols = getSupportedSourceProtocols();
		for (String type : contentTypes) {
			for (String protocol : protocols) {
				if (protocol.startsWith(type, 11)) {
					return type;
				}
			}
		}
		return null;
	}
}
