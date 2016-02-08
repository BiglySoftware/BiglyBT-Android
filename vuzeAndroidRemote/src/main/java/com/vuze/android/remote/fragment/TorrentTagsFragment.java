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

package com.vuze.android.remote.fragment;

import java.util.*;

import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.R;
import com.vuze.android.remote.SessionInfo;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.util.MapUtils;

public class TorrentTagsFragment
	extends TorrentDetailPage
{
	private static final String TAG = "TorrentTagsFragment";

	private TextView tvTags;

	private SpanTags spanTags;

	private Map<Object, Boolean> mapPendingTagChanges = new HashMap<>();

	public TorrentTagsFragment() {
		super();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View topView = inflater.inflate(R.layout.frag_torrent_tags, container,
				false);

		tvTags = (TextView) topView.findViewById(R.id.openoptions_tags);

		return topView;
	}

	@Override
	public void updateTorrentID(final long torrentID, boolean isTorrent,
			boolean wasTorrent, boolean torrentIdChanged) {
		updateTags();
	}

	@Override
	public void triggerRefresh() {
	}

	@Override
	public void rpcTorrentListReceived(String callID, List<?> addedTorrentMaps,
			List<?> removedTorrentIDs) {
		updateTags();
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.TorrentDetailPage#getTAG()
	 */
	@Override
	String getTAG() {
		return TAG;
	}

	private void createTags() {
		if (tvTags == null) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "no tvTags");
			}
			return;
		}

		List<Map<?, ?>> manualTags = new ArrayList<>();

		List<Map<?, ?>> allTags = sessionInfo.getTags();
		if (allTags == null) {
			tvTags.setText("");
			return;
		}

		for (Map<?, ?> mapTag : allTags) {
			int type = MapUtils.getMapInt(mapTag, "type", 0);
			if (type == 3) { // manual
				manualTags.add(mapTag);
			}
		}

		tvTags.setMovementMethod(LinkMovementMethod.getInstance());

		SpanTags.SpanTagsListener l = new SpanTags.SpanTagsListener() {
			@Override
			public void tagClicked(Map mapTag, String name) {
				final Object[] tags = new Object[] {
					MapUtils.getMapObject(mapTag, "uid", name, Object.class)
				};
				final boolean isRemove = isTagSelected(mapTag);

				mapPendingTagChanges.put(tags[0], !isRemove);
				updateTags();

				sessionInfo.executeRpc(new SessionInfo.RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						if (isRemove) {
							rpc.removeTagFromTorrents(TAG, new long[] {
								torrentID
							}, tags);

						} else {
							rpc.addTagToTorrents(TAG, new long[] {
								torrentID
							}, tags);
						}
					}
				});
			}

			@Override
			public int getTagState(Map mapTag, String name) {
				Object uid = MapUtils.getMapObject(mapTag, "uid", name, Object.class);
				Boolean pendingState = mapPendingTagChanges.get(uid);
				if (pendingState != null) {
					int state = SpanTags.TAG_STATE_UPDATING;
					if (pendingState) {
						state |= SpanTags.TAG_STATE_SELECTED;
					} else {
						state |= SpanTags.TAG_STATE_UNSELECTED;
					}
					return state;
				}
				boolean tagSelected = isTagSelected(mapTag);
				return tagSelected ? SpanTags.TAG_STATE_SELECTED
						: SpanTags.TAG_STATE_UNSELECTED;
			}
		};

		spanTags = new SpanTags(getContext(), sessionInfo, tvTags, l);
		spanTags.setTagMaps(manualTags);
	}

	private boolean isTagSelected(Map mapTag) {
		Map torrent = sessionInfo.getTorrent(torrentID);
		List<?> listTagUIDs = MapUtils.getMapList(torrent, "tag-uids", null);
		if (listTagUIDs == null) {
			return false;
		}

		long uid = MapUtils.getMapLong(mapTag, "uid", -1);
		return listTagUIDs.contains(Long.valueOf(uid));
	}

	private void updateTags() {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Map torrent = sessionInfo.getTorrent(torrentID);
				List<?> listTagUIDs = MapUtils.getMapList(torrent, "tag-uids", null);
				if (listTagUIDs != null) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "Uids " + listTagUIDs);
					}
					Iterator<Map.Entry<Object, Boolean>> it = mapPendingTagChanges.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry<Object, Boolean> next = it.next();

						Object uid = next.getKey();

						boolean hasTag = listTagUIDs.contains(uid);
						Boolean wantsTag = next.getValue();
						if (AndroidUtils.DEBUG) {
							Log.d(TAG, "Uid " + uid + " wants to be " + wantsTag
									+ "; Torrent says " + hasTag);
						}
						if (hasTag == wantsTag) {
							it.remove();
						}
					}
				}

				if (spanTags == null) {
					createTags();
				}
				if (spanTags != null) {
					spanTags.updateTags();
				}
			}
		});
	}

}
