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

import android.animation.LayoutTransition;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.FragmentM;
import com.biglybt.android.client.fragment.FragmentPagerListener;
import com.biglybt.util.Thunk;

import org.jetbrains.annotations.Contract;

import java.util.List;

/**
 * Created by TuxPaper on 3/14/20.
 */
public class PagerAdapter2UsingClasses
	extends FragmentStateAdapter
{

	private static final String ARGKEY_FRAG_IN_PAGEADAPTER = "fragInPageAdapter";

	private static final String TAG = "PagerAdapter2UC";

	@Thunk
	static class MyDefaultLifecycleObserver
		implements DefaultLifecycleObserver
	{
		@Override
		public void onCreate(@NonNull LifecycleOwner owner) {
			removeAnimateParent(owner);
		}

		@Override
		public void onStart(@NonNull LifecycleOwner owner) {
			removeAnimateParent(owner);
			owner.getLifecycle().removeObserver(this);
		}

		private static void removeAnimateParent(LifecycleOwner fragment) {
			if (!(fragment instanceof Fragment)) {
				return;
			}
			AndroidUtilsUI.walkTree(((Fragment) fragment).getView(), (v) -> {
				if (v instanceof ViewGroup) {
					LayoutTransition layoutTransition = ((ViewGroup) v).getLayoutTransition();
					if (layoutTransition != null) {
						//log("TAG", "setAnimateParentHierarchy " + v);
						layoutTransition.setAnimateParentHierarchy(false);
					}
				}
			});
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
				triggerPageActivationState(getCurrentFragment(), true);
			}

			@Override
			public void onStop(@NonNull LifecycleOwner owner) {
				triggerPageActivationState(getCurrentFragment(), false);
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

		try {
			Bundle args = new Bundle();
			args.setClassLoader(cla.getClassLoader());
			args.putBoolean(ARGKEY_FRAG_IN_PAGEADAPTER, true);

			Fragment fragment = cla.newInstance();
			fragment.setArguments(args);

			if (fragmentAdapterCallback != null) {
				fragmentAdapterCallback.pagerAdapterFragmentCreated(fragment);
			}

			fragment.getLifecycle().addObserver(new MyDefaultLifecycleObserver());
			return fragment;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	@Thunk
	static void triggerPageActivationState(@Nullable Fragment fragment,
			boolean activated) {
		if (fragment instanceof FragmentPagerListener) {
			Bundle args = fragment.getArguments();
			if (args == null) {
				args = new Bundle();
			}
			boolean isCurrenlyActivated = args.getBoolean("pageActivated", false);
			if (activated == isCurrenlyActivated) {
				if (AndroidUtils.DEBUG && (fragment instanceof FragmentM)) {
					((FragmentM) fragment).log(TAG,
							"triggerPageActivationState: already page"
									+ (activated ? "Activated" : "Deactivated") + "; "
									+ AndroidUtils.getCompressedStackTrace());
				}
				return;
			}
			args.putBoolean("pageActivated", activated);
			if (activated) {
				((FragmentPagerListener) fragment).pageActivated();
			} else {
				((FragmentPagerListener) fragment).pageDeactivated();
			}
		} else if (AndroidUtils.DEBUG_LIFECYCLE
				&& (fragment instanceof FragmentM)) {
			((FragmentM) fragment).log(TAG, "Not FragmentPagerListener; Skipping page"
					+ (activated ? "Activated" : "Deactivated"));
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
			if (position == oldPosition) {
				return;
			}

			if (oldPosition >= 0) {
				triggerPageActivationState(findFragmentByPosition(oldPosition), false);
			}

			triggerPageActivationState(findFragmentByPosition(position), true);

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
