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

import android.content.Context;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.TextViewFlipper.FlipValidator;
import com.biglybt.util.Thunk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checked == Activated according to google.  In google docs for View
 * .setActivated:
 * (Um, yeah, we are deeply sorry about the terminology here.)
 * <p/>
 * </p>
 * Other terms:
 * Focused: One focus per screen
 * Selected: highlighted item(s).  May not be activated
 * Checked: activated item(s)
 */
public class TorrentListAdapter
	extends
	SortableRecyclerAdapter<TorrentListAdapter, TorrentListHolder, TorrentListAdapterItem>
	implements FlexibleRecyclerAdapter.SetItemsCallBack<TorrentListAdapterItem>,
	SessionAdapterFilterTalkback<TorrentListAdapterItem>
{

	@Thunk
	static final boolean DEBUG = AndroidUtils.DEBUG_ADAPTER;

	private static final String TAG = "TorrentListAdapter";

	public static final int VIEWTYPE_TORRENT = 0;

	public static final int VIEWTYPE_HEADER = 2;

	public static class ViewHolderFlipValidator
		implements FlipValidator
	{
		private final TorrentListHolderItem holder;

		private final long torrentID;

		ViewHolderFlipValidator(@NonNull TorrentListHolderItem holder,
				long torrentID) {
			this.holder = holder;
			this.torrentID = torrentID;
		}

		@Override
		public boolean isStillValid() {
			return holder.torrentID == torrentID;
		}
	}

	@Thunk
	final Context context;

	@Thunk
	@NonNull
	final Object mLock = new Object();

	@NonNull
	private final TorrentListRowFiller torrentListRowFiller;

	@NonNull
	private final SessionGetter sessionGetter;

	private final boolean smallView;

	public TorrentListAdapter(@NonNull Context context,
			@NonNull SessionGetter sessionGetter,
			FlexibleRecyclerSelectionListener<TorrentListAdapter, TorrentListHolder, TorrentListAdapterItem> selector,
			boolean smallView) {
		super(TAG, selector);
		this.context = context;

		torrentListRowFiller = new TorrentListRowFiller(context);
		this.sessionGetter = sessionGetter;
		this.smallView = smallView;

		setHasStableIds(true);
	}

	@Override
	public boolean setItems(List<TorrentListAdapterItem> values,
			SparseIntArray countsByViewType) {
		return setItems(values, countsByViewType, this);
	}

	@Override
	public boolean areContentsTheSame(TorrentListAdapterItem oldItem,
			TorrentListAdapterItem newItem) {
		Session session = sessionGetter.getSession();
		if (!(oldItem instanceof TorrentListAdapterTorrentItem)
				|| session == null) {
			return true;
		}
		Map<?, ?> torrent = ((TorrentListAdapterTorrentItem) oldItem).getTorrentMap(
				session);
		long lastUpdated = MapUtils.getMapLong(torrent,
				TransmissionVars.FIELD_LAST_UPDATED, 0);
		long lastSetItemsOn = getLastSetItemsOn();
		return lastUpdated <= lastSetItemsOn;
	}

	@Override
	public LetterFilter<TorrentListAdapterItem> createFilter() {
		return new TorrentListFilter(this);
	}

	public void refreshDisplayList() {
		Session session = sessionGetter.getSession();
		if (session == null || !session.isReadyForUI()) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "skipped refreshDisplayList. ui not ready");
			}
			return;
		}
		getFilter().refilter(true);
	}

	@NonNull
	public TorrentListFilter getTorrentFilter() {
		return (TorrentListFilter) getFilter();
	}

	public Map<?, ?> getTorrentItem(int position) {
		Session session = sessionGetter.getSession();
		if (session == null) {
			return new HashMap<>();
		}
		TorrentListAdapterItem item = getItem(position);
		if (!(item instanceof TorrentListAdapterTorrentItem)) {
			return new HashMap<>();
		}
		return ((TorrentListAdapterTorrentItem) item).getTorrentMap(session);
	}

	public long getTorrentID(int position) {
		TorrentListAdapterItem item = getItem(position);
		if (item instanceof TorrentListAdapterTorrentItem) {
			return ((TorrentListAdapterTorrentItem) item).torrentID;
		}
		if (item instanceof TorrentListAdapterHeaderItem) {
			int id = ((TorrentListAdapterHeaderItem) item).id.hashCode();
			return id == 0 ? Integer.MIN_VALUE : id < 0 ? id : -id;
		}
		return -1;
	}

	@Override
	public int getItemViewType(int position) {
		return getItem(position) instanceof TorrentListAdapterHeaderItem
				? VIEWTYPE_HEADER : VIEWTYPE_TORRENT;
	}

	@NonNull
	@SuppressWarnings("unchecked")
	@Override
	public TorrentListHolder onCreateFlexibleViewHolder(@NonNull ViewGroup parent,
			@NonNull LayoutInflater inflater, int viewType) {
		@LayoutRes
		int resourceID;
		TorrentListHolder holder;

		boolean isHeader = viewType == VIEWTYPE_HEADER;
		if (isHeader) {
			View rowView = AndroidUtilsUI.requireInflate(inflater,
					R.layout.row_torrent_list_header, parent, false);
			holder = new TorrentListHolderHeader(this, this, rowView);
		} else {
			resourceID = smallView ? R.layout.row_torrent_list_small
					: R.layout.row_torrent_list;
			View rowView = AndroidUtilsUI.requireInflate(inflater, resourceID, parent,
					false);
			holder = new TorrentListHolderItem(this, rowView, smallView);
		}

		return holder;
	}

	@Override
	public void onBindFlexibleViewHolder(@NonNull TorrentListHolder holder,
			int position) {
		if (holder instanceof TorrentListHolderItem) {
			Map<?, ?> item = getTorrentItem(position);
			Session session = sessionGetter.getSession();
			torrentListRowFiller.fillHolder((TorrentListHolderItem) holder, item,
					session);
		} else if (holder instanceof TorrentListHolderHeader) {
			TorrentListAdapterItem item = getItem(position);
			if (item instanceof TorrentListAdapterHeaderItem) {
				((TorrentListHolderHeader) holder).bind(
						(TorrentListAdapterHeaderItem) item);
			}
		}
	}

	@Override
	public long getItemId(int position) {
		return getTorrentID(position);
	}

	public void flipHeaderCollapse(int adapterPosition) {
		TorrentListAdapterItem item = getItem(adapterPosition);
		if (item instanceof TorrentListAdapterHeaderItem) {
			TorrentListAdapterHeaderItem headerItem = (TorrentListAdapterHeaderItem) item;
			Comparable comparableGroup = headerItem.id;
			TorrentListFilter torrentFilter = getTorrentFilter();
			boolean nowCollapsed = !torrentFilter.isGroupCollapsed(comparableGroup);
			torrentFilter.setGroupCollapsed(comparableGroup, nowCollapsed);
			if (nowCollapsed) {
				int count = 0;
				int pos = adapterPosition + 1;
				while (true) {
					TorrentListAdapterItem nextItem = getItem(pos);
					if (nextItem instanceof TorrentListAdapterTorrentItem) {
						count++;
					} else {
						break;
					}
					pos++;
				}
				// headerItem.count might be incorrect if a row has been removed
				// and we haven't resorted/filtered yet! 
				// TODO ensure header gets updated when items added/removed without a refilter
				//int count = headerItem.count;
				removeItems(adapterPosition + 1, count);
			} else {
				refreshDisplayList();
			}
			RecyclerView rv = getRecyclerView();
			if (rv != null) {
				RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(
						adapterPosition);
				if (holder instanceof TorrentListHolderHeader) {
					((TorrentListHolderHeader) holder).updateCollapseState(nowCollapsed);
				}
			}
		} // else we could walk up the item tree until we hit a header item
	}

	public boolean showGroupCount(@SuppressWarnings("unused") Comparable id) {
		TorrentListSorter sorter = (TorrentListSorter) getSorter();
		if (sorter == null) {
			return false;
		}
		GroupedSortDefinition<TorrentListAdapterItem, Integer> sortDefinition = sorter.getGroupedSortDefinition();
		return sortDefinition != null && sortDefinition.showGroupCount();
	}

	public boolean isGroupCollapsed(Comparable id) {
		return getFilter().isGroupCollapsed(id);
	}

	@Override
	public boolean isItemCheckable(int position) {
		return getItem(position) instanceof TorrentListAdapterTorrentItem;
	}

	@Override
	public Session getSession() {
		return sessionGetter.getSession();
	}
}