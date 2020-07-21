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
import androidx.annotation.UiThread;
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.dialog.DialogFragmentAbstractLocationPicker.LocationPickerListener;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker;
import com.biglybt.android.util.FileUtils.PathInfo;

/**
 * Created by TuxPaper on 10/22/17.
 */
public class PrefFragmentLB
	extends LeanbackPreferenceFragmentCompat
	implements DialogFragmentNumberPicker.NumberPickerDialogListener,
	LocationPickerListener
{
	private PrefFragmentHandler prefFragmentHandler;

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		if (prefFragmentHandler != null) {
			prefFragmentHandler.onNumberPickerChange(callbackID, val);
		}
	}

	@Override
	public void locationChanged(String callbackID, @NonNull PathInfo location) {
		if (prefFragmentHandler != null) {
			prefFragmentHandler.locationChanged(location);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (prefFragmentHandler != null) {
			prefFragmentHandler.onResume();
		}
	}

	@Override
	public void onPause() {
		if (prefFragmentHandler != null) {
			prefFragmentHandler.onPreferenceScreenClosed(getPreferenceScreen());
		}
		super.onPause();
	}

	@Override
	public void onCreatePreferences(Bundle bundle, String rootKey) {
		// this will trigger {@link #setPreferenceScreen}
		Bundle arguments = getArguments();
		if (arguments == null) {
			throw new IllegalStateException("No arguments");
		}
		int prefResId = arguments.getInt("preferenceResource");
		setPreferencesFromResource(prefResId, rootKey);
	}

	@Override
	public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
		prefFragmentHandler = PrefFragmentHandlerCreator.createPrefFragment(
				(SessionActivity) requireActivity(), this);
		super.setPreferenceScreen(preferenceScreen);
		//noinspection ConstantConditions /* PreferenceManager initialized in onCreate */
		prefFragmentHandler.setPreferenceScreen(getPreferenceManager(),
				preferenceScreen);
	}

	@Override
	@UiThread
	public boolean onPreferenceTreeClick(Preference preference) {
		if (prefFragmentHandler != null
				&& prefFragmentHandler.onPreferenceTreeClick(preference)) {
			return true;
		}

		return super.onPreferenceTreeClick(preference);
	}
}
