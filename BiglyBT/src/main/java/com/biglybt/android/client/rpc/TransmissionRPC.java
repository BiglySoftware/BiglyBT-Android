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

package com.biglybt.android.client.rpc;

import android.util.Log;
import android.util.SparseBooleanArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;

import com.biglybt.android.client.*;
import com.biglybt.android.client.session.*;
import com.biglybt.android.util.BiglyCoreUtils;
import com.biglybt.android.util.JSONUtils;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import java.io.Serializable;
import java.net.ConnectException;
import java.util.*;

@SuppressWarnings({
	"rawtypes",
	"HardCodedStringLiteral"
})
public class TransmissionRPC
{
	private static final String RPCKEY_METHOD = "method";

	private static final String RPCKEY_ARGUMENTS = "arguments";

	private static final String RPCKEY_FIELDS = "fields";

	private class ReplyMapReceivedListenerWithRefresh
		implements ReplyMapReceivedListener
	{
		final ReplyMapReceivedListener l;

		final long[] ids;

		@NonNull
		final List<String> fields;

		int[] fileIndexes;

		String[] fileFields;

		final String callID;

		@Thunk
		ReplyMapReceivedListenerWithRefresh(String callID,
				@Nullable ReplyMapReceivedListener l, @Nullable long[] ids) {
			this.callID = callID;
			this.l = l;
			this.ids = ids;
			this.fields = getBasicTorrentFieldIDs();
		}

		public ReplyMapReceivedListenerWithRefresh(String callID,
				ReplyMapReceivedListener l, long[] torrentIDs, int[] fileIndexes,
				@Nullable String[] fileFields) {
			this.callID = callID;
			this.l = l;
			this.ids = torrentIDs;
			this.fileIndexes = fileIndexes;
			this.fileFields = fileFields;
			this.fields = getFileInfoFields(true);
		}

		@Override
		public void rpcSuccess(String requestID, Map optionalMap) {
			new Thread(() -> {
				try {
					Thread.sleep(800);
				} catch (InterruptedException ignore) {
				}

				getTorrents(callID, ids, fields, fileIndexes, fileFields, null);
			}).start();
			if (l != null) {
				l.rpcSuccess(requestID, optionalMap);
			}
		}

		@Override
		public void rpcFailure(String requestID, String message) {
			if (l != null) {
				l.rpcFailure(requestID, message);
			}
		}

		@Override
		public void rpcError(String requestID, Throwable e) {
			if (l != null) {
				l.rpcError(requestID, e);
			}
		}
	}

	private static final String TAG = "RPC";

	// From Transmission's rpcimp.c :(
	// #define RECENTLY_ACTIVE_SECONDS 60
	private static final long RECENTLY_ACTIVE_MS = 60 * 1000L;

	@Thunk
	@NonNull
	String rpcURL;

	@Thunk
	Map<String, String> headers;

	@Thunk
	int rpcVersion;

	@Thunk
	int rpcVersionAZ;

	@Thunk
	Boolean hasFileCountField = null;

	private List<String> basicTorrentFieldIDs;

	private final List<TorrentListReceivedListener> torrentListReceivedListeners = new ArrayList<>();

	@Thunk
	final List<SessionSettingsReceivedListener> sessionSettingsReceivedListeners = new ArrayList<>();

	@Thunk
	Map latestSessionSettings;

	@Thunk
	long lastRecentTorrentGet;

	@Thunk
	int cacheBuster = new Random().nextInt();

	@NonNull
	@Thunk
	final Session session;

	@Thunk
	@NonNull
	SparseBooleanArray mapSupports = new SparseBooleanArray();

	private String[] defaultFileFields = {};

	@Thunk
	RestJsonClient restJsonClient = null;

	@Thunk
	String biglyVersion;

	@Thunk
	String version;

	private boolean isDestroyed;

	@Thunk
	boolean requireStringUnescape;

	public TransmissionRPC(@NonNull Session session, @NonNull String rpcURL) {
		this.session = session;

		this.rpcURL = rpcURL;

		updateSessionSettings();
	}

	public void getSessionStats(String[] fields, ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_SESSION_STATS);
		if (fields != null) {
			Map<String, Object> mapArguments = new HashMap<>();
			map.put(RPCKEY_ARGUMENTS, mapArguments);

			mapArguments.put(RPCKEY_FIELDS, fields);
		}

