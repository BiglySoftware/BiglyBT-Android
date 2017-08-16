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
import com.biglybt.android.client.fragment.*;
import com.biglybt.android.client.rpc.RPCSupports;
import com.biglybt.android.client.session.*;

import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;

public class TorrentDetailsPagerAdapter
	extends TorrentPagerAdapter
	implements SessionSettingsChangedListener
{

	private final String remoteProfileID;

	private int count = 4;

	public TorrentDetailsPagerAdapter(FragmentManager fm, ViewPager pager,
			PagerSlidingTabStrip tabs, @Nullable String remoteProfileID) {
		super(fm);
		this.remoteProfileID = remoteProfileID;
		count = 4;
		init(pager, tabs);
	}

	@Override
	public void onResume() {
		super.onResume();

		Session session = SessionManager.getSession(remoteProfileID, null, null);
		session.addSessionSettingsChangedListeners(this);
	}

	@Override
	public void sessionSettingsChanged(SessionSettings newSessionSettings) {
		Session session = SessionManager.getSession(remoteProfileID, null, null);
		int newCount = session.getSupports(RPCSupports.SUPPORTS_TAGS) ? 4 : 3;
		if (newCount != count) {
			count = newCount;
			notifyDataSetChanged();
			//tabs.notifyDataSetChanged();
		}
	}

	@Override
	public void speedChanged(long downloadSpeed, long uploadSpeed) {

	}

	@Override
	public void onPause() {
		super.onPause();

		if (SessionManager.hasSession(remoteProfileID)) {
			Session session = SessionManager.getSession(remoteProfileID, null, null);
			session.removeSessionSettingsChangedListeners(this);
		}
	}

	@Override
	public Fragment createItem(int position) {
		Fragment fragment;
		switch (position) {
			case 3:
				fragment = new TorrentTagsFragment();
				break;
			case 2:
				fragment = new PeersFragment();
				break;
			case 1:
				fragment = new TorrentInfoFragment();
				break;
			default:
				fragment = new FilesFragment();
		}

		return fragment;
	}

	@Override
	public int getCount() {
		return count;
	}

	@Override
	public CharSequence getPageTitle(int position) {
		Resources resources = BiglyBTApp.getContext().getResources();
		switch (position) {
			case 0:
				return resources.getText(R.string.details_tab_files);

			case 2:
				return resources.getText(R.string.details_tab_peers);

			case 3:
				return resources.getText(R.string.details_tab_tags);

			case 1:
				return resources.getText(R.string.details_tab_info);
		}
		return super.getPageTitle(position);
	}

}
