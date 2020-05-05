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

package com.biglybt.android.client.service;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.NoSuchElementException;

import com.biglybt.android.client.*;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.RunnableWithObject;
import com.biglybt.util.Thunk;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

class BiglyBTServiceConnection
	implements ServiceConnection, IBinder.DeathRecipient
{
	@Thunk
	final WeakReference<BiglyBTServiceInitImpl> callback;

	@Thunk
	IBinder coreServiceBinder;

	IBiglyCoreInterface aidlBinder;

	final IBiglyCoreCallback eventCallback = new IBiglyCoreCallback.Stub() {
		@SuppressWarnings("RedundantThrows")
		@Override
		public void onCoreEvent(int event, Map data)
				throws RemoteException {
			BiglyBTServiceInitImpl cb = callback.get();
			if (cb == null) {
				return;
			}
			if (AndroidUtils.DEBUG) {
				cb.logd("] Received from service: " + event + ";"
						+ (data == null ? null : data.get("data")));
			}
			switch (event) {
				case BiglyBTService.MSG_OUT_REPLY_ADD_LISTENER:
					Runnable onAddedListener = cb.mapListeners.get("onAddedListener");
					String state = MapUtils.getMapString(data, "state", "");
					if (onAddedListener != null) {
						if (onAddedListener instanceof RunnableWithObject) {
							((RunnableWithObject) onAddedListener).object = state;
						}
						onAddedListener.run();
					}
					if ("ready-to-start".equals(state) && cb.messengerService != null) {
						try {
							cb.messengerService.startCore();
						} catch (Throwable e) {
							e.printStackTrace();
						}
					}
					break;

				case BiglyBTService.MSG_OUT_CORE_STARTED:
					Runnable onCoreStarted = cb.mapListeners.get("onCoreStarted");
					if (onCoreStarted != null) {
						onCoreStarted.run();
					}
					cb.coreServiceRestarting = false;
					break;
				case BiglyBTService.MSG_OUT_CORE_STOPPING:
					Runnable onCoreStopping = cb.mapListeners.get("onCoreStopping");
					cb.coreServiceRestarting = MapUtils.getMapBoolean(data, "restarting",
							false);
					if (!cb.coreServiceRestarting && onCoreStopping != null) {
						onCoreStopping.run();
					}
					Runnable onCoreRestarting = cb.mapListeners.get("onCoreRestarting");
					if (cb.coreServiceRestarting && onCoreRestarting != null) {
						onCoreRestarting.run();
					}
					break;

				case BiglyBTService.MSG_OUT_SERVICE_DESTROY:
					cb.coreServiceRestarting = MapUtils.getMapBoolean(data, "restarting",
							false);
					// trigger a powerUp, so that we attach our listeners to the
					// new service
					try {
						new Thread(() -> {
							try {
								Thread.sleep(1200);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							BiglyBTServiceInitImpl cb1 = callback.get();
							if (cb1 == null) {
								return;
							}
							if (cb1.coreServiceRestarting) {
								cb1.powerUp();
							}
						}).start();
					} catch (InternalError ignore) {
						// found in the wild.  Assuming that if we can't create a thread,
						// our service is pretty much dead so no use even checking for a
						// restart that wouldn't work
					}
					break;
			}
		}
	};

	BiglyBTServiceConnection(BiglyBTServiceInitImpl callback) {
		this.callback = new WeakReference<>(callback);
	}

	@Override
	public void binderDied() {
		BiglyBTServiceInitImpl cb = callback.get();
		if (cb != null) {
			if (CorePrefs.DEBUG_CORE) {
				cb.logd("onServiceConnected: binderDied");
			}
			cb.messengerService = null;
		}

		coreServiceBinder = null;
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		BiglyBTServiceInitImpl cb = callback.get();
		if (cb == null) {
			if (CorePrefs.DEBUG_CORE) {
				Log.d("BiglyBTServiceInit", "onServiceConnected: no cb");
			}
			return;
		}

		synchronized (callback) {
			if (coreServiceBinder == service) {
				if (CorePrefs.DEBUG_CORE) {
					cb.logd("onServiceConnected: multiple calls, ignoring this one");
				}
				cb.context.unbindService(this);
				return;
			}

			if (CorePrefs.DEBUG_CORE) {
				cb.logd("onServiceConnected: coreSession="
						+ SessionManager.findCoreSession());
			}

			aidlBinder = IBiglyCoreInterface.Stub.asInterface(service);
			coreServiceBinder = aidlBinder.asBinder();
		}

		cb.messengerService = aidlBinder;
		try {
			coreServiceBinder.linkToDeath(this, 0);

			aidlBinder.addListener(eventCallback);

		} catch (RemoteException e) {
			// In this case the service has crashed before we could even
			// do anything with it; we can count on soon being
			// disconnected (and then reconnected if it can be restarted)
			// so there is no need to do anything here.
			Log.d(BiglyBTServiceInitImpl.TAG,
					Integer.toHexString(cb.hashCode()) + "] onServiceConnected: ", e);
		}

		// allow BiglyBTService to destroy itself
		if (cb.context != null) {
			cb.context.unbindService(this);
		} else {
			// hmm, not sure about this
			try {
				BiglyBTApp.getContext().unbindService(this);
			} catch (Throwable ignore) {
			}
		}
		if (CorePrefs.DEBUG_CORE) {
			cb.logd("onServiceConnected: unbind done");
		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		BiglyBTServiceInitImpl cb = callback.get();
		if (cb != null) {
			if (CorePrefs.DEBUG_CORE) {
				cb.logd("onServiceDisconnected: ");
			}
			cb.messengerService = null;
		}

		if (coreServiceBinder != null) {
			try {
				coreServiceBinder.unlinkToDeath(this, 0);
			} catch (NoSuchElementException ignore) {

			}

			coreServiceBinder = null;
		}

		aidlBinder = null;
	}
}
