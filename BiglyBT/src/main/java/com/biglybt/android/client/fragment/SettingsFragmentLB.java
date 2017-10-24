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
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v14.preference.PreferenceFragment;
import android.support.v17.preference.LeanbackSettingsFragment;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

public class SettingsFragmentLB
	extends LeanbackSettingsFragment
	implements DialogFragmentNumberPicker.NumberPickerDialogListener,
	DialogPreference.TargetFragment
{
	private final static String TAG = "SettingsFragmentLB";

	protected final Stack<Fragment> fragments = new Stack<>();

	private Fragment prefFragment;

	private int prefID;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		prefID = PrefFragmentHandlerCreator.getPrefID(
				(SessionActivity) getActivity());
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void startPreferenceFragment(@NonNull Fragment fragment) {
		prefFragment = fragment;
		super.startPreferenceFragment(fragment);
	}

	@Override
	public void onPreferenceStartInitialScreen() {
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
		PreferenceFragment frag = buildPreferenceFragment(prefID,
				preferenceScreen.getKey());
		startPreferenceFragment(frag);
		return true;
	}

	@Override
	public Preference findPreference(CharSequence prefKey) {
		return ((PreferenceFragment) fragments.peek()).findPreference(prefKey);
	}

	private PreferenceFragment buildPreferenceFragment(int preferenceResId,
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