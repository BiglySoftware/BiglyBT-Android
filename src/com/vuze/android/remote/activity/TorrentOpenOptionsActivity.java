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

import java.util.List;
import java.util.Map;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.astuetz.PagerSlidingTabStrip;
import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.dialog.DialogFragmentMoveData.DialogFragmentMoveDataListener;
import com.vuze.android.remote.fragment.OpenOptionsFilesFragment;
import com.vuze.android.remote.fragment.OpenOptionsGeneralFragment;
import com.vuze.android.remote.fragment.OpenOptionsPagerAdapter;
import com.vuze.android.remote.rpc.TransmissionRPC;

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
 */
public class TorrentOpenOptionsActivity
	extends ActionBarActivity
	implements DialogFragmentMoveDataListener
{
	private static final String TAG = "TorrentOpenOptions";

	private SessionInfo sessionInfo;


	private long torrentID;

	private OpenOptionsPagerAdapter pagerAdapter;

	protected boolean positionLast = true;

	protected boolean stateQueued = true;

	/* (non-Javadoc)
	* @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
	*/
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
			finish();
			return;
		}

		String remoteProfileID = extras.getString(SessionInfoManager.BUNDLE_KEY);
		if (remoteProfileID != null) {
			sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID, this);
		}

		torrentID = extras.getLong("TorrentID");

		if (sessionInfo == null) {
			Log.e(TAG, "sessionInfo NULL!");
			finish();
			return;
		}
		
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			Log.e(TAG, "torrent NULL");
			finish();
			return;
		}

		

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		positionLast = remoteProfile.isAddPositionLast();
		stateQueued = remoteProfile.isAddStateQueued();
		
		setContentView(R.layout.activity_torrent_openoptions);

		setupActionBar();

		ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
		PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(R.id.pager_title_strip);
		if (viewPager != null && tabs != null) {
			pagerAdapter = new OpenOptionsPagerAdapter(
					getSupportFragmentManager(), viewPager, tabs);
		} else {
			pagerAdapter = null;
		}

		Button btnAdd = (Button) findViewById(R.id.openoptions_btn_add);
		Button btnCancel = (Button) findViewById(R.id.openoptions_btn_cancel);
		CompoundButton cbSilentAdd = (CompoundButton) findViewById(R.id.openoptions_cb_silentadd);

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
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					sessionInfo.getRemoteProfile().setAddTorrentSilently(isChecked);
				}
			});
			cbSilentAdd.setChecked(sessionInfo.getRemoteProfile().isAddTorrentSilently());
		}

	}

	protected void finish(boolean addTorrent) {
		if (addTorrent) {
			// set position and state, the rest are already set
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall(positionLast  ? "queue-move-bottom"
							: "queue-move-top", new long[] {
						torrentID
					}, null);
					if (stateQueued) {
						rpc.startTorrents("OpenOptions", new long[] {
							torrentID
						}, false, null);
					} else {
						// should be already stopped, but stop anyway
						rpc.stopTorrents("OpenOptions", new long[] {
							torrentID
						}, null);
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

	/* (non-Javadoc)
	 * @see android.support.v4.app.FragmentActivity#onPause()
	 */
	@Override
	protected void onPause() {
		if (pagerAdapter != null) {
			pagerAdapter.onPause();
		}

		super.onPause();
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.FragmentActivity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();

		if (pagerAdapter != null) {
			pagerAdapter.onResume();
		}
	}
	
	/* (non-Javadoc)
	 * @see android.support.v7.app.ActionBarActivity#onBackPressed()
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
			if (fragment.isAdded() && (fragment instanceof OpenOptionsGeneralFragment)) {
				((OpenOptionsGeneralFragment) fragment).locationChanged(location);
			}
		}
	}
}
