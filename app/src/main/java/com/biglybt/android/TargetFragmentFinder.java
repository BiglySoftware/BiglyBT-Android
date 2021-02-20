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

package com.biglybt.android;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.biglybt.android.client.AndroidUtils;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Created by TuxPaper on 5/10/18.
 */
public class TargetFragmentFinder<T>
{
	private final Class cla;

	public TargetFragmentFinder(Class cla) {
		this.cla = cla;
	}

	@SuppressWarnings("unchecked")
	public T findTarget(@NonNull Fragment fragment, @Nullable Context context) {
		T mListener = null;
		Fragment targetFragment = fragment.getTargetFragment();
		if (targetFragment != null
				&& cla.isAssignableFrom(targetFragment.getClass())) {
			mListener = (T) targetFragment;
		} else if (context != null && cla.isAssignableFrom(context.getClass())) {
			mListener = (T) context;
		} else {
			if (AndroidUtils.DEBUG) {
				Log.w("TF",
						"findTarget: No targetFragment or valid context.  Scanning");
			}
			FragmentActivity activity = fragment.getActivity();
			if (activity == null) {
				Log.e("TF", "No activity for " + fragment + "/" + context);
				return null;
			}

			// can't use targetFragment for non-appcompat Fragment -- need to
			// poke around for a fragment with a listener, or use some other
			// communication mechanism
			android.app.FragmentManager fragmentManager = activity.getFragmentManager();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				Object pFragment = fragmentManager.getPrimaryNavigationFragment();
				if (pFragment != null && cla.isAssignableFrom(pFragment.getClass())) {
					mListener = (T) pFragment;
				}
			} else {
				try {
					Field field = fragmentManager.getClass().getDeclaredField("mActive");
					field.setAccessible(true);
					@SuppressWarnings("unchecked")
					List<android.app.Fragment> listActive = (List<android.app.Fragment>) field.get(
							fragmentManager);
					for (android.app.Fragment activeFragment : listActive) {
						if (activeFragment != null
								&& cla.isAssignableFrom(activeFragment.getClass())) {
							mListener = (T) activeFragment;
							break;
						}
					}
				} catch (Exception ignore) {
					ignore.printStackTrace();
				}
			}

			if (mListener == null) {
				FragmentManager supportFragmentManager = activity.getSupportFragmentManager();
				Object pFragment = supportFragmentManager.getPrimaryNavigationFragment();
				if (pFragment != null && cla.isAssignableFrom(pFragment.getClass())) {
					mListener = (T) pFragment;
				} else {
					List<Fragment> fragments = supportFragmentManager.getFragments();
					for (Fragment frag : fragments) {
						if (frag != null && cla.isAssignableFrom(frag.getClass())) {
							mListener = (T) frag;
							break;
						}
					}
				}
			}
			if (mListener == null) {
				Log.e("TF", "No Target Fragment for " + fragment + "/" + context);
			}
		}
		return mListener;
	}

}