		sendRequest(TransmissionVars.METHOD_SESSION_STATS, map, l);
	}

	private void updateSessionSettings() {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_SESSION_GET);
		sendRequest(TransmissionVars.METHOD_SESSION_GET, map,
				new ReplyMapReceivedListener() {

					@Override
					public void rpcSuccess(String requestID, Map map) {
						synchronized (sessionSettingsReceivedListeners) {
							latestSessionSettings = map;

							// XXX TODO: Implement "message" key, and alert user
							rpcVersion = MapUtils.getMapInt(map, "rpc-version", -1);
							rpcVersionAZ = MapUtils.getMapInt(map, "az-rpc-version", -1);
							if (rpcVersionAZ < 0 && map.containsKey("az-version")) {
								rpcVersionAZ = 0;
							}
							if (rpcVersionAZ >= 2) {
								hasFileCountField = true;
							}
							requireStringUnescape = rpcVersionAZ > 0 && rpcVersionAZ < 5;
							List<String> listSupports = MapUtils.getMapList(map,
									"rpc-supports", null);
							if (listSupports != null) {
								mapSupports.put(RPCSupports.SUPPORTS_GZIP,
										listSupports.contains("rpc:receive-gzip"));
								mapSupports.put(RPCSupports.SUPPORTS_RCM,
										listSupports.contains("method:rcm-set-enabled"));
								mapSupports.put(RPCSupports.SUPPORTS_TORRENT_RENAAME,
										listSupports.contains("field:torrent-set-name"));
								mapSupports.put(RPCSupports.SUPPORTS_TAGS,
										listSupports.contains("method:tags-get-list"));
								mapSupports.put(RPCSupports.SUPPORTS_SUBSCRIPTIONS,
										listSupports.contains("method:subscription-get"));
								mapSupports.put(RPCSupports.SUPPORTS_FIELD_ISFORCED,
										listSupports.contains("field:torrent-get:isForced"));
								mapSupports.put(RPCSupports.SUPPORTS_FIELD_SEQUENTIAL,
										listSupports.contains("field:torrent:sequential"));
								mapSupports.put(RPCSupports.SUPPORTS_CONFIG,
										listSupports.contains("method:config-get")
												&& listSupports.contains("method:config-set"));
							}
							mapSupports.put(RPCSupports.SUPPORTS_SEARCH, rpcVersionAZ >= 0);

							version = (String) map.get("version");
							biglyVersion = (String) map.get("biglybt-version");

							String azVersion = (String) map.get("az-version");
							boolean goodAZ = azVersion == null
									|| compareVersions(azVersion, "5.7.4.1_B02") >= 0;

							restJsonClient = RestJsonClient.getInstance(
									getSupports(RPCSupports.SUPPORTS_GZIP), goodAZ);

							if (AndroidUtils.DEBUG_RPC) {
								Log.d(TAG, "Received Session-Get. " + map);
							}
							for (SessionSettingsReceivedListener l : sessionSettingsReceivedListeners) {
								l.sessionPropertiesUpdated(map);
							}
						}
					}

					@Override
					public void rpcFailure(String requestID, String message) {
						FragmentActivity activity = session.getCurrentActivity();
						if (activity != null) {
							AndroidUtilsUI.showConnectionError(activity, message, true);
						}
					}

					@Override
					public void rpcError(String requestID, Throwable e) {

						FragmentActivity activity = session.getCurrentActivity();
						String profileID = session.getRemoteProfile().getID();
						if (activity != null) {
							if (e instanceof RPCException) {
								RPCException rpcException = (RPCException) e;
								if (rpcException.getResponseCode() == 401) { // Not Authorized
									if (session.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_NORMAL) {
										AndroidUtilsUI.showConnectionError(activity, profileID,
												R.string.rpc_not_authorized_adv, false);
										return;
									}
								}
							}

							if (rpcURL.contains(".i2p:")) {
								String err = null;
								if (e instanceof RPCException) {
									RPCException re = (RPCException) e;
									String responseText = re.getHttpResponseText();
									if (responseText != null && responseText.contains(
											"Could not find the following destination")) {
										err = activity.getString(R.string.i2p_could_not_connect);
									}
								}
								AndroidUtilsUI.showConnectionError(activity,
										err == null ? "I2P: " + AndroidUtils.getCauses(e) : err,
										false);
							} else {
								AndroidUtilsUI.showConnectionError(activity, profileID, e,
										false);
							}
						} else {
							SessionManager.removeSession(profileID, true);
						}
					}
				});
	}

	public void addTorrentByUrl(String url, String friendlyName,
			boolean addPaused, @NonNull TorrentAddedReceivedListener l) {
		addTorrent(false, url, friendlyName, addPaused, l);
	}

	public void addTorrentByMeta(String torrentData, boolean addPaused,
			@NonNull TorrentAddedReceivedListener l) {
		addTorrent(true, torrentData, null, addPaused, l);
	}

	private void addTorrent(boolean isTorrentData, String data,
			@Nullable String friendlyName, boolean addPaused,
			@NonNull final TorrentAddedReceivedListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, "torrent-add");

		Map<String, Object> mapArguments = new HashMap<>();
		map.put(RPCKEY_ARGUMENTS, mapArguments);
		mapArguments.put("paused", addPaused);
		String id;
		if (isTorrentData) {
			id = "addTorrentByMeta";
			mapArguments.put("metainfo", data);
		} else {
			id = "addTorrentByUrl";
			mapArguments.put("filename", data);
			if (friendlyName != null) {
				mapArguments.put("name", friendlyName);
			}
		}
		//download-dir

		sendRequest(id, map, new ReplyMapReceivedListener() {

			@Override
			public void rpcSuccess(String requestID, Map optionalMap) {
				Map<Object, Object> mapTorrentAdded = MapUtils.getMapMap(optionalMap,
						"torrent-added", null);
				if (mapTorrentAdded != null) {
					l.torrentAdded(mapTorrentAdded, false);
					return;
				}
				Map<Object, Object> mapTorrentDupe = MapUtils.getMapMap(optionalMap,
						"torrent-duplicate", null);
				if (mapTorrentDupe != null) {
					l.torrentAdded(mapTorrentDupe, true);
				}
			}

			@Override
			public void rpcFailure(String requestID, String message) {
				l.torrentAddFailed(message);
			}

			@Override
			public void rpcError(String requestID, Throwable e) {
				l.torrentAddError(e);
			}
		});
	}

	/**
	 * Always triggers TorrentListReceivedListener
	 */
	public void getAllTorrents(String callID, TorrentListReceivedListener l) {
		getTorrents(callID, null, getBasicTorrentFieldIDs(), null, null, l);
	}

	/**
	 * Always triggers TorrentListReceivedListener
	 */
	public void getTorrent(String callID, long torrentID, List<String> fields,
			@Nullable TorrentListReceivedListener l) {
		getTorrents(callID, new long[] {
			torrentID
		}, fields, null, null, l);
	}

	/**
	 * Always triggers TorrentListReceivedListener
	 * 
	 * XXX If TorrentListReceivedListener is an Activity or Fragment, this is 
	 *     may lock the activity for a very long time (ie. when remote is down)
	 */
	@Thunk
	void getTorrents(final String callID, @Nullable final Object ids,
			final List<String> fields, @Nullable final int[] fileIndexes,
			@Nullable String[] fileFields,
			@Nullable final TorrentListReceivedListener l) {

		if (AndroidUtilsUI.isUIThread()) {
			new Thread(() -> getTorrents(callID, ids, fields, fileIndexes, fileFields,
					l)).start();
			return;
		}

		List<String> ourFields = fields == null ? new ArrayList<>()
				: new ArrayList<>(fields);

		Map<String, Object> map = new HashMap<>(2);
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_TORRENT_GET);

		Map<String, Object> mapArguments = new HashMap<>();
		map.put(RPCKEY_ARGUMENTS, mapArguments);

		if (ids != null) {
			mapArguments.put(TransmissionVars.ARG_IDS, ids);
		}

		mapArguments.put("base-url", session.getBaseURL());

		if (rpcVersionAZ >= 3) {

			if (ourFields.isEmpty()
					|| ourFields.contains(TransmissionVars.FIELD_TORRENT_FILES)) {
				mapArguments.put(TransmissionVars.ARG_TORRENT_GET_FILE_FIELDS,
						fileFields == null ? defaultFileFields : fileFields);

				ourFields.remove(TransmissionVars.FIELD_TORRENT_FILESTATS);

				// compact mode, where each file is an array instead of a map, and
				// they keys are stored in fileKeys
				if (rpcVersionAZ >= 7) {
					mapArguments.put("mapPerFile", false);
				}

				// build "hc"
				long[] torrentIDs = {};
				if (ids instanceof long[]) {
					torrentIDs = (long[]) ids;
				} else if (ids instanceof Number) {
					torrentIDs = new long[] {
						((Number) ids).longValue()
					};
				}
				for (long torrentID : torrentIDs) {
					if (fileIndexes != null) {
						mapArguments.put("file-indexes-" + torrentID, fileIndexes);
					}

					Map<?, ?> mapTorrent = session.torrent.getCachedTorrent(torrentID);
					if (mapTorrent != null) {
						List<Object> listFiles = MapUtils.getMapList(mapTorrent,
								TransmissionVars.FIELD_TORRENT_FILES, null);
						if (listFiles != null) {
							if (rpcVersionAZ >= 7 && false) {
								// Disabled.  Uses a lot of memory since strings are duplicated
								// The old method, with hc as list, may take more bandwidth,
								// but the strings are duplicated.
								StringBuilder sb = new StringBuilder();
								boolean first = true;
								if (fileIndexes != null) {
									for (int fileIndex : fileIndexes) {
										if (first) {
											first = false;
										} else {
											sb.append(",");
										}
										Map mapFile = (Map) listFiles.get(fileIndex);
										sb.append(mapFile.get("hc"));
									}
								} else {
									for (int i = 0; i < listFiles.size(); i++) {
										if (first) {
											first = false;
										} else {
											sb.append(",");
										}
										Map mapFile = (Map) listFiles.get(i);
										sb.append(mapFile.get("hc"));
									}
								}
								mapArguments.put("files-hc-" + torrentID, sb.toString());
							} else {
								List<Object> listHCs = new ArrayList<>();
								if (fileIndexes != null) {
									for (int fileIndex : fileIndexes) {
										Map mapFile = (Map) listFiles.get(fileIndex);
										listHCs.add(mapFile.get("hc"));
									}
								} else {
									for (int i = 0; i < listFiles.size(); i++) {
										Map mapFile = (Map) listFiles.get(i);
										listHCs.add(mapFile.get("hc"));
									}
								}
								mapArguments.put("files-hc-" + torrentID, listHCs);
							}
						}
					}
				}

			}
		}

		// Always include torrent id so we can ensure Session cache gets updated
		if (!ourFields.isEmpty()
				&& !ourFields.contains(TransmissionVars.FIELD_TORRENT_ID)) {
			ourFields.add(TransmissionVars.FIELD_TORRENT_ID);
		}

		if (ourFields.size() > 0) {
			mapArguments.put(RPCKEY_FIELDS, ourFields);
		}

		String idList = (ids instanceof long[]) ? Arrays.toString(((long[]) ids))
				: "" + ids;
		sendRequest(
				"getTorrents " + callID + " t=" + idList + "/f="
						+ Arrays.toString(fileIndexes) + ", " + ourFields.size() + "/"
						+ (fileFields == null ? "null" : fileFields.length),
				map, new ReplyMapReceivedListener() {

					@Override
					public void rpcSuccess(String requestID, Map optionalMap) {
						List<Object> list = MapUtils.getMapList(optionalMap, "torrents",
								Collections.emptyList());
						if (hasFileCountField == null || !hasFileCountField) {
							for (Object o : list) {
								if (!(o instanceof Map)) {
									continue;
								}
								Map map = (Map) o;
								// Transmission 3.0 returns 0 when requesting unknown fields
								// Transmission < 3.0 doesn't return field at all
								int fileCount = MapUtils.getMapInt(map,
										TransmissionVars.FIELD_TORRENT_FILE_COUNT, 0);
								if (fileCount > 0) {
									hasFileCountField = true;
									continue;
								}
								fileCount = MapUtils.getMapList(map,
										TransmissionVars.FIELD_TORRENT_PRIORITIES,
										Collections.emptyList()).size();
								if (fileCount > 0) {
									map.put(TransmissionVars.FIELD_TORRENT_FILE_COUNT, fileCount);
								}
							}
						}

						if (ourFields.contains(
								TransmissionVars.FIELD_TORRENT_PERCENT_DONE)) {
							for (Object o : list) {
								if (!(o instanceof Map)) {
									continue;
								}
								Map<String, Object> map = (Map<String, Object>) o;
								Boolean iscomplete = session.tag.hasStateTag(map,
										Session_Tag.STATEID_COMPLETE);
								if (iscomplete == null) {
									iscomplete = MapUtils.getMapFloat(map,
											TransmissionVars.FIELD_TORRENT_PERCENT_DONE, 0) >= 1;
								}
								map.put(TransmissionVars.FIELD_TORRENT_IS_COMPLETE, iscomplete);
							}
						}

						// TODO: If we request a list of torrent IDs, and we don't get them
						//       back on "success", then we should populate the listRemoved
						List<Object> listRemoved = MapUtils.getMapList(optionalMap,
								"removed", null);

						TorrentListReceivedListener[] listReceivedListeners = getTorrentListReceivedListeners();
						for (TorrentListReceivedListener torrentListReceivedListener : listReceivedListeners) {
							torrentListReceivedListener.rpcTorrentListReceived(callID, list,
									ourFields, fileIndexes, listRemoved);
						}

						// trigger local listener after class listeners, since there's a special class listener
						// than update's the torrent in Session.
						if (l != null) {
							l.rpcTorrentListReceived(callID, list, ourFields, fileIndexes,
									listRemoved);
						}
					}

					@Override
					public void rpcFailure(String requestID, String message) {
						// send event to listeners on fail/error
						// some do a call for a specific torrentID and rely on a response
						// of some sort to clean up (ie. files view progress bar), so
						// we must fake a reply with those torrentIDs

						List list = createFakeList(ids);

						if (l != null) {
							l.rpcTorrentListReceived(callID, list, ourFields, fileIndexes,
									null);
						}
						TorrentListReceivedListener[] listReceivedListeners = getTorrentListReceivedListeners();
						for (TorrentListReceivedListener torrentListReceivedListener : listReceivedListeners) {
							torrentListReceivedListener.rpcTorrentListReceived(callID, list,
									ourFields, fileIndexes, null);
						}

						if (AndroidUtils.DEBUG_RPC) {
							Log.d(TAG,
									requestID + "] rpcFailure.  fake listener for "
											+ listReceivedListeners.length + "/" + (l == null ? 0 : 1)
											+ ", " + list);
						}
					}

					private List createFakeList(@Nullable Object ids) {
						List<Map> list = new ArrayList<>();
						if (ids instanceof Long) {
							HashMap<String, Object> map = new HashMap<>(2);
							map.put("id", ids);
							list.add(map);
							return list;
						}
						if (ids instanceof long[]) {
							for (long torrentID : (long[]) ids) {
								HashMap<String, Object> map = new HashMap<>(2);
								map.put("id", torrentID);
								list.add(map);
							}
						}
						return list;
					}

					@Override
					public void rpcError(String requestID, Throwable e) {
						// send event to listeners on fail/error
						// some do a call for a specific torrentID and rely on a response
						// of some sort to clean up (ie. files view progress bar), so
						// we must fake a reply with those torrentIDs

						List list = createFakeList(ids);

						if (l != null) {
							l.rpcTorrentListReceived(callID, list, ourFields, fileIndexes,
									null);
						}
						TorrentListReceivedListener[] listReceivedListeners = getTorrentListReceivedListeners();
						for (TorrentListReceivedListener torrentListReceivedListener : listReceivedListeners) {
							torrentListReceivedListener.rpcTorrentListReceived(callID, list,
									ourFields, fileIndexes, null);
						}

						if (AndroidUtils.DEBUG_RPC) {
							Log.d(TAG,
									requestID + "] rpcError.  fake listener for "
											+ listReceivedListeners.length + "/" + (l == null ? 0 : 1)
											+ ", " + list);
						}
						FragmentActivity activity = session.getCurrentActivity();
						if (activity != null) {
							AndroidUtilsUI.showConnectionError(activity,
									session.getRemoteProfile().getID(), e, true);
						}
					}
				});
	}

	public void destroy() {
		torrentListReceivedListeners.clear();
		sessionSettingsReceivedListeners.clear();
		isDestroyed = true;
	}

	@Thunk
	@WorkerThread
	void sendRequest(final String requestID, final Map data,
			@Nullable final ReplyMapReceivedListener l) {

		if (AndroidUtils.DEBUG) {
			RemoteProfile remoteProfile = session.getRemoteProfile();
			if (!remoteProfile.isLocalHost()) {
				boolean inForeground = BiglyBTApp.isApplicationInForeground();
				boolean isAppVisible = BiglyBTApp.isApplicationVisible();
				if (!isAppVisible) {
					Log.e(TAG, "sendRequest(\"" + requestID + "\", " + data + ", " + l
							+ ") is not visible " + AndroidUtils.getCompressedStackTrace());
				} else if (!inForeground) {
					Log.w(TAG, "sendRequest(\"" + requestID + "\", " + data + ", " + l
							+ ") is background " + AndroidUtils.getCompressedStackTrace());
				}
			}
		}
		if (isDestroyed) {
			if (AndroidUtils.DEBUG) {
				String s = JSONUtils.encodeToJSON(data);
				Log.w(TAG,
						"sendRequest(" + requestID + ","
								+ (s.length() > 999 ? s.substring(0, 999) : s) + "," + l
								+ ") ignored, RPC Destroyed");
			}
			if (l != null) {
				l.rpcFailure(requestID, "RPC not available");
			}
			return;
		}

		if (requestID == null || data == null) {
			if (AndroidUtils.DEBUG_RPC) {
				String s = JSONUtils.encodeToJSON(data);
				Log.e(TAG, "sendRequest(" + requestID + ","
						+ (s.length() > 999 ? s.substring(0, 999) : s) + "," + l + ")");
			}
			return;
		}

		new Thread(() -> {
			data.put("random", Integer.toHexString(cacheBuster++));
			RemoteProfile remoteProfile = session.getRemoteProfile();
			try {
				if (restJsonClient == null) {
					restJsonClient = RestJsonClient.getInstance(false, false);
				}
				Map<?, ?> reply = restJsonClient.connect(requestID, rpcURL, data,
						headers, remoteProfile.getUser(), remoteProfile.getAC());

				String result = MapUtils.getMapString(reply, "result", "");
				if (l != null) {
					if ("success".equals(result)) {
						l.rpcSuccess(requestID, MapUtils.getMapMap(reply, RPCKEY_ARGUMENTS,
								Collections.emptyMap()));
					} else {
						if (AndroidUtils.DEBUG_RPC) {
							Log.d(TAG, requestID + "] rpcFailure: " + result);
						}
						// clean up things like:
						// org.gudy.azureus2.plugins.utils.resourcedownloader
						// .ResourceDownloaderException: http://foo.torrent: I/O
						// Exception while downloading 'http://foo.torrent', Operation
						// timed out
						result = result.replaceAll("org\\.[a-z.]+:", "");
						result = result.replaceAll("com\\.[a-z.]+:", "");
						l.rpcFailure(requestID, result);
					}
				}
			} catch (RPCException e) {
				int statusCode = e.getResponseCode();
				if (statusCode == 409) {
					if (AndroidUtils.DEBUG_RPC) {
						Log.d(TAG, "409: retrying");
					}
					headers = e.getFirstHeader("X-Transmission-Session-Id");
					sendRequest(requestID, data, l);
					return;
				}

				Throwable cause = e.getCause();
				if (cause instanceof ConnectException) {
					if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_CORE
							&& !BiglyCoreUtils.isCoreStarted()) {
						BiglyCoreUtils.waitForCore(session.getCurrentActivity());
						sendRequest(requestID, data, l);
						return;
					}
				}

				if (AndroidUtils.DEBUG_RPC) {
					String s = JSONUtils.encodeToJSON(data);
					Log.e(TAG,
							"sendRequest(" + requestID + ","
									+ (s.length() > 999 ? s.substring(0, 999) + "..." : s) + ","
									+ l + ")",
							e);
				}
				if (l != null) {
					l.rpcError(requestID, e);
				}
				// TODO: trigger a generic error listener, so we can put a "Could
				// not connect" status text somewhere
			}
		}, "sendRequest" + requestID).start();
	}

	@NonNull
	public synchronized List<String> getBasicTorrentFieldIDs() {
		if (basicTorrentFieldIDs == null) {

			basicTorrentFieldIDs = new ArrayList<>();
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_ID);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_HASH_STRING);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_NAME);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_PERCENT_DONE);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_SIZE_WHEN_DONE);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_RATE_UPLOAD);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_RATE_DOWNLOAD);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_ERROR); // TransmissionVars.TR_STAT_*
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_ERROR_STRING);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_ETA);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_DATE_ACTIVITY);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_POSITION);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_DATE_ADDED);
			//basicTorrentFieldIDs.add("speedHistory");
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_LEFT_UNTIL_DONE);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_TAG_UIDS);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_STATUS); // TransmissionVars
			// .TR_STATUS_*
		}

		List<String> fields = new ArrayList<>(basicTorrentFieldIDs);
		if (hasFileCountField == null) {
			fields.add(TransmissionVars.FIELD_TORRENT_FILE_COUNT); // azRPC 2+
			fields.add(TransmissionVars.FIELD_TORRENT_PRIORITIES); // for filesCount
		} else if (hasFileCountField) {
			fields.add(TransmissionVars.FIELD_TORRENT_FILE_COUNT); // azRPC 2+
		} else {
			fields.add(TransmissionVars.FIELD_TORRENT_PRIORITIES); // for filesCount
		}

		if (getSupports(RPCSupports.SUPPORTS_FIELD_ISFORCED)) {
			fields.add(TransmissionVars.FIELD_TORRENT_IS_FORCED);
		}

		if (getSupports(RPCSupports.SUPPORTS_FIELD_SEQUENTIAL)) {
			fields.add(TransmissionVars.FIELD_TORRENT_SEQUENTIAL);
		}

		return fields;
	}

	/**
	 * Get recently-active torrents, or all torrents if there are no recents
	 * <br>
	 * Always triggers TorrentListReceivedListener
	 */
	public void getRecentTorrents(String callID,
			@Nullable final TorrentListReceivedListener l) {
		getTorrents(callID, "recently-active", getBasicTorrentFieldIDs(), null,
				null, new TorrentListReceivedListener() {
					boolean doingAll = false;

					@Override
					public void rpcTorrentListReceived(String callID,
							List<?> addedTorrentMaps, List<String> fields,
							final int[] fileIndexes, List<?> removedTorrentIDs) {
						long diff = System.currentTimeMillis() - lastRecentTorrentGet;
						if (!doingAll && addedTorrentMaps.size() == 0) {
							if (diff >= RECENTLY_ACTIVE_MS) {
								doingAll = true;
								getAllTorrents(callID, this);
							}
						} else {
							lastRecentTorrentGet = System.currentTimeMillis();
						}
						if (l != null) {
							l.rpcTorrentListReceived(callID, addedTorrentMaps, fields,
									fileIndexes, removedTorrentIDs);
						}
					}
				});
	}

	@NonNull
	@Thunk
	List<String> getFileInfoFields(boolean includeBasic) {
		List<String> fieldIDs = includeBasic ? getBasicTorrentFieldIDs()
				: new ArrayList<>();
		if (!includeBasic) {
			fieldIDs.add(TransmissionVars.FIELD_TORRENT_ID);
		}
		fieldIDs.add(TransmissionVars.FIELD_TORRENT_FILES);
		fieldIDs.add(TransmissionVars.FIELD_TORRENT_FILESTATS);
		return fieldIDs;
	}

	/**
	 * Always triggers TorrentListReceivedListener
	 */
	public void getTorrentFileInfo(String callID, Object ids,
			@Nullable int[] fileIndexes, TorrentListReceivedListener l) {
		getTorrents(callID, ids, getFileInfoFields(false), fileIndexes,
				defaultFileFields, l);
	}

	/**
	 * Always triggers TorrentListReceivedListener
	 */
	public void getTorrentPeerInfo(String callID, Object ids,
			TorrentListReceivedListener l) {
		List<String> fieldIDs = new ArrayList<>();
		fieldIDs.add(TransmissionVars.FIELD_TORRENT_ID);
		fieldIDs.add(TransmissionVars.FIELD_TORRENT_PEERS);

		getTorrents(callID, ids, fieldIDs, null, null, l);
	}

	public void simpleRpcCall(String method, ReplyMapReceivedListener l) {
		simpleRpcCall(method, (Map) null, l);
	}

	public void simpleRpcCall(String method, long[] ids,
			@Nullable ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, method);
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<>();
			map.put(RPCKEY_ARGUMENTS, mapArguments);
			mapArguments.put(TransmissionVars.ARG_IDS, ids);
		}
		sendRequest(method, map, l);
	}

	public void simpleRpcCall(String method, @Nullable Map arguments,
			ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, method);
		if (arguments != null) {
			map.put(RPCKEY_ARGUMENTS, arguments);
		}
		sendRequest(method, map, l);
	}

	public void simpleRpcCallWithRefresh(String callID, String method, long[] ids,
			ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, method);
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<>();
			map.put(RPCKEY_ARGUMENTS, mapArguments);
			mapArguments.put(TransmissionVars.ARG_IDS, ids);
		}
		sendRequest(method, map,
				new ReplyMapReceivedListenerWithRefresh(callID, l, ids));
	}

	public void startTorrents(String callID, @Nullable long[] ids,
			boolean forceStart, @Nullable ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD,
				forceStart ? TransmissionVars.METHOD_TORRENT_START_NOW
						: TransmissionVars.METHOD_TORRENT_START);
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<>();
			map.put(RPCKEY_ARGUMENTS, mapArguments);
			mapArguments.put(TransmissionVars.ARG_IDS, ids);
		}
		sendRequest("startTorrents", map,
				new ReplyMapReceivedListenerWithRefresh(callID, l, ids));
	}

	public void stopTorrents(String callID, @Nullable long[] ids,
			@Nullable final ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_TORRENT_STOP);
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<>();
			map.put(RPCKEY_ARGUMENTS, mapArguments);
			mapArguments.put(TransmissionVars.ARG_IDS, ids);
		}
		sendRequest("stopTorrents", map,
				new ReplyMapReceivedListenerWithRefresh(callID, l, ids));
	}

	public void verifyTorrents(String callID, @Nullable long[] ids,
			@Nullable final ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_TORRENT_VERIFY);
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<>();
			map.put(RPCKEY_ARGUMENTS, mapArguments);
			mapArguments.put(TransmissionVars.ARG_IDS, ids);
		}
		sendRequest("verifyTorrents", map,
				new ReplyMapReceivedListenerWithRefresh(callID, l, ids));
	}

	/**
	 * Set's priority of files, and forces a torrent refresh
	 */
	public void setFilePriority(String callID, long torrentID, int[] fileIndexes,
			int priority, @Nullable final ReplyMapReceivedListener l) {
		long[] ids = {
			torrentID
		};
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_TORRENT_SET);
		Map<String, Object> mapArguments = new HashMap<>();
		map.put(RPCKEY_ARGUMENTS, mapArguments);
		mapArguments.put(TransmissionVars.ARG_IDS, ids);

		String key;
		switch (priority) {
			case TransmissionVars.TR_PRI_HIGH:
				key = "priority-high";
				break;

			case TransmissionVars.TR_PRI_NORMAL:
				key = "priority-normal";
				break;

			case TransmissionVars.TR_PRI_LOW:
				key = "priority-low";
				break;

			default:
				return;
		}

		mapArguments.put(key, fileIndexes);

		sendRequest("setFilePriority", map, new ReplyMapReceivedListenerWithRefresh(
				callID, l, ids, fileIndexes, null));
	}

	public void setWantState(String callID, long torrentID, int[] fileIndexes,
			boolean wanted, @Nullable final ReplyMapReceivedListener l) {
		long[] torrentIDs = {
			torrentID
		};
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_TORRENT_SET);
		Map<String, Object> mapArguments = new HashMap<>();
		map.put(RPCKEY_ARGUMENTS, mapArguments);
		mapArguments.put(TransmissionVars.ARG_IDS, torrentIDs);
		mapArguments.put(wanted ? "files-wanted" : "files-unwanted", fileIndexes);

		sendRequest("setWantState", map, new ReplyMapReceivedListenerWithRefresh(
				callID, l, torrentIDs, fileIndexes, null));
	}

	public void setDisplayName(String callID, long torrentID, String newName) {
		long[] torrentIDs = {
			torrentID
		};
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_TORRENT_SET);
		Map<String, Object> mapArguments = new HashMap<>();
		map.put(RPCKEY_ARGUMENTS, mapArguments);
		mapArguments.put(TransmissionVars.ARG_IDS, torrentIDs);
		mapArguments.put("name", newName);

		sendRequest("setDisplayName", map,
				new ReplyMapReceivedListenerWithRefresh(callID, null, torrentIDs));
	}

	public void setTorrentSequential(String callID, long[] torrentIDs,
			boolean sequential) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_TORRENT_SET);
		Map<String, Object> mapArguments = new HashMap<>();
		map.put(RPCKEY_ARGUMENTS, mapArguments);
		mapArguments.put(TransmissionVars.ARG_IDS, torrentIDs);
		mapArguments.put(TransmissionVars.FIELD_TORRENT_SEQUENTIAL, sequential);

		sendRequest(TransmissionVars.FIELD_TORRENT_SEQUENTIAL, map,
				new ReplyMapReceivedListenerWithRefresh(callID, null, torrentIDs));
	}

	public void addTagToTorrents(String callID, long[] torrentIDs,
			final Object[] tags) {
		if (tags == null || tags.length == 0) {
			return;
		}
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_TORRENT_SET);
		Map<String, Object> mapArguments = new HashMap<>();
		map.put(RPCKEY_ARGUMENTS, mapArguments);
		mapArguments.put(TransmissionVars.ARG_IDS, torrentIDs);
		mapArguments.put("tagAdd", tags);

		sendRequest("addTagToTorrent", map,
				new ReplyMapReceivedListenerWithRefresh(callID, null, torrentIDs) {
					@Override
					public void rpcSuccess(String requestID, Map optionalMap) {
						boolean hasNewTag = false;
						for (Object tag : tags) {
							if (tag instanceof String) {
								hasNewTag = true;
								break;
							}
						}
						session.tag.refreshTags(!hasNewTag);
						super.rpcSuccess(requestID, optionalMap);
					}
				});
	}

	public void removeTagFromTorrents(String callID, long[] torrentIDs,
			@NonNull Object[] tags) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_TORRENT_SET);
		Map<String, Object> mapArguments = new HashMap<>();
		map.put(RPCKEY_ARGUMENTS, mapArguments);

		if (rpcVersionAZ < 4) {
			// Older AZ RPC only allowed removal of tag names
			for (int i = 0; i < tags.length; i++) {
				if (tags[i] instanceof Number) {
					Map<?, ?> tag = session.tag.getTag(((Number) tags[i]).longValue());
					tags[i] = MapUtils.getMapString(tag, TransmissionVars.FIELD_TAG_NAME,
							null);
				}
			}
		}
		mapArguments.put(TransmissionVars.ARG_IDS, torrentIDs);
		mapArguments.put("tagRemove", tags);

		sendRequest("removeTagFromTorrent", map,
				new ReplyMapReceivedListenerWithRefresh(callID, null, torrentIDs));
	}

	/**
	 * To ensure session torrent list is fully up to date,
	 * you should be using {@link Session_Torrent#addListReceivedListener}
	 * instead of this one.
	 */
	public void addTorrentListReceivedListener(TorrentListReceivedListener l) {
		synchronized (torrentListReceivedListeners) {
			if (!torrentListReceivedListeners.contains(l)) {
				torrentListReceivedListeners.add(l);
			}
		}
	}

	public void removeTorrentListReceivedListener(TorrentListReceivedListener l) {
		synchronized (torrentListReceivedListeners) {
			torrentListReceivedListeners.remove(l);
		}
	}

	public void addSessionSettingsReceivedListener(
			@NonNull SessionSettingsReceivedListener l) {
		synchronized (sessionSettingsReceivedListeners) {
			if (!sessionSettingsReceivedListeners.contains(l)) {
				sessionSettingsReceivedListeners.add(l);
				if (latestSessionSettings != null) {
					l.sessionPropertiesUpdated(latestSessionSettings);
				}
			}
		}
	}

	public void removeSessionSettingsReceivedListener(
			SessionSettingsReceivedListener l) {
		synchronized (sessionSettingsReceivedListeners) {
			sessionSettingsReceivedListeners.remove(l);
		}
	}

	@NonNull
	@Thunk
	TorrentListReceivedListener[] getTorrentListReceivedListeners() {
		return torrentListReceivedListeners.toArray(
				new TorrentListReceivedListener[0]);
	}

	public void moveTorrent(long id, String newLocation,
			@Nullable ReplyMapReceivedListener listener) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_TORRENT_SET_LOCATION);

		Map<String, Object> mapArguments = new HashMap<>();
		map.put(RPCKEY_ARGUMENTS, mapArguments);

		long[] ids = new long[] {
			id
		};
		mapArguments.put(TransmissionVars.ARG_IDS, ids);
		mapArguments.put("move", true);
		mapArguments.put("location", newLocation);

		ReplyMapReceivedListenerWithRefresh l = new ReplyMapReceivedListenerWithRefresh(
				TAG, listener, ids);
		l.fields.add(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR);
		sendRequest(TransmissionVars.METHOD_TORRENT_SET_LOCATION, map, l);
	}

	public void removeTorrent(long[] ids, boolean deleteData,
			@Nullable final ReplyMapReceivedListener listener) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_TORRENT_REMOVE);

		Map<String, Object> mapArguments = new HashMap<>();
		map.put(RPCKEY_ARGUMENTS, mapArguments);

		mapArguments.put(TransmissionVars.ARG_IDS, ids);
		mapArguments.put("delete-local-data", deleteData);

		sendRequest(TransmissionVars.METHOD_TORRENT_REMOVE, map,
				new ReplyMapReceivedListener() {

					@Override
					public void rpcSuccess(String requestID, Map<?, ?> optionalMap) {
						try {
							Thread.sleep(500);
						} catch (InterruptedException ignore) {
						}
						getRecentTorrents(requestID, null);
						if (listener != null) {
							listener.rpcSuccess(requestID, optionalMap);
						}
					}

					@Override
					public void rpcFailure(String requestID, String message) {
						if (listener != null) {
							listener.rpcFailure(requestID, message);
						}
					}

					@Override
					public void rpcError(String requestID, Throwable e) {
						if (listener != null) {
							listener.rpcError(requestID, e);
						}
					}
				});
	}

	public void updateSettings(Map<String, Object> changes) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_SESSION_SET);

		map.put(RPCKEY_ARGUMENTS, changes);

		sendRequest(TransmissionVars.METHOD_SESSION_SET, map, null);
	}

	/**
	 * Listener's map will have a "size-bytes" key
	 */
	public void getFreeSpace(String path, ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put(TransmissionVars.FIELD_FREESPACE_PATH, path);

		simpleRpcCall(TransmissionVars.METHOD_FREE_SPACE, map, l);
	}

	public void getSubscriptionList(ReplyMapReceivedListener l) {
		simpleRpcCall(TransmissionVars.METHOD_SUBSCRIPTION_GET, l);
	}

	public void getSubscriptionResults(@NonNull String id,
			ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put(TransmissionVars.ARG_IDS, new String[] {
			id
		});
		map.put(RPCKEY_FIELDS, new String[] {
			TransmissionVars.FIELD_SUBSCRIPTION_RESULTS,
			TransmissionVars.FIELD_SUBSCRIPTION_NAME
		});

		simpleRpcCall(TransmissionVars.METHOD_SUBSCRIPTION_GET, map, l);
	}

	public void removeSubscriptions(@NonNull String[] subscriptionIDs,
			ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<>(1);
		map.put(TransmissionVars.ARG_IDS, subscriptionIDs);

		simpleRpcCall(TransmissionVars.METHOD_SUBSCRIPTION_REMOVE, map, l);
	}

	public void createSubscription(@NonNull String rssURL, @NonNull String name,
			ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put("rss-url", rssURL);
		map.put("name", name);

		simpleRpcCall("subscription-add", map, l);
	}

	public int getRPCVersion() {
		return rpcVersion;
	}

	public int getRPCVersionAZ() {
		return rpcVersionAZ;
	}

	public boolean getSupports(int id) {
		return mapSupports.get(id, false);
	}

	public void setDefaultFileFields(String[] fileFields) {
		this.defaultFileFields = fileFields;
	}

	public interface MetaSearchResultsListener
	{
		boolean onMetaSearchGotEngines(Serializable searchID, List engines);

		boolean onMetaSearchGotResults(Serializable searchID, List engines,
				boolean complete);
	}

	public void startMetaSearch(@NonNull String searchString,
			@NonNull MetaSearchResultsListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put("expression", searchString);
		simpleRpcCall("vuze-search-start", map,
				(SuccessReplyMapRecievedListener) (id, optionalMap) -> {

					final Serializable searchID = (Serializable) optionalMap.get("sid");
					final Map<String, Object> mapResultsRequest = new HashMap<>();

					mapResultsRequest.put("sid", searchID);
					if (searchID != null) {
						List<Object> listEngines = MapUtils.getMapList(optionalMap,
								"engines", Collections.emptyList());

						if (!l.onMetaSearchGotEngines(searchID, listEngines)) {
							return;
						}

						simpleRpcCall(TransmissionVars.METHOD_VUZE_SEARCH_GET_RESULTS,
								mapResultsRequest, new SuccessReplyMapRecievedListener() {

									@Override
									public void rpcSuccess(String requestID,
											Map<?, ?> optionalMap) {

										boolean complete = MapUtils.getMapBoolean(optionalMap,
												"complete", true);
										List<Object> listEngines = MapUtils.getMapList(optionalMap,
												"engines", Collections.emptyList());

										if (!l.onMetaSearchGotResults(searchID, listEngines,
												complete)) {
											return;
										}
										if (!complete) {
											try {
												Thread.sleep(1500);
											} catch (InterruptedException ignored) {
											}
											simpleRpcCall(
													TransmissionVars.METHOD_VUZE_SEARCH_GET_RESULTS,
													mapResultsRequest, this);
										}

									}
								});
					}
				});
	}

	/**
	 * compare two version strings of form n.n.n.n (e.g. 1.2.3.4)
	 *
	 * @return -ve -> version_1 lower, 0 = same, +ve -> version_1 higher
	 */
	@SuppressWarnings("ConstantConditions")
	@Thunk
	static int compareVersions(@NonNull String version_1,
			@NonNull String version_2) {
		try {
			version_1 = version_1.replaceAll("_CVS", "_B100");
			version_2 = version_2.replaceAll("_CVS", "_B100");

			if (version_1.startsWith(".")) {
				version_1 = "0" + version_1;
			}
			if (version_2.startsWith(".")) {
				version_2 = "0" + version_2;
			}

			version_1 = version_1.replaceAll("[^0-9.]", ".");
			version_2 = version_2.replaceAll("[^0-9.]", ".");

			StringTokenizer tok1 = new StringTokenizer(version_1, ".");
			StringTokenizer tok2 = new StringTokenizer(version_2, ".");

			while (true) {
				if (tok1.hasMoreTokens() && tok2.hasMoreTokens()) {

					int i1 = Integer.parseInt(tok1.nextToken());
					int i2 = Integer.parseInt(tok2.nextToken());

					if (i1 != i2) {

						return (i1 - i2);
					}
				} else if (tok1.hasMoreTokens()) {

					int i1 = Integer.parseInt(tok1.nextToken());

					if (i1 != 0) {

						return (1);
					}
				} else if (tok2.hasMoreTokens()) {

					int i2 = Integer.parseInt(tok2.nextToken());

					if (i2 != 0) {

						return (-1);
					}
				} else {
					return (0);
				}
			}
		} catch (Throwable e) {

			e.printStackTrace();

			return (0);
		}
	}

	public String getClientVersion() {
		return biglyVersion == null ? version : biglyVersion;
	}

	public boolean isRequireStringUnescape() {
		return requireStringUnescape;
	}
}
