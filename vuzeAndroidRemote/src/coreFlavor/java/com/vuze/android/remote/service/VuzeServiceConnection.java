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

import java.lang.ref.WeakReference;

import com.vuze.android.remote.CorePrefs;
import com.vuze.android.remote.session.SessionManager;
import com.vuze.util.Thunk;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.*;
import android.util.Log;

/**
 * Created by TuxPaper on 1/31/17.
 */
class VuzeServiceConnection
	implements ServiceConnection
{
	private final WeakReference<VuzeServiceInitImpl> callback;

	@Thunk
	IBinder coreServiceBinder;

	public VuzeServiceConnection(VuzeServiceInitImpl callback) {
		this.callback = new WeakReference<VuzeServiceInitImpl>(callback);
	}

	public IBinder getCoreServiceBinder() {
		return coreServiceBinder;
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		VuzeServiceInitImpl cb = callback.get();
		if (cb == null) {
			return;
		}

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

		coreServiceBinder = service;

		cb.messengerService = new Messenger(service);
		try {
			coreServiceBinder.linkToDeath(new IBinder.DeathRecipient() {
				@Override
				public void binderDied() {
					VuzeServiceInitImpl cb = callback.get();
					if (cb == null) {
						return;
					}
					if (CorePrefs.DEBUG_CORE) {
						cb.logd("onServiceConnected: binderDied");
					}

					coreServiceBinder = null;
				}
			}, 0);

			Message msg = Message.obtain(null, VuzeService.MSG_IN_ADD_LISTENER);
			msg.replyTo = cb.incomingMessenger;
			cb.messengerService.send(msg);

		} catch (RemoteException e) {
			// In this case the service has crashed before we could even
			// do anything with it; we can count on soon being
			// disconnected (and then reconnected if it can be restarted)
			// so there is no need to do anything here.
			Log.d(VuzeServiceInitImpl.TAG,
					Integer.toHexString(cb.hashCode()) + "] onServiceConnected: ", e);
		}

		// allow VuzeService to destroy itself
		cb.context.unbindService(this);
		if (CorePrefs.DEBUG_CORE) {
			cb.logd("onServiceConnected: unbind done");
		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		VuzeServiceInitImpl cb = callback.get();
		if (cb == null) {
			return;
		}
		if (CorePrefs.DEBUG_CORE) {
			cb.logd("onServiceDisconnected: ");
		}

		coreServiceBinder = null;
	}
}
