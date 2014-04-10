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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v7.view.ActionMode;
import android.util.Log;
import android.view.*;

import com.astuetz.PagerSlidingTabStrip;
import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentDetailsActivity;
import com.vuze.android.remote.activity.TorrentViewActivity;

/**
 * Torrent Details Fragment<br>
 * - Contains {@link PeersFragment}, {@link FilesFragment}, {@link TorrentInfoFragment}<br>
 * - Contained in {@link TorrentViewActivity} for wide screens<br>
 * - Contained in {@link TorrentDetailsActivity} for narrow screens<br>
 */
public class TorrentDetailsFragment
	extends Fragment
{
	protected static final String TAG = "TorrentDetailsFrag";

	ViewPager mViewPager;

	private TorrentDetailsPagerAdapter pagerAdapter;

	private long torrentID;

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this, TAG);
	}

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.frag_torrent_details, container,
				false);

		setHasOptionsMenu(true);

		mViewPager = (ViewPager) view.findViewById(R.id.pager);
		pagerAdapter = new TorrentDetailsPagerAdapter(getFragmentManager(),
				mViewPager);

		// Bind the tabs to the ViewPager
		PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) view.findViewById(R.id.pager_title_strip);

		mViewPager.setAdapter(pagerAdapter);
		tabs.setViewPager(mViewPager);

		tabs.setOnPageChangeListener(new OnPageChangeListener() {
			int oldPosition = 0;

			@Override
			public void onPageSelected(int position) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "page selected: " + position);
				}
				Fragment oldFrag = pagerAdapter.findFragmentByPosition(
						getFragmentManager(), oldPosition);
				if (oldFrag instanceof FragmentPagerListener) {
					FragmentPagerListener l = (FragmentPagerListener) oldFrag;
					l.pageDeactivated();
				}

				oldPosition = position;

				Fragment newFrag = pagerAdapter.findFragmentByPosition(
						getFragmentManager(), position);
				if (newFrag instanceof FragmentPagerListener) {
					FragmentPagerListener l = (FragmentPagerListener) newFrag;
					l.pageActivated();
				}
			}

			@Override
			public void onPageScrolled(int position, float positionOffset,
					int positionOffsetPixels) {
			}

			@Override
			public void onPageScrollStateChanged(int state) {
			}
		});

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		Fragment newFrag = pagerAdapter.findFragmentByPosition(
				getFragmentManager(), mViewPager.getCurrentItem());
		// newFrag will be null on first view, so position 0 will not
		// get pageActivated from here
		if (newFrag instanceof FragmentPagerListener) {
			FragmentPagerListener l = (FragmentPagerListener) newFrag;
			l.pageActivated();
		}
	}

	@Override
	public void onPause() {
		Fragment newFrag = pagerAdapter.findFragmentByPosition(
				getFragmentManager(), mViewPager.getCurrentItem());
		if (newFrag instanceof FragmentPagerListener) {
			FragmentPagerListener l = (FragmentPagerListener) newFrag;
			l.pageDeactivated();
		}

		super.onPause();
	}

	// Called from Activity
	public void setTorrentIDs(long[] newIDs) {
		this.torrentID = newIDs != null && newIDs.length == 1 ? newIDs[0] : -1;
		pagerAdapter.setSelection(torrentID);
		AndroidUtils.runOnUIThread(this, new Runnable() {
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

	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		MenuInflater inflater = mode.getMenuInflater();
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

	public void onPrepareActionMode(ActionMode mode, Menu menu) {
		List<Fragment> fragments = getFragmentManager().getFragments();
		for (Fragment frag : fragments) {
			if (frag instanceof FragmentPagerListener) {
				if (frag.hasOptionsMenu()) {
					frag.onPrepareOptionsMenu(menu);
				}
			}
		}
	}

	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
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
				((FilesFragment) frag).launchFile();
			}
		}
	}
}
