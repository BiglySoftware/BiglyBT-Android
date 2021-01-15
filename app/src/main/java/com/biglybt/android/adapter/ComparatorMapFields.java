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

package com.biglybt.android.adapter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import com.biglybt.android.client.AndroidUtils;

import android.util.Log;

public abstract class ComparatorMapFields<T>
	implements Comparator<T>
{
	private SortDefinition sortDefinition;

	private Comparator<? super Map<?, ?>> comparator;

	private boolean isAsc = true;

	public ComparatorMapFields() {
	}

	public ComparatorMapFields(SortDefinition sortDefinition, boolean isAsc) {
		this.sortDefinition = sortDefinition;
		this.isAsc = isAsc;
		if (sortDefinition != null) {
			sortDefinition.sortEventTriggered(SortDefinition.SORTEVENT_ACTIVATING);
		}
	}

	public boolean isAsc() {
		return isAsc;
	}

	public void setAsc(boolean asc) {
		if (sortDefinition == null || sortDefinition.allowSortDirection()) {
			this.isAsc = asc;
		}
	}

	public SortDefinition getSortDefinition() {
		return sortDefinition;
	}

	public void setSortFields(SortDefinition sortDefinition) {
		if (sortDefinition != this.sortDefinition) {
			if (this.sortDefinition != null) {
				this.sortDefinition.sortEventTriggered(
						SortDefinition.SORTEVENT_DEACTIVATING);
			}
			this.sortDefinition = sortDefinition;
			if (sortDefinition != null) {
				isAsc = sortDefinition.isSortAsc();
				sortDefinition.sortEventTriggered(SortDefinition.SORTEVENT_ACTIVATING);
			}
		}
		this.comparator = null;
	}

	public void setComparator(Comparator<? super Map<?, ?>> comparator) {
		this.comparator = comparator;
		sortDefinition = null;
	}

	public boolean isValid() {
		return comparator != null || sortDefinition != null;
	}

	public String toDebugString() {
		return Arrays.asList(sortDefinition.sortFieldIDs) + "/"
				+ Arrays.asList(sortDefinition.sortOrderNatural);
	}

	public abstract Map<?, ?> mapGetter(T o);

	@SuppressWarnings("UnusedParameters")
	public abstract int reportError(Comparable<?> oLHS, Comparable<?> oRHS,
			Throwable t);

	@SuppressWarnings({
		"unchecked",
		"rawtypes"
	})
	@Override
	public int compare(T lhs, T rhs) {
		if (lhs != null && lhs.equals(rhs)) {
			return 0;
		}
		Map<?, ?> mapLHS = mapGetter(lhs);
		Map<?, ?> mapRHS = mapGetter(rhs);

		if (mapLHS == null || mapRHS == null) {
			return 0;
		}

		if (sortDefinition == null) {
			if (comparator == null) {
				return 0;
			}
			return comparator.compare(mapLHS, mapRHS);
		} else {
			for (int i = 0; i < sortDefinition.sortFieldIDs.length; i++) {
				String fieldID = sortDefinition.sortFieldIDs[i];
				Comparable oLHS = (Comparable) mapLHS.get(fieldID);
				Comparable oRHS = (Comparable) mapRHS.get(fieldID);

				oLHS = modifyField(fieldID, mapLHS, oLHS);
				oRHS = modifyField(fieldID, mapRHS, oRHS);

				if (oLHS == null || oRHS == null) {
					if (oLHS != oRHS) {
						return oLHS == null ? -1 : 1;
					} // else == drops to next sort field

				} else {
					int comp;

					if ((oLHS instanceof String) && (oRHS instanceof String)) {
						comp = ((String) oLHS).compareToIgnoreCase((String) oRHS);
					} else if (oRHS instanceof Number && oLHS instanceof Number) {
						if (oRHS instanceof BigDecimal && oLHS instanceof BigDecimal) {
							comp = oLHS.compareTo(oRHS);
						} else if (oRHS instanceof Double || oLHS instanceof Double
								|| oRHS instanceof Float || oLHS instanceof Float
								|| oRHS instanceof BigDecimal || oLHS instanceof BigDecimal) {
							double dRHS = ((Number) oRHS).doubleValue();
							double dLHS = ((Number) oLHS).doubleValue();
							comp = Double.compare(dLHS, dRHS);
						} else {
							// convert to long so we can compare Integer and Long objects
							long lRHS = ((Number) oRHS).longValue();
							long lLHS = ((Number) oLHS).longValue();
							// Not available until API 19
							// comp = sortOrderAsc[i] ? Long.compare(lLHS, lRHS) :Long.compare(lRHS, lLHS);
							comp = lLHS > lRHS ? 1 : lLHS == lRHS ? 0 : -1;
						}
					} else {
						if (AndroidUtils.DEBUG) {
							if (!((oLHS instanceof Boolean) && (oRHS instanceof Boolean))) {
								Log.d("CMP", "compare using generic " + oLHS.getClass());
							}
						}
						try {
							comp = oLHS.compareTo(oRHS);
						} catch (Throwable t) {
							comp = reportError(oLHS, oRHS, t);
						}
					}
					if (comp != 0) {
						if (isAsc != sortDefinition.sortOrderNatural[i]) {
							return -comp;
						}
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