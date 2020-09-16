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
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;
import com.biglybt.android.client.dialog.DialogFragmentAbstractLocationPicker.LocationPickerListener;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker;
import com.biglybt.android.client.fragment.SettingsFragmentLB;
import com.biglybt.android.client.fragment.SettingsFragmentM;
import com.biglybt.android.util.PathInfo;

public class SettingsActivity
	extends SessionActivity
	implements DialogFragmentNumberPicker.NumberPickerDialogListener,
	LocationPickerListener
{
	public static final String TARGET_SETTING_PAGE = "rootKey";

	private Fragment fragmentLB;

	private androidx.fragment.app.Fragment fragmentAppCompat;

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
	public void locationChanged(String callbackID, @NonNull PathInfo location) {
		if (fragmentAppCompat instanceof LocationPickerListener) {
			((LocationPickerListener) fragmentAppCompat).locationChanged(callbackID,
					location);
		}
		if (fragmentLB instanceof LocationPickerListener) {
			((LocationPickerListener) fragmentLB).locationChanged(callbackID,
					location);
		}
	}

	@Override
	public int getThemeId() {
		if (AndroidUtils.isTV(this)
				&& Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			return R.style.ThemeLeanbackSettings;
		}
		return super.getThemeId();
	}

	@Override
	protected void onCreateWithSession(@Nullable Bundle savedInstanceState) {
		if (AndroidUtils.isTV(this)
				&& Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			if (savedInstanceState != null) {
				return;
			}
			fragmentLB = new SettingsFragmentLB();
			getSupportFragmentManager().beginTransaction().replace(
					android.R.id.content, fragmentLB).commit();
		} else {
			setContentView(R.layout.activity_toolbar_frag);
			Toolbar toolbar = findViewById(R.id.actionbar);
			toolbar.setTitle(R.string.settings);
			setSupportActionBar(toolbar);
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			toolbar.setNavigationOnClickListener(v -> onBackPressed());
			if (savedInstanceState == null) {
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
	}
}