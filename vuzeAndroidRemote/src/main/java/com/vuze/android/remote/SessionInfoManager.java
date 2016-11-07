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

import java.util.HashMap;
import java.util.Map;

import com.vuze.android.remote.fragment.SessionInfoGetter;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

public class SessionInfoManager
{
	private static final String TAG = "SessionInfoManager";

	public static String BUNDLE_KEY = "RemoteProfileID";

	private static final Map<String, SessionInfo> mapSessionInfo = new HashMap<>();

	private static String lastUsed;

	public static SessionInfo getSessionInfo(String profileID,
			Activity activity) {
		synchronized (mapSessionInfo) {
			SessionInfo sessionInfo = mapSessionInfo.get(profileID);
			RemoteProfile remoteProfile;
			if (sessionInfo == null) {
				remoteProfile = VuzeRemoteApp.getAppPreferences().getRemote(profileID);

				if (remoteProfile == null) {
					if (AndroidUtils.DEBUG) {
						Log.e(TAG, "No SessionInfo for " + profileID);
					}
					String errString = "Missing RemoteProfile"
							+ (profileID == null ? "null" : profileID.length()) + "."
							+ VuzeRemoteApp.getAppPreferences().getNumRemotes() + " "
							+ activity.getIntent() + "; " + RemoteUtils.lastOpenDebug;
					VuzeEasyTracker.getInstance(activity).logError(errString, null);
					return null;
				}
				sessionInfo = new SessionInfo(activity, remoteProfile);
				mapSessionInfo.put(profileID, sessionInfo);
			} else {
				remoteProfile = sessionInfo.getRemoteProfile();
			}
			lastUsed = profileID;
			IVuzeEasyTracker vet = VuzeEasyTracker.getInstance();
			String rt = remoteProfile.isLocalHost() ? "L"
					: Integer.toString(remoteProfile.getRemoteType());
			vet.set("&cd2", remoteProfile.getRemoteTypeName());
			vet.setClientID(rt);
			vet.setPage(rt);
			return sessionInfo;
		}
	}

	public static void removeSessionInfo(String id) {
		if (id.equals(lastUsed)) {
			lastUsed = null;
		}
		SessionInfo removedSessionInfo;
		synchronized (mapSessionInfo) {
			removedSessionInfo = mapSessionInfo.remove(id);
		}
		if (removedSessionInfo != null) {
			removedSessionInfo.destroy();
		}
	}

	public static SessionInfo getSessionInfo(RemoteProfile remoteProfile,
			Activity activity) {
		String id = remoteProfile.getID();
		synchronized (mapSessionInfo) {
			SessionInfo sessionInfo = mapSessionInfo.get(id);
			if (sessionInfo == null) {
				sessionInfo = new SessionInfo(activity, remoteProfile);
				mapSessionInfo.put(id, sessionInfo);
			}
			lastUsed = id;
			IVuzeEasyTracker vet = VuzeEasyTracker.getInstance();
			String rt = remoteProfile.isLocalHost() ? "L"
					: Integer.toString(remoteProfile.getRemoteType());
			vet.set("&cd2", remoteProfile.getRemoteTypeName());
			vet.setPage(rt);
			return sessionInfo;
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
			Log.d(TAG, "clearTorrentFilesCaches. " + numClears + " removed");
		}
	}

	public static SessionInfo findSessionInfo(Fragment fragment) {
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
		return SessionInfoManager.getSessionInfo(profileID, activity);
	}

	public static SessionInfo findSessionInfo(Activity activity, String TAG,
			boolean requireUIReady) {
		Intent intent = activity.getIntent();
		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
			return null;
		}

		SessionInfo sessionInfo = null;

		Bundle appData = intent.getBundleExtra(SearchManager.APP_DATA);
		if (appData != null) {
			String remoteProfileID = appData.getString(SessionInfoManager.BUNDLE_KEY);
			if (remoteProfileID != null) {
				sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID,
						activity);
			}
		} else {
			String remoteProfileID = extras.getString(SessionInfoManager.BUNDLE_KEY);
			if (remoteProfileID != null) {
				sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID,
						activity);
			}
		}

		if (sessionInfo == null) {
			Log.e(TAG, "sessionInfo NULL!");
		} else if (requireUIReady && !sessionInfo.isUIReady()) {
			Log.e(TAG, "UI NOT Ready!");
			return null;
		}

		return sessionInfo;
	}
}
