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

import com.biglybt.core.util.SHA1Hasher;

import android.text.TextUtils;

/**
 * Created by TuxPaper on 6/14/18.
 */
public class CoreRemoteAccessPreferences
	implements Cloneable
{
	public final boolean allowLANAccess;

	public final boolean reqPW;

	public final String user;

	public final String pw;

	public CoreRemoteAccessPreferences(boolean allowLANAccess, boolean reqPW,
			String user, String pw) {
		this.allowLANAccess = allowLANAccess;
		this.reqPW = reqPW;
		this.user = user;
		this.pw = pw;
	}

	@Override
	protected Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

	public byte[] getSHA1pw() {
		return new SHA1Hasher().calculateHash(pw.getBytes());
	}

	@Override
	public String toString() {
		return super.toString() + "{ " + allowLANAccess + ", " + reqPW + ", " + user
				+ ", " + (TextUtils.isEmpty(pw) ? "no pw" : pw) + "}";

	}
}
