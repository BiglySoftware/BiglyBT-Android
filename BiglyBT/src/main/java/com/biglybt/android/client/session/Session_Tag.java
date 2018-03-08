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

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import com.biglybt.android.client.*;
import com.biglybt.android.client.rpc.*;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;

/**
 * Tag methods for a {@link Session}
 *
 * Created by TuxPaper on 12/13/16.
 */

public class Session_Tag
{

	@Thunk
	final Session session;

	private final List<TagListReceivedListener> tagListReceivedListeners = new CopyOnWriteArrayList<>();

	@Thunk
	LongSparseArray<Map<?, ?>> mapTags;

	@Thunk
	boolean needsTagRefresh = false;

	private Long tagAllUID = null;

	Session_Tag(Session session) {
		this.session = session;
	}

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

	public Long getDownloadStateUID(int stateID) {
		session.ensureNotDestroyed();
		synchronized (session.mLock) {
			if (mapTags == null) {
				return null;
			}
			for (int i = 0, num = mapTags.size(); i < num; i++) {
				Map<?, ?> mapTag = mapTags.valueAt(i);
				long tagType = MapUtils.getMapLong(mapTag,
						TransmissionVars.FIELD_TAG_TYPE, -1);
				if (tagType != 2) {
					continue;
				}
				long tagID = MapUtils.getMapLong(mapTag, TransmissionVars.FIELD_TAG_ID,
						-1);
				if (tagID == stateID) {
					return ((Number) mapTag.get(
							TransmissionVars.FIELD_TAG_UID)).longValue();
				}
			}

		}
		return null;
	}

	public Long getTagAllUID() {
		session.ensureNotDestroyed();
		return tagAllUID;
	}

	@Nullable
	public List<Map<?, ?>> getTags() {
		session.ensureNotDestroyed();

		if (mapTags == null) {
			return null;
		}

		ArrayList<Map<?, ?>> list = new ArrayList<>();

		synchronized (session.mLock) {
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
		});
		return list;
	}

	@SuppressWarnings("unchecked")
	@Thunk
	void placeTagListIntoMap(List<?> tagList) {
		// put new list of tags into mapTags.  Update the existing tag Map in case
		// some other part of the app stored a reference to it.
		synchronized (session.mLock) {
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

					int type = MapUtils.getMapInt(mapNewTag, "type", 0);
					//category
					if (type == 1) {
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
		session.transmissionRPC.simpleRpcCall("tags-get-list", args,
				new ReplyMapReceivedListener() {

					@Override
					public void rpcError(String id, Exception e) {
						needsTagRefresh = false;
					}

					@Override
					public void rpcFailure(String id, String message) {
						needsTagRefresh = false;
					}

					@Override
					public void rpcSuccess(String id, Map<?, ?> optionalMap) {
						needsTagRefresh = false;
						List<?> tagList = MapUtils.getMapList(optionalMap, "tags", null);
						if (tagList == null) {
							synchronized (session.mLock) {
								mapTags = null;
							}
							return;
						}

						placeTagListIntoMap(tagList);
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
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.removeTagFromTorrents(callID, torrentIDs, tags);
			}
		});
	}

	public void addTagToTorrents(final String callID, final long[] torrentIDs,
			final Object[] tags) {
		session._executeRpc(new Session.RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.addTagToTorrents(callID, torrentIDs, tags);
			}

		});
	}
}