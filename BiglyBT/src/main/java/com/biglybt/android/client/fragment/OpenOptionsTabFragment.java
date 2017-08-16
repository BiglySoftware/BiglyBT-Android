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
import com.biglybt.android.client.AnalyticsTracker;
import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;
import com.biglybt.android.client.adapter.OpenOptionsPagerAdapter;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.util.Thunk;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

//import com.astuetz.PagerSlidingTabStrip;

/**
 * Created by TuxPaper on 12/29/15.
 */
public class OpenOptionsTabFragment
	extends Fragment
{
	private static final String TAG = "OpenOptionsTab";

	@Thunk
	OpenOptionsPagerAdapter pagerAdapter;

	@Override
	public void onStart() {
		super.onStart();
		AnalyticsTracker.getInstance(this).fragmentResume(this, TAG);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		FragmentActivity activity = getActivity();
		Intent intent = activity.getIntent();

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, activity + "] onCreateview " + this);
		}

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
		}

		View topView = inflater.inflate(AndroidUtils.isTV()
				? R.layout.frag_openoptions_tabs_tv : R.layout.frag_openoptions_tabs,
				container, false);

		String tag = getTag();

		final ViewPager viewPager = (ViewPager) topView.findViewById(R.id.pager);
		PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) topView.findViewById(
				R.id.pager_title_strip);
		//Log.e(TAG, this + "pagerAdapter is " + pagerAdapter + ";vp=" + viewPager + ";tabs=" + tabs + ";tag=" + tag);
		if (viewPager != null && tabs != null) {
			pagerAdapter = new OpenOptionsPagerAdapter(getChildFragmentManager(),
					viewPager, tabs, tag == null,
					SessionManager.findRemoteProfileID(this));
			tabs.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
				@Override
				public void onPageScrolled(int position, float positionOffset,
						int positionOffsetPixels) {

				}

				@Override
				public void onPageSelected(int position) {
					Fragment newFrag = pagerAdapter.getPrimaryItem();
					if (newFrag instanceof FragmentPagerListener) {
						FragmentPagerListener l = (FragmentPagerListener) newFrag;
						l.pageActivated();
					}
				}

				@Override
				public void onPageScrollStateChanged(int state) {

				}
			});
		} else {
			pagerAdapter = null;
		}

		return topView;
	}

	@Override
	public void onPause() {
		if (pagerAdapter != null) {
			pagerAdapter.onPause();
		}

		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();

		if (pagerAdapter != null) {
			pagerAdapter.onResume();
		}
	}

}
