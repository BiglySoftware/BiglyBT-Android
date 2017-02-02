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

package com.vuze.android.remote.receiver;

import com.vuze.android.remote.*;
import com.vuze.android.remote.service.VuzeService;
import com.vuze.android.remote.session.RemoteProfile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Simple Broadcast Receiver that launches Vuze Core on boot if configured by
 * user to do so.
 * <p>
 * Created by TuxPaper on 3/24/16.
 */
public class BootCompleteReceiver
	extends BroadcastReceiver
{
	private static final String TAG = "BootReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "BroadcastReceiver.onReceive");
		}
		if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			return;
		}
		if (!new CorePrefs().getPrefAutoStart()) {
			return;
		}

		if (coreSessionInfoExists()) {
			Intent intent2 = new Intent(context, VuzeService.class);
			intent2.setAction(VuzeService.INTENT_ACTION_START);
			context.startService(intent2);
			//VuzeCoreUtils.startVuzeCoreService() does bindings which BrowdcastReceviers shouldn't do
		}
	}

	private boolean coreSessionInfoExists() {
		AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
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
