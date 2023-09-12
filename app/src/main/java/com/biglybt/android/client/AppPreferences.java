/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.biglybt.android.client.AppCompatActivityM.PermissionRequestResults;
import com.biglybt.android.client.AppCompatActivityM.PermissionResultHandler;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.RemoteProfileFactory;
import com.biglybt.android.util.*;
import com.biglybt.android.widget.CustomToast;
import com.biglybt.util.RunnableWorkerThread;
import com.biglybt.util.Thunk;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.grandcentrix.tray.TrayPreferences;

import java.io.*;
import java.util.*;

@SuppressWarnings("rawtypes")
public class AppPreferences
{

	private static final String KEY_REMOTES = "remotes";

	private static final String KEY_CONFIG = "config";

	private static final String KEY_LASTUSED = "lastUsed";

	@Thunk
	static final String TAG = "AppPrefs";

	private static final long RATING_REMINDER_MIN_INSTALL_MS = DateUtils.DAY_IN_MILLIS
			* 30; // 30 days from first install

	private static final long RATING_REMINDER_MIN_UPDATE_MS = DateUtils.DAY_IN_MILLIS
			* 5; // 5 days from last update

	private static final long RATING_REMINDER_MIN_INTERVAL_MS = DateUtils.DAY_IN_MILLIS
			* 60; // 60 days from last shown

	private static final long RATING_REMINDER_MIN_LAUNCHES = 10; // at least 10

	private static final String KEY_NUM_APP_OPENS = "numAppOpens";

	private static final String KEY_ASKED_RATING_ON = "askedRatingOn";

	private static final String KEY_NEVER_ASK_RATING_AGAIN = "neverAskRatingAgain";

	private static final String KEY_NEVER_ASK_GIVEBACK_AGAIN = "neverAskGivebackAgain";

	private static final String KEY_IS_THEME_DARK = "isDarkTheme";

	private static final String KEY_OLD_AC = "ac";
	// launches

	@Thunk
	@NonNull
	final ImportPreferences preferences;

	@Thunk
	Map<String, Object> mapConfig;

	@Thunk
	final Object mapConfigLock = new Object();

	private final Application applicationContext;

	@Thunk
	List<AppPreferencesChangedListener> listAppPreferencesChangedListeners = new ArrayList<>(
			1);

	public interface AppPreferencesChangedListener
	{
		@AnyThread
		void appPreferencesChanged();
	}

	@Thunk
	final Object mLock = new Object();

	@Thunk
	boolean saveQueued;

	/**
	 * Store isThemeDark in variable to avoid disk access. 
	 * It's accessed synchronously during activity startup 
	 */
	private boolean isThemeDark;

	@WorkerThread
	static AppPreferences createAppPreferences(Application applicationContext) {
		return new AppPreferences(applicationContext);
	}

	private AppPreferences(Application applicationContext) {
		this.applicationContext = applicationContext;
		preferences = new ImportPreferences(applicationContext);
		isThemeDark = preferences.getBoolean(KEY_IS_THEME_DARK, false);
	}

	public boolean getBoolean(String s, boolean defaultValue) {
		return preferences.getBoolean(s, defaultValue);
	}

	@Nullable
	public RemoteProfile getLastUsedRemote() {
		try {
			Map<String, Object> mapConfig = getPrefs();
			String lastUsed = (String) mapConfig.get(KEY_LASTUSED);
			if (lastUsed == null) {
				return null;
			}

			Map<String, Object> mapRemotes = MapUtils.getMapMap(mapConfig,
					KEY_REMOTES, null);
			if (mapRemotes == null) {
				return null;
			}

			Map mapRemote = (Map) mapRemotes.get(lastUsed);
			if (mapRemote == null) {
				// backwards compat. KEY_LASTUSED used to be ac
				for (Object o : mapRemotes.values()) {
					if (o instanceof Map) {
						String ac = MapUtils.getMapString((Map) o, KEY_OLD_AC, null);
						if (ac != null && ac.equals(lastUsed)) {
							mapRemote = (Map) o;
							break;
						}
					}
				}
			}
			if (mapRemote != null) {
				return RemoteProfileFactory.create(mapRemote);
			}
		} catch (Throwable t) {
			AnalyticsTracker.getInstance().logError(t);
		}

		return null;
	}

