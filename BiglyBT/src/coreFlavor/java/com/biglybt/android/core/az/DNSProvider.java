/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
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
 */

package com.biglybt.android.core.az;

import androidx.annotation.Keep;

import java.net.*;
import java.util.*;

import org.xbill.DNS.*;

import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.DNSUtils;
import com.biglybt.core.util.DNSUtils.DNSUtilsIntf;
import com.biglybt.core.util.Debug;
import com.biglybt.util.Thunk;

/**
 * DNS calls using xbill
 * <p/>
 * Note: This is formatted similar to DNSUtilsImpl.java in core, for easy
 * diff comparison.<br/>
 * See https://github.com/BiglySoftware/BiglyBT/blob/master/core/src/com/biglybt/core/util/dns/DNSUtilsImpl.java
 */
@Keep
public class DNSProvider
	implements DNSUtilsIntf
{
	private final static int[]	REC_ALL = new int[]{ Type.A, Type.AAAA, Type.CNAME };
	private final static int[]	REC_V4 	= new int[]{ Type.A, Type.CNAME };
	private final static int[]	REC_V6 	= new int[]{ Type.AAAA, Type.CNAME };

	private final Map<String, Cache> cache_map = new HashMap<>(8);

	@Override
	public DNSUtils.DNSDirContext
	getInitialDirContext()

		throws Exception
	{
		return (new Context(null));
	}

	@Override
	public DNSUtils.DNSDirContext
	getDirContextForServer(
		String		dns_server_ip )

		throws Exception
	{
		return (new Context(dns_server_ip));
	}


	@Override
	public Inet6Address
	getIPV6ByName(
		String		host )

		throws UnknownHostException
	{
		List<Inet6Address>	all = getAllIPV6ByName( host );

		return( all.get(0));
	}

	@Override
	public Inet4Address
	getIPV4ByName(
		String		host )

		throws UnknownHostException
	{
		List<Inet4Address>	all = getAllIPV4ByName( host );

		return( all.get(0));
	}
	
	public List<Inet6Address>
	getAllIPV6ByName(
		String		host )

		throws UnknownHostException
	{
		try{
			List<InetAddress>	result = getAllByName( getInitialDirContext(), host, REC_V6 );

			if ( result.size() > 0 ){

				return((List<Inet6Address>)(Object)result );
			}
		}catch( Throwable e ){
		}

		throw( new UnknownHostException( host ));
	}

	public List<Inet4Address>
	getAllIPV4ByName(
		String		host )

		throws UnknownHostException
	{
		try{
			List<InetAddress>	result = getAllByName( getInitialDirContext(), host, REC_V4 );

			if ( result.size() > 0 ){

				return((List<Inet4Address>)(Object)result );
			}
		}catch( Throwable e ){
		}


		throw( new UnknownHostException( host ));
	}
	
	@Override
	public List<InetAddress>
	getAllByName(
		String		host )

		throws UnknownHostException
	{
		try{
			return( getAllByName( getInitialDirContext(), host ));

		} catch (UnknownHostException e) {

			throw (e);

		} catch (Throwable e) {

			throw (new UnknownHostException(
					host + ": " + Debug.getNestedExceptionMessage(e)));
		}
	}

	@Override
	public List<InetAddress>
	getAllByName(
		DNSUtils.DNSDirContext	context,
		String					host )

		throws UnknownHostException
	{
		List<InetAddress>	result = getAllByName( context, host, REC_ALL );
		
		if ( result.size() > 0 ){

			return( result );
		}

		throw( new UnknownHostException( host ));
	}

	private List<InetAddress>
	getAllByName(
		DNSUtils.DNSDirContext		context,
		String						host,
		int[]					attributes )

		throws UnknownHostException
	{
		if ( AENetworkClassifier.categoriseAddress( host ) != AENetworkClassifier.AT_PUBLIC ){
			
			throw( new UnknownHostException( host ));
		}
		
		List<InetAddress>	result = new ArrayList<>();
		
		getAllByNameSupport(context, host, attributes, 1, result);
		
		return( result );
	}
		
	private void
	getAllByNameSupport(
		DNSUtils.DNSDirContext		context,
		String						host,
		int[]					attributes,
		int							depth,
		List<InetAddress>			result )

		throws UnknownHostException
	{
		if ( depth > 16 ){
			
			return;
		}
		
		try{
			String server = ((Context) context).getServer();

			for (int attribute : attributes) {
				Lookup lookup = new Lookup(host, attribute);

				lookup.setResolver(new SimpleResolver(server));


				setCache(server, lookup);

				lookup.run();

				Record[] a_results = lookup.getAnswers();

				if (a_results == null) {
					continue;
				}

				for (Record r : a_results) {
					
					if (r instanceof CNAMERecord) {
						String value = ((CNAMERecord) r).getTarget().toString();
						getAllByNameSupport(context, value, attributes, depth+1, result);
					} else if (r instanceof ARecord) {
						result.add(((ARecord) r).getAddress());
					} else if (r instanceof AAAARecord) {
						result.add(((AAAARecord) r).getAddress());
					}
				}
			}
		}catch( Throwable e ){
			throw (new UnknownHostException(
					host + ": " + Debug.getNestedExceptionMessage(e)));
		}
	}

	@Override
	public List<String>
	getTXTRecords(
		String		query )
	{
		List<String>	result = new ArrayList<>();

		try{
			Lookup l = new Lookup(query, Type.TXT);

			setCache(null, l);

			l.run();

			Record[] records = l.getAnswers();

			if (records != null) {

				for (Record r : records) {

					TXTRecord txt = (TXTRecord) r;

					result.addAll((List<String>) txt.getStrings());
				}
			}
		}catch( Throwable e ){
		}

		return( result );
	}

	@Override
	public String
	getTXTRecord(
		String		query )

		throws UnknownHostException
	{
		try{
			Lookup l = new Lookup(query, Type.TXT);

			setCache(null, l);

			l.run();

			Record[] records = l.getAnswers();

			if (records != null) {

				for (Record r : records) {

					TXTRecord txt = (TXTRecord) r;

					List<String> strs = (List<String>) txt.getStrings();

					if (strs.size() > 0) {

						return (strs.get(0));
					}
				}
			}

			throw( new UnknownHostException( "DNS query returned no results" ));

		}catch( Throwable e ){

			throw( new UnknownHostException(
					query + ": " + Debug.getNestedExceptionMessage(e)));
		}
	}

	private void setCache(String server, Lookup lookup) {
		if (server != null) {

			Cache cache;

			synchronized (cache_map) {

				cache = cache_map.get(server);

				if (cache == null) {

					cache = new Cache();

					cache_map.put(server, cache);
				}
			}

			lookup.setCache(cache);
		}
	}

	@Thunk
	class Context
		implements DNSUtils.DNSDirContext
	{
		private final String server;

		@Thunk
		Context(String _server) {
			server = _server;
		}

		@Thunk
		String getServer() {
			return (server);
		}

		@Override
		public String getString() {
			return (server == null ? "<default>" : server); //NON-NLS
		}
	}
}
