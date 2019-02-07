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

import com.astuetz.PagerSlidingTabStrip;
import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.SetTorrentIdListener;
import com.biglybt.android.client.fragment.FragmentPagerListener;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.util.Log;

/**
 * PagerAdapter for a torrent.  Links up {@link ViewPager}, 
 * {@link PagerSlidingTabStrip} and adapter.
 * <p>
 * Any page Fragments that implement {@link FragmentPagerListener} will
 * get notified of activation/deactivation.
 */
public abstract class TorrentPagerAdapter
	extends PagerAdapterForPagerSlidingTabStrip
{

	private static final String TAG = "TorrentPagerAdapter";

	private long torrentID = -1;

	TorrentPagerAdapter(final FragmentManager fragmentManager,
			Lifecycle lifecycle, Class<? extends Fragment>... pageItemClasses) {
		super(fragmentManager, lifecycle, pageItemClasses);
	}

	/**
	 * Return the Fragment associated with a specified position.
	 * <p/>
	 * Only gets called once by {@link FragmentStatePagerAdapter} when creating
	 * fragment.
	 */
	@Override
	public final Fragment getItem(int position) {
		Fragment fragment = super.getItem(position);

		if (fragment == null) {
			return null;
		}

		fragment.getLifecycle().addObserver(new LifecycleObserver() {
			@OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
			public void onResume() {
				// Does this get called after screen rotation?
				if (AndroidUtils.DEBUG) {
					Log.i(TAG, "onResume: GOT CALLED for tab " + position + "; torrentID="
							+ torrentID);
				}
				if (fragment instanceof SetTorrentIdListener) {
					((SetTorrentIdListener) fragment).setTorrentID(torrentID);
				}
			}
		});

		return fragment;
	}

	public void setSelection(long torrentID) {
		this.torrentID = torrentID;
	}

	/* Since we are now using UpdatableFragmentPagerAdapter, let's assume
	 * TransactionTooLargeException is fixed 
	@Override
	public Parcelable saveState() {
		// Fix TransactionTooLargeException (See Solve 1 at https://medium.com/@mdmasudparvez/android-os-transactiontoolargeexception-on-nougat-solved-3b6e30597345 )
		Bundle bundle = (Bundle) super.saveState();
		if (bundle != null) {
			bundle.putParcelableArray("states", null); // Never maintain any states from the base class, just null it out
		}
		return bundle;
	}
	*/
}
