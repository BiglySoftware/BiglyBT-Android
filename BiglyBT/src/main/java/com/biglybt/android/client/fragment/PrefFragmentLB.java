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

import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;

/**
 * Created by TuxPaper on 10/22/17.
 */
public class PrefFragmentLB
	extends LeanbackPreferenceFragment
	implements DialogFragmentNumberPicker.NumberPickerDialogListener
{

	private static final String TAG = "SettingsFragmentLB";

	private PrefFragmentHandler prefFragmentHandler;

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		prefFragmentHandler.onNumberPickerChange(callbackID, val);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		prefFragmentHandler = PrefFragmentHandlerCreator.createPrefFragment((SessionActivity) getActivity());
		super.onCreate(savedInstanceState);

		prefFragmentHandler.onCreate(savedInstanceState, getPreferenceManager());
	}

	@Override
	public void onDestroy() {
		if (prefFragmentHandler != null) {
			prefFragmentHandler.onDestroy();
		}
		super.onDestroy();
	}

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		String root = getArguments().getString("root", null);
		int prefResId = getArguments().getInt("preferenceResource");
		if (root == null) {
			addPreferencesFromResource(prefResId);
		} else {
			setPreferencesFromResource(prefResId, root);
		}
	}

	@Override
	public boolean onPreferenceTreeClick(Preference preference) {
		if (prefFragmentHandler.onPreferenceTreeClick(preference)) {
			return true;
		}

		return super.onPreferenceTreeClick(preference);
	}

	@Override
	public void onAttach(Context context) {
		SettingsFragmentLB settingsFragmentLB = (SettingsFragmentLB) getParentFragment();
		settingsFragmentLB.fragments.push(this);
		super.onAttach(context);
	}

	@Override
	public void onDetach() {
		SettingsFragmentLB settingsFragmentLB = (SettingsFragmentLB) getParentFragment();
		settingsFragmentLB.fragments.pop();
		super.onDetach();
	}

}