	@SuppressWarnings("WeakerAccess")
	@WorkerThread
	public String getString(String keyConfig, String s) {
		return preferences.getString(keyConfig, s);
	}

	public boolean isThemeDark() {
		return isThemeDark;
	}

	@WorkerThread
	public void setBoolean(String keyConfig, boolean val) {
		preferences.put(keyConfig, val);
	}

	public void setThemeDark(boolean isDark) {
		isThemeDark = isDark;
		OffThread.runOffUIThread(() -> preferences.put(KEY_IS_THEME_DARK, isDark));
	}

	public boolean remoteExists(String profileID) {
		try {
			Map<String, Object> mapConfig = getPrefs();

			Map<String, Object> mapRemotes = MapUtils.getMapMap(mapConfig,
					KEY_REMOTES, null);
			if (mapRemotes != null) {
				return mapRemotes.containsKey(profileID);
			}
		} catch (Throwable t) {
			AnalyticsTracker.getInstance().logError(t);
		}

		return false;
	}

	public RemoteProfile getRemote(String profileID) {
		try {
			Map<String, Object> mapConfig = getPrefs();

			Map<String, Object> mapRemotes = MapUtils.getMapMap(mapConfig,
					KEY_REMOTES, null);
			if (mapRemotes != null) {
				Object mapRemote = mapRemotes.get(profileID);
				if (mapRemote instanceof Map) {
					return RemoteProfileFactory.create((Map) mapRemote);
				}
			}
		} catch (Throwable t) {
			AnalyticsTracker.getInstance().logError(t);
		}

		return null;
	}

	public int getNumRemotes() {
		try {
			Map<String, Object> mapConfig = getPrefs();

			Map<String, Object> mapRemotes = MapUtils.getMapMap(mapConfig,
					KEY_REMOTES, null);
			if (mapRemotes != null) {
				return mapRemotes.size();
			}
		} catch (Throwable t) {
			AnalyticsTracker.getInstance().logError(t);
		}

		return 0;
	}

	public boolean hasRemotes() {
		try {
			Map<String, Object> mapConfig = getPrefs();

			Map<String, Object> mapRemotes = MapUtils.getMapMap(mapConfig,
					KEY_REMOTES, null);
			return mapRemotes != null && mapRemotes.size() > 0;
		} catch (Throwable t) {
			AnalyticsTracker.getInstance().logError(t);
		}
		return false;
	}

	public RemoteProfile[] getRemotes() {
		List<RemoteProfile> listRemotes = new ArrayList<>(1);
		try {
			Map<String, Object> mapConfig = getPrefs();

			Map<String, Object> mapRemotes = MapUtils.getMapMap(mapConfig,
					KEY_REMOTES, null);
			if (mapRemotes != null) {
				for (Object val : mapRemotes.values()) {
					if (val instanceof Map) {
						listRemotes.add(RemoteProfileFactory.create((Map) val));
					}
				}
			}
		} catch (Throwable t) {
			AnalyticsTracker.getInstance().logError(t);
		}

		return listRemotes.toArray(new RemoteProfile[0]);
	}

	private @NonNull Map<String, Object> getPrefs() {
		synchronized (mLock) {
			if (mapConfig != null) {
				return mapConfig;
			}

			try {
				String config = preferences.getString(KEY_CONFIG, null);
				mapConfig = config == null ? new HashMap<>(4)
						: JSONUtils.decodeJSON(config);
			} catch (Throwable t) {
				AnalyticsTracker.getInstance().logError(t);
			}
		}
		return mapConfig;
	}

