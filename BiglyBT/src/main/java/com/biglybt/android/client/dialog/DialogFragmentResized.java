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

package com.biglybt.android.client.dialog;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Resize DialogFragment to Width/Height
 * <p/>
 * Typically, a dialog will expand its height based on the widgets.  However, the width tends to
 * not expand beyond a certain percentage of screen width, and must be tweaked to use more width.
 * <p/>
 * Created by TuxPaper on 8/5/16.
 */
public abstract class DialogFragmentResized
	extends DialogFragmentBase
{
	private static final String TAG = "DialogFragmentResized";

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	@DimenRes
	private int dialogWidthRes = View.NO_ID;

	@DimenRes
	private int dialogHeightRes = View.NO_ID;

	private static final int[] IDS_TO_COUNT_HEIGHT = new int[] {
		R.id.topPanel,
		R.id.contentPanel,
		R.id.buttonPanel
	};

	private static final int[] IDS_TO_SET_MATCH_PARENT = new int[] {
		R.id.parentPanel,
		R.id.customPanel,
		R.id.custom
	};

	@SuppressWarnings("WeakerAccess")
	public void setDialogHeightRes(@DimenRes int dialogHeightRes) {
		this.dialogHeightRes = dialogHeightRes;
	}

	@SuppressWarnings("WeakerAccess")
	public void setDialogWidthRes(@DimenRes int dialogWidthRes) {
		this.dialogWidthRes = dialogWidthRes;
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		resize(getDialog());
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		resize(getDialog());
	}

	@Override
	public void onResume() {
		super.onResume();
		resize(getDialog());
	}

	@NonNull
	@Override
	public LayoutInflater onGetLayoutInflater(
			@Nullable Bundle savedInstanceState) {
		LayoutInflater layoutInflater = super.onGetLayoutInflater(
				savedInstanceState);
		// API 15 needed this because sometimes onResume isn't called, and onAttach has null dialog
		// (Switch theme and open dialog on small device, and window won't resize without this)
		resize(getDialog());
		return layoutInflater;
	}

	private void resize(Dialog dialog) {
		try {
			if (DEBUG) {
				Log.d(TAG, "resize: " + AndroidUtils.getCompressedStackTrace());
			}

			if (dialog == null
					|| (dialogWidthRes == View.NO_ID && dialogHeightRes == View.NO_ID)) {
				return;
			}
			Window window = dialog.getWindow();
			if (window == null) {
				return;
			}

			Resources resources = getResources();

			View rootView = window.getDecorView().getRootView();
			if (DEBUG) {
				//AndroidUtilsUI.walkTree(rootView, "");
				// FrameLayout -> FrameLayout -> id/action_bar_root -> id/content  -> id/parentPanel -> [ id/topPanel, id/contentPanel, id/customPanel, id/buttonPanel]
				// id/topPanel -> [ id/title_template, id/titleDividerNoCustom ]
				// id/contentPanel -> [ id/scrollIndicatorUp. id/scrollView (..title) ]
				// id/customPanel -> id/custom -> actual inflated view
				// id/buttonPanel -> ButtonBarLayout

				Log.d(TAG, "resize: View: " + getView());
			}

			// topPanel + contentPanel + buttonPanel = View size without us
			int heightWithoutUs = 0;
			for (int id : IDS_TO_COUNT_HEIGHT) {
				ViewGroup vg = rootView.findViewById(id);
				if (vg != null) {
					int measuredHeight = vg.getMeasuredHeight();
					if (measuredHeight == 0) {
						vg.measure(View.MeasureSpec.UNSPECIFIED,
								View.MeasureSpec.UNSPECIFIED);
						measuredHeight = vg.getMeasuredHeight();
					}
					heightWithoutUs += measuredHeight;
					//Log.d(TAG, resources.getResourceName(id) + ": " + measuredHeight);
				}
			}

			for (int id : IDS_TO_SET_MATCH_PARENT) {
				try {
					ViewGroup vg = rootView.findViewById(id);
					if (vg == null) {
						continue;
					}

					ViewGroup.LayoutParams layoutParams = vg.getLayoutParams();
					if (layoutParams != null) {
						layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
						layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;

						vg.setLayoutParams(layoutParams);
					}
				} catch (Throwable ignore) {
				}
			}

			Point maxSize = AndroidUtilsUI.getContentAreaSize(requireActivity());

			int w;
			if (dialogWidthRes == View.NO_ID) {
				w = ViewGroup.LayoutParams.WRAP_CONTENT;
			} else {
				w = Math.min(
					AndroidUtilsUI.getResourceValuePX(resources, dialogWidthRes),
					maxSize.x);
				// Prevent really narrow dialogs
				if (w < maxSize.x / 2) {
					w = ViewGroup.LayoutParams.WRAP_CONTENT;
				}
			}
			int h;
			if (dialogHeightRes == View.NO_ID) {
				h = ViewGroup.LayoutParams.WRAP_CONTENT;
			} else {
				h = AndroidUtilsUI.getResourceValuePX(resources, dialogHeightRes);
				if (h > 0) {
					h += heightWithoutUs;
					if (h > maxSize.y) {
						h = maxSize.y;
					}
				}
			}
			window.setLayout(w, h);
		} catch (Throwable t) {
			Log.e(TAG, "resize", t);
		}
	}
}
