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

import java.util.ArrayList;
import java.util.List;

import com.astuetz.PagerSlidingTabStrip;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.client.R;
import com.biglybt.android.client.fragment.*;
import com.biglybt.android.client.rpc.RPCSupports;
import com.biglybt.android.client.session.*;

import android.arch.lifecycle.Lifecycle;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;

public class TorrentDetailsPagerAdapter
	extends TorrentPagerAdapter
	implements SessionSettingsChangedListener
{

	private final String remoteProfileID;

	protected TorrentDetailsPagerAdapter(FragmentManager fm, Lifecycle lifecycle,
			ViewPager pager, PagerSlidingTabStrip tabs,
			@NonNull String remoteProfileID) {
		//noinspection unchecked
		super(fm, lifecycle);
		this.remoteProfileID = remoteProfileID;

		setPageItemClasses();
		init(pager, tabs);
	}

	private void setPageItemClasses() {
		Session session = SessionManager.getSession(remoteProfileID, null, null);

		List<Class<? extends Fragment>> pageItemClasses = new ArrayList<>();
		pageItemClasses.add(FilesFragment.class);
		pageItemClasses.add(TorrentInfoFragment.class);
		pageItemClasses.add(PeersFragment.class);
		if (session.getSupports(RPCSupports.SUPPORTS_TAGS)) {
			pageItemClasses.add(TorrentTagsFragment.class);
		}

		//noinspection unchecked
		setPageItemClasses(pageItemClasses.toArray(new Class[0]));
	}

	@Override
	public void onResumePageHolderFragment() {
		super.onResumePageHolderFragment();

		Session session = SessionManager.getSession(remoteProfileID, null, null);
		session.addSessionSettingsChangedListeners(this);
	}

	@Override
	public void sessionSettingsChanged(SessionSettings newSessionSettings) {
		setPageItemClasses();
	}

	@Override
	public void speedChanged(long downloadSpeed, long uploadSpeed) {

	}

	@Override
	public void onPausePageHandlerFragment() {
		super.onPausePageHandlerFragment();

		if (SessionManager.hasSession(remoteProfileID)) {
			Session session = SessionManager.getSession(remoteProfileID, null, null);
			session.removeSessionSettingsChangedListeners(this);
		}
	}

	@Override
	public CharSequence getPageTitle(int position) {
		Resources resources = BiglyBTApp.getContext().getResources();
		switch (position) {
			case 0:
				return resources.getText(R.string.details_tab_files);

			case 1:
				return resources.getText(R.string.details_tab_info);

			case 2:
				return resources.getText(R.string.details_tab_peers);

			case 3:
				return resources.getText(R.string.details_tab_tags);
		}
		return super.getPageTitle(position);
	}

}
