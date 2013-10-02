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
	private Map mapRemote;

	public RemoteProfile(Map mapRemote) {
		this.mapRemote = mapRemote;
	}
	
	public RemoteProfile(String user, String ac) {
		mapRemote = new HashMap();
		mapRemote.put("user",user);
		mapRemote.put("ac", ac);
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
		if (nick == null && !mapRemote.containsKey("host")) {
			nick = getAC();
		}
		return nick;
	}
	
	public long getLastUsedOn() {
		return MapUtils.getMapLong(mapRemote, "lastUsed", 0);
	}
	
	public void setLastUsedOn(long t) {
		mapRemote.put("lastUsed", t);
	}
	
	public Map getAsMap() {
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
	
}
