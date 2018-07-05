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

import com.biglybt.android.client.R;
import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.activity.SettingsActivity;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker;
import com.biglybt.android.client.session.SessionManager;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.widget.Toolbar;

public class SettingsFragmentM
	extends PreferenceFragmentCompat
	implements DialogFragmentNumberPicker.NumberPickerDialogListener
{

	private PrefFragmentHandler prefFragmentHandler;

	@Override
	public void onDestroyView() {
		if (prefFragmentHandler != null) {
			prefFragmentHandler.onDestroy();
		}
		super.onDestroyView();
	}

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		prefFragmentHandler.onNumberPickerChange(callbackID, val);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (prefFragmentHandler != null) {
			prefFragmentHandler.onResume();
		}
		FragmentActivity activity = getActivity();
		if (activity != null) {
			Toolbar toolbar = activity.findViewById(R.id.actionbar);
			if (toolbar != null) {
				toolbar.setTitle(getPreferenceScreen().getTitle());
			}
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
	public boolean onPreferenceTreeClick(Preference preference) {
		if (prefFragmentHandler.onPreferenceTreeClick(preference)) {
			return true;
		}

		return super.onPreferenceTreeClick(preference);
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		int prefID = PrefFragmentHandlerCreator.getPrefID(
				(SessionActivity) requireActivity());
		final Bundle arguments = getArguments();
		if (arguments != null) {
			String key = arguments.getString(SettingsActivity.TARGET_SETTING_PAGE);
			if (key != null) {
				setPreferencesFromResource(prefID, key);
				return;
			}
		}

		setPreferencesFromResource(prefID, rootKey);
	}

	@Override
	public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
		super.setPreferenceScreen(preferenceScreen);
		prefFragmentHandler = PrefFragmentHandlerCreator.createPrefFragment(
				(SessionActivity) requireActivity());
		prefFragmentHandler.setPreferenceScreen(getPreferenceManager(),
				preferenceScreen);
	}

	@Override
	public void onNavigateToScreen(PreferenceScreen preferenceScreen) {
		final SessionActivity activity = (SessionActivity) requireActivity();
		Intent intent = new Intent(activity, SettingsActivity.class).putExtra(
				SettingsActivity.TARGET_SETTING_PAGE,
				preferenceScreen.getKey()).putExtra(SessionManager.BUNDLE_KEY,
						activity.getRemoteProfileID());
		startActivity(intent);

		super.onNavigateToScreen(preferenceScreen);
	}
}