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

import java.io.File;
import java.util.*;

import org.json.JSONException;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.util.Log;

import com.aelitis.azureus.util.JSONUtils;
import com.aelitis.azureus.util.MapUtils;
import com.google.analytics.tracking.android.MapBuilder;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
@SuppressWarnings({
	"rawtypes",
	"unchecked"
})
public class AppPreferences
{

	private static final String KEY_REMOTES = "remotes";

	private static final String KEY_CONFIG = "config";

	private static final String KEY_LASTUSED = "lastUsed";

	private static final String TAG = "AppPrefs";

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
				Map mapConfig = JSONUtils.decodeJSON(config);

				String lastUsed = (String) mapConfig.get(KEY_LASTUSED);
				if (lastUsed != null) {
					Map mapRemotes = MapUtils.getMapMap(mapConfig, KEY_REMOTES, null);
					if (mapRemotes != null) {
						Map mapRemote = (Map) mapRemotes.get(lastUsed);
						if (mapRemote == null) {
							// backwards compat. KEY_LASTUSED used to be ac
							for (Object o : mapRemotes.values()) {
								if (o instanceof Map) {
									String ac = MapUtils.getMapString(mapRemote, "ac", null);
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
			VuzeEasyTracker.getInstance(context).logError(context, t);
		}

		return null;
	}

	public RemoteProfile getRemote(String profileID) {
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			if (config != null) {
				Map mapConfig = JSONUtils.decodeJSON(config);

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
			VuzeEasyTracker.getInstance(context).logError(context, t);
		}

		return null;
	}

	public int getNumRemotes() {
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			if (config != null) {
				Map mapConfig = JSONUtils.decodeJSON(config);

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
			VuzeEasyTracker.getInstance(context).logError(context, t);
		}

		return 0;
	}

	public RemoteProfile[] getRemotes() {
		List<RemoteProfile> listRemotes = new ArrayList<RemoteProfile>(1);
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			if (config != null) {
				Map mapConfig = JSONUtils.decodeJSON(config);

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
			VuzeEasyTracker.getInstance(context).logError(context, t);
		}

		return listRemotes.toArray(new RemoteProfile[0]);
	}

	public void addRemoteProfile(RemoteProfile rp) {
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			Map mapConfig = config == null ? new HashMap()
					: JSONUtils.decodeJSON(config);

			if (mapConfig == null) {
				mapConfig = new HashMap();
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
				VuzeEasyTracker.getInstance(context).send(
						MapBuilder.createEvent(
								"Profile",
								"Created",
								rp.getRemoteType() == RemoteProfile.TYPE_LOOKUP ? "Vuze"
										: rp.isLocalHost() ? "Local" : "Transmission", null).build());
			}

		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			VuzeEasyTracker.getInstance(context).logError(context, t);
		}

	}

	public void setLastRemote(RemoteProfile remoteProfile) {
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			Map mapConfig = config == null ? new HashMap()
					: JSONUtils.decodeJSON(config);

			if (mapConfig == null) {
				mapConfig = new HashMap();
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
			VuzeEasyTracker.getInstance(context).logError(context, t);
		}

	}

	private void savePrefs(Map mapConfig) {
		Editor edit = preferences.edit();
		edit.putString(KEY_CONFIG, JSONUtils.encodeToJSON(mapConfig));
		edit.commit();
		if (AndroidUtils.DEBUG) {
			try {
				Log.d(
						TAG,
						"Saved Preferences: "
								+ new org.json.JSONObject(mapConfig).toString(2));
			} catch (JSONException t) {
				t.printStackTrace();
			}
		}
		
	}

	public void removeRemoteProfile(String profileID) {
		try {
			String config = preferences.getString(KEY_CONFIG, null);
			Map mapConfig = config == null ? new HashMap()
					: JSONUtils.decodeJSON(config);

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
					VuzeEasyTracker.getInstance(context).send(
							MapBuilder.createEvent(
									"Profile",
									"Removed",
									rp.getRemoteType() == RemoteProfile.TYPE_LOOKUP ? "Vuze"
											: "Transmission", null).build());
				} else {
					VuzeEasyTracker.getInstance(context).send(
							MapBuilder.createEvent("Profile", "Removed", null, null).build());
				}
			}

		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				t.printStackTrace();
			}
			VuzeEasyTracker.getInstance(context).logError(context, t);
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
}
