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

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker;
import com.biglybt.android.client.fragment.SettingsFragmentLB;
import com.biglybt.android.client.fragment.SettingsFragmentM;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.view.View;

public class SettingsActivity
	extends SessionActivity
	implements DialogFragmentNumberPicker.NumberPickerDialogListener
{
	private static final String TAG = "SettingsActivity";

	public static final String TARGET_SETTING_PAGE = "rootKey";

	private Fragment fragmentLB;

	private android.support.v4.app.Fragment fragmentAppCompat;

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {

		if (fragmentAppCompat instanceof DialogFragmentNumberPicker.NumberPickerDialogListener) {
			((DialogFragmentNumberPicker.NumberPickerDialogListener) fragmentAppCompat).onNumberPickerChange(
					callbackID, val);
		}
		if (fragmentLB instanceof DialogFragmentNumberPicker.NumberPickerDialogListener) {
			((DialogFragmentNumberPicker.NumberPickerDialogListener) fragmentLB).onNumberPickerChange(
					callbackID, val);
		}
	}

	@Override
	public int getThemeId() {
		if (AndroidUtils.isTV(this)) {
			return -1;// R.style.myThemePreferences;
		}
		return super.getThemeId();
	}

	@Override
	protected void onCreateWithSession(@Nullable Bundle savedInstanceState) {
		if (AndroidUtils.isTV(this)) {
			fragmentLB = new SettingsFragmentLB();
			getFragmentManager().beginTransaction().replace(android.R.id.content,
					fragmentLB).commit();
		} else {
			setContentView(R.layout.activity_toolbar_frag);
			Toolbar toolbar = findViewById(R.id.actionbar);
			toolbar.setTitle(R.string.settings);
			setSupportActionBar(toolbar);
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			toolbar.setNavigationOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					onBackPressed();

				}
			});
			fragmentAppCompat = new SettingsFragmentM();
			Intent intent = getIntent();
			if (intent != null) {
				String rootKey = intent.getStringExtra(TARGET_SETTING_PAGE);
				if (rootKey != null) {
					final Bundle bundle = new Bundle();
					bundle.putString(TARGET_SETTING_PAGE, rootKey);
					fragmentAppCompat.setArguments(bundle);
				}
			}
			getSupportFragmentManager().beginTransaction().replace(
					R.id.fragment_container, fragmentAppCompat).commit();
		}
	}

	@Override
	protected String getTag() {
		return TAG;
	}
}