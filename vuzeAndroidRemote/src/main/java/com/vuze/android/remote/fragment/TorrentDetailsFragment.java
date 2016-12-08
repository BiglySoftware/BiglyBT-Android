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

package com.vuze.android.remote.fragment;

import java.util.List;

import com.astuetz.PagerSlidingTabStrip;
import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentDetailsActivityTV;
import com.vuze.android.remote.activity.TorrentViewActivity;
import com.vuze.android.remote.adapter.TorrentDetailsPagerAdapter;
import com.vuze.android.remote.adapter.TorrentPagerAdapter;
import com.vuze.util.Thunk;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.view.ActionMode;
import android.view.*;

/**
 * Torrent Details Fragment<br>
 * - Contains {@link PeersFragment}, {@link FilesFragment}, {@link TorrentInfoFragment}<br>
 * - Contained in {@link TorrentViewActivity} for wide screens<br>
 * - Contained in {@link TorrentDetailsActivityTV} for narrow screens<br>
 */
public class TorrentDetailsFragment
	extends Fragment
	implements ActionModeBeingReplacedListener, View.OnKeyListener
{
	private static final String TAG = "TorrentDetailsFrag";

	private ViewPager viewPager;

	private TorrentPagerAdapter pagerAdapter;

	@Thunk
	long torrentID;

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).fragmentStart(this, TAG);
	}

	public View onCreateView(android.view.LayoutInflater inflater,
			final android.view.ViewGroup container, Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.frag_torrent_details, container,
				false);

		setHasOptionsMenu(true);

		viewPager = (ViewPager) view.findViewById(R.id.pager);
		PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) view.findViewById(
				R.id.pager_title_strip);

		viewPager.setOnKeyListener(this);
		view.setOnKeyListener(this);

		// adapter will bind pager, tabs and adapter together
		String remoteProfileID = SessionInfoManager.findRemoteProfileID(
				getActivity(), TAG);
		pagerAdapter = new TorrentDetailsPagerAdapter(getFragmentManager(),
				viewPager, tabs, remoteProfileID);

		return view;
	}

	public ViewPager getViewPager() {
		return viewPager;
	}

	@Override
	public void onResume() {
		super.onResume();

		pagerAdapter.onResume();
	}

	@Override
	public void onPause() {
		pagerAdapter.onPause();

		super.onPause();
	}

	// Called from Activity
	public void setTorrentIDs(@Nullable long[] newIDs) {
		this.torrentID = newIDs != null && newIDs.length == 1 ? newIDs[0] : -1;
		pagerAdapter.setSelection(torrentID);
		AndroidUtilsUI.runOnUIThread(this, new Runnable() {
			public void run() {
				List<Fragment> fragments = getFragmentManager().getFragments();
				for (Fragment item : fragments) {
					if (item instanceof SetTorrentIdListener) {
						((SetTorrentIdListener) item).setTorrentID(torrentID);
					}
				}
			}
		});
	}

	public boolean onCreateActionMode(@Nullable ActionMode mode, Menu menu) {
		MenuInflater inflater = mode == null ? getActivity().getMenuInflater()
				: mode.getMenuInflater();
		List<Fragment> fragments = getFragmentManager().getFragments();
		for (Fragment frag : fragments) {
			if (frag instanceof FragmentPagerListener) {
				if (frag.hasOptionsMenu()) {
					frag.onCreateOptionsMenu(menu, inflater);
				}
			}
		}
		return true;
	}

	public void onPrepareActionMode(Menu menu) {
		List<Fragment> fragments = getFragmentManager().getFragments();
		for (Fragment frag : fragments) {
			if (frag instanceof FragmentPagerListener) {
				if (frag.hasOptionsMenu()) {
					frag.onPrepareOptionsMenu(menu);
				}
			}
		}
	}

	public boolean onActionItemClicked(MenuItem item) {
		List<Fragment> fragments = getFragmentManager().getFragments();
		for (Fragment frag : fragments) {
			if (frag instanceof FragmentPagerListener) {
				if (frag.hasOptionsMenu()) {
					if (frag.onOptionsItemSelected(item)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public void playVideo() {
		List<Fragment> fragments = getFragmentManager().getFragments();
		for (Fragment frag : fragments) {
			if (frag instanceof FilesFragment) {
				((FilesFragment) frag).launchOrStreamFile();
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#setActionModeBeingReplaced(android.support.v7.view.ActionMode, boolean)
	 */
	@Override
	public void setActionModeBeingReplaced(ActionMode actionMode,
			boolean actionModeBeingReplaced) {
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#actionModeBeingReplacedDone()
	 */
	@Override
	public void actionModeBeingReplacedDone() {
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#rebuildActionMode()
	 */
	@Override
	public void rebuildActionMode() {
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#getActionMode()
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
