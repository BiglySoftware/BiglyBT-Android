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
 */

package com.vuze.android.remote.rpc;

import java.util.*;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.UsernamePasswordCredentials;

import android.app.Activity;
import android.util.Log;

import com.vuze.android.remote.*;
import com.vuze.util.JSONUtils;
import com.vuze.util.MapUtils;

@SuppressWarnings("rawtypes")
public class TransmissionRPC
{
	private final class ReplyMapReceivedListenerWithRefresh
		implements ReplyMapReceivedListener
	{
		private final ReplyMapReceivedListener l;

		private final long[] ids;

		private List<String> fields;

		private int[] fileIndexes;

		private String[] fileFields;

		private String callID;

		private ReplyMapReceivedListenerWithRefresh(String callID,
				ReplyMapReceivedListener l, long[] ids) {
			this.callID = callID;
			this.l = l;
			this.ids = ids;
			this.fields = getBasicTorrentFieldIDs();
		}

		public ReplyMapReceivedListenerWithRefresh(String callID,
				ReplyMapReceivedListener l, long[] torrentIDs, int[] fileIndexes,
				String[] fileFields) {
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
						Thread.sleep(500);
					} catch (InterruptedException e) {
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
	protected static final long RECENTLY_ACTIVE_MS = 60 * 1000l;

	private String rpcURL;

	private UsernamePasswordCredentials creds;

	protected Header[] headers;

	private int rpcVersion;

	private int rpcVersionAZ;

	private Boolean hasFileCountField = null;

	private List<String> basicTorrentFieldIDs;

	private List<TorrentListReceivedListener> torrentListReceivedListeners = new ArrayList<TorrentListReceivedListener>();

	private List<SessionSettingsReceivedListener> sessionSettingsReceivedListeners = new ArrayList<SessionSettingsReceivedListener>();

	protected Map latestSessionSettings;

	protected long lastRecentTorrentGet;

	private int cacheBuster = new Random().nextInt();

	private SessionInfo sessionInfo;

	protected boolean supportsGZIP;

	private String[] defaultFileFields = {};

	protected boolean supportsRCM;

	private boolean supportsTorrentRename;

	public TransmissionRPC(SessionInfo sessionInfo, String rpcURL,
			String username, String ac) {
		this.sessionInfo = sessionInfo;
		if (username != null) {
			creds = new UsernamePasswordCredentials(username, ac);
		}

		this.rpcURL = rpcURL;

		updateSessionSettings(ac);
	}

	public void getSessionStats(String[] fields, ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "session-stats");
		if (fields != null) {
			Map<String, Object> mapArguments = new HashMap<String, Object>();
			map.put("arguments", mapArguments);

			mapArguments.put("fields", fields);
		}

		sendRequest("session-stats", map, l);
	}

	private void updateSessionSettings(String id) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "session-get");
		sendRequest(id, map, new ReplyMapReceivedListener() {

			@Override
			public void rpcSuccess(String id, Map map) {
				synchronized (sessionSettingsReceivedListeners) {
					latestSessionSettings = map;
					rpcVersion = MapUtils.getMapInt(map, "rpc-version", -1);
					rpcVersionAZ = MapUtils.getMapInt(map, "az-rpc-version", -1);
					if (rpcVersionAZ < 0 && map.containsKey("az-version")) {
						rpcVersionAZ = 0;
					}
					if (rpcVersionAZ >= 2) {
						hasFileCountField = true;
					}
					List listSupports = MapUtils.getMapList(map, "rpc-supports", null);
					supportsGZIP = listSupports != null
							&& listSupports.contains("rpc:receive-gzip");
					supportsRCM = listSupports != null
							&& listSupports.contains("method:rcm-set-enabled");
					supportsTorrentRename = listSupports != null
							&& listSupports.contains("field:torrent-set-name");

					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "Received Session-Get. " + map);
					}
					for (SessionSettingsReceivedListener l : sessionSettingsReceivedListeners) {
						l.sessionPropertiesUpdated(map);
					}
				}
			}

			@Override
			public void rpcFailure(String id, String message) {
			}

