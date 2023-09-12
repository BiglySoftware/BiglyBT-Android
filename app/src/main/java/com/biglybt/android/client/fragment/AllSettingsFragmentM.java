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

package com.biglybt.android.client.fragment;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.biglybt.android.client.dialog.DialogFragmentAbstractLocationPicker.LocationPickerListener;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker.NumberPickerDialogListener;
import com.biglybt.android.util.PathInfo;

public class AllSettingsFragmentM
	extends PreferenceFragmentCompat
	implements NumberPickerDialogListener, LocationPickerListener
{

	private AllPrefFragmentHandler handler;

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		if (handler != null) {
			handler.onSaveInstanceState(outState);
		}
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		PreferenceManager preferenceManager = getPreferenceManager();
		if (preferenceManager == null) {
			return;
		}

		handler = new AllPrefFragmentHandler(this, preferenceManager,
				savedInstanceState, rootKey);
		setPreferenceScreen(handler.getPreferenceScreen());
	}

	@Override
	public Fragment getCallbackFragment() {
		return this;
	}

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		if (handler != null) {
			handler.onNumberPickerChange(callbackID, val);
		}
	}

	@Override
	public void locationChanged(String callbackID, @NonNull PathInfo location) {
		if (handler != null) {
			handler.locationChanged(callbackID, location);
		}
	}
}
