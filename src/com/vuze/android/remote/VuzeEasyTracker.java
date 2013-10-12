package com.vuze.android.remote;

import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.support.v4.app.Fragment;

import com.google.analytics.tracking.android.*;

public class VuzeEasyTracker
{
	private EasyTracker easyTracker;

	private static VuzeEasyTracker vuzeEasyTracker;

	private VuzeEasyTracker(Context ctx) {
		easyTracker = EasyTracker.getInstance(ctx);
	}

	public static synchronized VuzeEasyTracker getInstance(Context ctx) {
		if (vuzeEasyTracker == null) {
			vuzeEasyTracker = new VuzeEasyTracker(ctx);
		}
		return vuzeEasyTracker;
	}

	public static VuzeEasyTracker getInstance(Fragment fragment) {
		if (vuzeEasyTracker == null) {
			vuzeEasyTracker = new VuzeEasyTracker(fragment.getActivity());
		}
		return vuzeEasyTracker;
	}

	/**
	 * @param arg0
	 * @see com.google.analytics.tracking.android.EasyTracker#activityStart(android.app.Activity)
	 */
	public void activityStart(Activity activity) {
		easyTracker.set(Fields.SCREEN_NAME, activity.getClass().getSimpleName());
		easyTracker.activityStart(activity);
	}

	public void activityStart(Fragment fragment) {
		easyTracker.set(Fields.SCREEN_NAME, fragment.getClass().getSimpleName());
		easyTracker.activityStart(fragment.getActivity());
	}

	/**
	 * @param activity
	 * @see com.google.analytics.tracking.android.EasyTracker#activityStop(android.app.Activity)
	 */
	public void activityStop(Activity activity) {
		easyTracker.set(Fields.SCREEN_NAME, activity.getClass().getSimpleName());
		easyTracker.activityStop(activity);
	}

	public void activityStop(Fragment fragment) {
		easyTracker.set(Fields.SCREEN_NAME, fragment.getClass().getSimpleName());
		easyTracker.activityStop(fragment.getActivity());
	}

	/**
	 * @deprecated
	 * @see com.google.analytics.tracking.android.EasyTracker#dispatchLocalHits()
	 */
	public void dispatchLocalHits() {
		easyTracker.dispatchLocalHits();
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

	public void logError(Context ctx, String s) {
		easyTracker.send(MapBuilder.createException(s, true).build());
	}

	public void logError(Context ctx, Throwable e) {
		easyTracker.send(MapBuilder.createException(
				new StandardExceptionParser(ctx, null) // Context and optional collection of package names
																							 // to be used in reporting the exception.
				.getDescription(Thread.currentThread().getName(), // The name of the thread on which the exception occurred.
						e), // The exception.
				false) // False indicates a fatal exception
		.build());
	}
}
