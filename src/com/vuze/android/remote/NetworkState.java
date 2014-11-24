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

import java.lang.reflect.Method;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class NetworkState
{
	public final static String ETHERNET_SERVICE = "ethernet";

	public interface NetworkStateListener
	{
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
				setWifiConnected(isWifiConnected(context));
				setOnline(isOnline(context));
			}
		};
		setOnline(isOnline(context));
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

	private static boolean isOnline(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm == null) {
			return false;
		}
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected()) {
			return true;
		}
		return false;
	}

	public static boolean isOnlineMobile(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm == null) {
			return false;
		}
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected()) {
			int type = netInfo.getType();
			return type == ConnectivityManager.TYPE_MOBILE || type == 4 //ConnectivityManager.TYPE_MOBILE_DUN
					|| type == 5 //ConnectivityManager.TYPE_MOBILE_HIPRI
					|| type == 2 //ConnectivityManager.TYPE_MOBILE_MMS
					|| type == 3; //ConnectivityManager.TYPE_MOBILE_SUPL;
		}
		return false;
	}

	public static String getActiveIpAddress(Context context) {
		String ipAddress = "127.0.0.1";

		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm == null) {
			return ipAddress;
		}
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected()) {

			if (AndroidUtils.DEBUG) {
				Log.e("IP address", "activeNetwork=" + netInfo.getTypeName() + "/"
						+ netInfo.getType() + "/" + netInfo.getExtraInfo());
			}

			int netType = netInfo.getType();
			if (netType == ConnectivityManager.TYPE_WIFI) {
				WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
				if (wifiManager != null) {
					WifiInfo wifiInfo = wifiManager.getConnectionInfo();
					if (wifiInfo != null) {
						int ipAddressInt = wifiInfo.getIpAddress();

						ipAddress = String.format("%d.%d.%d.%d", (ipAddressInt & 0xff),
								(ipAddressInt >> 8 & 0xff), (ipAddressInt >> 16 & 0xff),
								(ipAddressInt >> 24 & 0xff));

						if (AndroidUtils.DEBUG) {
							Log.e("IP address", "activeNetwork Wifi=" + ipAddress);
						}
						return ipAddress;
					}
				}

				return getIpAddress("wlan");
			} else if (netType == ConnectivityManager.TYPE_ETHERNET) {
				if (AndroidUtils.DEBUG) {
					Log.e("IP address", "activeNetwork Ethernet");
				}
				Object oEthernetManager = context.getSystemService(ETHERNET_SERVICE);
				if (oEthernetManager != null) {

					// Try ethernetManager.readConfiguration.getiFaceAddress, if present
					try {
						Method methEthernetConfiguration = oEthernetManager.getClass().getDeclaredMethod(
								"readConfiguration");
						Object oEthernetConfiguration = methEthernetConfiguration.invoke(oEthernetManager);
						Method methGetIpAddress = oEthernetConfiguration.getClass().getDeclaredMethod(
								"getIfaceAddress");
						Object oIPAddress = methGetIpAddress.invoke(oEthernetConfiguration);
						if (AndroidUtils.DEBUG) {
							Log.e("IP address", "" + oIPAddress);
						}
						if (oIPAddress instanceof InetAddress) {
							return ((InetAddress) oIPAddress).getHostAddress();
						}
					} catch (NoSuchMethodException ex) {
						
					} catch (Throwable e) {
						Log.e("IP address", e.getMessage(), e);
					}

					// Try ethernetManager.getSaveEthConfig.getIpAddress, if present
					// (never have seen this)
					try {
						Method methGetSavedEthConfig = oEthernetManager.getClass().getDeclaredMethod(
								"getSavedEthConfig");
						Object oEthernetDevInfo = methGetSavedEthConfig.invoke(oEthernetManager);
						Method methGetIpAddress = oEthernetDevInfo.getClass().getDeclaredMethod(
								"getIpAddress");
						Object oIPAddress = methGetIpAddress.invoke(oEthernetDevInfo);

						Log.e("IP address", "" + oIPAddress);
						if (oIPAddress instanceof String) {
							return (String) oIPAddress;
						}
					} catch (NoSuchMethodException ex) {
					} catch (Throwable e) {
						Log.e("IP address", e.getMessage(), e);
					}

					return getIpAddress("eth");
				}
			}

		}
		// best guess
		ipAddress = getIpAddress(null);
		return ipAddress;
	}

	public static boolean isWifiConnected(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connManager == null) {
			return false;
		}
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (mWifi == null) {
			return false;
		}

		return mWifi.isConnected();
	}

	@SuppressLint("NewApi")
	public static String getIpAddress(String startsWith) {
		String ipAddress = "127.0.0.1";
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				if (intf.getName().startsWith("usb") || !intf.isUp()) {
					// ignore usb and !up
					continue;
				}
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()
							&& (inetAddress instanceof Inet4Address)) {
						ipAddress = inetAddress.getHostAddress().toString();

						if (AndroidUtils.DEBUG) {
							Log.e("IP address",
									intf.getDisplayName()
											+ "/"
											+ intf.getName()
											+ "/PtoP="
											+ intf.isPointToPoint()
											+ "/lb="
											+ intf.isLoopback()
											+ "/up="
											+ intf.isUp()
											+ "/virtual="
											+ intf.isVirtual()
											+ "/"
											+ (inetAddress.isSiteLocalAddress() ? "Local"
													: "NotLocal") + "/" + ipAddress);
						}
						if (startsWith != null && intf.getName().startsWith(startsWith)) {
							return ipAddress;
						}
					}
				}
			}
		} catch (SocketException ex) {
			Log.e("Socket exception in GetIP Address of Utilities", ex.toString());
		}
		return ipAddress;
	}
}
