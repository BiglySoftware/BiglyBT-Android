package com.vuze.android.remote.rpc;

import java.util.Map;

public interface TorrentAddedReceivedListener
{
	void torrentAdded(Map<?, ?> mapTorrentAdded, boolean duplicate);

	// TODO: pass original URL or name or something
	void torrentAddFailed(String message);

	// TODO: pass original URL or name or something
	void torrentAddError(Exception e);

}
