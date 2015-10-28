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

import android.content.res.Resources;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;

import com.astuetz.PagerSlidingTabStrip;
import com.vuze.android.remote.R;
import com.vuze.android.remote.VuzeRemoteApp;

public class OpenOptionsPagerAdapter
	extends TorrentPagerAdapter
{

	public OpenOptionsPagerAdapter(FragmentManager fm, ViewPager pager,
			PagerSlidingTabStrip tabs) {
		super(fm, pager, tabs);
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.TorrentPagerAdapter#createItem(int)
	 */
	@Override
	public Fragment createItem(int position) {
		Fragment fragment;
		switch (position) {
			case 0:
				fragment = new OpenOptionsGeneralFragment();
				break;

			default:
			case 1:
				fragment = new OpenOptionsFilesFragment();
				break;
		}

		return fragment;
	}

	@Override
	public int getCount() {
		return 2;
	}

	@Override
	public CharSequence getPageTitle(int position) {
		Resources resources = VuzeRemoteApp.getContext().getResources();
		switch (position) {
			case 1:
				return resources.getText(R.string.details_tab_files);

			case 0:
				return resources.getText(R.string.details_tab_general);
		}
		return super.getPageTitle(position);
	}

}
