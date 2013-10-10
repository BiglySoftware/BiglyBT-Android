package com.vuze.android.remote;

import java.util.HashMap;
import java.util.Map;

import com.aelitis.azureus.util.MapUtils;

@SuppressWarnings({
	"rawtypes",
	"unchecked"
})
public class RemoteProfile
{
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
		mapRemote.put("user",user);
		mapRemote.put("ac", ac);
		mapRemote.put("id", ac);
		remoteType = TYPE_LOOKUP;
	}
	
	public String getID() {
		return MapUtils.getMapString(mapRemote, "id", getAC());
	}

	public String getAC() {
		return (String) mapRemote.get("ac");
	}
	
	public String getUser() {
		String user = (String) mapRemote.get("user");
		return user;
	}
	
	public String getNick() {
		String nick = MapUtils.getMapString(mapRemote, "nick", null);
		String ac = getAC();
		if (nick == null || nick.equals(ac)) {
			if (getRemoteType() == TYPE_LOOKUP) {
				if (ac.length() > 1) {
					nick = "Remote " + getAC().substring(0, 2);
				} else {
					nick = "Remote";
				}
			} else {
				nick = MapUtils.getMapString(mapRemote, "host", "Remote");
			}
		}
		return nick;
	}
	
	public long getLastUsedOn() {
		return MapUtils.getMapLong(mapRemote, "lastUsed", 0);
	}
	
	public void setLastUsedOn(long t) {
		mapRemote.put("lastUsed", t);
	}
	
	public Map getAsMap(boolean forSaving) {
		if (forSaving && remoteType == TYPE_LOOKUP) {
			Map map = new HashMap(mapRemote);
			map.remove("host");
			map.remove("port");
			return map;
		}
		return mapRemote;
	}

	public String getHost() {
		return MapUtils.getMapString(mapRemote, "host", "");
	}

	public int getPort() {
		return MapUtils.getMapInt(mapRemote, "port", 9091);
	}

	public void setNick(String nick) {
		mapRemote.put("nick", nick);
	}

	public void setPort(int port) {
		mapRemote.put("port", port);
	}

	public void setHost(String host) {
		mapRemote.put("host", host);
	}

	public int getRemoteType() {
		return remoteType;
	}
	
}
