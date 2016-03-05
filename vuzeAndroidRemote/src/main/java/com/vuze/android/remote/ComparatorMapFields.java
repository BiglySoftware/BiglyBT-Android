/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.vuze.android.remote;

import java.util.Comparator;
import java.util.Map;

public abstract class ComparatorMapFields
	implements Comparator<Object>
{
	private String[] sortFieldIDs;

	private Boolean[] sortOrderAsc;

	private Comparator comparator;

	public ComparatorMapFields(String[] sortFieldIDs, Boolean[] sortOrderAsc) {
		this.sortOrderAsc = sortOrderAsc;
		this.sortFieldIDs = sortFieldIDs;
	}

	public ComparatorMapFields(String[] sortFieldIDs, Boolean[] sortOrderAsc,
			Comparator<?> comparator) {
		this.sortOrderAsc = sortOrderAsc;
		this.sortFieldIDs = sortFieldIDs;
		this.comparator = comparator;
	}

	public ComparatorMapFields(Comparator comparator) {
		this.comparator = comparator;
	}

	public abstract Map<?, ?> mapGetter(Object o);

	public abstract int reportError(Comparable<?> oLHS, Comparable<?> oRHS,
			Throwable t);

	@SuppressWarnings({
		"unchecked",
		"rawtypes"
	})
	@Override
	public int compare(Object lhs, Object rhs) {
		Map<?, ?> mapLHS = mapGetter(lhs);
		Map<?, ?> mapRHS = mapGetter(rhs);

		if (mapLHS == null || mapRHS == null) {
			return 0;
		}

		if (sortFieldIDs == null) {
			return comparator.compare(mapLHS, mapRHS);
		} else {
			for (int i = 0; i < sortFieldIDs.length; i++) {
				String fieldID = sortFieldIDs[i];
				Comparable oLHS = (Comparable) mapLHS.get(fieldID);
				Comparable oRHS = (Comparable) mapRHS.get(fieldID);
				if (oLHS == null || oRHS == null) {
					if (oLHS != oRHS) {
						return oLHS == null ? -1 : 1;
					} // else == drops to next sort field

				} else {
					int comp;
					
					oLHS = modifyField(fieldID, mapLHS, oLHS);
					oRHS = modifyField(fieldID, mapRHS, oRHS);
					
					if ((oLHS instanceof String) && (oLHS instanceof String)) {
						comp = sortOrderAsc[i]
								? ((String) oLHS).compareToIgnoreCase((String) oRHS)
								: ((String) oRHS).compareToIgnoreCase((String) oLHS);
					} else {
						try {
							comp = sortOrderAsc[i] ? oLHS.compareTo(oRHS)
									: oRHS.compareTo(oLHS);
						} catch (Throwable t) {
							comp = reportError(oLHS, oRHS, t);
						}
					}
					if (comp != 0) {
						return comp;
					} // else == drops to next sort field
				}
			}

			return 0;
		}
	}

	public Comparable modifyField(String fieldID, Map<?, ?> map, Comparable o) {
		return o;
	}
}