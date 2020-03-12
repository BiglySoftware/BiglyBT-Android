/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.adapter;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.*;
import androidx.viewpager.widget.ViewPager;

import com.astuetz.PagerSlidingTabStrip;
import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.fragment.FragmentPagerListener;
import com.biglybt.util.Thunk;

/**
 * Created by TuxPaper on 10/12/18.
 */
public class PagerAdapterForPagerSlidingTabStrip
	extends PagerAdapterUsingClasses
	implements LifecycleObserver
{
	private static final String TAG = "PagerAdapter_PSTS";

	@Thunk
	ViewPager viewPager;

	private Fragment curFrag;

	public PagerAdapterForPagerSlidingTabStrip(@NonNull FragmentManager fm,
			@NonNull Lifecycle lifecycle,
			Class<? extends Fragment>[] pageItemClasses) {
		super(fm, pageItemClasses);
		lifecycle.addObserver(this);
	}

	public void init(@NonNull ViewPager viewPager,
			@NonNull PagerSlidingTabStrip tabs) {
		this.viewPager = viewPager;

		// Bind the tabs to the ViewPager

		viewPager.setAdapter(this);
		tabs.setViewPager(viewPager);
		tabs.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			int oldPosition = 0;

			@Override
			public void onPageSelected(int position) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "page selected: " + position);
				}
				Fragment oldFrag = findFragmentByPosition(oldPosition);
				pageDeactivated(oldFrag);

				oldPosition = position;

				Fragment newFrag = findFragmentByPosition(position);
				pageActivated(newFrag);
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
	public Fragment getItem(int position) {
		Fragment fragment = super.getItem(position);

		if (fragment == null) {
			return null;
		}

		if (curFrag == null) {
			// Trigger pageActivated on first onResume because nothing else does
			// See https://stackoverflow.com/a/17694619 for bug in ViewPager preventing
			// OnPageChangeListener.onPageSelected being triggered on first tab
			fragment.getLifecycle().addObserver(new DefaultLifecycleObserver() {
				@Override
				public void onResume(@NonNull LifecycleOwner owner) {
					if (viewPager == null || viewPager.getCurrentItem() != position) {
						return;
					}
					pageActivated(fragment);
					fragment.getLifecycle().removeObserver(this);
				}
			});
		}

		return fragment;
	}

	public boolean pageActivated(Fragment newFrag) {
		if (curFrag == newFrag) {
			Log.e(TAG, "pageActivated: already on page " + newFrag + " via "
					+ AndroidUtils.getCompressedStackTrace());
			return false;
		} else if (AndroidUtils.DEBUG) {
			Log.d(TAG, "pageActivated: " + newFrag + " via "
					+ AndroidUtils.getCompressedStackTrace());
		}
		curFrag = newFrag;
		if (newFrag instanceof FragmentPagerListener) {
			((FragmentPagerListener) newFrag).pageActivated();
		}
		return true;
	}

	public boolean pageDeactivated(Fragment frag) {
		if (curFrag == null) {
			Log.e(TAG, "pageDeactivated: already null trying to deactivate " + frag
					+ " via " + AndroidUtils.getCompressedStackTrace());
			return false;
		} else if (AndroidUtils.DEBUG) {
			Log.d(TAG, "pageDeactivated: " + frag + " via "
					+ AndroidUtils.getCompressedStackTrace());
		}
		curFrag = null;
		if (frag instanceof FragmentPagerListener) {
			((FragmentPagerListener) frag).pageDeactivated();
		}
		return true;
	}

	@Nullable
	public Fragment getCurrentFragment() {
		if (viewPager == null) {
			return null;
		}
		return findFragmentByPosition(viewPager.getCurrentItem());
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
	public final void onResumePageHolderFragment() {
		if (viewPager == null) {
			return;
		}
		Fragment newFrag = findFragmentByPosition(viewPager.getCurrentItem());
		if (newFrag != null) {
			// Note: newFrag will be null on first view, so position 0 will not
			// get pageActivated from here
			pageActivated(newFrag);
		}
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_STOP)
	public void onStopPageHandlerFragment() {
		if (viewPager == null) {
			return;
		}
		Fragment frag = findFragmentByPosition(viewPager.getCurrentItem());
		pageDeactivated(frag);
	}

}
