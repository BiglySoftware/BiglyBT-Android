/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.util.ByteArrayBuffer;

import com.vuze.android.remote.NetworkState.NetworkStateListener;
import com.vuze.android.remote.activity.TorrentOpenOptionsActivity;
import com.vuze.android.remote.rpc.*;
import com.vuze.android.widget.CustomToast;
import com.vuze.util.MapUtils;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.*;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;
import android.text.Spanned;
import android.util.Log;
import android.widget.Toast;

import net.i2p.android.ui.I2PAndroidHelper;

import jcifs.netbios.NbtAddress;

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
	static final String TAG = "SessionInfo";

	public interface RpcExecuter
	{
		void executeRpc(TransmissionRPC rpc);
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

	/* @Thunk */ TransmissionRPC rpc;

	/* @Thunk */ final RemoteProfile remoteProfile;

	/** <Key, TorrentMap> */
	private LongSparseArray<Map<?, ?>> mapOriginal;

	private final Object mLock = new Object();

	private final List<TorrentListRefreshingListener> torrentListRefreshingListeners = new CopyOnWriteArrayList<>();

	private final List<TorrentListReceivedListener> torrentListReceivedListeners = new CopyOnWriteArrayList<>();

	private final List<SessionSettingsChangedListener> sessionSettingsChangedListeners = new CopyOnWriteArrayList<>();

	/* @Thunk */ final List<RefreshTriggerListener> refreshTriggerListeners = new CopyOnWriteArrayList<>();

	private final List<SessionInfoListener> availabilityListeners = new CopyOnWriteArrayList<>();

	private final List<TagListReceivedListener> tagListReceivedListeners = new CopyOnWriteArrayList<>();

	/* @Thunk */ Handler handler;

	private boolean uiReady = false;

	private Map<?, ?> mapSessionStats;

	/* @Thunk */ LongSparseArray<Map<?, ?>> mapTags;

	// >> Subscription
	private final List<SubscriptionListReceivedListener> subscriptionListReceivedListeners = new CopyOnWriteArrayList<>();

	// <SubscriptionID, Map<Key, Value>>
	/* @Thunk */ Map<String, Map<?, ?>> mapSubscriptions;

	private long lastSubscriptionListReceivedOn;

	private boolean refreshingSubscriptionList;

	// << Subscription

	private boolean refreshingTorrentList;

	private String rpcRoot;

	/**
	 * Store the last torrent id that was retrieved with file info, so when we
	 * are clearing the cache due to memory contraints, we can keep that last
	 * one.
	 */
	private long lastTorrentWithFiles = -1;

	private final List<RpcExecuter> rpcExecuteList = new ArrayList<>();

	/* @Thunk */ boolean needsFullTorrentRefresh = true;

	private String baseURL;

	/* @Thunk */ boolean needsTagRefresh = false;

	/* @Thunk */ Activity currentActivity;

	private Context context;

	protected long lastTorrentListReceivedOn;

	private Long tagAllUID = null;

	private boolean destroyed = false;

	public SessionInfo(final Activity activity,
			final RemoteProfile _remoteProfile) {
		this.remoteProfile = _remoteProfile;
		this.mapOriginal = new LongSparseArray<>();

		VuzeRemoteApp.getNetworkState().addListener(this);

		// Bind and Open take a while, do it on the non-UI thread
		Thread thread = new Thread("bindAndOpen") {
			public void run() {
				String host = remoteProfile.getHost();
				if (host != null && host.length() > 0
						&& remoteProfile.getRemoteType() != RemoteProfile.TYPE_LOOKUP) {
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

	protected void bindAndOpen(final Activity activity, final String ac,
			final String user) {

		RPC rpc = new RPC();
		try {
			Map<?, ?> bindingInfo = rpc.getBindingInfo(ac, remoteProfile);

			Map<?, ?> error = MapUtils.getMapMap(bindingInfo, "error", null);
			if (error != null) {
				String errMsg = MapUtils.getMapString(error, "msg", "Unknown Error");
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Error from getBindingInfo " + errMsg);
				}

				AndroidUtilsUI.showConnectionError(activity, errMsg, false);
				SessionInfoManager.removeSessionInfo(remoteProfile.getID());
				return;
			}

			final String host = MapUtils.getMapString(bindingInfo, "ip", null);
			final String protocol = MapUtils.getMapString(bindingInfo, "protocol",
					null);
			final String i2p = MapUtils.getMapString(bindingInfo, "i2p", null);
			final int port = (int) MapUtils.parseMapLong(bindingInfo, "port", 0);

			final boolean requireI2PviaInternet = false;
			if (port != 0) {
				if (i2p != null) {
					final I2PAndroidHelper i2pHelper = new I2PAndroidHelper(activity);
					if (i2pHelper.isI2PAndroidInstalled()) {
						i2pHelper.bind(new I2PAndroidHelper.Callback() {
							@Override
							public void onI2PAndroidBound() {
								// We are now on the UI Thread :(
								new Thread(new Runnable() {
									@Override
									public void run() {
										SessionInfo.this.onI2PAndroidBound(i2pHelper, activity, ac,
												protocol, host, port, i2p, false);
									}
								}).start();
							}
						});
						return;
					} else if (requireI2PviaInternet) {
						AndroidUtilsUI.showConnectionError(activity,
								"I2P App not installed", false);
						SessionInfoManager.removeSessionInfo(remoteProfile.getID());
						i2pHelper.unbind();
						return;
					} else if (AndroidUtils.DEBUG) {
						i2pHelper.unbind();
						Log.d(TAG, "onI2PAndroidBound: I2P not installed");
					}
				}
				if (open(activity, "vuze", ac, protocol, host, port)) {
					Map lastBindingInfo = new HashMap();
					lastBindingInfo.put("ip", host);
					lastBindingInfo.put("i2p", i2p);
					lastBindingInfo.put("port", port);
					lastBindingInfo.put("protocol",
							protocol == null || protocol.length() == 0 ? "http" : protocol);
					remoteProfile.setLastBindingInfo(lastBindingInfo);
					saveProfile();
				}
			}
		} catch (final RPCException e) {
			VuzeEasyTracker.getInstance(activity).logErrorNoLines(e);

			AndroidUtilsUI.showConnectionError(activity, e, false);
			SessionInfoManager.removeSessionInfo(remoteProfile.getID());
		}
	}

	private void onI2PAndroidBound(final I2PAndroidHelper i2pHelper,
			final Activity activity, String ac, String protocol, String host,
			int port, String i2p, boolean requireI2PviaInternet) {
		boolean isI2PRunning = i2pHelper.isI2PAndroidRunning();

		if (!isI2PRunning) {
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					i2pHelper.requestI2PAndroidStart(activity);
				}
			});
			if (requireI2PviaInternet) {
				AndroidUtilsUI.showConnectionError(activity, "I2P App not running",
						false);
				SessionInfoManager.removeSessionInfo(remoteProfile.getID());
				i2pHelper.unbind();
				return;
			} else if (AndroidUtils.DEBUG) {
				Log.d(TAG, "onI2PAndroidBound: I2P not running");
			}
		}
		i2pHelper.unbind();

		if (isI2PRunning ? open(activity, "vuze", ac, "http", i2p, port)
				: open(activity, "vuze", ac, protocol, host, port)) {
			Map lastBindingInfo = new HashMap();
			lastBindingInfo.put("ip", host);
			lastBindingInfo.put("i2p", i2p);
			lastBindingInfo.put("port", port);
			lastBindingInfo.put("protocol",
					protocol == null || protocol.length() == 0 ? "http" : protocol);
			remoteProfile.setLastBindingInfo(lastBindingInfo);
			saveProfile();
		}
	}

	/* @Thunk */ boolean open(final Activity activity, String user,
			final String ac, String protocol, String host, int port) {
		try {

			boolean isLocalHost = "localhost".equals(host);

			try {
				//noinspection ResultOfMethodCallIgnored
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

			if (isLocalHost && port == 9092 && VuzeRemoteApp.isCoreAllowed()) {
				// wait for Vuze Core to initialize
				// We should be on non-main thread
				// TODO check
				VuzeRemoteApp.waitForCore(activity, 15000);
			}

			if (!host.endsWith(".i2p") && !AndroidUtils.isURLAlive(rpcUrl)) {
				AndroidUtilsUI.showConnectionError(activity,
						R.string.error_remote_not_found, false);
				SessionInfoManager.removeSessionInfo(remoteProfile.getID());
				return false;
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
			return true;
		} catch (Exception e) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "open", e);
			}
			VuzeEasyTracker.getInstance(activity).logError(e);
		}
		return false;
	}

	@Override
	public void sessionPropertiesUpdated(Map<?, ?> map) {
		SessionSettings settings = new SessionSettings();
		settings.setDLIsAuto(
				MapUtils.getMapBoolean(map, "speed-limit-down-enabled", true));
		settings.setULIsAuto(
				MapUtils.getMapBoolean(map, "speed-limit-up-enabled", true));
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

					placeTagListIntoMap(tagList);

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

		if (currentActivity != null) {
			String message = MapUtils.getMapString(map, "az-message", null);
			if (message != null && message.length() > 0) {
				AndroidUtilsUI.showDialog(currentActivity,
						R.string.title_message_from_client, AndroidUtils.fromHTML(message));
			}
		}
	}

	/* @Thunk */ void placeTagListIntoMap(List<?> tagList) {
		// put new list of tags into mapTags.  Update the existing tag Map in case
		// some other part of the app stored a reference to it.
		synchronized (mLock) {
			int numUserCategories = 0;
			long uidUncat = -1;
			LongSparseArray mapNewTags = new LongSparseArray<>(tagList.size());
			for (Object tag : tagList) {
				if (tag instanceof Map) {
					Map<?, ?> mapNewTag = (Map<?, ?>) tag;
					Long uid = MapUtils.getMapLong(mapNewTag, "uid", 0);
					Map mapOldTag = mapTags == null ? null : mapTags.get(uid);
					if (mapNewTag.containsKey("name")) {
						if (mapOldTag == null) {
							mapNewTags.put(uid, mapNewTag);
						} else {
							mapOldTag.clear();
							mapOldTag.putAll(mapNewTag);
							mapNewTags.put(uid, mapOldTag);
						}
					} else {
						long count = MapUtils.getMapLong(mapNewTag, "count", -1);
						if (count >= 0 && mapOldTag != null) {
							mapOldTag.put("count", count);
						}
						mapNewTags.put(uid, mapOldTag);
					}

					int type = MapUtils.getMapInt(mapNewTag, "type", 0);
					//category
					if (type == 1) {
						// USER=0,ALL=1,UNCAT=2
						int catType = MapUtils.getMapInt(mapNewTag, "category-type", -1);
						if (catType == 0) {
							numUserCategories++;
						} else if (catType == 1) {
							tagAllUID = uid;
						} else if (catType == 2) {
							uidUncat = uid;
						}
					}
				}
			}

			if (numUserCategories == 0 && uidUncat >= 0) {
				mapNewTags.remove(uidUncat);
			}

			mapTags = mapNewTags;
		}

		if (tagListReceivedListeners.size() > 0) {
			List<Map<?, ?>> tags = getTags();
			for (TagListReceivedListener l : tagListReceivedListeners) {
				l.tagListReceived(tags);
			}
		}
	}

	/* @Thunk */ void setUIReady() {
		uiReady = true;

		IVuzeEasyTracker vet = VuzeEasyTracker.getInstance();
		String rpcVersion = rpc.getRPCVersion() + "/" + rpc.getRPCVersionAZ();
		vet.set("&cd3", rpcVersion);

		initRefreshHandler();
		if (needsFullTorrentRefresh) {
			triggerRefresh(false);
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

	public boolean isUIReady() {
		ensureNotDestroyed();

		return uiReady;
	}

	public Long getTagAllUID() {
		ensureNotDestroyed();
		return tagAllUID;
	}

	@Nullable
	public Map<?, ?> getTag(Long uid) {
		ensureNotDestroyed();

		if (uid < 10) {
			AndroidUtils.ValueStringArray basicTags = AndroidUtils.getValueStringArray(
					VuzeRemoteApp.getContext().getResources(), R.array.filterby_list);
			for (int i = 0; i < basicTags.size; i++) {
				if (uid == basicTags.values[i]) {
					Map map = new HashMap();
					map.put("uid", uid);
					String name = basicTags.strings[i].replaceAll("Download State: ", "");
					map.put("name", name);
					return map;
				}
			}
		}
		if (mapTags == null) {
			return null;
		}
		Map<?, ?> map = mapTags.get(uid);
		if (map == null) {
			needsTagRefresh = true;
		}
		return map;
	}

	@Nullable
	public List<Map<?, ?>> getTags() {
		ensureNotDestroyed();

		if (mapTags == null) {
			return null;
		}

		ArrayList<Map<?, ?>> list = new ArrayList<>();

		synchronized (mLock) {
			for (int i = 0, num = mapTags.size(); i < num; i++) {
				list.add(mapTags.valueAt(i));
			}
		}
		Collections.sort(list, new Comparator<Map<?, ?>>() {
			@Override
			public int compare(Map<?, ?> lhs, Map<?, ?> rhs) {
				int lType = MapUtils.getMapInt(lhs, "type", 0);
				int rType = MapUtils.getMapInt(rhs, "type", 0);
				if (lType < rType) {
					return -1;
				}
				if (lType > rType) {
					return 1;
				}

				String lhGroup = MapUtils.getMapString(lhs, "group", "");
				String rhGroup = MapUtils.getMapString(rhs, "group", "");
				int i = lhGroup.compareToIgnoreCase(rhGroup);
				if (i != 0) {
					return i;
				}

				String lhName = MapUtils.getMapString(lhs, "name", "");
				String rhName = MapUtils.getMapString(rhs, "name", "");
				return lhName.compareToIgnoreCase(rhName);
			}
		});
		return list;
	}

	/**
	 * @return the sessionSettings
	 */
	public @Nullable SessionSettings getSessionSettings() {
		ensureNotDestroyed();
		return sessionSettings;
	}

	/**
	 * Allows you to execute an RPC call, ensuring RPC is ready first (may
	 * not be called on same thread)
	 */
	public void executeRpc(RpcExecuter exec) {
		ensureNotDestroyed();

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
		ensureNotDestroyed();

		ArrayList<Map<?, ?>> list = new ArrayList<>();

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
		ensureNotDestroyed();

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
	public void addRemoveTorrents(String callID, List<?> addedTorrentIDs,
			List<?> removedTorrentIDs) {
		ensureNotDestroyed();

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "adding torrents " + addedTorrentIDs.size());
			if (removedTorrentIDs != null) {
				Log.d(TAG, "Removing Torrents "
						+ Arrays.toString(removedTorrentIDs.toArray()));
			}
		}
		int numAddedOrRemoved = 0;
		synchronized (mLock) {
			if (addedTorrentIDs.size() > 0) {
				numAddedOrRemoved = addedTorrentIDs.size();
				boolean addTorrentSilently = getRemoteProfile().isAddTorrentSilently();
				List<String> listOpenOptionHashes = addTorrentSilently ? null
						: remoteProfile.getOpenOptionsWaiterList();

				for (Object item : addedTorrentIDs) {
					if (!(item instanceof Map)) {
						continue;
					}
					Map mapUpdatedTorrent = (Map) item;
					Object key = mapUpdatedTorrent.get("id");
					if (!(key instanceof Number)) {
						continue;
					}
					if (mapUpdatedTorrent.size() == 1) {
						continue;
					}

					long torrentID = ((Number) key).longValue();

					Map old = mapOriginal.get(torrentID);
					mapOriginal.put(torrentID, mapUpdatedTorrent);

					if (mapUpdatedTorrent.containsKey(
							TransmissionVars.FIELD_TORRENT_FILES)) {
						lastTorrentWithFiles = torrentID;
					}

					// TODO: Send param to Vuze remote client to ensure it doesn't
					// escape!
					for (Object torrentKey : mapUpdatedTorrent.keySet()) {
						Object o = mapUpdatedTorrent.get(torrentKey);
						if (o instanceof String) {
							mapUpdatedTorrent.put(torrentKey,
									AndroidUtils.unescapeXML((String) o));
						}
					}

					if (old != null) {
						// merge anything missing in new map with old
						for (Object torrentKey : old.keySet()) {
							if (!mapUpdatedTorrent.containsKey(torrentKey)) {
								//System.out.println(key + " missing " + torrentKey);
								mapUpdatedTorrent.put(torrentKey, old.get(torrentKey));
							}
						}
					}

					List<?> listFiles = MapUtils.getMapList(mapUpdatedTorrent,
							TransmissionVars.FIELD_TORRENT_FILES, null);

					if (listFiles != null) {

						// merge "fileStats" into "files"
						List<?> listFileStats = MapUtils.getMapList(mapUpdatedTorrent,
								TransmissionVars.FIELD_TORRENT_FILESTATS, null);
						if (listFileStats != null) {
							for (int i = 0; i < listFiles.size(); i++) {
								Map mapFile = (Map) listFiles.get(i);
								Map mapFileStats = (Map) listFileStats.get(i);
								mapFile.putAll(mapFileStats);
							}
							mapUpdatedTorrent.remove(
									TransmissionVars.FIELD_TORRENT_FILESTATS);
						}

						// add an "index" key, for places that only get the file map
						// and has no reference to index
						for (int i = 0; i < listFiles.size(); i++) {
							Map mapFile = (Map) listFiles.get(i);
							if (!mapFile.containsKey(TransmissionVars.FIELD_FILES_INDEX)) {
								mapFile.put(TransmissionVars.FIELD_FILES_INDEX, i);
							} else {
								// assume if one has index, they all do
								break;
							}
						}
					}

					if (old != null) {
						mergeList(TransmissionVars.FIELD_TORRENT_FILES, mapUpdatedTorrent,
								old);
					}

					if (!addTorrentSilently) {
						activateOpenOptionsDialog(torrentID, mapUpdatedTorrent,
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
						if (mapOriginal.indexOfKey(torrentID) >= 0) {
							mapOriginal.remove(torrentID);
							numAddedOrRemoved++;
						} else {
							if (AndroidUtils.DEBUG) {
								Log.d(TAG, "addRemoveTorrents: Can't remove " + torrentID
										+ ". Doesn't exist");
							}
						}
					}
				}
			}
		}

		if (numAddedOrRemoved > 0) {
			refreshTags(true);
		}

		for (TorrentListReceivedListener l : torrentListReceivedListeners) {
			l.rpcTorrentListReceived(callID, addedTorrentIDs, removedTorrentIDs);
		}
	}

	/* @Thunk */ void setRefreshingTorrentList(boolean refreshingTorrentList) {
		synchronized (mLock) {
			this.refreshingTorrentList = refreshingTorrentList;
		}
		for (TorrentListRefreshingListener l : torrentListRefreshingListeners) {
			l.rpcTorrentListRefreshingChanged(refreshingTorrentList);
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
			intent.putExtra(SessionInfoManager.BUNDLE_KEY,
					getRemoteProfile().getID());
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
					int index = MapUtils.getMapInt(mapUpdatedFile,
							TransmissionVars.FIELD_FILES_INDEX, -1);
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
		ensureNotDestroyed();

		return addTorrentListReceivedListener(callID, l, true);
	}

	public boolean addTorrentListReceivedListener(TorrentListReceivedListener l,
			boolean fire) {
		ensureNotDestroyed();

		return addTorrentListReceivedListener(TAG, l, fire);
	}

	public boolean addTorrentListReceivedListener(String callID,
			TorrentListReceivedListener l, boolean fire) {
		ensureNotDestroyed();

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

	public boolean addTorrentListRefreshingListener(
			TorrentListRefreshingListener l, boolean fire) {
		ensureNotDestroyed();

		synchronized (torrentListRefreshingListeners) {
			if (torrentListRefreshingListeners.contains(l)) {
				return false;
			}
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "addTorrentListRefreshingListener " + l);
			}
			torrentListRefreshingListeners.add(l);
			List<Map<?, ?>> torrentList = getTorrentList();
			if (torrentList.size() > 0 && fire) {
				l.rpcTorrentListRefreshingChanged(refreshingTorrentList);
			}
		}
		return true;
	}

	public void removeTorrentListRefreshingListener(
			TorrentListRefreshingListener l) {
		synchronized (torrentListRefreshingListeners) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "removeTorrentListRefreshingListener " + l);
			}
			torrentListRefreshingListeners.remove(l);
		}
	}

	public void saveProfile() {
		AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
		appPreferences.addRemoteProfile(remoteProfile);
	}

	/**
	 * User specified new settings
	 *
	 * @param newSettings
	 */
	public void updateSessionSettings(SessionSettings newSettings) {
		SessionSettings originalSettings = getSessionSettings();

		if (originalSettings == null) {
			Log.e(TAG,
					"updateSessionSettings: Can't updateSessionSetting when " + "null");
			return;
		}

		saveProfile();

		if (handler == null) {
			initRefreshHandler();
		}

		Map<String, Object> changes = new HashMap<>();
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

	/* @Thunk */ void cancelRefreshHandler() {
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
		long interval = remoteProfile.calcUpdateInterval();
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

				long interval = remoteProfile.calcUpdateInterval();
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
					triggerRefresh(true);

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

	public void triggerRefresh(final boolean recentOnly) {
		if (rpc == null) {
			return;
		}
		if (!uiReady) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "trigger refresh called before Session-Get for "
						+ AndroidUtils.getCompressedStackTrace());
			}
			return;
		}
		synchronized (mLock) {
			if (refreshingTorrentList) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Refresh skipped. Already refreshing");
				}
				return;
			}
			setRefreshingTorrentList(true);
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Refresh Triggered " + AndroidUtils.getCompressedStackTrace());
		}

		if (needsTagRefresh && getSupportsTags()) {
			refreshTags(false);
		}

		rpc.getSessionStats(SESSION_STATS_FIELDS, new ReplyMapReceivedListener() {
			@Override
			public void rpcSuccess(String id, Map<?, ?> optionalMap) {
				updateSessionStats(optionalMap);

				TorrentListReceivedListener listener = new TorrentListReceivedListener() {

					@Override
					public void rpcTorrentListReceived(String callID,
							List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
						setRefreshingTorrentList(false);
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
				setRefreshingTorrentList(false);
			}

			@Override
			public void rpcFailure(String id, String message) {
				setRefreshingTorrentList(false);
			}
		});
	}

	public void refreshTags(boolean onlyRefreshCount) {
		if (mapTags == null || mapTags.size() == 0) {
			onlyRefreshCount = false;
		}
		Map args = null;
		if (onlyRefreshCount) {
			args = new HashMap();
			args.put("fields", Arrays.asList("uid", "count"));
		}
		rpc.simpleRpcCall("tags-get-list", args, new ReplyMapReceivedListener() {

			@Override
			public void rpcSuccess(String id, Map<?, ?> optionalMap) {
				needsTagRefresh = false;
				List<?> tagList = MapUtils.getMapList(optionalMap, "tags", null);
				if (tagList == null) {
					synchronized (mLock) {
						mapTags = null;
					}
					return;
				}

				placeTagListIntoMap(tagList);
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

	protected void updateSessionStats(Map<?, ?> map) {
		Map<?, ?> oldSessionStats = mapSessionStats;
		mapSessionStats = map;

//	 string                     | value type
//
// ---------------------------+-------------------------------------------------
//   "activeTorrentCount"       | number
//   "downloadSpeed"            | number
//   "pausedTorrentCount"       | number
//   "torrentCount"             | number
//   "uploadSpeed"              | number
//   ---------------------------+-------------------------------+
//   "cumulative-stats"         | object, containing:           |
//		                          +------------------+------------+
//		                          | uploadedBytes    | number     |
// tr_session_stats
//		                          | downloadedBytes  | number     |
// tr_session_stats
//		                          | filesAdded       | number     |
// tr_session_stats
//		                          | sessionCount     | number     |
// tr_session_stats
//		                          | secondsActive    | number     |
// tr_session_stats
//   ---------------------------+-------------------------------+
//   "current-stats"            | object, containing:           |
//                              +------------------+------------+
//                              | uploadedBytes    | number     |
// tr_session_stats
//                              | downloadedBytes  | number     |
// tr_session_stats
//                              | filesAdded       | number     |
// tr_session_stats
//                              | sessionCount     | number     |
// tr_session_stats
//                              | secondsActive    | number     |
// tr_session_stats

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
		ensureNotDestroyed();

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

	public void addTagListReceivedListener(TagListReceivedListener l) {
		ensureNotDestroyed();

		synchronized (tagListReceivedListeners) {
			if (!tagListReceivedListeners.contains(l)) {
				tagListReceivedListeners.add(l);
				if (mapTags != null) {
					l.tagListReceived(getTags());
				}
			}
		}
	}

	public void removeTagListReceivedListener(TagListReceivedListener l) {
		synchronized (tagListReceivedListeners) {
			tagListReceivedListeners.remove(l);
		}
	}

	public void addSubscriptionListReceivedListener(
			SubscriptionListReceivedListener l, long triggerIfNewDataSinceMS) {
		ensureNotDestroyed();

		synchronized (subscriptionListReceivedListeners) {
			if (!subscriptionListReceivedListeners.contains(l)) {
				subscriptionListReceivedListeners.add(l);
				if (mapSubscriptions != null
						&& lastSubscriptionListReceivedOn > triggerIfNewDataSinceMS) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "addSubscriptionListReceivedListener: triggering");
					}
					l.rpcSubscriptionListReceived(getSubscriptionList());
				}
			}
		}
	}

	public void removeSubscriptionListReceivedListener(
			SubscriptionListReceivedListener l) {
		synchronized (subscriptionListReceivedListeners) {
			subscriptionListReceivedListeners.remove(l);
		}
	}

	/**
	 * add SessionInfoListener.  listener is only triggered once for each method,
	 * and then removed
	 */
	public void addRpcAvailableListener(SessionInfoListener l) {
		ensureNotDestroyed();

		if (uiReady && rpc != null) {
			l.transmissionRpcAvailable(this);
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
		ensureNotDestroyed();

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
	public void onlineStateChanged(boolean isOnline, boolean isOnlineMobile) {
		ensureNotDestroyed();

		if (!uiReady) {
			return;
		}
		initRefreshHandler();
	}

	public String getRpcRoot() {
		ensureNotDestroyed();
		return rpcRoot;
	}

	public boolean isActivityVisible() {
		ensureNotDestroyed();
		return activityVisible;
	}

	public void activityResumed(Activity currentActivity) {
		ensureNotDestroyed();

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "ActivityResumed. needsFullTorrentRefresh? "
					+ needsFullTorrentRefresh);
		}
		this.currentActivity = currentActivity;
		activityVisible = true;
		if (needsFullTorrentRefresh) {
			triggerRefresh(false);
		} else {
			if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_CORE) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						VuzeRemoteApp.waitForCore(SessionInfo.this.currentActivity, 15000);
						triggerRefresh(false);
					}
				}).start();
			}
		}
	}

	public void activityPaused() {
		if (currentActivity != null && !currentActivity.isFinishing()) {
			ensureNotDestroyed();
		}

		currentActivity = null;
		activityVisible = false;
	}

	public void clearTorrentCache() {
		ensureNotDestroyed();

		synchronized (mLock) {
			mapOriginal.clear();
			needsFullTorrentRefresh = true;
		}
	}

	public int clearTorrentFilesCaches(boolean keepLastUsedTorrentFiles) {
		ensureNotDestroyed();

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
				if (map.containsKey(TransmissionVars.FIELD_TORRENT_FILES)) {
					map.remove(TransmissionVars.FIELD_TORRENT_FILES);
					num++;
				}
			}
		}
		return num;
	}

	/**
	 * @return -1 == Not Vuze; 0 == Vuze
	 */
	public int getRPCVersionAZ() {
		ensureNotDestroyed();
		return rpc == null ? -1 : rpc.getRPCVersionAZ();
	}

	public boolean getSupportsRCM() {
		ensureNotDestroyed();
		return rpc == null ? false : rpc.getSupportsRCM();
	}

	public boolean getSupportsTorrentRename() {
		ensureNotDestroyed();
		return rpc == null ? false : rpc.getSupportsTorrentRename();
	}

	public boolean getSupportsTags() {
		ensureNotDestroyed();
		return rpc == null ? false : rpc.getSupportsTags();
	}

	public String getBaseURL() {
		ensureNotDestroyed();
		return baseURL;
	}

	public void openTorrent(final Activity activity, final String sTorrentURL,
			final String friendlyName) {
		ensureNotDestroyed();
		if (sTorrentURL == null || sTorrentURL.length() == 0) {
			return;
		}
		executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.addTorrentByUrl(sTorrentURL, friendlyName, true,
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
				// TODO: Cancel button that removes torrent
				CustomToast.makeText(context, s, Toast.LENGTH_SHORT).show();
			}
		});

		VuzeEasyTracker.getInstance(activity).sendEvent("RemoteAction",
				"AddTorrent", "AddTorrentByUrl", null);
	}

	public void openTorrent(final Activity activity, final String name,
			InputStream is) {
		ensureNotDestroyed();
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
					if (bab.length() > 5) {
						bab.setLength(5);
					}
					s = activity.getResources().getString(R.string.not_torrent_file, name,
							new String(bab.toByteArray()));
				}
				AndroidUtilsUI.showDialog(activity, R.string.add_torrent,
						AndroidUtils.fromHTML(s));
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
			AndroidUtilsUI.showConnectionError(activity, "Out of Memory", true);
		}
	}

	/* @Thunk */ void openTorrentWithMetaData(final Activity activity,
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
				CustomToast.makeText(context, s, Toast.LENGTH_SHORT).show();
			}
		});
		VuzeEasyTracker.getInstance(activity).sendEvent("RemoteAction",
				"AddTorrent", "AddTorrentByMeta", null);
	}

	public void openTorrent(final Activity activity, final Uri uri) {
		ensureNotDestroyed();
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
			AndroidUtilsUI.requestPermissions(activity, new String[] {
				Manifest.permission.READ_EXTERNAL_STORAGE
			}, new Runnable() {
				@Override
				public void run() {
					openTorrent_perms(activity, uri);
				}
			}, new Runnable() {
				@Override
				public void run() {
					CustomToast.makeText(activity,
							R.string.content_read_failed_perms_denied,
							Toast.LENGTH_LONG).show();
				}
			});
		} else {
			openTorrent(activity, uri.toString(), (String) null);
		}
	}

	/* @Thunk */
	void openTorrent_perms(Activity activity, Uri uri) {
		try {
			InputStream stream = null;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				String realPath = PaulBurkeFileUtils.getPath(activity, uri);
				if (realPath != null) {
					String meh = realPath.startsWith("/") ? "file://" + realPath
							: realPath;
					try {
						stream = activity.getContentResolver().openInputStream(
								Uri.parse(meh));
					} catch (FileNotFoundException ignore) {
					}
				}
			}
			if (stream == null) {
				ContentResolver contentResolver = activity.getContentResolver();
				stream = contentResolver.openInputStream(uri);
			}
			openTorrent(activity, uri.toString(), stream);
		} catch (SecurityException e) {
			if (AndroidUtils.DEBUG) {
				e.printStackTrace();
			}
			VuzeEasyTracker.getInstance(activity).logError(e);
			String s = "Security Exception trying to access <b>" + uri + "</b>";
			CustomToast.makeText(activity, AndroidUtils.fromHTML(s),
					Toast.LENGTH_LONG).show();
		} catch (FileNotFoundException e) {
			if (AndroidUtils.DEBUG) {
				e.printStackTrace();
			}
			VuzeEasyTracker.getInstance(activity).logError(e);
			String s = "<b>" + uri + "</b> not found";
			if (e.getCause() != null) {
				s += ". " + e.getCause().getMessage();
			}
			CustomToast.makeText(activity, AndroidUtils.fromHTML(s),
					Toast.LENGTH_LONG).show();
		}
	}

	public boolean isRefreshingTorrentList() {
		return refreshingTorrentList;
	}

	private static class TorrentAddedReceivedListener2
		implements TorrentAddedReceivedListener
	{
		private SessionInfo sessionInfo;

		/* @Thunk */ Activity activity;

		/* @Thunk */ boolean showOptions;

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
						CustomToast.makeText(context, s, Toast.LENGTH_LONG).show();
					}
				});
			} else {
				String hashString = MapUtils.getMapString(mapTorrentAdded, "hashString",
						"");
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
						List<String> fields = new ArrayList<>(
								rpc.getBasicTorrentFieldIDs());
						if (showOptions) {
							fields.add(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR);
							fields.add(TransmissionVars.FIELD_TORRENT_FILES);
							fields.add(TransmissionVars.FIELD_TORRENT_FILESTATS);
						}
						rpc.getTorrent(TAG, id, fields, null);
					} else {
						rpc.getRecentTorrents(TAG, null);
					}
				}
			});
		}

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
						showUrlFailedDialog(activity, message, url,
								new String(bab.buffer(), 0, 5));
					}
					return;
				}
			} catch (Throwable t) {
			}
			AndroidUtilsUI.showDialog(activity, R.string.add_torrent, message);
		}

		@Override
		public void torrentAddError(Exception e) {

			if (e instanceof HttpHostConnectException) {
				AndroidUtilsUI.showConnectionError(activity,
						R.string.connerror_hostconnect, true);
			} else {
				AndroidUtilsUI.showConnectionError(activity, e.getMessage(), true);
			}
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

				Spanned msg = AndroidUtils.fromHTML(s);
				Builder builder = new AlertDialog.Builder(activity).setMessage(
						msg).setCancelable(true).setNegativeButton(android.R.string.ok,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
									}
								}).setNeutralButton(R.string.torrent_url_add_failed_openurl,
										new OnClickListener() {
											@Override
											public void onClick(DialogInterface dialog, int which) {
												Intent intent = new Intent(Intent.ACTION_VIEW,
														Uri.parse(url));
												try {
													activity.startActivity(intent);
												} catch (Throwable t) {
													AndroidUtilsUI.showDialog(activity,
															"Error opening URL", t.getMessage());
												}
											}
										});
				builder.show();
			}
		});

	}

	public void moveDataTo(final long id, final String s) {
		ensureNotDestroyed();

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
		ensureNotDestroyed();

		if (remoteProfile == null) {
			return;
		}
		remoteProfile.setSavePathHistory(history);
		saveProfile();
	}

	public Activity getCurrentActivity() {
		ensureNotDestroyed();

		return currentActivity;
	}

	// >> Subscriptions

	public boolean isRefreshingSubscriptionList() {
		ensureNotDestroyed();

		return refreshingSubscriptionList;
	}

	/* @Thunk */ void setRefreshingSubscriptionList(
			boolean refreshingSubscriptionList) {
		ensureNotDestroyed();

		synchronized (mLock) {
			this.refreshingSubscriptionList = refreshingSubscriptionList;
		}
		for (SubscriptionListReceivedListener l : subscriptionListReceivedListeners) {
			l.rpcSubscriptionListRefreshing(refreshingSubscriptionList);
		}
	}

	public int getSubscriptionListCount() {
		ensureNotDestroyed();

		return mapSubscriptions.size();
	}

	public List<String> getSubscriptionList() {
		ensureNotDestroyed();

		return new ArrayList<>(mapSubscriptions.keySet());
	}

	public Map<?, ?> getSubscription(String id) {
		ensureNotDestroyed();

		return MapUtils.getMapMap(mapSubscriptions, id, null);
	}

	public void refreshSubscriptionList() {
		ensureNotDestroyed();

		setRefreshingSubscriptionList(true);
		executeRpc(new SessionInfo.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.getSubscriptionList(new ReplyMapReceivedListener() {
					@Override
					public void rpcSuccess(String id, Map<?, ?> optionalMap) {
						setRefreshingSubscriptionList(false);

						Map map = MapUtils.getMapMap(optionalMap, "subscriptions", null);
						if (map == null) {
							map = Collections.emptyMap();
						}

						lastSubscriptionListReceivedOn = System.currentTimeMillis();

//						Map<String, Map<?, ?>> newMap = new HashMap<>();
//						for (Object o : map.keySet()) {
//							Object v = map.get(o);
//							newMap.put((String) o, (Map) v);
//						}
						// risky cast of the day, but it's cool
						mapSubscriptions = (Map<String, Map<?, ?>>) map;
						if (subscriptionListReceivedListeners.size() > 0) {
							List<String> list = getSubscriptionList();
							for (SubscriptionListReceivedListener l : subscriptionListReceivedListeners) {
								l.rpcSubscriptionListReceived(list);
							}
						}
					}

					@Override
					public void rpcError(String id, Exception e) {
						setRefreshingSubscriptionList(false);
						for (SubscriptionListReceivedListener l : subscriptionListReceivedListeners) {
							l.rpcSubscriptionListError(id, e);
						}
					}

					@Override
					public void rpcFailure(String id, String message) {
						setRefreshingSubscriptionList(false);
						for (SubscriptionListReceivedListener l : subscriptionListReceivedListeners) {
							l.rpcSubscriptionListFailure(id, message);
						}
					}
				});
			}
		});
	}

	// << Subscriptions

	private void ensureNotDestroyed() {
		if (destroyed) {
			Log.e(TAG, "Accessing destroyed SessionInfo"
					+ AndroidUtils.getCompressedStackTrace());
		}
	}

	public void destroy() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "destroy: " + AndroidUtils.getCompressedStackTrace());
		}
		cancelRefreshHandler();
		clearTorrentCache();
		clearTorrentFilesCaches(false);
		availabilityListeners.clear();
		refreshTriggerListeners.clear();
		sessionSettingsChangedListeners.clear();
		subscriptionListReceivedListeners.clear();
		tagListReceivedListeners.clear();
		torrentListRefreshingListeners.clear();
		destroyed = true;
	}

}
