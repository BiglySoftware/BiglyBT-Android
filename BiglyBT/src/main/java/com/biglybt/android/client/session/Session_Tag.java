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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import com.biglybt.android.client.*;
import com.biglybt.android.client.rpc.RPCSupports;
import com.biglybt.android.client.rpc.ReplyMapReceivedListener;
import com.biglybt.android.client.rpc.TagListReceivedListener;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;

/**
 * Tag methods for a {@link Session}
 *
 * Created by TuxPaper on 12/13/16.
 */

public class Session_Tag
{
	public static final int STATEID_INITIALISING = 0;

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

	private static class TagComparator
		implements Comparator<Map<?, ?>>
	{
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

			String lhGroup = MapUtils.getMapString(lhs,
					TransmissionVars.FIELD_TAG_GROUP, "");
			String rhGroup = MapUtils.getMapString(rhs,
					TransmissionVars.FIELD_TAG_GROUP, "");
			int i = lhGroup.compareToIgnoreCase(rhGroup);
			if (i != 0) {
				return i;
			}

			String lhName = MapUtils.getMapString(lhs, "name", "");
			String rhName = MapUtils.getMapString(rhs, "name", "");
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

	LongSparseArray<Map<?, ?>> mapStateIdToTag = new LongSparseArray<>();

	@Thunk
	boolean needsTagRefresh = false;

	private Long tagAllUID = null;

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
			return ((Number) mapTag.get(TransmissionVars.FIELD_TAG_UID)).longValue();
		}
	}

	public Long getTagAllUID() {
		session.ensureNotDestroyed();
		return tagAllUID;
	}

	@Nullable
	public List<Map<?, ?>> getTags() {
		return getTags(false);
	}

	@Nullable
	private List<Map<?, ?>> getTags(boolean onlyManuallyAddable) {
		session.ensureNotDestroyed();

		if (mapTags == null) {
			return null;
		}

		ArrayList<Map<?, ?>> list = new ArrayList<>();

		synchronized (session.mLock) {
			for (int i = 0, num = mapTags.size(); i < num; i++) {
				Map<?, ?> mapTag = mapTags.valueAt(i);
				if (onlyManuallyAddable) {
					int type = MapUtils.getMapInt(mapTag, "type", 0);
					if (type == 3) { // manual
						boolean hasAutoAdd = MapUtils.getMapBoolean(mapTag,
								TransmissionVars.FIELD_TAG_HASAUTOADD, false);
						boolean hasAutoRemove = MapUtils.getMapBoolean(mapTag,
								TransmissionVars.FIELD_TAG_HASAUTOREMOVE, false);
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
		}
		Collections.sort(list, new TagComparator());
		return list;
	}

	public List<Map<?, ?>> getManuallyAddableTags() {
		return getTags(true);
	}

	@SuppressWarnings("unchecked")
	@Thunk
	void placeTagListIntoMap(List<?> tagList, boolean updateStateIDs) {
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
				if (tag instanceof Map) {
					Map<?, ?> mapNewTag = (Map<?, ?>) tag;
					Long uid = MapUtils.getMapLong(mapNewTag, "uid", 0);
					Map mapOldTag = mapTags == null ? null : mapTags.get(uid);
					if (mapNewTag.containsKey("name")) {
						if (mapOldTag == null) {
							mapNewTags.put(uid, mapNewTag);
						} else {
							synchronized (mapOldTag) {
								mapOldTag.clear();
								mapOldTag.putAll(mapNewTag);
							}
							mapNewTags.put(uid, mapOldTag);
						}
					} else {
						long count = MapUtils.getMapLong(mapNewTag,
								TransmissionVars.FIELD_TAG_COUNT, -1);
						if (count >= 0 && mapOldTag != null) {
							synchronized (mapOldTag) {
								mapOldTag.put(TransmissionVars.FIELD_TAG_COUNT, count);
							}
						}
						mapNewTags.put(uid, mapOldTag);
					}

					int type = MapUtils.getMapInt(mapNewTag, "type", -1);
					if (type >= 0) {
						//category
						if (type == 1) { // TagType.TT_DOWNLOAD_CATEGORY
							// USER=0,ALL=1,UNCAT=2
							int catType = MapUtils.getMapInt(mapNewTag,
									TransmissionVars.FIELD_TAG_CATEGORY_TYPE, -1);
							if (catType == 0) {
								numUserCategories++;
							} else if (catType == 1) {
								tagAllUID = uid;
							} else if (catType == 2) {
								uidUncat = uid;
							}
						} else if (updateStateIDs && type == 2) { // TagType.TT_DOWNLOAD_STATE
							long tagID = MapUtils.getMapLong(mapNewTag,
									TransmissionVars.FIELD_TAG_ID, -1);
							mapStateIdToTag.put(tagID, mapNewTag);
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
			List<Map<?, ?>> tags = session.tag.getTags();
			for (TagListReceivedListener l : tagListReceivedListeners) {
				l.tagListReceived(tags);
			}
		}
	}

	public void refreshTags(boolean onlyRefreshCount) {
		if (!session.getSupports(RPCSupports.SUPPORTS_TAGS)) {
			return;
		}

		if (mapTags == null || mapTags.size() == 0) {
			onlyRefreshCount = false;
		}
		Map args = null;
		if (onlyRefreshCount) {
			args = new HashMap(1);
			//noinspection unchecked
			args.put("fields",
					Arrays.asList("uid", TransmissionVars.FIELD_TAG_COUNT));
		}
		boolean finalOnlyRefreshCount = onlyRefreshCount;
		session.transmissionRPC.simpleRpcCall("tags-get-list", args,
				new ReplyMapReceivedListener() {

					@Override
					public void rpcError(String requestID, Exception e) {
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

						placeTagListIntoMap(tagList, !finalOnlyRefreshCount);
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