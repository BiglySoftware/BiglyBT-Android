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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import org.xbill.DNS.*;

import com.biglybt.core.util.DNSUtils.DNSDirContext;
import com.biglybt.core.util.DNSUtils.DNSUtilsIntf;
import com.biglybt.core.util.Debug;
import com.biglybt.util.Thunk;

public class DNSProvider
	implements DNSUtilsIntf
{
	private final Map<String, Cache> cache_map = new HashMap<>(8);

	public DNSDirContext getInitialDirContext()

			throws Exception {
		return (new Context(null));
	}

	public DNSDirContext getDirContextForServer(String dns_server_ip)

			throws Exception {
		return (new Context(dns_server_ip));
	}

	public Inet6Address getIPV6ByName(String query)

			throws UnknownHostException {
		try {
			Lookup a6_l = new Lookup(query, Type.AAAA);

			setCache(null, a6_l);

			a6_l.run();

			Record[] a6_results = a6_l.getAnswers();

			if (a6_results != null) {

				for (Record r : a6_results) {

					AAAARecord a6_record = (AAAARecord) r;

					return ((Inet6Address) a6_record.getAddress());
				}
			}

			throw (new UnknownHostException(query));

		} catch (Exception e) {

			throw (new UnknownHostException(
					query + ": " + Debug.getNestedExceptionMessage(e)));
		}
	}

	public List<InetAddress> getAllByName(String query)

			throws UnknownHostException {
		try {
			return (getAllByName(getInitialDirContext(), query));

		} catch (UnknownHostException e) {

			throw (e);

		} catch (Throwable e) {

			throw (new UnknownHostException(
					query + ": " + Debug.getNestedExceptionMessage(e)));
		}
	}

	public List<InetAddress> getAllByName(DNSDirContext context, String query)

			throws UnknownHostException {
		List<InetAddress> result = new ArrayList<>(2);

		try {
			String server = ((Context) context).getServer();

			Lookup a_l = new Lookup(query, Type.A);

			a_l.setResolver(new SimpleResolver(server));

			setCache(server, a_l);

			a_l.run();

			Record[] a_results = a_l.getAnswers();

			if (a_results != null) {

				for (Record r : a_results) {

					ARecord a_record = (ARecord) r;

					result.add(a_record.getAddress());
				}
			}

			Lookup a6_l = new Lookup(query, Type.AAAA);

			a6_l.setResolver(new SimpleResolver(server));

			setCache(server, a6_l);

			a6_l.run();

			Record[] a6_results = a6_l.getAnswers();

			if (a6_results != null) {

				for (Record r : a6_results) {

					AAAARecord a6_record = (AAAARecord) r;

					result.add(a6_record.getAddress());
				}
			}

			if (result.size() == 0) {

				throw (new UnknownHostException(query));
			}

			return (result);

		} catch (Throwable e) {

			throw (new UnknownHostException(
					query + ": " + Debug.getNestedExceptionMessage(e)));
		}
	}

	public List<String> getTXTRecords(String query) {
		List<String> result = new ArrayList<>(2);

		try {
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
		} catch (Throwable e) {

		}

		return (result);
	}

	public String getTXTRecord(String query)

			throws UnknownHostException {
		try {
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

			return (null);

		} catch (Throwable e) {

			throw (new UnknownHostException(
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
		implements DNSDirContext
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

		public String getString() {
			return (server == null ? "<default>" : server);
		}
	}
}
