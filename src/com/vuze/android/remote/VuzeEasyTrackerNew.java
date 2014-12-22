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

import com.google.android.gms.analytics.*;
import com.google.android.gms.analytics.HitBuilders.EventBuilder;
import com.google.android.gms.analytics.HitBuilders.ScreenViewBuilder;
import com.google.android.gms.analytics.Logger.LogLevel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;

public class VuzeEasyTrackerNew
	implements IVuzeEasyTracker
{
	private static final String CAMPAIGN_SOURCE_PARAM = "utm_source";

	// http://goo.gl/M6dK2U
	public static final java.lang.String SCREEN_NAME = "&cd";

	public static final java.lang.String PAGE = "&dp";

	public static final java.lang.String CAMPAIGN_MEDIUM = "&cm";

	public static final java.lang.String CAMPAIGN_SOURCE = "&cs";

	private Tracker mTracker;

	protected VuzeEasyTrackerNew(Context ctx) {
		GoogleAnalytics analytics = GoogleAnalytics.getInstance(ctx);
		mTracker = analytics.newTracker(R.xml.analytics);
		mTracker.enableAutoActivityTracking(false);
		analytics.getLogger().setLogLevel(
				AndroidUtils.DEBUG ? LogLevel.VERBOSE : LogLevel.ERROR);
	}

	public void activityStart(Activity activity) {

		mTracker.setScreenName(activity.getClass().getSimpleName());
		ScreenViewBuilder builder = new ScreenViewBuilder();
		builder.set(SCREEN_NAME, activity.getClass().getSimpleName());
		Intent intent = activity.getIntent();
		if (intent != null) {
			Uri data = intent.getData();
			if (data != null) {
				builder.setAll(getReferrerMapFromUri(data));
			}
		}
		mTracker.send(builder.build());
	}

	public void screenStart(String name) {
		fragmentStart(null, name);
	}

	public void fragmentStart(Fragment fragment, String name) {
		mTracker.setScreenName(name);
		ScreenViewBuilder builder = new ScreenViewBuilder();
		builder.set(SCREEN_NAME, name);
		mTracker.send(builder.build());
	}

	public void activityStop(Activity activity) {
		GoogleAnalytics.getInstance(activity).reportActivityStop(activity);
	}

	public void fragmentStop(Fragment fragment) {
		// Does EasyTracker.activityStop do anything anyway?  I never see any
		// calls when GA is in debug log mode.
		//easyTracker.activityStop(fragment.getActivity());
		// However, we still want to notify that the main activity is back in view
		// since stopping a fragment doesn't tend to start a new activity
		FragmentActivity activity = fragment.getActivity();
		if (activity != null && !activity.isFinishing()) {
			activityStart(activity);
		}
	}

	public String get(String key) {
		return mTracker.get(key);
	}

	public void send(Map<String, String> params) {
		mTracker.send(params);
	}

	public void set(String key, String value) {
		mTracker.set(key, value);
	}

	/**
	 * @return
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return mTracker.toString();
	}

	public void logError(String s, String page) {
		HitBuilders.ExceptionBuilder builder = new HitBuilders.ExceptionBuilder();
		builder.setFatal(false);
		builder.setDescription(s);

		if (page != null) {
			builder.set(PAGE, page);
		}
		mTracker.send(builder.build());
	}

	public void logError(Throwable e) {
		HitBuilders.ExceptionBuilder builder = new HitBuilders.ExceptionBuilder();
		builder.setFatal(false);
		builder.setDescription(e.getClass().getSimpleName() + " "
				+ AndroidUtils.getCompressedStackTrace(e, 0, 8));
		mTracker.send(builder.build());
	}

	public void logErrorNoLines(Throwable e) {
		HitBuilders.ExceptionBuilder builder = new HitBuilders.ExceptionBuilder();
		builder.setFatal(false);
		builder.setDescription(AndroidUtils.getCauses(e));
		mTracker.send(builder.build());
	}

	@Override
	public void registerExceptionReporter(Context applicationContext) {
		ExceptionReporter myHandler = new ExceptionReporter(mTracker,
				Thread.getDefaultUncaughtExceptionHandler(), applicationContext);
		myHandler.setExceptionParser(new ExceptionParser() {
			@Override
			public String getDescription(String threadName, Throwable t) {
				String s = "*" + t.getClass().getSimpleName() + " "
						+ AndroidUtils.getCompressedStackTrace(t, 0, 9);
				return s;
			}
		});

		Thread.setDefaultUncaughtExceptionHandler(myHandler);
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.IVuzeEasyTracker#sendEvent(java.lang.String, java.lang.String, java.lang.String, java.lang.Long)
	 */
	@Override
	public void sendEvent(String category, String action, String label, Long value) {
		EventBuilder builder = new HitBuilders.EventBuilder(category, action);
		if (label != null) {
			builder.setLabel(label);
		}
		if (value != null) {
			builder.setValue(value);
		}
		mTracker.send(builder.build());
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
	public Map<String, String> getReferrerMapFromUri(Uri uri) {

		ScreenViewBuilder builder = new ScreenViewBuilder();

		// If no URI, return an empty Map.
		if (uri == null) {
			return builder.build();
		}

		try {
			// Source is the only required campaign field. No need to continue if not
			// present.
			if (uri.isHierarchical()
					&& uri.getQueryParameter(CAMPAIGN_SOURCE_PARAM) != null) {

				// MapBuilder.setCampaignParamsFromUrl parses Google Analytics campaign
				// ("UTM") parameters from a string URL into a Map that can be set on
				// the Tracker.
				builder.setCampaignParamsFromUrl(uri.toString());

				// If no source parameter, set authority to source and medium to
				// "referral".
			} else if (uri.getAuthority() != null && uri.getAuthority().length() > 0) {

				builder.set(CAMPAIGN_MEDIUM, "referral");
				builder.set(CAMPAIGN_SOURCE, uri.getAuthority());

			} else if (uri.getScheme() != null) {
				builder.set(CAMPAIGN_MEDIUM, uri.getScheme());
			}
		} catch (Throwable t) {
			// I found: java.lang.UnsupportedOperationException: This isn't a hierarchical URI.
			// Fixed above with isHeirarchical, but who knows what other throws there are
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
		}

		return builder.build();
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.IVuzeEasyTracker#setClientID(java.lang.String)
	 */
	@Override
	public void setClientID(String rt) {
		mTracker.setClientId(rt);
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.IVuzeEasyTracker#setPage(java.lang.String)
	 */
	@Override
	public void setPage(String rt) {
		mTracker.setPage(rt);
	}
}
