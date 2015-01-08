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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import jcifs.netbios.NbtAddress;

import org.apache.http.util.ByteArrayBuffer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.util.LongSparseArray;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.widget.Toast;

import com.aelitis.azureus.util.MapUtils;
import com.alibaba.fastjson.util.Base64;
import com.vuze.android.remote.NetworkState.NetworkStateListener;
import com.vuze.android.remote.activity.TorrentOpenOptionsActivity;
import com.vuze.android.remote.rpc.*;

/**
 * Access to all the information for a session, such as:<P>
 * - RemoteProfile<BR>
 * - SessionSettings<BR>
 * - RPC<BR>
 * - torrents<BR>
 */
public class SessionInfo
	implements SessionSettingsReceivedListener, NetworkStateListener
{
	private static final String TAG = "SessionInfo";

	public static interface RpcExecuter
	{
		public void executeRpc(TransmissionRPC rpc);
	}

	private static final String[] FILE_FIELDS_LOCALHOST = new String[] {};

	private static final String[] FILE_FIELDS_REMOTE = new String[] {
		TransmissionVars.FIELD_FILES_NAME,
		TransmissionVars.FIELD_FILES_LENGTH,
		TransmissionVars.FIELD_FILES_CONTENT_URL,
		TransmissionVars.FIELD_FILESTATS_BYTES_COMPLETED,
		TransmissionVars.FIELD_FILESTATS_PRIORITY,
		TransmissionVars.FIELD_FILESTATS_WANTED,
	};

	private static String[] SESSION_STATS_FIELDS = {
		TransmissionVars.TR_SESSION_STATS_DOWNLOAD_SPEED,
		TransmissionVars.TR_SESSION_STATS_UPLOAD_SPEED
	};

	private SessionSettings sessionSettings;

	private boolean activityVisible;

	private TransmissionRPC rpc;

	private final RemoteProfile remoteProfile;

	/** <Key, TorrentMap> */
	private LongSparseArray<Map<?, ?>> mapOriginal;

	private Object mLock = new Object();

	private List<TorrentListReceivedListener> torrentListReceivedListeners = new CopyOnWriteArrayList<TorrentListReceivedListener>();

	private List<SessionSettingsChangedListener> sessionSettingsChangedListeners = new CopyOnWriteArrayList<SessionSettingsChangedListener>();

	private List<RefreshTriggerListener> refreshTriggerListeners = new CopyOnWriteArrayList<RefreshTriggerListener>();

	private List<SessionInfoListener> availabilityListeners = new CopyOnWriteArrayList<SessionInfoListener>();

	private Handler handler;

	private boolean uiReady = false;

	private Map<?, ?> mapSessionStats;

	private LongSparseArray<Map<?, ?>> mapTags;

	private boolean refreshing;

	private String rpcRoot;

	private long lastTorrentWithFiles = -1;

	private List<RpcExecuter> rpcExecuteList = new ArrayList<>();

	private boolean needsFullTorrentRefresh = true;

	private String baseURL;

	private boolean needsTagRefresh = false;

	private Activity currentActivity;

	private Context context;

	protected long lastTorrentListReceivedOn;

	public SessionInfo(final Activity activity, final RemoteProfile _remoteProfile) {
		this.remoteProfile = _remoteProfile;
		this.mapOriginal = new LongSparseArray<>();

		VuzeRemoteApp.getNetworkState().addListener(this);

		// Bind and Open take a while, do it on the non-UI thread
		Thread thread = new Thread("bindAndOpen") {
			public void run() {
				String host = remoteProfile.getHost();
				if (host != null && host.length() > 0
						&& remoteProfile.getRemoteType() == RemoteProfile.TYPE_NORMAL) {
					open(activity, remoteProfile.getUser(), remoteProfile.getAC(),
							remoteProfile.getProtocol(), host, remoteProfile.getPort());
				} else {
					bindAndOpen(activity, remoteProfile.getAC(), remoteProfile.getUser());
				}
			}
		};
		thread.setDaemon(true);
		thread.start();

	}

	protected void bindAndOpen(Activity activity, final String ac,
			final String user) {

		RPC rpc = new RPC();
		try {
			Map<?, ?> bindingInfo = rpc.getBindingInfo(ac);

			Map<?, ?> error = MapUtils.getMapMap(bindingInfo, "error", null);
			if (error != null) {
				String errMsg = MapUtils.getMapString(error, "msg", "Unknown Error");
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Error from getBindingInfo " + errMsg);
				}

				AndroidUtils.showConnectionError(activity, errMsg, false);
				SessionInfoManager.removeSessionInfo(remoteProfile.getID());
				return;
			}

			String host = MapUtils.getMapString(bindingInfo, "ip", null);
			String protocol = MapUtils.getMapString(bindingInfo, "protocol", null);
			int port = Integer.valueOf(MapUtils.getMapString(bindingInfo, "port", "0"));

			if (host != null && protocol != null) {
				remoteProfile.setHost(host);
				remoteProfile.setPort(port);
				open(activity, "vuze", ac, protocol, host, port);
			}
		} catch (final RPCException e) {
			VuzeEasyTracker.getInstance(activity).logErrorNoLines(e);

			AndroidUtils.showConnectionError(activity, e, false);
			SessionInfoManager.removeSessionInfo(remoteProfile.getID());
		}
	}

	private void open(Activity activity, String user, final String ac,
			String protocol, String host, int port) {
		try {

			try {
				InetAddress.getByName(host);
			} catch (UnknownHostException e) {
				try {
					host = NbtAddress.getByName(host).getHostAddress();
				} catch (Throwable t) {
				}
			}

			rpcRoot = protocol + "://" + host + ":" + port + "/";
			String rpcUrl = rpcRoot + "transmission/rpc";

			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "rpc root = " + rpcRoot);
			}

			if (!AndroidUtils.isURLAlive(rpcUrl)) {
				AndroidUtils.showConnectionError(activity,
						R.string.error_remote_not_found, false);
				SessionInfoManager.removeSessionInfo(remoteProfile.getID());
				return;
			}

			AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
			remoteProfile.setLastUsedOn(System.currentTimeMillis());
			appPreferences.setLastRemote(remoteProfile);
			appPreferences.addRemoteProfile(remoteProfile);

			if (host.equals("127.0.0.1") || host.equals("localhost")) {
				baseURL = protocol + "://"
						+ VuzeRemoteApp.getNetworkState().getActiveIpAddress();
			} else {
				baseURL = protocol + "://" + host;
			}
			setRpc(new TransmissionRPC(this, rpcUrl, user, ac));
		} catch (Exception e) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "open", e);
			}
			VuzeEasyTracker.getInstance(activity).logError(e);
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.rpc.SessionSettingsReceivedListener#sessionPropertiesUpdated(java.util.Map)
	 */
	@Override
	public void sessionPropertiesUpdated(Map<?, ?> map) {
		SessionSettings settings = new SessionSettings();
		settings.setDLIsAuto(MapUtils.getMapBoolean(map,
				"speed-limit-down-enabled", true));
		settings.setULIsAuto(MapUtils.getMapBoolean(map, "speed-limit-up-enabled",
				true));
		settings.setDownloadDir(MapUtils.getMapString(map, "download-dir", null));

		settings.setDlSpeed(MapUtils.getMapLong(map, "speed-limit-down", 0));
		settings.setUlSpeed(MapUtils.getMapLong(map, "speed-limit-up", 0));

		sessionSettings = settings;

		for (SessionSettingsChangedListener l : sessionSettingsChangedListeners) {
			l.sessionSettingsChanged(settings);
		}

		if (!uiReady) {
			rpc.simpleRpcCall("tags-get-list", new ReplyMapReceivedListener() {

				@Override
				public void rpcSuccess(String id, Map<?, ?> optionalMap) {
					List<?> tagList = MapUtils.getMapList(optionalMap, "tags", null);
					if (tagList == null) {
						mapTags = null;
						setUIReady();
						return;
					}
					mapTags = new LongSparseArray<>(tagList.size());
					for (Object tag : tagList) {
						if (tag instanceof Map) {
							Map<?, ?> mapTag = (Map<?, ?>) tag;
							mapTags.put(MapUtils.getMapLong(mapTag, "uid", 0), mapTag);
						}
					}
					setUIReady();
				}

				@Override
				public void rpcFailure(String id, String message) {
					setUIReady();
				}

				@Override
				public void rpcError(String id, Exception e) {
					setUIReady();
				}
			});

		}
	}

	private void setUIReady() {
		uiReady = true;
		initRefreshHandler();
		if (needsFullTorrentRefresh) {
			triggerRefresh(false, null);
		}
		for (SessionInfoListener l : availabilityListeners) {
			l.uiReady(rpc);
		}
		availabilityListeners.clear();

		synchronized (rpcExecuteList) {
			for (RpcExecuter exec : rpcExecuteList) {
				try {
					exec.executeRpc(rpc);
				} catch (Throwable t) {
					VuzeEasyTracker.getInstance().logError(t);
				}
			}
			rpcExecuteList.clear();
		}
	}

	public Map<?, ?> getTag(Long uid) {
		if (mapTags == null) {
			return null;
		}
		Map<?, ?> map = mapTags.get(uid);
		if (map == null) {
			needsTagRefresh = true;
		}
		return map;
	}

	public List<Map<?, ?>> getTags() {
		if (mapTags == null) {
			return null;
		}

		ArrayList<Map<?, ?>> list = new ArrayList<Map<?, ?>>();

		synchronized (mLock) {
			for (int i = 0, num = mapTags.size(); i < num; i++) {
				list.add(mapTags.valueAt(i));
			}
		}
		return list;
	}

	/**
	 * @return the sessionSettings
	 */
	public SessionSettings getSessionSettings() {
		return sessionSettings;
	}

	/**
	 * Allows you to execute an RPC call, ensuring RPC is ready first (may
	 * not be called on same thread)
	 */
	public void executeRpc(RpcExecuter exec) {
		synchronized (rpcExecuteList) {
			if (!uiReady) {
				rpcExecuteList.add(exec);
				return;
			}
		}

		exec.executeRpc(rpc);
	}

	/**
	 * @return the remoteProfile
	 */
	public RemoteProfile getRemoteProfile() {
		return remoteProfile;
	}

	/**
	 * @param rpc the rpc to set
	 */
	public void setRpc(TransmissionRPC rpc) {
		if (this.rpc == rpc) {
			return;
		}

		if (this.rpc != null) {
			this.rpc.removeSessionSettingsReceivedListener(this);
		}

		this.rpc = rpc;

		if (rpc != null) {
			rpc.setDefaultFileFields(remoteProfile.isLocalHost()
					? FILE_FIELDS_LOCALHOST : FILE_FIELDS_REMOTE);

			for (SessionInfoListener l : availabilityListeners) {
				l.transmissionRpcAvailable(this);
			}

			rpc.addTorrentListReceivedListener(new TorrentListReceivedListener() {
				@Override
				public void rpcTorrentListReceived(String callID,
						List<?> addedTorrentMaps, List<?> removedTorrentIDs) {

					lastTorrentListReceivedOn = System.currentTimeMillis();
					// XXX If this is a full refresh, we should clear list!
					addRemoveTorrents(callID, addedTorrentMaps, removedTorrentIDs);
				}
			});

			rpc.addSessionSettingsReceivedListener(this);
		}
	}

	public long getLastTorrentListReceivedOn() {
		return lastTorrentListReceivedOn;
	}

	/*
	public HashMap<Object, Map<?, ?>> getTorrentList() {
		synchronized (mLock) {
			return new HashMap<Object, Map<?, ?>>(mapOriginal);
		}
	}
	*/

	/**
	 * Get all torrent maps.  Might be slow (walks tree)
	 */
	public List<Map<?, ?>> getTorrentList() {
		ArrayList<Map<?, ?>> list = new ArrayList<Map<?, ?>>();

		synchronized (mLock) {
			for (int i = 0, num = mapOriginal.size(); i < num; i++) {
				list.add(mapOriginal.valueAt(i));
			}
		}
		return list;
	}

	/*
	public long[] getTorrentListKeys() {
		synchronized (mLock) {
			int num = mapOriginal.size();
			long[] keys = new long[num];
			for(int i = 0; i < num; i++) {
				keys[i] = mapOriginal.keyAt(i);
			}
			return keys;
		}
	}
	*/

	public LongSparseArray<Map<?, ?>> getTorrentListSparseArray() {
		synchronized (mLock) {
			return mapOriginal.clone();
		}
	}

	public Map<?, ?> getTorrent(long id) {
		synchronized (mLock) {
			return mapOriginal.get(id);
		}
	}

	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	public void addRemoveTorrents(String callID, List<?> collection,
			List<?> removedTorrentIDs) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "adding torrents " + collection.size());
			if (removedTorrentIDs != null) {
				Log.d(TAG,
						"Removing Torrents " + Arrays.toString(removedTorrentIDs.toArray()));
			}
		}
		synchronized (mLock) {
			if (collection.size() > 0) {
				boolean addTorrentSilently = getRemoteProfile().isAddTorrentSilently();
				List<String> listOpenOptionHashes = addTorrentSilently ? null
						: remoteProfile.getOpenOptionsWaiterList();

				for (Object item : collection) {
					if (!(item instanceof Map)) {
						continue;
					}
					Map mapTorrent = (Map) item;
					Object key = mapTorrent.get("id");
					if (!(key instanceof Number)) {
						continue;
					}
					if (mapTorrent.size() == 1) {
						continue;
					}

					long torrentID = ((Number) key).longValue();

					Map old = mapOriginal.get(torrentID);
					mapOriginal.put(torrentID, mapTorrent);
					if (old != null) {
						// merge anything missing in new map with old
						for (Iterator iterator = old.keySet().iterator(); iterator.hasNext();) {
							Object torrentKey = iterator.next();
							if (!mapTorrent.containsKey(torrentKey)) {
								//System.out.println(key + " missing " + torrentKey);
								mapTorrent.put(torrentKey, old.get(torrentKey));
							}
						}

						if (mapTorrent.containsKey("files")) {
							lastTorrentWithFiles = torrentID;
						}

						// merge "fileStats" into "files"
						List<?> listFiles = MapUtils.getMapList(mapTorrent, "files", null);
						List<?> listFileStats = MapUtils.getMapList(mapTorrent,
								"fileStats", null);
						if (listFiles != null && listFileStats != null) {
							for (int i = 0; i < listFiles.size(); i++) {
								Map mapFile = (Map) listFiles.get(i);
								Map mapFileStats = (Map) listFileStats.get(i);
								mapFile.putAll(mapFileStats);
								if (!mapFile.containsKey("index")) {
									mapFile.put("index", i);
								}
								//mapFile.put("pathLevel", level);
							}
						}

						mergeList("files", mapTorrent, old);
					}

					if (!addTorrentSilently) {
						activateOpenOptionsDialog(torrentID, mapTorrent,
								listOpenOptionHashes);
					}
				}

				// only clean up open options after we got a non-empty torrent list,
				// AND after we've checked for matches (prevents an entry being removed
				// because it's "too old" when the user hasn't opened our app in a long
				// time)
				remoteProfile.cleanupOpenOptionsWaiterList();
			}

			if (removedTorrentIDs != null) {
				for (Object removedItem : removedTorrentIDs) {
					if (removedItem instanceof Number) {
						long torrentID = ((Number) removedItem).longValue();
						mapOriginal.remove(torrentID);
					}
				}
			}
		}

		for (TorrentListReceivedListener l : torrentListReceivedListeners) {
			l.rpcTorrentListReceived(callID, collection, removedTorrentIDs);
		}
	}

	private void activateOpenOptionsDialog(long torrentID, Map<?, ?> mapTorrent,
			List<String> listOpenOptionHashes) {
		if (listOpenOptionHashes.size() == 0) {
			return;
		}
		String hashString = MapUtils.getMapString(mapTorrent,
				TransmissionVars.FIELD_TORRENT_HASH_STRING, null);
		for (String waitingOn : listOpenOptionHashes) {
			if (!waitingOn.equalsIgnoreCase(hashString)) {
				continue;
			}

			long numFiles = MapUtils.getMapLong(mapTorrent,
					TransmissionVars.FIELD_TORRENT_FILE_COUNT, 0);
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Found waiting torrent " + hashString + " with " + numFiles
						+ " files");
			}

			if (numFiles <= 0) {
				continue;
			}

			Context context = currentActivity == null ? VuzeRemoteApp.getContext()
					: currentActivity;
			Intent intent = new Intent(Intent.ACTION_VIEW, null, context,
					TorrentOpenOptionsActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.putExtra(SessionInfoManager.BUNDLE_KEY, getRemoteProfile().getID());
			intent.putExtra("TorrentID", torrentID);

			try {
				context.startActivity(intent);

				remoteProfile.removeOpenOptionsWaiter(hashString);
				saveProfile();
				return;
			} catch (Throwable t) {
				// I imagine if we are trying to start an intent with 
				// a dead context, we'd get some sort of exception..
				// or does it magically create the activity anyway?
				VuzeEasyTracker.getInstance().logErrorNoLines(t);
			}
		}
	}

	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	private void mergeList(String key, Map mapTorrent, Map old) {
		List listOldFiles = MapUtils.getMapList(old, key, null);
		if (listOldFiles != null) {
			// files: merge special case
			List listUpdatedFiles = MapUtils.getMapList(mapTorrent, key, null);
			if (listUpdatedFiles != null) {
				List listNewFiles = new ArrayList(listOldFiles);
				for (Object oUpdatedFile : listUpdatedFiles) {
					if (!(oUpdatedFile instanceof Map)) {
						continue;
					}
					Map mapUpdatedFile = (Map) oUpdatedFile;
					int index = MapUtils.getMapInt(mapUpdatedFile, "index", -1);
					if (index < 0 || index >= listNewFiles.size()) {
						continue;
					}
					Map mapNewFile = (Map) listNewFiles.get(index);
					for (Object fileKey : mapUpdatedFile.keySet()) {
						mapNewFile.put(fileKey, mapUpdatedFile.get(fileKey));
					}
				}
				mapTorrent.put(key, listNewFiles);
			}
		}
	}

	public boolean addTorrentListReceivedListener(String callID,
			TorrentListReceivedListener l) {
		return addTorrentListReceivedListener(callID, l, true);
	}

	public boolean addTorrentListReceivedListener(TorrentListReceivedListener l,
			boolean fire) {
		return addTorrentListReceivedListener(TAG, l, fire);
	}

	public boolean addTorrentListReceivedListener(String callID,
			TorrentListReceivedListener l, boolean fire) {
		synchronized (torrentListReceivedListeners) {
			if (torrentListReceivedListeners.contains(l)) {
				return false;
			}
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "addTorrentListReceivedListener " + callID + "/" + l);
			}
			torrentListReceivedListeners.add(l);
			List<Map<?, ?>> torrentList = getTorrentList();
			if (torrentList.size() > 0 && fire) {
				l.rpcTorrentListReceived(callID, torrentList, null);
			}
		}
		return true;
	}

	public void removeTorrentListReceivedListener(TorrentListReceivedListener l) {
		synchronized (torrentListReceivedListeners) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "removeTorrentListReceivedListener " + l);
			}
			torrentListReceivedListeners.remove(l);
		}
	}

	public void saveProfile() {
		AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
		appPreferences.addRemoteProfile(remoteProfile);
	}

	public void updateSessionSettings(SessionSettings newSettings) {
		SessionSettings originalSettings = getSessionSettings();

		saveProfile();

		if (handler == null) {
			initRefreshHandler();
		}

		Map<String, Object> changes = new HashMap<String, Object>();
		if (newSettings.isDLAuto() != originalSettings.isDLAuto()) {
			changes.put("speed-limit-down-enabled", newSettings.isDLAuto());
		}
		if (newSettings.isULAuto() != originalSettings.isULAuto()) {
			changes.put("speed-limit-up-enabled", newSettings.isULAuto());
		}
		if (newSettings.getUlSpeed() != originalSettings.getUlSpeed()) {
			changes.put("speed-limit-up", newSettings.getUlSpeed());
		}
		if (newSettings.getDlSpeed() != originalSettings.getDlSpeed()) {
			changes.put("speed-limit-down", newSettings.getDlSpeed());
		}
		if (changes.size() > 0) {
			rpc.updateSettings(changes);
		}

		sessionSettings = newSettings;

		for (SessionSettingsChangedListener l : sessionSettingsChangedListeners) {
			l.sessionSettingsChanged(sessionSettings);
		}
	}

	private void cancelRefreshHandler() {
		if (handler == null) {
			return;
		}
		handler.removeCallbacksAndMessages(null);
		handler = null;
	}

	public void initRefreshHandler() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "initRefreshHandler");
		}
		if (handler != null) {
			return;
		}
		long interval = remoteProfile.calcUpdateInterval(context);
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Handler fires every " + interval);
		}
		if (interval <= 0) {
			cancelRefreshHandler();
			return;
		}
		handler = new Handler(Looper.getMainLooper());
		Runnable handlerRunnable = new Runnable() {
			public void run() {
				if (remoteProfile == null) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "Handler ignored: No remote profile");
					}
					return;
				}

				long interval = remoteProfile.calcUpdateInterval(context);
				if (interval <= 0) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "Handler ignored: update interval " + interval);
					}
					cancelRefreshHandler();
					return;
				}

				if (isActivityVisible()) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "Fire Handler");
					}
					triggerRefresh(true, null);

					for (RefreshTriggerListener l : refreshTriggerListeners) {
						l.triggerRefresh();
					}
				}

				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Handler fires in " + interval);
				}
				handler.postDelayed(this, interval * 1000);
			}
		};
		handler.postDelayed(handlerRunnable, interval * 1000);
	}

	public void triggerRefresh(final boolean recentOnly,
			final TorrentListReceivedListener l) {
		if (rpc == null) {
			return;
		}
		synchronized (mLock) {
			if (refreshing) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Refresh skipped. Already refreshing");
				}
				return;
			}
			refreshing = true;
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Refresh Triggered");
		}

		if (needsTagRefresh) {
			rpc.simpleRpcCall("tags-get-list", new ReplyMapReceivedListener() {

				@Override
				public void rpcSuccess(String id, Map<?, ?> optionalMap) {
					needsTagRefresh = false;
					List<?> tagList = MapUtils.getMapList(optionalMap, "tags", null);
					if (tagList == null) {
						mapTags = null;
						return;
					}
					mapTags = new LongSparseArray<>(tagList.size());
					for (Object tag : tagList) {
						if (tag instanceof Map) {
							Map<?, ?> mapTag = (Map<?, ?>) tag;
							mapTags.put(MapUtils.getMapLong(mapTag, "uid", 0), mapTag);
						}
					}
				}

				@Override
				public void rpcFailure(String id, String message) {
					needsTagRefresh = false;
				}

				@Override
				public void rpcError(String id, Exception e) {
					needsTagRefresh = false;
				}
			});
		}

		rpc.getSessionStats(SESSION_STATS_FIELDS, new ReplyMapReceivedListener() {
			@Override
			public void rpcSuccess(String id, Map<?, ?> optionalMap) {
				updateSessionStats(optionalMap);

				TorrentListReceivedListener listener = new TorrentListReceivedListener() {
					@Override
					public void rpcTorrentListReceived(String callID,
							List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
						synchronized (mLock) {
							refreshing = false;
						}
						if (l != null) {
							l.rpcTorrentListReceived(callID, addedTorrentMaps,
									removedTorrentIDs);
						}
					}
				};
				if (recentOnly && !needsFullTorrentRefresh) {
					rpc.getRecentTorrents(TAG, listener);
				} else {
					rpc.getAllTorrents(TAG, listener);
					needsFullTorrentRefresh = false;
				}
			}

			@Override
			public void rpcError(String id, Exception e) {
				synchronized (mLock) {
					refreshing = false;
				}
				if (l != null) {
					l.rpcTorrentListReceived("", Collections.emptyList(),
							Collections.emptyList());
				}
			}

			@Override
			public void rpcFailure(String id, String message) {
				synchronized (mLock) {
					refreshing = false;
				}
				if (l != null) {
					l.rpcTorrentListReceived("", Collections.emptyList(),
							Collections.emptyList());
				}
			}
		});
	}

	protected void updateSessionStats(Map<?, ?> map) {
		Map<?, ?> oldSessionStats = mapSessionStats;
		mapSessionStats = map;

/*
   string                     | value type
   ---------------------------+-------------------------------------------------
   "activeTorrentCount"       | number
   "downloadSpeed"            | number
   "pausedTorrentCount"       | number
   "torrentCount"             | number
   "uploadSpeed"              | number
   ---------------------------+-------------------------------+
   "cumulative-stats"         | object, containing:           |
                              +------------------+------------+
                              | uploadedBytes    | number     | tr_session_stats
                              | downloadedBytes  | number     | tr_session_stats
                              | filesAdded       | number     | tr_session_stats
                              | sessionCount     | number     | tr_session_stats
                              | secondsActive    | number     | tr_session_stats
   ---------------------------+-------------------------------+
   "current-stats"            | object, containing:           |
                              +------------------+------------+
                              | uploadedBytes    | number     | tr_session_stats
                              | downloadedBytes  | number     | tr_session_stats
                              | filesAdded       | number     | tr_session_stats
                              | sessionCount     | number     | tr_session_stats
                              | secondsActive    | number     | tr_session_stats
*/
		long oldDownloadSpeed = MapUtils.getMapLong(oldSessionStats,
				TransmissionVars.TR_SESSION_STATS_DOWNLOAD_SPEED, 0);
		long newDownloadSpeed = MapUtils.getMapLong(map,
				TransmissionVars.TR_SESSION_STATS_DOWNLOAD_SPEED, 0);
		long oldUploadSpeed = MapUtils.getMapLong(oldSessionStats,
				TransmissionVars.TR_SESSION_STATS_UPLOAD_SPEED, 0);
		long newUploadSpeed = MapUtils.getMapLong(map,
				TransmissionVars.TR_SESSION_STATS_UPLOAD_SPEED, 0);

		if (oldDownloadSpeed != newDownloadSpeed
				|| oldUploadSpeed != newUploadSpeed) {
			for (SessionSettingsChangedListener l : sessionSettingsChangedListeners) {
				l.speedChanged(newDownloadSpeed, newUploadSpeed);
			}
		}
	}

	public void addSessionSettingsChangedListeners(
			SessionSettingsChangedListener l) {
		synchronized (sessionSettingsChangedListeners) {
			if (!sessionSettingsChangedListeners.contains(l)) {
				sessionSettingsChangedListeners.add(l);
				if (sessionSettings != null) {
					l.sessionSettingsChanged(sessionSettings);
				}
			}
		}
	}

	public void removeSessionSettingsChangedListeners(
			SessionSettingsChangedListener l) {
		synchronized (sessionSettingsChangedListeners) {
			sessionSettingsChangedListeners.remove(l);
		}
	}

	/**
	 * add SessionInfoListener.  listener is only triggered once for each method,
	 * and then removed
	 */
	public void addRpcAvailableListener(SessionInfoListener l) {
		if (uiReady && rpc != null) {
			if (rpc != null) {
				l.transmissionRpcAvailable(this);
			}
			if (uiReady) {
				l.uiReady(rpc);
			}
		} else {
			synchronized (availabilityListeners) {
				if (availabilityListeners.contains(l)) {
					return;
				}
				availabilityListeners.add(l);
			}
			if (rpc != null) {
				l.transmissionRpcAvailable(this);
			}
		}
	}

	public void addRefreshTriggerListener(RefreshTriggerListener l) {
		if (refreshTriggerListeners.contains(l)) {
			return;
		}
		l.triggerRefresh();
		refreshTriggerListeners.add(l);
	}

	public void removeRefreshTriggerListener(RefreshTriggerListener l) {
		refreshTriggerListeners.remove(l);
	}

	@Override
	public void onlineStateChanged(boolean isOnline) {
		if (!uiReady) {
			return;
		}
		initRefreshHandler();
	}

	public String getRpcRoot() {
		return rpcRoot;
	}

	public boolean isActivityVisible() {
		return activityVisible;
	}

	public void activityResumed(Activity currentActivity) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "ActivityResumed. needsFullTorrentRefresh? "
					+ needsFullTorrentRefresh);
		}
		this.currentActivity = currentActivity;
		activityVisible = true;
		if (needsFullTorrentRefresh) {
			triggerRefresh(false, null);
		}
	}

	public void activityPaused() {
		currentActivity = null;
		activityVisible = false;
	}

	public void clearTorrentCache() {
		synchronized (mLock) {
			mapOriginal.clear();
			needsFullTorrentRefresh = true;
		}
	}

	public int clearTorrentFilesCaches(boolean keepLastUsedTorrentFiles) {
		int num = 0;
		synchronized (mLock) {
			int size = mapOriginal.size();
			if (size == 0) {
				return num;
			}
			for (int i = size - 1; i >= 0; i--) {
				long torrentID = mapOriginal.keyAt(i);
				if (keepLastUsedTorrentFiles && lastTorrentWithFiles == torrentID) {
					continue;
				}
				Map<?, ?> map = mapOriginal.valueAt(i);
				if (map.containsKey("files")) {
					map.remove("files");
					num++;
				}
			}
		}
		return num;
	}

	/**
	 * 
	 * @return -1 == Not Vuze; 0 == Vuze
	 */
	public int getRPCVersionAZ() {
		return rpc == null ? -1 : rpc.getRPCVersionAZ();
	}

	public boolean getSupportsRCM() {
		return rpc == null ? false : rpc.getSupportsRCM();
	}

	public boolean getSupportsTorrentRename() {
		return rpc == null ? false : rpc.getSupportsTorrentRename();
	}

	public String getBaseURL() {
		return baseURL;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.DialogFragmentOpenTorrent.OpenTorrentDialogListener#openTorrent(java.lang.String)
	 */
	public void openTorrent(final Activity activity, final String sTorrentURL,
			final String friendlyName) {
		if (sTorrentURL == null || sTorrentURL.length() == 0) {
			return;
		}
		executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.addTorrentByUrl(sTorrentURL, true,
						new TorrentAddedReceivedListener2(SessionInfo.this, activity, true,
								sTorrentURL));
			}
		});
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Context context = activity.isFinishing() ? VuzeRemoteApp.getContext()
						: activity;
				String s = context.getResources().getString(R.string.toast_adding_xxx,
						friendlyName == null ? sTorrentURL : friendlyName);
				Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
			}
		});

		VuzeEasyTracker.getInstance(activity).sendEvent("RemoteAction",
				"AddTorrent", "AddTorrentByUrl", null);
	}

	public void openTorrent(final Activity activity, final String name,
			InputStream is) {
		try {
			int available = is.available();
			if (available <= 0) {
				available = 32 * 1024;
			}
			ByteArrayBuffer bab = new ByteArrayBuffer(available);

			boolean ok = AndroidUtils.readInputStreamIfStartWith(is, bab, new byte[] {
				'd'
			});
			if (!ok) {
				String s;
				if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT
						&& bab.length() == 0) {
					s = activity.getResources().getString(
							R.string.not_torrent_file_kitkat, name);
				} else {
					s = activity.getResources().getString(R.string.not_torrent_file,
							name, Math.max(bab.length(), 5));
				}
				AndroidUtils.showDialog(activity, R.string.add_torrent,
						Html.fromHtml(s));
				return;
			}
			final String metainfo = Base64Encode.encodeToString(bab.buffer(), 0,
					bab.length());
			openTorrentWithMetaData(activity, name, metainfo);
		} catch (IOException e) {
			if (AndroidUtils.DEBUG) {
				e.printStackTrace();
			}
			VuzeEasyTracker.getInstance(activity).logError(e);
		} catch (OutOfMemoryError em) {
			VuzeEasyTracker.getInstance(activity).logError(em);
			AndroidUtils.showConnectionError(activity, "Out of Memory", true);
		}
	}

	private void openTorrentWithMetaData(final Activity activity,
			final String name, final String metainfo) {
		executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.addTorrentByMeta(metainfo, true, new TorrentAddedReceivedListener2(
						SessionInfo.this, activity, true, null));
			}
		});
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Context context = activity.isFinishing() ? VuzeRemoteApp.getContext()
						: activity;
				String s = context.getResources().getString(R.string.toast_adding_xxx,
						name);
				Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
			}
		});
		VuzeEasyTracker.getInstance(activity).sendEvent("RemoteAction",
				"AddTorrent", "AddTorrentByMeta", null);
	}

	public void openTorrent(final Activity activity, Uri uri) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "openTorrent " + uri);
		}
		if (uri == null) {
			return;
		}
		String scheme = uri.getScheme();
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "openTorrent " + scheme);
		}
		if ("file".equals(scheme) || "content".equals(scheme)) {
			try {
				InputStream stream = null;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
					String realPath = PaulBurkeFileUtils.getPath(activity, uri);
					if (realPath != null) {
						String meh = realPath.startsWith("/") ? "file://" + realPath
								: realPath;
						stream = activity.getContentResolver().openInputStream(
								Uri.parse(meh));
					}
				}
				if (stream == null) {
					ContentResolver contentResolver = activity.getContentResolver();
					stream = contentResolver.openInputStream(uri);
				}
				openTorrent(activity, uri.toString(), stream);
			} catch (FileNotFoundException e) {
				if (AndroidUtils.DEBUG) {
					e.printStackTrace();
				}
				VuzeEasyTracker.getInstance(activity).logError(e);
				Toast.makeText(activity, Html.fromHtml("<b>" + uri + "</b> not found"),
						Toast.LENGTH_LONG).show();
			}
		} else {
			openTorrent(activity, uri.toString(), (String) null);
		}
	}

	private static class TorrentAddedReceivedListener2
		implements TorrentAddedReceivedListener
	{
		private SessionInfo sessionInfo;

		private Activity activity;

		private boolean showOptions;

		private String url;

		public TorrentAddedReceivedListener2(SessionInfo sessionInfo,
				Activity activity, boolean showOptions, String url) {
			this.sessionInfo = sessionInfo;
			this.activity = activity;
			this.showOptions = showOptions;
			this.url = url;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public void torrentAdded(final Map mapTorrentAdded, boolean duplicate) {
			if (!showOptions) {
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						String name = MapUtils.getMapString(mapTorrentAdded, "name",
								"Torrent");
						Context context = activity.isFinishing()
								? VuzeRemoteApp.getContext() : activity;
						String s = context.getResources().getString(R.string.toast_added,
								name);
						Toast.makeText(context, s, Toast.LENGTH_LONG).show();
					}
				});
			} else {
				String hashString = MapUtils.getMapString(mapTorrentAdded,
						"hashString", "");
				if (hashString.length() > 0) {
					sessionInfo.getRemoteProfile().addOpenOptionsWaiter(hashString);
					sessionInfo.saveProfile();
				}
			}
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					long id = MapUtils.getMapLong(mapTorrentAdded, "id", -1);
					if (id >= 0) {
						List<String> fields = new ArrayList<>(rpc.getBasicTorrentFieldIDs());
						if (showOptions) {
							fields.add(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR);
							fields.add("files");
							fields.add("fileStats");
						}
						rpc.getTorrent(TAG, id, fields, null);
					} else {
						rpc.getRecentTorrents(TAG, null);
					}
				}
			});
		}

		/* (non-Javadoc)
		 * @see com.vuze.android.remote.rpc.TorrentAddedReceivedListener#torrentAddFailed(java.lang.String)
		 */
		@Override
		public void torrentAddFailed(String message) {
			try {
				if (url != null && url.startsWith("http")) {
					ByteArrayBuffer bab = new ByteArrayBuffer(32 * 1024);

					boolean ok = AndroidUtils.readURL(url, bab, new byte[] {
						'd'
					});
					if (ok) {
						final String metainfo = Base64Encode.encodeToString(bab.buffer(), 0,
								bab.length());
						sessionInfo.openTorrentWithMetaData(activity, url, metainfo);
					} else {
						showUrlFailedDialog(activity, message, url, new String(
								bab.buffer(), 0, 5));
					}
					return;
				}
			} catch (Throwable t) {
			}
			AndroidUtils.showDialog(activity, R.string.add_torrent, message);
		}

		@Override
		public void torrentAddError(Exception e) {

			AndroidUtils.showConnectionError(activity, e.getMessage(), true);
		}
	}

	public static void showUrlFailedDialog(final Activity activity,
			final String errMsg, final String url, final String sample) {
		if (activity == null) {
			Log.e(null, "No activity for error message " + errMsg);
			return;
		}
		activity.runOnUiThread(new Runnable() {
			public void run() {
				if (activity.isFinishing()) {
					return;
				}
				String s = activity.getResources().getString(
						R.string.torrent_url_add_failed, url, sample);

				Spanned msg = Html.fromHtml(s);
				Builder builder = new AlertDialog.Builder(activity).setMessage(msg).setCancelable(
						true).setNegativeButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
							}
						}).setNeutralButton(R.string.torrent_url_add_failed_openurl,
						new OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
								activity.startActivity(intent);
							}
						});
				builder.show();
			}
		});

	}

	public void moveDataTo(final long id, final String s) {
		executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.moveTorrent(id, s, null);

				VuzeEasyTracker.getInstance().sendEvent("RemoteAction", "MoveData",
						null, null);
			}
		});
	}

	public void moveDataHistoryChanged(ArrayList<String> history) {
		if (remoteProfile == null) {
			return;
		}
		remoteProfile.setSavePathHistory(history);
		saveProfile();
	}

	public Activity getCurrentActivity() {
		return currentActivity;
	}
}
