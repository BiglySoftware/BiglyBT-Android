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

package com.biglybt.android.util;

import java.lang.reflect.Method;
import java.net.*;
import java.util.*;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.util.Thunk;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.Log;

public class NetworkState
{
	public final static String ETHERNET_SERVICE = "ethernet";

	private static final String TAG = "NetworkState";

	public interface NetworkStateListener
	{
		void onlineStateChanged(boolean isOnline, boolean isOnlineMobile);
	}

	private Object oEthernetManager;

	private BroadcastReceiver mConnectivityReceiver;

	private boolean isOnline;

	@Thunk
	String onlineStateReason;

	private final Application applicationContext;

	private final List<NetworkStateListener> listeners = new ArrayList<>();

	@SuppressLint("WrongConstant")
	public NetworkState(final Application applicationContext) {
		this.applicationContext = applicationContext;

		try {
			//noinspection ResourceType ETHERNET_SERVICE is real! :)
			oEthernetManager = applicationContext.getSystemService(ETHERNET_SERVICE);
		} catch (Throwable ignore) {
		}

		// register BroadcastReceiver on network state changes
		mConnectivityReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
					return;
				}
				boolean online = intent.hasExtra(
						ConnectivityManager.EXTRA_NO_CONNECTIVITY)
								? !intent.getBooleanExtra(
										ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)
								: isOnline(applicationContext);
				setOnline(online, online ? isOnlineMobile() : false);
				onlineStateReason = intent.getStringExtra(
						ConnectivityManager.EXTRA_REASON);
				if (AndroidUtils.DEBUG) {
					if (isOnline(applicationContext) != online) {
						Log.w(TAG,
								"Broadcast online doesn't match our online of " + !online);
					}
					@SuppressWarnings("deprecation")
					NetworkInfo networkInfo = intent.getParcelableExtra(
							ConnectivityManager.EXTRA_NETWORK_INFO);
					NetworkInfo otherNetworkInfo = intent.getParcelableExtra(
							ConnectivityManager.EXTRA_OTHER_NETWORK_INFO);
					Boolean isFailover = intent.hasExtra(
							ConnectivityManager.EXTRA_IS_FAILOVER)
									? intent.getBooleanExtra(
											ConnectivityManager.EXTRA_IS_FAILOVER, false)
									: null;
					Boolean noConnnectivity = intent.hasExtra(
							ConnectivityManager.EXTRA_NO_CONNECTIVITY)
									? intent.getBooleanExtra(
											ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)
									: null;
					String extraInfo = intent.getStringExtra(
							ConnectivityManager.EXTRA_EXTRA_INFO);
					Log.d(TAG, (BiglyBTApp.isCoreProcess() ? "Core" : "App")
							+ "] CONNECTIVITY_ACTION; networkInfo=" + networkInfo.toString());
					Log.d(TAG,
							"otherNetworkInfo=" + otherNetworkInfo + "; reason="
									+ onlineStateReason + "; isFailOver=" + isFailover
									+ "; Online=" + online + "; noConnnectivity="
									+ noConnnectivity + "; extra=" + extraInfo);
					Log.d(TAG, "Active IP=" + getActiveIpAddress());
				}
			}
		};
		boolean online = isOnline(applicationContext);
		setOnline(online, !online ? false : isOnlineMobile());
		final IntentFilter mIFNetwork = new IntentFilter();
		mIFNetwork.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		applicationContext.registerReceiver(mConnectivityReceiver, mIFNetwork);
	}

	@Thunk
	void setOnline(boolean online, boolean onlineMobile) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "setOnline " + online);
		}
		isOnline = online;
		synchronized (listeners) {
			for (NetworkStateListener l : listeners) {
				l.onlineStateChanged(online, onlineMobile);
			}
		}
	}

	public boolean isOnline() {
		return isOnline;
	}

	public void dipose() {
		if (mConnectivityReceiver != null) {
			applicationContext.unregisterReceiver(mConnectivityReceiver);
			mConnectivityReceiver = null;
		}
	}

	public void addListener(NetworkStateListener l) {
		synchronized (listeners) {
			if (!listeners.contains(l)) {
				listeners.add(l);
			}
		}
		l.onlineStateChanged(isOnline, isOnlineMobile());
	}

	public void removeListener(NetworkStateListener l) {
		synchronized (listeners) {
			listeners.remove(l);
		}
	}

	@Thunk
	static boolean isOnline(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
				Context.CONNECTIVITY_SERVICE);
		if (cm == null) {
			return false;
		}
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected()) {
			return true;
		}
		if (netInfo == null) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "no active network");
			}
			//noinspection deprecation
			netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
			if (netInfo != null && netInfo.isConnected()) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "mobile network connected");
				}
				return true;
			}
		}
		return false;
	}

	public boolean hasMobileDataCapability() {
		if (applicationContext.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_TELEPHONY)) {
			return true;
		}

		// could have no phone ability, but still a data plan (somehow..)

		ConnectivityManager cm = (ConnectivityManager) applicationContext.getSystemService(
				Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

		return ni != null;
	}

	public boolean isOnlineMobile() {
		ConnectivityManager cm = (ConnectivityManager) applicationContext.getSystemService(
				Context.CONNECTIVITY_SERVICE);
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

	public String getActiveIpAddress() {
		String ipAddress = "127.0.0.1";

		ConnectivityManager cm = (ConnectivityManager) applicationContext.getSystemService(
				Context.CONNECTIVITY_SERVICE);
		if (cm == null) {
			return ipAddress;
		}
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected()) {

			int netType = netInfo.getType();
			if (netType == ConnectivityManager.TYPE_WIFI) {
				WifiManager wifiManager = (WifiManager) applicationContext.getSystemService(
						Context.WIFI_SERVICE);
				if (wifiManager != null) {
					WifiInfo wifiInfo = wifiManager.getConnectionInfo();
					if (wifiInfo != null) {
						int ipAddressInt = wifiInfo.getIpAddress();

						ipAddress = String.format(Locale.getDefault(), "%d.%d.%d.%d",
								(ipAddressInt & 0xff), (ipAddressInt >> 8 & 0xff),
								(ipAddressInt >> 16 & 0xff), (ipAddressInt >> 24 & 0xff));

						if (AndroidUtils.DEBUG) {
							Log.d("IP address", "activeNetwork Wifi=" + ipAddress);
						}
						return ipAddress;
					}
				}

				return getIpAddress("wlan");
			} else if (netType == ConnectivityManager.TYPE_ETHERNET) {
				if (AndroidUtils.DEBUG) {
					Log.d("IP address", "activeNetwork Ethernet");
				}
				if (oEthernetManager != null) {

					// Try ethernetManager.readConfiguration.getiFaceAddress, if present
					try {
						Method methEthernetConfiguration = oEthernetManager.getClass().getDeclaredMethod(
								"readConfiguration");
						Object oEthernetConfiguration = methEthernetConfiguration.invoke(
								oEthernetManager);
						Method methGetIpAddress = oEthernetConfiguration.getClass().getDeclaredMethod(
								"getIfaceAddress");
						Object oIPAddress = methGetIpAddress.invoke(oEthernetConfiguration);
						if (AndroidUtils.DEBUG) {
							Log.d("IP address", "" + oIPAddress);
						}
						if (oIPAddress instanceof InetAddress) {
							return ((InetAddress) oIPAddress).getHostAddress();
						}
					} catch (NoSuchMethodException ignore) {

					} catch (Throwable e) {
						Log.e("IP address", e.getMessage(), e);
					}

					// Try ethernetManager.getSaveEthConfig.getIpAddress, if present
					// (never have seen this)
					try {
						Method methGetSavedEthConfig = oEthernetManager.getClass().getDeclaredMethod(
								"getSavedEthConfig");
						Object oEthernetDevInfo = methGetSavedEthConfig.invoke(
								oEthernetManager);
						Method methGetIpAddress = oEthernetDevInfo.getClass().getDeclaredMethod(
								"getIpAddress");
						Object oIPAddress = methGetIpAddress.invoke(oEthernetDevInfo);

						Log.d("IP address", "" + oIPAddress);
						if (oIPAddress instanceof String) {
							return (String) oIPAddress;
						}
					} catch (NoSuchMethodException ignore) {
					} catch (Throwable e) {
						Log.e("IP address", e.getMessage(), e);
					}

					return getIpAddress("eth");
				}
			}

			if (AndroidUtils.DEBUG) {
				Log.i("IP address", "activeNetwork=" + netInfo.getTypeName() + "/"
						+ netInfo.getType() + "/" + netInfo.getExtraInfo());
			}
		}
		// best guess
		ipAddress = getIpAddress(null);
		return ipAddress;
	}

	/* Don't need Wifi check -- !isOnlineMobile is better because it includes LAN and Wifi 
	public boolean isWifiConnected() {
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
	*/

	/**
	 * Returns the IP that is "UP", preferring the one that "startsWith"
	 * Returns IP even if none "startsWith"
	 */
	private static String getIpAddress(@Nullable String startsWith) {
		String ipAddress = "127.0.0.1";
		try {
			Enumeration<NetworkInterface> networkInterfaces = getNetworkInterfaces();
			for (; networkInterfaces.hasMoreElements();) {
				NetworkInterface intf = networkInterfaces.nextElement();
				if (intf.getName().startsWith("usb") || !intf.isUp()) {
					// ignore usb and !up
					/*
					if (AndroidUtils.DEBUG) {
						if (startsWith == null || intf.getName().startsWith(startsWith)) {
							Log.d("IP address",
									"IGNORE: " + intf.getDisplayName() + "/" + intf.getName()
											+ "/PtoP=" + intf.isPointToPoint() + "/lb="
											+ intf.isLoopback() + "/up=" + intf.isUp() + "/virtual="
											+ intf.isVirtual());
						}
					}
					*/
					continue;
				}
				String ip = getIpAddress(intf, startsWith);
				if (ip != null) {
					ipAddress = ip;
				}
			}
		} catch (SocketException ex) {
			try {
				NetworkInterface intf = NetworkInterface.getByName(startsWith + "0");
				if (intf == null) {
					Log.e("IPAddress", "Can't get ip address", ex);
					return ipAddress;
				}
				String ip = getIpAddress(intf, startsWith);
				if (ip != null) {
					ipAddress = ip;
				}
			} catch (SocketException e) {
				Log.e("IPAddress", "Can't get ip address", e);
			}
		}
		return ipAddress;
	}

	public static Enumeration<NetworkInterface> getNetworkInterfaces()
			throws SocketException {
		SocketException se;
		try {
			return NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			/*
			Found on API 22 (Sony Bravia Android TV):
			java.net.SocketException
			     at java.net.NetworkInterface.rethrowAsSocketException(NetworkInterface.java:248)
			     at java.net.NetworkInterface.readIntFile(NetworkInterface.java:243)
			     at java.net.NetworkInterface.getByNameInternal(NetworkInterface.java:121)
			     at java.net.NetworkInterface.getNetworkInterfacesList(NetworkInterface.java:309)
			     at java.net.NetworkInterface.getNetworkInterfaces(NetworkInterface.java:298)
			     at whatevercalled getNetworkInterfaces()
			 Caused by: java.io.FileNotFoundException: /sys/class/net/p2p1/ifindex: open failed: ENOENT (No such file or directory)
			     at libcore.io.IoBridge.open(IoBridge.java:456)
			     at libcore.io.IoUtils$FileReader.<init>(IoUtils.java:209)
			     at libcore.io.IoUtils.readFileAsString(IoUtils.java:116)
			     at java.net.NetworkInterface.readIntFile(NetworkInterface.java:236)
			 	... 18 more
			 Caused by: android.system.ErrnoException: open failed: ENOENT (No such file or directory)
			     at libcore.io.Posix.open(Native Method)
			     at libcore.io.BlockGuardOs.open(BlockGuardOs.java:186)
			     at libcore.io.IoBridge.open(IoBridge.java:442)
			 	... 21 more
			 	*/
			se = e;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			List<NetworkInterface> list = new ArrayList<>();
			int i = 0;
			do {
				try {
					NetworkInterface nif = NetworkInterface.getByIndex(i);
					if (nif != null) {
						list.add(nif);
					} else if (i > 0) {
						break;
					}
				} catch (SocketException e) {
					e.printStackTrace();
				}
				i++;
			} while (true);
			if (list.size() > 0) {
				return Collections.enumeration(list);
			}
		}

		// Worst case, try some common interface names
		List<NetworkInterface> list = new ArrayList<>();
		final String[] commonNames = {
			"lo",
			"eth",
			"lan",
			"wlan",
			"en", // Some Android's Ethernet
			"p2p", // Android
			"net", // Windows, usually TAP
			"ppp" // Windows
		};
		for (String commonName : commonNames) {
			try {
				NetworkInterface nif = NetworkInterface.getByName(commonName);
				if (nif != null) {
					list.add(nif);
				}

				// Could interfaces skip numbers?  Oh well..
				int i = 0;
				while (true) {
					nif = NetworkInterface.getByName(commonName + i);
					if (nif != null) {
						list.add(nif);
					} else {
						break;
					}
					i++;
				}
			} catch (Throwable ignore) {
			}
		}
		if (list.size() > 0) {
			return Collections.enumeration(list);
		}

		throw se;
	}

	private static String getIpAddress(NetworkInterface intf, String startsWith)
			throws SocketException {
		Enumeration<InetAddress> inetAddresses = intf.getInetAddresses();
		for (; inetAddresses.hasMoreElements();) {
			InetAddress inetAddress = inetAddresses.nextElement();
			if (!inetAddress.isLoopbackAddress()
					&& (inetAddress instanceof Inet4Address)) {
				String ipAddress = inetAddress.getHostAddress();

				if (AndroidUtils.DEBUG) {
					Log.e("IP address",
							intf.getDisplayName() + "/" + intf.getName() + "/PtoP="
									+ intf.isPointToPoint() + "/lb=" + intf.isLoopback() + "/up="
									+ intf.isUp() + "/virtual=" + intf.isVirtual() + "/"
									+ (inetAddress.isSiteLocalAddress() ? "Local" : "NotLocal")
									+ "/" + ipAddress);
				}
				if (startsWith != null && intf.getName().startsWith(startsWith)) {
					return ipAddress;
				}
			}
		}
		return null;
	}

	public String getOnlineStateReason() {
		return onlineStateReason;
	}
}
