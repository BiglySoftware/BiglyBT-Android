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

package com.vuze.android.remote.session;

import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.http.conn.HttpHostConnectException;

import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentOpenOptionsActivity;
import com.vuze.android.remote.rpc.*;
import com.vuze.android.util.PaulBurkeFileUtils;
import com.vuze.android.widget.CustomToast;
import com.vuze.util.Base64Encode;
import com.vuze.util.MapUtils;
import com.vuze.util.Thunk;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

/**
 * Torrent methods for a {@link Session}
 *
 * Created by TuxPaper on 12/14/16.
 */

public class Session_Torrent
{
	private static final String TAG = "Session_Torrent";

	@Thunk
	final Session session;

	/** <Key, TorrentMap> */
	private final LongSparseArray<Map<?, ?>> mapOriginal;

	/**
	 * Store the last torrent id that was retrieved with file info, so when we
	 * are clearing the cache due to memory contraints, we can keep that last
	 * one.
	 */
	private long lastTorrentWithFiles = -1;

	@Thunk
	boolean needsFullTorrentRefresh = true;

	private boolean refreshingist;

	private final List<TorrentListRefreshingListener> refreshingListeners = new CopyOnWriteArrayList<>();

	private final List<TorrentListReceivedListener> receivedListeners = new CopyOnWriteArrayList<>();

	@Thunk
	long lastListReceivedOn;

	Session_Torrent(Session session) {
		this.session = session;
		this.mapOriginal = new LongSparseArray<>();
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

			Context context = session.currentActivity == null
					? VuzeRemoteApp.getContext() : session.currentActivity;
			Intent intent = new Intent(Intent.ACTION_VIEW, null, context,
					TorrentOpenOptionsActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.putExtra(SessionManager.BUNDLE_KEY,
					session.getRemoteProfile().getID());
			intent.putExtra("TorrentID", torrentID);

			try {
				context.startActivity(intent);

				session.remoteProfile.removeOpenOptionsWaiter(hashString);
				session.saveProfile();
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
	@Thunk
	void addRemoveTorrents(String callID, List<?> addedTorrentIDs,
			List<?> removedTorrentIDs) {
		session.ensureNotDestroyed();

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "adding torrents " + addedTorrentIDs.size());
			if (removedTorrentIDs != null) {
				Log.d(TAG, "Removing Torrents "
						+ Arrays.toString(removedTorrentIDs.toArray()));
			}
		}
		int numAddedOrRemoved = 0;
		synchronized (session.mLock) {
			if (addedTorrentIDs.size() > 0) {
				numAddedOrRemoved = addedTorrentIDs.size();
				boolean addTorrentSilently = session.getRemoteProfile().isAddTorrentSilently();
				List<String> listOpenOptionHashes = addTorrentSilently ? null
						: session.remoteProfile.getOpenOptionsWaiterList();

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
				session.remoteProfile.cleanupOpenOptionsWaiterList();
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
			session.tag.refreshTags(true);
		}

		for (TorrentListReceivedListener l : receivedListeners) {
			l.rpcTorrentListReceived(callID, addedTorrentIDs, removedTorrentIDs);
		}
	}

	public boolean addListReceivedListener(String callID,
			TorrentListReceivedListener l) {
		session.ensureNotDestroyed();

		return addListReceivedListener(callID, l, true);
	}

	public boolean addListReceivedListener(TorrentListReceivedListener l,
			boolean fire) {
		session.ensureNotDestroyed();

		return addListReceivedListener(TAG, l, fire);
	}

	@SuppressWarnings("WeakerAccess")
	public boolean addListReceivedListener(String callID,
			TorrentListReceivedListener l, boolean fire) {
		session.ensureNotDestroyed();

		synchronized (receivedListeners) {
			if (receivedListeners.contains(l)) {
				return false;
			}
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "addTorrentListReceivedListener " + callID + "/" + l);
			}
			receivedListeners.add(l);
			List<Map<?, ?>> torrentList = getList();
			if (torrentList.size() > 0 && fire) {
				l.rpcTorrentListReceived(callID, torrentList, null);
			}
		}
		return true;
	}

