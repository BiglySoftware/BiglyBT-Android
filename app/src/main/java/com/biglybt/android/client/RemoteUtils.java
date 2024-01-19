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

import android.Manifest.permission;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.biglybt.android.client.AppCompatActivityM.PermissionRequestResults;
import com.biglybt.android.client.AppCompatActivityM.PermissionResultHandler;
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
import com.biglybt.util.RunnableUIThread;
import com.biglybt.util.Thunk;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.grandcentrix.tray.TrayPreferences;

import java.util.List;
import java.util.Map;

public class RemoteUtils
{
	public static final String KEY_REMOTE_JSON = "remote.json";

	public static final String KEY_REQ_PW = "reqPW";

	//private static final String TAG = "RemoteUtils";
	public static String lastOpenDebug = null;

	public static void openRemote(final AppCompatActivityM activity,
			final RemoteProfile remoteProfile, final boolean isMain,
			final RunnableUIThread runOnNoOpen) {

		// Ensure remote is saved in AppPreferences
		OffThread.runOffUIThread(() -> {
			AppPreferences appPreferences = BiglyBTApp.getAppPreferences();

			if (appPreferences.getRemote(remoteProfile.getID()) == null) {
				appPreferences.addRemoteProfile(remoteProfile);
			}
		});

		// Ensure remote can autostart if option enabled
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
				&& remoteProfile.isLocalHost()) {
			AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
			TrayPreferences prefs = appPreferences.getPreferences();
			if (prefs.getBoolean(CorePrefs.PREF_CORE_AUTOSTART, false)) {

				PowerManager pm = (PowerManager) activity.getSystemService(
						Context.POWER_SERVICE);
				boolean isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(
						activity.getApplicationContext().getPackageName());

				boolean isRestricted = false;
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
					ActivityManager am = (ActivityManager) activity.getSystemService(
							Context.ACTIVITY_SERVICE);
					isRestricted = am.isBackgroundRestricted();
				}

				if (AndroidUtils.DEBUG) {
					Log.d("BatteryOpt",
							"isIgnoringBatteryOptimizations: "
									+ isIgnoringBatteryOptimizations + "; isRestricted: "
									+ isRestricted);
				}
				if (isRestricted || !isIgnoringBatteryOptimizations) {
					OffThread.runOnUIThread(activity, false, (a) -> {
						AlertDialog.Builder builder = new MaterialAlertDialogBuilder(
								activity).setCancelable(true);
						builder.setTitle(R.string.core_auto_start_on_boot);
						builder.setMessage(R.string.core_auto_start_on_boot_auth);
						builder.setPositiveButton(R.string.settings, (dialog, which) -> {
							Intent intent = new Intent();
							intent.setAction(
									Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
							activity.startActivity(intent);
							if (runOnNoOpen != null) {
								runOnNoOpen.run();
							}
						});
						builder.setNegativeButton(R.string.disable_auto_start,
								(dialog, which) -> OffThread.runOffUIThread(() -> {
									prefs.put(CorePrefs.PREF_CORE_AUTOSTART, false);

									OffThread.runOnUIThread(
											() -> reallyOpenRemote(activity, remoteProfile, isMain));
								}));
						if (runOnNoOpen != null) {
							builder.setOnCancelListener(dialog -> runOnNoOpen.run());
						}
						builder.show();
					});
					return;
				}
			}
		}

		// Ensure permissions
		List<String> requiredPermissions = remoteProfile.getRequiredPermissions();
		if (requiredPermissions.size() > 0) {
			activity.requestPermissions(requiredPermissions.toArray(new String[0]),
					new PermissionResultHandler() {
						@WorkerThread
						@Override
						public void onAllGranted() {
							OffThread.runOnUIThread(
									() -> reallyOpenRemote(activity, remoteProfile, isMain));
						}

						@WorkerThread
						@Override
						public void onSomeDenied(PermissionRequestResults results) {
							List<String> denies = results.getDenies();
							if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
								denies.remove(permission.POST_NOTIFICATIONS);
							}
							if (denies.size() > 0) {
								AndroidUtilsUI.showDialog(activity, R.string.permission_denied,
										R.string.error_client_requires_permissions);
								if (runOnNoOpen != null) {
									OffThread.runOnUIThread(runOnNoOpen);
								}
							} else {
								OffThread.runOnUIThread(
										() -> reallyOpenRemote(activity, remoteProfile, isMain));
							}
						}
					});
			return;
		}

		reallyOpenRemote(activity, remoteProfile, isMain);
	}

	@Thunk
	@UiThread
	static void reallyOpenRemote(AppCompatActivityM activity,
			RemoteProfile remoteProfile, boolean isMain) {

		Intent myIntent = new Intent(activity.getIntent());
		myIntent.setAction(Intent.ACTION_VIEW);
		myIntent.setFlags(
				myIntent.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
						| Intent.FLAG_GRANT_WRITE_URI_PERMISSION
						| Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
						| Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));
		myIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
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
		@AnyThread
		void onCoreProfileCreated(RemoteProfile coreProfile,
				boolean alreadyCreated);
	}

	public static void createCoreProfile(@NonNull final FragmentActivity activity,
			final OnCoreProfileCreated l) {
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
				AndroidUtils.getFriendlyDeviceName()));
		localProfile.setUpdateInterval(2);

		if (l != null) {
			l.onCoreProfileCreated(localProfile, false);
		}
	}

	public static RemoteProfile getCoreProfile() {
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
