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
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.widget.Toolbar;
import android.util.Log;

public class SettingsFragmentM
	extends PreferenceFragmentCompat
	implements DialogFragmentNumberPicker.NumberPickerDialogListener
{
	private Session session;

	private PrefFragmentHandler prefFragmentHandler;

	private int prefID;

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
	public void onCreate(Bundle savedInstanceState) {
		SessionActivity activity = (SessionActivity) getActivity();
		prefFragmentHandler = PrefFragmentHandlerCreator.createPrefFragment(
				activity);
		prefID = PrefFragmentHandlerCreator.getPrefID(activity);
		super.onCreate(savedInstanceState);
		prefFragmentHandler.onCreate(savedInstanceState, getPreferenceManager());
	}

	@Override
	public void onResume() {
		super.onResume();
		if (prefFragmentHandler != null) {
			prefFragmentHandler.onResume();
		}
		Toolbar toolbar = getActivity().findViewById(R.id.actionbar);
		if (toolbar != null) {
			toolbar.setTitle(getPreferenceScreen().getTitle());
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
		prefFragmentHandler.setPreferenceScreen(preferenceScreen);
	}

	@Override
	public void onNavigateToScreen(PreferenceScreen preferenceScreen) {
		final SessionActivity activity = (SessionActivity) getActivity();
		Intent intent = new Intent(getActivity(), SettingsActivity.class).putExtra(
				SettingsActivity.TARGET_SETTING_PAGE,
				preferenceScreen.getKey()).putExtra(SessionManager.BUNDLE_KEY,
						activity.getRemoteProfileID());
		startActivity(intent);

		super.onNavigateToScreen(preferenceScreen);
	}
}