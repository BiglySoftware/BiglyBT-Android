/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p>
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

package com.vuze.android.remote;

import java.util.*;

import com.vuze.android.remote.fragment.SessionInfoGetter;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

public class SessionInfoManager
{
	private static final String TAG = "SessionInfoManager";

	public static final String BUNDLE_KEY = "RemoteProfileID";

	private static final Map<String, SessionInfo> mapSessionInfo = new HashMap<>();

	private static final Map<String, List<SessionInfoChangedListener>> changedListeners = new HashMap<>();

	private static String lastUsed;

	public interface SessionInfoChangedListener
	{
		void sessionInfoChanged(@Nullable SessionInfo newSessionInfo);
	}

	public static boolean hasSessionInfo(String profileID) {
		synchronized (mapSessionInfo) {
			SessionInfo sessionInfo = mapSessionInfo.get(profileID);
			return sessionInfo != null && !sessionInfo.isDestroyed();
		}
	}

	public static @NonNull SessionInfo getSessionInfo(@Nullable String profileID,
			@Nullable Activity activity, @Nullable SessionInfoChangedListener l) {
		synchronized (mapSessionInfo) {
			SessionInfo sessionInfo = mapSessionInfo.get(profileID);
			if (sessionInfo != null && sessionInfo.isDestroyed()) {
				sessionInfo = null;
				mapSessionInfo.remove(profileID);
				if (AndroidUtils.DEBUG) {
					Log.w(TAG, "getSessionInfo: Had destroyed SessionInfo");
				}
			}
			if (sessionInfo == null) {
				RemoteProfile remoteProfile = VuzeRemoteApp.getAppPreferences().getRemote(
						profileID);
				if (remoteProfile == null) {
					if (AndroidUtils.DEBUG) {
						Log.e(TAG, "No SessionInfo for " + profileID);
					}
					@SuppressWarnings("DuplicateStringLiteralInspection")
					String errString = "Missing RemoteProfile"
							+ (profileID == null ? "null" : profileID.length()) + "."
							+ VuzeRemoteApp.getAppPreferences().getNumRemotes() + " "
							+ (activity != null ? activity.getIntent() : "") + "; "
							+ RemoteUtils.lastOpenDebug;
					VuzeEasyTracker.getInstance().logError(errString, null);
					return null;
				}
				if (AndroidUtils.DEBUG) {
					//noinspection DuplicateStringLiteralInspection
					Log.d(TAG, "Create SessionInfo for " + profileID + " via "
							+ AndroidUtils.getCompressedStackTrace());
				}
				sessionInfo = new SessionInfo(remoteProfile);
				if (activity != null) {
					sessionInfo.setCurrentActivity(activity);
				}
				mapSessionInfo.put(profileID, sessionInfo);

				synchronized (changedListeners) {
					List<SessionInfoChangedListener> listeners = changedListeners.get(
							profileID);
					if (listeners != null) {
						for (SessionInfoChangedListener trigger : listeners) {
							trigger.sessionInfoChanged(sessionInfo);
						}
					}
				}
			} else {
				if (activity != null) {
					sessionInfo.setCurrentActivity(activity);
				}
			}

			if (!profileID.equals(lastUsed)) {
				lastUsed = profileID;
				IVuzeEasyTracker vet = VuzeEasyTracker.getInstance();
				RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
				String rt = remoteProfile.isLocalHost() ? "L"
						: Integer.toString(remoteProfile.getRemoteType());
				vet.set("&cd2", remoteProfile.getRemoteTypeName());
				vet.setPage(rt);
			}

			if (l != null) {
				synchronized (changedListeners) {
					List<SessionInfoChangedListener> listeners = changedListeners.get(
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
			return sessionInfo;
		}
	}

	public static void removeSessionInfoChangedListener(String remoteProfileID,
			SessionInfoChangedListener l) {
		synchronized (changedListeners) {
			List<SessionInfoChangedListener> listeners = changedListeners.get(
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

	public static void removeSessionInfo(String profileID) {
		if (profileID.equals(lastUsed)) {
			lastUsed = null;
		}
		SessionInfo removedSessionInfo;
		synchronized (mapSessionInfo) {
			removedSessionInfo = mapSessionInfo.remove(profileID);
		}
		if (removedSessionInfo != null) {
			removedSessionInfo.destroy();
		}

		synchronized (changedListeners) {
			List<SessionInfoChangedListener> listeners = changedListeners.get(
					profileID);
			if (listeners != null) {
				for (SessionInfoChangedListener trigger : listeners) {
					trigger.sessionInfoChanged(null);
				}
			}
		}
	}

	public static void clearTorrentCaches(boolean keepLastUsed) {
		int numClears = 0;
		synchronized (mapSessionInfo) {
			for (String key : mapSessionInfo.keySet()) {
				if (keepLastUsed && key.equals(lastUsed)) {
					continue;
				}
				SessionInfo sessionInfo = mapSessionInfo.get(key);
				sessionInfo.clearTorrentCache();
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
		synchronized (mapSessionInfo) {
			for (String key : mapSessionInfo.keySet()) {
				SessionInfo sessionInfo = mapSessionInfo.get(key);
				numClears += sessionInfo.clearTorrentFilesCaches(
						keepLastUsedTorrentFiles);
			}
		}
		if (AndroidUtils.DEBUG) {
			//noinspection DuplicateStringLiteralInspection
			Log.d(TAG, "clearTorrentFilesCaches. " + numClears + " removed");
		}
	}

	public static SessionInfo findSessionInfo(Fragment fragment,
			@Nullable SessionInfoChangedListener l) {
		FragmentActivity activity = fragment.getActivity();
		if (activity instanceof SessionInfoGetter) {
			SessionInfoGetter sig = (SessionInfoGetter) activity;
			return sig.getSessionInfo();
		}

		Bundle arguments = fragment.getArguments();
		if (arguments == null) {
			return null;
		}
		String profileID = arguments.getString(SessionInfoManager.BUNDLE_KEY);
		if (profileID == null) {
			return null;
		}
		return SessionInfoManager.getSessionInfo(profileID, activity, l);
	}

	public static String findRemoteProfileID(Fragment fragment) {
		Bundle arguments = fragment.getArguments();
		if (arguments != null) {
			String profileID = arguments.getString(SessionInfoManager.BUNDLE_KEY);
			if (profileID != null) {
				return profileID;
			}
		}

		FragmentActivity activity = fragment.getActivity();
		if (activity instanceof SessionInfoGetter) {
			SessionInfoGetter sig = (SessionInfoGetter) activity;
			return sig.getSessionInfo().getRemoteProfile().getID();
		}
		return null;
	}

	public static String findRemoteProfileID(Activity activity, String TAG) {
		Intent intent = activity.getIntent();
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
}
