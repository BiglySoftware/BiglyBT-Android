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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.util.TypedValue;

/**
 * Created by TuxPaper on 10/22/17.
 */

public abstract class ThemedActivity
	extends AppCompatActivityM
{
	private boolean firstResume = true;

	public static final int REQUEST_VOICE = 4;

	// I bet something like Otto would be better
	public static onActivityResultCapture captureActivityResult = null;

	public interface onActivityResultCapture
	{
		boolean onActivityResult(int requestCode, int resultCode, Intent intent);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		int themeId = getThemeId();
		if (themeId > 0) {
			setTheme(themeId);
		}

		if (AndroidUtils.DEBUG) {
			Intent intent = getIntent();
			log("ThemedActivity", "intent = " + intent + (intent == null ? ""
					: ", Type:" + intent.getType() + ";" + intent.getDataString()));
		}

		super.onCreate(savedInstanceState);

	}

	public int getThemeId() {
		return getThemeId(this);
	}

	public static int getThemeId(Context context) {
		boolean isTV = AndroidUtils.isTV(context);
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
				if (outValue.string != null
						&& !themeName.contentEquals(outValue.string)) {
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

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent data) {
		if (captureActivityResult != null) {
			if (captureActivityResult.onActivityResult(requestCode, resultCode,
					data)) {
				return;
			}
		}
		if (resultCode == Activity.RESULT_CANCELED) {
			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}
}
