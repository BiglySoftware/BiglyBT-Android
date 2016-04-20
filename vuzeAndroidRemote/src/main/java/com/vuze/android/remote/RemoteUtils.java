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

package com.vuze.android.remote;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.vuze.android.remote.activity.IntentHandler;
import com.vuze.android.remote.activity.LoginActivity;
import com.vuze.android.remote.activity.TorrentViewActivity;
import com.vuze.android.remote.dialog.DialogFragmentGenericRemoteProfile;
import com.vuze.android.remote.dialog.DialogFragmentVuzeCoreProfile;
import com.vuze.android.remote.dialog.DialogFragmentVuzeRemoteProfile;
import com.vuze.util.JSONUtils;

import java.util.Map;

public class RemoteUtils
{
	//private static final String TAG = "RemoteUtils";

	public static void openRemote(Activity activity, RemoteProfile remoteProfile,
			boolean isMain) {
		AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();

		if (appPreferences.getRemote(remoteProfile.getID()) == null) {
			appPreferences.addRemoteProfile(remoteProfile);
		}

		Intent myIntent = new Intent(activity.getIntent());
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		if (isMain) {
			myIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			// Scenario:
			// User has multiple remote hosts.
			// User clicks on torrent link in browser.
			// User is displayed remote selector activity (IntenntHandler) and picks
			// Remote activity is opened, torrent is added
			// We want the back button to go back to the browser.  Going back to
			// the remote selector would be confusing (especially if they then chose
			// another remote!)
			myIntent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
		}
		myIntent.setClass(activity, TorrentViewActivity.class);

		myIntent.putExtra(SessionInfoManager.BUNDLE_KEY, remoteProfile.getID());

		activity.startActivity(myIntent);

	}

	public static void openRemoteList(Context context) {
		Intent myIntent = new Intent();
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.setFlags(
				Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		myIntent.setClass(context, IntentHandler.class);
		context.startActivity(myIntent);
	}

	public static void editProfile(RemoteProfile remoteProfile,
			FragmentManager fm) {
		DialogFragment dlg;

		int remoteType = remoteProfile.getRemoteType();
		switch (remoteType) {
			case RemoteProfile.TYPE_CORE:
				dlg = new DialogFragmentVuzeCoreProfile();
				break;
			case RemoteProfile.TYPE_LOOKUP:
				dlg = new DialogFragmentVuzeRemoteProfile();
				break;
			default:
				dlg = new DialogFragmentGenericRemoteProfile();
				break;
		}
		Bundle args = new Bundle();
		Map<?, ?> profileAsMap = remoteProfile.getAsMap(false);
		String profileAsJSON = JSONUtils.encodeToJSON(profileAsMap);
		args.putSerializable("remote.json", profileAsJSON);
		dlg.setArguments(args);
		AndroidUtils.showDialog(dlg, fm, "GenericRemoteProfile");
	}

	public interface OnCoreProfileCreated
	{
		public void onCoreProfileCreated(RemoteProfile coreProfile);
	}

	public static void createCoreProfile(final Activity activity,
			final OnCoreProfileCreated l) {
		AndroidUtilsUI.requestPermissions(activity, new String[] {
			Manifest.permission.WRITE_EXTERNAL_STORAGE
		}, new Runnable() {
			@Override
			public void run() {
				RemoteProfile localProfile = new RemoteProfile(RemoteProfile.TYPE_CORE);
				localProfile.setHost("localhost");
				localProfile.setPort(9092);
				localProfile.setNick(
						activity.getString(R.string.local_name, android.os.Build.MODEL));
				localProfile.setUpdateInterval(2);

				if (l != null) {
					l.onCoreProfileCreated(localProfile);
				}
			}
		}, new Runnable() {
			@Override
			public void run() {
				AndroidUtils.showDialog(activity, "Permission Denied",
						"Can't create torrent client on device without requested "
								+ "permissions.");
			}
		});
	}
}
