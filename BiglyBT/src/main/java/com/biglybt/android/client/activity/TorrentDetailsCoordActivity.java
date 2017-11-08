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

import com.astuetz.PagerSlidingTabStrip;
import com.biglybt.android.client.*;
import com.biglybt.android.client.adapter.TorrentDetailsPagerAdapter;
import com.biglybt.android.client.adapter.TorrentListRowFiller;
import com.biglybt.android.client.fragment.*;
import com.biglybt.android.client.rpc.TorrentListReceivedListener;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.NetworkState;
import com.biglybt.util.Thunk;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;

/**
 */
public class TorrentDetailsCoordActivity
	extends SessionActivity
	implements TorrentListReceivedListener, SessionGetter,
	ActionModeBeingReplacedListener, NetworkState.NetworkStateListener,
	View.OnKeyListener
{
	private static final String TAG = "TorrentDetailsCoord";

	@Thunk
	long torrentID;

	@Thunk
	TorrentListRowFiller torrentListRowFiller;

	private boolean hasActionMode;

	private TorrentDetailsPagerAdapter pagerAdapter;

	@Override
	protected String getTag() {
		return TAG;
	}

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

		setContentView(R.layout.activity_torrent_detail_coord);

		setupActionBar();

		CollapsingToolbarLayout collapsingToolbarLayout = findViewById(
				R.id.collapsing_toolbar);
		if (collapsingToolbarLayout != null) {
			AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) collapsingToolbarLayout.getLayoutParams();
			if (AndroidUtilsUI.getScreenHeightDp(this) >= 1000) {
				params.setScrollFlags(0);
			} else {
				params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
						| AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED);

				final AppBarLayout appbar = findViewById(R.id.appbar);
				appbar.addOnOffsetChangedListener(
						new AppBarLayout.OnOffsetChangedListener() {
							boolean isInFullView = true;

							@Override
							public void onOffsetChanged(AppBarLayout appBarLayout,
									int verticalOffset) {
								boolean isNowInFullView = verticalOffset == 0;
								if (isInFullView != isNowInFullView) {
									isInFullView = isNowInFullView;
									ActionBar actionBar = getSupportActionBar();
									if (actionBar == null) {
										return;
									}

									if (isInFullView) {
										RemoteProfile remoteProfile = session.getRemoteProfile();
										actionBar.setSubtitle(remoteProfile.getNick());
									} else {
										Map<?, ?> torrent = session.torrent.getCachedTorrent(
												torrentID);
										actionBar.setSubtitle(MapUtils.getMapString(torrent,
												TransmissionVars.FIELD_TORRENT_NAME, ""));

									}
								}
							}
						});
			}
		}

		final View viewTorrentRow = findViewById(R.id.activity_torrent_detail_row);
		torrentListRowFiller = new TorrentListRowFiller(viewTorrentRow.getContext(),
				viewTorrentRow);

		viewTorrentRow.setNextFocusDownId(R.id.pager_title_strip);
		viewTorrentRow.setNextFocusForwardId(R.id.pager_title_strip);

		if (getSupportActionBar() == null) {
			viewTorrentRow.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					AndroidUtilsUI.popupContextMenu(TorrentDetailsCoordActivity.this,
							null);
				}
			});
		}

//		setHasOptionsMenu(true);

		ViewPager viewPager = findViewById(R.id.pager);
		PagerSlidingTabStrip tabs = findViewById(R.id.pager_title_strip);

		viewPager.setOnKeyListener(this);
		//view.setOnKeyListener(this);

		// adapter will bind pager, tabs and adapter together
		pagerAdapter = new TorrentDetailsPagerAdapter(getSupportFragmentManager(),
				viewPager, tabs, remoteProfileID);

		setTorrentIDs(new long[] {
			torrentID
		});

	}

	@Override
	protected void onPause() {
		BiglyBTApp.getNetworkState().removeListener(this);
		super.onPause();
		session.torrent.removeListReceivedListener(this);
		pagerAdapter.onPause();
	}

	@Override
	protected void onResume() {
		BiglyBTApp.getNetworkState().addListener(this);
		super.onResume();
		session.torrent.addListReceivedListener(TAG, this);
		pagerAdapter.onResume();
	}

	/**
	 * Fragments call VET, so it's redundant here
	 * protected void onStart() {
	 * super.onStart();
	 * AnalyticsTracker.getInstance(this).activityStart(this);
	 * }
	 * <p/>
	 * protected void onStop() {
	 * super.onStop();
	 * AnalyticsTracker.getInstance(this).activityStop(this);
	 * }
	 */

	@Override
	public void rpcTorrentListReceived(String callID, List<?> addedTorrentMaps,
			final List<?> removedTorrentIDs) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
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

				AndroidUtilsUI.invalidateOptionsMenuHC(
						TorrentDetailsCoordActivity.this);
			}
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
			Log.e(TAG, "setupActionBar: actionBar is null");
			return;
		}