	public boolean addTorrentListRefreshingListener(
			TorrentListRefreshingListener l, boolean fire) {
		session.ensureNotDestroyed();

		synchronized (refreshingListeners) {
			if (refreshingListeners.contains(l)) {
				return false;
			}
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "addTorrentListRefreshingListener " + l);
			}
			refreshingListeners.add(l);
			List<Map<?, ?>> torrentList = getList();
			if (torrentList.size() > 0 && fire) {
				l.rpcTorrentListRefreshingChanged(refreshingist);
			}
		}
		return true;
	}

	public void clearCache() {
		session.ensureNotDestroyed();

		synchronized (session.mLock) {
			mapOriginal.clear();
			needsFullTorrentRefresh = true;
		}
	}

	public int clearFilesCaches(boolean keepLastUsedTorrentFiles) {
		session.ensureNotDestroyed();

		int num = 0;
		synchronized (session.mLock) {
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

	public void destroy() {
		refreshingListeners.clear();
		lastListReceivedOn = 0;
	}

	public long getLastListReceivedOn() {
		return lastListReceivedOn;
	}

	public Map<?, ?> getCachedTorrent(long id) {
		synchronized (session.mLock) {
			return mapOriginal.get(id);
		}
	}

	public void getFileInfo(final String callID, final Object ids,
			@Nullable final int[] fileIndexes, final TorrentListReceivedListener l) {
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.getTorrentFileInfo(callID, ids, fileIndexes, l);
			}
		});
	}

	/**
	 * Get all torrent maps.  Might be slow (walks tree)
	 */
	private List<Map<?, ?>> getList() {
		session.ensureNotDestroyed();

		ArrayList<Map<?, ?>> list = new ArrayList<>();

		synchronized (session.mLock) {
			for (int i = 0, num = mapOriginal.size(); i < num; i++) {
				list.add(mapOriginal.valueAt(i));
			}
		}
		return list;
	}

	public LongSparseArray<Map<?, ?>> getListAsSparseArray() {
		session.ensureNotDestroyed();

		synchronized (session.mLock) {
			return mapOriginal.clone();
		}
	}

	public boolean isRefreshingList() {
		return refreshingist;
	}

	public void moveDataTo(final long id, final String s) {
		session.ensureNotDestroyed();

		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.moveTorrent(id, s, null);

				VuzeEasyTracker.getInstance().sendEvent("RemoteAction", "MoveData",
						null, null);
			}
		});
	}

	public void openTorrent(final Activity activity, final String sTorrentURL,
			@Nullable final String friendlyName) {
		session.ensureNotDestroyed();
		if (sTorrentURL == null || sTorrentURL.length() == 0) {
			return;
		}
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.addTorrentByUrl(sTorrentURL, friendlyName, true,
						new TorrentAddedReceivedListener2(session, activity, true,
								sTorrentURL, friendlyName != null ? friendlyName : sTorrentURL));
			}
		});
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Context context = activity.isFinishing() ? VuzeRemoteApp.getContext()
						: activity;
				String s = context.getResources().getString(R.string.toast_adding_xxx,
						friendlyName == null ? sTorrentURL : friendlyName);
				// TODO: Cancel button on toast that removes torrent
				CustomToast.showText(s, Toast.LENGTH_SHORT);
			}
		});

		VuzeEasyTracker.getInstance(activity).sendEvent("RemoteAction",
				"AddTorrent", "AddTorrentByUrl", null);
	}

	private void openTorrent(final Activity activity, final String name,
			@Nullable InputStream is) {
		session.ensureNotDestroyed();
		if (is == null) {
			return;
		}
		try {
			int available = is.available();
			if (available <= 0) {
				available = 32 * 1024;
			}
			ByteArrayOutputStream buffer = new ByteArrayOutputStream(available);

			boolean ok = AndroidUtils.readInputStreamIfStartWith(is, buffer,
					new byte[] {
						'd'
					});
			if (!ok) {
				String s;
				if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT
						&& buffer.size() == 0) {
					s = activity.getResources().getString(
							R.string.not_torrent_file_kitkat, name);
				} else {
					byte[] bytes = buffer.toByteArray();
					String excerpt = new String(bytes, 0, Math.min(bytes.length, 5));
					s = activity.getResources().getString(R.string.not_torrent_file, name,
							excerpt);
				}
				AndroidUtilsUI.showDialog(activity, R.string.add_torrent,
						AndroidUtils.fromHTML(s));
				return;
			}
			byte[] bytes = buffer.toByteArray();
			final String metainfo = Base64Encode.encodeToString(bytes, 0,
					bytes.length);
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

	public void openTorrent(final Activity activity, final Uri uri) {
		session.ensureNotDestroyed();
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
					CustomToast.showText(R.string.content_read_failed_perms_denied,
							Toast.LENGTH_LONG);
				}
			});
		} else {
			openTorrent(activity, uri.toString(), (String) null);
		}
	}

	@Thunk
	void openTorrentWithMetaData(final Activity activity, final String name,
			final String metainfo) {
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.addTorrentByMeta(metainfo, true, new TorrentAddedReceivedListener2(
						session, activity, true, null, name));
			}
		});
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Context context = activity.isFinishing() ? VuzeRemoteApp.getContext()
						: activity;
				String s = context.getResources().getString(R.string.toast_adding_xxx,
						name);
				CustomToast.showText(s, Toast.LENGTH_SHORT);
			}
		});
		VuzeEasyTracker.getInstance(activity).sendEvent("RemoteAction",
				"AddTorrent", "AddTorrentByMeta", null);
	}

	@Thunk
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
			CustomToast.showText(AndroidUtils.fromHTML(s), Toast.LENGTH_LONG);
		} catch (FileNotFoundException e) {
			if (AndroidUtils.DEBUG) {
				e.printStackTrace();
			}
			VuzeEasyTracker.getInstance(activity).logError(e);
			String s = "<b>" + uri + "</b> not found";
			if (e.getCause() != null) {
				s += ". " + e.getCause().getMessage();
			}
			CustomToast.showText(AndroidUtils.fromHTML(s), Toast.LENGTH_LONG);
		}
	}

	public void removeTorrent(final long[] ids, final boolean deleteData,
			@Nullable final ReplyMapReceivedListener listener) {
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.removeTorrent(ids, deleteData, listener);
			}
		});
	}

	public void removeListReceivedListener(TorrentListReceivedListener l) {
		synchronized (receivedListeners) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "removeTorrentListReceivedListener " + l);
			}
			receivedListeners.remove(l);
		}
	}

	public void removeListRefreshingListener(TorrentListRefreshingListener l) {
		synchronized (refreshingListeners) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "removeTorrentListRefreshingListener " + l);
			}
			refreshingListeners.remove(l);
		}
	}

	@Thunk
	void setRefreshingList(boolean refreshingTorrentList) {
		synchronized (session.mLock) {
			this.refreshingist = refreshingTorrentList;
		}
		for (TorrentListRefreshingListener l : refreshingListeners) {
			l.rpcTorrentListRefreshingChanged(refreshingTorrentList);
		}
	}

	public void setDisplayName(final String callID, final long torrentID,
			final String newName) {
		session._executeRpc(new Session.RpcExecuter() {

			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.setDisplayName(callID, torrentID, newName);
			}
		});
	}

	public void setFileWantState(final String callID, final long torrentID,
			final int[] fileIndexes, final boolean wanted,
			@Nullable final ReplyMapReceivedListener l) {
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.setWantState(callID, torrentID, fileIndexes, wanted, l);
			}
		});
	}

	public void startAllTorrents() {
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.startTorrents(TAG, null, false, null);
			}
		});
	}

	public void startTorrents(@Nullable final long[] ids,
			final boolean forceStart) {
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.startTorrents(TAG, ids, forceStart, null);
			}
		});
	}

	public void stopAllTorrents() {
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.stopTorrents(TAG, null, null);
			}
		});
	}

	private static class TorrentAddedReceivedListener2
		implements TorrentAddedReceivedListener
	{
		private final Session session;

		@Thunk
		Activity activity;

		@Thunk
		boolean showOptions;

		private final String url;

		@NonNull
		private final String name;

		public TorrentAddedReceivedListener2(Session session, Activity activity,
				boolean showOptions, @Nullable String url, @NonNull String name) {
			this.session = session;
			this.activity = activity;
			this.showOptions = showOptions;
			this.url = url;
			this.name = name;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public void torrentAdded(final Map mapTorrentAdded, boolean duplicate) {
			if (duplicate) {
				String name = MapUtils.getMapString(mapTorrentAdded,
						TransmissionVars.FIELD_TORRENT_NAME, "Torrent");
				Context context = VuzeRemoteApp.getContext();
				String s = context.getResources().getString(
						R.string.toast_already_added, name);
				CustomToast.showText(s, Toast.LENGTH_LONG);
				return;
			}
			if (!showOptions) {
				String name = MapUtils.getMapString(mapTorrentAdded,
						TransmissionVars.FIELD_TORRENT_NAME, "Torrent");
				Context context = VuzeRemoteApp.getContext();
				String s = context.getResources().getString(R.string.toast_added, name);
				CustomToast.showText(s, Toast.LENGTH_LONG);
			} else {
				String hashString = MapUtils.getMapString(mapTorrentAdded,
						TransmissionVars.FIELD_TORRENT_HASH_STRING, "");
				if (hashString.length() > 0) {
					session.getRemoteProfile().addOpenOptionsWaiter(hashString);
					session.saveProfile();
				}
			}
			session._executeRpc(new Session.RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					long id = MapUtils.getMapLong(mapTorrentAdded,
							TransmissionVars.FIELD_TORRENT_ID, -1);
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
				if (url != null && url.startsWith(AndroidUtils.HTTP)) {
					ByteArrayOutputStream bab = new ByteArrayOutputStream(32 * 1024);

					boolean ok = AndroidUtils.readURL(url, bab, new byte[] {
						'd'
					});
					byte[] bytes = bab.toByteArray();
					if (ok) {
						final String metainfo = Base64Encode.encodeToString(bytes, 0,
								bytes.length);
						session.torrent.openTorrentWithMetaData(activity, url, metainfo);
					} else {
						Session.showUrlFailedDialog(activity, message, url,
								new String(bytes, 0, Math.min(5, bytes.length)));
					}
					return;
				}
			} catch (Throwable ignore) {
			}
			AndroidUtilsUI.showDialog(activity, R.string.add_torrent, message);
		}

		@Override
		public void torrentAddError(Exception e) {

			if (e instanceof HttpHostConnectException) {
				AndroidUtilsUI.showConnectionError(activity,
						session.getRemoteProfile().getID(), R.string.connerror_hostconnect,
						true);
			} else {
				String s = activity.getResources().getString(
						R.string.adding_torrent_error, TextUtils.htmlEncode(name),
						AndroidUtils.getCausesMesssages(e));
				AndroidUtilsUI.showConnectionError(activity, AndroidUtils.fromHTML(s),
						true);
			}
		}
	}

	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	private static void mergeList(String key, Map mapTorrent, Map old) {
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

	public void stopTorrents(@Nullable final long[] ids) {
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.stopTorrents(TAG, ids, null);
			}
		});
	}
}
