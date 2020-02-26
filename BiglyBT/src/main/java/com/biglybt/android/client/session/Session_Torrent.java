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

import java.io.*;
import java.net.ConnectException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.TorrentOpenOptionsActivity;
import com.biglybt.android.client.rpc.*;
import com.biglybt.android.util.FileUtils;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.widget.CustomToast;
import com.biglybt.util.Base64Encode;
import com.biglybt.util.Thunk;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.fragment.app.FragmentActivity;

/**
 * Torrent methods for a {@link Session}
 * <p>
 * Created by TuxPaper on 12/14/16.
 */

public class Session_Torrent
{
	private static final String TAG = "Session_Torrent";

	public static final String EXTRA_TORRENT_ID = "TorrentID";

	private static final boolean DEBUG_LISTENERS = false;

	@Thunk
	@NonNull
	final Session session;

	/**
	 * <Key, TorrentMap>
	 */
	private final LongSparseArray<Map<?, ?>> mapOriginal;

	/**
	 * Store the last torrent id that was retrieved with file info, so when we
	 * are clearing the cache due to memory constraints, we can keep that last
	 * one.
	 */
	private long lastTorrentWithFiles = -1;

	@Thunk
	boolean needsFullTorrentRefresh = true;

	private boolean refreshingList;

	private final List<TorrentListRefreshingListener> refreshingListeners = new CopyOnWriteArrayList<>();

	private final List<TorrentListReceivedListener> receivedListeners = new CopyOnWriteArrayList<>();

	@Thunk
	long lastListReceivedOn;

