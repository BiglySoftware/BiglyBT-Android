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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.client.R;
import com.biglybt.android.client.rpc.RPCSupports;
import com.biglybt.android.client.rpc.ReplyMapReceivedListener;
import com.biglybt.android.client.rpc.TagListReceivedListener;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.biglybt.android.client.TransmissionVars.*;

/**
 * Tag methods for a {@link Session}
 *
 * Created by TuxPaper on 12/13/16.
 */

public class Session_Tag
{
	private static final List<String> FIELDS_REFRESH = Arrays.asList(
			FIELD_TAG_UID, FIELD_TAG_COUNT);

	private static final List<String> FIELDS_FULL = Arrays.asList(FIELD_TAG_UID,
			FIELD_TAG_ID, FIELD_TAG_NAME, FIELD_TAG_TYPE, FIELD_TAG_CATEGORY_TYPE,
			FIELD_TAG_COLOR, FIELD_TAG_CANBEPUBLIC, FIELD_TAG_AUTO_ADD,
			FIELD_TAG_AUTO_REMOVE, FIELD_TAG_GROUP, FIELD_TAG_COUNT);

	@SuppressWarnings("WeakerAccess")
	public static final int STATEID_INITIALISING = 0;

	@SuppressWarnings("WeakerAccess")
	public static final int STATEID_DOWNLOADING = 1;

	public static final int STATEID_SEEDING = 2;

	public static final int STATEID_QUEUED_DOWNLOADING = 3;

	public static final int STATEID_QUEUED_SEEDING = 4;

	public static final int STATEID_STOPPED = 5;

	public static final int STATEID_ERROR = 6;

	public static final int STATEID_ACTIVE = 7;

	public static final int STATEID_PAUSED = 8;

	public static final int STATEID_INACTIVE = 9;

	public static final int STATEID_COMPLETE = 10;

	public static final int STATEID_INCOMPLETE = 11;

	/**
	 * Sort Tags by type, group, name
	 */
	@Thunk
	static class TagComparator
		implements Comparator<Map<?, ?>>
	{
		@Override
		public int compare(Map<?, ?> lhs, Map<?, ?> rhs) {
			int lType = MapUtils.getMapInt(lhs, FIELD_TAG_TYPE, 0);
			int rType = MapUtils.getMapInt(rhs, FIELD_TAG_TYPE, 0);
			if (lType < rType) {
				return -1;
			}
			if (lType > rType) {
				return 1;
			}

			String lhGroup = MapUtils.getMapString(lhs, FIELD_TAG_GROUP, "");
			String rhGroup = MapUtils.getMapString(rhs, FIELD_TAG_GROUP, "");
			int i = lhGroup.compareToIgnoreCase(rhGroup);
			if (i != 0) {
				return i;
			}

			String lhName = MapUtils.getMapString(lhs, FIELD_TAG_NAME, "");
			String rhName = MapUtils.getMapString(rhs, FIELD_TAG_NAME, "");
			return lhName.compareToIgnoreCase(rhName);
		}
	}

	@IntDef({
		STATEID_INITIALISING,
		STATEID_DOWNLOADING,
		STATEID_SEEDING,
		STATEID_QUEUED_DOWNLOADING,
		STATEID_QUEUED_SEEDING,
		STATEID_STOPPED,
		STATEID_ERROR,
		STATEID_ACTIVE,
		STATEID_PAUSED,
		STATEID_INACTIVE,
		STATEID_COMPLETE,
		STATEID_INCOMPLETE
	})
	@Retention(RetentionPolicy.SOURCE)
	public @interface StateID {
	}

	@Thunk
	final Session session;

	private final List<TagListReceivedListener> tagListReceivedListeners = new CopyOnWriteArrayList<>();

	@Thunk
	LongSparseArray<Map<?, ?>> mapTags;

	final LongSparseArray<Map<?, ?>> mapStateIdToTag = new LongSparseArray<>();

	@Thunk
	boolean needsTagRefresh = false;

	private Long tagAllUID = null;

	private boolean hasCategories;

	Session_Tag(Session session) {
		this.session = session;
	}

	/**
	 * Adds and triggers a TagListReceivedListener
	 */
	public void addTagListReceivedListener(TagListReceivedListener l) {
		session.ensureNotDestroyed();

		synchronized (tagListReceivedListeners) {
			if (!tagListReceivedListeners.contains(l)) {
				tagListReceivedListeners.add(l);
				if (mapTags != null) {
					l.tagListReceived(getTags());
				}
			}
		}
	}

