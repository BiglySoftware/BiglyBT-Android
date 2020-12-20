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

import com.biglybt.android.adapter.ComparatorMapFields;
import com.biglybt.android.adapter.SortDefinition;
import com.biglybt.android.client.AnalyticsTracker;

import java.util.Objects;

/**
 * Created by TuxPaper on 10/31/18.
 */
abstract class ComparatorMapFieldsErr<T>
	extends ComparatorMapFields<T>
{
	private Throwable lastError;

	public ComparatorMapFieldsErr(SortDefinition sortDefinition, boolean isAsc) {
		super(sortDefinition, isAsc);
	}

	@Override
	public int reportError(Comparable<?> oLHS, Comparable<?> oRHS, Throwable t) {
		if (lastError != null) {
			// R8 will desugar Objects.equals, so the minAPI 19 lint warning is wrong
			//noinspection NewApi
			if (Objects.equals(t.getCause(), lastError.getCause())
					&& Objects.equals(t.getMessage(), lastError.getMessage())) {
				return 0;
			}
		}
		lastError = t;
		String tag = getClass().getSimpleName();
		AnalyticsTracker.getInstance().logError(t, tag);
		return 0;
	}
}
