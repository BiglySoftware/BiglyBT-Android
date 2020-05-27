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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.res.Resources;
import android.net.wifi.WifiManager;
import android.os.*;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;

import com.biglybt.android.client.*;
import com.biglybt.android.client.CorePrefs.CorePrefsChangedListener;
import com.biglybt.android.client.activity.IntentHandler;
import com.biglybt.android.client.rpc.RPC;
import com.biglybt.android.core.az.BiglyBTManager;
import com.biglybt.android.util.NetworkState;
import com.biglybt.android.util.NetworkState.NetworkStateListener;
import com.biglybt.core.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerEnhancer;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.global.GlobalManagerStats;
import com.biglybt.core.networkmanager.admin.NetworkAdmin;
import com.biglybt.core.tag.*;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginManager;
import com.biglybt.update.CorePatchChecker;
import com.biglybt.update.UpdaterUpdateChecker;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.InitialisationFunctions;
import com.biglybt.util.Thunk;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Android Service handling launch and shutting down BiglyBT core as well as
 * the notification section
 * <p/>
 * Typically instantiated by {@link BiglyBTServiceInitImpl}.  Lifecycle:<br>
 * 1) BiglyBTServiceInit starts and binds to service
 * <br>
 * 2) Service start {@link #onStartCommand(Intent, int, int)}
 * <br>
 * 3) Notification entry created
 * <br>
 * 4) BiglyBTServiceInit receives {@link ServiceConnection#onServiceConnected(ComponentName, IBinder)}
 * <br>
 * 5) BiglyBTServiceInit calls {@link IBiglyCoreInterface#addListener(IBiglyCoreCallback)} to listen to the MSG_OUT_* actions
 * <br>
 * 6) BiglyBTService sends {@link #MSG_OUT_REPLY_ADD_LISTENER} with state of core (restarting, stopping, stopped, ready-to-start)
 * <br>
 * 7) If reply is ready-to-start, BiglyBTServiceInit starts the core
 * <br>
 * 8) Event {@link #MSG_OUT_CORE_STARTED} fired
 * <br>
 * 9) Event {@link #MSG_OUT_WEBUI_STARTED} fired.
 * <br>
 * 10) Service is requested to stopped by:<br>
 * 10.a) Android OS.  We'll get an {@link #onDestroy()} which stop core on
 *       a separate thread, hopefully before Android kills the thread.
 *       Since onDestroy() is on the main thread, a on-thread shutdown
 *       of core will cause an ANR if it takes too long (30s?), so we spawn
 *       off a new thread to shut down and hope Android doesn't kill the thread
 *       (it doesn't seem to, but probably will when there's low memory/battery
 *       or shutting down<br>
 * 10.b) {@link BiglyBTServiceInitImpl#stopService()} which calls {@link #stopSelfAndNotify()}.
 *       This will shutdown the core before calling stopSelf to shut down the service<br>
 * 10.c) The BiglyBT Core itself initiates a shutdown (stop) via 
 *      {@link ServiceCoreLifecycleAdapter#stopped(Core)} and notifies this 
 *      service, which invokes stopSelf.<br>
 * <br>
 * 11) Service {@link #onDestroy()} called by Android OS
 * <br>
 * 12) If BiglyBT core is not already stopping (10.a), BiglyBT Core stop is 
 *     called.
 * <br>
 * 13) When BiglyBT core is done stopping, {@link #MSG_OUT_CORE_STOPPED} event is 
 *     sent to BiglyBTServiceInit
 * <br>
 * 14) If there is request for the BiglyBT Core to be restarted, Service is restarted
 *     via {@link #startService(Intent)}
 * <br>
 * 15) Event {@link #MSG_OUT_SERVICE_DESTROY} is sent
 * <br>
 * 16) System.exit(0) is called to ensure Android does not keep
 *     the service thread around for super smart caching.  It would be nice
 *     to have a flag on a Service that said "kill the thread after onDestroy".
 *     Since BiglyBT Core uses a lot of static variables and does not fully clean
 *     them up, we can't guarantee all BiglyBT Core classes are disposed of unless
 *     we kill the threads.
 * <br>
 * <p/>
 * Goals:
 * - Ability to shut down core when 0 active torrents and no UI attached
 * <p/>
 * Created by TuxPaper on 3/24/16.
 */
