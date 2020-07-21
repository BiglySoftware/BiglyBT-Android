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

package com.biglybt.android.client.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.biglybt.android.client.*;
import com.biglybt.android.client.service.BiglyBTService;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.util.Thunk;

/**
 * Simple Broadcast Receiver that launches BiglyBT Core on boot if configured by
 * user to do so.
 * <p>
 * Created by TuxPaper on 3/24/16.
 */
public class BootCompleteReceiver
	extends BroadcastReceiver
{
	private static final String TAG = "BootReceiver";

	@Override
	public void onReceive(final Context context, Intent intent) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "BroadcastReceiver.onReceive");
		}
		if (intent.getAction() == null) {
			return;
		}
		if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			return;
		}
		final PendingResult pendingResult = goAsync();

		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		new Thread(() -> {
			try {
				if (!CorePrefs.getInstance().getPrefAutoStart()) {
					pendingResult.finish();
					return;
				}

				if (coreSessionInfoExists()) {
					Intent intent2 = new Intent(context, BiglyBTService.class);
					intent2.setAction(BiglyBTService.INTENT_ACTION_START);
					ContextCompat.startForegroundService(context, intent2);
					//BiglyCoreUtils.startBiglyBTCoreService() does bindings which BroadcastReceviers shouldn't do
				}

			} catch (Throwable t) {
				// TODO: We get an IllegalAccessError on certain devices (Skyworth; Android 8.0)
				//       due to R8/Proguard.  Hopefully it will get fixed, but since it's
				//       only been reported on one device on one OS version, it may not be
				//       We could warn the user, provide a non-optimized apk for them
				//       via non-Google Play
				AnalyticsTracker.getInstance().logError(t, stackTrace);
			}
			pendingResult.finish();
		}, TAG).start();
	}

	@Thunk
	boolean coreSessionInfoExists() {
		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		RemoteProfile[] remotes = appPreferences.getRemotes();
		if (remotes == null || remotes.length == 0) {
			return false;
		}
		for (RemoteProfile remote : remotes) {
			if (remote.getRemoteType() == RemoteProfile.TYPE_CORE) {
				return true;
			}
		}
		return false;
	}
}
