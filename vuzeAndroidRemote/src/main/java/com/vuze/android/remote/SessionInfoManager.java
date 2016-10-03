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

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.vuze.android.remote.fragment.SessionInfoGetter;

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
					VuzeEasyTracker.getInstance(activity).logError(
							"Missing RemoteProfile."
									+ (profileID == null ? "null" : profileID.length()) + "."
									+ VuzeRemoteApp.getAppPreferences().getNumRemotes() + " @ "
									+ AndroidUtils.getCompressedStackTrace(),
							null);
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
		synchronized (mapSessionInfo) {
			mapSessionInfo.remove(id);
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
}
