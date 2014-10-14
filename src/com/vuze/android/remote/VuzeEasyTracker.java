/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 */

package com.vuze.android.remote;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

public class VuzeEasyTracker 
{
	private static IVuzeEasyTracker vuzeEasyTracker;

	public static IVuzeEasyTracker getInstance(Context ctx) {
		synchronized (VuzeEasyTracker.class) {
			if (vuzeEasyTracker == null) {
				int available = GooglePlayServicesUtil.isGooglePlayServicesAvailable(ctx);
				if (available == ConnectionResult.SUCCESS) {
					if (AndroidUtils.DEBUG) {
						Log.d("VET", "Using GooglePlay GA");
					}
					vuzeEasyTracker = new VuzeEasyTrackerNew(ctx);
				} else {
					if (AndroidUtils.DEBUG) {
						Log.d("VET", "Using old GA API");
					}
					vuzeEasyTracker = new VuzeEasyTrackerOld(ctx);
				}
			}
		}
		return vuzeEasyTracker;
	}

	public static IVuzeEasyTracker getInstance() {
		synchronized (VuzeEasyTracker.class) {
			if (vuzeEasyTracker == null) {
				return getInstance(VuzeRemoteApp.getContext());
			}
		}
		return vuzeEasyTracker;
	}

	public static IVuzeEasyTracker getInstance(Fragment fragment) {
		synchronized (VuzeEasyTracker.class) {
			if (vuzeEasyTracker == null) {
				return getInstance(fragment.getActivity());
			}
		}
		return vuzeEasyTracker;
	}
}
