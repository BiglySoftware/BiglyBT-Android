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
import com.biglybt.android.client.fragment.FilesFragment;
import com.biglybt.android.client.fragment.OpenOptionsGeneralFragment;
import com.biglybt.android.client.fragment.OpenOptionsTagsFragment;
import com.biglybt.android.client.rpc.RPCSupports;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;

import androidx.annotation.UiThread;
import androidx.lifecycle.Lifecycle;
import android.content.res.Resources;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

public class OpenOptionsPagerAdapter
	extends TorrentPagerAdapter
{
	private final boolean needsGeneralFragment;

	@UiThread
	public OpenOptionsPagerAdapter(FragmentManager fm, Lifecycle lifecycle,
			ViewPager pager, PagerSlidingTabStrip tabs, boolean needsGeneralFragment,
			String remoteProfileID) {
		//noinspection unchecked
		super(fm, lifecycle);
		this.needsGeneralFragment = needsGeneralFragment;
		Session session = SessionManager.getSession(remoteProfileID, null);

		List<Class<? extends Fragment>> pageItemClasses = new ArrayList<>();
		if (needsGeneralFragment) {
			pageItemClasses.add(OpenOptionsGeneralFragment.class);
		}
		pageItemClasses.add(FilesFragment.class);
		if (session.getSupports(RPCSupports.SUPPORTS_TAGS)) {
			pageItemClasses.add(OpenOptionsTagsFragment.class);
		}

		//noinspection unchecked
		setPageItemClasses(pageItemClasses.toArray(new Class[0]));

		init(pager, tabs);
	}

	@Override
	public CharSequence getPageTitle(int position) {
		if (!needsGeneralFragment) {
			position++;
		}
		Resources resources = BiglyBTApp.getContext().getResources();
		switch (position) {
			case 0:
				return resources.getText(R.string.details_tab_general);

			case 1:
				return resources.getText(R.string.details_tab_files);

			case 2:
				return resources.getText(R.string.details_tab_tags);

		}
		return super.getPageTitle(position);
	}

}
