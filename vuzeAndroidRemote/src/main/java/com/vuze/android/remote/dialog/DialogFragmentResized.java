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

package com.vuze.android.remote.dialog;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.AndroidUtilsUI;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

/**
 * Resized DialogFragment to a Minimum Width
 *
 * Created by TuxPaper on 8/5/16.
 */
public abstract class DialogFragmentResized
		extends DialogFragmentBase
{
	private static final String TAG = "DialogFragmentResized";

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private int minWidthPX;

	public void setMinWidthPX(int px) {
		minWidthPX = px;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		resize(getDialog());
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		resize(getDialog());
	}


	@Override
	public void onResume() {
		super.onResume();
		resize(getDialog());
	}


	private void resize(Dialog dialog) {
		try {
			if (DEBUG) {
				Log.d(TAG, "resize: " + AndroidUtils.getCompressedStackTrace());
			}

			if (dialog == null || minWidthPX == 0) {
				return;
			}
			Window window = dialog.getWindow();
			View rootView = window.getDecorView().getRootView();
			if (DEBUG) {
				AndroidUtilsUI.walkTree(rootView, "");

				Log.d(TAG, "resize: View: " + getView());
			}

			int resourceId = Resources
					.getSystem().getIdentifier("parentPanel", "id", "android");
			if (DEBUG) {
				Log.d(TAG, "resize: " + resourceId);
			}

			View viewById = rootView.findViewById(resourceId);
			if (viewById != null) {
				ViewGroup.LayoutParams layoutParams = viewById.getLayoutParams();
				layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;

				viewById.setLayoutParams(layoutParams);
			}

			int w = minWidthPX;

			window.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
		} catch (Throwable t) {
			Log.e(TAG, "resize", t);
		}
	}
}
