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

import java.util.*;

import com.aelitis.azureus.util.MapUtils;

@SuppressWarnings({
	"rawtypes",
	"unchecked"
})
public class RemoteProfile
{
	private static final String ID_UPDATE_INTERVAL_ENABLED = "updateIntervalEnabled";

	private static final String ID_FILTER_BY = "filterBy";

	private static final String ID_SORT_BY = "sortBy";

	private static final String ID_SORT_ORDER = "sortOrder";

	private static final String ID_SORT = "sort";

	private static final String ID_NICK = "nick";

	private static final String ID_PORT = "port";

	private static final String ID_HOST = "host";

	private static final String ID_LAST_USED = "lastUsed";

	private static final String ID_ID = "id";

	private static final String ID_AC = "ac";

	private static final String ID_USER = "user";

	private static final String ID_UPDATEINTERVAL = "updateInterval";

	private static final String ID_SAVE_PATH_HISTORY = "savePathHistory";

	/** Map of Key = Hash; Value = AddedOn **/
	private static final String ID_OPEN_OPTION_HASHES = "openOptionHashes";
	
	private static final String ID_ADD_TORRENT_SILENTLY = "showTorrentOpenOptions";

	public static int TYPE_LOOKUP = 1;

	public static int TYPE_NORMAL = 2;

	private Map mapRemote;

	private int remoteType;