//		int screenSize = getResources().getConfiguration().screenLayout
//				& Configuration.SCREENLAYOUT_SIZE_MASK;
//		if (screenSize == Configuration.SCREENLAYOUT_SIZE_SMALL) {
//			actionBar.hide();
//			return;
//		}

		RemoteProfile remoteProfile = session.getRemoteProfile();
		actionBar.setSubtitle(remoteProfile.getNick());

		// enable ActionBar app icon to behave as action to toggle nav drawer
		actionBar.setDisplayHomeAsUpEnabled(true);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
		}
		if (TorrentListFragment.handleTorrentMenuActions(remoteProfileID,
				new long[] {
					torrentID
				}, getSupportFragmentManager(), item.getItemId())) {
			return true;
		}
		return super.onOptionsItemSelected(item);
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

		super.onCreateOptionsMenu(menu);

		getMenuInflater().inflate(R.menu.menu_context_torrent_details, menu);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onPrepareOptionsMenu");
		}

		if (torrentID < 0) {
			return super.onPrepareOptionsMenu(menu);
		}
		Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
		boolean canStart = TorrentUtils.canStart(torrent);
		boolean canStop = TorrentUtils.canStop(torrent);
		MenuItem menuStart = menu.findItem(R.id.action_sel_start);
		if (menuStart != null) {
			menuStart.setVisible(canStart);
		}

		MenuItem menuStop = menu.findItem(R.id.action_sel_stop);
		if (menuStop != null) {
			menuStop.setVisible(canStop);
		}

		AndroidUtils.fixupMenuAlpha(menu);

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
		if (pagerAdapter == null) {
			return null;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof ActionModeBeingReplacedListener) {
			return ((ActionModeBeingReplacedListener) frag).getActionMode();
		}
		return null;
	}

	@Override
	public ActionMode.Callback getActionModeCallback() {
		if (pagerAdapter == null) {
			return null;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof ActionModeBeingReplacedListener) {
			return ((ActionModeBeingReplacedListener) frag).getActionModeCallback();
		}
		return null;
	}

	@Override
	public void rebuildActionMode() {
	}

	@Override
	public void onlineStateChanged(boolean isOnline, boolean isOnlineMobile) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (isFinishing()) {
					return;
				}
				supportInvalidateOptionsMenu();
			}
		});
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (AndroidUtilsUI.sendOnKeyToFragments(this, keyCode, event)) {
			return true;
		}

		return super.onKeyUp(keyCode, event);
	}

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

	private void setTorrentIDs(long[] newIDs) {
		this.torrentID = newIDs != null && newIDs.length == 1 ? newIDs[0] : -1;
		pagerAdapter.setSelection(torrentID);
		runOnUiThread(new Runnable() {
			public void run() {
				List<Fragment> fragments = getSupportFragmentManager().getFragments();
				if (fragments == null) {
					return;
				}
				for (Fragment item : fragments) {
					if (item instanceof SetTorrentIdListener) {
						((SetTorrentIdListener) item).setTorrentID(torrentID);
					}
				}
			}
		});
	}

	public void playVideo() {
		List<Fragment> fragments = getSupportFragmentManager().getFragments();
		for (Fragment frag : fragments) {
			if (frag instanceof FilesFragment) {
				((FilesFragment) frag).launchOrStreamFile();
			}
		}
	}

	@Override
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof View.OnKeyListener) {
			boolean b = ((View.OnKeyListener) frag).onKey(v, keyCode, event);
			if (b) {
				return true;
			}
		}
		return false;
	}
}