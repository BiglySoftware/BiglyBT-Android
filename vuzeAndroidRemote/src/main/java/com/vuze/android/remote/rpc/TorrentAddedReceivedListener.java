package com.vuze.android.remote.rpc;

import java.util.Map;

public interface TorrentAddedReceivedListener
{
	public void torrentAdded(Map<?, ?> mapTorrentAdded, boolean duplicate);

	// TODO: pass original URL or name or something
	public void torrentAddFailed(String message);

	// TODO: pass original URL or name or something
	public void torrentAddError(Exception e);

}
