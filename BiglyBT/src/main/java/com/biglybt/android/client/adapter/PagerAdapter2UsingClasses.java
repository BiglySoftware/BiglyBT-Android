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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.biglybt.android.client.fragment.FragmentPagerListener;

import org.jetbrains.annotations.Contract;

import java.util.List;

/**
 * Created by TuxPaper on 3/14/20.
 */
public class PagerAdapter2UsingClasses
	extends FragmentStateAdapter
{
	private static final String ARGKEY_FRAG_IN_PAGEADAPTER = "fragInPageAdapter";

	private static class MyDefaultLifecycleObserver
		implements DefaultLifecycleObserver
	{
		private final Fragment fragment;

		MyDefaultLifecycleObserver(Fragment fragment) {
			this.fragment = fragment;
		}

		@Override
		public void onResume(@NonNull LifecycleOwner owner) {
			if (fragment instanceof FragmentPagerListener) {
				((FragmentPagerListener) fragment).pageActivated();
			}
			owner.getLifecycle().removeObserver(this);
		}
	}

	public interface FragmentAdapterCallback
	{
		void pagerAdapterFragmentCreated(Fragment fragment);
	}

	@NonNull
	private final FragmentManager fm;

	@NonNull
	private Class<? extends Fragment>[] pageItemClasses;

	@NonNull
	private final String[] pageItemTitles;

	@NonNull
	private final ViewPager2 viewPager;

	private FragmentAdapterCallback fragmentAdapterCallback;

	public PagerAdapter2UsingClasses(@NonNull Fragment fragment,
			@NonNull Class<? extends Fragment>[] pageItemClasses,
			@NonNull String[] pageItemTitles, @NonNull ViewPager2 viewPager) {
		super(fragment);
		fm = fragment.getChildFragmentManager();
		this.pageItemClasses = pageItemClasses;
		this.pageItemTitles = pageItemTitles;
		this.viewPager = viewPager;
		viewPager.registerOnPageChangeCallback(new ViewPager2PageChange());
		fragment.getLifecycle().addObserver(new DefaultLifecycleObserver() {
			@Override
			public void onResume(@NonNull LifecycleOwner owner) {
				Fragment frag = getCurrentFragment();
				if (frag instanceof FragmentPagerListener) {
					((FragmentPagerListener) frag).pageActivated();
				}
			}

			@Override
			public void onStop(@NonNull LifecycleOwner owner) {
				Fragment frag = getCurrentFragment();
				if (frag instanceof FragmentPagerListener) {
					((FragmentPagerListener) frag).pageDeactivated();
				}
			}
		});
	}

	@Override
	public long getItemId(int position) {
		return position >= 0 && position < pageItemClasses.length
				? pageItemClasses[position].hashCode() : super.getItemId(position);
	}

	@Override
	public boolean containsItem(long itemId) {
		for (Class<? extends Fragment> cla : pageItemClasses) {
			if (itemId == cla.hashCode()) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	@Contract("null -> false")
	public static boolean isFragInPageAdapter(Fragment fragment) {
		if (fragment == null) {
			return false;
		}
		Bundle arguments = fragment.getArguments();
		if (arguments == null) {
			return false;
		}
		return arguments.getBoolean(ARGKEY_FRAG_IN_PAGEADAPTER, false);
	}

	public void setFragmentAdapterCallback(
			FragmentAdapterCallback fragmentAdapterCallback) {
		this.fragmentAdapterCallback = fragmentAdapterCallback;
	}

	@NonNull
	@Override
	public Fragment createFragment(int position) {
		Class<? extends Fragment> cla = pageItemClasses[position];

		boolean needsActivate = false;// viewPager.getCurrentItem() == position;
		try {
			Bundle args = new Bundle();
			args.setClassLoader(cla.getClassLoader());
			args.putBoolean(ARGKEY_FRAG_IN_PAGEADAPTER, true);

			Fragment fragment = cla.newInstance();
			fragment.setArguments(args);

			if (fragmentAdapterCallback != null) {
				fragmentAdapterCallback.pagerAdapterFragmentCreated(fragment);
			}

			if (needsActivate) {
				fragment.getLifecycle().addObserver(
						new MyDefaultLifecycleObserver(fragment));
			}
			return fragment;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	@Override
	public int getItemCount() {
		return pageItemClasses.length;
	}

	@Nullable
	public Fragment findFragmentByPosition(int position, boolean onlyAdded) {
		if (position < 0 || position >= pageItemClasses.length) {
			return null;
		}
		Class<? extends Fragment> cla = pageItemClasses[position];
		List<Fragment> fragments = fm.getFragments();
		for (Fragment fragment : fragments) {
			if (fragment == null) {
				continue;
			}
			if (onlyAdded && !fragment.isAdded()) {
				continue;
			}
			if (cla.isInstance(fragment)) {
				return fragment;
			}
		}

		return null;
	}

	@Nullable
	public Fragment findFragmentByPosition(int position) {
		return findFragmentByPosition(position, true);
	}

	class ViewPager2PageChange
		extends ViewPager2.OnPageChangeCallback
	{
		int oldPosition = -1;

		@Override
		public void onPageSelected(int position) {
			if (oldPosition >= 0) {
				Fragment fragment = findFragmentByPosition(oldPosition);
				if (fragment instanceof FragmentPagerListener) {
					((FragmentPagerListener) fragment).pageDeactivated();
				}
			}
			Fragment fragment = findFragmentByPosition(position);
			if (fragment instanceof FragmentPagerListener) {
				((FragmentPagerListener) fragment).pageActivated();
			}
			oldPosition = position;
		}

	}

	public Fragment getCurrentFragment() {
		return findFragmentByPosition(viewPager.getCurrentItem());
	}

	public String getTitle(int position) {
		if (position < 0 || position >= pageItemTitles.length) {
			return "" + position;
		}
		return pageItemTitles[position];
	}
}
