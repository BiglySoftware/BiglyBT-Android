/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
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
 */

package com.biglybt.android.client.adapter;

import java.util.Map;

import com.biglybt.android.client.session.Session;

import android.support.annotation.NonNull;

/**
 * Created by TuxPaper on 3/10/17.
 */

public class TorrentListAdapterTorrentItem
	extends TorrentListAdapterItem
{
	final Long torrentID;

	public TorrentListAdapterTorrentItem(Long torrentID) {
		this.torrentID = torrentID;
	}

	public Map<?, ?> getTorrentMap(@NonNull Session session) {
		return session.torrent.getCachedTorrent(torrentID);
	}

	@Override
	public int compareTo(@NonNull TorrentListAdapterItem o) {
		if (o instanceof TorrentListAdapterTorrentItem) {
			return torrentID.compareTo(((TorrentListAdapterTorrentItem) o).torrentID);
		}
		return -1;
	}
}
