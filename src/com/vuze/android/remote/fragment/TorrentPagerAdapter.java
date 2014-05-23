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
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;

import com.astuetz.PagerSlidingTabStrip;
import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.SetTorrentIdListener;

/**
 * PagerAdapter for a torrent.  Links up {@link ViewPager}, 
 * {@link PagerSlidingTabStrip} and adapter.
 * <p>
 * Any page Fragments that implement {@link FragmentPagerListener} will
 * get notified of activation/deactivation.  Listener requires call to 
 * {@link #onPause()} and {@link #onResume()} to work correcly.
 */
public abstract class TorrentPagerAdapter
	extends FragmentStatePagerAdapter
{

	public static interface PagerPosition
	{
		public void setPagerPosition(int position);

		public int getPagerPosition();
	}

	protected static final String TAG = "TorrentPagerAdapter";

	private long torrentID = -1;

	private ViewPager viewPager;

	private FragmentManager fm;

	public TorrentPagerAdapter(final FragmentManager fragmentManager,
			ViewPager viewPager, PagerSlidingTabStrip tabs) {
		super(fragmentManager);
		this.fm = fragmentManager;
		this.viewPager = viewPager;

		// Bind the tabs to the ViewPager

		viewPager.setAdapter(this);
		tabs.setViewPager(viewPager);
		tabs.setOnPageChangeListener(new OnPageChangeListener() {
			int oldPosition = 0;

			@Override
			public void onPageSelected(int position) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "page selected: " + position);
				}
				Fragment oldFrag = findFragmentByPosition(fm, oldPosition);
				if (oldFrag instanceof FragmentPagerListener) {
					FragmentPagerListener l = (FragmentPagerListener) oldFrag;
					l.pageDeactivated();
				}

				oldPosition = position;

				Fragment newFrag = findFragmentByPosition(fm, position);
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

	}

	@Override
	public final Fragment getItem(int position) {
		Fragment fragment = createItem(position);

		updateFragmentArgs(fragment, position);

		return fragment;
	}

	public abstract Fragment createItem(int position);

	private void updateFragmentArgs(Fragment fragment, int position) {
		if (fragment == null) {
			return;
		}
		if (fragment instanceof PagerPosition) {
			PagerPosition pagerPosition = (PagerPosition) fragment;
			pagerPosition.setPagerPosition(position);
		}

		if (fragment.getActivity() != null) {
			if (fragment instanceof SetTorrentIdListener) {
				((SetTorrentIdListener) fragment).setTorrentID(torrentID);
			}
			if (position == viewPager.getCurrentItem()) {
				// Special case for first item, which never gets an onPageSelected event
				// Send pageActivated event.
				if (fragment instanceof FragmentPagerListener) {
					FragmentPagerListener l = (FragmentPagerListener) fragment;
					l.pageActivated();
				}
			}
		} else {
			Bundle arguments = fragment.getArguments();
			if (arguments == null) {
				arguments = new Bundle();
			}
			arguments.putLong("torrentID", torrentID);
			arguments.putInt("pagerPosition", position);
			// Fragment will have to handle pageActivated call when it's view is
			// attached :(
			arguments.putBoolean("isActive", position == viewPager.getCurrentItem());
			fragment.setArguments(arguments);
		}
	}

	public void setSelection(long torrentID) {
		this.torrentID = torrentID;
	}

	public Fragment findFragmentByPosition(FragmentManager fm, int position) {
		List<Fragment> fragments = fm.getFragments();
		if (fragments == null) {
			return null;
		}
		for (Fragment fragment : fragments) {
			if (fragment instanceof PagerPosition) {
				PagerPosition pp = (PagerPosition) fragment;
				if (pp.getPagerPosition() == position) {
					return fragment;
				}
			}
		}
		return null;
	}

	public void onResume() {
		Fragment newFrag = findFragmentByPosition(fm, viewPager.getCurrentItem());
		// newFrag will be null on first view, so position 0 will not
		// get pageActivated from here
		if (newFrag instanceof FragmentPagerListener) {
			FragmentPagerListener l = (FragmentPagerListener) newFrag;
			l.pageActivated();
		}
	}

	public void onPause() {
		Fragment newFrag = findFragmentByPosition(
				fm, viewPager.getCurrentItem());
		if (newFrag instanceof FragmentPagerListener) {
			FragmentPagerListener l = (FragmentPagerListener) newFrag;
			l.pageDeactivated();
		}
	}

}
