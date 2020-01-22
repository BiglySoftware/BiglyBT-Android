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

import com.biglybt.android.client.AnalyticsTracker;

import android.app.Dialog;

import androidx.fragment.app.DialogFragment;

public abstract class DialogFragmentBase
	extends DialogFragment
{
	@Override
	public void onResume() {
		super.onResume();
		AnalyticsTracker.getInstance(this).fragmentResume(this);
	}

	@Override
	public void onPause() {
		super.onPause();
		AnalyticsTracker.getInstance(this).fragmentPause(this);
	}

	public void dismissDialog() {
		Dialog dialog = getDialog();
		if (dialog == null) {
			return;
		}
		dialog.dismiss();
	}
	
	public void cancelDialog(){
		Dialog dialog = getDialog();
		if (dialog == null) {
			return;
		}
		dialog.cancel();
	}
}