			@Override
			public void rpcError(String id, Exception e) {
				Activity activity = sessionInfo.getCurrentActivity();
				if (activity != null) {
					AndroidUtils.showConnectionError(activity, e, false);
				}
				SessionInfoManager.removeSessionInfo(sessionInfo.getRemoteProfile().getID());
			}
		});
	}

	public void addTorrentByUrl(String url, boolean addPaused,
			final TorrentAddedReceivedListener l) {
		addTorrent(false, url, addPaused, l);
	}

	public void addTorrentByMeta(String torrentData, boolean addPaused,
			final TorrentAddedReceivedListener l) {
		addTorrent(true, torrentData, addPaused, l);
	}

	private void addTorrent(boolean isTorrentData, String data,
			boolean addPaused, final TorrentAddedReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-add");

		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);
		mapArguments.put("paused", addPaused);
		String id;
		if (isTorrentData) {
			id = "addTorrentByMeta";
			mapArguments.put("metainfo", data);
		} else {
			id = "addTorrentByUrl";
			mapArguments.put("filename", data);
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
			TorrentListReceivedListener l) {
		getTorrents(callID, new long[] {
			torrentID
		}, fields, null, null, l);
	}

	private void getTorrents(final String callID, final Object ids,
			List<String> fields, final int[] fileIndexes, String[] fileFields,
			final TorrentListReceivedListener l) {

		Map<String, Object> map = new HashMap<String, Object>(2);
		map.put("method", "torrent-get");

		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);

		mapArguments.put("fields", fields);

		if (ids != null) {
			mapArguments.put("ids", ids);
		}

		mapArguments.put("base-url", sessionInfo.getBaseURL());

		if (rpcVersionAZ >= 3) {

			if (fields == null || fields.contains("files")) {
				if (fileFields != null) {
					mapArguments.put("file-fields", fileFields);
				} else {
					mapArguments.put("file-fields", defaultFileFields);
				}

				if (fields != null) {
					fields.remove("fileStats");
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

					Map<?, ?> mapTorrent = sessionInfo.getTorrent(torrentID);
					if (mapTorrent != null) {
						List listFiles = MapUtils.getMapList(mapTorrent, "files", null);
						if (listFiles != null) {
							List<Object> listHCs = new ArrayList<Object>();
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
						+ (fileFields == null ? "null" : fileFields.length), map,
				new ReplyMapReceivedListener() {

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
								if (map.containsKey(TransmissionVars.FIELD_TORRENT_FILE_COUNT)) {
									hasFileCountField = true;
									continue;
								}
								map.put(
										TransmissionVars.FIELD_TORRENT_FILE_COUNT,
										MapUtils.getMapList(map,
												TransmissionVars.FIELD_TORRENT_PRIORITIES,
												Collections.EMPTY_LIST).size());
							}
						}
						// TODO: If we request a list of torrent IDs, and we don't get them
						//       back on "success", then we should populate the listRemoved
						List listRemoved = MapUtils.getMapList(optionalMap, "removed", null);
						TorrentListReceivedListener[] listReceivedListeners = getTorrentListReceivedListeners();
						for (TorrentListReceivedListener torrentListReceivedListener : listReceivedListeners) {
							torrentListReceivedListener.rpcTorrentListReceived(callID, list,
									listRemoved);
						}
						if (l != null) {
							l.rpcTorrentListReceived(callID, list, null);
						}
					}

					@Override
					public void rpcFailure(String id, String message) {
						// send event to listeners on fail/error
						// some do a call for a specific torrentID and rely on a response 
						// of some sort to clean up (ie. files view progress bar), so
						// we must fake a reply with those torrentIDs

						List list = createFakeList(ids);

						TorrentListReceivedListener[] listReceivedListeners = getTorrentListReceivedListeners();
						for (TorrentListReceivedListener torrentListReceivedListener : listReceivedListeners) {
							torrentListReceivedListener.rpcTorrentListReceived(callID, list,
									null);
						}
						if (l != null) {
							l.rpcTorrentListReceived(callID, list, null);
						}

						if (AndroidUtils.DEBUG) {
							Log.d(TAG, id + "] rpcFailure.  fake listener for "
									+ listReceivedListeners.length + "/" + (l == null ? 0 : 1)
									+ ", " + list);
						}
					}

					private List createFakeList(Object ids) {
						List<Map> list = new ArrayList<Map>();
						if (ids instanceof Long) {
							HashMap<String, Object> map = new HashMap<String, Object>(2);
							map.put("id", ids);
							list.add(map);
							return list;
						}
						if (ids instanceof long[]) {
							for (long torrentID : (long[]) ids) {
								HashMap<String, Object> map = new HashMap<String, Object>(2);
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

						TorrentListReceivedListener[] listReceivedListeners = getTorrentListReceivedListeners();
						for (TorrentListReceivedListener torrentListReceivedListener : listReceivedListeners) {
							torrentListReceivedListener.rpcTorrentListReceived(callID, list,
									null);
						}
						if (l != null) {
							l.rpcTorrentListReceived(callID, list, null);
						}

						if (AndroidUtils.DEBUG) {
							Log.d(TAG, id + "] rpcError.  fake listener for "
									+ listReceivedListeners.length + "/" + (l == null ? 0 : 1)
									+ ", " + list);
						}
					}
				});
	}

	private void sendRequest(final String id, final Map data,
			final ReplyMapReceivedListener l) {
		if (id == null || data == null) {
			if (AndroidUtils.DEBUG) {
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
					Map reply = RestJsonClient.connect(id, rpcURL, data, headers, creds,
							supportsGZIP);

					String result = MapUtils.getMapString(reply, "result", "");
					if (l != null) {
						if (result.equals("success")) {
							l.rpcSuccess(id,
									MapUtils.getMapMap(reply, "arguments", Collections.EMPTY_MAP));
						} else {
							if (AndroidUtils.DEBUG) {
								Log.d(TAG, id + "] rpcFailure: " + result);
							}
							// clean up things like:
							// org.gudy.azureus2.plugins.utils.resourcedownloader.ResourceDownloaderException: http://foo.torrent: I/O Exception while downloading 'http://foo.torrent', Operation timed out
							result = result.replaceAll("org\\.[a-z.]+:", "");
							result = result.replaceAll("com\\.[a-z.]+:", "");
							l.rpcFailure(id, result);
						}
					}
				} catch (RPCException e) {
					HttpResponse httpResponse = e.getHttpResponse();
					if (httpResponse != null
							&& httpResponse.getStatusLine().getStatusCode() == 409) {
						if (AndroidUtils.DEBUG) {
							Log.d(TAG, "409: retrying");
						}
						Header header = httpResponse.getFirstHeader("X-Transmission-Session-Id");
						headers = new Header[] {
							header
						};
						sendRequest(id, data, l);
						return;
					}
					if (l != null) {
						l.rpcError(id, e);
					}
				}
			}
		}, "sendRequest" + id).start();
	}

	public synchronized List<String> getBasicTorrentFieldIDs() {
		if (basicTorrentFieldIDs == null) {

			basicTorrentFieldIDs = new ArrayList<String>();
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
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_POSITION);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO);
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_DATE_ADDED);
			basicTorrentFieldIDs.add("speedHistory");
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_LEFT_UNTIL_DONE);
			basicTorrentFieldIDs.add("tag-uids");
			basicTorrentFieldIDs.add(TransmissionVars.FIELD_TORRENT_STATUS); // TransmissionVars.TR_STATUS_*
		}

		List<String> fields = new ArrayList<String>(basicTorrentFieldIDs);
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
			final TorrentListReceivedListener l) {
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

	private List<String> getFileInfoFields() {
		List<String> fieldIDs = getBasicTorrentFieldIDs();
		fieldIDs.add("files");
		fieldIDs.add("fileStats");
		return fieldIDs;
	}

	public void getTorrentFileInfo(String callID, Object ids, int[] fileIndexes,
			TorrentListReceivedListener l) {
		getTorrents(callID, ids, getFileInfoFields(), fileIndexes,
				defaultFileFields, l);
	}

	public void getTorrentPeerInfo(String callID, Object ids,
			TorrentListReceivedListener l) {
		List<String> fieldIDs = new ArrayList<String>();
		fieldIDs.add(TransmissionVars.FIELD_TORRENT_ID);
		fieldIDs.add("peers");

		getTorrents(callID, ids, fieldIDs, null, null, l);
	}

	public void simpleRpcCall(String method, ReplyMapReceivedListener l) {
		simpleRpcCall(method, (Map) null, l);
	}

	public void simpleRpcCall(String method, long[] ids,
			ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", method);
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<String, Object>();
			map.put("arguments", mapArguments);
			mapArguments.put("ids", ids);
		}
		sendRequest(method, map, l);
	}

	public void simpleRpcCall(String method, Map arguments,
			ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", method);
		if (arguments != null) {
			map.put("arguments", arguments);
		}
		sendRequest(method, map, l);
	}

	public void simpleRpcCallWithRefresh(String callID, String method,
			long[] ids, ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", method);
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<String, Object>();
			map.put("arguments", mapArguments);
			mapArguments.put("ids", ids);
		}
		sendRequest(method, map, new ReplyMapReceivedListenerWithRefresh(callID, l,
				ids));
	}

	public void startTorrents(String callID, long[] ids, boolean forceStart,
			ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", forceStart ? "torrent-start-now" : "torrent-start");
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<String, Object>();
			map.put("arguments", mapArguments);
			mapArguments.put("ids", ids);
		}
		sendRequest("startTorrents", map, new ReplyMapReceivedListenerWithRefresh(
				callID, l, ids));
	}

	public void stopTorrents(String callID, long[] ids,
			final ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-stop");
		if (ids != null) {
			Map<String, Object> mapArguments = new HashMap<String, Object>();
			map.put("arguments", mapArguments);
			mapArguments.put("ids", ids);
		}
		sendRequest("stopTorrents", map, new ReplyMapReceivedListenerWithRefresh(
				callID, l, ids));
	}

	public void setFilePriority(String callID, long torrentID, int[] fileIndexes,
			int priority, final ReplyMapReceivedListener l) {
		long[] ids = {
			torrentID
		};
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-set");
		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);
		mapArguments.put("ids", ids);

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

		sendRequest("setFilePriority", map,
				new ReplyMapReceivedListenerWithRefresh(callID, l, ids, fileIndexes,
						null));
	}

	public void setWantState(String callID, long torrentID, int[] fileIndexes,
			boolean wanted, final ReplyMapReceivedListener l) {
		long[] torrentIDs = {
			torrentID
		};
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-set");
		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);
		mapArguments.put("ids", torrentIDs);
		mapArguments.put(wanted ? "files-wanted" : "files-unwanted", fileIndexes);

		sendRequest("setWantState", map, new ReplyMapReceivedListenerWithRefresh(
				callID, l, torrentIDs, fileIndexes, null));
	}

	public void addTag(String callID, long torrentID, String[] tags,
			final ReplyMapReceivedListener l) {
		long[] torrentIDs = {
			torrentID
		};
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-set");
		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);
		mapArguments.put("ids", torrentIDs);
		mapArguments.put("tagAdd", tags);

		sendRequest("addTag", map, new ReplyMapReceivedListenerWithRefresh(
				callID, l, torrentIDs));
	}


	public void setDisplayName(String callID, long torrentID, String newName) {
		long[] torrentIDs = {
			torrentID
		};
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-set");
		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);
		mapArguments.put("ids", torrentIDs);
		mapArguments.put("name", newName);

		sendRequest("setDisplayName", map, new ReplyMapReceivedListenerWithRefresh(
				callID, null, torrentIDs));
	}

	/**
	 * To ensure session torrent list is fully up to date, 
	 * you should be using {@link SessionInfo#addTorrentListReceivedListener(TorrentListReceivedListener)}
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

	public TorrentListReceivedListener[] getTorrentListReceivedListeners() {
		return torrentListReceivedListeners.toArray(new TorrentListReceivedListener[0]);
	}

	public void moveTorrent(long id, String newLocation,
			ReplyMapReceivedListener listener) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-set-location");

		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);

		long[] ids = new long[] {
			id
		};
		mapArguments.put("ids", ids);
		mapArguments.put("move", true);
		mapArguments.put("location", newLocation);

		sendRequest("torrent-set-location", map,
				new ReplyMapReceivedListenerWithRefresh(TAG, listener, ids));
	}

	public void removeTorrent(long[] ids, boolean deleteData,
			final ReplyMapReceivedListener listener) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "torrent-remove");

		Map<String, Object> mapArguments = new HashMap<String, Object>();
		map.put("arguments", mapArguments);

		mapArguments.put("ids", ids);
		mapArguments.put("delete-local-data", deleteData);

		sendRequest("torrent-remove", map, new ReplyMapReceivedListener() {

			@Override
			public void rpcSuccess(String id, Map<?, ?> optionalMap) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
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
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("method", "session-set");

		map.put("arguments", changes);

		sendRequest("session-set", map, null);
	}

	/**
	 * Listener's map will have a "size-bytes" key
	 */
	public void getFreeSpace(String path, ReplyMapReceivedListener l) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("path", path);

		simpleRpcCall("free-space", map, l);
	}

	public int getRPCVersion() {
		return rpcVersion;
	}

	public int getRPCVersionAZ() {
		return rpcVersionAZ;
	}

	public boolean getSupportsRCM() {
		return supportsRCM;
	}

	public boolean getSupportsTorrentRename() {
		return supportsTorrentRename;
	}

	public void setDefaultFileFields(String[] fileFields) {
		this.defaultFileFields = fileFields;
	}
}
