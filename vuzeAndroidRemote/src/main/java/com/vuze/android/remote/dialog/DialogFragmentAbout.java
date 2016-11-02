/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
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

package com.vuze.android.remote.dialog;

import com.vuze.android.remote.AndroidUtilsUI;
import com.vuze.android.remote.AndroidUtilsUI.AlertDialogBuilder;
import com.vuze.android.remote.R;

import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.TextView;

public class DialogFragmentAbout
	extends DialogFragmentBase
{

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.about_window);

		Builder builder = alertDialogBuilder.builder;

		AndroidUtilsUI.linkify(alertDialogBuilder.view, R.id.about_thanksto);
		AndroidUtilsUI.linkify(alertDialogBuilder.view, R.id.about_ideas);

		TextView tvLicense = (TextView) alertDialogBuilder.view.findViewById(
				R.id.about_license);
		try {
			PackageManager manager = getActivity().getPackageManager();
			PackageInfo info = manager.getPackageInfo(getActivity().getPackageName(),
					0);
			String license = getResources().getString(R.string.about_version,
					info.versionName, "" + info.versionCode);

			tvLicense.setText(license);
		} catch (NameNotFoundException ignore) {
		}

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
					}
				});
		return builder.create();
	}

	@Override
	public String getLogTag() {
		return "About";
	}
}
