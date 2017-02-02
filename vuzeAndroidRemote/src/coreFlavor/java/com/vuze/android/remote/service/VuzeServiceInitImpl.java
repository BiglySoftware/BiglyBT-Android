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

package com.vuze.android.remote.service;

import java.util.HashMap;
import java.util.Map;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.CorePrefs;
import com.vuze.android.remote.VuzeRemoteApp;
import com.vuze.util.Thunk;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.util.Log;

/**
 * Created by TuxPaper on 3/28/16.
 */
public class VuzeServiceInitImpl
	implements VuzeServiceInit
{
	@Thunk
	static final String TAG = "VuzeServiceInit";

	@Thunk
	Context context;

	@Thunk
	boolean coreServiceRestarting;

	@Thunk
	Map<String, Runnable> mapListeners = new HashMap<>(1);

	/**
	 * If not null, we are connected to Vuze Core and can send messages
	 */
	@Thunk
	Messenger messengerService;

	private VuzeServiceConnection serviceConnection;

	public VuzeServiceInitImpl(final Context context,
			Map<String, Runnable> mapListeners) {
		this.context = context;
		this.mapListeners = mapListeners;
		if (CorePrefs.DEBUG_CORE) {
			logd("init " + AndroidUtils.getCompressedStackTrace());
		}
	}

	@Override
	public void powerUp() {
		if (CorePrefs.DEBUG_CORE) {
			logd("powerUp "
					+ (messengerService == null ? "(needs to bind) " : "(already bound) ")
					+ AndroidUtils.getCompressedStackTrace());
		}

		if (messengerService == null) {
			new Handler(Looper.getMainLooper()).post(new Runnable() {
				@Override
				public void run() {
					startService(context);
				}
			});
		}
	}

	@Override
	public void startService(Context contextx) {
		Context context = VuzeRemoteApp.getContext();

		// Always start the service first, otherwise the service will shut down
		// when it has no bindings
		Intent intent = new Intent(context, VuzeService.class);
		context.startService(intent);

		if (CorePrefs.DEBUG_CORE) {
			logd("startService " + AndroidUtils.getCompressedStackTrace());
		}

		serviceConnection = new VuzeServiceConnection(this);
		boolean result = context.bindService(intent, serviceConnection,
				Context.BIND_AUTO_CREATE);
		if (CorePrefs.DEBUG_CORE) {
			logd("bindService: " + result);
		}
	}

	@Override
	public void detachCore() {
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "detachCore " + messengerService);
		}
		if (serviceConnection != null) {
			try {
				serviceConnection.sendWithReplyTo(
						Message.obtain(null, VuzeService.MSG_IN_REMOVE_LISTENER));
			} catch (RemoteException e) {
				// In this case the service has crashed before we could even
				// do anything with it; we can count on soon being
				// disconnected (and then reconnected if it can be restarted)
				// so there is no need to do anything here.
				Log.d(TAG, Integer.toHexString(VuzeServiceInitImpl.this.hashCode())
						+ "] detachCore: ", e);
			}
			serviceConnection = null;
		}
		messengerService = null;
		mapListeners.clear();
		context = null;
	}

	@Override
	public void stopService() {
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "StopService");
		}
		Context context = VuzeRemoteApp.getContext();
		Intent intentStop = new Intent(context, VuzeService.class);
		intentStop.setAction(VuzeService.INTENT_ACTION_STOP);
		PendingIntent piStop = PendingIntent.getService(context, 0, intentStop,
				PendingIntent.FLAG_CANCEL_CURRENT);

		try {
			piStop.send();
		} catch (PendingIntent.CanceledException e) {
			Log.e(TAG, "stopService", e);
		}
	}

	@Thunk
	void logd(String s) {
		Log.d(TAG, Integer.toHexString(this.hashCode()) + "] " + s);
	}
}
