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

package com.biglybt.android.client;

import java.util.List;
import java.util.Map;

import com.biglybt.android.client.activity.IntentHandler;
import com.biglybt.android.client.activity.TorrentViewActivity;
import com.biglybt.android.client.dialog.DialogFragmentBiglyBTCoreProfile;
import com.biglybt.android.client.dialog.DialogFragmentBiglyBTRemoteProfile;
import com.biglybt.android.client.dialog.DialogFragmentGenericRemoteProfile;
import com.biglybt.android.client.rpc.RPC;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.RemoteProfileFactory;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.util.JSONUtils;
import com.biglybt.util.Thunk;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.AnyThread;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

public class RemoteUtils
{
	public static final String KEY_REMOTE_JSON = "remote.json";

	public static final String KEY_REQ_PW = "reqPW";

	//private static final String TAG = "RemoteUtils";
	public static String lastOpenDebug = null;

	/**
	 * 
	 * @return true if opened immediately
	 */
	public static boolean openRemote(final AppCompatActivityM activity,
			final RemoteProfile remoteProfile, final boolean isMain,
			final boolean closeActivityOnSuccess) {
		AndroidUtilsUI.runOffUIThread(() -> { 
			AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
	
			if (appPreferences.getRemote(remoteProfile.getID()) == null) {
				appPreferences.addRemoteProfile(remoteProfile);
			}
		});

		List<String> requiredPermissions = remoteProfile.getRequiredPermissions();
		if (requiredPermissions.size() > 0) {
			return activity.requestPermissions(
					requiredPermissions.toArray(new String[0]), () -> {
						if (closeActivityOnSuccess && !isMain) {
							activity.finish();
						}
						reallyOpenRemote(activity, remoteProfile, isMain);
					},
					() -> AndroidUtilsUI.showDialog(activity, R.string.permission_denied,
							R.string.error_client_requires_permissions));
		}

		if (closeActivityOnSuccess && !isMain) {
			activity.finish();
		}
		reallyOpenRemote(activity, remoteProfile, isMain);
		return true;
	}

	@Thunk
	static void reallyOpenRemote(AppCompatActivityM activity,
			RemoteProfile remoteProfile, boolean isMain) {

		Intent myIntent = new Intent(activity.getIntent());
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		if (isMain) {
			myIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			// Scenario:
			// User has multiple remote hosts.
			// User clicks on torrent link in browser.
			// User is displayed remote selector activity (IntentHandler) and picks
			// Remote activity is opened, torrent is added
			// We want the back button to go back to the browser.  Going back to
			// the remote selector would be confusing (especially if they then chose
			// another remote!)
			myIntent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
			activity.finish();
		}
		myIntent.setClass(activity, TorrentViewActivity.class);

		myIntent.putExtra(SessionManager.BUNDLE_KEY, remoteProfile.getID());

		lastOpenDebug = AndroidUtils.getCompressedStackTrace();

		activity.startActivity(myIntent);
	}

	@AnyThread
	public static void openRemoteList(Context context) {
		Intent myIntent = new Intent();
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.setFlags(
				Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_CLEAR_TOP
						| Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
		myIntent.setClass(context, IntentHandler.class);
		context.startActivity(myIntent);
	}

	public static void editProfile(RemoteProfile remoteProfile,
			FragmentManager fm, boolean reqPW) {
		DialogFragment dlg;

		int remoteType = remoteProfile.getRemoteType();
		switch (remoteType) {
			case RemoteProfile.TYPE_CORE:
				dlg = new DialogFragmentBiglyBTCoreProfile();
				break;
			case RemoteProfile.TYPE_LOOKUP:
				dlg = new DialogFragmentBiglyBTRemoteProfile();
				break;
			default:
				dlg = new DialogFragmentGenericRemoteProfile();
				break;
		}
		Bundle args = new Bundle();
		Map<?, ?> profileAsMap = remoteProfile.getAsMap(false);
		String profileAsJSON = JSONUtils.encodeToJSON(profileAsMap);
		args.putString(SessionManager.BUNDLE_KEY, remoteProfile.getID());
		args.putSerializable(KEY_REMOTE_JSON, profileAsJSON);
		args.putBoolean(KEY_REQ_PW, reqPW);
		dlg.setArguments(args);
		AndroidUtilsUI.showDialog(dlg, fm, "GenericRemoteProfile");
	}

	public interface OnCoreProfileCreated
	{
		void onCoreProfileCreated(RemoteProfile coreProfile,
				boolean alreadyCreated);
	}

	public static void createCoreProfile(final FragmentActivity activity,
			final OnCoreProfileCreated l) {
		AndroidUtilsUI.requestPermissions(activity, new String[] {
			Manifest.permission.WRITE_EXTERNAL_STORAGE
		}, () -> {
			RemoteProfile coreProfile = RemoteUtils.getCoreProfile();
			if (coreProfile != null) {
				if (l != null) {
					l.onCoreProfileCreated(coreProfile, true);
				}
				return;
			}

			RemoteProfile localProfile = RemoteProfileFactory.create(
					RemoteProfile.TYPE_CORE);
			localProfile.setHost("localhost");
			localProfile.setPort(RPC.LOCAL_BIGLYBT_PORT);
			localProfile.setNick(activity.getString(R.string.local_name,
					BiglyBTApp.deviceName == null ? Build.MODEL : BiglyBTApp.deviceName));
			localProfile.setUpdateInterval(2);

			if (l != null) {
				l.onCoreProfileCreated(localProfile, false);
			}
		}, () -> AndroidUtilsUI.showDialog(activity, R.string.permission_denied,
				R.string.error_client_requires_permissions));
	}

	private static RemoteProfile getCoreProfile() {
		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		RemoteProfile[] remotes = appPreferences.getRemotes();
		RemoteProfile coreProfile = null;
		for (RemoteProfile remoteProfile : remotes) {
			if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_CORE) {
				coreProfile = remoteProfile;
				break;
			}
		}
		return coreProfile;
	}
}
