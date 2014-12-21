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

import android.content.Context;

import com.aelitis.azureus.util.MapUtils;

@SuppressWarnings({
	"rawtypes",
	"unchecked"
})
public class RemoteProfile
{
	private static final String ID_UPDATE_INTERVAL_ENABLED = "updateIntervalEnabled";

	private static final String ID_UPDATE_INTERVAL_MOBILE_SEPARATE = "updateIntervalMobileSeparate";

	private static final String ID_UPDATE_INTERVAL_MOBILE_ENABLED = "updateIntervalMobileEnabled";
	
	private static final String ID_FILTER_BY = "filterBy";

	private static final String ID_SORT_BY = "sortBy";

	private static final String ID_SORT_ORDER = "sortOrder";

	private static final String ID_SORT = "sort";

	private static final String ID_NICK = "nick";

	private static final String ID_PORT = "port";

	private static final String ID_HOST = "host";
	
	private static final String ID_PROTOCOL = "protocol";

	private static final String ID_LAST_USED = "lastUsed";

	private static final String ID_ID = "id";

	private static final String ID_AC = "ac";

	private static final String ID_USER = "user";

	private static final String ID_UPDATEINTERVAL = "updateInterval";

	private static final String ID_UPDATEINTERVAL_MOBILE = "updateIntervalMobile";

	private static final String ID_SAVE_PATH_HISTORY = "savePathHistory";

	/** Map of Key = Hash; Value = AddedOn **/
	private static final String ID_OPEN_OPTION_HASHES = "openOptionHashes";

	private static final String ID_ADD_TORRENT_SILENTLY = "showTorrentOpenOptions";

	private static final String ID_ADD_POSITION_LAST = "addPositionLast";

	private static final String ID_ADD_STATE_QUEUED = "addStateQueued";

	private static final String ID_DELETE_REMOVES_DATA = "deleteRemovesData";

	private static final boolean DEFAULT_ADD_POSITION_LAST = true;

	private static final boolean DEFAULT_ADD_STATE_QUEUED = true;

	private static final boolean DEFAULT_ADD_TORRENTS_SILENTLY = false;

	private static final boolean DEFAULT_DELETE_REMOVES_DATA = true;

	public static int TYPE_LOOKUP = 1;

	public static int TYPE_NORMAL = 2;

	private Map<String, Object> mapRemote;

	private int remoteType;

