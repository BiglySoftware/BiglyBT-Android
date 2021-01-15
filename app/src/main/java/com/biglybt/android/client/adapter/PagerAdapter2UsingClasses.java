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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.FragmentM;
import com.biglybt.android.client.fragment.FragmentPagerListener;
import com.biglybt.util.Thunk;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
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
			if (owner instanceof Fragment) {
				Bundle args = ((Fragment) owner).getArguments();

				boolean isCurrenlyActivated;
				if (args != null && args.containsKey("pageActivated")) {
					isCurrenlyActivated = args.getBoolean("pageActivated", false);
					owner.getLifecycle().removeObserver(this);
				} else {
					isCurrenlyActivated = false;
				}

				// Fix bug in ViewPager2: fragments in non-visible tabs can gain focus
				// using D-Pad.  Fix by making non-visible fragments View.GONE.
				// Negative Side Effect: Sliding animation looks pretty boring.
				View view = ((Fragment) owner).getView();
				if (view != null) {
					view.setVisibility(isCurrenlyActivated ? View.VISIBLE : View.GONE);
				}
			}
		}

		@Override
		public void onResume(@NonNull LifecycleOwner owner) {
			// Case: onPageSelected can get called before createFragment, so
			//       we'll need to call pageActivated once the fragment is visible
			if (owner instanceof Fragment) {
				triggerPageActivationState((Fragment) owner, true);
			}
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
						//layoutTransition.setAnimateParentHierarchy(false);
						((ViewGroup) v).setLayoutTransition(null);
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
	private final Class<? extends Fragment>[] pageItemClasses;

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

		// Case: User opened another activity (ie. tasklist), and returns
		//       OnPageChangeCallback doesn't trigger (rightly so), but we want to
		//       send page (de)activations
		fragment.getLifecycle().addObserver(new DefaultLifecycleObserver() {
			@Override
			public void onResume(@NonNull LifecycleOwner owner) {
				Fragment currentFragment = getCurrentFragment();
				// first resume will have no currentFragment. trigger will be done when 
				// fragment is created and shown
				if (currentFragment != null) {
					triggerPageActivationState(currentFragment, true);
				}
			}

			@Override
			public void onStop(@NonNull LifecycleOwner owner) {
				triggerPageActivationState(getCurrentFragment(), false);
			}
		});

		// Fix Bug in ViewPager2: RecyclerViewImpl is focusable, so if you have
		// a TabItem focused, and press down, the focus will be on it, and from
		// the users's perspective, the focus will be "gone"
		ArrayList<View> rvList = AndroidUtilsUI.findByClass(viewPager,
				RecyclerView.class, null);
		if (rvList.size() > 0) {
			rvList.get(0).setFocusable(false);
		}
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
		if (fragment == null) {
			if (AndroidUtils.DEBUG) {
				Log.println(Log.WARN, TAG, "triggerPageActivationState: null fragment; "
					+ AndroidUtils.getCompressedStackTrace());
			}
			return;
		}
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
		if (AndroidUtils.DEBUG_LIFECYCLE && (fragment instanceof FragmentM)) {
			((FragmentM) fragment).log(TAG,
					"triggerPageActivationState: page"
							+ (activated ? "Activated" : "Deactivated") + "; "
							+ AndroidUtils.getCompressedStackTrace());
		}
		args.putBoolean("pageActivated", activated);

		// Fix bug in ViewPager2: fragments in non-visible tabs can gain focus
		// using D-Pad.  Fix by making non-visible fragments View.GONE.
		// Negative Side Effect: Sliding animation looks pretty boring.
		View view = fragment.getView();
		if (view != null) {
			view.setVisibility(activated ? View.VISIBLE : View.GONE);
		}

		if (fragment instanceof FragmentPagerListener) {
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

			// Note: We can get a onPageSelected trigger before the fragment is created
			Fragment fragment = findFragmentByPosition(position);
			if (fragment != null) {
				triggerPageActivationState(fragment, true);
			} else if (AndroidUtils.DEBUG_LIFECYCLE) {
				Log.println(Log.WARN, TAG,
						"triggerPageActivationState: null fragment ("
								+ findFragmentByPosition(position, false) + "); pos=" + position
								+ "; " + AndroidUtils.getCompressedStackTrace());
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
