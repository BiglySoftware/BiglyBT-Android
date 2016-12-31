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

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.CorePrefs;
import com.vuze.android.remote.VuzeRemoteApp;
import com.vuze.util.Thunk;

import android.app.PendingIntent;
import android.content.*;
import android.os.*;
import android.util.Log;

/**
 * Created by TuxPaper on 3/28/16.
 */
public class VuzeServiceInit
{
	static final String TAG = "VuzeServiceInit";

	@Thunk
	final Context context;

	@Thunk
	final Runnable onCoreStarted;

	@Thunk
	final Runnable onCoreStopping;

	@Thunk
	IBinder coreServiceBinder;

	private String initStatus;

	class IncomingHandler
		extends Handler
	{
		@Override
		public void handleMessage(Message msg) {
			if (AndroidUtils.DEBUG) {
				Bundle data = msg.getData();
				Log.d(TAG, "Received from service: " + msg.what + ";" + (data == null ?  null : data.get("data")));
			}
			switch (msg.what) {
				case VuzeService.MSG_OUT_CORE_STARTED:
					if (onCoreStarted != null) {
						onCoreStarted.run();
					}
					break;
				case VuzeService.MSG_OUT_CORE_STOPPING:
					if (onCoreStopping != null) {
						onCoreStopping.run();
					}

					coreServiceBinder = null;
					break;
				default:
					super.handleMessage(msg);
			}
		}
	}

	public VuzeServiceInit(final Context context, Runnable onCoreStarted,
			Runnable onCoreStopping) {
		this.context = context;
		this.onCoreStarted = onCoreStarted;
		this.onCoreStopping = onCoreStopping;
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "init " + AndroidUtils.getCompressedStackTrace());
		}
	}

	public void powerUp() {
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "powerUp " + AndroidUtils.getCompressedStackTrace());
		}

		if (coreServiceBinder == null) {
			new Handler(Looper.getMainLooper()).post(new Runnable() {
				@Override
				public void run() {
					startService(context);
				}
			});
		}
	}

	@Thunk
	void startService(final Context context) {
		Intent intent = new Intent(context, VuzeService.class);
		// Start the service, so that onStartCommand is called
		context.startService(intent);

		// Bind, so we can get the service
		final Messenger mMessenger = new Messenger(new IncomingHandler());
		ServiceConnection serviceConnection = new ServiceConnection() {

			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				if (coreServiceBinder == service) {
					if (CorePrefs.DEBUG_CORE) {
						Log.d(TAG, "onServiceConnected: multiple calls, ignoring this one");
					}
					context.unbindService(this);
					return;
				}

				if (CorePrefs.DEBUG_CORE) {
					Log.d(TAG, "onServiceConnected: ");
				}

				coreServiceBinder = service;

				final Messenger messengerService = new Messenger(service);
				try {
					Message msg = Message.obtain(null, VuzeService.MSG_IN_ADD_LISTENER);
					msg.replyTo = mMessenger;
					messengerService.send(msg);
				} catch (RemoteException e) {
					// In this case the service has crashed before we could even
					// do anything with it; we can count on soon being
					// disconnected (and then reconnected if it can be restarted)
					// so there is no need to do anything here.
				}

				// allow VuzeService to destroy itself
				context.unbindService(this);
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				if (CorePrefs.DEBUG_CORE) {
					Log.d(TAG, "onServiceDisconnected: ");
				}

				coreServiceBinder = null;
			}
		};
		context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
	}

	public static void stopService() {
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
}
