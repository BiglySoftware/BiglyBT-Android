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

import java.io.Serializable;
import java.net.ConnectException;
import java.util.*;

import org.jetbrains.annotations.NonNls;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.TransmissionVars;
import com.biglybt.android.client.session.*;
import com.biglybt.android.util.BiglyCoreUtils;
import com.biglybt.android.util.JSONUtils;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import okhttp3.internal.http.HttpHeaders;

@SuppressWarnings("rawtypes")
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
			this.fields = getFileInfoFields();
		}

		@Override
		public void rpcSuccess(String id, Map optionalMap) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(800);
					} catch (InterruptedException ignore) {
					}

					getTorrents(callID, ids, fields, fileIndexes, fileFields, null);
				}
			}).start();
			if (l != null) {
				l.rpcSuccess(id, optionalMap);
			}
		}

		@Override
		public void rpcFailure(String id, String message) {
			if (l != null) {
				l.rpcFailure(id, message);
			}
		}

		@Override
		public void rpcError(String id, Exception e) {
			if (l != null) {
				l.rpcError(id, e);
			}
		}
	}

	private static final String TAG = "RPC";

	// From Transmission's rpcimp.c :(
	// #define RECENTLY_ACTIVE_SECONDS 60
	private static final long RECENTLY_ACTIVE_MS = 60 * 1000L;

	@Thunk
	String rpcURL;

	@Thunk
	String username;

	@Thunk
	String pw;

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

	@Thunk
	Session session;

	@Thunk
	Map<String, Boolean> mapSupports = new HashMap<>();

	private String[] defaultFileFields = {};

	@Thunk
	RestJsonClient restJsonClient = null;

	@Thunk
	String azVersion;

	@Thunk
	String version;

	private boolean isDestroyed;

	public TransmissionRPC(Session session, String rpcURL, String username,
			String ac) {
		this.session = session;
		if (username != null) {
			this.username = username;
			this.pw = ac;
		}

		this.rpcURL = rpcURL;

		updateSessionSettings(ac);
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

	private void updateSessionSettings(String id) {
		Map<String, Object> map = new HashMap<>();
		map.put(RPCKEY_METHOD, "session-get");
		sendRequest(id, map, new ReplyMapReceivedListener() {

			@Override
			public void rpcSuccess(String id, Map map) {
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
					List listSupports = MapUtils.getMapList(map, "rpc-supports", null);
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
					}
					mapSupports.put(RPCSupports.SUPPORTS_SEARCH, rpcVersionAZ >= 0);
					map.put("supports", mapSupports);

					version = (String) map.get("version");
					azVersion = (String) map.get("az-version");
					boolean goodAZ = azVersion == null
							|| compareVersions(azVersion, "5.7.4.1_B02") >= 0;

					HttpHeaders.FORCE_UNKNOWN_CONTENT_LENGTH_AT = goodAZ ? -1
							: 512 * 1024L;

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
			public void rpcFailure(String id, String message) {
				Activity activity = session.getCurrentActivity();
				if (activity != null) {
					AndroidUtilsUI.showConnectionError(activity, message, true);
				}
			}

			@Override
			public void rpcError(String id, Exception e) {
				Activity activity = session.getCurrentActivity();
				String profileID = session.getRemoteProfile().getID();
				if (activity != null) {
					if (rpcURL.contains(".i2p:")) {
						String err = null;
						if (e instanceof RPCException) {
							RPCException re = (RPCException) e;
							if (re.getHttpResponseText().contains(
									"Could not find the following destination")) {
								err = "Could not find the I2P destination.  Your remote "
										+ "machine may not be running, or may have lost its I2P "
										+ "connection";
							}
						}
						AndroidUtilsUI.showConnectionError(activity,
								err == null ? "I2P: " + AndroidUtils.getCauses(e) : err, false);
					} else {
						AndroidUtilsUI.showConnectionError(activity, profileID, e, false);
					}
				} else {
					SessionManager.removeSession(profileID);
				}
			}
		});
	}

	public void addTorrentByUrl(String url, String friendlyName,
			boolean addPaused, final TorrentAddedReceivedListener l) {
		addTorrent(false, url, friendlyName, addPaused, l);
	}

	public void addTorrentByMeta(String torrentData, boolean addPaused,
			final TorrentAddedReceivedListener l) {
		addTorrent(true, torrentData, null, addPaused, l);
	}

	private void addTorrent(boolean isTorrentData, String data,
			@Nullable String friendlyName, boolean addPaused,
			final TorrentAddedReceivedListener l) {
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
			public void rpcSuccess(String id, Map optionalMap) {
				Map mapTorrentAdded = MapUtils.getMapMap(optionalMap, "torrent-added",
						null);
				if (mapTorrentAdded != null) {
					l.torrentAdded(mapTorrentAdded, false);
					return;
				}
				Map mapTorrentDupe = MapUtils.getMapMap(optionalMap,
						"torrent-duplicate", null);
				if (mapTorrentDupe != null) {
					l.torrentAdded(mapTorrentDupe, true);
					return;
				}
			}

			@Override
			public void rpcFailure(String id, String message) {
				l.torrentAddFailed(message);
			}

			@Override
			public void rpcError(String id, Exception e) {
				l.torrentAddError(e);
			}
		});
	}

	public void getAllTorrents(String callID, TorrentListReceivedListener l) {
		getTorrents(callID, null, getBasicTorrentFieldIDs(), null, null, l);
	}

	public void getTorrent(String callID, long torrentID, List<String> fields,
			@Nullable TorrentListReceivedListener l) {
		getTorrents(callID, new long[] {
			torrentID
		}, fields, null, null, l);
	}

	@Thunk
	void getTorrents(final String callID, @Nullable final Object ids,
			final List<String> fields, @Nullable final int[] fileIndexes,
			@Nullable String[] fileFields,
			@Nullable final TorrentListReceivedListener l) {

		Map<String, Object> map = new HashMap<>(2);
		map.put(RPCKEY_METHOD, TransmissionVars.METHOD_TORRENT_GET);

		Map<String, Object> mapArguments = new HashMap<>();
		map.put(RPCKEY_ARGUMENTS, mapArguments);

		mapArguments.put(RPCKEY_FIELDS, fields);

		if (ids != null) {
			mapArguments.put(TransmissionVars.ARG_IDS, ids);
		}

		mapArguments.put("base-url", session.getBaseURL());

		if (rpcVersionAZ >= 3) {

			if (fields == null
					|| fields.contains(TransmissionVars.FIELD_TORRENT_FILES)) {
				mapArguments.put(TransmissionVars.ARG_TORRENT_GET_FILE_FIELDS,
						fileFields == null ? defaultFileFields : fileFields);

				if (fields != null) {
					fields.remove(TransmissionVars.FIELD_TORRENT_FILESTATS);
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
						List listFiles = MapUtils.getMapList(mapTorrent,
								TransmissionVars.FIELD_TORRENT_FILES, null);
						if (listFiles != null) {
							List<Object> listHCs = new ArrayList<>();
							for (int i = 0; i < listFiles.size(); i++) {
								Map mapFIle = (Map) listFiles.get(i);
								listHCs.add(mapFIle.get("hc"));
							}
							mapArguments.put("files-hc-" + torrentID, listHCs);
						}
					}
				}

			}
		}

		String idList = (ids instanceof long[]) ? Arrays.toString(((long[]) ids))
				: "" + ids;
		sendRequest(
				"getTorrents t=" + idList + "/f=" + Arrays.toString(fileIndexes) + ", "
						+ (fields == null ? "null" : fields.size()) + "/"
						+ (fileFields == null ? "null" : fileFields.length),
				map, new ReplyMapReceivedListener() {

					@SuppressWarnings({
						"unchecked",
					})
					@Override
					public void rpcSuccess(String id, Map optionalMap) {
						List list = MapUtils.getMapList(optionalMap, "torrents",
								Collections.EMPTY_LIST);
						if (hasFileCountField == null || !hasFileCountField) {
							for (Object o : list) {
								if (!(o instanceof Map)) {
									continue;
								}
								Map map = (Map) o;
								if (map.containsKey(
										TransmissionVars.FIELD_TORRENT_FILE_COUNT)) {
									hasFileCountField = true;
									continue;
								}
								int fileCount = MapUtils.getMapList(map,
										TransmissionVars.FIELD_TORRENT_PRIORITIES,
										Collections.EMPTY_LIST).size();
								if (fileCount > 0) {
									map.put(TransmissionVars.FIELD_TORRENT_FILE_COUNT, fileCount);
								}
							}
						}

						if (fields == null || fields.contains(
								TransmissionVars.FIELD_TORRENT_PERCENT_DONE)) {
							for (Object o : list) {
								if (!(o instanceof Map)) {
									continue;
								}
								Map map = (Map) o;
								float donePct = MapUtils.getMapFloat(map,
										TransmissionVars.FIELD_TORRENT_PERCENT_DONE, 0);
								map.put(TransmissionVars.FIELD_TORRENT_IS_COMPLETE,
										donePct >= 1);
							}
						}

						// TODO: If we request a list of torrent IDs, and we don't get them
						//       back on "success", then we should populate the listRemoved
						List listRemoved = MapUtils.getMapList(optionalMap, "removed",
								null);

						if (l != null) {
							l.rpcTorrentListReceived(callID, list, listRemoved);
						}
						TorrentListReceivedListener[] listReceivedListeners = getTorrentListReceivedListeners();
						for (TorrentListReceivedListener torrentListReceivedListener : listReceivedListeners) {
							torrentListReceivedListener.rpcTorrentListReceived(callID, list,
									listRemoved);
						}
					}

					@Override
					public void rpcFailure(String id, String message) {
						// send event to listeners on fail/error
						// some do a call for a specific torrentID and rely on a response
						// of some sort to clean up (ie. files view progress bar), so
						// we must fake a reply with those torrentIDs

						List list = createFakeList(ids);

						if (l != null) {
							l.rpcTorrentListReceived(callID, list, null);
						}
						TorrentListReceivedListener[] listReceivedListeners = getTorrentListReceivedListeners();
						for (TorrentListReceivedListener torrentListReceivedListener : listReceivedListeners) {
							torrentListReceivedListener.rpcTorrentListReceived(callID, list,
									null);
						}

						if (AndroidUtils.DEBUG_RPC) {
							Log.d(TAG,
									id + "] rpcFailure.  fake listener for "
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
					public void rpcError(String id, Exception e) {
						// send event to listeners on fail/error
						// some do a call for a specific torrentID and rely on a response
						// of some sort to clean up (ie. files view progress bar), so
						// we must fake a reply with those torrentIDs

						List list = createFakeList(ids);

						if (l != null) {
							l.rpcTorrentListReceived(callID, list, null);
						}
						TorrentListReceivedListener[] listReceivedListeners = getTorrentListReceivedListeners();
						for (TorrentListReceivedListener torrentListReceivedListener : listReceivedListeners) {
							torrentListReceivedListener.rpcTorrentListReceived(callID, list,
									null);
						}

						if (AndroidUtils.DEBUG_RPC) {
							Log.d(TAG,
									id + "] rpcError.  fake listener for "
											+ listReceivedListeners.length + "/" + (l == null ? 0 : 1)
											+ ", " + list);
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
	void sendRequest(final @NonNls String id, final Map data,
			@Nullable final ReplyMapReceivedListener l) {

		if (isDestroyed) {
			if (AndroidUtils.DEBUG) {
				Log.w(TAG, "sendRequest(" + id + "," + JSONUtils.encodeToJSON(data)
						+ "," + l + ") ignored, RPC Destroyed");
			}
			if (l != null) {
				l.rpcFailure(id, "RPC not available");
			}
			return;
		}

		if (id == null || data == null) {
			if (AndroidUtils.DEBUG_RPC) {
				Log.e(TAG, "sendRequest(" + id + "," + JSONUtils.encodeToJSON(data)
						+ "," + l + ")");
			}
			return;
		}

		new Thread(new Runnable() {
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				data.put("random", Integer.toHexString(cacheBuster++));
				try {
					if (restJsonClient == null) {
						restJsonClient = RestJsonClient.getInstance(false, false);
					}
					Map reply = restJsonClient.connect(id, rpcURL, data, headers,
							username, pw);

					String result = MapUtils.getMapString(reply, "result", "");
					if (l != null) {
						if (result.equals("success")) {
							l.rpcSuccess(id, MapUtils.getMapMap(reply, RPCKEY_ARGUMENTS,
									Collections.EMPTY_MAP));
						} else {
							if (AndroidUtils.DEBUG_RPC) {
								Log.d(TAG, id + "] rpcFailure: " + result);
							}
							// clean up things like:
							// org.gudy.azureus2.plugins.utils.resourcedownloader
							// .ResourceDownloaderException: http://foo.torrent: I/O
							// Exception while downloading 'http://foo.torrent', Operation
							// timed out
							result = result.replaceAll("org\\.[a-z.]+:", "");
							result = result.replaceAll("com\\.[a-z.]+:", "");
							l.rpcFailure(id, result);
						}
					}
				} catch (RPCException e) {
					int statusCode = e.getResponseCode();
					if (statusCode == 409) {
						if (AndroidUtils.DEBUG_RPC) {
							Log.d(TAG, "409: retrying");
						}
						headers = e.getFirstHeader("X-Transmission-Session-Id");
						sendRequest(id, data, l);
						return;
					}

					Throwable cause = e.getCause();
					if (session != null && (cause instanceof ConnectException)) {
						RemoteProfile remoteProfile = session.getRemoteProfile();
						if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_CORE
								&& !BiglyCoreUtils.isCoreStarted()) {
							BiglyCoreUtils.waitForCore(session.getCurrentActivity(), 20000);
							sendRequest(id, data, l);
							return;
						}
					}
					if (AndroidUtils.DEBUG_RPC) {
						Log.e(TAG, "sendRequest(" + id + "," + JSONUtils.encodeToJSON(data)
								+ "," + l + ")", e);
					}
					if (l != null) {
						l.rpcError(id, e);
					}
					// TODO: trigger a generic error listener, so we can put a "Could
					// not connect" status text somewhere
				}
			}
		}, "sendRequest" + id).start();
	}

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

		return fields;
	}

	/**
	 * Get recently-active torrents, or all torrents if there are no recents
	 */
	public void getRecentTorrents(String callID,
			@Nullable final TorrentListReceivedListener l) {
		getTorrents(callID, "recently-active", getBasicTorrentFieldIDs(), null,
				null, new TorrentListReceivedListener() {
					boolean doingAll = false;

					@Override
					public void rpcTorrentListReceived(String callID,
							List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
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
							l.rpcTorrentListReceived(callID, addedTorrentMaps,
									removedTorrentIDs);
						}
					}
				});
	}

	@Thunk
	List<String> getFileInfoFields() {
		List<String> fieldIDs = getBasicTorrentFieldIDs();
		fieldIDs.add(TransmissionVars.FIELD_TORRENT_FILES);
		fieldIDs.add(TransmissionVars.FIELD_TORRENT_FILESTATS);
		return fieldIDs;
	}

	public void getTorrentFileInfo(String callID, Object ids,
			@Nullable int[] fileIndexes, TorrentListReceivedListener l) {
		getTorrents(callID, ids, getFileInfoFields(), fileIndexes,
				defaultFileFields, l);
	}

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
		map.put(RPCKEY_METHOD, forceStart ? "torrent-start-now" : "torrent-start");
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
		map.put(RPCKEY_METHOD, "torrent-stop");
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<>();
			map.put(RPCKEY_ARGUMENTS, mapArguments);
			mapArguments.put(TransmissionVars.ARG_IDS, ids);
		}
		sendRequest("stopTorrents", map,
				new ReplyMapReceivedListenerWithRefresh(callID, l, ids));
	}

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
					public void rpcSuccess(String id, Map optionalMap) {
						boolean hasNewTag = false;
						for (Object tag : tags) {
							if (tag instanceof String) {
								hasNewTag = true;
								break;
							}
						}
						session.tag.refreshTags(!hasNewTag);
						super.rpcSuccess(id, optionalMap);
					}
				});
	}

	public void removeTagFromTorrents(String callID, long[] torrentIDs,
			Object[] tags) {
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
			SessionSettingsReceivedListener l) {
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

	@Thunk
	TorrentListReceivedListener[] getTorrentListReceivedListeners() {
		return torrentListReceivedListeners.toArray(
				new TorrentListReceivedListener[torrentListReceivedListeners.size()]);
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

		sendRequest(TransmissionVars.METHOD_TORRENT_SET_LOCATION, map,
				new ReplyMapReceivedListenerWithRefresh(TAG, listener, ids));
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
					public void rpcSuccess(String id, Map<?, ?> optionalMap) {
						try {
							Thread.sleep(500);
						} catch (InterruptedException ignore) {
						}
						getRecentTorrents(id, null);
						if (listener != null) {
							listener.rpcSuccess(id, optionalMap);
						}
					}

					@Override
					public void rpcFailure(String id, String message) {
						if (listener != null) {
							listener.rpcFailure(id, message);
						}
					}

					@Override
					public void rpcError(String id, Exception e) {
						if (listener != null) {
							listener.rpcError(id, e);
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
		map.put("path", path);

		simpleRpcCall("free-space", map, l);
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

	@Thunk
	boolean getSupports(String id) {
		return MapUtils.getMapBoolean(mapSupports, id, false);
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

	public void startMetaSearch(final String searchString,
			final MetaSearchResultsListener l) {
		Map<String, Object> map = new HashMap<>();
		map.put("expression", searchString);
		simpleRpcCall("vuze-search-start", map, new ReplyMapReceivedListener() {

			@Override
			public void rpcSuccess(String id, Map<?, ?> optionalMap) {

				final Serializable searchID = (Serializable) optionalMap.get("sid");
				final Map<String, Object> mapResultsRequest = new HashMap<>();

				mapResultsRequest.put("sid", searchID);
				if (searchID != null) {
					List listEngines = MapUtils.getMapList(optionalMap, "engines",
							Collections.emptyList());

					if (!l.onMetaSearchGotEngines(searchID, listEngines)) {
						return;
					}

					simpleRpcCall(TransmissionVars.METHOD_VUZE_SEARCH_GET_RESULTS,
							mapResultsRequest, new ReplyMapReceivedListener() {

								@Override
								public void rpcSuccess(String id, Map<?, ?> optionalMap) {

									boolean complete = MapUtils.getMapBoolean(optionalMap,
											"complete", true);
									List listEngines = MapUtils.getMapList(optionalMap, "engines",
											Collections.emptyList());

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

								@Override
								public void rpcFailure(String id, String message) {
								}

								@Override
								public void rpcError(String id, Exception e) {
								}
							});
				}
			}

			@Override
			public void rpcFailure(String id, String message) {
			}

			@Override
			public void rpcError(String id, Exception e) {
			}
		});
	}

	/**
	 * compare two version strings of form n.n.n.n (e.g. 1.2.3.4)
	 *
	 * @return -ve -> version_1 lower, 0 = same, +ve -> version_1 higher
	 */
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
		return azVersion == null ? version : azVersion;
	}
}
