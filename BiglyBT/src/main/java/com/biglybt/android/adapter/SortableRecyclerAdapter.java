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

package com.biglybt.android.adapter;

import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import android.arch.lifecycle.Lifecycle;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.widget.Filterable;

/**
 * Created by TuxPaper on 8/6/18.
 */
public abstract class SortableRecyclerAdapter<ADAPTERTYPE extends SortableRecyclerAdapter<ADAPTERTYPE, VH, T>, VH extends RecyclerView.ViewHolder, T extends Comparable<T>>
	extends FlexibleRecyclerAdapter<ADAPTERTYPE, VH, T>
	implements Filterable, FastScrollRecyclerView.SectionedAdapter,
	SortableAdapter<T>, DelayedFilter.PerformingFilteringListener
{
	private LetterFilter<T> filter;

	private final Object lockFilter = new Object();

	private LettersUpdatedListener lettersUpdateListener;

	private DelayedFilter.PerformingFilteringListener performingFilteringListener;

	public SortableRecyclerAdapter(String TAG, Lifecycle lifecycle,
			FlexibleRecyclerSelectionListener<ADAPTERTYPE, VH, T> rs) {
		super(TAG, lifecycle, rs);
	}

	abstract public LetterFilter<T> createFilter();

	public void resetFilter() {
		if (filter != null) {
			filter.destroy();
		}
		filter = null;
	}

	@Override
	public LetterFilter<T> getFilter() {
		synchronized (lockFilter) {
			if (filter == null) {
				filter = createFilter();
				if (lettersUpdateListener != null) {
					filter.setLettersUpdatedListener(lettersUpdateListener);
				}
				ComparatorMapFields<T> sorter = filter.getSorter();
				if (sorter != null) {
					// call setSortDefinition in case any subclasses override to
					// calculate stuff (FilesTreeAdapter).
					setSortDefinition(sorter.getSortDefinition(), sorter.isAsc());
				}
			}
			return filter;
		}
	}

	@NonNull
	@Override
	public String getSectionName(int position) {
		return getFilter().getSectionName(position);
	}

	public void setLettersUpdatedListener(LettersUpdatedListener l) {
		lettersUpdateListener = l;
		synchronized (lockFilter) {
			if (filter != null) {
				filter.setLettersUpdatedListener(l);
			}
		}
	}

	@Override
	public ComparatorMapFields<T> getSorter() {
		return getFilter().getSorter();
	}

	@Override
	public void setSortDefinition(SortDefinition sortDefinition, boolean isAsc) {
		getFilter().setSortDefinition(sortDefinition, isAsc);
	}

	@Override
	public void performingFilteringChanged(
			@DelayedFilter.FilterState int filterState,
			@DelayedFilter.FilterState int oldState) {
		if (performingFilteringListener == null) {
			return;
		}
		performingFilteringListener.performingFilteringChanged(filterState,
				oldState);
	}

	@Override
	protected void triggerOnSetItemsCompleteListeners() {
		super.triggerOnSetItemsCompleteListeners();
		LetterFilter<T> filter = getFilter();
		if (filter.getFilterState() == DelayedFilter.FILTERSTATE_PUBLISHING) {
			filter.setFilterState(DelayedFilter.FILTERSTATE_IDLE);
		}
	}

	@Override
	public void setPerformingFilteringListener(boolean immediateTrigger,
			DelayedFilter.PerformingFilteringListener performingFilteringListener) {
		this.performingFilteringListener = performingFilteringListener;
		if (performingFilteringListener != null && immediateTrigger) {
			int filterState = getFilter().getFilterState();
			performingFilteringListener.performingFilteringChanged(filterState,
					filterState);
		}
	}

}
