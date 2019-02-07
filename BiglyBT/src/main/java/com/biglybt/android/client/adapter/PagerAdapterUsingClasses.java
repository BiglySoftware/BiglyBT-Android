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

import java.util.List;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.util.Log;

import eu.inloop.pager.UpdatableFragmentPagerAdapter;

/**
 * Created by TuxPaper on 10/12/18.
 */
public abstract class PagerAdapterUsingClasses
	extends UpdatableFragmentPagerAdapter
{
	private static final String ARGKEY_FRAG_IN_PAGEADAPTER = "fragInPageAdapter";

	private static final String KEY_FRAG_ID = "UFPA.id";

	private Class<? extends Fragment>[] pageItemClasses;

	private final FragmentManager fm;

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

	public PagerAdapterUsingClasses(@NonNull FragmentManager fm,
			Class<? extends Fragment>[] pageItemClasses) {
		super(fm);
		this.fm = fm;
		this.pageItemClasses = pageItemClasses;
	}

	public void setPageItemClasses(Class<? extends Fragment>[] pageItemClasses) {
		boolean invalidate = this.pageItemClasses != null;
		this.pageItemClasses = pageItemClasses;
		if (invalidate) {
			// TODO: Check if this really invalidates tabs
			notifyDataSetChanged();
		}
	}

	@Override
	public final int getCount() {
		return pageItemClasses == null ? 0 : pageItemClasses.length;
	}

	@Override
	public Fragment getItem(int position) {
		if (pageItemClasses == null) {
			return null;
		}
		// only called for creation
		if (position < 0 || position >= pageItemClasses.length) {
			return null;
		}
		Class<? extends Fragment> cla = pageItemClasses[position];

		try {
			// could use Fragment#instantiate but it requires Context to get class
			Bundle args = new Bundle();
			args.setClassLoader(cla.getClassLoader());

			args.putInt(KEY_FRAG_ID, cla.hashCode());
			args.putBoolean(ARGKEY_FRAG_IN_PAGEADAPTER, true);

			Fragment fragment = cla.newInstance();

			fragment.setArguments(args);

			return fragment;
		} catch (Throwable e) {
			Log.e("UFPA", "getItem: " + position, e);
		}

		return null;
	}

	@Override
	public int getItemPosition(@NonNull Object object) {
		if (pageItemClasses == null) {
			return POSITION_NONE;
		}
		for (int i = 0; i < pageItemClasses.length; i++) {
			Class<? extends Fragment> pageItemClass = pageItemClasses[i];
			if (pageItemClass.isInstance(object)) {
				return i;
			}
		}
		return POSITION_NONE;
	}

	@Override
	public long getItemId(int position) {
		if (pageItemClasses == null) {
			return super.getItemId(position);
		}
		return pageItemClasses[position].hashCode();
	}

	@Nullable
	public Fragment findFragmentByPosition(int position, boolean onlyAdded) {
		if (pageItemClasses == null) {
			return null;
		}
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

}