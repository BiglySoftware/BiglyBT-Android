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

package com.biglybt.android.client;

import com.biglybt.util.Thunk;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;

public class AnalyticsTracker
{
	public static final String CAT_PROFILE = "Profile";

	public static final String CAT_UI_ACTION = "uiAction";

	public static final String ACTION_RATING = "Rating";

	public static final String ACTION_REMOVED = "Removed";

	@Thunk
	static IAnalyticsTracker analyticsTracker = null;

	@NonNull
	public static IAnalyticsTracker getInstance(Context ctx) {
		synchronized (AnalyticsTracker.class) {
			if (analyticsTracker == null) {
				analyticsTracker = new AnalyticsTrackerBare() {
					@Override
					public void stop() {
						super.stop();
						analyticsTracker = null;
					}
				};
			}
		}
		return analyticsTracker;
	}

	public static IAnalyticsTracker getInstance() {
		synchronized (AnalyticsTracker.class) {
			if (analyticsTracker == null) {
				return getInstance(BiglyBTApp.getContext());
			}
		}
		return analyticsTracker;
	}

	public static IAnalyticsTracker getInstance(Fragment fragment) {
		synchronized (AnalyticsTracker.class) {
			if (analyticsTracker == null) {
				return getInstance(fragment.getActivity());
			}
		}
		return analyticsTracker;
	}
}
