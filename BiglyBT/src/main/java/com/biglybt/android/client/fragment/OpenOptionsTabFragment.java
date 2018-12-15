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

import com.astuetz.PagerSlidingTabStrip;
import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.adapter.OpenOptionsPagerAdapter;
import com.biglybt.android.client.sidelist.*;
import com.biglybt.util.Thunk;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Fragment fo Open Options activity.  Contains tabs, body, propagates SideList calls to page fragments.
 * <p/>
 * Contained in {@link com.biglybt.android.client.activity.TorrentOpenOptionsActivity}
 * <p/>
 * Created by TuxPaper on 12/29/15.
 */
public class OpenOptionsTabFragment
	extends SideListFragment
{
	private static final String TAG = "OpenOptionsTab";

	@Thunk
	OpenOptionsPagerAdapter pagerAdapter;

	private View topView;

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		String tag = getTag();

		Object oTag = topView.getTag();
		if (oTag instanceof String) {
			tag = (String) oTag;
		}

		final ViewPager viewPager = topView.findViewById(R.id.pager);
		PagerSlidingTabStrip tabs = topView.findViewById(R.id.pager_title_strip);

		if (viewPager != null && tabs != null) {
			long torrentID = TorrentUtils.getTorrentID(requireActivity());

			pagerAdapter = new OpenOptionsPagerAdapter(getChildFragmentManager(),
					getLifecycle(), viewPager, tabs, "general".equals(tag),
					remoteProfileID) {
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
			if (torrentID >= 0) {
				pagerAdapter.setSelection(torrentID);
			}
		} else {
			pagerAdapter = null;
		}
	}

	@Override
	public View onCreateViewWithSession(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		FragmentActivity activity = requireActivity();
		Intent intent = activity.getIntent();

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, activity + "] onCreateview " + this);
		}

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
		}

		topView = inflater.inflate(AndroidUtils.isTV(getContext())
				? R.layout.frag_openoptions_tabs_tv : R.layout.frag_openoptions_tabs,
				container, false);

		return topView;
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

		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			return ((SideListHelperListener) frag).getSideActionSelectionListener();
		}
		return null;
	}

	@Override
	public void sideListExpandListChanged(boolean expanded) {
		if (pagerAdapter == null) {
			super.sideListExpandListChanged(expanded);
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).sideListExpandListChanged(expanded);
		}
	}

	@Override
	public void sideListExpandListChanging(boolean expanded) {
		if (pagerAdapter == null) {
			super.sideListExpandListChanging(expanded);
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).sideListExpandListChanging(expanded);
		}
	}

	@Override
	public void onSideListHelperVisibleSetup(View view) {
		if (pagerAdapter == null) {
			super.onSideListHelperVisibleSetup(view);
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).onSideListHelperVisibleSetup(view);
		}
	}

	@Override
	public void onSideListHelperPostSetup(SideListHelper sideListHelper) {
		if (pagerAdapter == null) {
			super.onSideListHelperPostSetup(sideListHelper);
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).onSideListHelperPostSetup(sideListHelper);
		}
	}

	@Override
	public void onSideListHelperCreated(SideListHelper sideListHelper) {
		if (pagerAdapter == null) {
			super.onSideListHelperCreated(sideListHelper);
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
