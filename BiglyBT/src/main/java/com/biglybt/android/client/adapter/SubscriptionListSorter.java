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

import java.util.Map;

import com.biglybt.android.adapter.SortDefinition;
import com.biglybt.android.client.TransmissionVars;

/**
 * Created by TuxPaper on 9/22/18.
 */
class SubscriptionListSorter
	extends ComparatorMapFieldsErr<String>
{

	private final SubscriptionListAdapter.SubscriptionSelectionListener rs;

	SubscriptionListSorter(SortDefinition sortDefinition, boolean isAsc,
			SubscriptionListAdapter.SubscriptionSelectionListener rs) {
		super(sortDefinition, isAsc);
		this.rs = rs;
	}

	@Override
	public Comparable modifyField(String fieldID, Map<?, ?> map, Comparable o) {
		if (fieldID.equals(
				TransmissionVars.FIELD_SUBSCRIPTION_ENGINE_LASTUPDATED)) {
			Map mapEngine = (Map) map.get(TransmissionVars.FIELD_SUBSCRIPTION_ENGINE);
			if (mapEngine == null) {
				return 0;
			}
			return (Comparable) mapEngine.get(fieldID);
		}
		return o;
	}

	@Override
	public Map<?, ?> mapGetter(String o) {
		return rs.getSubscriptionMap(o);
	}
}
