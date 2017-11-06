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

import com.biglybt.android.client.*;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.TypedValue;

/**
 * Created by TuxPaper on 10/22/17.
 */

public abstract class ThemedActivity
	extends AppCompatActivityM
{
	private String TAG;
	
	private boolean firstResume = true;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		TAG = getTag();
		int themeId = getThemeId();
		if (themeId > 0) {
			setTheme(themeId);
		}

		if (AndroidUtils.DEBUG) {
			Intent intent = getIntent();
			Log.d(TAG, "intent = " + intent);
			if (intent != null) {
				Log.d(TAG, "Type:" + intent.getType() + ";" + intent.getDataString());
			}
		}

		super.onCreate(savedInstanceState);

	}

	public int getThemeId() {
		boolean isTV = AndroidUtils.isTV();
		if (AndroidUtilsUI.ALWAYS_DARK || isTV
				|| BiglyBTApp.getAppPreferences().isThemeDark()) {
			return R.style.AppThemeDark;
		}
		return R.style.AppTheme;
	}

	public String getThemeName() {
		return getThemeId() == R.style.AppThemeDark ? "dark" : "light";
	}

	@Override
	protected void onResume() {
		if (!firstResume) {
			try {
				final String themeName = getThemeName();
				TypedValue outValue = new TypedValue();
				getTheme().resolveAttribute(R.attr.themeName, outValue, true);
				if (outValue.string != null && !themeName.equals(outValue.string)) {
					recreate();
				}
			} catch (Throwable ignore) {
			}
		} else {
			firstResume = false;
		}
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (AndroidUtils.canShowMultipleActivities()) {
			onLostForeground();
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (!AndroidUtils.canShowMultipleActivities()) {
			onLostForeground();
		}
	}

	protected void onLostForeground() {
	}

	protected abstract String getTag();

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (new ActivityResultHandler(this).onActivityResult(requestCode,
				resultCode, data)) {
			return;
		}

	}
}
