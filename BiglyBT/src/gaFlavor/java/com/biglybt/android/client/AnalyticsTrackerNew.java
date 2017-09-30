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

import java.util.Map;

import com.google.android.gms.analytics.*;
import com.google.android.gms.analytics.HitBuilders.EventBuilder;
import com.google.android.gms.analytics.HitBuilders.ScreenViewBuilder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

public class AnalyticsTrackerNew
	implements IAnalyticsTracker
{
	private static final String CAMPAIGN_SOURCE_PARAM = "utm_source";

	// http://goo.gl/M6dK2U
	private static final String SCREEN_NAME = "&cd";

	private static final String PAGE = "&dp";

	private static final String CAMPAIGN_MEDIUM = "&cm";

	private static final String CAMPAIGN_SOURCE = "&cs";

	private static final String TAG = "VETnew";

	private GoogleAnalytics analytics;

	private Tracker mTracker;

	protected AnalyticsTrackerNew(Context ctx) {
		try {
			analytics = GoogleAnalytics.getInstance(ctx);
			mTracker = analytics.newTracker(R.xml.global_tracker);
			mTracker.enableAutoActivityTracking(false);
			if (BiglyBTApp.isCoreProcess()) {
				mTracker.setAppName("BiglyBT: Core");
			}
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "AnalyticsTrackerNew", t);
			}
		}
	}

	@Override
	public void activityResume(Activity activity) {

		activityResume(activity, activity.getClass().getSimpleName());
	}

	@Override
	public void activityResume(Activity activity, String name) {
		try {
			mTracker.setScreenName(name);
			ScreenViewBuilder builder = new ScreenViewBuilder();
			builder.set(SCREEN_NAME, name);
			Intent intent = activity.getIntent();
			if (intent != null) {
				Uri data = intent.getData();
				if (data != null) {
					builder.setAll(getReferrerMapFromUri(data));
				}
			}
			mTracker.send(builder.build());
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "activityStart", t);
			}
		}
	}

	@Override
	public void fragmentResume(@NonNull Fragment fragment, String name) {
		try {
			mTracker.setScreenName(name);
			ScreenViewBuilder builder = new ScreenViewBuilder();
			builder.set(SCREEN_NAME, name);
			mTracker.send(builder.build());
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "fragmentStart", t);
			}
		}
	}

	@Override
	public void activityPause(Activity activity) {
		try {
			GoogleAnalytics.getInstance(activity).reportActivityStop(activity);
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "activityStop", t);
			}
		}

	}

	@Override
	public void fragmentPause(Fragment fragment) {
		try {
			// Does EasyTracker.activityStop do anything anyway?  I never see any
			// calls when GA is in debug log mode.
			//easyTracker.activityStop(fragment.getActivity());
			// However, we still want to notify that the main activity is back in view
			// since stopping a fragment doesn't tend to start a new activity
			FragmentActivity activity = fragment.getActivity();
			if (activity != null && !activity.isFinishing()) {
				activityResume(activity);
			}
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "fragmentStop", t);
			}
		}
	}

	@Override
	public void set(String key, String value) {
		try {
			mTracker.set(key, value);
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "set", t);
			}
		}
	}

	@Override
	public void logError(String s, String page) {
		try {
			HitBuilders.ExceptionBuilder builder = new HitBuilders.ExceptionBuilder();
			builder.setFatal(false);
			builder.setDescription(s);

			if (page != null) {
				builder.set(PAGE, page);
			}
			mTracker.send(builder.build());
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "logError", t);
			}
		}

	}

	@Override
	public void logError(Throwable e) {
		try {
			HitBuilders.ExceptionBuilder builder = new HitBuilders.ExceptionBuilder();
			builder.setFatal(false);
			String s = e.getClass().getSimpleName();
			if (e instanceof SecurityException || e instanceof RuntimeException) {
				s += ":" + e.getMessage();
			}
			builder.setDescription(
					s + " " + AndroidUtils.getCompressedStackTrace(e, 0, 8));
			mTracker.send(builder.build());
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "logError", t);
			}
		}
	}

	@Override
	public void logError(Throwable e, String extra) {
		try {
			HitBuilders.ExceptionBuilder builder = new HitBuilders.ExceptionBuilder();
			builder.setFatal(false);
			String s = e.getClass().getSimpleName();
			if (e instanceof SecurityException || e instanceof RuntimeException) {
				s += ":" + e.getMessage();
			}
			if (extra != null) {
				s += "[" + extra + "]";
			}
			builder.setDescription(
					s + " " + AndroidUtils.getCompressedStackTrace(e, 0, 8));
			mTracker.send(builder.build());
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "logError", t);
			}
		}
	}

	public void logErrorNoLines(Throwable e) {
		try {
			HitBuilders.ExceptionBuilder builder = new HitBuilders.ExceptionBuilder();
			builder.setFatal(false);
			builder.setDescription(AndroidUtils.getCauses(e));
			mTracker.send(builder.build());
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "logErrorNoLines", t);
			}
		}
	}

	@Override
	public void registerExceptionReporter(Context applicationContext) {
		try {
			ExceptionReporter myHandler = new ExceptionReporter(mTracker,
					Thread.getDefaultUncaughtExceptionHandler(), applicationContext);
			myHandler.setExceptionParser(new ExceptionParser() {
				@Override
				public String getDescription(String threadName, Throwable t) {
					StringBuilder sb = new StringBuilder();
					sb.append('*').append(t.getClass().getSimpleName()).append(
							' ').append(AndroidUtils.getCompressedStackTrace(t, 0, 9));
					if (threadName != null) {
						sb.append(" {").append(threadName).append("}");
					}
					return sb.toString();
				}
			});

			Thread.setDefaultUncaughtExceptionHandler(myHandler);
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "registerExceptionReporter", t);
			}
		}
	}

	@Override
	public void sendEvent(String category, String action, String label,
			Long value) {
		try {
			EventBuilder builder = new HitBuilders.EventBuilder(category, action);
			if (label != null) {
				builder.setLabel(label);
			}
			if (value != null) {
				builder.setValue(value);
			}
			mTracker.send(builder.build());
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "sendEvent", t);
			}
		}
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
	private static Map<String, String> getReferrerMapFromUri(Uri uri) {
		try {
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
				} else if (uri.getAuthority() != null
						&& uri.getAuthority().length() > 0) {

					builder.set(CAMPAIGN_MEDIUM, "referral");
					builder.set(CAMPAIGN_SOURCE, uri.getAuthority());

				} else if (uri.getScheme() != null) {
					builder.set(CAMPAIGN_MEDIUM, uri.getScheme());
				}
			} catch (Throwable t) {
				// I found: java.lang.UnsupportedOperationException: This isn't a
				// hierarchical URI.
				// Fixed above with isHeirarchical, but who knows what other throws
				// there are
				if (AndroidUtils.DEBUG) {
					t.printStackTrace();
				}
			}

			return builder.build();
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "getReferrerMapFromUri", t);
			}
		}

		return null;

	}

	@Override
	public void stop() {
		try {
			analytics.dispatchLocalHits();
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "stop", t);
			}
		}

	}
}
