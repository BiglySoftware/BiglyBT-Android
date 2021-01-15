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

package com.biglybt.android.client.fragment;

import androidx.fragment.app.Fragment;

import com.biglybt.android.client.R;
import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.Session;

/**
 * Created by TuxPaper on 10/23/17.
 */

public class PrefFragmentHandlerCreator
{
	public static int getPrefID(SessionActivity activity) {
		Session session = activity.getSession();
		boolean isCore = session != null
				&& session.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_CORE;
		return isCore ? R.xml.prefs_core : R.xml.prefs;
	}

	public static PrefFragmentHandler createPrefFragment(
			SessionActivity activity, Fragment fragment) {
		Session session = activity.getSession();
		boolean isCore = session != null
				&& session.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_CORE;
		return isCore ? new PrefFragmentHandlerCore(activity, fragment)
				: new PrefFragmentHandler(activity, fragment);
	}
}