	@SuppressWarnings("unchecked")
	public void addRemoteProfile(RemoteProfile rp) {
		try {
			boolean isNew;
			synchronized (mLock) {
				Map<String, Object> mapConfig = getPrefs();

				Map<String, Object> mapRemotes = MapUtils.getMapMap(mapConfig,
						KEY_REMOTES, null);
				if (mapRemotes == null) {
					mapRemotes = new HashMap(4);
					mapConfig.put(KEY_REMOTES, mapRemotes);
				}

				isNew = !mapRemotes.containsKey(rp.getID());
				mapRemotes.put(rp.getID(), rp.getAsMap(true));

				savePrefs();
			}

			if (isNew) {
				AnalyticsTracker.getInstance().sendEvent(AnalyticsTracker.CAT_PROFILE,
						"Created", rp.getRemoteTypeName(), null);
			}

		} catch (Throwable t) {
			AnalyticsTracker.getInstance().logError(t);
		}

	}

	public void setLastRemote(@Nullable RemoteProfile remoteProfile) {
		try {
			synchronized (mLock) {
				Map<String, Object> mapConfig = getPrefs();

				if (remoteProfile == null) {
					mapConfig.remove(KEY_LASTUSED);
				} else {
					mapConfig.put(KEY_LASTUSED, remoteProfile.getID());
				}

				Map<String, Object> mapRemotes = MapUtils.getMapMap(mapConfig,
						KEY_REMOTES, null);
				if (mapRemotes == null) {
					mapRemotes = new HashMap<>(4);
					mapConfig.put(KEY_REMOTES, mapRemotes);
				}
			}

			savePrefs();

		} catch (Throwable t) {
			AnalyticsTracker.getInstance().logError(t);
		}

	}

