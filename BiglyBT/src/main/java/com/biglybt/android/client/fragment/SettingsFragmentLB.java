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

import java.util.Stack;

import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker;

import android.app.Fragment;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragment;
import androidx.leanback.preference.LeanbackSettingsFragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

public class SettingsFragmentLB
	extends LeanbackSettingsFragment
	implements DialogFragmentNumberPicker.NumberPickerDialogListener,
	DialogPreference.TargetFragment
{
	protected final Stack<Fragment> fragments = new Stack<>();

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
				(SessionActivity) getActivity());
		startPreferenceFragment(buildPreferenceFragment(prefID, null));
	}

	@Override
	public boolean onPreferenceStartFragment(
			PreferenceFragment preferenceFragment, Preference preference) {
		return false;
	}

	@Override
	public boolean onPreferenceStartScreen(PreferenceFragment preferenceFragment,
			PreferenceScreen preferenceScreen) {
		int prefID = PrefFragmentHandlerCreator.getPrefID(
				(SessionActivity) getActivity());
		PreferenceFragment frag = buildPreferenceFragment(prefID,
				preferenceScreen.getKey());
		startPreferenceFragment(frag);
		return true;
	}

	@Override
	public Preference findPreference(CharSequence prefKey) {
		return ((PreferenceFragment) fragments.peek()).findPreference(prefKey);
	}

	private static PreferenceFragment buildPreferenceFragment(int preferenceResId,
			String root) {
		PreferenceFragment fragment = new PrefFragmentLB();
		Bundle args = new Bundle();
		args.putInt("preferenceResource", preferenceResId);
		args.putString("root", root);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		if (prefFragment instanceof PrefFragmentLB) {
			((PrefFragmentLB) prefFragment).onNumberPickerChange(callbackID, val);
		}
	}

}