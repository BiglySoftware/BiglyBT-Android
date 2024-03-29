package com.biglybt.android.client;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.UriPermission;
import android.os.Build;
import android.os.LocaleList;
import android.os.StrictMode;
import android.util.Log;

import java.util.List;
import java.util.Locale;

import leakcanary.AppWatcher;
import leakcanary.AppWatcher.Config;

@SuppressLint({
	"Registered", // False Positive: Registered in 'debug' build type
	"LogConditional"
})
public class DebugBiglyBTApp
	extends BiglyBTApp
{
	private static final boolean CLEAR_PERMISSIONS = false;

	@Override
	public void onCreate() {

//		leakcanary.LeakCanary.Config config = leakcanary.LeakCanary.getConfig().newBuilder().retainedVisibleThreshold(
//					1).build();
		Config config = AppWatcher.getConfig().copy(false, false, false, false, 0,
				false);
		AppWatcher.setConfig(config);
//		Log.e(TAG, "onCreate: WAITING");
//		try {
//			Thread.sleep(10000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}

		Log.d(TAG, "Application.onCreate " + BuildConfig.FLAVOR + " "
				+ getApplicationContext() + ";" + getBaseContext());

		StrictMode.setThreadPolicy(
				new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().detectCustomSlowCalls().penaltyLog().build());
		StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder().detectActivityLeaks().penaltyLog();
//			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//				builder.detectCleartextNetwork();
//			}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			builder.detectContentUriWithoutPermission();
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			builder.detectFileUriExposure();
		}
		builder.detectLeakedClosableObjects();
		builder.detectLeakedRegistrationObjects();
		builder.detectLeakedSqlLiteObjects();
		StrictMode.setVmPolicy(builder.build());

		super.onCreate();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			LocaleList localeList = LocaleList.getDefault();
			Log.d(TAG,
					"LocaleList: " + localeList + "; Locale " + Locale.getDefault());
		}

		Log.d(TAG, "onCreate: appname="
				+ AndroidUtils.getProcessName(applicationContext));
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
}
