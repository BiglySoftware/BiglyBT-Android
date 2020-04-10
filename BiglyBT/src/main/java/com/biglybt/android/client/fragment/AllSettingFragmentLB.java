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

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.biglybt.android.client.dialog.DialogFragmentNumberPicker.NumberPickerDialogListener;

public class AllSettingFragmentLB
	extends LeanbackPreferenceFragmentCompat
	implements NumberPickerDialogListener
{
	private AllPrefFragmentHandler handler;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

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
	public void onActivityResult(int requestCode, int resultCode,
			@Nullable Intent data) {
		if (handler != null) {
			handler.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		if (handler != null) {
			handler.onNumberPickerChange(callbackID, val);
		}
	}
}