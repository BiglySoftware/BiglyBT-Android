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

package com.biglybt.android.client.activity;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;
import com.biglybt.android.client.dialog.DialogFragmentAbstractLocationPicker.LocationPickerListener;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker.NumberPickerDialogListener;
import com.biglybt.android.client.fragment.SettingsFragmentLB;
import com.biglybt.android.util.PathInfo;

public class SettingsActivityLB
	extends SettingsActivity
{
	private Fragment frag;

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {

		if (frag instanceof NumberPickerDialogListener) {
			((NumberPickerDialogListener) frag).onNumberPickerChange(callbackID, val);
		} else {
			super.onNumberPickerChange(callbackID, val);
		}
	}

	@Override
	public void locationChanged(String callbackID, @NonNull PathInfo location) {
		if (frag instanceof LocationPickerListener) {
			((LocationPickerListener) frag).locationChanged(callbackID, location);
		} else {
			super.locationChanged(callbackID, location);
		}
	}


	@Override
	public int getThemeId() {
		return 0; // Use AndroidManifest's value
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	protected void onCreateWithSession(@Nullable Bundle savedInstanceState) {
		if (savedInstanceState != null) {
			return;
		}
		frag = new SettingsFragmentLB();
		getSupportFragmentManager().beginTransaction().replace(android.R.id.content,
				frag).commit();
	}
}