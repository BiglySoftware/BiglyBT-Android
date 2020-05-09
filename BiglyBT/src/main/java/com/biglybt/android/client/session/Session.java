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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.biglybt.android.client.*;
import com.biglybt.android.client.rpc.*;
import com.biglybt.android.util.BiglyCoreUtils;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.NetworkState;
import com.biglybt.util.Thunk;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.i2p.android.ui.I2PAndroidHelper;

import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import jcifs.netbios.NbtAddress;

/**
 * Access to all the information for a session, such as:<P>
 * - RemoteProfile<BR>
 * - SessionSettings<BR>
 * - RPC<BR>
 * - torrents<BR>
 */
public class Session
	implements SessionSettingsReceivedListener, NetworkState.NetworkStateListener
{
	private static final String TAG = "Session";

	public interface RpcExecuter
	{
		void executeRpc(TransmissionRPC rpc);
	}

	private static final String[] FILE_FIELDS_LOCALHOST = new String[] {
		TransmissionVars.FIELD_FILES_NAME,
		TransmissionVars.FIELD_FILES_LENGTH,
		TransmissionVars.FIELD_FILESTATS_BYTES_COMPLETED,
		TransmissionVars.FIELD_FILESTATS_PRIORITY,
		TransmissionVars.FIELD_FILESTATS_WANTED,
		TransmissionVars.FIELD_FILES_FULL_PATH,
	};

	@UiThread
	protected class HandlerRunnable
		implements Runnable
	{
		@Override
		@UiThread
		public void run() {
			handler = null;

			if (destroyed) {
				if (AndroidUtils.DEBUG) {
					logd("Handler ignored: destroyed");
				}
				return;
			}

			long interval = remoteProfile.calcUpdateInterval();
			if (interval <= 0) {
				if (AndroidUtils.DEBUG) {
					logd("Handler ignored: update interval " + interval);
				}
				cancelRefreshHandler();
				return;
			}

			if (hasCurrentActivity()) {
				if (AndroidUtils.DEBUG_ANNOY) {
					logd("Fire Handler");
				}
				triggerRefresh(true);

				for (RefreshTriggerListener l : refreshTriggerListeners) {
					l.triggerRefresh();
				}
			}
		}
	}

	private final String[] FILE_FIELDS_REMOTE = new String[] {
		TransmissionVars.FIELD_FILES_NAME,
		TransmissionVars.FIELD_FILES_LENGTH,
		TransmissionVars.FIELD_FILESTATS_BYTES_COMPLETED,
		TransmissionVars.FIELD_FILESTATS_PRIORITY,
		TransmissionVars.FIELD_FILESTATS_WANTED,
	};

	private static final String[] SESSION_STATS_FIELDS = {
		TransmissionVars.TR_SESSION_STATS_DOWNLOAD_SPEED,
		TransmissionVars.TR_SESSION_STATS_UPLOAD_SPEED
	};

	private SessionSettings sessionSettings;

	@Thunk
	TransmissionRPC transmissionRPC;

	@Thunk
	@NonNull
	final RemoteProfile remoteProfile;

	@Thunk
	final Object mLock = new Object();

	@NonNull
	private final List<SessionSettingsChangedListener> sessionSettingsChangedListeners = new CopyOnWriteArrayList<>();

	@Thunk
	final List<RefreshTriggerListener> refreshTriggerListeners = new CopyOnWriteArrayList<>();

	private final List<SessionListener> availabilityListeners = new CopyOnWriteArrayList<>();

	@Thunk
	Handler handler;

	private boolean readyForUI = false;

	private Map<?, ?> mapSessionStats;

	private String rpcRoot;

	@NonNull
	private final List<RpcExecuter> rpcExecuteList = new ArrayList<>();

	private String baseURL;

	@Thunk
	@NonNull
	WeakReference<FragmentActivity> currentActivityRef = new WeakReference<>(
			null);

	/** 
	 * Set whenever currentActivityRef is set, for easy way to check if WeakReference was lost 
	 */
	private boolean hasCurrentActivity;

	@Thunk
	boolean destroyed = false;

	/**
	 * Access to Subscription methods
	 */
	public final Session_Subscription subscription = new Session_Subscription(
			this);

	/**
	 * Access to RCM (Swarm Discoveries) methods
	 */
	@NonNull
	public final Session_RCM rcm = new Session_RCM(this);

	/**
	 * Access to Tag methods
	 */
	@NonNull
	public final Session_Tag tag = new Session_Tag(this);

	/**
	 * Access to Torrent methods
	 */
	@NonNull
	public final Session_Torrent torrent = new Session_Torrent(this);

	private long contentPort;

	@NonNull
	private final Runnable handlerRunnable;

	private long lastRefreshInterval = -1;

	@Thunk
	final TorrentListReceivedListener doneRefreshingListListener = (callID,
			addedTorrentMaps, fields, fileIndexes,
			removedTorrentIDs) -> torrent.setRefreshingList(false);

	public Session(final @NonNull RemoteProfile _remoteProfile) {
		this.remoteProfile = _remoteProfile;
		handlerRunnable = new HandlerRunnable();

		if (AndroidUtils.DEBUG) {
			Log.d(TAG,
					"Session: init from " + AndroidUtils.getCompressedStackTrace());
		}

		Object lastSessionProperties = remoteProfile.get("lastSessionProperties",
				null);
		if (lastSessionProperties != null) {
			remoteProfile.set("lastSessionProperties", null);
		}

		BiglyBTApp.getNetworkState().addListener(this);
	}

	private void bindAndOpen() {
		// Bind and Open take a while, do it on the non-UI thread
		Thread thread = new Thread(() -> {

			String host = remoteProfile.getHost();
			if (host != null && host.endsWith(".i2p")) {
				bindToI2P(host, remoteProfile.getPort(), null, null, true);
				return;
			}
			if (host != null && host.length() > 0
					&& remoteProfile.getRemoteType() != RemoteProfile.TYPE_LOOKUP) {
				open(remoteProfile.getProtocol(), host, remoteProfile.getPort());
			} else {
				bindAndOpen(remoteProfile.getI2POnly());
			}
		}, "bindAndOpen");
		thread.setDaemon(true);
		thread.start();

	}

	@Thunk
	void bindAndOpen(final boolean requireI2P) {

		try {
			Map<?, ?> bindingInfo = RPC.getBindingInfo(remoteProfile);

			Map<?, ?> error = MapUtils.getMapMap(bindingInfo, "error", null);
			if (error != null) {
				String errMsg = MapUtils.getMapString(error, "msg", "Unknown Error");
				if (AndroidUtils.DEBUG) {
					logd("Error from getBindingInfo " + errMsg);
				}

				AndroidUtilsUI.showConnectionError(currentActivityRef.get(), errMsg,
						false);
				return;
			}

			final String host = MapUtils.getMapString(bindingInfo, "ip", null);
			final String protocol = MapUtils.getMapString(bindingInfo, "protocol",
					null);
			final String i2p = MapUtils.getMapString(bindingInfo, "i2p", null);
			final int port = (int) MapUtils.parseMapLong(bindingInfo, "port", 0);

			if (port != 0) {
				if (i2p != null) {
					if (bindToI2P(i2p, port, host, protocol, requireI2P)) {
						return;
					}
					if (requireI2P) {
						// User would have got a fail message from bindToI2P
						return;
					}
				} else if (requireI2P) {
					FragmentActivity currentActivity = currentActivityRef.get();
					if (currentActivity != null) {
						AndroidUtilsUI.showConnectionError(currentActivity,
								currentActivity.getString(R.string.i2p_remote_client_needs_i2p),
								false);
						return;
					}
				}
				if (open(protocol, host, port)) {
					Map<String, Object> lastBindingInfo = new HashMap<>();
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
			AnalyticsTracker.getInstance(currentActivityRef.get()).logErrorNoLines(e);

			AndroidUtilsUI.showConnectionError(currentActivityRef.get(),
					remoteProfile.getID(), e, false);
		}
	}

	@Thunk
	boolean bindToI2P(final String hostI2P, final int port,
			@Nullable final String hostFallBack,
			@Nullable final String protocolFallBack, final boolean requireI2P) {
		{
			FragmentActivity currentActivity = currentActivityRef.get();
			if (currentActivity == null) {
				Log.e(TAG, "bindToI2P: currentActivity null");
				return false;
			}
			final I2PAndroidHelper i2pHelper = new I2PAndroidHelper(currentActivity);
			if (i2pHelper.isI2PAndroidInstalled()) {
				i2pHelper.bind(() -> {
					// We are now on the UI Thread :(
					new Thread(() -> onI2PAndroidBound(i2pHelper, hostI2P, port,
							hostFallBack, protocolFallBack, requireI2P)).start();
				});
				return true;
			} else if (requireI2P) {
				AndroidUtilsUI.showConnectionError(currentActivity,
						currentActivity.getString(R.string.i2p_not_installed), false);
				i2pHelper.unbind();
			} else if (AndroidUtils.DEBUG) {
				i2pHelper.unbind();
				logd("onI2PAndroidBound: I2P not installed");
			}
		}
		return false;
	}

	@Thunk
	void onI2PAndroidBound(final @NonNull I2PAndroidHelper i2pHelper,
			String hostI2P, int port, String hostFallBack, String protocolFallback,
			boolean requireI2P) {
		boolean isI2PRunning = i2pHelper.isI2PAndroidRunning();

		if (AndroidUtils.DEBUG) {
			logd("onI2PAndroidBound: I2P running? " + isI2PRunning);
		}

		if (!isI2PRunning) {
			FragmentActivity currentActivity = currentActivityRef.get();
			if (requireI2P && currentActivity != null) {
//				activity.runOnUiThread(new Runnable() {
//					@Override
//					public void run() {
//						i2pHelper.requestI2PAndroidStart(activity);
//					}
//				});
				AndroidUtilsUI.showConnectionError(currentActivity,
						currentActivity.getString(R.string.i2p_not_running), false);
				i2pHelper.unbind();
				return;
			}
		}
		if (!i2pHelper.areTunnelsActive() && requireI2P) {
			FragmentActivity currentActivity = currentActivityRef.get();
			if (currentActivity != null) {
				AndroidUtilsUI.showConnectionError(currentActivity,
						currentActivity.getString(R.string.i2p_no_tunnels), false);
			}
			i2pHelper.unbind();
			return;
		}
		i2pHelper.unbind();

		boolean opened = false;
		if (isI2PRunning && hostI2P != null) {
			opened = open("http", hostI2P, port);
		}
		if (!opened && hostFallBack != null && protocolFallback != null) {
			opened = open(protocolFallback, hostFallBack, port);
		}

		if (opened && hostFallBack != null) {
			Map<String, Object> lastBindingInfo = new HashMap<>();
			lastBindingInfo.put("ip", hostFallBack);
			lastBindingInfo.put("i2p", hostI2P);
			lastBindingInfo.put("port", port);
			lastBindingInfo.put("protocol",
					protocolFallback == null || protocolFallback.length() == 0 ? "http"
							: protocolFallback);
			remoteProfile.setLastBindingInfo(lastBindingInfo);
			saveProfile();
		}
	}

	public long getContentPort() {
		return contentPort;
	}

	@Thunk
	boolean open(@NonNull String protocol, @NonNull String host, int port) {
		try {

			boolean isLocalHost = "localhost".equals(host);

			try {
				//noinspection ResultOfMethodCallIgnored
				InetAddress.getByName(host);
			} catch (UnknownHostException e) {
				try {
					// Note: {@link org.xbill.DNS.Address#getByName} doesn't handle Netbios names
					host = NbtAddress.getByName(host).getHostAddress();
				} catch (Throwable ignore) {
				}
			}

			rpcRoot = protocol + "://" + host + ":" + port + "/";
			String rpcUrl = rpcRoot + "transmission/rpc";

			if (AndroidUtils.DEBUG) {
				logd("rpc root = " + rpcRoot);
			}

			if (isLocalHost && port == RPC.LOCAL_BIGLYBT_PORT
					&& BiglyCoreUtils.isCoreAllowed()) {
				// wait for Vuze Core to initialize
				// We should be on non-main thread
				// TODO check
				BiglyCoreUtils.waitForCore(currentActivityRef.get());
			}

			if (!host.endsWith(".i2p") && !AndroidUtils.isURLAlive(rpcUrl)) {
				AndroidUtilsUI.showConnectionError(currentActivityRef.get(),
						remoteProfile.getID(), R.string.error_remote_not_found, false);
				return false;
			}

			AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
			remoteProfile.setLastUsedOn(System.currentTimeMillis());
			appPreferences.setLastRemote(remoteProfile);
			appPreferences.addRemoteProfile(remoteProfile);

			if ("127.0.0.1".equals(host) || "localhost".equals(host)) {
				baseURL = protocol + "://"
						+ BiglyBTApp.getNetworkState().getActiveIpAddress();
			} else {
				baseURL = protocol + "://" + host;
			}
			setTransmissionRPC(new TransmissionRPC(this, rpcUrl));
			return true;
		} catch (Exception e) {
			AnalyticsTracker.getInstance(currentActivityRef.get()).logError(e);
		}
		return false;
	}

	@Override
	public void sessionPropertiesUpdated(Map<?, ?> map) {
		SessionSettings settings = SessionSettings.createFromRPC(map);

		contentPort = MapUtils.getMapLong(map, "az-content-port", -1);

		sessionSettings = settings;

		for (SessionSettingsChangedListener l : sessionSettingsChangedListeners) {
			l.sessionSettingsChanged(settings);
		}

		if (!readyForUI) {
			if (getSupports(RPCSupports.SUPPORTS_TAGS)) {
				transmissionRPC.simpleRpcCall("tags-get-list",
						new ReplyMapReceivedListener() {

							@Override
							public void rpcSuccess(String requestID, Map<?, ?> optionalMap) {
								List<?> tagList = MapUtils.getMapList(optionalMap, "tags",
										null);
								if (tagList == null) {
									tag.mapTags = null;
									setReadyForUI();
									return;
								}

								tag.placeTagListIntoMap(tagList, true, true);

								setReadyForUI();
							}

							@Override
							public void rpcFailure(String requestID, String message) {
								setReadyForUI();
							}

							@Override
							public void rpcError(String requestID, Throwable e) {
								setReadyForUI();
							}
						});
			} else {
				setReadyForUI();
			}
		}

		FragmentActivity currentActivity = currentActivityRef.get();
		if (currentActivity != null) {
			String message = MapUtils.getMapString(map, "az-message", null);
			if (message != null && message.length() > 0) {
				AndroidUtilsUI.showDialog(currentActivity,
						R.string.title_message_from_client, R.string.hardcoded_string,
						message);
			}
		}
	}

	@Thunk
	void setReadyForUI() {
		readyForUI = true;

		IAnalyticsTracker vet = AnalyticsTracker.getInstance();
		String rpcVersion = transmissionRPC.getRPCVersion() + "/"
				+ transmissionRPC.getRPCVersionAZ();
		vet.setRPCVersion(rpcVersion);
		vet.setClientVersion(transmissionRPC.getClientVersion());

		setupNextRefresh();
		if (torrent.needsFullTorrentRefresh) {
			triggerRefresh(false);
		}
		for (SessionListener l : availabilityListeners) {
			l.sessionReadyForUI(transmissionRPC);
		}
		availabilityListeners.clear();

		synchronized (rpcExecuteList) {
			for (RpcExecuter exec : rpcExecuteList) {
				try {
					exec.executeRpc(transmissionRPC);
				} catch (Throwable t) {
					AnalyticsTracker.getInstance().logError(t);
				}
			}
			rpcExecuteList.clear();
		}
	}

	public boolean isReadyForUI() {
		ensureNotDestroyed();

		return readyForUI;
	}

	/**
	 * Returns a new SessionSettings object
	 */
	public @Nullable SessionSettings getSessionSettingsClone() {
		ensureNotDestroyed();
		if (sessionSettings == null) {
			return null;
		}
		return SessionSettings.createFromRPC(sessionSettings.toRPC(null));
	}

	/**
	 * Allows you to execute an RPC call, ensuring RPC is ready first (may
	 * not be called on same thread)
	 *
	 * @deprecated Discouraged.  Add method to Session that executes the RPC
	 */
	public void executeRpc(@NonNull RpcExecuter exec) {
		ensureNotDestroyed();

		if (destroyed) {
			if (AndroidUtils.DEBUG) {
				logd("executeRpc ignored, Session destroyed "
						+ AndroidUtils.getCompressedStackTrace());
			}
			return;
		}

		synchronized (rpcExecuteList) {
			if (!readyForUI) {
				rpcExecuteList.add(exec);
				return;
			}
		}

		exec.executeRpc(transmissionRPC);
	}

	void _executeRpc(@NonNull RpcExecuter exec) {
		ensureNotDestroyed();

		if (destroyed) {
			if (AndroidUtils.DEBUG) {
				logd("_executeRpc ignored, Session destroyed "
						+ AndroidUtils.getCompressedStackTrace());
			}
			return;
		}

		synchronized (rpcExecuteList) {
			if (!readyForUI) {
				rpcExecuteList.add(exec);
				return;
			}
		}

		exec.executeRpc(transmissionRPC);
	}

	/**
	 * @return the remoteProfile
	 */
	public @NonNull RemoteProfile getRemoteProfile() {
		return remoteProfile;
	}

	/**
	 * @param transmissionRPC the rpc to set
	 */
	private void setTransmissionRPC(TransmissionRPC transmissionRPC) {
		if (this.transmissionRPC == transmissionRPC) {
			return;
		}

		if (this.transmissionRPC != null) {
			this.transmissionRPC.removeSessionSettingsReceivedListener(this);
		}

		this.transmissionRPC = transmissionRPC;

		if (transmissionRPC != null) {
			String[] fields;
			if (remoteProfile.isLocalHost()) {
				fields = FILE_FIELDS_LOCALHOST;
			} else {
				if (contentPort <= 0) {
					List<String> strings = new ArrayList<>(
							Arrays.asList(FILE_FIELDS_REMOTE));
					strings.add(TransmissionVars.FIELD_FILES_CONTENT_URL);
					fields = strings.toArray(new String[0]);
				} else {
					fields = FILE_FIELDS_REMOTE;
				}
			}
			transmissionRPC.setDefaultFileFields(fields);

			transmissionRPC.addTorrentListReceivedListener((callID, addedTorrentMaps,
					fields2, fileIndexes, removedTorrentIDs) -> {

				torrent.lastListReceivedOn = System.currentTimeMillis();
				// XXX If this is a full refresh, we should clear list!
				torrent.addRemoveTorrents(callID, addedTorrentMaps, fields2,
						fileIndexes, removedTorrentIDs);
			});

			transmissionRPC.addSessionSettingsReceivedListener(this);

		}
	}

	/*
	public HashMap<Object, Map<?, ?>> getTorrentList() {
		synchronized (mLock) {
			return new HashMap<Object, Map<?, ?>>(mapOriginal);
		}
	}
	*/

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

	public void saveProfile() {
		AndroidUtilsUI.runOffUIThread(() -> {
			AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
			appPreferences.addRemoteProfile(remoteProfile);
		});
	}

	/**
	 * User specified new settings
	 */
	public void updateSessionSettings(@NonNull SessionSettings newSettings) {
		if (sessionSettings == null) {
			Log.e(TAG,
					"updateSessionSettings: Can't updateSessionSetting when " + "null");
			return;
		}

		saveProfile();

		setupNextRefresh();

		Map<String, Object> changes = newSettings.toRPC(sessionSettings);

		if (changes.size() > 0) {
			transmissionRPC.updateSettings(changes);
		}

		sessionSettings = newSettings;

		triggerSessionSettingsChanged();
	}

	public void triggerSessionSettingsChanged() {
		for (SessionSettingsChangedListener l : sessionSettingsChangedListeners) {
			l.sessionSettingsChanged(sessionSettings);
		}
	}

	@Thunk
	void cancelRefreshHandler() {
		if (handler == null) {
			return;
		}
		handler.removeCallbacksAndMessages(null);
		handler = null;
	}

	protected void setupNextRefresh() {
		if (AndroidUtils.DEBUG_ANNOY) {
			logd("setupNextRefresh");
		}
		long interval = remoteProfile.calcUpdateInterval();
		if (handler != null && interval == lastRefreshInterval) {
			return;
		}
		lastRefreshInterval = interval;
		if (AndroidUtils.DEBUG_ANNOY) {
			logd("Handler fires every " + interval);
		}
		if (interval <= 0) {
			cancelRefreshHandler();
			return;
		}
		handler = new Handler(Looper.getMainLooper());
		handler.postDelayed(handlerRunnable, interval * 1000);
	}

	/**
	 * Triggeres a refresh of:
	 * <ul>
	 * <li>Tags (If needed)</li>
	 * <li>Session Stats</li>
	 * <li>Torrents</li>
	 * </ul>
	 * All tasks may not complete (for example, when the app is no longer active/visible)
	 */
	public void triggerRefresh(final boolean recentOnly) {
		if (transmissionRPC == null) {
			return;
		}
		if (!readyForUI) {
			if (AndroidUtils.DEBUG) {
				log(Log.WARN, "trigger refresh called before Session-Get for "
						+ AndroidUtils.getCompressedStackTrace());
			}
			return;
		}
		synchronized (mLock) {
			if (torrent.isRefreshingList()) {
				if (AndroidUtils.DEBUG) {
					logd("Refresh skipped. Already refreshing");
				}
				return;
			}
			torrent.setRefreshingList(true);
		}
		if (AndroidUtils.DEBUG_ANNOY) {
			logd("Refresh Triggered " + AndroidUtils.getCompressedStackTrace());
		}

		if (!hasCurrentActivity()) {
			torrent.setRefreshingList(false);
			if (AndroidUtils.DEBUG) {
				logd("Refresh skipped. No Current Activity."
						+ AndroidUtils.getCompressedStackTrace());
			}
			return;
		}

		if (tag.needsTagRefresh) {
			tag.refreshTags(false);
		}

		transmissionRPC.getSessionStats(SESSION_STATS_FIELDS,
				new ReplyMapReceivedListener() {
					@Override
					public void rpcSuccess(String requestID, Map<?, ?> optionalMap) {
						updateSessionStats(optionalMap);

						if (!hasCurrentActivity()) {
							torrent.setRefreshingList(false);
							if (AndroidUtils.DEBUG) {
								logd("Refresh skipped. No Current Activity.");
							}
							return;
						}

						if (recentOnly && !torrent.needsFullTorrentRefresh) {
							transmissionRPC.getRecentTorrents(TAG + ".Refresh",
									doneRefreshingListListener);
						} else {
							transmissionRPC.getAllTorrents(TAG + ".Refresh",
									doneRefreshingListListener);
							torrent.needsFullTorrentRefresh = false;
						}
					}

					@Override
					public void rpcError(String requestID, Throwable e) {
						torrent.setRefreshingList(false);
					}

					@Override
					public void rpcFailure(String requestID, String message) {
						torrent.setRefreshingList(false);
					}
				});
	}

	@Thunk
	void updateSessionStats(Map<?, ?> map) {
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
			@NonNull SessionSettingsChangedListener l) {
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

	/**
	 * add SessionListener.  listener is only triggered once for each method,
	 * and then removed
	 */
	public void addSessionListener(@NonNull SessionListener l) {
		ensureNotDestroyed();

		if (readyForUI && transmissionRPC != null) {
			l.sessionReadyForUI(transmissionRPC);
		} else {
			synchronized (availabilityListeners) {
				if (availabilityListeners.contains(l)) {
					return;
				}
				availabilityListeners.add(l);
			}
		}
	}

	/**
	 * Adds a {@link RefreshTriggerListener}.  Triggers refresh if listener has 
	 * not been added yet
	 */
	@UiThread
	public void addRefreshTriggerListener(@NonNull RefreshTriggerListener l,
			boolean trigger) {
		ensureNotDestroyed();

		if (!refreshTriggerListeners.contains(l)) {
			refreshTriggerListeners.add(l);
		}
		if (trigger) {
			l.triggerRefresh();
		}
	}

	public void removeRefreshTriggerListener(RefreshTriggerListener l) {
		refreshTriggerListeners.remove(l);
	}

	@Override
	public void onlineStateChanged(boolean isOnline, boolean isOnlineMobile) {
		ensureNotDestroyed();

		if (!readyForUI) {
			return;
		}
		setupNextRefresh();
	}

	/**
	 * Whether this session's Activity is the current.
	 * <p/>
	 * The activity may not be foreground or visible.  ie. Another app might
	 * be active, and our activity only resides in the task list.  Or,
	 * we are split screen and visible, but may or may not be focused.
	 */
	@Thunk
	boolean hasCurrentActivity() {
		ensureNotDestroyed();
		FragmentActivity currentActivity = currentActivityRef.get();
		if (hasCurrentActivity
				&& (currentActivity == null || currentActivity.isFinishing()
						|| !BiglyBTApp.isApplicationVisible())) {
			if (AndroidUtils.DEBUG) {
				log(Log.WARN,
						"Activity isn't visible trigger. currentActivity=" + currentActivity
								+ "; isAppVis? " + BiglyBTApp.isApplicationVisible() + " via "
								+ AndroidUtils.getCompressedStackTrace());
			}
			hasCurrentActivity = false;
			currentActivityRef = new WeakReference<>(null);
			SessionManager.clearActiveSession(this);
		}
		return hasCurrentActivity;
	}

	public void setCurrentActivity(@NonNull FragmentActivity currentActivity) {
		FragmentActivity lastActivity = currentActivityRef.get();
		if (lastActivity == currentActivity) {
			if (AndroidUtils.DEBUG) {
				logd("skip setCurrentActivity; "
						+ AndroidUtils.getCompressedStackTrace(3));
			}
			return;
		}

		ensureNotDestroyed();

		currentActivityRef = new WeakReference<>(currentActivity);
		if (AndroidUtils.DEBUG) {
			logd("setCurrentActivity " + currentActivity
					+ "; needsFullTorrentRefresh? " + torrent.needsFullTorrentRefresh
					+ " via " + AndroidUtils.getCompressedStackTrace());
		}
		hasCurrentActivity = true;

		// Session now has an activity, ensure bindings and refreshes are triggered

		SessionManager.setActiveSession(this);

		if (transmissionRPC == null) {
			bindAndOpen();
			return;
		}

		if (torrent.needsFullTorrentRefresh) {
			triggerRefresh(false);
		} else {
			if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_CORE) {
				new Thread(() -> {
					BiglyCoreUtils.waitForCore(currentActivityRef.get());
					triggerRefresh(false);
				}).start();
			}

			setupNextRefresh();
		}
	}

	public boolean clearCurrentActivity(Activity activity) {
		FragmentActivity currentActivity = currentActivityRef.get();
		boolean clearing = currentActivity == null || currentActivity == activity;
		if (clearing) {
			if (AndroidUtils.DEBUG) {
				logd("clearCurrentActivity " + activity + " via "
						+ AndroidUtils.getCompressedStackTrace(3));
			}
			hasCurrentActivity = false;
			currentActivityRef = new WeakReference<>(null);
			SessionManager.clearActiveSession(this);
		} else {
			if (AndroidUtils.DEBUG) {
				logd("skip clearCurrentActivity " + activity + " via "
						+ AndroidUtils.getCompressedStackTrace(3));
			}
		}
		return clearing;
	}

	/**
	 * @return -1 == Not Vuze; 0 == Vuze
	 */
	public int getRPCVersionAZ() {
		ensureNotDestroyed();
		return transmissionRPC == null ? -1 : transmissionRPC.getRPCVersionAZ();
	}

	public boolean getSupports(int id) {
		if (AndroidUtils.DEBUG && transmissionRPC == null) {
			Log.w(TAG, "getSupports: No rpc calling getSupports(" + id + ") "
					+ AndroidUtils.getCompressedStackTrace());
		}
		return transmissionRPC != null && transmissionRPC.getSupports(id);
	}

	public String getBaseURL() {
		ensureNotDestroyed();
		return baseURL;
	}

	@Thunk
	static void showUrlFailedDialog(final FragmentActivity activity,
			final String errMsg, final String url, final String sample) {
		if (activity == null) {
			Log.e(null, "No activity for error message " + errMsg);
			return;
		}
		AndroidUtilsUI.runOnUIThread(activity, false, validActivity -> {
			String s = validActivity.getResources().getString(
					R.string.torrent_url_add_failed, url, sample);

			Spanned msg = AndroidUtils.fromHTML(s);
			AlertDialog.Builder builder = new MaterialAlertDialogBuilder(
					validActivity).setMessage(msg).setCancelable(true).setNegativeButton(
							android.R.string.ok, (dialog, which) -> {
							}).setNeutralButton(R.string.torrent_url_add_failed_openurl,
									(dialog, which) -> AndroidUtilsUI.openURL(validActivity, url,
											url));
			builder.show();
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

	public FragmentActivity getCurrentActivity() {
		ensureNotDestroyed();

		return currentActivityRef.get();
	}

	void ensureNotDestroyed() {
		if (destroyed) {
			Log.e(TAG, "Accessing destroyed Session from "
					+ AndroidUtils.getCompressedStackTrace(15));
		}
	}

	public boolean isDestroyed() {
		return destroyed;
	}

	protected void destroy() {
		if (AndroidUtils.DEBUG) {
			logd("destroy: " + AndroidUtils.getCompressedStackTrace());
		}
		cancelRefreshHandler();
		if (transmissionRPC != null) {
			transmissionRPC.destroy();
		}
		torrent.clearCache();
		torrent.clearFilesCaches(false);
		availabilityListeners.clear();
		refreshTriggerListeners.clear();
		sessionSettingsChangedListeners.clear();
		subscription.destroy();
		tag.destroy();
		torrent.destroy();
		currentActivityRef = new WeakReference<>(null);
		BiglyBTApp.getNetworkState().removeListener(this);

		destroyed = true;
		boolean isCore = remoteProfile.getRemoteType() == RemoteProfile.TYPE_CORE;

		if (isCore) {
			BiglyCoreUtils.detachCore();
		}
	}

	@SuppressLint("LogConditional")
	public void logd(String s) {
		Log.d(TAG, remoteProfile.getNick() + "] " + s);
	}

	@SuppressLint("LogConditional")
	public void log(int priority, String s) {
		Log.println(priority, TAG, remoteProfile.getNick() + "] " + s);
	}
}
