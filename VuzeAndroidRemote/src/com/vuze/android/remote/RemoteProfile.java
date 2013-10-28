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

	private static final String ID_NICK = "nick";

	private static final String ID_PORT = "port";

	private static final String ID_HOST = "host";

	private static final String ID_LAST_USED = "lastUsed";

	private static final String ID_ID = "id";

	private static final String ID_AC = "ac";

	private static final String ID_USER = "user";

	private static final String ID_UPDATEINTERVAL = "updateInterval";

	private static final String ID_SAVE_PATH_HISTORY = "savePathHistory";

	public static int TYPE_LOOKUP = 1;

	public static int TYPE_NORMAL = 2;

	private Map mapRemote;

	private int remoteType;

	public RemoteProfile(Map mapRemote) {
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
		return (String) mapRemote.get(ID_AC);
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
		return MapUtils.getMapString(mapRemote, ID_HOST, "");
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

	public int getRemoteType() {
		return remoteType;
	}

	public String getSortBy() {
		return MapUtils.getMapString(mapRemote, ID_SORT_BY, null);
	}

	public void setSortBy(String sortBy) {
		mapRemote.put(ID_SORT_BY, sortBy);
	}

	public String getFilterBy() {
		return MapUtils.getMapString(mapRemote, ID_FILTER_BY, null);
	}

	public void setFilterBy(String filterBy) {
		mapRemote.put(ID_FILTER_BY, filterBy);
	}

	public boolean isUpdateIntervalEnabled() {
		return MapUtils.getMapBoolean(mapRemote, ID_UPDATE_INTERVAL_ENABLED, true);
	}

	public void setUpdateIntervalEnabled(boolean enabled) {
		mapRemote.put(ID_UPDATE_INTERVAL_ENABLED, enabled);
	}

	public long getUpdateInterval() {
		return MapUtils.getMapInt(mapRemote, ID_UPDATEINTERVAL, -1);
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
}
