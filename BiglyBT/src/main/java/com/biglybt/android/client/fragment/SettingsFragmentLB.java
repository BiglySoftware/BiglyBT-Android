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
import androidx.leanback.preference.LeanbackSettingsFragmentCompat;
import androidx.preference.*;

import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.dialog.DialogFragmentAbstractLocationPicker.LocationPickerListener;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker;

public class SettingsFragmentLB
	extends LeanbackSettingsFragmentCompat
	implements DialogFragmentNumberPicker.NumberPickerDialogListener,
	LocationPickerListener
{
	private Fragment prefFragment;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void startPreferenceFragment(@NonNull Fragment fragment) {
		prefFragment = fragment;
		super.startPreferenceFragment(fragment);
	}

	@Override
	public void onPreferenceStartInitialScreen() {
		int prefID = PrefFragmentHandlerCreator.getPrefID(
				(SessionActivity) requireActivity());
		startPreferenceFragment(buildPreferenceFragment(prefID, null));
	}

	@Override
	public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller,
			Preference preference) {
		// Copied from LeanbackSettingsFragmentCompat JavaDoc (https://developer.android.com/reference/androidx/leanback/preference/LeanbackSettingsFragmentCompat)
		final Bundle args = preference.getExtras();
		final Fragment f = getChildFragmentManager().getFragmentFactory().instantiate(
				requireActivity().getClassLoader(), preference.getFragment());
		f.setArguments(args);
		f.setTargetFragment(caller, 0);
		if (f instanceof PreferenceFragmentCompat
				|| f instanceof PreferenceDialogFragmentCompat) {
			startPreferenceFragment(f);
		} else {
			startImmersiveFragment(f);
		}
		return true;
	}

	@Override
	public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller,
			PreferenceScreen preferenceScreen) {
		int prefID = PrefFragmentHandlerCreator.getPrefID(
				(SessionActivity) requireActivity());
		PreferenceFragmentCompat frag = buildPreferenceFragment(prefID,
				preferenceScreen.getKey());
		startPreferenceFragment(frag);
		return true;
	}

	private static PreferenceFragmentCompat buildPreferenceFragment(
			int preferenceResId, String root) {
		PreferenceFragmentCompat fragment = new PrefFragmentLB();
		Bundle args = new Bundle();
		args.putInt("preferenceResource", preferenceResId);
		args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, root);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		if (prefFragment instanceof PrefFragmentLB) {
			((PrefFragmentLB) prefFragment).onNumberPickerChange(callbackID, val);
		}
	}

	@Override
	public void locationChanged(String location) {
		if (prefFragment instanceof PrefFragmentLB) {
			((PrefFragmentLB) prefFragment).locationChanged(location);
		}
	}
}