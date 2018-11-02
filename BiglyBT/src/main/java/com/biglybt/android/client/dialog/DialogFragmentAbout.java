/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.dialog;

import java.util.Locale;

import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;

import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

public class DialogFragmentAbout
	extends DialogFragmentBase
{

	private static final String TAG = "About";

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final FragmentActivity activity = getActivity();
		assert activity != null;

		AndroidUtilsUI.AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				activity, R.layout.about_window);

		AlertDialog.Builder builder = alertDialogBuilder.builder;

		AndroidUtilsUI.linkify(activity,
				alertDialogBuilder.view.findViewById(R.id.about_thanksto), null,
				R.string.about_thanks);
		AndroidUtilsUI.linkify(activity,
				alertDialogBuilder.view.findViewById(R.id.about_ideas), link -> {
					if ("subscribe".equals(link)) {
						DialogFragmentGiveback.openDialog(activity, getFragmentManager(),
								true, TAG);
						return true;
					}
					return false;
				}, R.string.about_ideas);

		TextView tvLicense = alertDialogBuilder.view.findViewById(
				R.id.about_license);
		try {
			PackageManager manager = activity.getPackageManager();
			PackageInfo info = manager.getPackageInfo(activity.getPackageName(), 0);
			if (info != null && tvLicense != null) {
				String license = getResources().getString(R.string.about_version,
						info.versionName, "" + info.versionCode);

				tvLicense.setText(license);
			}
		} catch (NameNotFoundException ignore) {
		}

		final TextView tvTranslator = alertDialogBuilder.view.findViewById(
				R.id.about_translator);
		if (tvTranslator != null) {
			String translator = getString(R.string.about_translator,
					Locale.getDefault().getDisplayLanguage());
			if (translator.contains("PUTYOURNAMEHERE")) {
				tvTranslator.setVisibility(View.GONE);
			} else {
				tvTranslator.setText(translator);
				tvTranslator.setVisibility(View.VISIBLE);
			}
		}

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok, (dialog, id) -> {
		});
		return builder.create();
	}
}
