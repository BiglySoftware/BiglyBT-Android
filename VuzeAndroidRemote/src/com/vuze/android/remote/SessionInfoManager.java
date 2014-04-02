package com.vuze.android.remote;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.util.Log;

public class SessionInfoManager
{
	private static final String TAG = "SessionInfoManager";

	public static String BUNDLE_KEY = "RemoteProfileID";

	private static Map<String, SessionInfo> mapSessionInfo = new HashMap<String, SessionInfo>();

	private static String lastUsed;

	public static SessionInfo getSessionInfo(String id, Activity activity,
			boolean rememberSettingChanges) {
		synchronized (mapSessionInfo) {
			SessionInfo sessionInfo = mapSessionInfo.get(id);
			if (sessionInfo == null) {
				RemoteProfile remoteProfile = VuzeRemoteApp.getAppPreferences().getRemote(
						id);

				if (remoteProfile == null) {
					Log.d("SessionInfo", "No SessionInfo for " + id);
					return null;
				}
				sessionInfo = new SessionInfo(activity, remoteProfile,
						rememberSettingChanges);
				mapSessionInfo.put(id, sessionInfo);
				Log.d("SessionInfo", "setting SessionInfo for " + id);

			}
			lastUsed = id;
			return sessionInfo;
		}
	}
	
	public static void removeSessionInfo(String id) {
		lastUsed = null;
		synchronized (mapSessionInfo) {
			mapSessionInfo.remove(id);
		}
	}

	public static SessionInfo getSessionInfo(RemoteProfile remoteProfile,
			Activity activity, boolean rememberSettingChanges) {
		String id = remoteProfile.getID();
		synchronized (mapSessionInfo) {
			SessionInfo sessionInfo = mapSessionInfo.get(id);
			if (sessionInfo == null) {
				sessionInfo = new SessionInfo(activity, remoteProfile,
						rememberSettingChanges);
				mapSessionInfo.put(id, sessionInfo);
			}
			lastUsed = id;
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
				numClears += sessionInfo.clearTorrentFilesCaches(keepLastUsedTorrentFiles);
			}
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "clearTorrentFilesCaches. " + numClears + " removed");
		}
	}
}
