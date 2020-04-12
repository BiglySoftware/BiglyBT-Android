/*
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

package com.biglybt.android.client;

import androidx.annotation.NonNull;

public class CoreProxyPreferences
	implements Cloneable
{
	public boolean proxyTrackers;

	public boolean proxyOutgoingPeers;

	@NonNull
	public String proxyType;

	@NonNull
	public String host;

	public int port;

	@NonNull
	public String user;

	@NonNull
	public String pw;

	public CoreProxyPreferences(boolean proxyTrackers, boolean proxyOutGoingPeers,
			String proxyType, String host, int port, String user, String pw) {
		this.proxyTrackers = proxyTrackers;
		this.proxyOutgoingPeers = proxyOutGoingPeers;
		this.proxyType = proxyType == null ? "HTTP" : proxyType;
		this.host = host == null ? "" : host;
		this.port = port;
		this.user = user == null ? "" : user;
		this.pw = pw == null ? "" : pw;
	}

	@Override
	protected Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

	@NonNull
	@Override
	public String toString() {
		return super.toString() + "{ " + proxyTrackers + ", " + proxyOutgoingPeers
				+ ", " + proxyType + ", " + host + ", " + port + ", " + user + "}";
	}
}
