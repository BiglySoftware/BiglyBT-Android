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

package com.biglybt.android.client.session;

import java.util.*;

import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.fragment.SessionGetter;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

public class SessionManager
{
	private static final String TAG = "SessionManager";

	public static final String BUNDLE_KEY = "RemoteProfileID";

	private static final Map<String, Session> mapSessions = new HashMap<>();

	private static final Map<String, List<SessionChangedListener>> changedListeners = new HashMap<>();

	private static String lastUsed = null;

	private static Session currentVisibleSession = null;

	public interface SessionChangedListener
	{
		void sessionChanged(@Nullable Session newSession);
	}

	public static boolean hasSession(String profileID) {
		synchronized (mapSessions) {
			Session session = mapSessions.get(profileID);
			return session != null && !session.isDestroyed();
		}
	}

	public static @NonNull Session getSession(@NonNull String profileID,
			@Nullable FragmentActivity activity, @Nullable SessionChangedListener l) {
		synchronized (mapSessions) {
			Session session = mapSessions.get(profileID);
			if (session != null && session.isDestroyed()) {
				session = null;
				mapSessions.remove(profileID);
				if (AndroidUtils.DEBUG) {
					Log.w(TAG, "getSession: Had destroyed Session");
				}
			}
			if (session == null) {
				RemoteProfile remoteProfile = BiglyBTApp.getAppPreferences().getRemote(
						profileID);
				if (remoteProfile == null) {
					if (AndroidUtils.DEBUG) {
						Log.e(TAG, "No Session for " + profileID);
					}
					@SuppressWarnings("DuplicateStringLiteralInspection")
					String errString = "Missing RemoteProfile" + profileID.length() + "."
							+ BiglyBTApp.getAppPreferences().getNumRemotes() + " "
							+ (activity != null ? activity.getIntent() : "") + "; "
							+ RemoteUtils.lastOpenDebug;
					AnalyticsTracker.getInstance().logError(errString, null);
					// UH OH, breaking the @NotNull
					return null;
				}
				if (AndroidUtils.DEBUG) {
					//noinspection DuplicateStringLiteralInspection
					Log.d(TAG, "Create Session for " + profileID + " via "
							+ AndroidUtils.getCompressedStackTrace());
				}
				session = new Session(remoteProfile);
				if (activity != null) {
					session.setCurrentActivity(activity);
				}
				mapSessions.put(profileID, session);

				synchronized (changedListeners) {
					List<SessionChangedListener> listeners = changedListeners.get(
							profileID);
					if (listeners != null) {
						for (SessionChangedListener trigger : listeners) {
							trigger.sessionChanged(session);
						}
					}
				}
			} else {
				if (activity != null) {
					session.setCurrentActivity(activity);
				}
			}

			if (!profileID.equals(lastUsed)) {
				lastUsed = profileID;
				IAnalyticsTracker vet = AnalyticsTracker.getInstance();
				RemoteProfile remoteProfile = session.getRemoteProfile();
				vet.set("&cd2", remoteProfile.getRemoteTypeName());
			}

			if (l != null) {
				synchronized (changedListeners) {
					List<SessionChangedListener> listeners = changedListeners.get(
							profileID);
					if (listeners == null) {
						listeners = new ArrayList<>(1);
						changedListeners.put(profileID, listeners);
					}
					if (!listeners.contains(l)) {
						listeners.add(l);
					}
				}
			}
			return session;
		}
	}

	public static void removeSessionChangedListener(String remoteProfileID,
			SessionChangedListener l) {
		synchronized (changedListeners) {
			List<SessionChangedListener> listeners = changedListeners.get(
					remoteProfileID);
			if (listeners == null) {
				return;
			}
			listeners.remove(l);
			if (listeners.size() == 0) {
				changedListeners.remove(remoteProfileID);
			}
		}
	}

