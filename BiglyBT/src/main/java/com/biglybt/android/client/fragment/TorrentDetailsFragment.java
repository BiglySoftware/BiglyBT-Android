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

import java.util.List;
import java.util.Map;

import com.astuetz.PagerSlidingTabStrip;
import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.activity.TorrentDetailsActivity;
import com.biglybt.android.client.activity.TorrentViewActivity;
import com.biglybt.android.client.adapter.TorrentDetailsPagerAdapter;
import com.biglybt.android.client.adapter.TorrentPagerAdapter;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.sidelist.*;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.view.*;

/**
 * Torrent Details Fragment<br>
 * - Contains {@link PeersFragment}, {@link FilesFragment}, {@link TorrentInfoFragment}, {@link TorrentTagsFragment}<br>
 * - Contained in {@link TorrentViewActivity} for wide screens<br>
 * - Contained in {@link TorrentDetailsActivity} for narrow screens<br>
 */
public class TorrentDetailsFragment
	extends SideListFragment
	implements ActionModeBeingReplacedListener, View.OnKeyListener
{
	private TorrentPagerAdapter pagerAdapter;

	@Thunk
	long torrentID;

	@Override
	public View onCreateViewWithSession(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		Context context = requireContext();

		View view = inflater.inflate(
				AndroidUtils.isTV(context) ? R.layout.frag_torrent_details_tv
						: R.layout.frag_torrent_details_coord,
				container, false);
		assert view != null;

		CollapsingToolbarLayout collapsingToolbarLayout = view.findViewById(
				R.id.collapsing_toolbar);
		if (collapsingToolbarLayout != null) {
			AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) collapsingToolbarLayout.getLayoutParams();
			if (AndroidUtilsUI.getScreenHeightDp(context) >= 1000) {
				params.setScrollFlags(0);
			} else {
				params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
						| AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED);

				final AppBarLayout appbar = view.findViewById(R.id.appbar);
				appbar.addOnOffsetChangedListener(
						new AppBarLayout.OnOffsetChangedListener() {
							boolean isInFullView = true;

							@Override
							public void onOffsetChanged(AppBarLayout appBarLayout,
									int verticalOffset) {
								boolean isNowInFullView = verticalOffset == 0;
								if (isInFullView != isNowInFullView) {
									isInFullView = isNowInFullView;
									SessionActivity activity = (SessionActivity) requireActivity();
									if (activity.isFinishing()) {
										return;
									}
									ActionBar actionBar = activity.getSupportActionBar();
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

		setHasOptionsMenu(true);

		ViewPager viewPager = view.findViewById(R.id.pager);
		PagerSlidingTabStrip tabs = view.findViewById(R.id.pager_title_strip);

		if (viewPager != null) {
			viewPager.setOnKeyListener(this);
		}
		view.setOnKeyListener(this);

		// adapter will bind pager, tabs and adapter together
		pagerAdapter = new TorrentDetailsPagerAdapter(getFragmentManager(),
				getLifecycle(), viewPager, tabs, remoteProfileID) {
			@Override
			public boolean pageActivated(Fragment pageFragment) {
				if (!super.pageActivated(pageFragment)) {
					return false;
				}
				SideListActivity sideListActivity = getSideListActivity();
				if (sideListActivity != null) {
					sideListActivity.rebuildSideList();
				}
				return true;
			}

		};

		return view;
	}

	// Called from Activity
	public void setTorrentIDs(@Nullable long[] newIDs) {
		this.torrentID = newIDs != null && newIDs.length == 1 ? newIDs[0] : -1;
		if (pagerAdapter != null) {
			pagerAdapter.setSelection(torrentID);
		}
		AndroidUtilsUI.runOnUIThread(this, false, activity -> {
			List<Fragment> fragments = AndroidUtilsUI.getFragments(
					getFragmentManager());
			for (Fragment item : fragments) {
				if (item instanceof SetTorrentIdListener) {
					((SetTorrentIdListener) item).setTorrentID(torrentID);
				}
			}
		});
	}

	public void onCreateActionMode(@Nullable ActionMode mode, Menu menu) {
		MenuInflater inflater;
		if (mode == null) {
			FragmentActivity activity = getActivity();
			if (activity == null) {
				return;
			}
			inflater = activity.getMenuInflater();
		} else {
			inflater = mode.getMenuInflater();
		}
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getFragmentManager());
		for (Fragment frag : fragments) {
			if (frag instanceof FragmentPagerListener) {
				frag.onCreateOptionsMenu(menu, inflater);
			}
		}
	}

	public void onPrepareActionMode(Menu menu) {
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getFragmentManager());
		for (Fragment frag : fragments) {
			if (frag instanceof FragmentPagerListener) {
				frag.onPrepareOptionsMenu(menu);
			}
		}
	}

	public boolean onActionItemClicked(MenuItem item) {
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getFragmentManager());
		for (Fragment frag : fragments) {
			if (frag instanceof FragmentPagerListener) {
				if (frag.onOptionsItemSelected(item)) {
					return true;
				}
			}
		}
		return false;
	}

	public void playVideo() {
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getFragmentManager());
		for (Fragment frag : fragments) {
			if (frag instanceof FilesFragment) {
				((FilesFragment) frag).launchOrStreamFile();
			}
		}
	}

	@Override
	public void setActionModeBeingReplaced(ActionMode actionMode,
			boolean actionModeBeingReplaced) {
	}

	@Override
	public void actionModeBeingReplacedDone() {
	}

	@Override
	public void rebuildActionMode() {
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
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (pagerAdapter == null) {
			return false;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof View.OnKeyListener) {
			return ((View.OnKeyListener) frag).onKey(v, keyCode, event);
		}
		return false;
	}

	@Override
	public SortableRecyclerAdapter getMainAdapter() {
		if (pagerAdapter == null) {
			return null;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			return ((SideListHelperListener) frag).getMainAdapter();
		}
		return null;
	}

	@Override
	public SideActionSelectionListener getSideActionSelectionListener() {
		if (pagerAdapter == null) {
			return null;
		}
		Toolbar abToolBar = requireActivity().findViewById(R.id.actionbar);
		boolean canShowSideActionsArea = abToolBar == null
				|| abToolBar.getVisibility() == View.GONE;
		if (!canShowSideActionsArea) {
			return null;
		}

		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			return ((SideListHelperListener) frag).getSideActionSelectionListener();
		}
		return null;
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void sideListExpandListChanged(boolean expanded) {
		if (pagerAdapter == null) {
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).sideListExpandListChanged(expanded);
		}
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void sideListExpandListChanging(boolean expanded) {
		if (pagerAdapter == null) {
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).sideListExpandListChanging(expanded);
		}
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void onSideListHelperVisibleSetup(View view) {
		if (pagerAdapter == null) {
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).onSideListHelperVisibleSetup(view);
		}
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void onSideListHelperPostSetup(SideListHelper sideListHelper) {
		if (pagerAdapter == null) {
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).onSideListHelperPostSetup(sideListHelper);
		}
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void onSideListHelperCreated(SideListHelper sideListHelper) {
		if (pagerAdapter == null) {
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).onSideListHelperCreated(sideListHelper);
		}
	}

	@Override
	public boolean showFilterEntry() {
		if (pagerAdapter == null) {
			return false;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			return ((SideListHelperListener) frag).showFilterEntry();
		}
		return false;
	}
}
