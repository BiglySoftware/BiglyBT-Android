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

package com.biglybt.android.client.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;
import com.biglybt.android.client.dialog.DialogFragmentAbstractLocationPicker.LocationPickerListener;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker.NumberPickerDialogListener;
import com.biglybt.android.client.fragment.SettingsFragmentM;
import com.biglybt.android.util.PathInfo;

public class SettingsActivity
	extends SessionActivity
	implements NumberPickerDialogListener, LocationPickerListener
{
	public static final String TARGET_SETTING_PAGE = "rootKey";

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		Fragment frag = AndroidUtilsUI.findFragmentByClass(this,
				NumberPickerDialogListener.class);
		if (frag instanceof NumberPickerDialogListener) {
			((NumberPickerDialogListener) frag).onNumberPickerChange(callbackID, val);
		}
	}

	@Override
	public void locationChanged(String callbackID, @NonNull PathInfo location) {
		Fragment frag = AndroidUtilsUI.findFragmentByClass(this,
				LocationPickerListener.class);
		if (frag instanceof LocationPickerListener) {
			((LocationPickerListener) frag).locationChanged(callbackID, location);
		}
	}

	@Override
	protected void onCreateWithSession(@Nullable Bundle savedInstanceState) {
		setContentView(R.layout.activity_toolbar_frag);
		Toolbar toolbar = findViewById(R.id.actionbar);
		toolbar.setTitle(R.string.settings);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		toolbar.setNavigationOnClickListener(v -> onBackPressed());
		if (savedInstanceState == null) {
			SettingsFragmentM frag = new SettingsFragmentM();
			Intent intent = getIntent();
			if (intent != null) {
				String rootKey = intent.getStringExtra(TARGET_SETTING_PAGE);
				if (rootKey != null) {
					final Bundle bundle = new Bundle();
					bundle.putString(TARGET_SETTING_PAGE, rootKey);
					frag.setArguments(bundle);
				}
			}
			getSupportFragmentManager().beginTransaction().replace(
					R.id.fragment_container, frag).commit();
		}
	}
}