	@Thunk
	void savePrefs() {
		synchronized (mapConfigLock) {
			if (saveQueued) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Save Preferences Skipped: "
							+ AndroidUtils.getCompressedStackTrace());
				}
				return;
			}
			saveQueued = true;
			if (AndroidUtils.DEBUG) {
				Log.d(TAG,
						"Save Preferences: " + AndroidUtils.getCompressedStackTrace());
			}
		}

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(100);
				} catch (InterruptedException ignore) {
				}
				savePrefsNow();
			}

			private void savePrefsNow() {
				String val;
				synchronized (mapConfigLock) {
					if (mapConfig == null) {
						return;
					}
					val = JSONUtils.encodeToJSON(mapConfig);
				}

				saveQueued = false;
				preferences.put(KEY_CONFIG, val);

				AppPreferencesChangedListener[] listeners = listAppPreferencesChangedListeners.toArray(
						new AppPreferencesChangedListener[0]);
				for (AppPreferencesChangedListener l : listeners) {
					l.appPreferencesChanged();
				}

				if (AndroidUtils.DEBUG) {
					try {
						Log.d(TAG, "Saved Preferences: ");
						//		+ new org.json.JSONObject(mapConfig).toString(2));
					} catch (Throwable t) {
						t.printStackTrace();
					}
				}

				// After x seconds, null mapConfig to save memory
				try {
					Thread.sleep(2000);
				} catch (InterruptedException ignore) {
				}

				synchronized (mLock) {
					if (!saveQueued && mapConfig != null) {
						mapConfig = null;
						if (AndroidUtils.DEBUG) {
							Log.d(TAG, "Clear map, save memory");
						}
					}
				}

			}
		}, "SavePrefs").start();

	}

	public void removeRemoteProfile(String profileID) {
		try {
			Map<String, Object> mapConfig = getPrefs();

			Object mapRemote;
			synchronized (mLock) {
				Map<String, Object> mapRemotes = MapUtils.getMapMap(mapConfig,
						KEY_REMOTES, null);
				if (mapRemotes == null) {
					return;
				}

				mapRemote = mapRemotes.remove(profileID);
				if (mapRemote == null) {
					return;
				}

				savePrefs();
			}

			if (mapRemote instanceof Map) {
				RemoteProfile rp = RemoteProfileFactory.create((Map) mapRemote);
				AnalyticsTracker.getInstance().sendEvent(AnalyticsTracker.CAT_PROFILE,
						AnalyticsTracker.ACTION_REMOVED, rp.getRemoteTypeName(), null);
			} else {
				AnalyticsTracker.getInstance().sendEvent(AnalyticsTracker.CAT_PROFILE,
						AnalyticsTracker.ACTION_REMOVED, null, null);
			}

		} catch (Throwable t) {
			AnalyticsTracker.getInstance().logError(t);
		}
	}

	private long getFirstInstalledOn() {
		try {
			String packageName = applicationContext.getPackageName();
			PackageInfo packageInfo = applicationContext.getPackageManager().getPackageInfo(
					packageName, 0);
			return packageInfo.firstInstallTime;
		} catch (Exception ignore) {
		}
		return System.currentTimeMillis();
	}

	private long getLastUpdatedOn() {
		try {
			String packageName = applicationContext.getPackageName();
			PackageInfo packageInfo = applicationContext.getPackageManager().getPackageInfo(
					packageName, 0);
			return packageInfo.lastUpdateTime;
		} catch (Exception ignore) {
		}
		return System.currentTimeMillis();
	}

	public long getNumOpens() {
		return preferences.getLong(KEY_NUM_APP_OPENS, 0);
	}

	public void setNumOpens(long num) {
		preferences.put(KEY_NUM_APP_OPENS, num);
	}

	@WorkerThread
	private void setAskedRating() {
		preferences.put(KEY_ASKED_RATING_ON, System.currentTimeMillis());
	}

	@WorkerThread
	private long getAskedRatingOn() {
		return preferences.getLong(KEY_ASKED_RATING_ON, 0);
	}

	@Thunk
	@WorkerThread
	void setNeverAskRatingAgain() {
		preferences.put(KEY_NEVER_ASK_RATING_AGAIN, true);
	}

	@WorkerThread
	private boolean getNeverAskRatingAgain() {
		return BuildConfig.FLAVOR.toLowerCase().contains(
				BuildConfig.FLAVOR_gaD.toLowerCase())
						? preferences.getBoolean(KEY_NEVER_ASK_RATING_AGAIN, false) : true;
	}

	private boolean shouldShowRatingReminder() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG,
					"# Opens: " + getNumOpens() + "\n" + "FirstInstalledOn: "
							+ ((System.currentTimeMillis() - getFirstInstalledOn())
									/ DateUtils.HOUR_IN_MILLIS)
							+ "hr\n" + "LastUpdatedOn: "
							+ ((System.currentTimeMillis() - getLastUpdatedOn())
									/ DateUtils.HOUR_IN_MILLIS)
							+ "hr\n" + "AskedRatingOn: "
							+ ((System.currentTimeMillis() - getAskedRatingOn())
									/ DateUtils.HOUR_IN_MILLIS)
							+ "hr\n");
		}
		if (!getNeverAskRatingAgain()
				&& getNumOpens() > RATING_REMINDER_MIN_LAUNCHES
				&& System.currentTimeMillis()
						- getFirstInstalledOn() > RATING_REMINDER_MIN_INSTALL_MS
				&& System.currentTimeMillis()
						- getLastUpdatedOn() > RATING_REMINDER_MIN_UPDATE_MS
				&& System.currentTimeMillis()
						- getAskedRatingOn() > RATING_REMINDER_MIN_INTERVAL_MS) {
			return true;
		}
		return false;
	}

	@WorkerThread
	public void showRateDialog(final Activity mContext) {
		// shouldShowRatingReminder is slow
		if (!shouldShowRatingReminder()) {
			return;
		}

		mContext.runOnUiThread(() -> {
			if (mContext.isFinishing()) {
				return;
			}
			ui_ShowRateDialog(mContext);
		});
	}

	@UiThread
	private void ui_ShowRateDialog(final Activity mContext) {
		// skip showing if they are adding a torrent (or anything else)
		Intent intent = mContext.getIntent();
		if (intent != null) {
			Uri data = intent.getData();
			if (data != null) {
				return;
			}
		}

		// even if something goes wrong, we want to set that we asked, so
		// it doesn't continue to pop up
		OffThread.runOffUIThread(this::setAskedRating);

		AlertDialog.Builder builder = new MaterialAlertDialogBuilder(mContext);
		builder.setMessage(R.string.ask_rating_message).setCancelable(
				false).setPositiveButton(R.string.rate_now, (dialog, which) -> {
					AndroidUtilsUI.openMarket(mContext, mContext.getPackageName());
					setNeverAskRatingAgain();
					AnalyticsTracker.getInstance(mContext).sendEvent(
							AnalyticsTracker.CAT_UI_ACTION, AnalyticsTracker.ACTION_RATING,
							"AskStoreClick", null);
				}).setNeutralButton(R.string.later, (dialog, which) -> {
				}).setNegativeButton(R.string.no_thanks,
						(dialog, which) -> setNeverAskRatingAgain());
		AlertDialog dialog = builder.create();

		AnalyticsTracker.getInstance(mContext).sendEvent(
				AnalyticsTracker.CAT_UI_ACTION, AnalyticsTracker.ACTION_RATING,
				"AskShown", null);
		dialog.show();
	}

	public void importPrefs(@NonNull Context context,
			@NonNull ActivityResultLauncher<Intent> launcher) {
		FileUtils.launchFileChooser(context, FileUtils.getMimeTypeForExt("json"),
				launcher);
	}

	public void importPrefs(final AppCompatActivityM activity, final Uri uri,
			RunnableWorkerThread runAfterImport) {
		activity.requestPermissions(new String[] {
			Manifest.permission.READ_EXTERNAL_STORAGE
		}, new PermissionResultHandler() {
			@WorkerThread
			@Override
			public void onAllGranted() {
				importPrefs_withPerms(activity, uri, runAfterImport);
			}

			@WorkerThread
			@Override
			public void onSomeDenied(PermissionRequestResults results) {
				CustomToast.showText(R.string.content_read_failed_perms_denied,
						Toast.LENGTH_LONG);
			}
		});
	}

	@Thunk
	@WorkerThread
	boolean importPrefs_withPerms(FragmentActivity activity, Uri uri,
			RunnableWorkerThread runAfterImport) {
		if (uri == null) {
			return false;
		}
		String scheme = uri.getScheme();
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onActivityResult: scheme=" + scheme);
		}
		if ("file".equals(scheme) || "content".equals(scheme)) {
			try {
				InputStream stream = FileUtils.getInputStream(activity, uri);
				if (stream == null) {
					return false;
				}

				ByteArrayOutputStream bab = new ByteArrayOutputStream(8192);
				boolean ok = AndroidUtils.readInputStreamIfStartWith(stream, bab,
						new byte[] {
							'{'
						});
				stream.close();

				if (!ok) {
					AndroidUtilsUI.showDialog(activity,
							R.string.dialog_title_error_loading_config,
							R.string.hardcoded_string,
							uri.toString() + " could not be parsed as JSON.");
					return false;
				}

				String s = bab.toString();

				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onActivityResult: read " + s);
				}

				Map<String, Object> map = JSONUtils.decodeJSON(s);

				replacePreferences(map);

				if (runAfterImport != null) {
					runAfterImport.run();
				}

				return true;

			} catch (FileNotFoundException e) {
				if (AndroidUtils.DEBUG) {
					e.printStackTrace();
				}
				CustomToast.showText(
						AndroidUtils.fromHTML("<b>" + uri + "</b> not found"),
						Toast.LENGTH_LONG);
			} catch (IOException e) {
				e.printStackTrace();
				AndroidUtilsUI.showDialog(activity,
						R.string.dialog_title_error_loading_config,
						R.string.hardcoded_string,
						uri.toString() + " could not be loaded. " + e.toString());
			} catch (Exception e) {
				e.printStackTrace();
				AndroidUtilsUI.showDialog(activity,
						R.string.dialog_title_error_loading_config,
						R.string.hardcoded_string,
						uri.toString() + " could not be parsed. " + e.toString());
			}
		} else {
			AndroidUtilsUI.showDialog(activity,
					R.string.dialog_title_error_loading_config, R.string.hardcoded_string,
					uri.toString() + " is not a file or content.");
		}
		return false;
	}

	public void exportPrefs(@NonNull AppCompatActivityM activity,
			@NonNull ActivityResultLauncher<Intent> launcher) {
		activity.requestPermissions(new String[] {
			Manifest.permission.WRITE_EXTERNAL_STORAGE
		}, new PermissionResultHandler() {
			@WorkerThread
			@Override
			public void onAllGranted() {
				exportPrefs_withPerms(activity, launcher);
			}

			@WorkerThread
			@Override
			public void onSomeDenied(PermissionRequestResults results) {
				CustomToast.showText(R.string.content_saved_failed_perms_denied,
						Toast.LENGTH_LONG);
			}
		});
	}
	
	@Thunk
	void exportPrefs_withPerms(@NonNull Context context,
			@NonNull ActivityResultLauncher<Intent> launcher) {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
			Intent intent = FileUtils.createNewFileChooserIntent("application/json",
					null, "BiglyBTSettings.json");
			if (intent != null) {
				launcher.launch(intent);
				return;
			}
		}
		OffThread.runOffUIThread(() -> exportPrefs(context, null));
	}

	@WorkerThread
	public void exportPrefs(@NonNull Context context, @Nullable Uri uri) {
		String c = getAllPrefsAsString();

		String resultText;
		String failText = null;
		BufferedWriter writer = null;
		String filename = "BiglyBTSettings.json";
		String parentPath = "";
		try {
			if (uri == null) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Writing " + filename + " via fallback");
				}
				final File directory = AndroidUtils.getDownloadDir();
				final File outFile = new File(directory, filename);
				parentPath = outFile.getParent();

				writer = new BufferedWriter(new FileWriter(outFile));
			} else {
				OutputStream outputStream = context.getContentResolver().openOutputStream(
						uri, "w");
				PathInfo pathInfo = PathInfo.buildPathInfo(uri.toString());
				if (pathInfo.storageVolumeName != null) {
					parentPath = pathInfo.storageVolumeName;
				} else if (pathInfo.storagePath != null) {
					parentPath = pathInfo.storagePath;
				}
				if (pathInfo.shortName != null) {
					filename = pathInfo.shortName;
				} else {
					filename = FileUtils.getUriTitle(context, uri);
				}
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Writing " + filename + " via Uri " + uri);
				}
				writer = new BufferedWriter(new OutputStreamWriter(outputStream));
			}

			writer.write(c);

		} catch (Exception e) {
			AnalyticsTracker.getInstance().logError(e);
			failText = e.getMessage();
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException ignore) {
				}
			}
		}

		if (failText == null) {
			resultText = context.getResources().getString(R.string.content_saved,
					TextUtils.htmlEncode(filename), TextUtils.htmlEncode(parentPath));
		} else {
			resultText = context.getResources().getString(
					R.string.content_saved_failed, TextUtils.htmlEncode(filename),
					TextUtils.htmlEncode(parentPath), TextUtils.htmlEncode(failText));
		}
		CustomToast.showText(AndroidUtils.fromHTML(resultText), Toast.LENGTH_LONG);
	}

	private String getAllPrefsAsString() {
		return getString(KEY_CONFIG, "");
	}

	private void replacePreferences(Map<String, Object> map) {
		if (map == null || map.size() == 0) {
			return;
		}

		synchronized (mLock) {
			mapConfig.clear();

			mapConfig.putAll(map);

			savePrefs();
		}

		AnalyticsTracker.getInstance().sendEvent(AnalyticsTracker.CAT_PROFILE,
				"Import", null, null);
	}

	public void addAppPreferencesChangedListener(
			AppPreferencesChangedListener l) {
		if (listAppPreferencesChangedListeners.contains(l)) {
			return;
		}
		listAppPreferencesChangedListeners.add(l);
	}

	public void removeAppPreferencesChangedListener(
			AppPreferencesChangedListener l) {
		listAppPreferencesChangedListeners.remove(l);
	}

	public void setNeverAskGivebackAgain() {
		preferences.put(KEY_NEVER_ASK_GIVEBACK_AGAIN, true);
	}

	@NonNull
	public TrayPreferences getPreferences() {
		return preferences;
	}
}
