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
import com.vuze.android.remote.session.SessionManager;
import com.vuze.util.RunnableWithObject;
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
	@Thunk
	static final String TAG = "VuzeServiceInit";

	@Thunk
	final Context context;

	@Thunk
	IBinder coreServiceBinder;

	@Thunk
	boolean coreServiceRestarting;

	@Thunk
	Map<String, Runnable> mapListeners = new HashMap<>(1);

	@Thunk
	Messenger messengerService;

	private class IncomingHandler
		extends Handler
	{
		@Override
		public void handleMessage(Message msg) {
			if (AndroidUtils.DEBUG) {
				Bundle data = msg.getData();
				logd("] Received from service: " + msg.what + ";"
						+ (data == null ? null : data.get("data")));
			}
			switch (msg.what) {
				case VuzeService.MSG_OUT_REPLY_ADD_LISTENER:
					Runnable onAddedListener = mapListeners.get("onAddedListener");
					String state = msg.getData().getString("state");
					if (onAddedListener != null) {
						if (onAddedListener instanceof RunnableWithObject) {
							((RunnableWithObject) onAddedListener).object = state;
						}
						onAddedListener.run();
					}
					if (state != null && state.equals("ready-to-start")) {
						Message msgIn = Message.obtain(null, VuzeService.MSG_IN_START_CORE);
						try {
							messengerService.send(msgIn);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
					break;

				case VuzeService.MSG_OUT_CORE_STARTED:
					Runnable onCoreStarted = mapListeners.get("onCoreStarted");
					if (onCoreStarted != null) {
						onCoreStarted.run();
					}
					coreServiceRestarting = false;
					break;
				case VuzeService.MSG_OUT_CORE_STOPPING:
					Runnable onCoreStopping = mapListeners.get("onCoreStopping");
					coreServiceRestarting = msg.getData().getBoolean("restarting");
					if (!coreServiceRestarting && onCoreStopping != null) {
						onCoreStopping.run();
					}
					Runnable onCoreRestarting = mapListeners.get("onCoreRestarting");
					if (coreServiceRestarting && onCoreRestarting != null) {
						onCoreRestarting.run();
					}
					break;

				case VuzeService.MSG_OUT_SERVICE_DESTROY:
					coreServiceBinder = null;
					coreServiceRestarting = msg.getData().getBoolean("restarting");
					// trigger a powerUp, so that we attach our listeners to the
					// new service
					new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								Thread.sleep(1200);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}

							if (coreServiceRestarting) {
								powerUp();
							}
						}
					}).start();
					break;
				default:
					super.handleMessage(msg);
			}
		}
	}

	public VuzeServiceInit(final Context context,
			Map<String, Runnable> mapListeners) {
		this.context = context;
		this.mapListeners = mapListeners;
		if (CorePrefs.DEBUG_CORE) {
			logd("] init " + AndroidUtils.getCompressedStackTrace());
		}
	}

	@Thunk
	void powerUp() {
		if (CorePrefs.DEBUG_CORE) {
			logd("] powerUp "
					+ (coreServiceBinder == null ? "(needs to bind)" : "(already bound) ")
					+ AndroidUtils.getCompressedStackTrace());
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
		if (CorePrefs.DEBUG_CORE) {
			logd("startService");
		}

		// Bind, so we can get the service
		final Messenger mMessenger = new Messenger(new IncomingHandler());
		final ServiceConnection serviceConnection = new ServiceConnection() {

			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				if (coreServiceBinder == service) {
					if (CorePrefs.DEBUG_CORE) {
						logd("onServiceConnected: multiple calls, ignoring this one");
					}
					context.unbindService(this);
					return;
				}

				if (CorePrefs.DEBUG_CORE) {
					logd("onServiceConnected: coreSession="
							+ SessionManager.findCoreSession());
				}

				coreServiceBinder = service;

				messengerService = new Messenger(service);
				try {
					coreServiceBinder.linkToDeath(new IBinder.DeathRecipient() {
						@Override
						public void binderDied() {
							if (CorePrefs.DEBUG_CORE) {
								logd("onServiceConnected: binderDied");
							}

							coreServiceBinder = null;
						}
					}, 0);

					Message msg = Message.obtain(null, VuzeService.MSG_IN_ADD_LISTENER);
					msg.replyTo = mMessenger;
					messengerService.send(msg);

				} catch (RemoteException e) {
					// In this case the service has crashed before we could even
					// do anything with it; we can count on soon being
					// disconnected (and then reconnected if it can be restarted)
					// so there is no need to do anything here.
					Log.d(TAG, Integer.toHexString(VuzeServiceInit.this.hashCode())
							+ "] onServiceConnected: ", e);
				}

				// allow VuzeService to destroy itself
				context.unbindService(this);
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				if (CorePrefs.DEBUG_CORE) {
					logd("onServiceDisconnected: ");
				}

				coreServiceBinder = null;
			}
		};
		boolean result = context.bindService(intent, serviceConnection,
				Context.BIND_AUTO_CREATE);
		if (CorePrefs.DEBUG_CORE) {
			logd("bindService: " + result);
		}
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

	@Thunk
	void logd(String s) {
		Log.d(TAG, Integer.toHexString(this.hashCode()) + "] " + s);
	}
}
