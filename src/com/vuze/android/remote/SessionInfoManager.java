package com.vuze.android.remote;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.util.Log;

import com.google.analytics.tracking.android.Fields;

public class SessionInfoManager
{
	private static final String TAG = "SessionInfoManager";

	public static String BUNDLE_KEY = "RemoteProfileID";

	private static Map<String, SessionInfo> mapSessionInfo = new HashMap<String, SessionInfo>();

	private static String lastUsed;

	public static SessionInfo getSessionInfo(String profileID, Activity activity) {
		synchronized (mapSessionInfo) {
			SessionInfo sessionInfo = mapSessionInfo.get(profileID);
			RemoteProfile remoteProfile;
			if (sessionInfo == null) {
				remoteProfile = VuzeRemoteApp.getAppPreferences().getRemote(profileID);

				if (remoteProfile == null) {
					if (AndroidUtils.DEBUG) { 
						Log.e(TAG, "No SessionInfo for " + profileID);
					}
					return null;
				}
				sessionInfo = new SessionInfo(activity, remoteProfile);
				mapSessionInfo.put(profileID, sessionInfo);
			} else {
				remoteProfile = sessionInfo.getRemoteProfile();
			}
			lastUsed = profileID;
			VuzeEasyTracker vet = VuzeEasyTracker.getInstance();
			String rt = remoteProfile.isLocalHost() ? "L"
					: Integer.toString(remoteProfile.getRemoteType());
			vet.set(Fields.CLIENT_ID, rt);
			vet.set(Fields.PAGE, rt);
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
			VuzeEasyTracker vet = VuzeEasyTracker.getInstance();
			String rt = remoteProfile.isLocalHost() ? "L"
					: Integer.toString(remoteProfile.getRemoteType());
			vet.set(Fields.CLIENT_ID, rt);
			vet.set(Fields.PAGE, rt);
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
