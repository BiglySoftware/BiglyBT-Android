/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.vuze.android.remote;

import java.util.ArrayList;
import java.util.List;

import android.content.*;
import android.net.ConnectivityManager;

public class NetworkState
{
	public interface NetworkStateListener {
		public void onlineStateChanged(boolean isOnline);
		public void wifiConnectionChanged(boolean isWifiConnected);
	}

	private BroadcastReceiver mConnectivityReceiver;
	
	private boolean wifiConnected;

	private boolean isOnline;

	private Context context;
	
	private List<NetworkStateListener> listeners = new ArrayList<NetworkState.NetworkStateListener>();

	public NetworkState(Context context) {
		this.context = context;
		// register BroadcastReceiver on network state changes
		mConnectivityReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
					return;
				}
				setWifiConnected(AndroidUtils.isWifiConnected(context));
				setOnline(AndroidUtils.isOnline(context));
			}
		};
		setOnline(AndroidUtils.isOnline(context));
		final IntentFilter mIFNetwork = new IntentFilter();
		mIFNetwork.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		context.registerReceiver(mConnectivityReceiver, mIFNetwork);
	}

	protected void setOnline(boolean online) {
		if (isOnline == online) {
			return;
		}
		isOnline = online;
		synchronized (listeners) {
			for (NetworkStateListener l : listeners) {
				l.onlineStateChanged(online);
			}
		}
	}
	
	public boolean isOnline() {
		return isOnline;
	}

	protected void setWifiConnected(boolean wifiConnected) {
		if (wifiConnected == this.wifiConnected) {
			return;
		}
		this.wifiConnected = wifiConnected;
		synchronized (listeners) {
			for (NetworkStateListener l : listeners) {
				l.wifiConnectionChanged(wifiConnected);
			}
		}
	}
	
	public boolean isWifiConnected() {
		return wifiConnected;
	}

	public void dipose() {
		if (mConnectivityReceiver != null) {
			context.unregisterReceiver(mConnectivityReceiver);
			mConnectivityReceiver = null;
		}
	}
	
	public void addListener(NetworkStateListener l) {
		synchronized (listeners) {
			if (!listeners.contains(l)) {
				listeners.add(l);
			}
		}
		l.wifiConnectionChanged(wifiConnected);
		l.onlineStateChanged(isOnline);
	}
	
	public void removeListener(NetworkStateListener l) {
		synchronized (listeners) {
			listeners.remove(l);
		}
	}
}
