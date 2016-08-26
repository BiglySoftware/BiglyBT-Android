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

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;

import android.Manifest;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.*;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.aelitis.azureus.core.*;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdmin;
import com.aelitis.azureus.core.pairing.PairingManager;
import com.aelitis.azureus.core.pairing.PairingManagerFactory;
import com.aelitis.azureus.core.pairing.impl.PairingManagerImpl;
import com.aelitis.azureus.core.tag.*;
import com.vuze.android.core.az.VuzeManager;
import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.IntentHandler;
import com.vuze.util.DisplayFormatters;

import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.global.GlobalManagerListener;
import org.gudy.azureus2.core3.global.GlobalManagerStats;
import org.gudy.azureus2.core3.security.SESecurityManager;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.ui.config.BooleanParameter;

/**
 * Launch and shut down Vuze core.
 * <p/>
 * Goals:
 * - Ability to shut down core when 0 active torrents and no UI attached
 * <p/>
 * Created by TuxPaper on 3/24/16.
 */
public class VuzeService
	extends Service
	implements PairingManagerImpl.UIAdapter, NetworkState.NetworkStateListener
{
	public static final int MSG_IN_ADD_LISTENER = 0;

	public static final int MSG_IN_REMOVE_LISTENER = 1;

	public static final int MSG_OUT_CORE_STARTED = 100;

	public static final int MSG_OUT_CORE_STOPPED = 200;

	public static final int MSG_OUT_WEBUI_STARTED = 300;

	public static final int MSG_OUT_CORE_STOPPING = 150;

	static final String TAG = "VuzeService";

	public static final String INTENT_ACTION_STOP = "com.vuze.android.remote"
			+ ".STOP_SERVICE";

	private static final String INTENT_ACTION_PAUSE = "com.vuze.android.remote"
			+ ".PAUSE_TORRENTS";

	private static final String INTENT_ACTION_RESUME = "com.vuze.android.remote"
			+ ".RESUME_TORRENTS";

	class IncomingHandler
		extends Handler
	{
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MSG_IN_ADD_LISTENER: {
					mClients.add(msg.replyTo);
					if (coreStarted) {
						sendStuff(MSG_OUT_CORE_STARTED, null);
					}
					if (webUIStarted) {
						sendStuff(MSG_OUT_WEBUI_STARTED, null);
					}
					break;
				}
				case MSG_IN_REMOVE_LISTENER: {
					mClients.remove(msg.replyTo);
					break;
				}
			}
		}
	}

	static private AzureusCore staticCore;

	private static File vuzeCoreConfigRoot;

	private static VuzeService instance;

	final Messenger mMessenger = new Messenger(new IncomingHandler());

	ArrayList<Messenger> mClients = new ArrayList<>();

	private VuzeManager vuzeManager;

	/* @Thunk */ boolean isCoreStopping;

	private boolean restartService;

	private long lastRemoteRequest = 0;

	/* @Thunk */ boolean seeding_only_mode;

	private Boolean lastOnlineMobile = null;

	private Boolean lastOnline = null;

	private boolean bindToLocalHost = false;

	private int bindToLocalHostReasonID = R.string.core_noti_sleeping;

	private boolean allowNotificationUpdate = true;

	private boolean isServiceStopping;

	/* @Thunk */ boolean coreStarted = false;

	/* @Thunk */ boolean webUIStarted = false;

	public VuzeService() {
		super();
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "VuzeService: Init Class ");
		}
	}

	public static AzureusCore getCore() {
		return staticCore;
	}

	public static VuzeService getInstance() {
		return instance;
	}

	private boolean wouldBindToLocalHost() {
		NetworkState networkState = VuzeRemoteApp.getNetworkState();
		if (CorePrefs.getPrefOnlyPluggedIn()
				&& !AndroidUtils.isPowerConnected(VuzeRemoteApp.getContext())) {
			return true;
		} else if (!CorePrefs.getPrefAllowCellData()
				&& networkState.isOnlineMobile()) {
			return true;
		} else if (!networkState.isOnline()) {
			return true;
		}
		return false;
	}

	private void buildCustomFile() {
		File vuzeCustomDir = new File(vuzeCoreConfigRoot, "custom");
		vuzeCustomDir.mkdirs();
		try {
			File configFile = new File(vuzeCustomDir, "VuzeRemote_Start.config");
			FileWriter fileWriter = new FileWriter(configFile, false);

			NetworkState networkState = VuzeRemoteApp.getNetworkState();
			bindToLocalHost = false;
			if (CorePrefs.getPrefOnlyPluggedIn()
					&& !AndroidUtils.isPowerConnected(VuzeRemoteApp.getContext())) {
				bindToLocalHost = true;
				bindToLocalHostReasonID = R.string.core_noti_sleeping_battery;
			} else if (!CorePrefs.getPrefAllowCellData()
					&& networkState.isOnlineMobile()) {
				bindToLocalHost = true;
				bindToLocalHostReasonID = R.string.core_noti_sleeping_oncellular;
			} else if (!networkState.isOnline()) {
				bindToLocalHost = true;
				bindToLocalHostReasonID = R.string.core_noti_sleeping;
			}


			fileWriter.write("Plugin.xmwebui.Port=long:9092\n");

			if (bindToLocalHost) {
				fileWriter.write("Enforce\\ Bind\\ IP=bool:true\n");
				fileWriter.write("Check\\ Bind\\ IP\\ On\\ Start=bool:true\n");
				fileWriter.write("Bind\\ IP=string:127.0.0.1\n");
				fileWriter.write("Plugin.mldht.enable=bool:false\n");
				// this one doesn't work (case)
				fileWriter.write("Plugin.mlDHT.enable=bool:false\n");
				// but this one does!?
				fileWriter.write("Plugin.DHT.dht.enabled=bool:false\n");
				if (CorePrefs.DEBUG_CORE) {
					Log.d(TAG, "buildCustomFile: setting binding to localhost only");
				}
			} else {
				fileWriter.write("Enforce\\ Bind\\ IP=bool:false\n");
				fileWriter.write("Check\\ Bind\\ IP\\ On\\ Start=bool:false\n");
				fileWriter.write("Bind\\ IP=string:\n");
				fileWriter.write("Plugin.mldht.enable=bool:true\n");
				fileWriter.write("Plugin.mlDHT.enable=bool:true\n");
				fileWriter.write("Plugin.DHT.dht.enabled=bool:true\n");
				if (CorePrefs.DEBUG_CORE) {
					Log.d(TAG, "buildCustomFile: clearing binding");
				}
			}
			fileWriter.close();
		} catch (IOException e) {
			Log.e(TAG, "buildCustomFile: ", e);
		}

	}

	public void sendStuff(int what, String s) {
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				Message obtain = Message.obtain(null, what, 0, 0);
				if (s != null) {
					Bundle bundle = new Bundle();
					bundle.putString("data", s);
					obtain.setData(bundle);
				}
				mClients.get(i).send(obtain);
			} catch (RemoteException e) {
				// The client is dead.  Remove it from the list;
				// we are going through the list from back to front
				// so this is safe to do inside the loop.
				mClients.remove(i);
			}
		}
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "VuzeService: onCreate");
		}

		instance = this;
		//if (BuildConfig.DEBUG) {
			//android.os.Debug.waitForDebugger();
		//}

		if (!AndroidUtils.hasPermisssion(this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
			// TODO: implement check
			Log.d(TAG, "onCreate: No WRITE_EXTERNAL_STORAGE permission");
		}

		// requires WRITE_EXTERNAL_STORAGE
		File storageRoot = Environment.getExternalStorageDirectory();

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO) {
			File dirDoc = Environment.getExternalStoragePublicDirectory("Documents");
			File dirDl = Environment.getExternalStoragePublicDirectory("Download");
			File dirVideo = Environment.getExternalStoragePublicDirectory("Movies");
			File dirAudio = Environment.getExternalStoragePublicDirectory("Music");
			if (CorePrefs.DEBUG_CORE) {
				Log.d("VuzeService", "Doc=" + dirDoc + "\nDL=" + dirDl + "\nVideo="
						+ dirVideo + "\nAudio=" + dirAudio + "\nStorage=" + storageRoot);
			}

		}

		File vuzeDownloadDir = new File(new File(storageRoot, "Vuze"), "Downloads");
		File internalRoot = this.getApplicationContext().getFilesDir();
		vuzeCoreConfigRoot = new File(internalRoot, ".vuze");

		if (!vuzeCoreConfigRoot.exists() || !vuzeDownloadDir.exists()) {
			File vuzeCustomDir = new File(vuzeCoreConfigRoot, "custom");
			vuzeCustomDir.mkdirs();
			try {
				File configFile = new File(vuzeCustomDir, "VuzeRemote.config");
				FileWriter fileWriter = new FileWriter(configFile, false);
				fileWriter.write("Default\\ save\\ path=string:"
						+ vuzeDownloadDir.getAbsolutePath().replace("\\", "\\\\"));
				fileWriter.close();
			} catch (IOException e) {
			}
			vuzeDownloadDir.mkdirs();
		}

		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "onCreate: config root=" + vuzeCoreConfigRoot + ";manager="
					+ vuzeManager);
		}

		if (vuzeManager == null) {

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

			NetworkState networkState = VuzeRemoteApp.getNetworkState();
			networkState.addListener(this); // triggers

			buildCustomFile();
			vuzeManager = new VuzeManager(vuzeCoreConfigRoot);
			staticCore = vuzeManager.getCore();

			if (!AndroidUtils.DEBUG) {
				System.setOut(new PrintStream(new OutputStream() {
					public void write(int b) {
						//DO NOTHING
					}
				}));
			}

			SimpleTimer.addPeriodicEvent("Update Notification", 30000,
					new TimerEventPerformer() {
						@Override
						public void perform(TimerEvent event) {
							updateNotification();
						}
					});

			PairingManager pairingManager = PairingManagerFactory.getSingleton();
			if (pairingManager != null) {
				try {
					Field ui = pairingManager.getClass().getDeclaredField("ui");
					ui.setAccessible(true);
					ui.set(pairingManager, this);
				} catch (Throwable t) {
					if (CorePrefs.DEBUG_CORE) {
						Log.e(TAG, "onCreate: ", t);
					}
				}
			}
			if (CorePrefs.getPrefOnlyPluggedIn()) {
				boolean wasConnected = AndroidUtils.isPowerConnected(
						VuzeRemoteApp.getContext());
				boolean isConnected = AndroidUtils.isPowerConnected(
						VuzeRemoteApp.getContext());

				if (wasConnected != isConnected) {
					if (CorePrefs.DEBUG_CORE) {
						Log.d(TAG,
								"state changed while starting up.. stop core and try again");
					}

					restartService();
					return;
				}
			}
			staticCore.addLifecycleListener(new AzureusCoreLifecycleAdapter() {

				@Override
				public void started(AzureusCore core) {
					// not called if listener is added after core is started!
					if (CorePrefs.DEBUG_CORE) {
						Log.d(TAG, "started: core");
					}

					coreStarted = true;
					sendStuff(MSG_OUT_CORE_STARTED, null);

					updateNotification();

					core.getGlobalManager().addListener(new GlobalManagerListener() {

						@Override
						public void downloadManagerAdded(DownloadManager dm) {

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
							VuzeService.this.seeding_only_mode = seeding_only_mode;
							if (CorePrefs.DEBUG_CORE) {
								Log.d(TAG, "seedingStatusChanged: " + seeding_only_mode);
							}

							if (seeding_only_mode) {
								CorePrefs.releasePowerLock();
							} else {
								CorePrefs.adjustPowerLock();
							}
						}
					});
				}

				@Override
				public void componentCreated(AzureusCore core,
						AzureusCoreComponent component) {
					// GlobalManager is always called, even if already created
					if (component instanceof GlobalManager) {

						String s = NetworkAdmin.getSingleton().getNetworkInterfacesAsString();
						if (CorePrefs.DEBUG_CORE) {
							Log.d(TAG, "started: " + s);
						}

					}
					if (component instanceof PluginInterface) {
						String pluginID = ((PluginInterface) component).getPluginID();
						if (CorePrefs.DEBUG_CORE) {
							Log.d(TAG, "plugin " + pluginID + " started");
						}

						if (pluginID.equals("xmwebui")) {
							webUIStarted = true;
							sendStuff(MSG_OUT_WEBUI_STARTED, null);
							updateNotification();
						}
					} else {
						Log.d(TAG, "component " + component.getClass().getSimpleName()
								+ " started");
					}
				}

				@Override
				public void stopped(AzureusCore core) {
					if (CorePrefs.DEBUG_CORE) {
						Log.d(TAG, "stopped: core");
					}

					core.removeLifecycleListener(this);

					sendStuff(MSG_OUT_CORE_STOPPED, null);

					// This will never get called if there's a non-deamon thread
					// (measurement-1 from GA, also Binder_2, Binder_1).  Instead,
					// System.exit(0) is called, which kills the service.
					// The service is restarted (STICKY) by android & everything starts
					// up nicely.

					// in case we do get here, keep exit path consistent
					if (CorePrefs.DEBUG_CORE) {
						Log.d(TAG, "stopped: core!");
					}

					NetworkState networkState = VuzeRemoteApp.getNetworkState();
					networkState.removeListener(VuzeService.this);

					// Delay exitVM otherwise Vuze core will error with:
					//Listener dispatch timeout: failed = com.vuze.android.remote
					// .service.VuzeService
					Thread thread = new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							if (CorePrefs.DEBUG_CORE) {
								Log.d(TAG, "stopped: delayed exit vm");
							}

							SESecurityManager.exitVM(0);
						}
					});
					thread.setDaemon(false);
					thread.start();
					//System.exit(0);
					if (CorePrefs.DEBUG_CORE) {
						Log.d(TAG, "stopped: core!!");
					}

				}

				@Override
				public void stopping(AzureusCore core) {
					if (CorePrefs.DEBUG_CORE) {
						Log.d(TAG, "stopping: core");
					}

					isCoreStopping = true;

					VuzeEasyTracker.getInstance().stop();
					sendStuff(MSG_OUT_CORE_STOPPING, null);
					CorePrefs.releasePowerLock();

					updateNotification();
				}
			});
		} else {
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "onCreate: vuzeManager already created");
			}
		}
	}

	/* @Thunk */ void updateNotification() {
		if (!allowNotificationUpdate) {
			return;
		}
		//if (CorePrefs.DEBUG_CORE) {
			//Log.d(TAG, "updateNotification");
		//}
		try {
			NotificationManager mNotificationManager = (NotificationManager) getSystemService(
					Context.NOTIFICATION_SERVICE);
			Notification notification = getNotificationBuilder().build();
			mNotificationManager.notify(1, notification);
		} catch (IllegalArgumentException ignore) {
		}
	}

	public void restartService() {
		if (restartService || staticCore == null) {
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "restartService skipped: " + AndroidUtils.getCompressedStackTrace());
			}
			return;
		}
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "restartService: " + AndroidUtils.getCompressedStackTrace());
		}
		restartService = true;
		if (vuzeManager != null) {
			AzureusCore core = vuzeManager.getCore();
			if (core != null) {
				core.stop();
			}
			vuzeManager = null;
		}
		stopSelfAndNotify();
	}

	private void stopSelfAndNotify() {
		isServiceStopping = true;
		updateNotification();
		stopSelf();
	}

	public int onStartCommand(Intent intent, int flags, int startId) {
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "onStartCommand: ");
		}

		super.onStartCommand(intent, flags, startId);

		if (intent != null && INTENT_ACTION_STOP.equals(intent.getAction())) {
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "onStartCommand: Stop");
			}
			stopSelfAndNotify();
			stopForeground(true);
			return START_NOT_STICKY;
		}

		if (intent != null && INTENT_ACTION_RESUME.equals(intent.getAction())) {
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "onStartCommand: Resume");
			}
			AzureusCore core = getCore();
			if (core != null && core.isStarted()) {
				GlobalManager gm = core.getGlobalManager();
				if (gm != null) {
					gm.resumeDownloads();
					updateNotification();
				}
			}
			return START_NOT_STICKY;
		}

		if (intent != null && INTENT_ACTION_PAUSE.equals(intent.getAction())) {
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "onStartCommand: Pause");
			}
			AzureusCore core = getCore();
			if (core != null && core.isStarted()) {
				GlobalManager gm = core.getGlobalManager();
				if (gm != null) {
					gm.pauseDownloads();
					updateNotification();
				}
			}
			return START_NOT_STICKY;
		}

		Notification notification = getNotificationBuilder().build();
		startForeground(1, notification);

		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "onStartCommand: Start Sticky");
		}
		return (START_STICKY);
	}

	private NotificationCompat.Builder getNotificationBuilder() {
		Resources resources = getResources();
		final Intent notificationIntent = new Intent(this, IntentHandler.class);
		final PendingIntent pi = PendingIntent.getActivity(this, 0,
				notificationIntent, 0);

		String title = resources.getString(R.string.core_noti_title);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(
				this).setSmallIcon(R.drawable.notification_small).setContentTitle(
						title).setOngoing(true).setCategory(
								Notification.CATEGORY_SERVICE).setContentIntent(pi);

		if (!isCoreStopping) {
			Intent intentStop = new Intent(this, VuzeService.class);
			intentStop.setAction(INTENT_ACTION_STOP);
			PendingIntent piStop = PendingIntent.getService(this, 0, intentStop,
					PendingIntent.FLAG_CANCEL_CURRENT);

			builder.addAction(R.drawable.ic_power_settings_new_white_24dp,
					resources.getString(R.string.core_noti_stop_button), piStop);

			AzureusCore core = getCore();
			if (core != null && core.isStarted()) {
				GlobalManager gm = core.getGlobalManager();
				if (gm != null) {
					boolean canPause = gm.canPauseDownloads();
					Intent intentPR = new Intent(this, VuzeService.class);
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
				AzureusCore core = getCore();
				if (core != null && core.isStarted()) {
					GlobalManager gm = staticCore.getGlobalManager();
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
					Tag tagActive = tagManager.lookupTagByUID(7);// active
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
	public void onTaskRemoved(Intent rootIntent) {
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "onTaskRemoved: ");
		}

		//AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(getApplicationContext(), getClass());
		intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(
				getApplicationContext(), 1, intent, 0);
	}

	@Override
	public void onDestroy() {
		allowNotificationUpdate = false;

		instance = null;
		if (CorePrefs.DEBUG_CORE) {
			Log.d(TAG, "onDestroy: " + AndroidUtils.getCompressedStackTrace());
		}

		super.onDestroy();
		NetworkState networkState = VuzeRemoteApp.getNetworkState();
		networkState.removeListener(this);

		if (vuzeManager != null) {
			AzureusCore core = vuzeManager.getCore();
			vuzeManager = null;
			// Hopefully in most cases, core is already stopping, so the
			// likelyhood of core stopping before onDestroy is done is probably low
			if (core != null && !isCoreStopping) {
				core.stop();
			}
		}
		if (restartService) {
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "onDestroy: Restarting");
			}

			startService(new Intent(this, VuzeService.class));
		}
	}

	@Override
	public void initialise(PluginInterface pi, BooleanParameter icon_enable) {
	}

	@Override
	public void recordRequest(String name, String ip, boolean good) {
		lastRemoteRequest = System.nanoTime();
	}

	private boolean isDataFlowing() {
		AzureusCore core = getCore();
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
	public char[] getSRPPassword() {
		return null;
	}

	@Override
	public void onlineStateChanged(boolean isOnline, boolean isOnlineMobile) {
		boolean requireRestart = false;

		if (lastOnline == null) {
			lastOnline = isOnline;
		} else if (lastOnline != isOnline) {
			lastOnline = isOnline;
			requireRestart = true;
			if (CorePrefs.DEBUG_CORE) {
				Log.d(TAG, "onlineStateChanged: isOnline changed");
			}
		}

		if (!CorePrefs.getPrefAllowCellData()) {
			if (lastOnlineMobile == null) {
				lastOnlineMobile = isOnlineMobile;
			} else if (lastOnlineMobile != isOnlineMobile) {
				lastOnlineMobile = isOnlineMobile;
				requireRestart = true;
				if (CorePrefs.DEBUG_CORE) {
					Log.d(TAG, "onlineStateChanged: isOnlineMobile changed");
				}
			}
		}

		if (requireRestart && wouldBindToLocalHost() != bindToLocalHost) {
			restartService();
		}
	}

	public void checkForSleepModeChange() {
		if (wouldBindToLocalHost() != bindToLocalHost) {
			restartService();
		}
	}
}
