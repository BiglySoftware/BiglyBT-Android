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