	public RemoteProfile(int remoteType) {
		mapRemote = new HashMap();
		this.remoteType = remoteType;
		mapRemote.put(ID_ID,
				Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE)));
	}

	public RemoteProfile(Map mapRemote) {
		if (mapRemote == null) {
			mapRemote = new HashMap();
		}
		this.mapRemote = mapRemote;
		remoteType = getHost().length() > 0 ? TYPE_NORMAL : TYPE_LOOKUP;
	}

	public RemoteProfile(String user, String ac) {
		mapRemote = new HashMap();
		mapRemote.put(ID_USER, user);
		mapRemote.put(ID_AC, ac);
		mapRemote.put(ID_ID, ac);
		remoteType = TYPE_LOOKUP;
	}

	public String getID() {
		return MapUtils.getMapString(mapRemote, ID_ID, getAC());
	}

	public String getAC() {
		return MapUtils.getMapString(mapRemote, ID_AC, "");
	}

	public void setAC(String ac) {
		mapRemote.put(ID_AC, ac);
	}

	public String getUser() {
		String user = (String) mapRemote.get(ID_USER);
		return user;
	}

	public String getNick() {
		String nick = MapUtils.getMapString(mapRemote, ID_NICK, null);
		String ac = getAC();
		if (nick == null || nick.equals(ac)) {
			if (getRemoteType() == TYPE_LOOKUP) {
				if (ac.length() > 1) {
					nick = "Remote " + getAC().substring(0, 2);
				} else {
					nick = "Remote";
				}
			} else {
				nick = MapUtils.getMapString(mapRemote, ID_HOST, "Remote");
			}
		}
		return nick;
	}

	public long getLastUsedOn() {
		return MapUtils.getMapLong(mapRemote, ID_LAST_USED, 0);
	}

	public void setLastUsedOn(long t) {
		mapRemote.put(ID_LAST_USED, t);
	}

	public Map getAsMap(boolean forSaving) {
		if (forSaving && remoteType == TYPE_LOOKUP) {
			Map map = new HashMap(mapRemote);
			map.remove(ID_HOST);
			map.remove(ID_PORT);
			return map;
		}
		return mapRemote;
	}

	public String getHost() {
		return MapUtils.getMapString(mapRemote, ID_HOST, "").trim();
	}

	public int getPort() {
		return MapUtils.getMapInt(mapRemote, ID_PORT, 9091);
	}

	public void setNick(String nick) {
		mapRemote.put(ID_NICK, nick);
	}

	public void setPort(int port) {
		mapRemote.put(ID_PORT, port);
	}

	public void setHost(String host) {
		mapRemote.put(ID_HOST, host);
	}

	public boolean isLocalHost() {
		return "localhost".equals(getHost());
	}

	public int getRemoteType() {
		return remoteType;
	}

	public String[] getSortBy() {
		Map mapSort = MapUtils.getMapMap(mapRemote, ID_SORT, null);
		if (mapSort != null) {
			List mapList = MapUtils.getMapList(mapSort, ID_SORT_BY, null);
			if (mapList != null) {
				return (String[]) mapList.toArray(new String[0]);
			}
		}
		return new String[] {
			"name"
		};
	}

	public Boolean[] getSortOrder() {
		Map mapSort = MapUtils.getMapMap(mapRemote, ID_SORT, null);
		if (mapSort != null) {
			List mapList = MapUtils.getMapList(mapSort, ID_SORT_ORDER, null);
			if (mapList != null) {
				return (Boolean[]) mapList.toArray(new Boolean[0]);
			}
		}
		return new Boolean[] {
			true
		};
	}

	public void setSortBy(String[] sortBy, Boolean[] sortOrderAsc) {
		Map mapSort = MapUtils.getMapMap(mapRemote, ID_SORT, null);
		if (mapSort == null) {
			mapSort = new HashMap();
			mapRemote.put(ID_SORT, mapSort);
		}
		mapSort.put(ID_SORT_BY, sortBy);
		mapSort.put(ID_SORT_ORDER, sortOrderAsc);
	}

	public long getFilterBy() {
		return MapUtils.getMapLong(mapRemote, ID_FILTER_BY, -1);
	}

	public void setFilterBy(long filterBy) {
		mapRemote.put(ID_FILTER_BY, filterBy);
	}

	public boolean isUpdateIntervalEnabled() {
		return MapUtils.getMapBoolean(mapRemote, ID_UPDATE_INTERVAL_ENABLED, true);
	}

	public void setUpdateIntervalEnabled(boolean enabled) {
		mapRemote.put(ID_UPDATE_INTERVAL_ENABLED, enabled);
	}

	public long getUpdateInterval() {
		return MapUtils.getMapInt(mapRemote, ID_UPDATEINTERVAL, 30);
	}

	public void setUpdateInterval(long interval) {
		mapRemote.put(ID_UPDATEINTERVAL, interval);
	}

	public List<String> getSavePathHistory() {
		return MapUtils.getMapList(mapRemote, ID_SAVE_PATH_HISTORY,
				new ArrayList<String>());
	}

	public void setSavePathHistory(List<String> history) {
		mapRemote.put(ID_SAVE_PATH_HISTORY, history);
	}

	public void setUser(String user) {
		mapRemote.put(ID_USER, user);
	}

	public void addOpenOptionsWaiter(String hashString) {
		Map mapOpenOptionHashes = MapUtils.getMapMap(mapRemote,
				ID_OPEN_OPTION_HASHES, null);
		if (mapOpenOptionHashes == null) {
			mapOpenOptionHashes = new HashMap<>();
			mapRemote.put(ID_OPEN_OPTION_HASHES, mapOpenOptionHashes);
		}
		mapOpenOptionHashes.put(hashString, System.currentTimeMillis());
	}

	public void removeOpenOptionsWaiter(String hashString) {
		Map mapOpenOptionHashes = MapUtils.getMapMap(mapRemote,
				ID_OPEN_OPTION_HASHES, null);
		if (mapOpenOptionHashes == null) {
			return;
		}
		mapOpenOptionHashes.remove(hashString);
	}

	public List<String> getOpenOptionsWaiterList() {
		Map mapOpenOptionHashes = MapUtils.getMapMap(mapRemote,
				ID_OPEN_OPTION_HASHES, null);
		if (mapOpenOptionHashes == null) {
			return Collections.emptyList();
		}
		return new ArrayList(mapOpenOptionHashes.keySet());
	}

	public void cleanupOpenOptionsWaiterList() {
		Map<String, Long> mapOpenOptionHashes = MapUtils.getMapMap(mapRemote,
				ID_OPEN_OPTION_HASHES, null);
		if (mapOpenOptionHashes == null) {
			return;
		}
		long tooOld = System.currentTimeMillis() - (1000l * 3600 * 2); // 2 hours
		for (String key : mapOpenOptionHashes.keySet()) {
			Long since = mapOpenOptionHashes.get(key);
			if (since < tooOld) {
				mapOpenOptionHashes.remove(key);
			}
		}
	}
	
	public boolean isAddTorrentSilently() {
		return MapUtils.getMapBoolean(mapRemote, ID_ADD_TORRENT_SILENTLY, false);
	}
	
	public void setAddTorrentSilently(boolean silent) {
		mapRemote.put(ID_ADD_TORRENT_SILENTLY, silent);
	}
}
