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

import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.rpc.TagListReceivedListener;
import com.biglybt.android.client.spanbubbles.SpanTags;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.MenuBuilder;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

public class TorrentTagsFragment
	extends TorrentDetailPage
	implements TagListReceivedListener
{
	private static final String TAG = "TorrentTagsFragment";

	private TextView tvTags;

	@Thunk
	SpanTags spanTags;

	@Thunk
	Map<Object, Boolean> mapPendingTagChanges = new HashMap<>();

	private MenuBuilder actionmenuBuilder;

	private boolean showOnlyChosen;

	private CompoundButton btnFilterShowOnlyChosen;

	public TorrentTagsFragment() {
		super();
	}

	@Override
	public View onCreateViewWithSession(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.frag_torrent_tags, container, false);
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		FragmentActivity activity = requireActivity();

		tvTags = activity.findViewById(R.id.openoptions_tags);

		Button btnNew = activity.findViewById(R.id.torrent_tags_new);
		if (btnNew != null) {
			btnNew.setOnClickListener(v -> triggerCreateNewTag());
		}
	}

	private void triggerCreateNewTag() {
		AlertDialog alertDialog = AndroidUtilsUI.createTextBoxDialog(
				requireContext(), R.string.create_new_tag, R.string.newtag_name, 0,
				(dialog, which, editText) -> {

					final String newName = editText.getText().toString();
					session.tag.addTagToTorrents(TAG, new long[] {
						torrentID
					}, new Object[] {
						newName
					});
				});
		alertDialog.show();
	}

	@Override
	public void triggerRefresh() {
		if (torrentID < 0) {
			return;
		}
		session.tag.refreshTags(false);
	}

	@Override
	public void rpcTorrentListReceived(String callID, List<?> addedTorrentMaps,
			List<String> fields, final int[] fileIndexes, List<?> removedTorrentIDs) {
		super.rpcTorrentListReceived(callID, addedTorrentMaps, fields, fileIndexes,
				removedTorrentIDs);
		updateTags();
	}

	@SuppressLint("RestrictedApi")
	@Override
	protected MenuBuilder getActionMenuBuilder() {
		if (actionmenuBuilder == null) {
			Context context = getContext();
			if (context == null) {
				return null;
			}
			actionmenuBuilder = new MenuBuilder(context);

			MenuItem item = actionmenuBuilder.add(0, R.id.action_refresh, 0,
					R.string.action_refresh);
			item.setIcon(R.drawable.ic_refresh_white_24dp);

			SubMenu subMenuForFile = actionmenuBuilder.addSubMenu(0,
					R.id.menu_group_context, 0, R.string.sideactions_tag_header);
			new MenuInflater(context).inflate(R.menu.menu_torrent_tags,
					subMenuForFile);

		}

		return actionmenuBuilder;
	}

	@Override
	protected boolean handleMenu(MenuItem menuItem) {
		if (super.handleMenu(menuItem)) {
			return true;
		}
		if (menuItem.getItemId() == R.id.action_create_tag) {
			triggerCreateNewTag();
			return true;
		}
		return false;
	}

	@Override
	protected boolean prepareContextMenu(Menu menu) {
		super.prepareContextMenu(menu);
		return false;
	}

	@Thunk
	void createTags() {
		if (tvTags == null) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "no tvTags");
			}
			return;
		}

		List<Map<?, ?>> manualTags = session.tag.getManuallyAddableTags();

		if (manualTags == null) {
			tvTags.setText("");
			return;
		}

		if (showOnlyChosen) {
			for (Iterator<Map<?, ?>> iterator = manualTags.iterator(); iterator.hasNext();) {
				Map<?, ?> manualTag = iterator.next();
				if (!isTagSelected(manualTag)) {
					iterator.remove();
				}
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
					MapUtils.getMapObject(mapTag, TransmissionVars.FIELD_TAG_UID, name,
							Object.class)
				};
				final boolean isRemove = isTagSelected(mapTag);

				mapPendingTagChanges.put(tags[0], !isRemove);
				updateTags();

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
				Object uid = MapUtils.getMapObject(mapTag,
						TransmissionVars.FIELD_TAG_UID, name, Object.class);
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

		spanTags = new SpanTags(tvTags, l);
		spanTags.setLineSpaceExtra(AndroidUtilsUI.dpToPx(8));
		spanTags.setTagMaps(manualTags);
		spanTags.setShowGroupNames(true);
	}

	@Thunk
	boolean isTagSelected(Map mapTag) {
		Map<String, Object> torrent = session.torrent.getCachedTorrent(torrentID);
		List<?> listTagUIDs = MapUtils.getMapList(torrent,
				TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
		if (listTagUIDs == null) {
			return false;
		}

		long uid = MapUtils.getMapLong(mapTag, TransmissionVars.FIELD_TAG_UID, -1);
		return listTagUIDs.contains(uid);
	}

	@Thunk
	void updateTags() {
		AndroidUtilsUI.runOnUIThread(this, false, (activity) -> {
			Map<String, Object> torrent = session.torrent.getCachedTorrent(torrentID);
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
		});
	}

	@Override
	public SortableRecyclerAdapter getMainAdapter() {
		return null;
	}

	@Override
	public void pageActivated() {
		super.pageActivated();

		FragmentActivity activity = getActivity();
		if (activity == null || activity.isFinishing()) {
			return;
		}
		View filtersArea = activity.findViewById(R.id.sidefilter_tags_group);
		if (filtersArea != null) {
			filtersArea.setVisibility(View.VISIBLE);
		}

		btnFilterShowOnlyChosen = activity.findViewById(
			R.id.tags_showonly_chosen);
		if (btnFilterShowOnlyChosen != null) {
			showOnlyChosen = btnFilterShowOnlyChosen.isChecked();
			btnFilterShowOnlyChosen.setOnClickListener(v -> {
				showOnlyChosen = ((CompoundButton) v).isChecked();
				updateTags();
			});
		}

		session.tag.addTagListReceivedListener(this);
	}

	@Override
	public void pageDeactivated() {
		super.pageDeactivated();

		session.tag.removeTagListReceivedListener(this);

		FragmentActivity activity = getActivity();
		if (activity == null || activity.isFinishing()) {
			return;
		}
		View filtersArea = activity.findViewById(R.id.sidefilter_tags_group);
		if (filtersArea != null) {
			filtersArea.setVisibility(View.GONE);
		}
		// Since the filter button is not part of this fragment, we need
		// to remove our listener, otherwise this fragment doesn't get destroyed
		// until the activity does.
		if (btnFilterShowOnlyChosen != null) {
			btnFilterShowOnlyChosen.setOnClickListener(null);
		}
	}

	@Override
	public boolean showFilterEntry() {
		return true;
	}

	@Override
	public void tagListReceived(@Nullable List<Map<?, ?>> tags) {
		updateTags();
	}
}
