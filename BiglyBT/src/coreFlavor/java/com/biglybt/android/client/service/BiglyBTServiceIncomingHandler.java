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

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.util.RunnableWithObject;
import com.biglybt.util.Thunk;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

class BiglyBTServiceIncomingHandler
	extends Handler
{
	@Thunk
	final WeakReference<BiglyBTServiceInitImpl> callback;

	public BiglyBTServiceIncomingHandler(BiglyBTServiceInitImpl callback) {
		this.callback = new WeakReference<>(callback);
	}

	@Override
	public void handleMessage(Message msg) {
		BiglyBTServiceInitImpl cb = callback.get();
		if (cb == null) {
			return;
		}
		if (AndroidUtils.DEBUG) {
			Bundle data = msg.getData();
			cb.logd("] Received from service: " + msg.what + ";"
					+ (data == null ? null : data.get("data")));
		}
		switch (msg.what) {
			case BiglyBTService.MSG_OUT_REPLY_ADD_LISTENER:
				Runnable onAddedListener = cb.mapListeners.get("onAddedListener");
				String state = msg.getData().getString("state");
				if (onAddedListener != null) {
					if (onAddedListener instanceof RunnableWithObject) {
						((RunnableWithObject) onAddedListener).object = state;
					}
					onAddedListener.run();
				}
				if ("ready-to-start".equals(state) && cb.messengerService != null) {
					Message msgIn = Message.obtain(null,
							BiglyBTService.MSG_IN_START_CORE);
					try {
						cb.messengerService.send(msgIn);
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
				cb.coreServiceRestarting = msg.getData().getBoolean("restarting");
				if (!cb.coreServiceRestarting && onCoreStopping != null) {
					onCoreStopping.run();
				}
				Runnable onCoreRestarting = cb.mapListeners.get("onCoreRestarting");
				if (cb.coreServiceRestarting && onCoreRestarting != null) {
					onCoreRestarting.run();
				}
				break;

			case BiglyBTService.MSG_OUT_SERVICE_DESTROY:
				cb.coreServiceRestarting = msg.getData().getBoolean("restarting");
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
						BiglyBTServiceInitImpl cb = callback.get();
						if (cb == null) {
							return;
						}
						if (cb.coreServiceRestarting) {
							cb.powerUp();
						}
					}
				}).start();
				break;
			default:
				super.handleMessage(msg);
		}
	}

}
