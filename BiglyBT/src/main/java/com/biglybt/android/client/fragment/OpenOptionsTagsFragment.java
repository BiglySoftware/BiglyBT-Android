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
import com.biglybt.android.client.activity.TorrentOpenOptionsActivity;
import com.biglybt.android.client.rpc.SuccessReplyMapRecievedListener;
import com.biglybt.android.client.spanbubbles.SpanTags;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

public class OpenOptionsTagsFragment
	extends SessionFragment
	implements FragmentPagerListener
{
	private static final String TAG = "OpenOptionsTag";

	@Thunk
	long torrentID;

	private TextView tvTags;

	private boolean tagLookupCalled;

	@Thunk
	SpanTags spanTags;

	@Thunk
	TorrentOpenOptionsActivity ourActivity;

	@Thunk
	String remoteProfileID;

	public OpenOptionsTagsFragment() {
		// Required empty public constructor
	}

	@Override
	public View onCreateViewWithSession(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onCreateview " + this);
		}

		FragmentActivity activity = requireActivity();
		torrentID = TorrentUtils.getTorrentID(activity);
		if (torrentID < 0) {
			return null;
		}

		final Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
		if (torrent == null) {
			Log.e(TAG, "No torrent!");
			// In theory TorrentOpenOptionsActivity handled this NPE already
			return null;
		}

		if (activity instanceof TorrentOpenOptionsActivity) {
			ourActivity = (TorrentOpenOptionsActivity) activity;
		}

		View topView = inflater.inflate(R.layout.frag_torrent_tags, container,
				false);

		tvTags = topView.findViewById(R.id.openoptions_tags);

		Button btnNew = topView.findViewById(R.id.torrent_tags_new);
		if (btnNew != null) {
			btnNew.setOnClickListener(v -> triggerCreateNewTag());
		}

		if (!tagLookupCalled) {
			tagLookupCalled = true;
			tagSuggestionLookup(torrent);
		}

		return topView;
	}

	private void tagSuggestionLookup(Map<?, ?> torrent) {
		session.executeRpc(rpc -> {
			Map<String, Object> map = new HashMap<>();
			map.put("ids", new Object[] {
				torrent.get(TransmissionVars.FIELD_TORRENT_HASH_STRING)
			});
			rpc.simpleRpcCall(TransmissionVars.METHOD_TAGS_LOOKUP_START, map,
					(SuccessReplyMapRecievedListener) (id,
							optionalMap) -> recievedSuggestionLookupStart(optionalMap));
		});
	}

	private void recievedSuggestionLookupStart(Map<?, ?> optionalMap) {
		if (ourActivity.isFinishing()) {
			return;
		}

		if (OpenOptionsTagsFragment.this.isRemoving()) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "isRemoving");
			}

			return;
		}

		Object tagSearchID = optionalMap.get("id");
		final Map<String, Object> mapResultsRequest = new HashMap<>();

		mapResultsRequest.put("id", tagSearchID);
		if (tagSearchID == null) {
			return;
		}
		session.executeRpc(rpc -> rpc.simpleRpcCall(
				TransmissionVars.METHOD_TAGS_LOOKUP_GET_RESULTS, mapResultsRequest,
				(SuccessReplyMapRecievedListener) (id, optionalMap1) -> {
					recievedSuggestionLookupResults(optionalMap1, mapResultsRequest);
				}));
	}

	private void recievedSuggestionLookupResults(Map<?, ?> optionalMap,
			Map<String, Object> mapResultsRequest) {
		if (ourActivity.isFinishing()) {
			return;
		}

		if (OpenOptionsTagsFragment.this.isRemoving()) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "isRemoving");
			}

			return;
		}

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "tag results: " + optionalMap);
		}
		boolean complete = MapUtils.getMapBoolean(optionalMap, "complete", true);
		if (!complete) {
			try {
				Thread.sleep(1500);
			} catch (InterruptedException ignored) {
			}

			if (ourActivity.isFinishing()) {
				return;
			}
			session.executeRpc(rpc -> rpc.simpleRpcCall(
					TransmissionVars.METHOD_TAGS_LOOKUP_GET_RESULTS, mapResultsRequest,
					(SuccessReplyMapRecievedListener) (id,
							optionalMap1) -> recievedSuggestionLookupResults(optionalMap1,
									mapResultsRequest)));
		}

		updateSuggestedTags(optionalMap);
	}

	@Thunk
	void triggerCreateNewTag() {
		AlertDialog alertDialog = AndroidUtilsUI.createTextBoxDialog(
				requireContext(), R.string.create_new_tag, R.string.newtag_name, 0,
				(dialog, which, editText) -> {
					final String newName = editText.getText().toString();
					spanTags.addTagNames(Collections.singletonList(newName));
					ourActivity.flipTagState(null, newName);
					updateTags();
					session.tag.addTagToTorrents(TAG, new long[] {
						torrentID
					}, new Object[] {
						newName
					});
				});

		alertDialog.show();
	}

	@Thunk
	void updateSuggestedTags(Map<?, ?> optionalMap) {
		List listTorrents = MapUtils.getMapList(optionalMap, "torrents", null);
		if (listTorrents == null) {
			return;
		}
		for (Object oTorrent : listTorrents) {
			if (oTorrent instanceof Map) {
				Map mapTorrent = (Map) oTorrent;
				final List tags = MapUtils.getMapList(mapTorrent, "tags", null);
				if (tags == null) {
					continue;
				}
				AndroidUtilsUI.runOnUIThread(this, false, activity -> {
					if (spanTags != null) {
						//noinspection unchecked
						spanTags.addTagNames(tags);
					}
					updateTags();
				});
				break;
			}
		}
	}

	private void createTags() {
		if (tvTags == null) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "no tvTags");
			}
			return;
		}

		List<Map<?, ?>> manualTags = session.tag.getManuallyAddableTags();

		if (manualTags == null) {
			return;
		}

		tvTags.setMovementMethod(LinkMovementMethod.getInstance());

		SpanTags.SpanTagsListener l = new SpanTags.SpanTagsListener() {
			@Override
			public void tagClicked(int index, Map mapTags, String name) {
				ourActivity.flipTagState(mapTags, name);
			}

			@Override
			public int getTagState(int index, Map mapTag, String name) {
				List<Object> selectedTags = ourActivity.getSelectedTags();
				Object id = MapUtils.getMapObject(mapTag,
						TransmissionVars.FIELD_TAG_UID, name, Object.class);

				return selectedTags.contains(id) ? SpanTags.TAG_STATE_SELECTED
						: SpanTags.TAG_STATE_UNSELECTED;
			}
		};

		spanTags = new SpanTags(tvTags, l);
		spanTags.setLineSpaceExtra(AndroidUtilsUI.dpToPx(8));
		spanTags.setTagMaps(manualTags);
		spanTags.setShowGroupNames(true);
	}

	@Thunk
	void updateTags() {
		if (spanTags == null) {
			createTags();
		}
		if (spanTags != null) {
			spanTags.updateTags();
		}
	}

	@Override
	public void pageActivated() {
		updateTags();
	}

	@Override
	public void pageDeactivated() {
	}
}