	public void destroy() {
		tagListReceivedListeners.clear();
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public Map<?, ?> getTag(Long uid) {
		session.ensureNotDestroyed();

		if (uid < 10) {
			AndroidUtils.ValueStringArray basicTags = AndroidUtils.getValueStringArray(
					BiglyBTApp.getContext().getResources(), R.array.filterby_list);
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
			if (AndroidUtils.DEBUG_RPC) {
				session.logd("missing tag uid=" + uid + " via "
						+ AndroidUtils.getCompressedStackTrace());
			}
		}
		return map;
	}

	public Long getDownloadStateUID(@StateID int stateID) {
		session.ensureNotDestroyed();
		synchronized (session.mLock) {
			Map<?, ?> mapTag = mapStateIdToTag.get(stateID);
			if (mapTag == null) {
				return null;
			}
			return ((Number) mapTag.get(FIELD_TAG_UID)).longValue();
		}
	}

	@Nullable
	public Boolean hasStateTag(Map<String, Object> mapTorrent,
			@StateID int stateID) {
		Long tagUID = getDownloadStateUID(stateID);
		List<?> listTagUIDs = MapUtils.getMapList(mapTorrent,
				FIELD_TORRENT_TAG_UIDS, null);
		if (listTagUIDs != null && tagUID != null) {
			return listTagUIDs.contains(tagUID);
		}

		return null;
	}

	public boolean hasCategories() {
		return hasCategories;
	}

	public Long getTagAllUID() {
		session.ensureNotDestroyed();
		return tagAllUID;
	}

	/**
	 * Get list of Tags, sorted by {@link TagComparator}
	 */
	@NonNull
	public List<Map<?, ?>> getTags() {
		return getTags(false);
	}

	@NonNull
	private List<Map<?, ?>> getTags(boolean onlyManuallyAddable) {
		session.ensureNotDestroyed();

		if (mapTags == null) {
			return Collections.emptyList();
		}

		ArrayList<Map<?, ?>> list = new ArrayList<>();

		synchronized (session.mLock) {
			for (int i = 0, num = mapTags.size(); i < num; i++) {
				Map<?, ?> mapTag = mapTags.valueAt(i);
				if (onlyManuallyAddable) {
					int type = MapUtils.getMapInt(mapTag, FIELD_TAG_TYPE, 0);
					if (type == 3) { // manual
						boolean hasAutoAdd = MapUtils.getMapBoolean(mapTag,
								FIELD_TAG_AUTO_ADD, false);
						boolean hasAutoRemove = MapUtils.getMapBoolean(mapTag,
								FIELD_TAG_AUTO_REMOVE, false);
						if (hasAutoAdd || hasAutoRemove) {
							// Both rules means any tags we remove or add will get reverted
							// Any tag with just AutoAdd will get back the tag if user removes it
							// Any tag with just AutoRemove will lose the tag if user adds it, UNLESS the AutoRemove rule allows it
							// So, we could allow adding/removing tags to tags with only AutoRemove,
							// but waiting until we have a UI to tell the user why adding their tag add didn't apply
							continue;
						}
					} else {
						continue;
					}
				}
				list.add(mapTag);
			}

			Collections.sort(list, new TagComparator());
		}
		return list;
	}

	public List<Map<?, ?>> getManuallyAddableTags() {
		return getTags(true);
	}

	@SuppressWarnings("unchecked")
	@Thunk
	void placeTagListIntoMap(List<?> tagList, boolean updateCountOnly,
			boolean updateStateIDs) {
		List<Map<?, ?>> changedTags = new ArrayList<>();
		List<Map<?, ?>> changedTorrentsTags = new ArrayList<>();
		// put new list of tags into mapTags.  Update the existing tag Map in case
		// some other part of the app stored a reference to it.
		synchronized (session.mLock) {
			int numUserCategories = 0;
			long uidUncat = -1;
			LongSparseArray mapNewTags = new LongSparseArray(tagList.size());
			if (updateStateIDs) {
				mapStateIdToTag.clear();
			}

			for (Object tag : tagList) {
				if (!(tag instanceof Map)) {
					continue;
				}

				Map<String, Object> mapNewTag = (Map<String, Object>) tag;

				int type = MapUtils.getMapInt(mapNewTag, FIELD_TAG_TYPE, -1);
				// Only Category, Download State, and Manual Tags
				// We can't handle the others (Peer IP Set, DL Internal)
				if (type != -1 && type != 1 && type != 2 && type != 3) {
					continue;
				}

				long uid = MapUtils.getMapLong(mapNewTag, FIELD_TAG_UID, 0);
				Map mapOldTag = mapTags == null ? null : mapTags.get(uid);
				if (mapOldTag == null) {
					if (updateCountOnly) {
						continue;
					}
					mapNewTags.put(uid, mapNewTag);
					changedTags.add(mapNewTag);
					changedTorrentsTags.add(mapNewTag);
				} else {
					//noinspection SynchronizationOnLocalVariableOrMethodParameter
					synchronized (mapOldTag) {
						if (mapChanged(mapOldTag, mapNewTag)) {
							changedTags.add(mapOldTag);
							if (!updateCountOnly) {
								changedTorrentsTags.add(mapNewTag);
							}
						}
						mapOldTag.putAll(mapNewTag);
					}
					mapNewTags.put(uid, mapOldTag);
				}

				if (type >= 0) {
					//category
					if (type == 1) { // TagType.TT_DOWNLOAD_CATEGORY
						// USER=0,ALL=1,UNCAT=2
						int catType = MapUtils.getMapInt(mapNewTag, FIELD_TAG_CATEGORY_TYPE,
								-1);
						if (catType == 0) {
							numUserCategories++;
						} else if (catType == 1) {
							tagAllUID = uid;
						} else if (catType == 2) {
							uidUncat = uid;
						}
					} else if (updateStateIDs && type == 2) { // TagType.TT_DOWNLOAD_STATE
						long tagID = MapUtils.getMapLong(mapNewTag, FIELD_TAG_ID, -1);
						mapStateIdToTag.put(tagID, mapNewTag);
					}
				}
			}

			hasCategories = numUserCategories > 0;

			// Update torrent's FIELD_LAST_UPDATED when tag has changed
			// TODO: Instead of using changedTags, use a list of tags that only
			//       changed important files.  ie. 'count' change is irrelevant to
			//       torrent since it's not displayed in any torrent view.
			List<Long> changedTorrentTagUIDs = new ArrayList<>();
			for (Map<?, ?> tag : changedTorrentsTags) {
				changedTorrentTagUIDs.add(MapUtils.getMapLong(tag, FIELD_TAG_UID, -1));
			}

			if (AndroidUtils.DEBUG_RPC && changedTags.size() > 0) {
				StringBuilder sb = new StringBuilder();
				for (Map<?, ?> t : changedTags) {
					sb.append(MapUtils.getMapLong(t, FIELD_TAG_UID, -1)).append(':');
					sb.append(MapUtils.getMapString(t, FIELD_TAG_NAME, "?")).append(' ');
				}
				session.logd(changedTags.size() + " tags changed: " + sb.toString());
			}

			LongSparseArray<Map<?, ?>> listTorrents = session.torrent.getListAsSparseArray();
			for (int i = 0, count = listTorrents.size(); i < count; i++) {
				Map mapTorrent = listTorrents.valueAt(i);
				List<?> listTagUIDs = MapUtils.getMapList(mapTorrent,
						FIELD_TORRENT_TAG_UIDS, null);
				if (listTagUIDs != null && !listTagUIDs.isEmpty()) {
					if (!Collections.disjoint(listTagUIDs, changedTorrentTagUIDs)) {
						if (AndroidUtils.DEBUG_RPC) {
							List common = new ArrayList(listTagUIDs);
							common.retainAll(changedTorrentTagUIDs);
							session.logd(
									"tagListReceived: '" + mapTorrent.get(FIELD_TORRENT_NAME)
											+ "' tag change. common=" + common);
						}
						mapTorrent.put(FIELD_LAST_UPDATED, System.currentTimeMillis());
					}
				}
			}

			mapTags = mapNewTags;
		}

		if (tagListReceivedListeners.size() > 0 && changedTags.size() > 0) {
			for (TagListReceivedListener l : tagListReceivedListeners) {
				l.tagListReceived(changedTags);
			}
		}
	}

	private static boolean mapChanged(Map mapOldTag,
			Map<String, Object> mapNewTag) {
		if (mapNewTag.size() > mapOldTag.size()) {
			return true;
		}
		for (String key : mapNewTag.keySet()) {
			if (mapOldTag.containsKey(key)) {
				if (!Objects.equals(mapNewTag.get(key), mapOldTag.get(key))) {
					return true;
				}
			} else {
				return true;
			}
		}
		return false;
	}

	public void refreshTags(boolean onlyRefreshCount) {
		if (!session.getSupports(RPCSupports.SUPPORTS_TAGS)) {
			return;
		}

		if (!BiglyBTApp.isApplicationVisible()) {
			needsTagRefresh = true;
			return;
		}

		if (mapTags == null || mapTags.size() == 0) {
			onlyRefreshCount = false;
		}
		Map<String, Object> args = new HashMap<>(1);
		args.put("fields", onlyRefreshCount ? FIELDS_REFRESH : FIELDS_FULL);
		boolean finalOnlyRefreshCount = onlyRefreshCount;
		session.transmissionRPC.simpleRpcCall("tags-get-list", args,
				new ReplyMapReceivedListener() {

					@Override
					public void rpcError(String requestID, Throwable e) {
						needsTagRefresh = false;
					}

					@Override
					public void rpcFailure(String requestID, String message) {
						needsTagRefresh = false;
					}

					@Override
					public void rpcSuccess(String requestID, Map<?, ?> optionalMap) {
						needsTagRefresh = false;
						List<?> tagList = MapUtils.getMapList(optionalMap, "tags", null);
						if (tagList == null) {
							synchronized (session.mLock) {
								mapTags = null;
							}
							return;
						}

						placeTagListIntoMap(tagList, finalOnlyRefreshCount,
								!finalOnlyRefreshCount);
					}
				});
	}

	public void removeTagListReceivedListener(TagListReceivedListener l) {
		synchronized (tagListReceivedListeners) {
			tagListReceivedListeners.remove(l);
		}
	}

	public void removeTagFromTorrents(final String callID,
			final long[] torrentIDs, final Object[] tags) {
		session._executeRpc(
				rpc -> rpc.removeTagFromTorrents(callID, torrentIDs, tags));
	}

	public void addTagToTorrents(final String callID, final long[] torrentIDs,
			final Object[] tags) {
		session._executeRpc(rpc -> rpc.addTagToTorrents(callID, torrentIDs, tags));
	}
}