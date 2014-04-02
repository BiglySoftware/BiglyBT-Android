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

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;

import com.vuze.android.remote.R;
import com.vuze.android.remote.SetTorrentIdListener;
import com.vuze.android.remote.VuzeRemoteApp;

public class TorrentDetailsPagerAdapter
	extends FragmentStatePagerAdapter
{

	public static interface PagerPosition {
		public void setPagerPosition(int position);
		public int getPagerPosition();
	}
	
	private long torrentID = -1;
	private ViewPager pager;
	
	public TorrentDetailsPagerAdapter(FragmentManager fm, ViewPager pager) {
		super(fm);
		this.pager = pager;
	}

	@Override
	public Fragment getItem(int position) {
		Fragment fragment;
		switch (position) {
			case 2:
				fragment = new PeersFragment();
				break;
			case 1:
				fragment = new TorrentInfoFragment();
				break;
			default:
				fragment = new FilesFragment();
		}
		
		updateFragmentArgs(fragment, position);

		return fragment;
	}

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
			if (position == pager.getCurrentItem()) {
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
  		arguments.putBoolean("isActive", position == pager.getCurrentItem());
  		fragment.setArguments(arguments);
		}
	}

	@Override
	public int getCount() {
		return 3;
	}

	@Override
	public CharSequence getPageTitle(int position) {
		Resources resources = VuzeRemoteApp.getContext().getResources();
		switch (position) {
			case 0:
				return resources.getText(R.string.details_tab_files);

			case 2:
				return resources.getText(R.string.details_tab_peers);

			case 1:
				return resources.getText(R.string.details_tab_info);
		}
		return super.getPageTitle(position);
	}

	public void setSelection(long torrentID) {
		this.torrentID = torrentID;
	}

	public Fragment findFragmentByPosition(FragmentManager fm, int position) {
		List<Fragment> fragments = fm.getFragments();
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
}
