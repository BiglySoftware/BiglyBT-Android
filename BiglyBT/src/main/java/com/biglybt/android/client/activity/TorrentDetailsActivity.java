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

import java.util.List;
import java.util.Map;

import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.adapter.TorrentListRowFiller;
import com.biglybt.android.client.fragment.ActionModeBeingReplacedListener;
import com.biglybt.android.client.fragment.TorrentDetailsFragment;
import com.biglybt.android.client.fragment.TorrentListFragment;
import com.biglybt.android.client.rpc.TorrentListReceivedListener;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.sidelist.SideActionSelectionListener;
import com.biglybt.android.client.sidelist.SideListActivity;
import com.biglybt.android.client.sidelist.SideListFragment;
import com.biglybt.android.util.NetworkState.NetworkStateListener;
import com.biglybt.util.Thunk;

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.*;

/**
 * Activity to hold {@link TorrentDetailsFragment}.  Used for narrow screens.
 * <p/>
 * Typically, we show the torrent row from {@link TorrentListFragment} and
 * a {@link TorrentDetailsFragment}, which is a tabbed Pager widget with
 * various groupings of information
 */
public class TorrentDetailsActivity
	extends SideListActivity
	implements TorrentListReceivedListener, ActionModeBeingReplacedListener,
	NetworkStateListener
{
	private static final String TAG = "TorrentDetailsView";

	@Thunk
	long torrentID;

	@Thunk
	TorrentListRowFiller torrentListRowFiller;

	private boolean hasActionMode;

	@Override
	protected void onCreateWithSession(Bundle savedInstanceState) {
		torrentID = TorrentUtils.getTorrentID(this);
		if (torrentID < 0) {
			finish();
			return;
		}

		setContentView(AndroidUtils.isTV(this) ? R.layout.activity_torrent_detail_tv
				: R.layout.activity_torrent_detail_coord);

		setupActionBar();

		final View viewTorrentRow = findViewById(R.id.activity_torrent_detail_row);
		torrentListRowFiller = new TorrentListRowFiller(this, viewTorrentRow);

		viewTorrentRow.setFocusable(false);

		TorrentDetailsFragment detailsFrag = (TorrentDetailsFragment) getSupportFragmentManager().findFragmentById(
				R.id.frag_torrent_details);

		if (detailsFrag != null) {
			detailsFrag.setTorrentIDs(new long[] {
				torrentID
			});
		}

	}

	@Override
	protected void onHideActivity() {
		super.onHideActivity();
		BiglyBTApp.getNetworkState().removeListener(this);
		session.torrent.removeListReceivedListener(this);
	}

	@Override
	protected void onShowActivity() {
		super.onShowActivity();
		BiglyBTApp.getNetworkState().addListener(this);
		session.torrent.addListReceivedListener(TAG, this);
	}

	@Override
	public void rpcTorrentListReceived(String callID, List<?> addedTorrentMaps,
			List<String> fields, final int[] fileIndexes,
			final List<?> removedTorrentIDs) {
		runOnUiThread(() -> {
			if (isFinishing()) {
				return;
			}

			if (removedTorrentIDs != null) {
				boolean found = false;
				for (Object removedItem : removedTorrentIDs) {
					if (removedItem instanceof Number) {
						found = torrentID == ((Number) removedItem).longValue();
						if (found) {
							break;
						}
					}
				}
				if (found) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "Closing Details View- torrent rmeoved");
					}
					finish();
					return;
				}
			}
			Map<?, ?> mapTorrent = session.torrent.getCachedTorrent(torrentID);
			torrentListRowFiller.fillHolder(mapTorrent, session);

			AndroidUtilsUI.invalidateOptionsMenuHC(TorrentDetailsActivity.this);
		});
	}

	private void setupActionBar() {
		Toolbar toolBar = findViewById(R.id.actionbar);
		if (toolBar != null) {
			setSupportActionBar(toolBar);
		}

		// enable ActionBar app icon to behave as action to toggle nav drawer
		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			return;
		}

		RemoteProfile remoteProfile = session.getRemoteProfile();
		actionBar.setSubtitle(remoteProfile.getNick());

		actionBar.setDisplayHomeAsUpEnabled(true);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (onOptionsItemSelected_drawer(item)) {
			return true;
		}
		if (super.onOptionsItemSelected(item)) {
			return true;
		}
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
		}
		if (TorrentListFragment.handleTorrentMenuActions(session, new long[] {
			torrentID
		}, getSupportFragmentManager(), item)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (onOptionsItemSelected(item)) {
			return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onCreateOptionsMenu; hasActionMode=" + hasActionMode);
		}

		if (hasActionMode) {
			return false;
		}

		// Add menus from fragment first
		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.menu_context_torrent_details, menu);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onPrepareOptionsMenu");
		}

		if (torrentID >= 0) {
			TorrentListFragment.prepareTorrentMenuItems(menu, new Map[] {
				session.torrent.getCachedTorrent(torrentID)
			}, session);
			AndroidUtils.fixupMenuAlpha(menu);
		}

		// FragmentActivity will call onPrepareOptionsMenu for fragments
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void setActionModeBeingReplaced(ActionMode actionMode,
			boolean actionModeBeingReplaced) {
		if (actionModeBeingReplaced) {
			hasActionMode = true;
		}
	}

	@Override
	public void actionModeBeingReplacedDone() {
		hasActionMode = false;
		supportInvalidateOptionsMenu();
	}

	@Override
	public ActionMode getActionMode() {
		return null;
	}

	@Override
	public void rebuildActionMode() {
	}

	@Override
	public void onlineStateChanged(boolean isOnline, boolean isOnlineMobile) {
		runOnUiThread(() -> {
			if (isFinishing()) {
				return;
			}
			supportInvalidateOptionsMenu();
		});
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (AndroidUtilsUI.sendOnKeyToFragments(this, keyCode, event)) {
			return true;
		}

		return super.onKeyUp(keyCode, event);
	}

	@SuppressLint("LogConditional")
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		if (AndroidUtilsUI.sendOnKeyToFragments(this, keyCode, event)) {
			return true;
		}
		if (AndroidUtilsUI.handleCommonKeyDownEvents(this, keyCode, event)) {
			return true;
		}

		switch (keyCode) {
			case KeyEvent.KEYCODE_PROG_GREEN: {
				Log.d(TAG, "CurrentFocus is " + getCurrentFocus());
				break;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public SortableRecyclerAdapter getMainAdapter() {
		SideListFragment detailsFrag = (SideListFragment) getSupportFragmentManager().findFragmentById(
				R.id.frag_torrent_details);
		if (detailsFrag != null) {
			return detailsFrag.getMainAdapter();
		}
		return null;
	}

	@Override
	public SideActionSelectionListener getSideActionSelectionListener() {
		SideListFragment detailsFrag = (SideListFragment) getSupportFragmentManager().findFragmentById(
				R.id.frag_torrent_details);
		if (detailsFrag != null) {
			return detailsFrag.getSideActionSelectionListener();
		}
		return null;
	}

	@Override
	public boolean showFilterEntry() {
		SideListFragment detailsFrag = (SideListFragment) getSupportFragmentManager().findFragmentById(
				R.id.frag_torrent_details);
		if (detailsFrag != null) {
			return detailsFrag.showFilterEntry();
		}
		return false;
	}
}