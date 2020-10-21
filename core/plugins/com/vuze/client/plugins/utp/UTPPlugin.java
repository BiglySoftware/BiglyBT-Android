/*
 * Created on Aug 26, 2010
 * Created by Paul Gardner
 * 
 * Copyright 2010 Vuze, Inc.  All rights reserved.
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



package com.vuze.client.plugins.utp;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.aelitis.azureus.core.networkmanager.impl.utp.UTPConnectionManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.util.Debug;
import com.biglybt.net.udp.uc.PRUDPPacketHandler;
import com.biglybt.net.udp.uc.PRUDPPacketHandlerFactory;
import com.biglybt.net.udp.uc.PRUDPPrimordialHandler;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginListener;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.ui.config.BooleanParameter;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.IntParameter;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.plugin.upnp.UPnPMapping;
import com.biglybt.plugin.upnp.UPnPPlugin;

public class 
UTPPlugin
	implements Plugin
{
	private PluginInterface		plugin_interface;

	private boolean				logging_enabled;
	private LoggerChannel		log;

	private BooleanParameter	enabled_param;
		
	private UTPConnectionManager manager;
	
	private Handler				default_handler = new Handler();

	private Map<Integer, Handler>	extra_handlers = new ConcurrentHashMap<>();
	
	
	public void 
	initialize(
		PluginInterface _plugin_interface )
	{
		plugin_interface = _plugin_interface;
	
		log = plugin_interface.getLogger().getTimeStampedChannel( "uTP" );

		log.setDiagnostic();
		
		log.setForce(true);

		plugin_interface.getUtilities().getLocaleUtilities().integrateLocalisedMessageBundle( "com.vuze.client.plugins.utp.internat.Messages");

		BasicPluginConfigModel	config = plugin_interface.getUIManager().createBasicPluginConfigModel(ConfigSection.SECTION_PLUGINS, "utp.name" );
			
			// enable
		
		enabled_param = config.addBooleanParameter2( "utp.enabled", "utp.enabled", true );
		
		enabled_param.addListener(
			new com.biglybt.pif.ui.config.ParameterListener()
			{
				public void 
				parameterChanged(
					Parameter param )
				{
					checkEnabledState();
				}
			});

			// logging
		
		final BooleanParameter logging_param = config.addBooleanParameter2( "utp.logging.enabled", "utp.logging.enabled", false );
		
		logging_param.addListener(
			new com.biglybt.pif.ui.config.ParameterListener()
			{
				public void 
				parameterChanged(
					Parameter param )
				{
					logging_enabled = logging_param.getValue();
				}
			});
		
		enabled_param.addEnabledOnSelection( logging_param );
		
		logging_enabled = logging_param.getValue();
		
		manager = new UTPConnectionManager( this );

			// prefer uTP
		
		final BooleanParameter prefer_utp_param = config.addBooleanParameter2( "utp.prefer_utp", "utp.prefer_utp", true );
		
		prefer_utp_param.addListener(
			new com.biglybt.pif.ui.config.ParameterListener()
			{
				public void 
				parameterChanged(
					Parameter param )
				{
					manager.preferUTP(prefer_utp_param.getValue());
				}
			});
		
		manager.preferUTP(prefer_utp_param.getValue());
		
		enabled_param.addEnabledOnSelection( prefer_utp_param );

		if ( manager.getProviderVersion() > 1 ){
			
			final IntParameter recv_buff_size = config.addIntParameter2( "utp.recv.buff", "utp.recv.buff", UTPConnectionManager.DEFAULT_RECV_BUFFER_KB, 0, 5*1024 );
			final IntParameter send_buff_size = config.addIntParameter2( "utp.send.buff", "utp.send.buff", UTPConnectionManager.DEFAULT_SEND_BUFFER_KB, 0, 5*1024 );
			
			recv_buff_size.addListener(
					new com.biglybt.pif.ui.config.ParameterListener()
					{
						public void 
						parameterChanged(
							Parameter param )
						{
							manager.setReceiveBufferSize( recv_buff_size.getValue());
						}
					});
					
			send_buff_size.addListener(
					new com.biglybt.pif.ui.config.ParameterListener()
					{
						public void 
						parameterChanged(
							Parameter param )
						{
							manager.setSendBufferSize( recv_buff_size.getValue());
						}
					});
			
			manager.setReceiveBufferSize( recv_buff_size.getValue());
			manager.setSendBufferSize( send_buff_size.getValue());
		
			enabled_param.addEnabledOnSelection( recv_buff_size );
			enabled_param.addEnabledOnSelection( send_buff_size );
		}
		
		plugin_interface.addListener(
				new PluginListener()
				{

					private ParameterListener paramListener;

					public void
					initializationComplete()
					{
						paramListener = 
							new ParameterListener() {
							
							public void
							parameterChanged(
								String name ) 
							{
								checkEnabledState();
							}
						};
						
						COConfigurationManager.addWeakParameterListener(
							paramListener, 
							true,
							"TCP.Listen.Port", 
							"TCP.Listen.Port.Enable", 
							"TCP.Listen.AdditionalPorts" );
					}
										
					public void
					closedownInitiated()
					{
					}
					
					public void
					closedownComplete()
					{
					}
				});
	}
	
	public PluginInterface
	getPluginInterface()
	{
		return( plugin_interface );
	}
	
	private void
	checkEnabledState()
	{
		boolean	plugin_enabled	= enabled_param.getValue();
		
		boolean	tcp_enabled 	= COConfigurationManager.getBooleanParameter( "TCP.Listen.Port.Enable" );
		
		if ( tcp_enabled && plugin_enabled ){
			
			log( "Plugin is enabled: version=" + plugin_interface.getPluginVersion());
									
			int	port = COConfigurationManager.getIntParameter( "TCP.Listen.Port" );				

			if ( default_handler.getPort() != port ){
			
				default_handler.setPort( port );
			}
			
			List<Long> extra = (List<Long>)COConfigurationManager.getListParameter( "TCP.Listen.AdditionalPorts", new ArrayList<>());
			
			for ( Long l_p: extra ){
				
				int	p = l_p.intValue();
				
				if ( !extra_handlers.containsKey( p )){
					
					Handler h = new Handler();
					
					h.setPort( p );
					
					extra_handlers.put( p, h );
				}
			}
			
			manager.activate();
			
		}else{
			
			log( "Plugin is disabled" );
			
			default_handler.clear();
			
			for ( Handler h: extra_handlers.values()){
				
				h.clear();
			}
			
			extra_handlers.clear();
		}
	}
	
	public boolean
	send(
		int					local_port,
		InetSocketAddress	to,
		byte[]				buffer,
		int					length )
	{
		if ( length != buffer.length ){
			
			Debug.out( "optimise this" );
			
			byte[] temp = new byte[length];
			
			System.arraycopy(buffer, 0, temp, 0, length );
			
			buffer = temp;
		}
		
		if ( local_port == default_handler.getPort() || local_port == 0 ){
		
			return( default_handler.send( to, buffer ));
			
		}else{
			
			Handler handler = extra_handlers.get( local_port );
			
			if ( handler == null ){
				
				return( default_handler.send( to, buffer ));
				
			}else{
				
				return( handler.send( to, buffer ));
			}
		}
	}
	
	public void
	log(
		String		str )
	{
		if ( logging_enabled ){
		
			log.log( str );
		}
	}
	
	public void
	log(
		String		str,
		Throwable 	e )
	{		
		log.log( str, e );
		
		Debug.out( e );
	}
	
	private class
	Handler
	{
		int						port;
		
		PRUDPPacketHandler 		handler;
		PRUDPPrimordialHandler	handler_handler;

		UPnPMapping upnp_mapping;
		
		void
		setPort(
			int		_port )
		{
			port	= _port;
			
			if ( upnp_mapping != null ){
				
				upnp_mapping.destroy();
				
				upnp_mapping = null;
			}

			PluginInterface pi_upnp = plugin_interface.getPluginManager().getPluginInterfaceByClass( UPnPPlugin.class );
			
			if ( pi_upnp == null ){

				log( "UPnP plugin not found, can't map port" );
				
			}else{
				
				upnp_mapping = 
					((UPnPPlugin)pi_upnp.getPlugin()).addMapping( 
						plugin_interface.getPluginName(), 
						false, 
						port, 
						true );
				
				log( "UPnP mapping registered for port " + port );
			}
			
			if ( handler == null || port != handler.getPort()){
				
				if ( handler != null ){
					
					handler.removePrimordialHandler( handler_handler );
				}
					
				handler = PRUDPPacketHandlerFactory.getHandler( port );
				
				handler_handler = 
					new PRUDPPrimordialHandler()
					{
						public boolean
						packetReceived(
							DatagramPacket	packet )
						{
							return( manager.receive( port, (InetSocketAddress)packet.getSocketAddress(), packet.getData(), packet.getLength()));
						}
					};
				
				handler.addPrimordialHandler( handler_handler );		
			}
		}
		
		int
		getPort()
		{
			return( port );
		}
		
		boolean
		send(
			InetSocketAddress		to,
			byte[]					buffer )
		{
			try{
				handler.primordialSend( buffer, to );
				
				return( true );
				
			}catch( Throwable e ){
				
				return( false );
			}
		}
		
		void
		clear()
		{
			if ( handler != null ){
				
				handler.removePrimordialHandler( handler_handler  );
				
				handler = null;
			}
			
			if ( upnp_mapping != null ){
				
				upnp_mapping.destroy();
				
				upnp_mapping = null;
			}
			
			port = 0;
		}
	}
}
