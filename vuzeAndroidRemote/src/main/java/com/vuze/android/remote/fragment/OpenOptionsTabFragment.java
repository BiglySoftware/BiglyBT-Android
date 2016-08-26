package com.vuze.android.remote.fragment;

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
import com.astuetz.PagerSlidingTabStrip;
import com.vuze.android.remote.*;
import com.vuze.android.remote.adapter.OpenOptionsPagerAdapter;

/**
 * Created by TuxPaper on 12/29/15.
 */
public class OpenOptionsTabFragment
	extends Fragment
{
	private static final String TAG = "OpenOptionsTab";

	/* @Thunk */ OpenOptionsPagerAdapter pagerAdapter;

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).fragmentStart(this, TAG);
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
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

		View topView = inflater.inflate(R.layout.frag_openoptions_tabs, container,
				false);

		String tag = getTag();

		final ViewPager viewPager = (ViewPager) topView.findViewById(R.id.pager);
		PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) topView.findViewById(
				R.id.pager_title_strip);
		//Log.e(TAG, this + "pagerAdapter is " + pagerAdapter + ";vp=" + viewPager + ";tabs=" + tabs + ";tag=" + tag);
		if (viewPager != null && tabs != null) {
			pagerAdapter = new OpenOptionsPagerAdapter(getChildFragmentManager(),
					viewPager, tabs, tag == null);
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
