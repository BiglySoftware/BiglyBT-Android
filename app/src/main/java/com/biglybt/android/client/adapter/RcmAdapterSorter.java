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

import androidx.annotation.NonNull;

import java.util.Map;

import com.biglybt.android.adapter.SortDefinition;

/**
 * Created by TuxPaper on 9/21/18.
 */
class RcmAdapterSorter
	extends ComparatorMapFieldsErr<String>
{

	@NonNull
	private final RcmAdapter.RcmSelectionListener rs;

	RcmAdapterSorter(@NonNull RcmAdapter.RcmSelectionListener rs,
			SortDefinition sortDefinition, boolean isAsc) {
		super(sortDefinition, isAsc);
		this.rs = rs;
	}

	@Override
	public Map<?, ?> mapGetter(String o) {
		return rs.getSearchResultMap(o);
	}
}
