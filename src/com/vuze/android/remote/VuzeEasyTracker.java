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

import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.Fragment;

import com.google.analytics.tracking.android.*;

public class VuzeEasyTracker
{
	private EasyTracker easyTracker;

	private static VuzeEasyTracker vuzeEasyTracker;

	private static final String CAMPAIGN_SOURCE_PARAM = "utm_source";

	private VuzeEasyTracker(Context ctx) {
		easyTracker = EasyTracker.getInstance(ctx);
	}

	public static VuzeEasyTracker getInstance(Context ctx) {
		synchronized (VuzeEasyTracker.class) {
			if (vuzeEasyTracker == null) {
				vuzeEasyTracker = new VuzeEasyTracker(ctx);
			}
		}
		return vuzeEasyTracker;
	}

	public static VuzeEasyTracker getInstance() {
		synchronized (VuzeEasyTracker.class) {
			if (vuzeEasyTracker == null) {
				vuzeEasyTracker = new VuzeEasyTracker(VuzeRemoteApp.getContext());
			}
		}
		return vuzeEasyTracker;
	}

	public static VuzeEasyTracker getInstance(Fragment fragment) {
		synchronized (VuzeEasyTracker.class) {
			if (vuzeEasyTracker == null) {
				vuzeEasyTracker = new VuzeEasyTracker(fragment.getActivity());
			}
		}
		return vuzeEasyTracker;
	}

	/**
	 * @param arg0
	 * @see com.google.analytics.tracking.android.EasyTracker#activityStart(android.app.Activity)
	 */
	public void activityStart(Activity activity) {

		easyTracker.set(Fields.SCREEN_NAME, activity.getClass().getSimpleName());
		MapBuilder mapBuilder = MapBuilder.createAppView().set(Fields.SCREEN_NAME,
				activity.getClass().getSimpleName());
		Intent intent = activity.getIntent();
		if (intent != null) {
			Uri data = intent.getData();
			if (data != null) {
				mapBuilder.setAll(getReferrerMapFromUri(data));
			}
		}
		easyTracker.send(mapBuilder.build());
	}

	public void activityStart(Fragment fragment, String name) {
		easyTracker.set(Fields.SCREEN_NAME, name);
		MapBuilder mapBuilder = MapBuilder.createAppView().set(Fields.SCREEN_NAME,
				name);
		easyTracker.send(mapBuilder.build());
	}

	/**
	 * @param activity
	 * @see com.google.analytics.tracking.android.EasyTracker#activityStop(android.app.Activity)
	 */
	public void activityStop(Activity activity) {
		easyTracker.activityStop(activity);
	}

	public void activityStop(Fragment fragment) {
		// Does EasyTracker.activityStop do anything anyway?  I never see any
		// calls when GA is in debug log mode.
		//easyTracker.activityStop(fragment.getActivity());
	}

	/**
	 * @param o
	 * @return
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object o) {
		return easyTracker.equals(o);
	}

	/**
	 * @param key
	 * @return
	 * @see com.google.analytics.tracking.android.Tracker#get(java.lang.String)
	 */
	public String get(String key) {
		return easyTracker.get(key);
	}

	/**
	 * @return
	 * @see com.google.analytics.tracking.android.Tracker#getName()
	 */
	public String getName() {
		return easyTracker.getName();
	}

	/**
	 * @return
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return easyTracker.hashCode();
	}

	/**
	 * @param params
	 * @see com.google.analytics.tracking.android.EasyTracker#send(java.util.Map)
	 */
	public void send(Map<String, String> params) {
		easyTracker.send(params);
	}

	/**
	 * @param key
	 * @param value
	 * @see com.google.analytics.tracking.android.Tracker#set(java.lang.String, java.lang.String)
	 */
	public void set(String key, String value) {
		easyTracker.set(key, value);
	}

	/**
	 * @return
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return easyTracker.toString();
	}

	public void logError(Context ctx, String s, String page) {
		MapBuilder mapBuilder = MapBuilder.createException(s, false);
		if (page != null) {
			mapBuilder.set(Fields.PAGE, page);
		}
		easyTracker.send(mapBuilder.build());
	}

	public void logError(Context ctx, Throwable e) {
		if (ctx == null) {
			ctx = VuzeRemoteApp.getContext();
		}
		easyTracker.send(MapBuilder.createException(
				new StandardExceptionParser(ctx, null) // Context and optional collection of package names
																							 // to be used in reporting the exception.
				.getDescription(Thread.currentThread().getName(), // The name of the thread on which the exception occurred.
						e), // The exception.
				false) // False indicates a fatal exception
		.build());
	}

	/*
	* Given a URI, returns a map of campaign data that can be sent with
	* any GA hit.
	*
	* @param uri A hierarchical URI that may or may not have campaign data
	*     stored in query parameters.
	*
	* @return A map that may contain campaign or referrer
	*     that may be sent with any Google Analytics hit.
	*/
	static Map<String, String> getReferrerMapFromUri(Uri uri) {

		MapBuilder paramMap = new MapBuilder();

		// If no URI, return an empty Map.
		if (uri == null) {
			return paramMap.build();
		}

		try {
			// Source is the only required campaign field. No need to continue if not
			// present.
			if (uri.isHierarchical()
					&& uri.getQueryParameter(CAMPAIGN_SOURCE_PARAM) != null) {

				// MapBuilder.setCampaignParamsFromUrl parses Google Analytics campaign
				// ("UTM") parameters from a string URL into a Map that can be set on
				// the Tracker.
				paramMap.setCampaignParamsFromUrl(uri.toString());

				// If no source parameter, set authority to source and medium to
				// "referral".
			} else if (uri.getAuthority() != null && uri.getAuthority().length() > 0) {

				paramMap.set(Fields.CAMPAIGN_MEDIUM, "referral");
				paramMap.set(Fields.CAMPAIGN_SOURCE, uri.getAuthority());

			} else if (uri.getScheme() != null) {
				paramMap.set(Fields.CAMPAIGN_MEDIUM, uri.getScheme());
			}
		} catch (Throwable t) {
			// I found: java.lang.UnsupportedOperationException: This isn't a hierarchical URI.
			// Fixed above with isHeirarchical, but who knows what other throws there are
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
		}

		return paramMap.build();
	}
}
