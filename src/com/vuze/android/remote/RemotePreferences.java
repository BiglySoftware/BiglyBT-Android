package com.vuze.android.remote;

import java.util.HashMap;
import java.util.Map;

import com.aelitis.azureus.util.MapUtils;

@SuppressWarnings({
	"rawtypes",
	"unchecked"
})
public class RemotePreferences
{
	private Map mapRemote;

	public RemotePreferences(Map mapRemote) {
		this.mapRemote = mapRemote;
	}
	
	public RemotePreferences(String user, String ac) {
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
	
	public String getName() {
		return getAC();
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
	
}
