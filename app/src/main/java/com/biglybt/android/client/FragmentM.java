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

package com.biglybt.android.client;

import static com.biglybt.android.client.AndroidUtils.DEBUG_LIFECYCLE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

/**
 * Fragment with handy logging
 *
 * Created by TuxPaper on 3/18/16.
 */
public class FragmentM
	extends Fragment
{
	private String classSimpleName;

	private boolean isFragmentVisible;

	@Override
	public void onPause() {
		if (DEBUG_LIFECYCLE) {
			log("Lifecycle", "onPause");
		}
		super.onPause();
	}

	@Override
	public void onResume() {
		if (DEBUG_LIFECYCLE) {
			log("Lifecycle", "onResume");
		}
		super.onResume();
		if (!isFragmentVisible) {
			isFragmentVisible = true;
			onShowFragment();
		}
	}

	/**
	 * Fragment is no longer visible.
	 *
	 * @implSpec When in multi-window mode, triggered via onStop.  Otherwise
	 *           triggered via onPause
	 */
	protected void onHideFragment() {
		if (DEBUG_LIFECYCLE) {
			log("Lifecycle",
					"onHideFragment via " + AndroidUtils.getCompressedStackTrace(3));
		}
	}

	/**
	 * Fragment is now visible to user
	 */
	protected void onShowFragment() {
		if (DEBUG_LIFECYCLE) {
			log("Lifecycle", "onShowFragment");
		}
	}

	public boolean isFragmentVisible() {
		return isFragmentVisible;
	}

	@Override
	public void onStart() {
		super.onStart();
		if (DEBUG_LIFECYCLE) {
			log("Lifecycle", "onStart");
		}
		AnalyticsTracker.getInstance(this).fragmentResume(this);
	}

	@Override
	public void onAttach(@NonNull Context context) {
		if (DEBUG_LIFECYCLE) {
			log("Lifecycle", "onAttach to " + context);
		}
		super.onAttach(context);
	}

	@Override
	public void onStop() {
		if (isFragmentVisible) {
			isFragmentVisible = false;
			onHideFragment();
		}

		super.onStop();
		AnalyticsTracker.getInstance(this).fragmentPause(this);
		if (DEBUG_LIFECYCLE) {
			log("Lifecycle", "onStop");
		}
	}

	@Override
	public void onDestroy() {
		if (DEBUG_LIFECYCLE) {
			log("Lifecycle", "onDestroy");
		}
		super.onDestroy();
	}

	@SuppressLint("LogConditional")
	public void log(String TAG, String s) {
		if (classSimpleName == null) {
			classSimpleName = AndroidUtils.getSimpleName(getClass()) + "@"
					+ Integer.toHexString(hashCode());
		}
		Log.d(classSimpleName, TAG + ": " + s);
	}

	@SuppressLint("LogConditional")
	public void log(int prority, String TAG, String s) {
		if (classSimpleName == null) {
			classSimpleName = AndroidUtils.getSimpleName(getClass()) + "@"
					+ Integer.toHexString(hashCode());
		}
		Log.println(prority, classSimpleName, TAG + ": " + s);
	}
}
