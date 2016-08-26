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

package com.vuze.android.remote.activity;

import java.util.List;
import java.util.Map;

import com.astuetz.PagerSlidingTabStrip;
import com.vuze.android.remote.*;
import com.vuze.android.remote.NetworkState.NetworkStateListener;
import com.vuze.android.remote.adapter.TorrentDetailsPagerAdapter;
import com.vuze.android.remote.adapter.TorrentListRowFiller;
import com.vuze.android.remote.fragment.*;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.util.MapUtils;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;

/**
 */
public class TorrentDetailsCoordActivity
	extends AppCompatActivity
	implements TorrentListReceivedListener, SessionInfoGetter,
	ActionModeBeingReplacedListener, NetworkStateListener, View.OnKeyListener
{
	static final String TAG = "TorrentDetailsCoord";

	/* @Thunk */ long torrentID;

	/* @Thunk */ SessionInfo sessionInfo;

	/* @Thunk */ TorrentListRowFiller torrentListRowFiller;

	private boolean hasActionMode;

	private ViewPager viewPager;

	private TorrentDetailsPagerAdapter pagerAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		AndroidUtilsUI.onCreate(this);
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "TorrentDetailsActivity intent = " + intent);
		}

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
			finish();
			return;
		}

		torrentID = extras.getLong("TorrentID");
		String remoteProfileID = extras.getString(SessionInfoManager.BUNDLE_KEY);
		sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID, this);

		if (sessionInfo == null) {
			Log.e(TAG, "No sessionInfo!");
			finish();
			return;
		}

		setContentView(R.layout.activity_torrent_detail_coord);

		setupActionBar();

		CollapsingToolbarLayout collapsingToolbarLayout = (CollapsingToolbarLayout) findViewById(
				R.id.collapsing_toolbar);
		final AppBarLayout appbar = (AppBarLayout) findViewById(R.id.appbar);
		if (AndroidUtilsUI.getScreenHeightDp(this) > 1000) {
			// Disable scroll-to-hide for long views
			((AppBarLayout.LayoutParams) collapsingToolbarLayout.getLayoutParams()).setScrollFlags(
					0);
		} else {
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
									RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
									if (remoteProfile != null) {
										actionBar.setSubtitle(remoteProfile.getNick());
									}
								} else {
									Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
									actionBar.setSubtitle(
											MapUtils.getMapString(torrent, "name", ""));

								}
							}
						}
					});
		}

		final View viewTorrentRow = findViewById(R.id.activity_torrent_detail_row);
		torrentListRowFiller = new TorrentListRowFiller(this, viewTorrentRow);

		viewTorrentRow.setNextFocusDownId(R.id.pager_title_strip);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			viewTorrentRow.setNextFocusForwardId(R.id.pager_title_strip);
		}

		viewTorrentRow.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Toolbar tb = (Toolbar) findViewById(R.id.toolbar_bottom);
				if (tb == null) {
					AndroidUtilsUI.popupContextMenu(TorrentDetailsCoordActivity.this,
							null);
				}
			}
		});

//		setHasOptionsMenu(true);

		viewPager = (ViewPager) findViewById(R.id.pager);
		PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(
				R.id.pager_title_strip);

		viewPager.setOnKeyListener(this);
		//view.setOnKeyListener(this);

		// adapter will bind pager, tabs and adapter together
		pagerAdapter = new TorrentDetailsPagerAdapter(getSupportFragmentManager(),
				viewPager, tabs);

		setTorrentIDs(sessionInfo.getRemoteProfile().getID(), new long[] {
			torrentID
		});

	}

	@Override
	protected void onPause() {
		VuzeRemoteApp.getNetworkState().removeListener(this);
		super.onPause();
		if (sessionInfo != null) {
			sessionInfo.activityPaused();
			sessionInfo.removeTorrentListReceivedListener(this);
		}
		pagerAdapter.onPause();
	}

	@Override
	protected void onResume() {
		VuzeRemoteApp.getNetworkState().addListener(this);
		super.onResume();
		if (sessionInfo != null) {
			sessionInfo.activityResumed(this);
			sessionInfo.addTorrentListReceivedListener(TAG, this);
		}
		pagerAdapter.onResume();
	}

	/**
	 * Fragments call VET, so it's redundant here
	 * protected void onStart() {
	 * super.onStart();
	 * VuzeEasyTracker.getInstance(this).activityStart(this);
	 * }
	 * <p/>
	 * protected void onStop() {
	 * super.onStop();
	 * VuzeEasyTracker.getInstance(this).activityStop(this);
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
				Map<?, ?> mapTorrent = sessionInfo.getTorrent(torrentID);
				torrentListRowFiller.fillHolder(mapTorrent, sessionInfo);

				AndroidUtils.invalidateOptionsMenuHC(TorrentDetailsCoordActivity.this);
			}
		});
	}

	private void setupActionBar() {
		Toolbar toolBar = (Toolbar) findViewById(R.id.actionbar);
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

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		if (remoteProfile != null) {
			actionBar.setSubtitle(remoteProfile.getNick());
		}

		// enable ActionBar app icon to behave as action to toggle nav drawer
		actionBar.setDisplayHomeAsUpEnabled(true);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
		}
		if (TorrentListFragment.handleTorrentMenuActions(sessionInfo, new long[] {
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

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_bottom);
		ActionBarToolbarSplitter.buildActionBar(this, null,
				R.menu.menu_context_torrent_details, menu, toolbar);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onPrepareOptionsMenu");
		}

		if (sessionInfo == null || torrentID < 0) {
			return super.onPrepareOptionsMenu(menu);
		}
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		int status = MapUtils.getMapInt(torrent,
				TransmissionVars.FIELD_TORRENT_STATUS,
				TransmissionVars.TR_STATUS_STOPPED);
		boolean canStart = status == TransmissionVars.TR_STATUS_STOPPED;
		boolean canStop = status != TransmissionVars.TR_STATUS_STOPPED;
		MenuItem menuStart = menu.findItem(R.id.action_sel_start);
		if (menuStart != null) {
			menuStart.setVisible(canStart);
		}

		MenuItem menuStop = menu.findItem(R.id.action_sel_stop);
		if (menuStop != null) {
			menuStop.setVisible(canStop);
		}

		AndroidUtils.fixupMenuAlpha(menu);

		ActionBarToolbarSplitter.prepareToolbar(menu,
				(Toolbar) findViewById(R.id.toolbar_bottom));

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public SessionInfo getSessionInfo() {
		return sessionInfo;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment
	 * .ActionModeBeingReplacedListener#setActionModeBeingReplaced(boolean)
	 */
	@Override
	public void setActionModeBeingReplaced(ActionMode actionMode,
			boolean actionModeBeingReplaced) {
		if (actionModeBeingReplaced) {
			hasActionMode = true;
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment
	 * .ActionModeBeingReplacedListener#actionModeBeingReplacedDone()
	 */
	@Override
	public void actionModeBeingReplacedDone() {
		hasActionMode = false;
		supportInvalidateOptionsMenu();
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment
	 * .ActionModeBeingReplacedListener#getActionMode()
	 */
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

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment
	 * .ActionModeBeingReplacedListener#rebuildActionMode()
	 */
	@Override
	public void rebuildActionMode() {
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.NetworkState
	 * .NetworkStateListener#onlineStateChanged(boolean)
	 */
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

	public void setTorrentIDs(String remoteProfileID, long[] newIDs) {
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