	public static void removeSession(String profileID) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "removeSession " + profileID + "; "
					+ AndroidUtils.getCompressedStackTrace());
		}
		if (profileID.equals(lastUsed)) {
			lastUsed = null;
		}
		Session removedSession;
		synchronized (mapSessions) {
			removedSession = mapSessions.remove(profileID);
		}
		if (removedSession != null) {
			Activity currentActivity = removedSession.getCurrentActivity();
			removedSession.destroy();
			if (currentActivity != null && !currentActivity.isFinishing()) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Shutting down related activity");
				}
				RemoteUtils.openRemoteList(currentActivity);
			}
			if (removedSession == currentVisibleSession) {
				currentVisibleSession = null;
			}
		}

		synchronized (changedListeners) {
			List<SessionChangedListener> listeners = changedListeners.get(profileID);
			if (listeners != null) {
				for (SessionChangedListener trigger : listeners) {
					trigger.sessionChanged(null);
				}
			}
		}
	}

	public static void removeAllSessions() {
		synchronized (mapSessions) {
			for (String key : mapSessions.keySet()) {
				Session session = mapSessions.get(key);
				if (session != null) {
					session.destroy();
				}
			}
			mapSessions.clear();
			changedListeners.clear();
		}
	}

	public static void clearTorrentCaches(boolean keepLastUsed) {
		int numClears = 0;
		synchronized (mapSessions) {
			for (String key : mapSessions.keySet()) {
				if (keepLastUsed && key.equals(lastUsed)) {
					continue;
				}
				Session session = mapSessions.get(key);
				session.torrent.clearCache();
				numClears++;
			}
		}
		if (AndroidUtils.DEBUG) {
			//noinspection DuplicateStringLiteralInspection
			Log.d(TAG, "clearTorrentCache. " + numClears + " removed");
		}
	}

	public static void clearTorrentFilesCaches(boolean keepLastUsedTorrentFiles) {
		int numClears = 0;
		synchronized (mapSessions) {
			for (String key : mapSessions.keySet()) {
				Session session = mapSessions.get(key);
				numClears += session.torrent.clearFilesCaches(keepLastUsedTorrentFiles);
			}
		}
		if (AndroidUtils.DEBUG) {
			//noinspection DuplicateStringLiteralInspection
			Log.d(TAG, "clearTorrentFilesCaches. " + numClears + " removed");
		}
	}

	public static Session findOrCreateSession(Fragment fragment,
			@Nullable SessionChangedListener l) {
		FragmentActivity activity = fragment.getActivity();
		if (activity instanceof SessionGetter) {
			SessionGetter sig = (SessionGetter) activity;
			return sig.getSession();
		}

		if (activity instanceof SessionActivity) {
			String remoteProfileID = ((SessionActivity) activity).getRemoteProfileID();
			if (remoteProfileID != null) {
				Session session = SessionManager.getSession(remoteProfileID, activity,
						l);
				if (session != null) {
					return session;
				}
			}
		}

		Bundle arguments = fragment.getArguments();
		if (arguments == null) {
			return null;
		}
		String profileID = arguments.getString(SessionManager.BUNDLE_KEY);
		if (profileID == null) {
			return null;
		}
		return SessionManager.getSession(profileID, activity, l);
	}

	public static String findRemoteProfileID(Fragment fragment) {
		Bundle arguments = fragment.getArguments();
		if (arguments != null) {
			String profileID = arguments.getString(SessionManager.BUNDLE_KEY);
			if (profileID != null) {
				return profileID;
			}
		}

		FragmentActivity activity = fragment.getActivity();
		if (activity instanceof SessionGetter) {
			SessionGetter sig = (SessionGetter) activity;
			return sig.getSession().getRemoteProfile().getID();
		}
		return null;
	}

	public static String findRemoteProfileID(Activity activity, String TAG) {
		return findRemoteProfileID(activity.getIntent(), TAG);
	}

	public static String findRemoteProfileID(Intent intent, String TAG) {
		if (intent == null) {
			return null;
		}
		final Bundle extras = intent.getExtras();
		if (extras == null) {
			//noinspection DuplicateStringLiteralInspection
			Log.e(TAG, "No extras!");
			return null;
		}

		Bundle appData = intent.getBundleExtra(SearchManager.APP_DATA);
		if (appData != null) {
			String remoteProfileID = appData.getString(BUNDLE_KEY);
			if (remoteProfileID != null) {
				return remoteProfileID;
			}
		} else {
			String remoteProfileID = extras.getString(BUNDLE_KEY);
			if (remoteProfileID != null) {
				return remoteProfileID;
			}
		}
		return null;
	}

	public static @Nullable Session findCoreSession() {
		synchronized (mapSessions) {
			for (String profileID : mapSessions.keySet()) {
				Session session = mapSessions.get(profileID);
				if (session.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_CORE) {
					return session;
				}
			}
		}
		return null;
	}

	public static void setCurrentVisibleSession(Session currentVisibleSession) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "setCurrentVisibleSession: " + currentVisibleSession);
		}
		SessionManager.currentVisibleSession = currentVisibleSession;
	}

	public static Session getCurrentVisibleSession() {
		return currentVisibleSession;
	}

}