	Session_Torrent(@NonNull Session session) {
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

			FragmentActivity currentActivity = session.getCurrentActivity();
			Context context = currentActivity == null ? BiglyBTApp.getContext()
					: currentActivity;
			Intent intent = new Intent(Intent.ACTION_VIEW, null, context,
					TorrentOpenOptionsActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.putExtra(SessionManager.BUNDLE_KEY,
					session.getRemoteProfile().getID());
			intent.putExtra(EXTRA_TORRENT_ID, torrentID);

			try {
				context.startActivity(intent);

				session.remoteProfile.removeOpenOptionsWaiter(hashString);
				session.saveProfile();
				return;
			} catch (Throwable t) {
				// I imagine if we are trying to start an intent with
				// a dead context, we'd get some sort of exception..
				// or does it magically create the activity anyway?
				AnalyticsTracker.getInstance().logErrorNoLines(t);
			}
		}
	}

	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	@Thunk
	void addRemoveTorrents(String callID, List<?> addedTorrentIDs,
			List<String> fields, final int[] fileIndexes, List<?> removedTorrentIDs) {
		session.ensureNotDestroyed();

		if (AndroidUtils.DEBUG) {
			if (addedTorrentIDs.size() > 0) {
				Log.d(TAG, "adding torrents " + addedTorrentIDs.size());
			}
			if (removedTorrentIDs != null) {
				Log.d(TAG, "Removing Torrents "
						+ Arrays.toString(removedTorrentIDs.toArray()));
			}
		}
		int numAddedOrRemoved = 0;
		boolean requireStringUnescape = session.transmissionRPC.isRequireStringUnescape();
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

					Map<?, ?> old = mapOriginal.get(torrentID, null);
					mapOriginal.put(torrentID, mapUpdatedTorrent);

					if (mapUpdatedTorrent.containsKey(
							TransmissionVars.FIELD_TORRENT_FILES)) {
						lastTorrentWithFiles = torrentID;
					}

					/* Older Vuze clients would escape the strings */
					if (requireStringUnescape) {
						for (Object torrentKey : mapUpdatedTorrent.keySet()) {
							Object o = mapUpdatedTorrent.get(torrentKey);
							if (o instanceof String) {
								mapUpdatedTorrent.put(torrentKey,
										AndroidUtils.unescapeXML((String) o));
							}
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

					mergeFiles(mapUpdatedTorrent, old, fileIndexes);

					mapUpdatedTorrent.put(TransmissionVars.FIELD_LAST_UPDATED,
							System.currentTimeMillis());

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
			l.rpcTorrentListReceived(callID, addedTorrentIDs, fields, fileIndexes,
					removedTorrentIDs);
		}
	}

	private void mergeFiles(Map mapUpdatedTorrent, Map old,
			final int[] fileIndexes) {
		List<?> listUpdatedFiles = MapUtils.getMapList(mapUpdatedTorrent,
				TransmissionVars.FIELD_TORRENT_FILES, null);

		if (listUpdatedFiles != null) {

			// Compact mode has an array per file instead of a map. All arrays
			// are in the same order, and the keys are stored in "fileKeys"
			// This saves a lot of bandwidth when you have 10k files.
			List fileKeys = MapUtils.getMapList(mapUpdatedTorrent, "fileKeys", null);
			int numUpdatedFiles = listUpdatedFiles.size();
			if (fileKeys != null && fileKeys.size() > 0) {
				mapUpdatedTorrent.remove("fileKeys");
				String[] keys = (String[]) fileKeys.toArray(new String[0]);
				List<Map> listFilesNew = new ArrayList<>(numUpdatedFiles);
				for (int i = 0; i < numUpdatedFiles; i++) {
					List fileNoKeys = (List) listUpdatedFiles.get(i);

					if (fileNoKeys.size() != keys.length) {
						Log.e(TAG, "addRemoveTorrents: fileKeys size mismatch keys= "
								+ Arrays.toString(keys) + ", fileNoKeys=" + fileNoKeys);
						break;
					}

					Map<String, Object> mapFile = new HashMap<>();
					listFilesNew.add(mapFile);
					for (int j = 0; j < keys.length; j++) {
						mapFile.put(keys[j], fileNoKeys.get(j));
					}
				}

				if (listFilesNew.size() == listUpdatedFiles.size()) {
					listUpdatedFiles = listFilesNew;
					mapUpdatedTorrent.put(TransmissionVars.FIELD_TORRENT_FILES,
							listUpdatedFiles);
				}
//		} else {
//			Log.w(TAG, "addRemoveTorrents: NOT USING FILEKEYS " + numUpdatedFiles + "/" + callID);
			}

			// merge "fileStats" into "files"
			List<?> listFileStats = MapUtils.getMapList(mapUpdatedTorrent,
					TransmissionVars.FIELD_TORRENT_FILESTATS, null);
			if (listFileStats != null) {
				for (int i = 0; i < numUpdatedFiles; i++) {
					Map mapFile = (Map) listUpdatedFiles.get(i);
					Map mapFileStats = (Map) listFileStats.get(i);
					mapFile.putAll(mapFileStats);
				}
				mapUpdatedTorrent.remove(TransmissionVars.FIELD_TORRENT_FILESTATS);
			}

			// add an "index" key, for places that only get the file map
			// and has no reference to index
			for (int i = 0; i < numUpdatedFiles; i++) {
				Map mapFile = (Map) listUpdatedFiles.get(i);
				if (!mapFile.containsKey(TransmissionVars.FIELD_FILES_INDEX)) {
					int index = fileIndexes != null && i < fileIndexes.length
							? fileIndexes[i] : i;
					mapFile.put(TransmissionVars.FIELD_FILES_INDEX, index);
				} else {
					// assume if one has index, they all do
					break;
				}
			}

			// hack to remove .dnd_az! path
			// The proper way to do this would be to get the "dnd" directory
			// name from RPC, or have the RPC not include the "dnd" part of the
			// path.  The latter would be preferable.
			for (int i = 0; i < numUpdatedFiles; i++) {
				Map mapFile = (Map) listUpdatedFiles.get(i);
				final Object o = mapFile.get(TransmissionVars.FIELD_FILES_NAME);
				if (o instanceof String) {
					String name = (String) o;

					final int posDND = name.indexOf(".dnd_az!");
					if (posDND >= 0 && posDND + 8 < name.length()) {
						String s = name.substring(0, posDND) + name.substring(posDND + 9);
						mapFile.put(TransmissionVars.FIELD_FILES_NAME, s);
					}
				}
			}
		}

		if (old != null) {
			mergeList(TransmissionVars.FIELD_TORRENT_FILES, mapUpdatedTorrent, old);
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
				if (AndroidUtils.DEBUG) {
					Log.w(TAG, "addListReceivedListener: Already added " + l + "; "
							+ AndroidUtils.getCompressedStackTrace());
				}
				return false;
			}
			if (DEBUG_LISTENERS) {
				Log.d(TAG, "addTorrentListReceivedListener " + callID + "/" + l);
			}
			receivedListeners.add(l);
			List<Map<?, ?>> torrentList = getList();
			if (torrentList.size() > 0 && fire) {
				l.rpcTorrentListReceived(callID, torrentList, null, null, null);
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
			if (DEBUG_LISTENERS) {
				Log.d(TAG, "addTorrentListRefreshingListener " + l);
			}
			refreshingListeners.add(l);
			List<Map<?, ?>> torrentList = getList();
			if (torrentList.size() > 0 && fire) {
				l.rpcTorrentListRefreshingChanged(refreshingList);
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

	public Map<String, Object> getCachedTorrent(long id) {
		synchronized (session.mLock) {
			//noinspection unchecked
			return (Map<String, Object>) mapOriginal.get(id, null);
		}
	}

	public void clearTorrentFromCache(long id) {
		synchronized (session.mLock) {
			mapOriginal.remove(id);
		}
	}

	/**
	 * Always triggers TorrentListReceivedListener
	 */
	public void getFileInfo(final String callID, final Object ids,
			@Nullable final int[] fileIndexes, final TorrentListReceivedListener l) {
		session._executeRpc(
				rpc -> rpc.getTorrentFileInfo(callID, ids, fileIndexes, l));
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

	public int getCount() {
		session.ensureNotDestroyed();

		return mapOriginal.size();
	}

	public LongSparseArray<Map<?, ?>> getListAsSparseArray() {
		session.ensureNotDestroyed();

		synchronized (session.mLock) {
			return mapOriginal.clone();
		}
	}

	public boolean isRefreshingList() {
		return refreshingList;
	}

	public void moveDataTo(final long id, final String s) {
		session.ensureNotDestroyed();

		session._executeRpc(rpc -> {
			rpc.moveTorrent(id, s, null);

			AnalyticsTracker.getInstance().sendEvent("RemoteAction", "MoveData", null,
					null);
		});
	}

	public void openTorrent(final FragmentActivity activity,
			final String sTorrentURL, @Nullable final String friendlyName) {
		session.ensureNotDestroyed();
		if (sTorrentURL == null || sTorrentURL.length() == 0) {
			return;
		}
		session._executeRpc(rpc -> rpc.addTorrentByUrl(sTorrentURL, friendlyName,
				true, new TorrentAddedReceivedListener2(session, activity, true,
						sTorrentURL, friendlyName != null ? friendlyName : sTorrentURL)));
		activity.runOnUiThread(() -> {
			Context context = activity.isFinishing() ? BiglyBTApp.getContext()
					: activity;
			String name;
			if (friendlyName != null) {
				name = friendlyName;
			} else {
				name = FileUtils.getUriTitle(activity, Uri.parse(sTorrentURL));
				if (name == null) {
					name = sTorrentURL;
				}
			}
			String s = context.getResources().getString(R.string.toast_adding_xxx,
					name);
			// TODO: Cancel button on toast that removes torrent
			CustomToast.showText(s, Toast.LENGTH_SHORT);
		});

		AnalyticsTracker.getInstance(activity).sendEvent("RemoteAction",
				"AddTorrent", "AddTorrentByUrl", null);
	}

	private void openTorrent(final FragmentActivity activity, final String name,
			@Nullable InputStream is) {
		session.ensureNotDestroyed();
		if (is == null) {
			return;
		}

		if (AndroidUtilsUI.isUIThread()) {
			new Thread(() -> openTorrent(activity, name, is)).start();
			return;
		}

		try {
			int available = is.available();
			if (available <= 0 || available > 1024 * 1024) {
				available = 32 * 1024;
			}
			ByteArrayOutputStream buffer = new ByteArrayOutputStream(available);

			boolean ok = AndroidUtils.readInputStreamIfStartWith(is, buffer,
					new byte[] {
						'd'
					});
			if (!ok) {
				if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT
						&& buffer.size() == 0) {
					AndroidUtilsUI.showDialog(activity, R.string.add_torrent,
							R.string.not_torrent_file_kitkat, name);
				} else {
					byte[] bytes = buffer.toByteArray();
					String excerpt = new String(bytes, 0, Math.min(bytes.length, 5));
					AndroidUtilsUI.showDialog(activity, R.string.add_torrent,
							R.string.not_torrent_file, name, excerpt);
				}
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
			AnalyticsTracker.getInstance(activity).logError(e);
		} catch (OutOfMemoryError em) {
			AnalyticsTracker.getInstance(activity).logError(em);
			AndroidUtilsUI.showConnectionError(activity, "Out of Memory", true);
		}
	}

	public void openTorrent(final FragmentActivity activity, final Uri uri) {
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
			}, () -> openTorrent_perms(activity, uri),
					() -> CustomToast.showText(R.string.content_read_failed_perms_denied,
							Toast.LENGTH_LONG));
		} else {
			openTorrent(activity, uri.toString(), (String) null);
		}
	}

	@Thunk
	void openTorrentWithMetaData(final FragmentActivity activity,
			final String name, final String metainfo) {
		session._executeRpc(rpc -> rpc.addTorrentByMeta(metainfo, true,
				new TorrentAddedReceivedListener2(session, activity, true, null,
						name)));
		activity.runOnUiThread(() -> {
			Context context = activity.isFinishing() ? BiglyBTApp.getContext()
					: activity;
			String s = context.getResources().getString(R.string.toast_adding_xxx,
					name);
			CustomToast.showText(s, Toast.LENGTH_SHORT);
		});
		AnalyticsTracker.getInstance(activity).sendEvent("RemoteAction",
				"AddTorrent", "AddTorrentByMeta", null);
	}

	@Thunk
	void openTorrent_perms(FragmentActivity activity, Uri uri) {
		try {
			InputStream stream = FileUtils.getInputStream(activity, uri);
			if (stream != null) {
				openTorrent(activity, FileUtils.getUriTitle(activity, uri), stream);
			}
		} catch (SecurityException e) {
			if (AndroidUtils.DEBUG) {
				e.printStackTrace();
			}
			AnalyticsTracker.getInstance(activity).logError(e);
			String s = "Security Exception trying to access <b>" + uri + "</b>";
			CustomToast.showText(AndroidUtils.fromHTML(s), Toast.LENGTH_LONG);
		} catch (FileNotFoundException e) {
			if (AndroidUtils.DEBUG) {
				e.printStackTrace();
			}
			AnalyticsTracker.getInstance(activity).logError(e);
			String s = "<b>" + uri + "</b> not found";
			if (e.getCause() != null) {
				s += ". " + e.getCause().getMessage();
			}
			CustomToast.showText(AndroidUtils.fromHTML(s), Toast.LENGTH_LONG);
		}
	}

	public void removeTorrent(final long[] ids, final boolean deleteData,
			@Nullable final ReplyMapReceivedListener listener) {
		session._executeRpc(rpc -> rpc.removeTorrent(ids, deleteData, listener));
	}

	public void removeListReceivedListener(TorrentListReceivedListener l) {
		synchronized (receivedListeners) {
			if (DEBUG_LISTENERS) {
				Log.d(TAG, "removeTorrentListReceivedListener " + l);
			}
			receivedListeners.remove(l);
		}
	}

	public void removeListRefreshingListener(TorrentListRefreshingListener l) {
		synchronized (refreshingListeners) {
			if (DEBUG_LISTENERS) {
				Log.d(TAG, "removeTorrentListRefreshingListener " + l);
			}
			refreshingListeners.remove(l);
		}
	}

	@Thunk
	void setRefreshingList(boolean refreshingTorrentList) {
		synchronized (session.mLock) {
			this.refreshingList = refreshingTorrentList;
		}
		for (TorrentListRefreshingListener l : refreshingListeners) {
			l.rpcTorrentListRefreshingChanged(refreshingTorrentList);
		}
		session.setupNextRefresh();
	}

	public void setDisplayName(final String callID, final long torrentID,
			final String newName) {
		session._executeRpc(rpc -> rpc.setDisplayName(callID, torrentID, newName));
	}

	public void setFileWantState(final String callID, final long torrentID,
			final int[] fileIndexes, final boolean wanted,
			@Nullable final ReplyMapReceivedListener l) {
		session._executeRpc(
				rpc -> rpc.setWantState(callID, torrentID, fileIndexes, wanted, l));
	}

	public void startAllTorrents() {
		session._executeRpc(rpc -> rpc.startTorrents(TAG, null, false, null));
	}

	public void startTorrents(@Nullable final long[] ids,
			final boolean forceStart) {
		session._executeRpc(rpc -> rpc.startTorrents(TAG, ids, forceStart, null));
	}

	public void stopAllTorrents() {
		session._executeRpc(rpc -> rpc.stopTorrents(TAG, null, null));
	}

	private static class TorrentAddedReceivedListener2
		implements TorrentAddedReceivedListener
	{
		private final Session session;

		@Thunk
		FragmentActivity activity;

		@Thunk
		boolean showOptions;

		private final String url;

		@NonNull
		private final String name;

		TorrentAddedReceivedListener2(Session session, FragmentActivity activity,
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
				Context context = BiglyBTApp.getContext();
				String s = context.getResources().getString(
						R.string.toast_already_added, name);
				CustomToast.showText(s, Toast.LENGTH_LONG);
				return;
			}
			if (!showOptions) {
				String name = MapUtils.getMapString(mapTorrentAdded,
						TransmissionVars.FIELD_TORRENT_NAME, "Torrent");
				Context context = BiglyBTApp.getContext();
				String s = context.getResources().getString(R.string.toast_added, name);
				CustomToast.showText(s, Toast.LENGTH_LONG);
			} else {
				String hashString = MapUtils.getMapString(mapTorrentAdded,
						TransmissionVars.FIELD_TORRENT_HASH_STRING, "");
				if (hashString.length() > 0) {
					session.getRemoteProfile().addOpenOptionsWaiter(hashString);
					session.saveProfile();
				}
				boolean isMagnet = TorrentUtils.isMagnetTorrent(mapTorrentAdded);
				if (isMagnet) {
					session.setupNextRefresh();
					Context context = BiglyBTApp.getContext();
					String newName = MapUtils.getMapString(mapTorrentAdded,
							TransmissionVars.FIELD_TORRENT_NAME, name);
					String s = context.getResources().getString(R.string.toast_added,
							newName);
					CustomToast.showText(s, Toast.LENGTH_LONG);
				}
			}
			session._executeRpc(rpc -> {
				long id = MapUtils.getMapLong(mapTorrentAdded,
						TransmissionVars.FIELD_TORRENT_ID, -1);
				if (id >= 0) {
					List<String> fields = new ArrayList<>(rpc.getBasicTorrentFieldIDs());
					if (showOptions) {
						fields.add(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR);
						fields.add(TransmissionVars.FIELD_TORRENT_FILES);
						fields.add(TransmissionVars.FIELD_TORRENT_FILESTATS);
					}
					rpc.getTorrent(TAG, id, fields, null);
				} else {
					rpc.getRecentTorrents(TAG, null);
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
			AndroidUtilsUI.showDialog(activity, R.string.add_torrent,
					R.string.hardcoded_string, message);
		}

		@Override
		public void torrentAddError(Throwable e) {

			if (e instanceof ConnectException) {
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

	/**
	 * <p>Map objects for mapTorrent[key][0..n] are retained, but updated with
	 * new values.</p>
	 *
	 */
	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	private static void mergeList(String key, Map mapTorrent, Map mapTorrentOld) {
		// listUpdatedFiles: mapTorrent[key]. values are Map
		// listNewFiles: copy of mapTorrentOld[key].  values are Map
		// walk through listUpdatedFiles, putting all map values into corresponding listNewFiles map,
		// thus maintaining Map object accross merges
		List listOldFiles = MapUtils.getMapList(mapTorrentOld, key, null);
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
					synchronized (mapUpdatedFile) {
						for (Object fileKey : mapUpdatedFile.keySet()) {
							mapNewFile.put(fileKey, mapUpdatedFile.get(fileKey));
						}
					}
				}
				mapTorrent.put(key, listNewFiles);
			}
		}
	}

	public void stopTorrents(@Nullable final long[] ids) {
		session._executeRpc(rpc -> rpc.stopTorrents(TAG, ids, null));
	}

	public void verifyTorrents(@Nullable final long[] ids) {
		session._executeRpc(rpc -> rpc.verifyTorrents(TAG, ids, null));
	}
}
