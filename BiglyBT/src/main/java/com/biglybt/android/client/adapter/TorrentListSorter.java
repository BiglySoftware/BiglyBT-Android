/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.adapter;

import java.util.List;
import java.util.Map;

import com.biglybt.android.adapter.GroupedSortDefinition;
import com.biglybt.android.adapter.SortDefinition;
import com.biglybt.android.client.SessionGetter;
import com.biglybt.android.client.TransmissionVars;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.Session_Tag;
import com.biglybt.android.util.MapUtils;

/**
 * Created by TuxPaper on 8/6/18.
 */
public class TorrentListSorter
	extends ComparatorMapFieldsErr<TorrentListAdapterItem>
{
	static final String SORTDEFINITION_ACTIVESORT = "ActiveSort";

	private final SessionGetter sessionGetter;

	private Long tagUID_Active;

	TorrentListSorter(SessionGetter sessionGetter, SortDefinition sortDefinition,
			boolean isAsc) {
		super(sortDefinition, isAsc);
		this.sessionGetter = sessionGetter;
	}

	public GroupedSortDefinition<TorrentListAdapterItem, Integer> getGroupedSortDefinition() {
		SortDefinition sortDefinition = getSortDefinition();
		if (sortDefinition instanceof GroupedSortDefinition) {
			//noinspection unchecked
			return (GroupedSortDefinition<TorrentListAdapterItem, Integer>) sortDefinition;
		}
		return null;
	}

	@Override
	public Map<?, ?> mapGetter(TorrentListAdapterItem o) {
		Session session = sessionGetter.getSession();
		if (session != null && (o instanceof TorrentListAdapterTorrentItem)) {
			return ((TorrentListAdapterTorrentItem) o).getTorrentMap(session);
		}
		return null;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Comparable modifyField(String fieldID, Map map, Comparable o) {
		if (fieldID.equals(SORTDEFINITION_ACTIVESORT)) {
			boolean active;
			List<?> listTagUIDs = MapUtils.getMapList(map,
					TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
			Session session = sessionGetter.getSession();
			if (listTagUIDs != null && tagUID_Active == null && session != null) {
				tagUID_Active = session.tag.getDownloadStateUID(
						Session_Tag.STATEID_ACTIVE);
			}
			if (listTagUIDs != null && tagUID_Active != null) {
				active = listTagUIDs.contains(tagUID_Active);
			} else {
				long rateDL = MapUtils.getMapLong(map,
						TransmissionVars.FIELD_TORRENT_RATE_DOWNLOAD, 0);
				long rateUL = MapUtils.getMapLong(map,
						TransmissionVars.FIELD_TORRENT_RATE_UPLOAD, 0);
				active = rateDL > 0 && rateUL > 0;
			}
			return active;
		}
		if (fieldID.equals(TransmissionVars.FIELD_TORRENT_ETA)) {
			if (((Number) o).longValue() < 0) {
				o = Long.MAX_VALUE;
			}
		}
		return o;
	}
}
