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

package com.biglybt.android.client.fragment;

import java.util.*;

import com.biglybt.android.client.*;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.spanbubbles.SpanTags;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class TorrentTagsFragment
	extends TorrentDetailPage
{
	private static final String TAG = "TorrentTagsFragment";

	private TextView tvTags;

	@Thunk
	SpanTags spanTags;

	@Thunk
	Map<Object, Boolean> mapPendingTagChanges = new HashMap<>();

	public TorrentTagsFragment() {
		super();
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View topView = inflater.inflate(R.layout.frag_torrent_tags, container,
				false);

		tvTags = topView.findViewById(R.id.openoptions_tags);

		Button btnNew = topView.findViewById(R.id.torrent_tags_new);
		if (btnNew != null) {
			btnNew.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					AlertDialog alertDialog = AndroidUtilsUI.createTextBoxDialog(
							getContext(), R.string.create_new_tag, R.string.newtag_name,
							new AndroidUtilsUI.OnTextBoxDialogClick() {

								@Override
								public void onClick(DialogInterface dialog, int which,
										EditText editText) {

									final String newName = editText.getText().toString();
									Session session = getSession();
									session.tag.addTagToTorrents(TAG, new long[] {
										torrentID
									}, new Object[] {
										newName
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

	@Override
	String getTAG() {
		return TAG;
	}

	@Thunk
	void createTags() {
		if (tvTags == null) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "no tvTags");
			}
			return;
		}

		Session session = getSession();

		List<Map<?, ?>> manualTags = new ArrayList<>();

		List<Map<?, ?>> allTags = session.tag.getTags();
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

				Session session = getSession();
				if (isRemove) {
					session.tag.removeTagFromTorrents(TAG, new long[] {
						torrentID
					}, tags);
				} else {
					session.tag.addTagToTorrents(TAG, new long[] {
						torrentID
					}, tags);
				}
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

		spanTags = new SpanTags(getContext(), session, tvTags, l);
		spanTags.setLineSpaceExtra(AndroidUtilsUI.dpToPx(8));
		spanTags.setTagMaps(manualTags);
	}

	@Thunk
	boolean isTagSelected(Map mapTag) {
		Session session = getSession();
		Map torrent = session.torrent.getCachedTorrent(torrentID);
		List<?> listTagUIDs = MapUtils.getMapList(torrent,
				TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
		if (listTagUIDs == null) {
			return false;
		}

		long uid = MapUtils.getMapLong(mapTag, "uid", -1);
		return listTagUIDs.contains(uid);
	}

	@Thunk
	void updateTags() {
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				FragmentActivity activity = getActivity();
				Session session = getSession();
				if (activity == null) {
					return;
				}
				Map torrent = session.torrent.getCachedTorrent(torrentID);
				List<?> listTagUIDs = MapUtils.getMapList(torrent,
						TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
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
