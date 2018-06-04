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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;

import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.util.NetworkState;
import com.biglybt.util.Thunk;
import com.jaredrummler.android.device.DeviceName;
import com.squareup.picasso.*;

import android.app.Application;
import android.app.UiModeManager;
import android.content.*;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.*;
import android.os.Process;
import android.support.multidex.MultiDexApplication;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewConfiguration;

import divstar.ico4a.codec.ico.ICODecoder;
import divstar.ico4a.codec.ico.ICOImage;

/**
 * TODO: Start/Stop all: If list filtered, ask to stop/start list or all
 * TODO: For Local Core, use native directory browser for save/move
 */
public class BiglyBTApp
	extends MultiDexApplication
{
	@Thunk
	static final String TAG = "App";

	private static final boolean CLEAR_PERMISSIONS = AndroidUtils.DEBUG;

	public static final String URL_BUGS = "https://bugs.biglybt.com/android";

	private static final Object lock = new Object();

	private static AppPreferences appPreferences = null;

	private static NetworkState networkState = null;

	@Thunk
	static Application applicationContext = null;

	@Thunk
	static boolean isCoreProcess = false;

	private static Picasso picassoInstance = null;

	public static String deviceName = null;

	private static AppLifecycleCallbacks appLifecycleCallbacks;

	public static void onClearFromRecentService() {
		if (isCoreProcess) {
			return;
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Application.onClearFromRecentService");
		}

		SessionManager.removeAllSessions();
		if (networkState != null) {
			networkState.dipose();
			networkState = null;
		}
		appPreferences = null;
		/*
		if (picassoInstance != null) {
			picassoInstance.shutdown();
			picassoInstance = null;
		}
		*/
	}

	@Override
	public void onCreate() {
		super.onCreate();

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Application.onCreate " + BuildConfig.FLAVOR + " "
					+ getApplicationContext() + ";" + getBaseContext());

			StrictMode.setThreadPolicy(
					new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().detectCustomSlowCalls().penaltyLog().build());
		}

		applicationContext = (Application) getApplicationContext();

		appLifecycleCallbacks = new AppLifecycleCallbacks();
		applicationContext.registerActivityLifecycleCallbacks(
				appLifecycleCallbacks);

		if (AndroidUtils.DEBUG) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				LocaleList localeList = LocaleList.getDefault();
				Log.d(TAG,
						"LocaleList: " + localeList + "; Locale " + Locale.getDefault());
			}

			Log.d(TAG, "onCreate: appname="
					+ AndroidUtils.getProcessName(applicationContext, Process.myPid()));
			Log.d(TAG, "Build: id=" + Build.ID + ",type=" + Build.TYPE + ",device="
					+ Build.DEVICE);

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				ContentResolver contentResolver = BiglyBTApp.getContext().getContentResolver();
				List<UriPermission> persistedUriPermissions = contentResolver.getPersistedUriPermissions();
				Log.d(TAG,
						"persistedUriPermissions: " + persistedUriPermissions.toString());
				List<UriPermission> outgoingPersistedUriPermissions = contentResolver.getOutgoingPersistedUriPermissions();
				Log.d(TAG, "outgoingPersistedUriPermissions: "
						+ outgoingPersistedUriPermissions.toString());

				if (CLEAR_PERMISSIONS) {
					for (UriPermission permission : persistedUriPermissions) {
						contentResolver.releasePersistableUriPermission(permission.getUri(),
								(permission.isReadPermission()
										? Intent.FLAG_GRANT_READ_URI_PERMISSION : 0)
										| (permission.isWritePermission()
												? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0));
					}
				}
			}

		}
		isCoreProcess = AndroidUtils.getProcessName(applicationContext,
				Process.myPid()).endsWith(":core_service"); //NON-NLS
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Core Process? " + isCoreProcess);
		}

		new Thread(new Runnable() {
			@SuppressWarnings("HardCodedStringLiteral")
			public void run() {
				// Init App Preferences off of UI thread to prevent StrictModeDiskReadViolation
				final AppPreferences appPreferences = getAppPreferences();

				if (!isCoreProcess) {
					appPreferences.setNumOpens(appPreferences.getNumOpens() + 1);

					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "initMainApp: increased # opens");
					}
				}

				IAnalyticsTracker vet = AnalyticsTracker.getInstance();
				vet.registerExceptionReporter(applicationContext);

				DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
				vet.setDensity(dm.densityDpi);

				String deviceType = null;

				UiModeManager uiModeManager = (UiModeManager) BiglyBTApp.getContext().getSystemService(
						Context.UI_MODE_SERVICE);
				int currentModeType = uiModeManager == null
						? Configuration.UI_MODE_TYPE_UNDEFINED
						: uiModeManager.getCurrentModeType();
				switch (currentModeType) {
					case Configuration.UI_MODE_TYPE_TELEVISION:
						deviceType = "TV";
						break;
					case Configuration.UI_MODE_TYPE_APPLIANCE:
						deviceType = "Appliance";
						break;
					case Configuration.UI_MODE_TYPE_DESK:
						deviceType = "Desk";
						break;
					case Configuration.UI_MODE_TYPE_CAR:
						deviceType = "Car";
						break;
					case Configuration.UI_MODE_TYPE_WATCH:
						deviceType = "Watch";
						break;
					case Configuration.UI_MODE_TYPE_VR_HEADSET:
						deviceType = "VR-HS";
						break;
					default:
						if (AndroidUtils.DEBUG && !isCoreProcess) {
							Log.d(TAG, "UiModeManager.getCurrentModeType " + currentModeType);
						}
						if (AndroidUtils.isTV()) {
							deviceType = "TV-Guess";
							break;
						}
				}
				if (deviceType == null) {
					int i = applicationContext.getResources().getConfiguration().screenLayout
							& Configuration.SCREENLAYOUT_SIZE_MASK;
					switch (i) {
						case Configuration.SCREENLAYOUT_SIZE_LARGE:
							deviceType = "L";
							break;
						case Configuration.SCREENLAYOUT_SIZE_NORMAL:
							deviceType = "N";
							break;
						case Configuration.SCREENLAYOUT_SIZE_SMALL:
							deviceType = "S";
							break;
						case Configuration.SCREENLAYOUT_SIZE_XLARGE:
							deviceType = "XL";
							break;
					}

					if (deviceType == null) {
						deviceType = AndroidUtilsUI.pxToDp(
								Math.max(dm.widthPixels, dm.heightPixels)) + "dp";
					}
					if (AndroidUtils.isTV()) {
						deviceType = "TV-Guess-" + deviceType;
					} else if (AndroidUtils.isChromium()) {
						deviceType = "Chromium-" + deviceType;
					}
				}

				if (AndroidUtils.DEBUG && !isCoreProcess) {
					Log.d(TAG, "UIMode: " + deviceType);
				}
				vet.setDeviceType(deviceType);

				double xInches = dm.widthPixels / dm.xdpi;
				double yInches = dm.heightPixels / dm.ydpi;
				double x = Math.pow(xInches, 2);
				double y = Math.pow(yInches, 2);
				double screenInches = Math.sqrt(x + y);

				vet.setScreenInches(screenInches);

				try {
					DeviceName.DeviceInfo deviceInfo = DeviceName.getDeviceInfo(
							getContext());
					deviceName = deviceInfo.manufacturer + " " + deviceInfo.getName();
					if (AndroidUtils.DEBUG && !isCoreProcess) {
						Log.d(TAG, "Device: " + deviceName + ";" + deviceInfo.codename + ";"
								+ deviceInfo.model);
					}
				} catch (Throwable t) {
//					org.json.JSONException: End of input at character 0 of
//					    at org.json.JSONTokener.syntaxError(JSONTokener.java:450)
//					    at org.json.JSONTokener.nextValue(JSONTokener.java:97)
//					    at org.json.JSONArray.<init>(JSONArray.java:92)
//					    at org.json.JSONArray.<init>(JSONArray.java:108)
//					    at com.jaredrummler.android.device.DeviceName.getDeviceInfo(DeviceName.java:1763)
//					    at com.jaredrummler.android.device.DeviceName.getDeviceInfo(DeviceName.java:1698)
					deviceName = Build.MODEL;
				}
				vet.setDeviceName(deviceName);

				vet.logEvent("AppStart" + (isCoreProcess ? "Core" : ""));
			}
		}, "VET Init").start();

		if (!isCoreProcess) {
			initMainApp();
		}

	}

	private void initMainApp() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "initMainApp");
			DisplayMetrics dm = getContext().getResources().getDisplayMetrics();

			Log.d(TAG,
					"Display: " + dm.widthPixels + "px x " + dm.heightPixels
							+ "px; x/ydpi:" + dm.xdpi + "/" + dm.ydpi + "; density:"
							+ dm.densityDpi);
			Log.d(TAG, "Display: Using xdpi, " + pxToDpX(dm.widthPixels) + "dp x "
					+ pxToDpY(dm.heightPixels) + "dp");
			Log.d(TAG,
					"Display: Using dm.density, " + AndroidUtilsUI.pxToDp(dm.widthPixels)
							+ "dp x " + AndroidUtilsUI.pxToDp(dm.heightPixels) + "dp");
			Log.d(TAG,
					"Display: Using pxToInch, " + AndroidUtilsUI.pxToInchX(dm.widthPixels)
							+ "\" x " + AndroidUtilsUI.pxToInchY(dm.heightPixels) + "\"");
			Log.d(TAG,
					"Display: Using pxToInch, "
							+ (AndroidUtilsUI.pxToInchX(dm.widthPixels) * 160) + "dp x "
							+ (AndroidUtilsUI.pxToInchY(dm.heightPixels) * 160) + "dp");
			Log.d(TAG,
					"Display: Using dm.densityDpi, " + convertPixelsToDp(dm.widthPixels)
							+ "dp x " + convertPixelsToDp(dm.heightPixels) + "dp");

			Configuration configuration = BiglyBTApp.getContext().getResources().getConfiguration();

			Log.d(TAG, "Configuration: " + configuration.toString());

			String[] names = getPackageManager().getSystemSharedLibraryNames();
			Log.d(TAG, "SharedLibraryNames " + Arrays.toString(names));

			PackageManager pm = BiglyBTApp.getContext().getPackageManager();
			FeatureInfo[] systemAvailableFeatures = pm.getSystemAvailableFeatures();
			StringBuilder sbFeatures = new StringBuilder();
			sbFeatures.append("Features: ");
			for (FeatureInfo fi : systemAvailableFeatures) {
				sbFeatures.append(fi.name);
				sbFeatures.append(", ");
			}
			Log.d(TAG, sbFeatures.toString());
		}

		picassoInstance = new Picasso.Builder(applicationContext).addRequestHandler(
				new IcoRequestHandler()).build();

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "initMainApp: picassoInstance now initialized");
		}

		// Common hack to always show overflow icon on actionbar if menu has
		// overflow
		try {
			ViewConfiguration config = ViewConfiguration.get(this);
			Field menuKeyField = ViewConfiguration.class.getDeclaredField(
					"sHasPermanentMenuKey");

			if (menuKeyField != null) {
				menuKeyField.setAccessible(true);
				menuKeyField.setBoolean(config, false);
			}
		} catch (Exception e) {
			// presumably, not relevant
		}

		if (AndroidUtils.DEBUG) {
			AndroidUtils.dumpBatteryStats(applicationContext);
		}
	}

	@Override
	public void onTrimMemory(int level) {
		super.onTrimMemory(level);
		if (isCoreProcess) {
			return;
		}

		switch (level) {
			case TRIM_MEMORY_UI_HIDDEN: // not really a low memory event
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory TRIM_MEMORY_UI_HIDDEN");
				}
				break;
			case TRIM_MEMORY_BACKGROUND: // app moved to background
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory TRIM_MEMORY_BACKGROUND");
				}
				break;
			case TRIM_MEMORY_MODERATE:
				// app in middle of background list 
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory Moderate");
				}
				SessionManager.clearTorrentFilesCaches(true);
				break;
			case TRIM_MEMORY_COMPLETE:
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory Complete");
				}
				// app next to be killed unless more mem found
				SessionManager.clearTorrentCaches(false); // clear all
				break;
			case TRIM_MEMORY_RUNNING_MODERATE:
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory RunningModerate");
				}
				SessionManager.clearTorrentCaches(true); // clear all except
				// current
				break;
			case TRIM_MEMORY_RUNNING_LOW: // Low memory
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory RunningLow");
				}
				SessionManager.clearTorrentCaches(true); // clear all except
				// current
				SessionManager.clearTorrentFilesCaches(true); // clear all except last file
				break;
			case TRIM_MEMORY_RUNNING_CRITICAL:
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory RunningCritical");
				}
				SessionManager.clearTorrentCaches(true); // clear all except
				// current
				SessionManager.clearTorrentFilesCaches(true); // clear all except last file
				break;
			default:
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onTrimMemory " + level);
				}
		}
	}

	@Override
	public void onLowMemory() {
		if (isCoreProcess) {
			super.onLowMemory();
			return;
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onLowMemory");
		}
		SessionManager.clearTorrentCaches(false);
		super.onLowMemory();
	}

	private int pxToDpX(int px) {
		DisplayMetrics dm = getResources().getDisplayMetrics();

		return Math.round(px / (dm.xdpi / DisplayMetrics.DENSITY_DEFAULT));
	}

	private int pxToDpY(int py) {
		DisplayMetrics dm = getResources().getDisplayMetrics();

		return Math.round(py / (dm.ydpi / DisplayMetrics.DENSITY_DEFAULT));
	}

	private float convertPixelsToDp(float px) {
		DisplayMetrics dm = getResources().getDisplayMetrics();
		float dp = px / (dm.densityDpi / 160f);
		return Math.round(dp);
	}

	@Override
	public void onTerminate() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Application.onTerminate "
					+ (isCoreProcess ? "CoreProcess" : "MainProcess"));
		}

		// NOTE: This is never called except in emulation!
		if (networkState != null) {
			networkState.dipose();
		}

		super.onTerminate();
	}

	public static AppPreferences getAppPreferences() {
		synchronized (lock) {
			if (appPreferences == null) {
				appPreferences = AppPreferences.createAppPreferences(
						applicationContext);
			}
		}
		return appPreferences;
	}

	public static NetworkState getNetworkState() {
		if (networkState == null) {
			networkState = new NetworkState(applicationContext);
		}
		return networkState;
	}

	public static Context getContext() {
		return applicationContext;
	}

	@Thunk
	static class IcoRequestHandler
		extends RequestHandler
	{

		@Override
		public boolean canHandleRequest(Request data) {
			if (data.uri == null) {
				return false;
			}
			String path = data.uri.getPath();
			if (path == null) {
				return false;
			}
			return path.endsWith(".ico"); //NON-NLS
		}

		@Override
		public Result load(Request request, int networkPolicy)
				throws IOException {

			UrlConnectionDownloader downloader = new UrlConnectionDownloader(
					applicationContext);

//			OkHttpDownloader downloader = new OkHttpDownloader(
//					applicationContext);
			Downloader.Response response = downloader.load(request.uri,
					networkPolicy);

			if (response == null) {
				return null;
			}

			//Picasso.LoadedFrom loadedFrom = response.cached ? DISK : NETWORK;

			@SuppressWarnings("deprecation")
			Bitmap bitmap = response.getBitmap();
			if (bitmap != null) {
				return new Result(bitmap, Picasso.LoadedFrom.DISK);
			}

			InputStream is = response.getInputStream();
			if (is == null) {
				return null;
			}

			List<ICOImage> icoImages = ICODecoder.readExt(is);
			if (icoImages == null || icoImages.size() == 0) {
				return null;
			}

			/*
			for (ICOImage image : icoImages) {
				Log.d(TAG, "load: ICO #" + image.getIconIndex() + "=" + image.getWidth() + ";" + image.getColourCount());
			}
			*/
			Collections.sort(icoImages, new Comparator<ICOImage>() {
				@Override
				public int compare(ICOImage lhs, ICOImage rhs) {
					int i = AndroidUtils.integerCompare(lhs.getWidth(), rhs.getWidth());
					if (i == 0) {
						i = AndroidUtils.integerCompare(lhs.getColourDepth(),
								rhs.getColourDepth());
					}
					return -i;
				}
			});

			ICOImage biggestICO = icoImages.get(0);
			//Log.d(TAG, "load: got ICO " + biggestICO.getWidth() + ";" + biggestICO.getColourDepth());

			return new Result(biggestICO.getImage(), Picasso.LoadedFrom.NETWORK);
		}
	}

	public static Picasso getPicassoInstance() {
		return picassoInstance;
	}

	public static boolean isCoreProcess() {
		return isCoreProcess;
	}

	public static boolean isApplicationInForeground() {
		return appLifecycleCallbacks.isApplicationInForeground();
	}

	@Override
	protected void finalize()
			throws Throwable {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Application.finalize "
					+ (isCoreProcess ? "CoreProcess" : "MainProcess"));
		}
		super.finalize();
	}

}
