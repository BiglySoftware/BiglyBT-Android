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

import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.widget.TextView;

public class DialogFragmentAbout
	extends DialogFragmentBase
{

	private static final String TAG = "About";

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final FragmentActivity activity = getActivity();

		AndroidUtilsUI.AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.about_window);

		AlertDialog.Builder builder = alertDialogBuilder.builder;

		AndroidUtilsUI.linkify(getActivity(),
				(TextView) alertDialogBuilder.view.findViewById(R.id.about_thanksto),
				null, R.string.about_thanks);
		AndroidUtilsUI.linkify(getActivity(),
				(TextView) alertDialogBuilder.view.findViewById(R.id.about_ideas),
				new AndroidUtilsUI.LinkClickListener() {
					@Override
					public boolean linkClicked(String link) {
						if (link.equals("subscribe")) {
							DialogFragmentGiveback.openDialog(getActivity(),
									getFragmentManager(), true, TAG);
							return true;
						}
						return false;
					}
				}, R.string.about_ideas);

		TextView tvLicense = alertDialogBuilder.view.findViewById(
				R.id.about_license);
		try {
			PackageManager manager = getActivity().getPackageManager();
			PackageInfo info = manager.getPackageInfo(activity.getPackageName(), 0);
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
		return TAG;
	}
}
