/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.vuze.android.remote.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.adapter.OpenOptionsPagerAdapter;
import com.vuze.android.remote.dialog.DialogFragmentMoveData.DialogFragmentMoveDataListener;
import com.vuze.android.remote.fragment.*;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.util.JSONUtils;
import com.vuze.util.MapUtils;
import com.vuze.util.Thunk;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

/**
 * Open Torrent: Options Dialog (Window)
 * <p>
 * Many Layouts!
 * <p>
 * 1) Wide and Long:  General Info on the left, File List on the right
 * 2) Just Long: General Info on top, File List on bottom
 * 3) Small: Tabs with General and Files
 * 
 * <P>
 * Related classes: 
 * {@link OpenOptionsPagerAdapter}
 * {@link OpenOptionsGeneralFragment}
 * {@link OpenOptionsFilesFragment}
 * {@link OpenOptionsTagsFragment}
 */
public class TorrentOpenOptionsActivity
	extends SessionActivity
	implements DialogFragmentMoveDataListener, SessionInfoGetter
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
	protected String getTag() {
		return TAG;
	}

	/* (non-Javadoc)
		* @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
		*/
	@Override
	protected void onCreateWithSession(Bundle savedInstanceState) {
		Intent intent = getIntent();

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
			finish();
			return;
		}

		torrentID = extras.getLong("TorrentID");

		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			Log.e(TAG, "torrent NULL");
			finish();
			return;
		}

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		positionLast = remoteProfile.isAddPositionLast();
		stateQueued = remoteProfile.isAddStateQueued();

		setContentView(
				AndroidUtils.isTV() ? R.layout.activity_torrent_openoptions_tv
						: R.layout.activity_torrent_openoptions);
		setupActionBar();

		TextView tvHeader = (TextView) findViewById(R.id.openoptions_header);
		if (tvHeader != null) {
			tvHeader.setText("Add Torrent " + MapUtils.getMapString(torrent,
					TransmissionVars.FIELD_TORRENT_NAME, ""));
		}

		Button btnAdd = (Button) findViewById(R.id.openoptions_btn_add);
		Button btnCancel = (Button) findViewById(R.id.openoptions_btn_cancel);
		CompoundButton cbSilentAdd = (CompoundButton) findViewById(
				R.id.openoptions_cb_silentadd);

		if (btnAdd != null) {
			btnAdd.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finish(true);
				}
			});
		}
		if (btnCancel != null) {
			btnCancel.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					finish(false);
				}
			});
		}
		if (cbSilentAdd != null) {
			cbSilentAdd.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView,
						boolean isChecked) {
					sessionInfo.getRemoteProfile().setAddTorrentSilently(isChecked);
				}
			});
			cbSilentAdd.setChecked(
					sessionInfo.getRemoteProfile().isAddTorrentSilently());
		}

	}

	@Thunk
	void finish(boolean addTorrent) {
		if (addTorrent) {
			// set position and state, the rest are already set
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					long[] ids = new long[] {
						torrentID
					};
					rpc.simpleRpcCall(
							positionLast ? "queue-move-bottom" : "queue-move-top", ids, null);
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
				}
			});
		} else {
			// remove the torrent
			sessionInfo.executeRpc(new RpcExecuter() {

				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.removeTorrent(new long[] {
						torrentID
					}, true, null);
				}

			});
		}
		if (!isFinishing()) {
			finish();
		}
	}

	private void setupActionBar() {
		Toolbar toolBar = (Toolbar) findViewById(R.id.actionbar);
		if (toolBar != null) {
			setSupportActionBar(toolBar);
		}

		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			if (AndroidUtils.DEBUG) {
				System.err.println("actionBar is null");
			}
			return;
		}
		actionBar.setDisplayHomeAsUpEnabled(false);

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		actionBar.setSubtitle(remoteProfile.getNick());
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		//outState.putInt("viewpagerid", viewpagerid);

		String s = JSONUtils.encodeToJSON(selectedTags);
		outState.putString("selectedTags", s);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		String selectedTagsString = savedInstanceState.getString("selectedTags");
		if (selectedTagsString != null) {
			selectedTags = JSONUtils.decodeJSONList(selectedTagsString);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}

	/* (non-Javadoc)
	 * @see android.support.v7.app.AppCompatActivity#onBackPressed()
	 */
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
		sessionInfo.getRemoteProfile().setAddPositionLast(positionLast);
	}

	public void setStateQueued(boolean stateQueud) {
		this.stateQueued = stateQueud;
		sessionInfo.getRemoteProfile().setAddStateQueued(stateQueud);
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentMoveData.DialogFragmentMoveDataListener#locationChanged(java.lang.String)
	 */
	@Override
	public void locationChanged(String location) {
		List<Fragment> fragments = getSupportFragmentManager().getFragments();
		for (Fragment fragment : fragments) {
			if ((fragment instanceof OpenOptionsGeneralFragment)
					&& fragment.isAdded()) {
				((OpenOptionsGeneralFragment) fragment).locationChanged(location);
			}
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
}
