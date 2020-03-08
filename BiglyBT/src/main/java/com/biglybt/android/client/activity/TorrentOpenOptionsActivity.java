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

package com.biglybt.android.client.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.adapter.OpenOptionsPagerAdapter;
import com.biglybt.android.client.dialog.DialogFragmentAbstractLocationPicker.LocationPickerListener;
import com.biglybt.android.client.fragment.OpenOptionsGeneralFragment;
import com.biglybt.android.client.fragment.OpenOptionsTabFragment;
import com.biglybt.android.client.fragment.OpenOptionsTagsFragment;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.sidelist.SideActionSelectionListener;
import com.biglybt.android.client.sidelist.SideListActivity;
import com.biglybt.android.client.sidelist.SideListFragment;
import com.biglybt.android.util.JSONUtils;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Open Torrent: Options Dialog (Window)
 * <p>
 * 1) Wide and Long:  Tabs for General, Files, Tags<br>
 * 2) Just Long: General Info on top, Files & Tags in tabs<br>
 * 
 * <P>
 * Related classes: <br>
 * The Tab Holder: {@link OpenOptionsTabFragment}<br>
 * The Pager: {@link OpenOptionsPagerAdapter}<br>
 * The General Tab: {@link OpenOptionsGeneralFragment}<br>
 * The Files Tab: {@link com.biglybt.android.client.fragment.FilesFragment}<br>
 * The Tags Tab: {@link OpenOptionsTagsFragment}<br>
 */
