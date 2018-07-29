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

package com.biglybt.android.client;

import java.util.*;

import com.biglybt.util.Thunk;

import android.util.Log;

import net.grandcentrix.tray.TrayPreferences;
import net.grandcentrix.tray.core.OnTrayPreferenceChangeListener;
import net.grandcentrix.tray.core.TrayItem;

/**
 * <p>
 * Local Core specific preferences.  There will usually be up to 2 active 
 * instances of this class, one for the core process, and one for the ui 
 * process.
 * </p><p>
 * Preference values are persisted via {@link TrayPreferences}, and changes
 * are monitored so multiple CorePref instances stay in sync.
 * </p><p>
 * Some preferences are backed by {@link com.biglybt.core.config.COConfigurationManager}
 * params.  Syncing is handled in {@link com.biglybt.android.client.service.BiglyBTService}
 * and not here.  BiglyBTService will monitor COConfigurationManager for
 * changes, and update TrayPreferences, which will trigger the CorePref classes
 * to update.
 * </p><p>
 * Created by TuxPaper on 4/5/16.
 * </p>
 */
public class CorePrefs
	implements OnTrayPreferenceChangeListener
{
	public static final boolean DEBUG_CORE = AndroidUtils.DEBUG;

	public static final String PREF_CORE_AUTOSTART = "core_autostart";

	public static final String PREF_CORE_ALLOWCELLDATA = "core_allowcelldata";

	public static final String PREF_CORE_DISABLESLEEP = "core_disablesleep";

	public static final String PREF_CORE_ONLYPLUGGEDIN = "core_onlypluggedin";

	public static final String PREF_CORE_ALLOWLANACCESS = "core_allowlanaccess";

	public static final String PREF_CORE_RACCESS_REQPW = "core_raccess_reqpw";

	public static final String PREF_CORE_RACCESS_USER = "core_raccess_user";

	public static final String PREF_CORE_RACCESS_PW = "core_raccess_pw";

	public static final String PREF_CORE_PROXY_TRACKERS = "core_proxy_trackers";

	public static final String PREF_CORE_PROXY_DATA = "core_proxy_data";

	public static final String PREF_CORE_PROXY_TYPE = "core_proxy_type";

	public static final String PREF_CORE_PROXY_HOST = "core_proxy_host";

	public static final String PREF_CORE_PROXY_PORT = "core_proxy_port";

	public static final String PREF_CORE_PROXY_USER = "core_proxy_user";

	public static final String PREF_CORE_PROXY_PW = "core_proxy_pw";

	@Thunk
	static final String TAG = "BiglyBTCorePrefs";

	public interface CorePrefsChangedListener
	{
		void corePrefAutoStartChanged(boolean autoStart);

		void corePrefAllowCellDataChanged(boolean allowCellData);

		void corePrefDisableSleepChanged(boolean disableSleep);

		void corePrefOnlyPluggedInChanged(boolean onlyPluggedIn);

		void corePrefProxyChanged(CoreProxyPreferences prefProxy);

		void corePrefRemAccessChanged(CoreRemoteAccessPreferences prefRemoteAccess);
	}

	private final List<CorePrefsChangedListener> changedListeners = new ArrayList<>();

	private Boolean prefAllowCellData = null;

	private Boolean prefDisableSleep = null;

	private Boolean prefAutoStart = null;

	private Boolean prefOnlyPluggedIn = null;

	private CoreProxyPreferences prefProxy = null;

	private CoreRemoteAccessPreferences raPrefs = null;

	private final List<String> listPendingPrefChanges = new ArrayList<>();

	private Thread threadGroupPrefChanges = null;

	private static CorePrefs corePrefs = null;

	public synchronized static CorePrefs getInstance() {
		if (corePrefs == null) {
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "getInstance: COREPREFS");
			}
			corePrefs = new CorePrefs();
		}
		return corePrefs;
	}

	private CorePrefs() {
		final TrayPreferences preferences = BiglyBTApp.getAppPreferences().preferences;
		preferences.registerOnTrayPreferenceChangeListener(this);
		loadPref(preferences);
	}

	public void addChangedListener(CorePrefsChangedListener changedListener,
			boolean trigger) {
		if (!changedListeners.contains(changedListener)) {
			changedListeners.add(changedListener);
		}
		if (changedListener != null && trigger) {
			changedListener.corePrefAllowCellDataChanged(prefAllowCellData);
			changedListener.corePrefOnlyPluggedInChanged(prefOnlyPluggedIn);
			changedListener.corePrefDisableSleepChanged(prefDisableSleep);
			changedListener.corePrefAutoStartChanged(prefAutoStart);
			changedListener.corePrefRemAccessChanged(raPrefs);
			changedListener.corePrefProxyChanged(prefProxy);
		}
	}

	public void removeChangedListener(CorePrefsChangedListener l) {
		changedListeners.remove(l);
	}

	public Boolean getPrefAllowCellData() {
		return prefAllowCellData;
	}

	public Boolean getPrefDisableSleep() {
		return prefDisableSleep;
	}

	public Boolean getPrefOnlyPluggedIn() {
		return prefOnlyPluggedIn;
	}

	public Boolean getPrefAutoStart() {
		return prefAutoStart;
	}

	private void setOnlyPluggedIn(boolean b) {
		if (prefOnlyPluggedIn == null || b != prefOnlyPluggedIn) {
			prefOnlyPluggedIn = b;
			for (CorePrefsChangedListener changedListener : changedListeners) {
				changedListener.corePrefOnlyPluggedInChanged(b);
			}
		}
	}

	private void setDisableSleep(boolean b) {
		if (prefDisableSleep == null || b != prefDisableSleep) {
			prefDisableSleep = b;
			for (CorePrefsChangedListener changedListener : changedListeners) {
				changedListener.corePrefDisableSleepChanged(b);
			}
		}
	}

	private void setAutoStart(boolean b) {
		if (prefAutoStart == null || b != prefAutoStart) {
			prefAutoStart = b;
			for (CorePrefsChangedListener changedListener : changedListeners) {
				changedListener.corePrefAutoStartChanged(b);
			}
		}
	}

	private void setAllowCellData(boolean b) {
		if (prefAllowCellData == null || b != prefAllowCellData) {
			prefAllowCellData = b;
			for (CorePrefsChangedListener changedListener : changedListeners) {
				changedListener.corePrefAllowCellDataChanged(b);
			}
		}
	}

	private void loadPref(TrayPreferences prefs, String... keys) {
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, this + "] loadPref: " + Arrays.toString(keys) + " "
					+ AndroidUtils.getCompressedStackTrace());
		}
		boolean all = keys == null || keys.length == 0;
		boolean isProxy = all;
		if (!all) {
			Arrays.sort(keys);
			for (String key : keys) {
				if (key.startsWith("core_proxy")) {
					isProxy = true;
					break;
				}
			}
		}
		if (all || Arrays.binarySearch(keys, PREF_CORE_ALLOWCELLDATA) == 0) {
			setAllowCellData(prefs.getBoolean(PREF_CORE_ALLOWCELLDATA, false));
		}
		if (all || Arrays.binarySearch(keys, PREF_CORE_AUTOSTART) == 0) {
			setAutoStart(prefs.getBoolean(PREF_CORE_AUTOSTART, true));
		}
		if (all || Arrays.binarySearch(keys, PREF_CORE_DISABLESLEEP) == 0) {
			setDisableSleep(prefs.getBoolean(PREF_CORE_DISABLESLEEP, true));
		}
		if (all || Arrays.binarySearch(keys, PREF_CORE_ONLYPLUGGEDIN) == 0) {
			setOnlyPluggedIn(prefs.getBoolean(PREF_CORE_ONLYPLUGGEDIN, false));
		}
		if (all || Arrays.binarySearch(keys, PREF_CORE_ALLOWLANACCESS) == 0
				|| Arrays.binarySearch(keys, PREF_CORE_RACCESS_REQPW) == 0
				|| Arrays.binarySearch(keys, PREF_CORE_RACCESS_USER) == 0
				|| Arrays.binarySearch(keys, PREF_CORE_RACCESS_PW) == 0) {
			setRemAccessPrefs(prefs.getBoolean(PREF_CORE_ALLOWLANACCESS, false),
					prefs.getBoolean(PREF_CORE_RACCESS_REQPW, false),
					prefs.getString(PREF_CORE_RACCESS_USER, "biglybt"), prefs.getString(
							PREF_CORE_RACCESS_PW, AndroidUtils.generateEasyPW(4)));
		}
		if (all || isProxy) {
			setProxyPreferences(prefs.getBoolean(PREF_CORE_PROXY_TRACKERS, false),
					prefs.getBoolean(PREF_CORE_PROXY_DATA, false),
					prefs.getString(PREF_CORE_PROXY_TYPE, ""),
					prefs.getString(PREF_CORE_PROXY_HOST, ""),
					prefs.getInt(PREF_CORE_PROXY_PORT, 0),
					prefs.getString(PREF_CORE_PROXY_USER, ""),
					prefs.getString(PREF_CORE_PROXY_PW, ""));
		}
	}

	@Override
	public void onTrayPreferenceChanged(Collection<TrayItem> items) {
		synchronized (listPendingPrefChanges) {
			for (TrayItem item : items) {
				listPendingPrefChanges.add(item.key());
			}
			if (threadGroupPrefChanges == null) {
				threadGroupPrefChanges = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							int oldCount;
							int newCount;
							do {
								synchronized (listPendingPrefChanges) {
									oldCount = listPendingPrefChanges.size();
								}
								Thread.sleep(100);
								synchronized (listPendingPrefChanges) {
									newCount = listPendingPrefChanges.size();
								}
							} while (oldCount != newCount);
						} catch (InterruptedException e) {
						}
						synchronized (listPendingPrefChanges) {
							final String[] keys = listPendingPrefChanges.toArray(
									new String[listPendingPrefChanges.size()]);
							final TrayPreferences preferences = BiglyBTApp.getAppPreferences().preferences;
							loadPref(preferences, keys);
							listPendingPrefChanges.clear();
							threadGroupPrefChanges = null;
						}
					}
				}, "threadGroupPrefChanges");
				threadGroupPrefChanges.start();
			}
		}
	}

	@Override
	protected void finalize()
			throws Throwable {
		// Shouldn't be needed, but https://github.com/grandcentrix/tray/issues/124
		try {
			final TrayPreferences preferences = BiglyBTApp.getAppPreferences().preferences;
			if (preferences != null) {
				preferences.unregisterOnTrayPreferenceChangeListener(this);
			}
		} catch (Throwable ignore) {
		}

		super.finalize();
	}

	public CoreRemoteAccessPreferences getRemoteAccessPreferences() {
		return raPrefs;
	}

	private void setRemAccessPrefs(boolean allowLANAccess, boolean reqPW,
			String user, String pw) {
		setRemAccessPrefs(
				new CoreRemoteAccessPreferences(allowLANAccess, reqPW, user, pw));
	}

	private void setRemAccessPrefs(CoreRemoteAccessPreferences newPrefs) {
		boolean changed = false;

		if (raPrefs == null) {
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "setRemAccessCreds: no prefRemoteAccess");
			}
			changed = true;
		} else {
			if (raPrefs.allowLANAccess != newPrefs.allowLANAccess) {
				changed = true;
			}
			if (raPrefs.reqPW != newPrefs.reqPW) {
				changed = true;
			}
			if (raPrefs.user == null || !raPrefs.user.equals(newPrefs.user)) {
				changed = true;
			}

			if (raPrefs.pw == null || !raPrefs.pw.equals(newPrefs.pw)) {
				changed = true;
			}
		}

		if (changed) {
			raPrefs = (CoreRemoteAccessPreferences) newPrefs.clone();
			for (CorePrefsChangedListener changedListener : changedListeners) {
				changedListener.corePrefRemAccessChanged(raPrefs);
			}
		}
	}

	public CoreProxyPreferences getProxyPreferences() {
		return (CoreProxyPreferences) prefProxy.clone();
	}

	private void setProxyPreferences(boolean proxyTrackers,
			boolean proxyOutGoingPeers, String proxyType, String host, int port,
			String user, String pw) {
		setProxyPreferences(new CoreProxyPreferences(proxyTrackers,
				proxyOutGoingPeers, proxyType, host, port, user, pw));
	}

	private void setProxyPreferences(CoreProxyPreferences newPrefs) {
		boolean changed = false;

		if (prefProxy == null) {
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "setProxyPreferences: no prefProxy");
			}
			prefProxy = (CoreProxyPreferences) newPrefs.clone();
			changed = true;
		} else {

			if (!prefProxy.proxyType.equals(newPrefs.proxyType)) {
				prefProxy.proxyType = newPrefs.proxyType;
				changed = true;
			}

			if (!prefProxy.host.equals(newPrefs.host)) {
				prefProxy.host = newPrefs.host;
				changed = true;
			}

			if (!prefProxy.user.equals(newPrefs.user)) {
				prefProxy.user = newPrefs.user;
				changed = true;
			}

			if (!prefProxy.pw.equals(newPrefs.pw)) {
				prefProxy.pw = newPrefs.pw;
				changed = true;
			}

			if (prefProxy.proxyTrackers != newPrefs.proxyTrackers) {
				prefProxy.proxyTrackers = newPrefs.proxyTrackers;
				changed = true;
			}

			if (prefProxy.proxyOutgoingPeers != newPrefs.proxyOutgoingPeers) {
				prefProxy.proxyOutgoingPeers = newPrefs.proxyOutgoingPeers;
				changed = true;
			}

			if (prefProxy.port != newPrefs.port) {
				prefProxy.port = newPrefs.port;
				changed = true;
			}
		}

		if (CorePrefs.DEBUG_CORE && changedListeners.size() > 0) {
			Log.d(TAG, "setProxyPreferences: changed? " + changed + ";via "
					+ AndroidUtils.getCompressedStackTrace());
		}

		if (changed) {
			for (CorePrefsChangedListener changedListener : changedListeners) {
				changedListener.corePrefProxyChanged(prefProxy);
			}
		}

	}

}
