package com.vuze.android.remote;

import java.util.*;

import org.json.JSONException;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.aelitis.azureus.util.JSONUtils;

@SuppressWarnings({
	"rawtypes",
	"unchecked"
})
public class AppPreferences
{

	private SharedPreferences preferences;

	public AppPreferences(Context context) {
		preferences = context.getSharedPreferences("AndroidRemote",
				Activity.MODE_PRIVATE);
	}

	public RemotePreferences getLastUsedRemote() {
		try {
			String config = preferences.getString("config", null);
			if (config != null) {
				Map mapConfig = JSONUtils.decodeJSON(config);

				String lastUsed = (String) mapConfig.get("lastUsed");
				if (lastUsed != null) {
					Map mapRemotes = (Map) mapConfig.get("remotes");
					if (mapRemotes != null) {
						Map mapRemote = (Map) mapRemotes.get(lastUsed);
						if (mapRemote != null) {
							return new RemotePreferences(mapRemote);
						}
					}
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}

		return null;
	}

	public String getLastUsedRemoteID() {
		try {
			String config = preferences.getString("config", null);
			if (config != null) {
				Map mapConfig = JSONUtils.decodeJSON(config);

				return (String) mapConfig.get("lastUsed");
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}

		return null;
	}

	public RemotePreferences[] getRemotes() {
		List<RemotePreferences> listRemotes = new ArrayList<RemotePreferences>(1);
		try {
			String config = preferences.getString("config", null);
			if (config != null) {
				Map mapConfig = JSONUtils.decodeJSON(config);

				Map mapRemotes = (Map) mapConfig.get("remotes");
				if (mapRemotes != null) {
					for (Object val : mapRemotes.values()) {
						if (val instanceof Map) {
							listRemotes.add(new RemotePreferences((Map) val));
						}
					}
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}

		return listRemotes.toArray(new RemotePreferences[0]);
	}

	public void addRemotePref(RemotePreferences rp) {
		try {
			String config = preferences.getString("config", null);
			Map mapConfig = config != null ? new HashMap()
					: JSONUtils.decodeJSON(config);

			Map mapRemotes = (Map) mapConfig.get("remotes");
			if (mapRemotes == null) {
				mapRemotes = new HashMap();
				mapConfig.put("remotes", mapRemotes);
			}

			mapRemotes.put(rp.getAC(), rp.getAsMap());

			savePrefs(mapConfig);

		} catch (Throwable t) {
			t.printStackTrace();
		}

	}

	public void setLastRemote(String ac) {
		try {
			String config = preferences.getString("config", null);
			Map mapConfig = config == null ? new HashMap()
					: JSONUtils.decodeJSON(config);

			if (ac == null) {
				mapConfig.remove("lastUsed");
			} else {
				mapConfig.put("lastUsed", ac);
			}

			Map mapRemotes = (Map) mapConfig.get("remotes");
			if (mapRemotes == null) {
				mapRemotes = new HashMap();
			}

			savePrefs(mapConfig);

		} catch (Throwable t) {
			t.printStackTrace();
		}

	}

	public void savePrefs(Map mapConfig) {
		Editor edit = preferences.edit();
		edit.putString("config", JSONUtils.encodeToJSON(mapConfig));
		edit.commit();
		try {
			System.out.println("Saved Preferences: "
					+ new org.json.JSONObject(mapConfig).toString(2));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
