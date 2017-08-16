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

import com.biglybt.android.client.CorePrefs;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.util.Thunk;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.*;
import android.util.Log;

class BiglyBTServiceConnection
	implements ServiceConnection, IBinder.DeathRecipient
{
	@Thunk
	final WeakReference<BiglyBTServiceInitImpl> callback;

	@Thunk
	IBinder coreServiceBinder;

	private BiglyBTServiceIncomingHandler incomingHandler;

	private Messenger incomingMessenger;

	public BiglyBTServiceConnection(BiglyBTServiceInitImpl callback) {
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

	public void detachCore() {
		incomingMessenger = null;
		if (incomingHandler != null) {
			incomingHandler.removeCallbacksAndMessages(null);
			incomingHandler = null;
		}
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

			coreServiceBinder = service;
		}

		cb.messengerService = new Messenger(service);
		try {
			coreServiceBinder.linkToDeath(this, 0);

			incomingHandler = new BiglyBTServiceIncomingHandler(cb);
			incomingMessenger = new Messenger(incomingHandler);
			Message msg = Message.obtain(null, BiglyBTService.MSG_IN_ADD_LISTENER);
			msg.replyTo = incomingMessenger;
			cb.messengerService.send(msg);

		} catch (RemoteException e) {
			// In this case the service has crashed before we could even
			// do anything with it; we can count on soon being
			// disconnected (and then reconnected if it can be restarted)
			// so there is no need to do anything here.
			Log.d(BiglyBTServiceInitImpl.TAG,
					Integer.toHexString(cb.hashCode()) + "] onServiceConnected: ", e);
		}

		// allow BiglyBTService to destroy itself
		cb.context.unbindService(this);
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
			coreServiceBinder.unlinkToDeath(this, 0);
			coreServiceBinder = null;
		}

	}

	public void sendWithReplyTo(Message msg)
			throws RemoteException {
		BiglyBTServiceInitImpl cb = callback.get();
		if (cb == null) {
			return;
		}
		msg.replyTo = incomingMessenger;
		cb.messengerService.send(msg);
	}
}
