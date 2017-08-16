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

package com.biglybt.android.client.adapter;

import com.astuetz.PagerSlidingTabStrip;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.client.R;
import com.biglybt.android.client.fragment.OpenOptionsFilesFragment;
import com.biglybt.android.client.fragment.OpenOptionsGeneralFragment;
import com.biglybt.android.client.fragment.OpenOptionsTagsFragment;
import com.biglybt.android.client.rpc.RPCSupports;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;

import android.content.res.Resources;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;

//import com.astuetz.PagerSlidingTabStrip;

public class OpenOptionsPagerAdapter
	extends TorrentPagerAdapter
{
	private int count = 3;

	private final boolean needsGeneralFragment;

	public OpenOptionsPagerAdapter(FragmentManager fm, ViewPager pager,
			PagerSlidingTabStrip tabs, boolean needsGeneralFragment,
			String remoteProfileID) {
		super(fm);
		count = needsGeneralFragment ? 3 : 2;
		this.needsGeneralFragment = needsGeneralFragment;
		Session session = SessionManager.getSession(remoteProfileID, null, null);
		if (!session.getSupports(RPCSupports.SUPPORTS_TAGS)) {
			count--;
		}
		init(pager, tabs);
	}

	@Override
	public Fragment createItem(int position) {
		Fragment fragment;
		if (!needsGeneralFragment) {
			position++;
		}
		switch (position) {
			case 0:
				fragment = new OpenOptionsGeneralFragment();
				break;

			case 2:
				fragment = new OpenOptionsTagsFragment();
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
		return count;
	}

	@Override
	public CharSequence getPageTitle(int position) {
		if (!needsGeneralFragment) {
			position++;
		}
		Resources resources = BiglyBTApp.getContext().getResources();
		switch (position) {
			case 2:
				return resources.getString(R.string.details_tab_tags);

			case 1:
				return resources.getText(R.string.details_tab_files);

			case 0:
				return resources.getText(R.string.details_tab_general);
		}
		return super.getPageTitle(position);
	}

}