public class TorrentOpenOptionsActivity
	extends SideListActivity
	implements LocationPickerListener
{
	private static final String TAG = "TorrentOpenOptions";

	@Thunk
	long torrentID;

	@Thunk
	boolean positionLast = true;

	@Thunk
	boolean stateQueued = true;

	// Either Long (uid) or String (name)
	@Thunk
	List<Object> selectedTags = new ArrayList<>();

	@Override
	protected void onCreateWithSession(Bundle savedInstanceState) {
		torrentID = TorrentUtils.getTorrentID(this);
		if (torrentID < 0) {
			finish();
			return;
		}

		Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
		if (torrent == null) {
			Log.e(TAG, "torrent NULL");
			finish();
			return;
		}

		RemoteProfile remoteProfile = session.getRemoteProfile();
		positionLast = remoteProfile.isAddPositionLast();
		stateQueued = remoteProfile.isAddStateQueued();

		setContentView(
				AndroidUtils.isTV(this) ? R.layout.activity_torrent_openoptions_tv
						: R.layout.activity_torrent_openoptions);
		setupActionBar();

		TextView tvHeader = findViewById(R.id.openoptions_header);
		if (tvHeader != null) {
			tvHeader.setText(
					getString(R.string.header_openoptions, MapUtils.getMapString(torrent,
							TransmissionVars.FIELD_TORRENT_NAME, "")));
		}

		Button btnAdd = findViewById(R.id.openoptions_btn_add);
		Button btnCancel = findViewById(R.id.openoptions_btn_cancel);
		CompoundButton cbSilentAdd = findViewById(R.id.openoptions_cb_silentadd);

		if (btnAdd != null) {
			btnAdd.setOnClickListener(v -> finish(true));
		}
		if (btnCancel != null) {
			btnCancel.setOnClickListener(v -> finish(false));
		}
		if (cbSilentAdd != null) {
			cbSilentAdd.setOnCheckedChangeListener((buttonView,
					isChecked) -> session.getRemoteProfile().setAddTorrentSilently(
							isChecked));
			cbSilentAdd.setChecked(session.getRemoteProfile().isAddTorrentSilently());
		}

	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (onOptionsItemSelected_drawer(item)) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Thunk
	void finish(boolean addTorrent) {
		if (addTorrent) {
			// set position and state, the rest are already set
			session.executeRpc(rpc -> {
				long[] ids = new long[] {
					torrentID
				};
				rpc.simpleRpcCall(positionLast ? TransmissionVars.METHOD_Q_MOVE_BOTTOM
						: TransmissionVars.METHOD_Q_MOVE_TOP, ids, null);
				if (selectedTags != null) {
					Object[] selectedTagObjects = selectedTags.toArray();
					rpc.addTagToTorrents("OpenOptions", ids, selectedTagObjects);
				}
				if (stateQueued) {
					rpc.startTorrents("OpenOptions", ids, false, null);
				} else {
					// should be already stopped, but stop anyway
					rpc.stopTorrents("OpenOptions", ids, null);
				}
			});
		} else {
			// remove the torrent
			session.executeRpc(rpc -> rpc.removeTorrent(new long[] {
				torrentID
			}, false, null));
		}
		if (!isFinishing()) {
			finish();
		}
	}

	private void setupActionBar() {
		Toolbar toolBar = findViewById(R.id.actionbar);
		if (toolBar != null) {
			setSupportActionBar(toolBar);
		}

		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			return;
		}
		actionBar.setDisplayHomeAsUpEnabled(true);

		RemoteProfile remoteProfile = session.getRemoteProfile();
		actionBar.setSubtitle(remoteProfile.getNick());
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		//outState.putInt("viewpagerid", viewpagerid);

		String s = JSONUtils.encodeToJSON(selectedTags);
		outState.putString("selectedTags", s);
	}

	@Override
	protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		String selectedTagsString = savedInstanceState.getString("selectedTags");
		if (selectedTagsString != null) {
			selectedTags = JSONUtils.decodeJSONList(selectedTagsString);
		}
	}

	@Override
	public void onBackPressed() {
		finish(false);
		super.onBackPressed();
	}

	public boolean isPositionLast() {
		return positionLast;
	}

	public boolean isStateQueued() {
		return stateQueued;
	}

	public void setPositionLast(boolean positionLast) {
		this.positionLast = positionLast;
		session.getRemoteProfile().setAddPositionLast(positionLast);
	}

	public void setStateQueued(boolean stateQueud) {
		this.stateQueued = stateQueud;
		session.getRemoteProfile().setAddStateQueued(stateQueud);
	}

	@Override
	public void locationChanged(String location) {
		List<Fragment> fragments = getSupportFragmentManager().getFragments();
		locationChanged(location, fragments);
	}

	private void locationChanged(String location, List<Fragment> fragments) {
		for (Fragment fragment : fragments) {
			if (fragment == null || !fragment.isAdded()) {
				continue;
			}
			if (fragment instanceof OpenOptionsGeneralFragment) {
				((OpenOptionsGeneralFragment) fragment).locationChanged(location);
			}
			FragmentManager childFragmentManager = fragment.getChildFragmentManager();
			locationChanged(location, childFragmentManager.getFragments());
		}
	}

	public void flipTagState(@Nullable Map mapTags, String word) {
		Object id = MapUtils.getMapObject(mapTags, "uid", word, Object.class);
		if (selectedTags.contains(id)) {
			selectedTags.remove(id);
		} else {
			selectedTags.add(id);
		}
	}

	/**
	 * Retrieve the list of selected tags
	 *
	 * @return The list of selected tags.  Each item is either a Long (uid) or
	 * String (name)
	 */
	public List<Object> getSelectedTags() {
		return selectedTags;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (AndroidUtilsUI.handleCommonKeyDownEvents(this, keyCode, event)) {
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public SortableRecyclerAdapter getMainAdapter() {
		SideListFragment detailsFrag = (SideListFragment) getSupportFragmentManager().findFragmentById(
				R.id.frag_openoptions_tabs);
		if (detailsFrag != null) {
			return detailsFrag.getMainAdapter();
		}
		return null;
	}

	@Override
	public SideActionSelectionListener getSideActionSelectionListener() {
		SideListFragment detailsFrag = (SideListFragment) getSupportFragmentManager().findFragmentById(
				R.id.frag_openoptions_tabs);
		if (detailsFrag != null) {
			return detailsFrag.getSideActionSelectionListener();
		}
		return null;
	}

	@Override
	public boolean showFilterEntry() {
		SideListFragment detailsFrag = (SideListFragment) getSupportFragmentManager().findFragmentById(
				R.id.frag_openoptions_tabs);
		if (detailsFrag != null) {
			return detailsFrag.showFilterEntry();
		}
		return false;
	}
}
