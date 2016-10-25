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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.*;
import android.widget.*;

import com.vuze.android.remote.*;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.util.MapUtils;

public class TorrentTagsFragment
	extends TorrentDetailPage
{
	static final String TAG = "TorrentTagsFragment";

	private TextView tvTags;

	/* @Thunk */ SpanTags spanTags;

	/* @Thunk */ Map<Object, Boolean> mapPendingTagChanges = new HashMap<>();

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

		Button btnNew = (Button) topView.findViewById(R.id.torrent_tags_new);
		if (btnNew != null) {
			btnNew.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					AlertDialog alertDialog = AndroidUtilsUI.createTextBoxDialog(
							getContext(), R.string.create_new_tag, R.string.newtag_name,
							new AndroidUtilsUI.OnTextBoxDialogClick()
							{

								@Override
								public void onClick(DialogInterface dialog, int which,
										EditText editText) {

									final String newName = editText.getText().toString();
									sessionInfo.executeRpc(new SessionInfo.RpcExecuter()
									{

										@Override
										public void executeRpc(TransmissionRPC rpc) {
											rpc.addTagToTorrents(TAG, new long[] {
													torrentID
											}, new Object[] {
													newName
											});
										}
									});
								}
							});
					alertDialog.show();

				}
			});
		}

		return topView;
	}

	@Override
	public void pageActivated() {
		super.pageActivated();

		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
			// Issue in older APIs where ScrollView doesn't calculate scrolling
			// (scrollbars don't appear) when updateTorrentID(..) is trriggered
			// (via onResume).  pageActivated it fired later.
			// Issue is present on API 7 and 10, but not on 18
			tvTags.post(new Runnable() {
				@Override
				public void run() {
					updateTags();
				}
			});
		}
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

	/* @Thunk */ void createTags() {
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

		if (spanTags != null) {
			Collection<Map<?, ?>> tagMaps = spanTags.getTagMaps();
			if (tagMaps.size() == manualTags.size()) {
				// TODO: More robust comparison
				return;
			}
		}

		tvTags.setMovementMethod(LinkMovementMethod.getInstance());

		SpanTags.SpanTagsListener l = new SpanTags.SpanTagsListener() {
			@Override
			public void tagClicked(int index, Map mapTag, String name) {
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
			public int getTagState(int index, Map mapTag, String name) {
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

	/* @Thunk */ boolean isTagSelected(Map mapTag) {
		Map torrent = sessionInfo.getTorrent(torrentID);
		List<?> listTagUIDs = MapUtils.getMapList(torrent, "tag-uids", null);
		if (listTagUIDs == null) {
			return false;
		}

		long uid = MapUtils.getMapLong(mapTag, "uid", -1);
		return listTagUIDs.contains(uid);
	}

	/* @Thunk */ void updateTags() {
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				FragmentActivity activity = getActivity();
				if (activity == null) {
					return;
				}
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

				createTags();
				if (spanTags != null) {
					spanTags.updateTags();
				}
			}
		});
	}

}
