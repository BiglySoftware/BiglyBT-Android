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
import com.biglybt.android.client.session.Session;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

public class SettingsFragmentM
	extends PreferenceFragmentCompat
	implements DialogFragmentNumberPicker.NumberPickerDialogListener
{
	private Session session;

	private PrefFragmentHandler prefFragmentHandler;

	private int prefID;

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
	public void onDestroy() {
		super.onDestroy();
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
		addPreferencesFromResource(prefID);
	}
}