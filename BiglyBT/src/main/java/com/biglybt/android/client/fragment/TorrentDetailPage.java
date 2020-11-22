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

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.*;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.app.ProgressBarManager;

import com.biglybt.android.client.*;
import com.biglybt.android.client.adapter.PagerAdapter2UsingClasses;
import com.biglybt.android.client.rpc.TorrentListReceivedListener;
import com.biglybt.android.client.session.RefreshTriggerListener;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.sidelist.*;
import com.biglybt.util.Thunk;

import java.util.List;
import java.util.Map;

/**
 * A Fragment that belongs to a page in {@link TorrentDetailsFragment}
 */
public abstract class TorrentDetailPage
	extends SideListFragment
	implements SetTorrentIdListener, RefreshTriggerListener,
	TorrentListReceivedListener, FragmentPagerListener
{

	private static final String TAG = "TorrentDetailPage";

	@Thunk
	class TDPSideActionSelectionListener
		implements SideActionSelectionListener
	{

		private MenuBuilder menuBuilder;

		@Override
		public Session getSession() {
			return TorrentDetailPage.this.getSession();
		}

		@Override
		public boolean isRefreshing() {
			return refreshing;
		}

		@Override
		public void prepareActionMenus(Menu menu) {
			prepareContextMenu(menu);
		}

		@Override
		@SuppressLint("RestrictedApi")
		public MenuBuilder getMenuBuilder() {
			if (menuBuilder == null) {
				menuBuilder = TorrentDetailPage.this.getActionMenuBuilder();
				if (menuBuilder == null) {
					menuBuilder = new MenuBuilder(requireContext());
				}
				SubMenu subMenuForTorrent = menuBuilder.addSubMenu(0,
						R.id.menu_group_torrent, 0, R.string.sideactions_torrent_header);
				new MenuInflater(menuBuilder.getContext()).inflate(
						R.menu.menu_context_torrent_details, subMenuForTorrent);
			}
			return menuBuilder;
		}

		@Override
		public int[] getRestrictToMenuIDs() {
			return null;
		}

		@Override
		public void onItemClick(SideActionsAdapter adapter, int position) {
			SideActionsAdapter.SideActionsInfo item = adapter.getItem(position);
			if (item == null) {
				return;
			}
			int itemId = item.menuItem.getItemId();
			if (itemId == R.id.action_refresh) {
				triggerRefresh();
				return;
			}

			FragmentActivity activity = getActivity();
			if (activity != null && !activity.isFinishing()) {
				if (activity.onOptionsItemSelected(item.menuItem)) {
					return;
				}
			}

			if (handleMenu(item.menuItem)) {
				return;
			}
		}

		@Override
		public boolean onItemLongClick(SideActionsAdapter adapter, int position) {
			return false;
		}

		@Override
		public void onItemSelected(SideActionsAdapter adapter, int position,
				boolean isChecked) {

		}

		@Override
		public void onItemCheckedChanged(SideActionsAdapter adapter,
				SideActionsAdapter.SideActionsInfo item, boolean isChecked) {

		}
	}

	@Thunk
	boolean refreshing = false;

	private TDPSideActionSelectionListener sideActionSelectionListener = new TDPSideActionSelectionListener();

	private ProgressBarManager progressBarManager;

	private final Object mLock = new Object();

	private int numProgresses = 0;

	protected abstract MenuBuilder getActionMenuBuilder();

	@UiThread
	protected boolean handleMenu(MenuItem menuItem) {
		return TorrentListFragment.handleTorrentMenuActions(session, new long[] {
			torrentID
		}, getFragmentManager(), menuItem);
	}

	protected boolean prepareContextMenu(Menu menu) {
		TorrentListFragment.prepareTorrentMenuItems(menu, new Map[] {
			session.torrent.getCachedTorrent(torrentID)
		}, session);
		return true;
	}

	protected long torrentID = -1;

	private boolean viewActive = false;

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		FragmentActivity activity = requireActivity();

		// PB belongs in TorrentDetailPage or TorrentDetailsFragment
		ProgressBar progressBar = activity.findViewById(R.id.details_progress_bar);
		if (progressBar == null) {
			progressBar = activity.findViewById(R.id.sideaction_spinner);
		}
		if (progressBar != null) {
			progressBarManager = new ProgressBarManager();
			progressBarManager.setProgressBarView(progressBar);

			if (numProgresses > 0) {
				progressBarManager.show();
			}
		}
	}

	@Override
	public final void onHideFragment() {
		super.onHideFragment();
		// pageDeactivated will be called by PagerAdapter on pause.
		// Explicitly call when not in a PagerAdapter
		if (!PagerAdapter2UsingClasses.isFragInPageAdapter(this)) {
			// Delay the post to ensure any child class logic is ran first
			AndroidUtilsUI.postDelayed(this::pageDeactivated);
		}
	}

	@Override
	public final void onShowFragment() {
		super.onShowFragment();
		// pageActivated will be called by PagerAdapter on resume.
		// Explicitly call when not in a PagerAdapter.
		if (!PagerAdapter2UsingClasses.isFragInPageAdapter(this)) {
			// Delay the post to ensure any child class logic is ran first
			AndroidUtilsUI.postDelayed(this::pageActivated);
		}
	}

	/**
	 * Page has been deactivated, or if not in a PageAdapter, Fragment has been paused
	 * <p/>
	 * Removes {@link RefreshTriggerListener}, {@link TorrentListReceivedListener}
	 */
	@Override
	public void pageDeactivated() {
		if (!viewActive) {
			if (AndroidUtils.DEBUG_LIFECYCLE) {
				log(TAG, "pageDeactivated ALREADY ACTIVE"
						+ AndroidUtils.getCompressedStackTrace());
			}
			return;
		}

		if (AndroidUtils.DEBUG_LIFECYCLE) {
			log(TAG, "pageDeactivated " + torrentID + " "
					+ AndroidUtils.getCompressedStackTrace());
		}
		viewActive = false;
		refreshing = false;
		numProgresses = 0;

		session.removeRefreshTriggerListener(this);
		session.torrent.removeListReceivedListener(this);

		{ // if (hasOptionsMenu()) {
			AndroidUtilsUI.invalidateOptionsMenuHC(getActivity());
		}

		SideListActivity sideListActivity = getSideListActivity();
		if (sideListActivity != null) {
			sideListActivity.setControllingFragment(null);
		}

		AnalyticsTracker.getInstance(this).fragmentPause(this);
	}

	@Thunk
	void showProgressBar() {
		synchronized (mLock) {
			numProgresses++;
			/*
			if (AndroidUtils.DEBUG) {
				log(TAG, "showProgress " + numProgresses + " via "
						+ AndroidUtils.getCompressedStackTrace());
			}
			*/
		}

		FragmentActivity activity = getActivity();
		if (activity == null || progressBarManager == null || activity.isFinishing()
				|| numProgresses <= 0) {
			return;
		}
		OffThread.runOnUIThread(this, false, a -> progressBarManager.show());
	}

	@Thunk
	void hideProgressBar() {
		synchronized (mLock) {
			numProgresses--;
			/*
			if (AndroidUtils.DEBUG) {
				log(TAG, "hideProgress " + numProgresses + " via "
						+ AndroidUtils.getCompressedStackTrace());
			}
			*/
			if (numProgresses <= 0) {
				numProgresses = 0;
			} else {
				return;
			}
		}

		FragmentActivity activity = getActivity();
		if (activity == null || progressBarManager == null
				|| activity.isFinishing()) {
			return;
		}
		OffThread.runOnUIThread(this, false, a -> progressBarManager.hide());
	}

	@UiThread
	public void setRefreshing(boolean refreshing) {
		if (refreshing) {
			showProgressBar();
		} else {
			hideProgressBar();
		}

		if (this.refreshing == refreshing) {
			return;
		}
		this.refreshing = refreshing;
		SideListActivity sideListActivity = getSideListActivity();
		if (sideListActivity != null) {
			sideListActivity.updateSideListRefreshButton();
		}
	}

	public boolean isRefreshing() {
		return refreshing;
	}

	/**
	 * Page has been activated, or if not in a PageAdapter, Fragment has been resumed
	 * <p/>
	 * Adds {@link RefreshTriggerListener}, {@link TorrentListReceivedListener}
	 * <br/>
	 * Triggers {@link #triggerRefresh()}
	 */
	@Override
	public void pageActivated() {
		if (viewActive) {
			if (AndroidUtils.DEBUG_LIFECYCLE) {
				log(TAG, "pageActivated ALREADY ACTIVE"
						+ AndroidUtils.getCompressedStackTrace());
			}
			return;
		}
		if (AndroidUtils.DEBUG_LIFECYCLE) {
			log(TAG, "pageActivated " + AndroidUtils.getCompressedStackTrace());
		}
		viewActive = true;

		session.addRefreshTriggerListener(this, false);
		session.torrent.addListReceivedListener(this, false);

		FragmentActivity activity = getActivity();
		if (activity instanceof ActionModeBeingReplacedListener) {
			((ActionModeBeingReplacedListener) activity).rebuildActionMode();
		}
		SideListActivity sideListActivity = getSideListActivity();
		if (sideListActivity != null) {
			sideListActivity.setControllingFragment(this);
		}

		Bundle arguments = getArguments();
		if (arguments != null) {
			// calls triggerRefresh
			setTorrentID(arguments.getLong("torrentID", torrentID));
		}

		AnalyticsTracker.getInstance(this).fragmentResume(this);
	}

	@Override
	@UiThread
	public final void setTorrentID(long id) {
		if (id != torrentID) {
			torrentID = id;
			triggerRefresh();
		}
	}

	/**
	 * refresh request triggered on a timer length set by user.<br>
	 * Also triggered on {@link #pageActivated()} and when torrent id changes
	 */
	@Override
	@UiThread
	public abstract void triggerRefresh();

	@Override
	public boolean showFilterEntry() {
		return false;
	}

	@Override
	public SideActionSelectionListener getSideActionSelectionListener() {
		return hasSideActons() ? sideActionSelectionListener : null;
	}

	protected boolean hasSideActons() {
		Toolbar abToolBar = requireActivity().findViewById(R.id.actionbar);
		boolean canShowSideActionsArea = abToolBar == null
				|| abToolBar.getVisibility() == View.GONE;
		return canShowSideActionsArea;
	}

	@Override
	public void rpcTorrentListReceived(String callID, List<?> addedTorrentMaps,
			List<String> fields, final int[] fileIndexes,
			@Nullable List<?> removedTorrentIDs) {
		if (!viewActive) {
			return;
		}
		OffThread.runOnUIThread(this, false, activity -> {
			if (!viewActive) {
				return;
			}
			SideListActivity sideListActivity = getSideListActivity();
			if (sideListActivity != null) {
				sideListActivity.updateSideActionMenuItems();
			}
		});
	}

	@Override
	public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
		super.onViewStateRestored(savedInstanceState);
		if (savedInstanceState != null) {
			torrentID = savedInstanceState.getLong(TAG + ".torrentID", torrentID);
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong(TAG + ".torrentID", torrentID);
	}

	@Override
	public void sessionChanged(@Nullable Session newSession) {
		if (viewActive) {
			OffThread.runOnUIThread(this::triggerRefresh);
		}
	}
}