@SuppressLint("Registered") // False Positive: Registered in coreFlavor
public class BiglyBTService
	extends Service
	implements NetworkStateListener, CorePrefsChangedListener
{
	private static final boolean HIGH_CPU_SHUTDOWN = false;

	public static final int MSG_OUT_REPLY_ADD_LISTENER = 10;

	public static final int MSG_OUT_CORE_STARTED = 100;

	public static final int MSG_OUT_CORE_STOPPED = 200;

	public static final int MSG_OUT_WEBUI_STARTED = 300;

	public static final int MSG_OUT_CORE_STOPPING = 150;

	public static final int MSG_OUT_SERVICE_DESTROY = 400;

	static final String TAG = "BiglyBTService";

	public static final String INTENT_ACTION_START = "com.biglybt.android.client.START_SERVICE";

	public static final String INTENT_ACTION_STOP = "com.biglybt.android.client.STOP_SERVICE";

	public static final String INTENT_ACTION_RESTART = "com.biglybt.android.client.RESTART_SERVICE";

	private static final String INTENT_ACTION_PAUSE = "com.biglybt.android.client.PAUSE_TORRENTS";

	private static final String INTENT_ACTION_RESUME = "com.biglybt.android.client.RESUME_TORRENTS";

	private static final String WIFI_LOCK_TAG = "biglybt power lock";

	public static final String DEFAULT_WEBUI_PW_DISABLED_WHITELIST = "localhost,127.0.0.1,[::1],$";

	public static final String DEFAULT_WEBUI_PW_LAN_ONLY = DEFAULT_WEBUI_PW_DISABLED_WHITELIST
			+ ",192.168.0.0-192.168.255.255,10.0.0.0-10.255.255.255,172.16.0.0-172.31.255.255";

	private static final String NOTIFICATION_CHANNEL_ID = "service";

	private static final int NOTIFICATION_ID = 1;

	@SuppressWarnings("RedundantThrows")
	private final IBiglyCoreInterface.Stub mBinder = new IBiglyCoreInterface.Stub() {

		@Override
		public boolean addListener(IBiglyCoreCallback callback)
				throws RemoteException {
			IBinder binder = callback.asBinder();
			boolean added = mapListeners.containsKey(binder);
			if (!added) {
				synchronized (mapListeners) {
					mapListeners.put(binder, callback);
				}
			}

			if (CorePrefs.DEBUG_CORE) {
				logd("handleMessage: ADD_LISTENER(" + callback + ", " + binder
						+ "). coreStarted? " + coreStarted + "; webUIStarted? "
						+ webUIStarted + "; alreadyAdded? " + added);
			}
			String state;
			if (isCoreStopping || isServiceStopping) {
				state = restartService ? "restarting" : "stopping";
			} else if (coreStarted) {
				state = "started";
			} else {
				state = "ready-to-start";
			}

			Map<String, Object> map = new HashMap<>();
			map.put("data", "MSG_OUT_REPLY_ADD_LISTENER");
			map.put("state", state);
			map.put("restarting", restartService);
			sendStuff(MSG_OUT_REPLY_ADD_LISTENER, map);

			if (coreStarted) {
				sendStuff(MSG_OUT_CORE_STARTED, "MSG_OUT_CORE_STARTED");
			}
			if (webUIStarted) {
				sendStuff(MSG_OUT_WEBUI_STARTED, "MSG_OUT_WEBUI_STARTED");
			}

			return added;
		}

		@Override
		public void startCore()
				throws RemoteException {
			if (isServiceStopping || restartService) {
				if (CorePrefs.DEBUG_CORE) {
					logd("handleMessage: ignoring START_CORE as service is stopping ("
							+ isServiceStopping + ") or restarting");
				}
				return;
			}
			if (CorePrefs.DEBUG_CORE) {
				logd("handleMessage: START_CORE. coreStarted? " + coreStarted
						+ "; webUIStarted? " + webUIStarted);
			}
			if (!coreStarted) {
				new Thread(BiglyBTService.this::startCore).start();
			}
		}

		@Override
		public boolean removeListener(IBiglyCoreCallback callback) {
			IBinder binder = callback.asBinder();
			boolean removed;
			synchronized (mapListeners) {
				removed = mapListeners.remove(binder) != null;
			}
			if (CorePrefs.DEBUG_CORE) {
				logd("handleMessage: REMOVE_LISTENER(" + callback + ", " + binder + ") "
						+ (removed ? "success" : "failure") + ". # clients "
						+ mapListeners.size());
				if (!removed) {
					loge("removeListener: callback=" + callback + "/binder=" + binder
							+ "; list=" + mapListeners, null);
				}
			}
			return removed;
		}

		@Override
		public int getParamInt(String key)
				throws RemoteException {
			return COConfigurationManager.getIntParameter(key);
		}

		@Override
		public boolean setParamInt(String key, int val)
				throws RemoteException {
			return COConfigurationManager.setParameter(key, val);
		}

		@Override
		public long getParamLong(String key)
				throws RemoteException {
			return COConfigurationManager.getLongParameter(key);
		}

		@Override
		public boolean setParamLong(String key, long val)
				throws RemoteException {
			return COConfigurationManager.setParameter(key, val);
		}

		@Override
		public float getParamFloat(String key)
				throws RemoteException {
			return COConfigurationManager.getFloatParameter(key);
		}

		@Override
		public boolean setParamFloat(String key, float val)
				throws RemoteException {
			return COConfigurationManager.setParameter(key, val);
		}

		@Override
		public String getParamString(String key)
				throws RemoteException {
			return COConfigurationManager.getStringParameter(key);
		}

		@Override
		public boolean setParamString(String key, String val)
				throws RemoteException {
			return COConfigurationManager.setParameter(key, val);
		}

		@Override
		public boolean getParamBool(String key)
				throws RemoteException {
			return COConfigurationManager.getBooleanParameter(key);
		}

		@Override
		public boolean setParamBool(String key, boolean val)
				throws RemoteException {
			return COConfigurationManager.setParameter(key, val);
		}
	};

	@Thunk
	class ScreenReceiver
		extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action == null) {
				return;
			}
			if (action.equals(Intent.ACTION_SCREEN_OFF)) {
				screenOff = true;
			} else if (action.equals(Intent.ACTION_SCREEN_ON)) {
				screenOff = false;
				updateNotification();
			}
		}
	}

	@Thunk
	class ServiceCoreLifecycleAdapter
		extends CoreLifecycleAdapter
	{
		@NonNull
		final CorePrefs corePrefs;

		boolean startedCalled = false;

		public ServiceCoreLifecycleAdapter(@NonNull CorePrefs corePrefs) {
			this.corePrefs = corePrefs;
		}

		@Override
		public void started(Core core) {
			if (startedCalled) {
				return;
			}
			startedCalled = true;
			// not called if listener is added after core is started!
			if (CorePrefs.DEBUG_CORE) {
				logd("started: core");
			}

			coreStarted = true;

			disableUpdater();

			sendStuff(MSG_OUT_CORE_STARTED, "MSG_OUT_CORE_STARTED");

			updateNotification();

			core.getGlobalManager().addListener(new GlobalManagerListener() {

				@Override
				public void downloadManagerAdded(DownloadManager dm) {
					if (lowResourceMode && !dm.getAssumedComplete()) {
						int state = dm.getState();
						if (state == DownloadManager.STATE_QUEUED
								|| state == DownloadManager.STATE_ALLOCATING
								|| state == DownloadManager.STATE_CHECKING
								|| state == DownloadManager.STATE_DOWNLOADING
								|| state == DownloadManager.STATE_INITIALIZING
								|| state == DownloadManager.STATE_INITIALIZED
								|| state == DownloadManager.STATE_READY
								|| state == DownloadManager.STATE_WAITING) {

							AERunStateHandler.setResourceMode(
									AERunStateHandler.RS_ALL_ACTIVE);
							lowResourceMode = false;

							if (CorePrefs.DEBUG_CORE) {
								logd(
										"downloadManagerAdded: non-stopped download; turning off low resource mode");
							}
						}
					}

				}

				@Override
				public void downloadManagerRemoved(DownloadManager dm) {

				}

				@Override
				public void destroyInitiated() {

				}

				@Override
				public void destroyed() {
				}

				@Override
				public void seedingStatusChanged(boolean seeding_only_mode,
						boolean potentially_seeding_only_mode) {
					BiglyBTService.this.seeding_only_mode = seeding_only_mode;
					if (CorePrefs.DEBUG_CORE) {
						logd("seedingStatusChanged: " + seeding_only_mode);
					}

					if (seeding_only_mode) {
						releasePowerLock();
					} else {
						adjustPowerLock(corePrefs);
					}
				}
			});
		}

		private void disableUpdater() {
			// disable the core component updaters otherwise they block plugin
			// updates

			PluginManager pm = core.getPluginManager();

			PluginInterface pi = pm.getPluginInterfaceByClass(CorePatchChecker.class);

			if (pi != null) {

				pi.getPluginState().setDisabled(true);
			}

			pi = pm.getPluginInterfaceByClass(UpdaterUpdateChecker.class);

			if (pi != null) {

				pi.getPluginState().setDisabled(true);
			}

			/*
			pm.getDefaultPluginInterface().addListener(new PluginAdapter() {
			
				@Override
				public void initializationComplete() {
					//checkUpdates();
				}
			
				@Override
				public void closedownInitiated() {
				}
			
				@Override
				public void closedownComplete() {
				}
			});
			 */
		}

		@Override
		public void componentCreated(Core core, CoreComponent component) {
			// GlobalManager is always called, even if already created
			if (component instanceof GlobalManager) {
				if (DownloadManagerEnhancer.getSingleton() == null) {
					InitialisationFunctions.earlyInitialisation(core);
				}

				if (CorePrefs.DEBUG_CORE) {
					String s = NetworkAdmin.getSingleton().getNetworkInterfacesAsString();
					logd("started: " + s);
				}

			}

			if (component instanceof PluginInterface) {
				String pluginID = ((PluginInterface) component).getPluginID();
				if (CorePrefs.DEBUG_CORE) {
					logd("plugin " + pluginID + " started");
				}

				if ("xmwebui".equals(pluginID)) {
					webUIStarted = true;
					sendStuff(MSG_OUT_WEBUI_STARTED, "MSG_OUT_WEBUI_STARTED");
					updateNotification();
				}
			} else {
				logd("component " + component.getClass().getSimpleName() + " started");
			}
		}

		@Override
		public void stopped(Core core) {
			BiglyBTService.this.core = null;

			if (CorePrefs.DEBUG_CORE) {
				logd("AZCoreLifeCycle:stopped: start");
			}

			if (HIGH_CPU_SHUTDOWN) {
				useCPU(10);
			}

			core.removeLifecycleListener(this);

			NetworkState networkState = BiglyBTApp.getNetworkState();
			networkState.removeListener(BiglyBTService.this);

			Map bundle = new HashMap();
			bundle.put("data", "MSG_OUT_CORE_STOPPED");
			bundle.put("restarting", restartService);
			sendStuff(MSG_OUT_CORE_STOPPED, bundle);
			msgOutCoreStoppedCalled = true;

			if (CorePrefs.DEBUG_CORE) {
				logd("AZCoreLifeCycle:stopped: done");
			}

			if (isServiceDestroyed) {
				if (CorePrefs.DEBUG_CORE) {
					logd("AZCoreLifeCycle:stopped: system.exit");
				}
				System.exit(0);
			} else {
				if (CorePrefs.DEBUG_CORE) {
					logd("AZCoreLifeCycle:stopped: stopself");
				}
				stopSelf();
			}
		}

		@Override
		public void stopping(Core core) {
			if (CorePrefs.DEBUG_CORE) {
				logd("stopping: core");
			}

			isCoreStopping = true;

			AnalyticsTracker.getInstance().stop();

			Map bundle = new HashMap();
			bundle.put("data", "MSG_OUT_CORE_STOPPING");
			bundle.put("restarting", restartService);
			sendStuff(MSG_OUT_CORE_STOPPING, bundle);
			releasePowerLock();

			updateNotification();

			if (HIGH_CPU_SHUTDOWN) {
				useCPU(10);
			}
		}

		@Override
		public boolean stopRequested(Core core)
				throws CoreException {
			stopSelfAndNotify();
			return true;
		}

		@Override
		public boolean restartRequested(Core core)
				throws CoreException {
			reallyRestartService();
			return true;
		}
	}

	@Thunk
	void useCPU(int secs) {
		for (int i = 0; i < secs; i++) {
			long startOn = SystemClock.uptimeMillis();
			do {
				double d = Math.random() * 123281.1231;
				for (int j = 0; j < 10000; j++) {
					d = Math.sqrt((d * d) * Math.random()) / (Math.random() + 0.1);
				}

				try {
					Thread.sleep((long) (Math.random() * 10));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} while (SystemClock.uptimeMillis() - startOn < 1000);
			logd("useCPU: Slept for " + (i + 1) + " of " + secs + " secs");
		}
	}

	@SuppressLint("LogConditional")
	@Thunk
	void logd(String s) {
		Log.d(TAG, getLogHeader() + s);
	}

	@SuppressLint("LogConditional")
	private void logw(String s) {
		Log.w(TAG, getLogHeader() + s);
	}

	@SuppressLint("LogConditional")
	private void loge(String s, Throwable t) {
		if (t == null) {
			Log.e(TAG, getLogHeader() + s);
		} else {
			Log.e(TAG, getLogHeader() + s, t);
		}
	}

	private String getLogHeader() {
		return (core == null ? "c" : "C") + (biglyBTManager == null ? "m" : "M")
				+ "," + (isServiceDestroyed ? "S:D" : isServiceStopping ? "S:S" : "S:A")
				+ "," + (isCoreStopping ? "C:S" : "S:D") + ","
				+ Thread.currentThread().getName() + "] ";
	}

	@Thunk
	Core core = null;

	private static File biglybtCoreConfigRoot = null;

	private boolean skipBind = false;

	@NonNull
	final Map<IBinder, IBiglyCoreCallback> mapListeners = new HashMap<>();

	@Thunk
	BiglyBTManager biglyBTManager;

	@Thunk
	static boolean isCoreStopping;

	/**
	 * Static, because we exitVM on shutdown of the service to ensure
	 * all those static instances BiglyBT core made aren't sticking around.
	 * This variable is used on BiglyBTService creation to detect issues.  ie.
	 * If we find a Core available and restartService is false, the
	 * user probably tried to start up the core right after shutting it down
	 */
	@Thunk
	static boolean restartService = false;

	@Thunk
	boolean seeding_only_mode;

	private Boolean lastOnlineMobile = null;

	private Boolean lastOnline = null;

	private boolean bindToLocalHost = false;

	private int bindToLocalHostReasonID = R.string.core_noti_sleeping;

	private boolean allowNotificationUpdate = true;

	@Thunk
	boolean isServiceStopping;

	@Thunk
	boolean isServiceDestroyed;

	private WifiManager.WifiLock wifiLock = null;

	private BroadcastReceiver batteryReceiver = null;

	@Thunk
	boolean lowResourceMode = true;

	@Thunk
	boolean coreStarted;

	@Thunk
	boolean webUIStarted;

	@Thunk
	boolean msgOutCoreStoppedCalled = false;

	private static Object staticVar = null;

	@Thunk
	boolean screenOff = false;

	private ScreenReceiver screenReceiver;

	/**
	 * Can we actually display notifications?  Who knows, can't find an API for it
	 * Tried:
	 * - NotificationManager.isNotificationPolicyAccessGranted() but it always returns false
	 * - Checking if there's an activity for Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS) also fails (null)
	 */
	private static final boolean canDisplayNotifications = true;

	public BiglyBTService() {
		super();
		if (CorePrefs.DEBUG_CORE) {
			logd("BiglyBTService: Init Class. restarting=" + restartService + "/"
					+ staticVar);
		}
		if (staticVar != null) {
			restartService = true;
			return;
		}
		coreStarted = false;
		webUIStarted = false;
		new Thread(
				() -> CorePrefs.getInstance().addChangedListener(this, true)).start();
		if (CorePrefs.DEBUG_CORE) {
			logd("BiglyBTService: Init Class ");
		}

	}

	private static boolean wouldBindToLocalHost(@NonNull CorePrefs corePrefs) {
		NetworkState networkState = BiglyBTApp.getNetworkState();
		if (corePrefs.getPrefOnlyPluggedIn()
				&& !AndroidUtils.isPowerConnected(BiglyBTApp.getContext())) {
			return true;
		} else if (!corePrefs.getPrefAllowCellData()
				&& networkState.isOnlineMobile()) {
			return true;
		} else if (!networkState.isOnline()) {
			return true;
		}
		return false;
	}

	private void buildCustomFile(@NonNull CorePrefs corePrefs) {
		File biglybtCustomDir = new File(biglybtCoreConfigRoot, "custom");
		biglybtCustomDir.mkdirs();
		try {
			File configFile = new File(biglybtCustomDir, "BiglyBT_Start.config");
			FileWriter fw = new FileWriter(configFile, false);

			fw.write("Send\\ Version\\ Info=bool:false\n");

			NetworkState networkState = BiglyBTApp.getNetworkState();
			bindToLocalHost = false;
			if (corePrefs.getPrefOnlyPluggedIn()
					&& !AndroidUtils.isPowerConnected(BiglyBTApp.getContext())) {
				bindToLocalHost = true;
				bindToLocalHostReasonID = R.string.core_noti_sleeping_battery;
			} else if (!corePrefs.getPrefAllowCellData()
					&& networkState.isOnlineMobile()) {
				bindToLocalHost = true;
				bindToLocalHostReasonID = R.string.core_noti_sleeping_oncellular;
			} else if (!networkState.isOnline()) {
				bindToLocalHost = true;
				bindToLocalHostReasonID = R.string.core_noti_sleeping;
			}

			fw.write("Plugin.xmwebui.Port=long:" + RPC.LOCAL_BIGLYBT_PORT + "\n");
			CoreRemoteAccessPreferences raPrefs = corePrefs.getRemoteAccessPreferences();
			if (raPrefs.allowLANAccess) {
				writeLine(fw, paramToCustom(CoreParamKeys.SPARAM_XMWEBUI_BIND_IP, ""));
				writeLine(fw,
						paramToCustom(CoreParamKeys.SPARAM_XMWEBUI_PW_DISABLED_WHITELIST,
								DEFAULT_WEBUI_PW_LAN_ONLY));
			} else {
				writeLine(fw,
						paramToCustom(CoreParamKeys.SPARAM_XMWEBUI_BIND_IP, "127.0.0.1"));
				writeLine(fw,
						paramToCustom(CoreParamKeys.SPARAM_XMWEBUI_PW_DISABLED_WHITELIST,
								DEFAULT_WEBUI_PW_DISABLED_WHITELIST));
			}
			writeLine(fw, paramToCustom("Plugin.xmwebui.xmwebui.trace",
					CorePrefs.DEBUG_CORE && false));
			writeLine(fw,
					paramToCustom(CoreParamKeys.BPARAM_XMWEBUI_UPNP_ENABLE, false));
			writeLine(fw,
					paramToCustom(CoreParamKeys.BPARAM_XMWEBUI_PW_ENABLE, raPrefs.reqPW));
			writeLine(fw,
					paramToCustom(CoreParamKeys.SPARAM_XMWEBUI_USER, raPrefs.user));
			writeLine(fw,
					paramToCustom(CoreParamKeys.SPARAM_XMWEBUI_PW, raPrefs.getSHA1pw()));
			writeLine(fw,
					paramToCustom(CoreParamKeys.BPARAM_XMWEBUI_PAIRING_AUTO_AUTH, false));

			if (bindToLocalHost) {
				writeLine(fw,
						paramToCustom(CoreParamKeys.BPARAM_ENFORCE_BIND_IP, true));
				writeLine(fw,
						paramToCustom(CoreParamKeys.BPARAM_CHECK_BIND_IP_ONSTART, true));
				writeLine(fw, paramToCustom(CoreParamKeys.BPARAM_BIND_IP, "127.0.0.1"));
				fw.write("PluginInfo.azextseed.enabled=bool:false\n");
				fw.write("PluginInfo.mldht.enabled=bool:false\n");
				if (CorePrefs.DEBUG_CORE) {
					logd("buildCustomFile: setting binding to localhost only");
				}
			} else {
				if (CorePrefs.DEBUG_CORE) {
					logd("buildCustomFile: clearing binding");
				}

				writeLine(fw,
						paramToCustom(CoreParamKeys.BPARAM_ENFORCE_BIND_IP, false));
				writeLine(fw,
						paramToCustom(CoreParamKeys.BPARAM_CHECK_BIND_IP_ONSTART, false));
				writeLine(fw, paramToCustom(CoreParamKeys.BPARAM_BIND_IP, ""));
				fw.write("PluginInfo.azextseed.enabled=bool:true\n");
				fw.write("PluginInfo.mldht.enabled=bool:true\n");
			}
			fw.close();

			if (CorePrefs.DEBUG_CORE) {
				logd("buildCustomFile: " + FileUtil.readFileAsString(configFile, -1));
			}
		} catch (IOException e) {
			loge("buildCustomFile: ", e);
		}

	}

	@NonNull
	private String paramToCustom(@NonNull String key, byte[] val) {
		return key.replace(" ", "\\ ") + "=byte[]:"
				+ ByteFormatter.encodeString(val);
	}

	private void writeLine(@NonNull Writer writer, @NonNull String s)
			throws IOException {
		writer.write(s);
		writer.write('\n');
	}

	@NonNull
	private String paramToCustom(@NonNull String key, String s) {
		return key.replace(" ", "\\ ") + "=string:" + s;
	}

	@NonNull
	private String paramToCustom(@NonNull String key, boolean b) {
		return key.replace(" ", "\\ ") + "=bool:" + (b ? "true" : "false");
	}

	@Override
	public void corePrefAutoStartChanged(CorePrefs corePrefs, boolean autoStart) {
		// no triggering needed, on boot event, we check the pref
	}

	@Override
	public void corePrefAllowCellDataChanged(CorePrefs corePrefs,
			boolean allowCellData) {
		if (biglyBTManager != null) {
			sendRestartServiceIntent();
		}
	}

	@Override
	public void corePrefDisableSleepChanged(@NonNull CorePrefs corePrefs,
			boolean disableSleep) {
		adjustPowerLock(corePrefs);
	}

	@Override
	public void corePrefOnlyPluggedInChanged(@NonNull CorePrefs corePrefs,
			boolean onlyPluggedIn) {
		if (onlyPluggedIn) {
			enableBatteryMonitoring(BiglyBTApp.getContext(), corePrefs);
		} else {
			disableBatteryMonitoring(BiglyBTApp.getContext());
		}
	}

	@Override
	public void corePrefProxyChanged(CorePrefs corePrefs,
			CoreProxyPreferences prefProxy) {
		if (biglyBTManager == null) {
			if (CorePrefs.DEBUG_CORE) {
				logd("corePrefProxyChanged: no core, skipping");
			}
			return;
		}

		if (CorePrefs.DEBUG_CORE) {
			logd("corePrefProxyChanged: " + prefProxy);
		}
		COConfigurationManager.setParameter(
				CoreParamKeys.BPARAM_PROXY_ENABLE_TRACKERS, prefProxy.proxyTrackers);
		boolean enableSOCKS = prefProxy.proxyType.startsWith("SOCK");
		COConfigurationManager.setParameter(CoreParamKeys.BPARAM_PROXY_ENABLE_SOCKS,
				enableSOCKS);
		COConfigurationManager.setParameter(CoreParamKeys.SPARAM_PROXY_HOST,
				prefProxy.host);
		COConfigurationManager.setParameter(CoreParamKeys.SPARAM_PROXY_PORT,
				"" + prefProxy.port);
		COConfigurationManager.setParameter(CoreParamKeys.SPARAM_PROXY_USER,
				prefProxy.user);
		COConfigurationManager.setParameter(CoreParamKeys.SPARAM_PROXY_PW,
				prefProxy.pw);
		if (enableSOCKS) {
			COConfigurationManager.setParameter(
					CoreParamKeys.BPARAM_PROXY_DATA_ENABLE, prefProxy.proxyOutgoingPeers);
			String ver = "V" + prefProxy.proxyType.substring(4);
			COConfigurationManager.setParameter(
					CoreParamKeys.SPARAM_PROXY_DATA_SOCKS_VER, ver);
			COConfigurationManager.setParameter(CoreParamKeys.BPARAM_PROXY_DATA_SAME,
					true);
		} else {
			// Technically, we can have outgoings peers on a proxy, but that requires
			// BPARAM_PROXY_DATA_SAME as false, and separate config param keys
			// just for data.
			// For now, that's a lot of confusing UI, so we limit using data proxy 
			// only when socks proxy for trackers is on
			COConfigurationManager.setParameter(
					CoreParamKeys.BPARAM_PROXY_DATA_ENABLE, false);
		}
		if (biglyBTManager != null) {
			sendRestartServiceIntent();
		}
	}

	@Override
	public void corePrefRemAccessChanged(CorePrefs corePrefs,
			CoreRemoteAccessPreferences raPrefs) {
		if (biglyBTManager == null) {
			if (CorePrefs.DEBUG_CORE) {
				logd("corePrefRemAccessChanged: no core, skipping");
			}
			return;
		}

		if (CorePrefs.DEBUG_CORE) {
			logd("corePrefRemAccessChanged: " + raPrefs);
		}

		COConfigurationManager.setParameter(CoreParamKeys.SPARAM_XMWEBUI_BIND_IP,
				raPrefs.allowLANAccess ? "" : "127.0.0.1");
		COConfigurationManager.setParameter(
				CoreParamKeys.SPARAM_XMWEBUI_PW_DISABLED_WHITELIST,
				raPrefs.allowLANAccess ? DEFAULT_WEBUI_PW_LAN_ONLY
						: DEFAULT_WEBUI_PW_DISABLED_WHITELIST);
		COConfigurationManager.setParameter(CoreParamKeys.BPARAM_XMWEBUI_PW_ENABLE,
				raPrefs.reqPW);
		COConfigurationManager.setParameter(CoreParamKeys.SPARAM_XMWEBUI_USER,
				raPrefs.user);
		COConfigurationManager.setParameter(CoreParamKeys.SPARAM_XMWEBUI_PW,
				raPrefs.getSHA1pw());

		//sendRestartServiceIntent();
	}

	@Thunk
	void sendStuff(int what, @Nullable String s) {
		if (s != null) {
			Map map = new HashMap();
			map.put("data", s);
			sendStuff(what, map);
		} else {
			sendStuff(what, (Map) null);
		}
	}

	@Thunk
	void sendStuff(int what, @Nullable Map map) {
		if (map != null) {
			if (CorePrefs.DEBUG_CORE) {
				logd("sendStuff: " + what + "; " + map.get("data") + ";state="
						+ map.get("state") + " to " + mapListeners.size() + " clients, "
						+ AndroidUtils.getCompressedStackTrace());
			}
		}
		synchronized (mapListeners) {
			for (Iterator<IBinder> iterator = mapListeners.keySet().iterator(); iterator.hasNext();) {
				IBinder binder = iterator.next();
				IBiglyCoreCallback callback = mapListeners.get(binder);
				try {
					callback.onCoreEvent(what, map);
				} catch (RemoteException e) {
					e.printStackTrace();
					// The client is dead.  Remove it from the list;
					// we are going through the list from back to front
					// so this is safe to do inside the loop.
					iterator.remove();
				}
			}
		}
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		if (skipBind) {
			if (CorePrefs.DEBUG_CORE) {
				logd("Skipping Bind");
			}

			return null;
		}
		if (CorePrefs.DEBUG_CORE) {
			logd("onBind " + intent + "; binder=" + mBinder);
		}

		return mBinder;
	}

	@Thunk
	@WorkerThread
	synchronized void startCore() {
		CorePrefs corePrefs = CorePrefs.getInstance();
		if (CorePrefs.DEBUG_CORE) {
			logd("startCore");
		}

//		if (BuildConfig.DEBUG) {
//		// still need to attach to process
//		android.os.Debug.waitForDebugger();
//		}

		if (!AndroidUtils.hasPermisssion(this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
			// TODO: implement check
			logd("startCore: No WRITE_EXTERNAL_STORAGE permission");
		}

		// requires WRITE_EXTERNAL_STORAGE
		File storageRoot = Environment.getExternalStorageDirectory();

		if (CorePrefs.DEBUG_CORE) {
			File dirDoc = Environment.getExternalStoragePublicDirectory("Documents");
			File dirDl = Environment.getExternalStoragePublicDirectory("Download");
			File dirVideo = Environment.getExternalStoragePublicDirectory("Movies");
			File dirAudio = Environment.getExternalStoragePublicDirectory("Music");
			logd("Doc=" + dirDoc + "\nDL=" + dirDl + "\nVideo=" + dirVideo
					+ "\nAudio=" + dirAudio + "\nStorage=" + storageRoot + "\nAppDir="
					+ SystemProperties.getApplicationPath());
		}

		File internalRoot = this.getApplicationContext().getFilesDir();
		biglybtCoreConfigRoot = new File(internalRoot, ".biglybt");

		if (CorePrefs.DEBUG_CORE) {
			logd("startCore: config root=" + biglybtCoreConfigRoot + ";manager="
					+ biglyBTManager);
		}

		if (biglyBTManager == null) {

			// TODO: If non-cellular only:
			// - Check ConnectivityManager.getActiveNetworkInfo().getType
			// - if TYPE_MOBILE, bind to "lo" or "127.0.0.1" before core start
			// - if non TYPE_MOBILE, check what's in UP state (ex. eth0, wlan0).
			// Should not be rmnet
			// - if not rmnet, bind before core start
			// ALSO
			// on network change, do the above, restarting core (shut down and start
			// again) if bind needs to change
			// If any network (cellular or otherwise)
			// - Ensure binding is blank, restart core if needed

			NetworkState networkState = BiglyBTApp.getNetworkState();
			networkState.addListener(this); // triggers

			buildCustomFile(corePrefs);
			try {
				if (CoreFactory.isCoreAvailable() && isCoreStopping) {
					if (CorePrefs.DEBUG_CORE) {
						logw("Core already available");
					}

					if (restartService) {
						if (CorePrefs.DEBUG_CORE) {
							logd("BiglyBT is shutting down, will restart afterwards");
						}
					} else {
						if (CorePrefs.DEBUG_CORE) {
							logd("BiglyBT is shutting down, setting to restart");
						}
						biglyBTManager = null;
						sendRestartServiceIntent();
					}
					return;
				}
				isCoreStopping = false;
				biglyBTManager = new BiglyBTManager(biglybtCoreConfigRoot);
			} catch (CoreException ex) {
				AnalyticsTracker.getInstance(this).logError(ex,
						(core == null) ? "noCore" : "hasCore");
				if (ex.getMessage().contains("already instantiated")) {
					sendRestartServiceIntent();
				}
				return;
			}

			core = biglyBTManager.getCore();

			if (!AndroidUtils.DEBUG) {
				System.setOut(new PrintStream(new OutputStream() {
					@Override
					public void write(int b) {
						//DO NOTHING
					}
				}));
			}

			SimpleTimer.addPeriodicEvent("Update Notification", 10000,
					event -> updateNotification());

			if (corePrefs.getPrefOnlyPluggedIn()) {
				boolean wasConnected = AndroidUtils.isPowerConnected(
						BiglyBTApp.getContext());
				boolean isConnected = AndroidUtils.isPowerConnected(
						BiglyBTApp.getContext());

				if (wasConnected != isConnected) {
					if (CorePrefs.DEBUG_CORE) {
						logd("state changed while starting up.. stop core and try again");
					}

					sendRestartServiceIntent();
					return;
				}
			}

			ServiceCoreLifecycleAdapter lifecycleAdapter = new ServiceCoreLifecycleAdapter(
					corePrefs);
			core.addLifecycleListener(lifecycleAdapter);
			if (core.isStarted()) {
				// If core was already started before adding listener, we need to manually trigger started
				lifecycleAdapter.started(core);
			}
			PluginInterface pluginXMWebui = core.getPluginManager().getPluginInterfaceByID(
					"xmwebui", true);
			if (pluginXMWebui != null && !webUIStarted) {
				webUIStarted = true;
				sendStuff(MSG_OUT_WEBUI_STARTED, "MSG_OUT_WEBUI_STARTED");
				updateNotification();
			}

		} else {
			if (CorePrefs.DEBUG_CORE) {
				logd("startCore: biglyBTManager already created");
			}
		}
	}

	@Thunk
	void updateNotification() {
		if (!canDisplayNotifications) {
			return;
		}
		if (!allowNotificationUpdate) {
			return;
		}

		if (screenOff) {
			return;
		}
		try {
			NotificationManager mNotificationManager = (NotificationManager) getSystemService(
					Context.NOTIFICATION_SERVICE);
			if (mNotificationManager != null) {
				Notification notification = getNotificationBuilder().build();
				mNotificationManager.notify(NOTIFICATION_ID, notification);
			}
		} catch (IllegalArgumentException ignore) {
		}
	}

	public static void sendRestartServiceIntent() {
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "sendRestartServiceIntent via "
					+ AndroidUtils.getCompressedStackTrace());
		}
		Context context = BiglyBTApp.getContext();
		Intent intentStop = new Intent(context, BiglyBTService.class);
		intentStop.setAction(BiglyBTService.INTENT_ACTION_RESTART);
		PendingIntent piRestart = PendingIntent.getService(context, 0, intentStop,
				PendingIntent.FLAG_CANCEL_CURRENT);

		try {
			piRestart.send();
		} catch (PendingIntent.CanceledException e) {
			Log.e(TAG, "restartService", e);
		}
	}

	public void reallyRestartService() {
		if (restartService || core == null) {
			if (CorePrefs.DEBUG_CORE) {
				logd("restartService skipped: "
						+ AndroidUtils.getCompressedStackTrace());
			}
			return;
		}
		if (CorePrefs.DEBUG_CORE) {
			logd("restartService: " + AndroidUtils.getCompressedStackTrace());
		}
		restartService = true;
		stopSelfAndNotify();
	}

	@Thunk
	void stopSelfAndNotify() {
		isServiceStopping = true;
		updateNotification();
		beginCoreShutdown();
	}

	private void beginCoreShutdown() {
		if (core == null) {
			if (CorePrefs.DEBUG_CORE) {
				logd("beginCoreShutdown: core is null");
			}
			stopSelf();
			return;
		}
		if (isCoreStopping) {
			if (CorePrefs.DEBUG_CORE) {
				logw("beginCoreShutdown: core already stopping");
			}
			stopSelf();
			return;
		}

		if (CorePrefs.DEBUG_CORE) {
			logd("beginCoreShutdown: startThread via "
					+ AndroidUtils.getCompressedStackTrace());
		}

		Thread thread = new Thread(() -> {
			if (CorePrefs.DEBUG_CORE) {
				logd("beginCoreShutdown: stopping off thread");
			}
			try {
				core.stop();
			} catch (Throwable t) {
				AnalyticsTracker.getInstance().logError(t);
				stopSelf();
			}

			if (CorePrefs.DEBUG_CORE) {
				logd("beginCoreShutdown: Stopped off thread");
			}
		});
		thread.setDaemon(false);
		thread.start();
	}

	@Override
	public void onCreate() {
		super.onCreate();

		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		screenReceiver = new ScreenReceiver();
		registerReceiver(screenReceiver, filter);
		initChannels(this);
	}

	public void initChannels(Context context) {
		if (Build.VERSION.SDK_INT < 26) {
			return;
		}
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(
				Context.NOTIFICATION_SERVICE);
		if (notificationManager == null) {
			return;
		}
		NotificationChannel channel = new NotificationChannel(
				NOTIFICATION_CHANNEL_ID, "BiglyBT Core Notification",
				NotificationManager.IMPORTANCE_LOW);
		channel.setDescription("Displays the state of BiglyBT core");
		notificationManager.createNotificationChannel(channel);
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		boolean hadStaticVar = staticVar != null;

		if (CorePrefs.DEBUG_CORE) {
			logd("onStartCommand: " + intent + "; flags = "
					+ Integer.toBinaryString(flags) + "; startId=" + startId
					+ "; hadStaticVar=" + hadStaticVar + "; " + staticVar);
		}

		// https://issuetracker.google.com/issues/36941858
		// Between 2.3/Gingerbread/9 and 4.0.4/ICS/15 (inclusive), START_FLAG_RETRY was always true
		if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				&& (flags & START_FLAG_RETRY) > 0) {
			if (CorePrefs.DEBUG_CORE) {
				logw(
						"Starting BiglyBTService with START_FLAG_RETRY.  Assuming restarting from 'crash' and shutting down service if "
								+ !hadStaticVar);
			}
			if (!hadStaticVar) {
				skipBind = true;
				isServiceStopping = true;
				stopSelf();
			}
			return START_NOT_STICKY;
		}

		final String intentAction = intent == null ? INTENT_ACTION_START
				: intent.getAction();

		if (hadStaticVar && INTENT_ACTION_START.equals(intentAction)) {
			logd("onStartCommand: Service Stopping, NOT_STICKY");
			return START_NOT_STICKY;
		}

		if (intentAction != null && intentAction.startsWith("com.biglybt")) {
			Thread thread = new Thread(() -> handleStartAction(intentAction),
					"BiglyBTServiceAction");
			thread.setDaemon(true);
			thread.start();
			if (!INTENT_ACTION_START.equals(intentAction)) {
				return START_NOT_STICKY;
			}
		}

		if (!hadStaticVar) {
			staticVar = new Object();
		}

		/*
		 * Things I discovered (which may be wrong):
		 *
		 * Calling startForeground causes a Service in a separate process to be
		 * linked to the rootIntent, and prevents the rootIntent from fully
		 * being destroyed.  ie. Swiping your app away on the Recent Apps List
		 * will remove it from the list, and perhaps destroy some objects, but the
		 * Application object will remain.
		 *
		 * Without startForeground, the service will be killed when the app
		 * is swiped-away, and then started up again (1000ms on my device).  This
		 * is not a graceful destroy, but a thread kill, so you don't get a onDestroy()
		 * call.  The restart call to onStartCommand will have a null intent.
		 *
		 * Since I want to gracefully shut down, the best option for me is to
		 * startForeground, and minimize memory usage of the app when the user
		 * swipes it away. The cleanup is triggered via a dedicated local service
		 * that notifies the app onTaskRemoved.
		 */
		Notification notification = getNotificationBuilder().build();
		startForeground(NOTIFICATION_ID, notification);

		if (CorePrefs.DEBUG_CORE) {
			logd("onStartCommand: startForeground, Start Sticky; flags=" + flags
					+ ";startID=" + startId + ";"
					+ (intent == null ? "null intent" : intent.getAction() + ";"
							+ intent.getExtras() + ";" + intent.getDataString())
					+ "; hadStaticVar=" + hadStaticVar);
		}
		return (START_STICKY);
	}

	@Thunk
	@WorkerThread
	void handleStartAction(String intentAction) {
		if (intentAction == null) {
			return;
		}
		switch (intentAction) {
			case INTENT_ACTION_RESTART:
				if (CorePrefs.DEBUG_CORE) {
					logd("onStartCommand: Restart");
				}
				reallyRestartService();
				break;

			case INTENT_ACTION_START:
				if (CorePrefs.DEBUG_CORE) {
					logd("onStartCommand: Start");
				}
				startCore();
				break;

			case INTENT_ACTION_STOP:
				if (CorePrefs.DEBUG_CORE) {
					logd("onStartCommand: Stop");
				}
				stopSelfAndNotify();
				break;

			case INTENT_ACTION_RESUME:
				if (CorePrefs.DEBUG_CORE) {
					logd("onStartCommand: Resume");
				}
				if (core != null && core.isStarted()) {
					GlobalManager gm = core.getGlobalManager();
					if (gm != null) {
						gm.resumeDownloads();
						updateNotification();
					}
				}
				break;

			case INTENT_ACTION_PAUSE:
				if (CorePrefs.DEBUG_CORE) {
					logd("onStartCommand: Pause");
				}
				if (core != null && core.isStarted()) {
					GlobalManager gm = core.getGlobalManager();
					if (gm != null) {
						gm.pauseDownloads();
						updateNotification();
					}
				}
				break;
		}

	}

	private NotificationCompat.Builder getNotificationBuilder() {
		Resources resources = getResources();
		final Intent notificationIntent = new Intent(this, IntentHandler.class);
		final PendingIntent pi = PendingIntent.getActivity(this, 0,
				notificationIntent, 0);

		String title = resources.getString(R.string.core_noti_title);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
				NOTIFICATION_CHANNEL_ID);
		builder.setSmallIcon(R.drawable.ic_core_statusbar);
		builder.setContentTitle(title);
		builder.setOngoing(true);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			builder.setCategory(Notification.CATEGORY_SERVICE);
		}
		builder.setContentIntent(pi);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			builder.setPriority(Notification.PRIORITY_LOW);
		}
		builder.setShowWhen(false);

		if (!isCoreStopping && !isServiceStopping) {
			Intent intentStop = new Intent(this, BiglyBTService.class);
			intentStop.setAction(INTENT_ACTION_STOP);
			PendingIntent piStop = PendingIntent.getService(this, 0, intentStop,
					PendingIntent.FLAG_CANCEL_CURRENT);

			builder.addAction(R.drawable.ic_power_settings_new_white_24dp,
					resources.getString(R.string.core_noti_stop_button), piStop);

			if (core != null && core.isStarted()) {
				GlobalManager gm = core.getGlobalManager();
				if (gm != null) {
					boolean canPause = gm.canPauseDownloads();
					Intent intentPR = new Intent(this, BiglyBTService.class);
					intentPR.setAction(
							canPause ? INTENT_ACTION_PAUSE : INTENT_ACTION_RESUME);
					PendingIntent piPR = PendingIntent.getService(this, 0, intentPR,
							PendingIntent.FLAG_CANCEL_CURRENT);

					builder.addAction(
							canPause ? R.drawable.ic_playlist_pause_n
									: R.drawable.ic_playlist_play_white_n,
							resources.getString(canPause ? R.string.core_noti_pause_button
									: R.string.core_noti_resume_button),
							piPR);
				}
			}

			if (CorePrefs.DEBUG_CORE) {
				Intent intentRestart = new Intent(this, BiglyBTService.class);
				intentRestart.setAction(INTENT_ACTION_RESTART);
				PendingIntent piRestart = PendingIntent.getService(this, 0,
						intentRestart, PendingIntent.FLAG_CANCEL_CURRENT);
				builder.addAction(R.drawable.ic_notification_restart, "Restart",
						piRestart);
			}

		}

		String subTitle;
		if (isCoreStopping || isServiceStopping) {
			int id = restartService ? R.string.core_noti_restarting
					: R.string.core_noti_stopping;
			subTitle = resources.getString(id);
		} else {
			if (bindToLocalHost) {
				subTitle = resources.getString(bindToLocalHostReasonID);
			} else {
				GlobalManagerStats stats = null;
				if (core != null && core.isStarted()) {
					GlobalManager gm = core.getGlobalManager();
					if (gm != null) {
						stats = gm.getStats();
					}
				}

				if (stats != null) {
					String downSpeed = DisplayFormatters.formatByteCountToKiBEtcPerSec(
							stats.getDataAndProtocolReceiveRate());
					String upSpeed = DisplayFormatters.formatByteCountToKiBEtcPerSec(
							stats.getDataAndProtocolSendRate());
					TagManager tagManager = TagManagerFactory.getTagManager();
					Tag tagActive = tagManager.getTagType(
							TagType.TT_DOWNLOAD_STATE).getTag(7);// active
					int numActive = tagActive == null ? 0 : tagActive.getTaggedCount();
					subTitle = resources.getQuantityString(R.plurals.core_noti_running,
							numActive, downSpeed, upSpeed,
							DisplayFormatters.formatNumber(numActive));
				} else {
					subTitle = resources.getString(R.string.core_noti_starting);
				}

			}
		}
		builder.setContentText(subTitle);

		return builder;
	}

	@Override
	public boolean stopService(Intent name) {
		if (CorePrefs.DEBUG_CORE) {
			logd("stopService: " + AndroidUtils.getCompressedStackTrace());
		}
		return super.stopService(name);
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		if (CorePrefs.DEBUG_CORE) {
			logd("onTaskRemoved: " + rootIntent);
		}
		// Case: User uses task manager to close app (swipe right)
		// App doesn't get notified, but this service does if the app
		// started this service
		synchronized (mapListeners) {
			for (Iterator<IBinder> iterator = mapListeners.keySet().iterator(); iterator.hasNext();) {
				IBinder binder = iterator.next();
				IBiglyCoreCallback callback = mapListeners.get(binder);
				if (!binder.isBinderAlive()) {
					if (CorePrefs.DEBUG_CORE) {
						logd("onTaskRemoved: removing dead binding #" + binder);
					}
					iterator.remove();
				} else if (!binder.pingBinder()) {
					if (CorePrefs.DEBUG_CORE) {
						logd("onTaskRemoved: not removing dead-ping binding #" + binder);
					}
				} else {
					if (CorePrefs.DEBUG_CORE) {
						logd("onTaskRemoved: not removing, ok binder=" + binder);
					}
				}
			}
		}
	}

	@Override
	public void onDestroy() {
		isServiceDestroyed = true;

		if (CorePrefs.DEBUG_CORE) {
			logd("onDestroy: " + AndroidUtils.getCompressedStackTrace());
		}

		super.onDestroy();
		NetworkState networkState = BiglyBTApp.getNetworkState();
		networkState.removeListener(this);

		if (screenReceiver != null) {
			unregisterReceiver(screenReceiver);
		}

		if (core != null && !isCoreStopping) {
			// Hopefully in most cases, core is already stopping, so the
			// likelyhood of core stopping before onDestroy is done is probably low
			beginCoreShutdown();
		}

		Map bundle = new HashMap();
		bundle.put("data", "MSG_OUT_SERVICE_DESTROY");
		bundle.put("restarting", restartService);
		sendStuff(MSG_OUT_SERVICE_DESTROY, bundle);

		allowNotificationUpdate = false;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(
				Context.NOTIFICATION_SERVICE);
		if (mNotificationManager != null) {
			mNotificationManager.cancel(1);
		}

		//staticVar = null;

		if (restartService) {
			if (CorePrefs.DEBUG_CORE) {
				logd("onDestroy: Restarting");
			}

			Intent intent = new Intent(this, BiglyBTService.class);
			if (coreStarted) {
				intent.setAction(INTENT_ACTION_START);
			}
			PendingIntent pendingIntent = PendingIntent.getService(this, 1, intent,
					PendingIntent.FLAG_ONE_SHOT);
			AlarmManager alarmManager = (AlarmManager) getSystemService(
					Context.ALARM_SERVICE);
			if (alarmManager != null) {
				alarmManager.set(AlarmManager.RTC_WAKEUP,
						SystemClock.elapsedRealtime() + 500, pendingIntent);
			}

			if (CorePrefs.DEBUG_CORE) {
				logd("onDestroy: kill old service thread. hadBiglyBTManager="
						+ (biglyBTManager != null));
			}
		}

		CorePrefs.getInstance().removeChangedListener(this);

		// Android will mark this process as an "Empty Process".  According to
		// https://stackoverflow.com/a/33099331 :
		// A process that doesn't hold any active application components. 
		// The only reason to keep this kind of process alive is for caching 
		// purposes, to improve startup time the next time a component needs to 
		// run in it. 
		// The system often kills these processes in order to balance overall 
		// system resources between process caches and the underlying kernel caches.

		// Since BiglyBT core doesn't clean up its threads on shutdown, we
		// need to force kill
		if (core == null) {
			if (CorePrefs.DEBUG_CORE) {
				logd("onDestroy: system.exit");
			}
			System.exit(0);
		} else {
			// System.exit will be called after core is done
			if (CorePrefs.DEBUG_CORE) {
				logd("onDestroy: done");
			}
		}
	}

	private boolean isDataFlowing() {
		if (core == null) {
			return false;
		}
		GlobalManagerStats stats = core.getGlobalManager().getStats();
		int dataSendRate = stats.getDataSendRate();
		int dataReceiveRate = stats.getDataReceiveRate();
		if (dataSendRate > 0 || dataReceiveRate > 0) {
			// data flowing, no need to check smooth rate
			return true;
		}
		long smoothedReceiveRate = stats.getSmoothedReceiveRate();
		long smoothedSendRate = stats.getSmoothedSendRate();
		return smoothedReceiveRate > 1024 && smoothedSendRate > 1024;
	}

	@Override
	public void onlineStateChanged(boolean isOnline, boolean isOnlineMobile) {
		CorePrefs corePrefs = CorePrefs.getInstance();
		// delay restarting core due to loss of internet, in case the internet
		// comes back.  For example, our chromebook, when shut with wifi access,
		// will send a disconnect upon being opened, followed almost immediately
		// by a connect.
		if (!isOnline && lastOnline != null && lastOnline != isOnline) {
			Handler handler = new Handler(Looper.getMainLooper());
			handler.postDelayed(() -> {
				NetworkState networkState = BiglyBTApp.getNetworkState();
				onlineStateChangedNoDelay(corePrefs, networkState.isOnline(),
						networkState.isOnlineMobile());
			}, 10000);
			return;
		}
		onlineStateChangedNoDelay(corePrefs, isOnline, isOnlineMobile);
	}

	public void onlineStateChangedNoDelay(@NonNull CorePrefs corePrefs,
			boolean isOnline, boolean isOnlineMobile) {
		boolean requireRestart = false;

		if (lastOnline == null) {
			lastOnline = isOnline;
		} else if (lastOnline != isOnline) {
			lastOnline = isOnline;
			requireRestart = true;
			if (CorePrefs.DEBUG_CORE) {
				logd("onlineStateChanged: isOnline changed");
			}
		}

		if (!corePrefs.getPrefAllowCellData()) {
			if (lastOnlineMobile == null) {
				lastOnlineMobile = isOnlineMobile;
			} else if (lastOnlineMobile != isOnlineMobile) {
				lastOnlineMobile = isOnlineMobile;
				requireRestart = true;
				if (CorePrefs.DEBUG_CORE) {
					logd("onlineStateChanged: isOnlineMobile changed");
				}
			}
		}

		if (requireRestart && wouldBindToLocalHost(corePrefs) != bindToLocalHost) {
			sendRestartServiceIntent();
		}
	}

	public void checkForSleepModeChange(@NonNull CorePrefs corePrefs) {
		if (wouldBindToLocalHost(corePrefs) != bindToLocalHost) {
			sendRestartServiceIntent();
		}
	}

	private void acquirePowerLock() {
		if (wifiLock == null || !wifiLock.isHeld()) {
			if (!AndroidUtils.hasPermisssion(BiglyBTApp.getContext(),
					Manifest.permission.WAKE_LOCK)) {
				if (CorePrefs.DEBUG_CORE) {
					logd("No Permissions to access wake lock");
				}
				return;
			}
			WifiManager wifiManager = (WifiManager) BiglyBTApp.getContext().getApplicationContext().getSystemService(
					Context.WIFI_SERVICE);
			if (wifiManager != null) {
				wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL,
						WIFI_LOCK_TAG);
				wifiLock.acquire();
				if (CorePrefs.DEBUG_CORE) {
					logd("Wifi lock acquired");
				}
			}

		}
	}

	public void releasePowerLock() {
		if (wifiLock != null && wifiLock.isHeld()) {
			try {
				wifiLock.release();
			} catch (RuntimeException ignore) {
				// WifiLock under-locked
			}
			if (CorePrefs.DEBUG_CORE) {
				logd("Wifi lock released");
			}

		}
	}

	public void adjustPowerLock(@NonNull CorePrefs corePrefs) {
		if (corePrefs.getPrefDisableSleep()) {
			acquirePowerLock();
		} else {
			releasePowerLock();
		}
	}

	private void disableBatteryMonitoring(@NonNull Context context) {
		if (batteryReceiver != null) {
			context.unregisterReceiver(batteryReceiver);
			batteryReceiver = null;
			if (CorePrefs.DEBUG_CORE) {
				logd("disableBatteryMonitoring: ");
			}

		}
	}

	private void enableBatteryMonitoring(@NonNull Context context,
			@NonNull CorePrefs corePrefs) {
		if (batteryReceiver != null) {
			return;
		}
		IntentFilter intentFilterConnected = new IntentFilter(
				Intent.ACTION_POWER_CONNECTED);
		IntentFilter intentFilterDisconnected = new IntentFilter(
				Intent.ACTION_POWER_DISCONNECTED);

		batteryReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (CorePrefs.DEBUG_CORE && intent.getAction() != null) {
					boolean isConnected = intent.getAction().equals(
							Intent.ACTION_POWER_CONNECTED);
					logd("Battery connected? " + isConnected);
				}
				Core core = BiglyBTService.this.core;
				if (core == null) {
					if (CorePrefs.DEBUG_CORE) {
						logd("Battery changed, but core not initialized yet");
					}

					return;
				}
				if (corePrefs.getPrefOnlyPluggedIn()) {
					checkForSleepModeChange(corePrefs);
				}
			}
		};
		context.registerReceiver(batteryReceiver, intentFilterConnected);
		context.registerReceiver(batteryReceiver, intentFilterDisconnected);
		if (CorePrefs.DEBUG_CORE) {
			logd("enableBatteryMonitoring: ");
		}

	}

}
