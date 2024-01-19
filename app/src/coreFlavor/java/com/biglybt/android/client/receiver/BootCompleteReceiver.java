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
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.biglybt.android.client.*;
import com.biglybt.android.client.service.BiglyBTService;

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
		final long startedOn = SystemClock.elapsedRealtime();
		final IAnalyticsTracker tracker = AnalyticsTracker.getInstance();
		tracker.setDeviceName(
				AndroidUtils.getDeviceNameForLogger(context.getContentResolver()));

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "BroadcastReceiver.onReceive");
		}
		if (intent.getAction() == null) {
			return;
		}
		if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			return;
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "BroadcastReceiver.onReceive ACTION_BOOT_COMPLETED");
		}
		final PendingResult pendingResult = goAsync();

		final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		new Thread(() -> {
			try {
				if (RemoteUtils.getCoreProfile() == null
						|| !CorePrefs.getInstance().getPrefAutoStart()) {
					return;
				}

				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "startForegroundService BiglyBTService");
				}

				ContextCompat.startForegroundService(context,
						new Intent(BiglyBTService.INTENT_ACTION_START, null, context,
								BiglyBTService.class));
				//BiglyCoreUtils.startBiglyBTCoreService() does bindings which BroadcastReceivers shouldn't do

			} catch (Throwable t) {
				tracker.setLastViewName(
						(SystemClock.elapsedRealtime() - startedOn) + "ms");
				tracker.logError(t, stackTrace);
			} finally {
				pendingResult.finish();
			}
		}, TAG).start();
	}
}
