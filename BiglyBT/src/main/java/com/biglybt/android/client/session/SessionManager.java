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

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.SessionActivity;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class SessionManager
{
	private static final String TAG = "SessionManager";

	public static final String BUNDLE_KEY = "RemoteProfileID";

	private static final ConcurrentHashMap<String, Session> mapSessions = new ConcurrentHashMap<>();

	private static final ConcurrentHashMap<String, CopyOnWriteArrayList<SessionChangedListener>> changedListeners = new ConcurrentHashMap<>();

	private static String lastUsed = null;

	private static Session activeSession = null;

	public interface SessionChangedListener
	{
		@AnyThread
		void sessionChanged(@Nullable Session newSession);
	}

	public static boolean hasSession(@NonNull String profileID) {
		Session session = mapSessions.get(profileID);
		return session != null && !session.isDestroyed();
	}

	public static @NonNull Session getSession(@NonNull String profileID,
			@Nullable SessionChangedListener l) {
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
						+ BiglyBTApp.getAppPreferences().getNumRemotes() + "; "
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
			mapSessions.put(profileID, session);

			List<SessionChangedListener> listeners = changedListeners.get(profileID);
			if (listeners != null) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Trigger " + listeners.size() + " SessionChanged for "
							+ profileID);
				}
				for (SessionChangedListener trigger : listeners) {
					if (AndroidUtils.DEBUG) {
						//noinspection DuplicateStringLiteralInspection
						Log.d(TAG,
								"-> Trigger " + trigger + " SessionChanged for " + profileID);
					}
					trigger.sessionChanged(session);
				}
			}
		}

		if (!profileID.equals(lastUsed)) {
			lastUsed = profileID;
			IAnalyticsTracker vet = AnalyticsTracker.getInstance();
			RemoteProfile remoteProfile = session.getRemoteProfile();
			vet.setRemoteTypeName(remoteProfile.getRemoteTypeName());
		}

		if (l != null) {
			addSessionChangedListener(profileID, l);
		}
		return session;
	}

	public static void addSessionChangedListener(@NonNull String profileID,
			SessionChangedListener l) {
		CopyOnWriteArrayList<SessionChangedListener> listeners = changedListeners.get(
				profileID);
		if (listeners == null) {
			listeners = new CopyOnWriteArrayList<>();
			changedListeners.put(profileID, listeners);
		}
		if (!listeners.contains(l)) {
			listeners.add(l);
		}
	}

	public static void removeSessionChangedListener(
			@Nullable String remoteProfileID, SessionChangedListener l) {
		if (remoteProfileID == null) {
			if (AndroidUtils.DEBUG) {
				Log.w(TAG, "removeSessionChangedListener: can't remove, no id "
						+ AndroidUtils.getCompressedStackTrace());
			}
			return;
		}
		List<SessionChangedListener> listeners = changedListeners.get(
				remoteProfileID);
		if (listeners == null) {
			if (AndroidUtils.DEBUG) {
				Log.w(TAG,
						"removeSessionChangedListener: can't remove, no listeners for "
								+ remoteProfileID + "; "
								+ AndroidUtils.getCompressedStackTrace());
			}
			return;
		}
		boolean remove = listeners.remove(l);
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "removeSessionChangedListener: removed? " + remove + " + for "
					+ remoteProfileID + "; " + AndroidUtils.getCompressedStackTrace());
		}
		if (listeners.size() == 0) {
			changedListeners.remove(remoteProfileID);
		}
	}

	@AnyThread
	public static void removeSession(@NonNull String profileID,
			boolean openProfileList) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "removeSession " + profileID + "; "
					+ AndroidUtils.getCompressedStackTrace());
		}
		if (profileID.equals(lastUsed)) {
			lastUsed = null;
		}
		Session removedSession = mapSessions.remove(profileID);
		if (removedSession != null) {
			Activity currentActivity = removedSession.getCurrentActivity();
			removedSession.destroy();
			if (currentActivity != null && !currentActivity.isFinishing()) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Shutting down related activity");
				}
				if (openProfileList) {
					RemoteUtils.openRemoteList(currentActivity);
					currentActivity.finish();
				} else {
					if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
						currentActivity.finishAndRemoveTask();
					} else {
						currentActivity.finish();
					}
				}
			}
			if (removedSession == activeSession) {
				activeSession = null;
			}
		}

		List<SessionChangedListener> listeners = changedListeners.get(profileID);
		if (listeners != null) {
			for (SessionChangedListener trigger : listeners) {
				trigger.sessionChanged(null);
			}
		}
	}

	public static void removeAllSessions() {
		for (String key : mapSessions.keySet()) {
			Session session = mapSessions.get(key);
			if (session != null) {
				session.destroy();
			}
		}
		mapSessions.clear();
		changedListeners.clear();
	}

	public static void clearInactiveSessions() {
		int numClears = 0;
		for (Iterator<Session> iter = mapSessions.values().iterator(); iter.hasNext();) {
			Session session = iter.next();

			if (session != null && !session.hasCurrentActivity()) {
				if (!session.isDestroyed()) {
					session.destroy();
				}
				iter.remove();
				numClears++;
			}
		}
		if (AndroidUtils.DEBUG) {
			//noinspection DuplicateStringLiteralInspection
			Log.d(TAG, "clearInactiveSessions. " + numClears + " removed. "
					+ mapSessions.size() + " left.");
		}
	}

	public static void clearTorrentCaches(boolean keepLastUsed) {
		int numClears = 0;
		for (String key : mapSessions.keySet()) {
			if (keepLastUsed && key.equals(lastUsed)) {
				continue;
			}
			Session session = mapSessions.get(key);
			session.torrent.clearCache();
			numClears++;
		}
		if (AndroidUtils.DEBUG) {
			//noinspection DuplicateStringLiteralInspection
			Log.d(TAG, "clearTorrentCache. " + numClears + " removed");
		}
	}

	public static void clearTorrentFilesCaches(boolean keepLastUsedTorrentFiles) {
		int numClears = 0;
		for (Session session : mapSessions.values()) {
			numClears += session.torrent.clearFilesCaches(keepLastUsedTorrentFiles);
		}
		if (AndroidUtils.DEBUG) {
			//noinspection DuplicateStringLiteralInspection
			Log.d(TAG, "clearTorrentFilesCaches. " + numClears + " removed");
		}
	}

	@Nullable
	public static Session findOrCreateSession(@NonNull Fragment fragment,
			@Nullable SessionChangedListener l) {
		FragmentActivity activity = fragment.getActivity();
		if (activity instanceof SessionGetter) {
			SessionGetter sig = (SessionGetter) activity;
			Session session = sig.getSession();
			if (l != null && session != null) {
				addSessionChangedListener(session.getRemoteProfile().getID(), l);
			}
			return session;
		}

		if (activity instanceof SessionActivity) {
			String remoteProfileID = ((SessionActivity) activity).getRemoteProfileID();
			if (remoteProfileID != null) {
				Session session = SessionManager.getSession(remoteProfileID, l);
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
		return SessionManager.getSession(profileID, l);
	}

	public static String findRemoteProfileID(@NonNull Fragment fragment) {
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

	public static String findRemoteProfileID(@Nullable Activity activity) {
		if (activity == null) {
			return null;
		}
		return findRemoteProfileID(activity.getIntent(),
				activity.getClass().getSimpleName());
	}

	public static String findRemoteProfileID(@Nullable Intent intent,
			String TAG) {
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
		for (Session session : mapSessions.values()) {
			if (session.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_CORE) {
				return session;
			}
		}
		return null;
	}

	public static void setActiveSession(@NonNull Session activeSession) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "setActiveSession: " + activeSession + "; "
					+ AndroidUtils.getCompressedStackTrace());
		}
		SessionManager.activeSession = activeSession;
	}

	@Nullable
	public static Session getActiveSession() {
		return activeSession;
	}

	public static boolean clearActiveSession(@NonNull Session session) {
		boolean sameSession = activeSession == session;
		if (sameSession) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "clearActiveSession: " + activeSession + "; "
						+ AndroidUtils.getCompressedStackTrace());
			}
			activeSession = null;
		}
		return sameSession;
	}
}