	public RemoteProfile(int remoteType) {
		mapRemote = new HashMap<String, Object>();
		this.remoteType = remoteType;
		mapRemote.put(ID_ID,
				Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE)));
	}

	public RemoteProfile(Map mapRemote) {
		if (mapRemote == null) {
			mapRemote = new HashMap<String, Object>();
		}
		this.mapRemote = mapRemote;
		remoteType = getHost().length() > 0 ? TYPE_NORMAL : TYPE_LOOKUP;
	}

	public RemoteProfile(String user, String ac) {
		mapRemote = new HashMap<String, Object>();
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

	public Map<String, Object> getAsMap(boolean forSaving) {
		if (forSaving && remoteType == TYPE_LOOKUP) {
			Map<String, Object> map = new HashMap<String, Object>(mapRemote);
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
	

	public void setProtocol(String protocol) {
		mapRemote.put(ID_PROTOCOL, protocol);
	}

	public String getProtocol() {
		return MapUtils.getMapString(mapRemote, ID_PROTOCOL, "http").trim();
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

	public boolean isUpdateIntervalMobileSeparate() {
		return MapUtils.getMapBoolean(mapRemote, ID_UPDATE_INTERVAL_MOBILE_SEPARATE, false);
	}

	public void setUpdateIntervalEnabledSeparate(boolean separate) {
		mapRemote.put(ID_UPDATE_INTERVAL_MOBILE_SEPARATE, separate);
	}

	public boolean isUpdateIntervalMobileEnabled() {
		return MapUtils.getMapBoolean(mapRemote, ID_UPDATE_INTERVAL_MOBILE_ENABLED, true);
	}

	public void setUpdateIntervalMobileEnabled(boolean enabled) {
		mapRemote.put(ID_UPDATE_INTERVAL_MOBILE_ENABLED, enabled);
	}

	/**
	 * @return current update interval based on network connection. 
	 * 0 for manual refresh.
	 * < 0 for refresh impossible (not online)
	 */
	public long calcUpdateInterval(Context context) {
		if (isLocalHost()) {
			if (isUpdateIntervalEnabled()) {
				return getUpdateInterval();
			}
			return -1;
		}
		NetworkState networkState = VuzeRemoteApp.getNetworkState();
		if (isUpdateIntervalMobileSeparate() && networkState.isOnlineMobile()) {
			if (isUpdateIntervalMobileEnabled()) {
				return getUpdateIntervalMobile();
			}
			return 0;
		} else if (networkState.isOnline()) {
			if (isUpdateIntervalEnabled()) {
				return getUpdateInterval();
			}
			return 0;
		}
		return -1;
	}
	
	public long getUpdateInterval() {
		return MapUtils.getMapInt(mapRemote, ID_UPDATEINTERVAL, 30);
	}

	public void setUpdateInterval(long interval) {
		mapRemote.put(ID_UPDATEINTERVAL, interval);
	}

	public long getUpdateIntervalMobile() {
		return MapUtils.getMapInt(mapRemote, ID_UPDATEINTERVAL_MOBILE, 30);
	}

	public void setUpdateIntervalMobile(long interval) {
		mapRemote.put(ID_UPDATEINTERVAL_MOBILE, interval);
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
		return new ArrayList<String>(mapOpenOptionHashes.keySet());
	}

	public void cleanupOpenOptionsWaiterList() {
		Map<String, Long> mapOpenOptionHashes = MapUtils.getMapMap(mapRemote,
				ID_OPEN_OPTION_HASHES, null);
		if (mapOpenOptionHashes == null) {
			return;
		}
		long tooOld = System.currentTimeMillis() - (1000l * 3600 * 2); // 2 hours

		for (Iterator<Long> it = mapOpenOptionHashes.values().iterator(); it.hasNext();) {
			Long since = it.next();
			if (since < tooOld) {
				it.remove();
			}
		}
	}

	public boolean isAddTorrentSilently() {
		return MapUtils.getMapBoolean(mapRemote, ID_ADD_TORRENT_SILENTLY,
				DEFAULT_ADD_TORRENTS_SILENTLY);
	}

	public void setAddTorrentSilently(boolean silent) {
		if (silent == DEFAULT_ADD_TORRENTS_SILENTLY) {
			mapRemote.remove(ID_ADD_TORRENT_SILENTLY);
		} else {
			mapRemote.put(ID_ADD_TORRENT_SILENTLY, silent);
		}
	}

	public boolean isAddPositionLast() {
		return MapUtils.getMapBoolean(mapRemote, ID_ADD_POSITION_LAST,
				DEFAULT_ADD_POSITION_LAST);
	}

	public void setAddPositionLast(boolean last) {
		if (last == DEFAULT_ADD_POSITION_LAST) {
			mapRemote.remove(ID_ADD_POSITION_LAST);
		} else {
			mapRemote.put(ID_ADD_POSITION_LAST, last);
		}
	}

	public boolean isAddStateQueued() {
		return MapUtils.getMapBoolean(mapRemote, ID_ADD_STATE_QUEUED,
				DEFAULT_ADD_STATE_QUEUED);
	}

	public void setAddStateQueued(boolean queued) {
		if (queued == DEFAULT_ADD_STATE_QUEUED) {
			mapRemote.remove(ID_ADD_STATE_QUEUED);
		} else {
			mapRemote.put(ID_ADD_STATE_QUEUED, queued);
		}
	}
	
	public boolean isDeleteRemovesData() {
		return MapUtils.getMapBoolean(mapRemote, ID_DELETE_REMOVES_DATA,
				DEFAULT_DELETE_REMOVES_DATA);
	}

	public void setDeleteRemovesData(boolean removesData) {
		if (removesData == DEFAULT_DELETE_REMOVES_DATA) {
			mapRemote.remove(ID_DELETE_REMOVES_DATA);
		} else {
			mapRemote.put(ID_ADD_STATE_QUEUED, removesData);
		}
	}
}
