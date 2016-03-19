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
 * 
 */

package com.vuze.android.remote;

import java.io.*;
import java.util.*;

import org.json.JSONException;

import com.vuze.util.JSONUtils;
import com.vuze.util.MapUtils;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
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

	private static final String TAG = "AppPrefs";

	private static final long RATING_REMINDER_MIN_INSTALL_MS = DateUtils.DAY_IN_MILLIS
			* 30; // 30 days from first install

	private static final long RATING_REMINDER_MIN_UPDATE_MS = DateUtils.DAY_IN_MILLIS
			* 5; // 5 days from last update

	private static final long RATING_REMINDER_MIN_INTERVAL_MS = DateUtils.DAY_IN_MILLIS
			* 60; // 60 days from last shown

	private static final long RATING_REMINDER_MIN_LAUNCHES = 10; // at least 10 launches

	private SharedPreferences preferences;

	private Context context;

	protected static AppPreferences createAppPreferences(Context context) {
		return new AppPreferences(context);
	}

	private AppPreferences(Context context) {
		this.context = context;
		preferences = context.getSharedPreferences("AndroidRemote",
				Activity.MODE_PRIVATE);
	}

	public RemoteProfile getLastUsedRemote() {
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			if (config != null) {
				Map<String, Object> mapConfig = JSONUtils.decodeJSON(config);

				String lastUsed = (String) mapConfig.get(KEY_LASTUSED);
				if (lastUsed != null) {
					Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
					if (mapRemotes != null) {
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
					}
				}
			}
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			VuzeEasyTracker.getInstance().logError(t);
		}

		return null;
	}

	public RemoteProfile getRemote(String profileID) {
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			if (config != null) {
				Map<String, Object> mapConfig = JSONUtils.decodeJSON(config);

				Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
				if (mapRemotes != null) {
					Object mapRemote = mapRemotes.get(profileID);
					if (mapRemote instanceof Map) {
						return new RemoteProfile((Map) mapRemote);
					}
				}
			}
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			VuzeEasyTracker.getInstance().logError(t);
		}

		return null;
	}

	public int getNumRemotes() {
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			if (config != null) {
				Map<String, Object> mapConfig = JSONUtils.decodeJSON(config);

				if (mapConfig != null) {
					Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
					if (mapRemotes != null) {
						return mapRemotes.size();
					}
				}
			}
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			VuzeEasyTracker.getInstance().logError(t);
		}

		return 0;
	}

	public RemoteProfile[] getRemotes() {
		List<RemoteProfile> listRemotes = new ArrayList<>(1);
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			if (config != null) {
				Map<String, Object> mapConfig = JSONUtils.decodeJSON(config);

				if (mapConfig != null) {
					Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
					if (mapRemotes != null) {
						for (Object val : mapRemotes.values()) {
							if (val instanceof Map) {
								listRemotes.add(new RemoteProfile((Map) val));
							}
						}
					}
				}
			}
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			VuzeEasyTracker.getInstance().logError(t);
		}

		return listRemotes.toArray(new RemoteProfile[listRemotes.size()]);
	}

	@SuppressWarnings("unchecked")
	public void addRemoteProfile(RemoteProfile rp) {
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			Map<String, Object> mapConfig = config == null
					? new HashMap<String, Object>() : JSONUtils.decodeJSON(config);

			if (mapConfig == null) {
				mapConfig = new HashMap<>();
			}

			Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
			if (mapRemotes == null) {
				mapRemotes = new HashMap();
				mapConfig.put(KEY_REMOTES, mapRemotes);
			}

			boolean isNew = !mapRemotes.containsKey(rp.getID());
			mapRemotes.put(rp.getID(), rp.getAsMap(true));

			savePrefs(mapConfig);

			if (isNew) {
				VuzeEasyTracker.getInstance().sendEvent("Profile", "Created",
						rp.getRemoteType() == RemoteProfile.TYPE_LOOKUP ? "Vuze"
								: rp.isLocalHost() ? "Local" : "Transmission",
						null);
			}

		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			VuzeEasyTracker.getInstance().logError(t);
		}

	}

	public void setLastRemote(RemoteProfile remoteProfile) {
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			Map<String, Object> mapConfig = config == null
					? new HashMap<String, Object>() : JSONUtils.decodeJSON(config);

			if (mapConfig == null) {
				mapConfig = new HashMap<>();
			}

			if (remoteProfile == null) {
				mapConfig.remove(KEY_LASTUSED);
			} else {
				mapConfig.put(KEY_LASTUSED, remoteProfile.getID());
			}

			Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
			if (mapRemotes == null) {
				mapRemotes = new HashMap();
				mapConfig.put(KEY_REMOTES, mapRemotes);
			}

			savePrefs(mapConfig);

		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			VuzeEasyTracker.getInstance().logError(t);
		}

	}

	private void savePrefs(Map mapConfig) {
		Editor edit = preferences.edit();
		edit.putString(KEY_CONFIG, JSONUtils.encodeToJSON(mapConfig));
		edit.commit();
		if (AndroidUtils.DEBUG) {
			try {
				Log.d(TAG, "Saved Preferences: "
						+ new org.json.JSONObject(mapConfig).toString(2));
			} catch (JSONException t) {
				t.printStackTrace();
			}
		}

	}

	public void removeRemoteProfile(String profileID) {
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			Map<String, Object> mapConfig = config == null
					? new HashMap<String, Object>() : JSONUtils.decodeJSON(config);

			if (mapConfig == null) {
				return;
			}

			Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
			if (mapRemotes == null) {
				return;
			}

			Object mapRemote = mapRemotes.remove(profileID);

			savePrefs(mapConfig);

			if (mapRemote != null) {
				if (mapRemote instanceof Map) {
					RemoteProfile rp = new RemoteProfile((Map) mapRemote);
					VuzeEasyTracker.getInstance().sendEvent("Profile", "Removed",
							rp.getRemoteType() == RemoteProfile.TYPE_LOOKUP ? "Vuze"
									: "Transmission",
							null);
				} else {
					VuzeEasyTracker.getInstance().sendEvent("Profile", "Removed", null,
							null);
				}
			}

		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			VuzeEasyTracker.getInstance().logError(t);
		}
	}

	public SharedPreferences getSharedPreferences() {
		return preferences;
	}

	public long getFirstInstalledOn() {
		try {
			String packageName = context.getPackageName();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
				return getFistInstalledOn_GB(packageName);
			} else {
				long firstInstallTIme = preferences.getLong("firstInstallTime", 0);
				if (firstInstallTIme == 0) {
					ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
							packageName, 0);
					String sAppFile = appInfo.sourceDir;
					firstInstallTIme = new File(sAppFile).lastModified();
					Editor edit = preferences.edit();
					edit.putLong("firstInstallTime", firstInstallTIme);
					edit.commit();
				}
				return firstInstallTIme;
			}
		} catch (Exception e) {
		}
		return System.currentTimeMillis();
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private long getFistInstalledOn_GB(String packageName)
			throws NameNotFoundException {
		PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
				packageName, 0);
		return packageInfo.firstInstallTime;
	}

	public long getLastUpdatedOn() {
		try {
			String packageName = context.getPackageName();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
				return getLastUpdatedOn_GB(packageName);
			} else {
				ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
						packageName, 0);
				String sAppFile = appInfo.sourceDir;
				return new File(sAppFile).lastModified();
			}
		} catch (Exception e) {
		}
		return System.currentTimeMillis();
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private long getLastUpdatedOn_GB(String packageName)
			throws NameNotFoundException {
		PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
				packageName, 0);
		return packageInfo.lastUpdateTime;
	}

	public long getNumOpens() {
		return preferences.getLong("numAppOpens", 0);
	}

	public void setNumOpens(long num) {
		Editor edit = preferences.edit();
		edit.putLong("numAppOpens", num);
		edit.commit();
	}

	public void setAskedRating() {
		Editor edit = preferences.edit();
		edit.putLong("askedRatingOn", System.currentTimeMillis());
		edit.commit();
	}

	public long getAskedRatingOn() {
		return preferences.getLong("askedRatingOn", 0);
	}

	public void setNeverAskRatingAgain() {
		Editor edit = preferences.edit();
		edit.putBoolean("neverAskRatingAgain", true);
		edit.commit();
	}

	public boolean getNeverAskRatingAgain() {
		return BuildConfig.FLAVOR.equals("fossFlavor") ? true
				: preferences.getBoolean("neverAskRatingAgain", false);
	}

	public boolean shouldShowRatingReminder() {
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
								VuzeEasyTracker.getInstance(mContext).sendEvent("uiAction",
										"Rating", "AskStoreClick", null);
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

		VuzeEasyTracker.getInstance(mContext).sendEvent("uiAction", "Rating",
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
				importPrefs((Activity) activity, uri);
			}
		}, new Runnable() {
			@Override
			public void run() {
				Toast.makeText(activity, R.string.content_read_failed_perms_denied,
						Toast.LENGTH_LONG).show();
			}
		});
	}

	private static boolean importPrefs(Activity activity, Uri uri) {
		if (uri == null) {
			return false;
		}
		String scheme = uri.getScheme();
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onActivityResult: scheme=" + scheme);
		}
		if ("file".equals(scheme) || "content".equals(scheme)) {
			try {
				InputStream stream = null;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
					String realPath = PaulBurkeFileUtils.getPath(activity, uri);
					if (realPath != null) {
						String meh = realPath.startsWith("/") ? "file://" + realPath
								: realPath;
						stream = activity.getContentResolver().openInputStream(
								Uri.parse(meh));
					}
				}
				if (stream == null) {
					ContentResolver contentResolver = activity.getContentResolver();
					stream = contentResolver.openInputStream(uri);
				}
				String s = new String(AndroidUtils.readInputStreamAsByteArray(stream));

				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onActivityResult: read " + s);
				}
				stream.close();

				Map<String, Object> map = JSONUtils.decodeJSON(s);

				VuzeRemoteApp.getAppPreferences().replacePreferenced(map);

				return true;

			} catch (FileNotFoundException e) {
				if (AndroidUtils.DEBUG) {
					e.printStackTrace();
				}
				Toast.makeText(activity, Html.fromHtml("<b>" + uri + "</b> not found"),
						Toast.LENGTH_LONG).show();
			} catch (IOException e) {
				e.printStackTrace();
				AndroidUtils.showDialog(activity, "Error Loading Config",
						uri.toString() + " could not be loaded. " + e.toString());
			} catch (Exception e) {
				e.printStackTrace();
				AndroidUtils.showDialog(activity, "Error Loading Config",
						uri.toString() + " could not be parsed. " + e.toString());
			}
		} else {
			AndroidUtils.showDialog(activity, "Error Loading Config",
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
				Toast.makeText(activity, R.string.content_saved_failed_perms_denied,
						Toast.LENGTH_LONG).show();
			}
		});
	}

	private static void exportPrefs(final AppCompatActivity activity) {
		new Thread(new Runnable() {
			String failText = null;

			@Override
			public void run() {
				String c = VuzeRemoteApp.getAppPreferences().getSharedPreferences().getString(
						"config", "");
				final File directory = AndroidUtils.getDownloadDir();
				final File outFile = new File(directory, "VuzeRemoteSettings.json");

				try {
					BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
					writer.write(c);
					writer.close();
				} catch (Exception e) {
					VuzeEasyTracker.getInstance().logError(e);
					if (AndroidUtils.DEBUG) {
						Log.e(TAG, "exportPrefs", e);
					}
					failText = e.getMessage();
				}
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						String s;
						if (failText == null) {
							s = activity.getResources().getString(R.string.content_saved,
									TextUtils.htmlEncode(outFile.getName()),
									TextUtils.htmlEncode(outFile.getParent()));
						} else {
							s = activity.getResources().getString(
									R.string.content_saved_failed,
									TextUtils.htmlEncode(outFile.getName()),
									TextUtils.htmlEncode(outFile.getParent()),
									TextUtils.htmlEncode(failText));
						}
						Toast.makeText(activity, Html.fromHtml(s),
								Toast.LENGTH_LONG).show();
					}
				});
			}
		}).start();
	}

	public void replacePreferenced(Map<String, Object> map) {
		if (map == null || map.size() == 0) {
			return;
		}

		savePrefs(map);

		VuzeEasyTracker.getInstance().sendEvent("Profile", "Import", null, null);
	}
}
