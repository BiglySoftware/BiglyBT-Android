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

import java.io.*;
import java.util.*;

import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.util.*;
import com.biglybt.android.widget.CustomToast;
import com.biglybt.util.Thunk;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;

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

	private static final String KEY_FIRST_INSTALL_TIME = "firstInstallTime";

	private static final String KEY_NUM_APP_OPENS = "numAppOpens";

	private static final String KEY_ASKED_RATING_ON = "askedRatingOn";

	private static final String KEY_NEVER_ASK_RATING_AGAIN = "neverAskRatingAgain";

	private static final String KEY_ASKED_GIVEBACK_ON = "askedGiveBackOn";

	private static final String KEY_NEVER_ASK_GIVEBACK_AGAIN = "neverAskGivebackAgain";

	private static final String PREF_ID = "AndroidRemote";
	// launches

	@Thunk
	final SharedPreferences preferences;

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
		void appPreferencesChanged();
	}

	@Thunk
	final Object mLock = new Object();

	@Thunk
	boolean saveQueued;

	protected static AppPreferences createAppPreferences(
			Application applicationContext) {
		return new AppPreferences(applicationContext);
	}

	private AppPreferences(Application applicationContext) {
		this.applicationContext = applicationContext;
		preferences = applicationContext.getSharedPreferences(PREF_ID,
				Activity.MODE_PRIVATE);
	}

	@Nullable
	public RemoteProfile getLastUsedRemote() {
		try {
			Map<String, Object> mapConfig = getPrefs();
			String lastUsed = (String) mapConfig.get(KEY_LASTUSED);
			if (lastUsed == null) {
				return null;
			}

			Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
			if (mapRemotes == null) {
				return null;
			}

			Map mapRemote = (Map) mapRemotes.get(lastUsed);
			if (mapRemote == null) {
				// backwards compat. KEY_LASTUSED used to be ac
				for (Object o : mapRemotes.values()) {
					if (o instanceof Map) {
						String ac = MapUtils.getMapString((Map) o, "ac", null);
						if (ac != null && ac.equals(lastUsed)) {
							mapRemote = (Map) o;
							break;
						}
					}
				}
			}
			if (mapRemote != null) {
				return new RemoteProfile(mapRemote);
			}
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			AnalyticsTracker.getInstance().logError(t);
		}

		return null;
	}

	public boolean remoteExists(String profileID) {
		try {
			Map<String, Object> mapConfig = getPrefs();

			Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
			if (mapRemotes != null) {
				return mapRemotes.containsKey(profileID);
			}
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			AnalyticsTracker.getInstance().logError(t);
		}

		return false;
	}

	public RemoteProfile getRemote(String profileID) {
		try {
			Map<String, Object> mapConfig = getPrefs();

			Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
			if (mapRemotes != null) {
				Object mapRemote = mapRemotes.get(profileID);
				if (mapRemote instanceof Map) {
					return new RemoteProfile((Map) mapRemote);
				}
			}
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			AnalyticsTracker.getInstance().logError(t);
		}

		return null;
	}

	public int getNumRemotes() {
		try {
			Map<String, Object> mapConfig = getPrefs();

			Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
			if (mapRemotes != null) {
				return mapRemotes.size();
			}
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			AnalyticsTracker.getInstance().logError(t);
		}

		return 0;
	}

	public RemoteProfile[] getRemotes() {
		List<RemoteProfile> listRemotes = new ArrayList<>(1);
		try {
			Map<String, Object> mapConfig = getPrefs();

			Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
			if (mapRemotes != null) {
				for (Object val : mapRemotes.values()) {
					if (val instanceof Map) {
						listRemotes.add(new RemoteProfile((Map) val));
					}
				}
			}
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			AnalyticsTracker.getInstance().logError(t);
		}

		return listRemotes.toArray(new RemoteProfile[listRemotes.size()]);
	}

	private @NonNull Map<String, Object> getPrefs() {
		synchronized (mLock) {
			if (mapConfig != null) {
				return mapConfig;
			}

			try {
				String config = preferences.getString(KEY_CONFIG, null);
				mapConfig = config == null ? new HashMap<String, Object>(4)
						: JSONUtils.decodeJSON(config);

				if (mapConfig == null) {
					mapConfig = new HashMap<>(4);
				}
			} catch (Throwable t) {
				if (AndroidUtils.DEBUG) {
					t.printStackTrace();
				}
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

				Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
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
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
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

				Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
				if (mapRemotes == null) {
					mapRemotes = new HashMap(4);
					mapConfig.put(KEY_REMOTES, mapRemotes);
				}
			}

			savePrefs();

		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
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
				Editor edit = preferences.edit();
				edit.putString(KEY_CONFIG, val);
				edit.commit();

				AppPreferencesChangedListener[] listeners = listAppPreferencesChangedListeners.toArray(
						new AppPreferencesChangedListener[listAppPreferencesChangedListeners.size()]);
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
		}).start();

	}

	public void removeRemoteProfile(String profileID) {
		try {
			Map<String, Object> mapConfig = getPrefs();

			Object mapRemote;
			synchronized (mLock) {
				Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
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
				RemoteProfile rp = new RemoteProfile((Map) mapRemote);
				AnalyticsTracker.getInstance().sendEvent(AnalyticsTracker.CAT_PROFILE,
						AnalyticsTracker.ACTION_REMOVED, rp.getRemoteTypeName(), null);
			} else {
				AnalyticsTracker.getInstance().sendEvent(AnalyticsTracker.CAT_PROFILE,
						AnalyticsTracker.ACTION_REMOVED, null, null);
			}

		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			AnalyticsTracker.getInstance().logError(t);
		}
	}

	public SharedPreferences getSharedPreferences() {
		return preferences;
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
		Editor edit = preferences.edit();
		edit.putLong(KEY_NUM_APP_OPENS, num);
		edit.commit();
	}

	private void setAskedRating() {
		Editor edit = preferences.edit();
		edit.putLong(KEY_ASKED_RATING_ON, System.currentTimeMillis());
		edit.commit();
	}

	private long getAskedRatingOn() {
		return preferences.getLong(KEY_ASKED_RATING_ON, 0);
	}

	@Thunk
	void setNeverAskRatingAgain() {
		Editor edit = preferences.edit();
		edit.putBoolean(KEY_NEVER_ASK_RATING_AGAIN, true);
		edit.commit();
	}

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

	public void showRateDialog(final Activity mContext) {

		if (!shouldShowRatingReminder()) {
			return;
		}

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
		setAskedRating();

		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		builder.setMessage(R.string.ask_rating_message).setCancelable(
				false).setPositiveButton(R.string.rate_now,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int which) {
								final String appPackageName = mContext.getPackageName();
								try {
									mContext.startActivity(new Intent(Intent.ACTION_VIEW,
											Uri.parse("market://details?id=" + appPackageName)));
								} catch (android.content.ActivityNotFoundException anfe) {
									mContext.startActivity(new Intent(Intent.ACTION_VIEW,
											Uri.parse("http://play.google.com/store/apps/details?id="
													+ appPackageName)));
								}
								setNeverAskRatingAgain();
								AnalyticsTracker.getInstance(mContext).sendEvent(
										AnalyticsTracker.CAT_UI_ACTION,
										AnalyticsTracker.ACTION_RATING, "AskStoreClick", null);
							}
						}).setNeutralButton(R.string.later,
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
									}
								}).setNegativeButton(R.string.no_thanks,
										new DialogInterface.OnClickListener() {
											@Override
											public void onClick(DialogInterface dialog, int which) {
												setNeverAskRatingAgain();
											}
										});
		AlertDialog dialog = builder.create();

		AnalyticsTracker.getInstance(mContext).sendEvent(
				AnalyticsTracker.CAT_UI_ACTION, AnalyticsTracker.ACTION_RATING,
				"AskShown", null);
		dialog.show();
	}

	public static void importPrefs(final AppCompatActivityM activity,
			final Uri uri) {
		activity.requestPermissions(new String[] {
			Manifest.permission.READ_EXTERNAL_STORAGE
		}, new Runnable() {
			@Override
			public void run() {
				importPrefs_withPerms(activity, uri);
			}
		}, new Runnable() {
			@Override
			public void run() {
				CustomToast.showText(R.string.content_read_failed_perms_denied,
						Toast.LENGTH_LONG);
			}
		});
	}

	@Thunk
	static boolean importPrefs_withPerms(Activity activity, Uri uri) {
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

				String s = new String(AndroidUtils.readInputStreamAsByteArray(stream));

				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onActivityResult: read " + s);
				}
				stream.close();

				Map<String, Object> map = JSONUtils.decodeJSON(s);

				BiglyBTApp.getAppPreferences().replacePreferences(map);

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
				AndroidUtilsUI.showDialog(activity, "Error Loading Config",
						uri.toString() + " could not be loaded. " + e.toString());
			} catch (Exception e) {
				e.printStackTrace();
				AndroidUtilsUI.showDialog(activity, "Error Loading Config",
						uri.toString() + " could not be parsed. " + e.toString());
			}
		} else {
			AndroidUtilsUI.showDialog(activity, "Error Loading Config",
					uri.toString() + " is not a file or content.");
		}
		return false;
	}

	public static void exportPrefs(final AppCompatActivityM activity) {
		activity.requestPermissions(new String[] {
			Manifest.permission.WRITE_EXTERNAL_STORAGE
		}, new Runnable() {
			@Override
			public void run() {
				exportPrefs((AppCompatActivity) activity);
			}
		}, new Runnable() {
			@Override
			public void run() {
				CustomToast.showText(R.string.content_saved_failed_perms_denied,
						Toast.LENGTH_LONG);
			}
		});
	}

	@Thunk
	static void exportPrefs(final AppCompatActivity activity) {
		new Thread(new Runnable() {
			String failText = null;

			@Override
			public void run() {
				String c = BiglyBTApp.getAppPreferences().getSharedPreferences().getString(
						KEY_CONFIG, "");
				final File directory = AndroidUtils.getDownloadDir();
				final File outFile = new File(directory, "BiglyBTSettings.json");

				try {
					BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
					writer.write(c);
					writer.close();
				} catch (Exception e) {
					AnalyticsTracker.getInstance().logError(e);
					if (AndroidUtils.DEBUG) {
						Log.e(TAG, "exportPrefs", e);
					}
					failText = e.getMessage();
				}
				String s;
				if (failText == null) {
					s = activity.getResources().getString(R.string.content_saved,
							TextUtils.htmlEncode(outFile.getName()),
							TextUtils.htmlEncode(outFile.getParent()));
				} else {
					s = activity.getResources().getString(R.string.content_saved_failed,
							TextUtils.htmlEncode(outFile.getName()),
							TextUtils.htmlEncode(outFile.getParent()),
							TextUtils.htmlEncode(failText));
				}
				CustomToast.showText(AndroidUtils.fromHTML(s), Toast.LENGTH_LONG);
			}
		}).start();
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
		Editor edit = preferences.edit();
		edit.putBoolean(KEY_NEVER_ASK_GIVEBACK_AGAIN, true);
		edit.commit();
	}
}
