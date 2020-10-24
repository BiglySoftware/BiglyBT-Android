/*
 * Created on May 29, 2014
 * Created by Paul Gardner
 * 
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
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



package lbms.plugins.mldht.azureus;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.*;

import com.biglybt.core.util.BEncoder;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.SystemTime;

import com.biglybt.core.dht.transport.DHTTransportAlternativeContact;
import com.biglybt.core.dht.transport.DHTTransportAlternativeNetwork;
import com.biglybt.core.dht.transport.udp.impl.DHTUDPUtils;

public class 
AlternativeContactHandler 
{
	private DHTTransportAlternativeNetworkImpl		ipv4_net = new DHTTransportAlternativeNetworkImpl( DHTTransportAlternativeNetwork.AT_MLDHT_IPV4 );
	private DHTTransportAlternativeNetworkImpl		ipv6_net = new DHTTransportAlternativeNetworkImpl( DHTTransportAlternativeNetwork.AT_MLDHT_IPV6 );
	
	protected
	AlternativeContactHandler()
	{
		DHTUDPUtils.registerAlternativeNetwork( ipv4_net );
		DHTUDPUtils.registerAlternativeNetwork( ipv6_net );
	}
	
	protected void
	nodeAlive(
		InetSocketAddress	address )
	{
		if ( address.getAddress() instanceof Inet4Address ){
			
			ipv4_net.addAddress( address );
			
		}else{
			
			ipv6_net.addAddress( address );
		}
	}
	
	protected void
	destroy()
	{
		DHTUDPUtils.unregisterAlternativeNetwork( ipv4_net );
		DHTUDPUtils.unregisterAlternativeNetwork( ipv6_net );
	}
	
	private static class
	DHTTransportAlternativeNetworkImpl
		implements DHTTransportAlternativeNetwork
	{
		private static final int ADDRESS_HISTORY_MAX	= 32;
		
		private int	network;
		
		private LinkedList<Object[]>	address_history = new LinkedList<Object[]>();
			
		private
		DHTTransportAlternativeNetworkImpl(
			int			net )
		{
			network	= net;
		}
		
		@Override
		public int
		getNetworkType()
		{
			return( network );
		}
		
		private void
		addAddress(
			InetSocketAddress	address )
		{
			synchronized( address_history ){
				
				address_history.addFirst(new Object[]{  address, new Long( SystemTime.getMonotonousTime())});
				
				if ( address_history.size() > ADDRESS_HISTORY_MAX ){
					
					address_history.removeLast();
				}
			}
		}
		
		@Override
		public List<DHTTransportAlternativeContact>
		getContacts(
			int		max )
		{
			List<DHTTransportAlternativeContact> result = new ArrayList<DHTTransportAlternativeContact>( max );
			
			synchronized( address_history ){
				
				for ( Object[] entry: address_history ){
					
					result.add( new DHTTransportAlternativeContactImpl((InetSocketAddress)entry[0],(Long)entry[1]));
					
					if ( result.size() == max ){
						
						break;
					}
				}
			}
			
			return( result );
		}
		
		private class
		DHTTransportAlternativeContactImpl
			implements DHTTransportAlternativeContact
		{
			private final InetSocketAddress		address;
			private final int	 				seen_secs;
			private final int	 				id;
			
			private
			DHTTransportAlternativeContactImpl(
				InetSocketAddress		_address,
				long					seen )
			{
				address	= _address;
				
				seen_secs = (int)( seen/1000 );
				
				int	_id;
				
				try{
				
					_id = Arrays.hashCode( BEncoder.encode(getProperties()));
					
				}catch( Throwable e ){
					
					Debug.out( e );
					
					_id = 0;
				}
				
				id	= _id;
			}
			
			@Override
			public int
			getNetworkType()
			{
				return( network );
			}
			
			@Override
			public int
			getVersion()
			{
				return( 1 );
			}
			
			@Override
			public int
			getID()
			{
				return( id );
			}
			
			@Override
			public int
			getLastAlive()
			{
				return( seen_secs );
			}
			
			@Override
			public int
			getAge()
			{
				return(((int)( SystemTime.getMonotonousTime()/1000)) - seen_secs );
			}
			
			@Override
			public Map<String,Object>
			getProperties()
			{
				Map<String,Object>	properties = new HashMap<String, Object>();
				
				try{
					properties.put( "a", address.getAddress().getAddress());
					properties.put( "p", new Long( address.getPort()));
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
				
				return( properties );
			}
		}
	}
}
