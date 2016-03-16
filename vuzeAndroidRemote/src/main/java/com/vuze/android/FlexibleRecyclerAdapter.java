/*
 * *
 *  * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *  *
 *  * This program is free software; you can redistribute it and/or
 *  * modify it under the terms of the GNU General Public License
 *  * as published by the Free Software Foundation; either version 2
 *  * of the License, or (at your option) any later version.
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program; if not, write to the Free Software
 *  * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 *  USA.
 *
 *
 */

package com.vuze.android;

import java.util.*;

import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.AndroidUtilsUI;

/**
 * This adapter requires only having one RecyclerView attached to it.
 *
 * @param <VH> ViewHolder class for an item
 * @param <T>  Data representation class of an item
 */
public abstract class FlexibleRecyclerAdapter<VH extends RecyclerView.ViewHolder, T extends Comparable<T>>
	extends RecyclerView.Adapter<VH>
	implements FlexibleRecyclerViewHolder.RecyclerSelectorInternal<VH>
{

	private static final String TAG = "FlexibleRecyclerAdapter";

	public static final int NO_CHECK_ON_SELECTED = -1;

	private final Object mLock = new Object();

	/** List of they keys of all entries displayed, in the display order */
	private List<T> mItems = new ArrayList<>();

	private int selectedPosition = -1;

	private T selectedItem;

	private FlexibleRecyclerSelectionListener selector;

	private List<T> checkedItems = new ArrayList<>();

	private boolean mIsMultiSelectMode;

	private int checkOnSelectedAfterMS = NO_CHECK_ON_SELECTED;

	private Runnable runnableDelayedCheck;

	private RecyclerView recyclerView;

	private boolean mAllowMultiSelectMode = true;

	public FlexibleRecyclerAdapter() {
		super();
	}

	public FlexibleRecyclerAdapter(FlexibleRecyclerSelectionListener rs) {
		super();
		selector = rs;
	}

	public FlexibleRecyclerSelectionListener getRecyclerSelector() {
		return selector;
	}

	@Override
	public void onAttachedToRecyclerView(RecyclerView recyclerView) {
		if (this.recyclerView != null) {
			Log.e(TAG, "Multiple RecyclerViews not allowed on Adapter " + this);
		}
		this.recyclerView = recyclerView;
	}

	@Override
	public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
		super.onDetachedFromRecyclerView(recyclerView);
		this.recyclerView = null;
	}

	public void setRecyclerSelector(FlexibleRecyclerSelectionListener rs) {
		selector = rs;
	}

	public void notifyDataSetInvalidated() {
		if (AndroidUtils.DEBUG_ADAPTER) {
			log("setItems: invalidate all");
		}
		notifyItemRangeChanged(0, getItemCount());
	}

	/**
	 * @return The positions of the checked items
	 */
	public int[] getCheckedItemPositions() {
		synchronized (mItems) {
			int[] positions = new int[checkedItems.size()];
			for (int i = 0; i < positions.length; i++) {
				positions[i] = getPositionForItem(checkedItems.get(i));
			}
			return positions;
		}
	}

	private void setCheckedPositions(int[] positions) {
		// TODO: notify before clearing
		checkedItems.clear();
		if (positions == null || positions.length == 0) {
			return;
		}
		for (int position : positions) {
			T item = getItem(position);
			if (item != null) {
				checkedItems.add(item);
			}
		}
	}

	/**
	 * Saves the state of the current selection on the items.
	 *
	 * @param outState Current state
	 */
	public void onSaveInstanceState(Bundle outState) {
		outState.putIntArray(TAG + ".checked", getCheckedItemPositions());
		outState.putInt(TAG + ".selPos", selectedPosition);
	}

	/**
	 * Restores the previous state of the selection on the items.
	 *
	 * @param savedInstanceState Previous state
	 */
	public void onRestoreInstanceState(Bundle savedInstanceState,
			RecyclerView rv) {
		if (savedInstanceState == null) {
			return;
		}
		int[] checkedPositions = savedInstanceState.getIntArray(TAG + ".checked");
		setCheckedPositions(checkedPositions);
		selectedPosition = savedInstanceState.getInt(TAG + ".selPos", -1);
		if (selectedPosition >= 0) {
			selectedItem = getItem(selectedPosition);
			rv.scrollToPosition(selectedPosition);
		}
	}

	//////////////////
	// Item functions
	//////////////////

	/**
	 * Retrieve the position of the Item in the Adapter
	 *
	 * @param item The item
	 * @return The position in the Adapter if found, -1 otherwise
	 */
	public int getPositionForItem(T item) {
		if (item == null) {
			return -1;
		}
		int position = mItems.indexOf(item);
		if (position >= 0) {
			return position;
		}
		// Direct comparison failed, maybe item is in list, but as a different object
		int s = mItems.size();
		for (int i = 0; i < s; i++) {
			if (mItems.get(i).compareTo(item) == 0) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Retrieve the position of the Items in the Adapter
	 *
	 * @param items list of item Objects
	 * @return list of positions
	 */
	public int[] getPositionForItems(T[] items) {
		int positions[] = new int[items.length];
		int i = 0;
		for (T torrentID : items) {
			positions[i] = getPositionForItem(torrentID);
		}

		return positions;
	}

	/**
	 * Returns the custom object "Item".
	 *
	 * @param position The position of the item in the list
	 * @return The custom "Item" object or null if item not found
	 */
	public T getItem(int position) {
		if (position < 0 || position >= mItems.size()) {
			return null;
		}
		return mItems.get(position);
	}

	//int countC = 0; // For tracking/debuging if we are creating too many holders
	//int countB = 0; // instead of just rebinding them

	@Override
	public final VH onCreateViewHolder(ViewGroup parent, int viewType) {
		//log("onCreateViewHolder: " + (++countC));
		VH vh = onCreateFlexibleViewHolder(parent, viewType);
		return vh;
	}

	@Override
	public final void onBindViewHolder(VH holder, int position,
			List<Object> payloads) {
		//log("onBindViewHolder: " + (++countB));
		onBindFlexibleViewHolder(holder, position, payloads);
	}

	private void onBindFlexibleViewHolder(VH holder, int position,
			List<Object> payloads) {
		onBindViewHolder(holder, position);
	}

	public abstract VH onCreateFlexibleViewHolder(ViewGroup parent, int viewType);

	@Override
	public final void onBindViewHolder(VH holder, int position) {
		onBindFlexibleViewHolder(holder, position);

		boolean checked = isItemChecked(position);
		boolean selected = isItemSelected(position);
		if (holder.itemView != null) {
			holder.itemView.setSelected(selected);
			AndroidUtilsUI.setViewChecked(holder.itemView, checked);
		}
	}

	public abstract void onBindFlexibleViewHolder(VH holder, final int position);

	@Override
	public int getItemCount() {
		return mItems.size();
	}

	public boolean isEmpty() {
		return getItemCount() == 0;
	}

	public void updateItem(final int position, final T item) {
		if (!AndroidUtilsUI.isUIThread()) {
			recyclerView.post(new Runnable() {
				@Override
				public void run() {
					updateItem(position, item);
				}
			});
			return;
		}
		if (position < 0) {
			return;
		}
		synchronized (mLock) {
			mItems.set(position, item);

			if (selectedPosition == position) {
				selectedItem = item;
			}
		}

		if (AndroidUtils.DEBUG_ADAPTER) {
			Log.v(TAG, "updateItem: " + position);
		}
		notifyItemChanged(position);
	}

	public void addItem(final T item) {
		if (!AndroidUtilsUI.isUIThread()) {
			recyclerView.post(new Runnable() {
				@Override
				public void run() {
					if (AndroidUtils.DEBUG_ADAPTER) {
						log("addItem: delayed");
					}
					addItem(item);
				}
			});
			return;
		}
		int position;
		synchronized (mLock) {
			mItems.add(item);
			position = mItems.size() - 1;
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			log("addItem: " + position);
		}
		notifyItemInserted(position);
	}

	/**
	 * Insert given Item at position or Add Item at last position.
	 *
	 * @param position Position of the item to add
	 * @param item     The item to add
	 */
	public void addItem(int position, final T item) {
		if (!AndroidUtilsUI.isUIThread()) {
			final int finalPosition = position;
			recyclerView.post(new Runnable() {
				@Override
				public void run() {
					if (AndroidUtils.DEBUG_ADAPTER) {
						log("addItem: delayed. " + finalPosition);
					}
					addItem(finalPosition, item);
				}
			});
			return;
		}
		if (position < 0) {
			Log.w(TAG, "Cannot addItem on negative position");
			return;
		}
		//Insert Item
		if (position < mItems.size()) {
			synchronized (mLock) {
				mItems.add(position, item);
				if (selectedPosition >= 0 && selectedPosition < position) {
					selectedPosition++;
				}
			}
		} else { //Add Item at the last position
			synchronized (mLock) {
				mItems.add(item);
				position = mItems.size() - 1;
			}
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			log("addItem: " + position);
		}
		notifyItemInserted(position);
	}

	public void removeItem(final int position) {
		if (position < 0) {
			return;
		}

		if (!AndroidUtilsUI.isUIThread()) {
			recyclerView.post(new Runnable() {
				@Override
				public void run() {
					removeItem(position);
				}
			});
			return;
		}

		T itemRemoved = null;
		synchronized (mLock) {
			itemRemoved = mItems.remove(position);
		}
		if (itemRemoved == null) {
			return;
		}

		if (selectedPosition == position) {
			selectedItem = null;
			selectedPosition = -1;
		} else if (selectedPosition > position) {
			selectedPosition--;
			if (selector != null && selectedItem != null) {
				selector.onItemSelected(this, selectedPosition,
						isItemChecked(selectedItem));
			}
		}

		if (checkedItems.size() > 0) {
			boolean removed = checkedItems.remove(itemRemoved);
			if (removed && selector != null) {
				selector.onItemCheckedChanged(this, itemRemoved, false);
			}
		}

		notifyItemRangeRemoved(position, 1);
	}

	public void removeAllItems() {
		if (!AndroidUtilsUI.isUIThread()) {
			recyclerView.post(new Runnable() {
				@Override
				public void run() {
					if (AndroidUtils.DEBUG_ADAPTER) {
						log("removeAllItems: delayed");
					}
					removeAllItems();
				}
			});
			return;
		}
		int count;
		synchronized (mLock) {
			count = mItems.size();
			mItems.clear();
		}
		if (selectedPosition >= 0) {
			selectedPosition = -1;
			selectedItem = null;
			// trigger some unselection event?
		}
		if (checkedItems.size() > 0) {
			if (selector != null) {
				for (T checkedItem : checkedItems) {
					selector.onItemCheckedChanged(this, checkedItem, false);
				}
			}
			checkedItems.clear();
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			log("removeAllItems: " + count);
		}
		notifyItemRangeRemoved(0, count);
	}

	public void setItems(final List<T> items) {
		if (!AndroidUtilsUI.isUIThread()) {
			recyclerView.post(new Runnable() {
				@Override
				public void run() {
					if (AndroidUtils.DEBUG_ADAPTER) {
						log("setItems: delay");
					}
					setItems(items);
				}
			});
			return;
		}

		List<T> notifyUncheckedList = new ArrayList<>();
		int oldCount;
		int newCount;
		synchronized (mLock) {
			oldCount = mItems.size();
			newCount = items.size();

			mItems = new ArrayList<>();
			mItems.clear();
			mItems.addAll(items);
			if (selectedItem != null) {
				// relink, since we may have a new object with the same stableId
				selectedPosition = getPositionForItem(selectedItem);
				selectedItem = getItem(selectedPosition);
			}

			notifyUncheckedList = relinkCheckedItems();
		}

		if (selector != null) {
			for (T item : notifyUncheckedList) {
				selector.onItemCheckedChanged(this, item, false);
			}
		}

		if (AndroidUtils.DEBUG_ADAPTER) {
			log("setItems: oldCount=" + oldCount + ";new=" + newCount + ";" + this);
		}

		// prevent
		// java.lang.IndexOutOfBoundsException: Inconsistency detected. Invalid
		// view holder adapter position

		// This isn't what I want to do, but this is the only way I can get it to
		// work on some devices.
		// notifyItemRangeRemoved(0, oldCount);
		// notifyItemRangeInserted(0, newCount);

		// This is how I want to do it.  Works only when setItemAnimator is null
		// It's like RecyclerView is trying to run predictive animations after
		// dataset has changed and not laid out (despite it checking
		// mDataSetHasChangedAfterLayout?)
		if (oldCount > newCount) {
			notifyItemRangeRemoved(newCount, oldCount - newCount);
			notifyItemRangeChanged(0, newCount);
			if (AndroidUtils.DEBUG_ADAPTER) {
				log("setItems: remove from " + newCount + " size "
						+ (oldCount - newCount));
				log("setItems: change from 0, size " + newCount);
			}
		} else if (newCount > oldCount) {
			notifyItemRangeInserted(oldCount, newCount - oldCount);
			notifyItemRangeChanged(0, oldCount);
			if (AndroidUtils.DEBUG_ADAPTER) {
				log("setItems: insert from " + oldCount + " size "
						+ (newCount - oldCount));
				log("setItems: change 0 to " + oldCount);
			}
		} else {
			notifyDataSetInvalidated();
		}
	}

	private List<T> relinkCheckedItems() {
		if (checkedItems.size() == 0) {
			return Collections.emptyList();
		}

		List<T> notifyUncheckedList = new ArrayList<>();
		ListIterator<T> checkedItemsIterator = checkedItems.listIterator();
		while (checkedItemsIterator.hasNext()) {
			T item = checkedItemsIterator.next();

			int newPosition = getPositionForItem(item);

			if (newPosition < 0) {
				checkedItemsIterator.remove();
				notifyUncheckedList.add(item);
			} else {
				checkedItemsIterator.set(getItem(newPosition));
			}
		}
		return notifyUncheckedList;
	}

	public void sortItems(final Comparator<Object> sorter) {
		if (AndroidUtils.DEBUG_ADAPTER) {
			log("sortItems");
		}

		new Thread(new Runnable() {
			@Override
			public void run() {
				synchronized (mLock) {
					List<T> itemsNew = doSort(mItems, sorter, true);
					setItems(itemsNew);
				}

				recyclerView.post(new Runnable() {
					@Override
					public void run() {
						notifyDataSetInvalidated();
					}
				});
			}
		}, "sortItems " + this.getClass().getSimpleName()).start();

	}

	public List<T> doSort(List<T> items, Comparator<Object> sorter,
			boolean createNewList) {
		if (AndroidUtilsUI.isUIThread()) {
			Log.w(TAG,
					"Sorting on UIThread! " + AndroidUtils.getCompressedStackTrace());
		}

		List<T> itemsNew = createNewList ? new ArrayList<T>(items) : items;

		// java.lang.IllegalArgumentException: Comparison method violates its
		// general contract!
		try {
			Collections.sort(itemsNew, sorter);
		} catch (Throwable t) {
			Log.e(TAG, "doSort: ", t);
		}

		return itemsNew;
	}

	///////////////////////
	// Selection Functions
	///////////////////////

	@Override
	public void onItemClick(VH holder, View view) {
		int position = holder.getLayoutPosition();
		boolean alreadyChecked = isItemChecked(position);
		// clear previous selection when not in multimode
		if (!mIsMultiSelectMode && getCheckedItemCount() > 0) {
			if (getCheckedItemCount() > 1
					|| getPositionForItem(checkedItems.get(0)) != position) {
				clearChecked();
			}
		}

		setItemSelected(position, holder);

		if (mIsMultiSelectMode || !alreadyChecked) {
			toggleItemChecked(holder);
		}

		if (selector != null) {
			selector.onItemClick(this, position);
		}
	}

	@Override
	public boolean onItemLongClick(VH holder, View view) {
		int position = holder.getLayoutPosition();

		if (!mIsMultiSelectMode) {
			if (mAllowMultiSelectMode) {
				mIsMultiSelectMode = true;
			} else {
				if (getCheckedItemCount() > 0 && (getCheckedItemCount() > 1
						|| getPositionForItem(checkedItems.get(0)) != position)) {
					clearChecked();
				}
			}
		}
		setItemSelected(position, holder);

		if (selector != null) {
			if (selector.onItemLongClick(this, position)) {
				return true;
			}
		}

		// Only toggle checked if selector didn't handle it
		toggleItemChecked(holder, true);

		return true;
	}

	public boolean isItemSelected(int position) {
		return position != -1 && position == selectedPosition;
	}

	public void onFocusChange(VH holder, View v, boolean hasFocus) {
		if (AndroidUtils.DEBUG_ADAPTER) {
			log("onFocusChange: " + hasFocus + ";" + this + ";"
					+ AndroidUtils.getCompressedStackTrace());
		}
		if (!hasFocus) {
			return;
		}
		final int position = holder.getLayoutPosition();
		setItemSelected(position, holder);

		// Check item on selection
		if (checkOnSelectedAfterMS >= 0) {
			if (runnableDelayedCheck != null) {
				v.getRootView().removeCallbacks(runnableDelayedCheck);
				runnableDelayedCheck = null;
			}

			boolean isChecked = isItemChecked(position);
			if (isMultiCheckMode() || isChecked) {
				// Multiselect: We don't want to auto-check when focus changes
				// Already checked? If we aren't multimode, then something has already
				// done our job (such as a tap)
				return;
			}
			runnableDelayedCheck = new Runnable() {
				@Override
				public void run() {
					if (runnableDelayedCheck != this || !isItemSelected(position)) {
						return;
					}
					clearChecked();
					setItemChecked(position, true);
					runnableDelayedCheck = null;
				}
			};
			v.getRootView().postDelayed(runnableDelayedCheck, checkOnSelectedAfterMS);
		}

	}

	private void setItemSelected(int position, VH holder) {
		RecyclerView.ViewHolder selectedHolder = selectedPosition < 0 ? null
				: recyclerView.findViewHolderForAdapterPosition(selectedPosition);

		if (selectedHolder != null && selectedHolder != holder) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log("setItemSelected: Unselect previous position of "
						+ selectedPosition);
			}
			selectedHolder.itemView.setSelected(false);
			notifyItemChanged(selectedPosition);
		}
		selectedPosition = position;
		selectedItem = getItem(selectedPosition);
		holder.itemView.setSelected(selectedItem != null);

		if (AndroidUtils.DEBUG_ADAPTER) {
			log("setItemSelected: changed selected to " + selectedPosition);
		}

		if (selector != null) {
			selector.onItemSelected(this, position, isItemChecked(position));
		}
	}

	public boolean isItemChecked(int position) {
		return checkedItems.contains(getItem(position));
	}

	private boolean isItemChecked(T item) {
		return checkedItems.contains(item);
	}

	private void toggleItemChecked(RecyclerView.ViewHolder holder) {
		Integer position = holder.getLayoutPosition();
		boolean nowChecked;
		T item = getItem(position);
		if (item == null) {
			return;
		}
		if (isItemChecked(item)) {
			checkedItems.remove(item);
			nowChecked = false;
			if (checkedItems.size() == 0) {
				setMultiCheckMode(false);
			}
		} else {
			checkedItems.add(item);
			nowChecked = true;
		}
		if (AndroidUtils.DEBUG) {
			log("toggleItemChecked to " + nowChecked + " for " + position);
		}
		AndroidUtilsUI.setViewChecked(holder.itemView, nowChecked);

		notifyItemChanged(position);
		if (selector != null) {
			selector.onItemCheckedChanged(this, item, nowChecked);
		}

	}

	private void toggleItemChecked(RecyclerView.ViewHolder holder, boolean on) {
		Integer position = holder.getLayoutPosition();
		T item = getItem(position);
		boolean alreadyChecked = checkedItems.contains(item);
		if (AndroidUtils.DEBUG) {
			log("toggleItemChecked to " + on + " for " + position + "; was "
					+ alreadyChecked);
		}
		if (on != alreadyChecked) {
			if (on) {
				checkedItems.add(item);
			} else {
				checkedItems.remove(item);
				if (checkedItems.size() == 0) {
					setMultiCheckMode(false);
				}
			}
			AndroidUtilsUI.setViewChecked(holder.itemView, on);
			notifyItemChanged(position);

			if (selector != null) {
				selector.onItemCheckedChanged(this, item, on);
			}
		}
	}

	/**
	 * Flips the checked state of an item
	 *
	 * @param position The position of the item to flip the state of
	 */
	// doesn't immediately update check state visually
	public void toggleItemChecked(int position) {
		toggleItemChecked(position, true);
	}

	private void toggleItemChecked(Integer position, boolean notifySelector) {
		boolean checked;
		T item = getItem(position);
		if (checkedItems.contains(item)) {
			checkedItems.remove(item);
			checked = false;
			if (checkedItems.size() == 0) {
				setMultiCheckMode(false);
			}
		} else {
			checkedItems.add(item);
			checked = true;
		}

		if (AndroidUtils.DEBUG) {
			log("toggleItemChecked to " + checked + " for " + position + ";"
					+ AndroidUtils.getCompressedStackTrace(4));
		}
		notifyItemChanged(position);

		if (selector != null && notifySelector) {
			selector.onItemCheckedChanged(this, item, checked);
		}
	}

	public void setItemChecked(T item, boolean checked) {
		if (item == null) {
			return;
		}
		setItemChecked(item, getPositionForItem(item), checked);
	}

	// doesn't immediately update check state visually
	public void setItemChecked(int position, boolean checked) {
		if (position < 0) {
			return;
		}
		T item = getItem(position);
		setItemChecked(item, position, checked);
	}

	private void setItemChecked(T item, int position, boolean checked) {
		boolean alreadyChecked = checkedItems.contains(item);
		if (checked != alreadyChecked) {
			if (checked) {
				checkedItems.add(item);
			} else {
				checkedItems.remove(item);
				if (checkedItems.size() == 0) {
					setMultiCheckMode(false);
				}
			}
			if (AndroidUtils.DEBUG) {
				Log.d(TAG,
						"setItemChecked to " + checked + " for " + position + "; was "
								+ alreadyChecked + ";"
								+ AndroidUtils.getCompressedStackTrace(4));
			}

			notifyItemChanged(position);
			if (selector != null) {
				selector.onItemCheckedChanged(this, item, checked);
			}
		}
	}

	/**
	 * Unchecks all checked items
	 */
	public void clearChecked() {
		if (AndroidUtils.DEBUG_ADAPTER) {
			log("Clear " + checkedItems.size() + " checked via "
					+ AndroidUtils.getCompressedStackTrace(4));
		}
		Object[] checkedItemsArray = checkedItems.toArray();
		for (Object checkedItem : checkedItemsArray) {
			int position = getPositionForItem((T) checkedItem);
			if (position >= 0) {
				toggleItemChecked(position, false);
				notifyItemChanged(position);
			}
		}
	}

	public int getCheckedItemCount() {
		return checkedItems.size();
	}

	public boolean isMultiCheckMode() {
		return mIsMultiSelectMode;
	}

	public void setMultiCheckMode(boolean on) {
		mIsMultiSelectMode = on;
		if (!on && getCheckedItemCount() > 1) {
			clearChecked();
		}
	}

	/**
	 * Get the selected position.  May not be checked.
	 * Will not be focused if RecyclerView is not focus.
	 *
	 * @return The selected position.  < 0 if nothing is selected
	 */
	public int getSelectedPosition() {
		return selectedPosition;
	}

	public T getSelectedItem() {
		return selectedItem;
	}

	/**
	 * Retrieves the delay time before a seleced item becomes checked while in
	 * single select mode.  Multi-select mode ignores this parameter.
	 *
	 * @return Time in MS, or NO_CHECK_ON_SELECTED to disabled auto checking
	 * selected items
	 */
	public int getCheckOnSelectedAfterMS() {
		return checkOnSelectedAfterMS;
	}

	/**
	 * Sets the delay time before a selected item becomes checked while in
	 * single select mode.  Multi-select mode ignores this parameter.
	 *
	 * @param checkOnSelectedAfterMS Time in MS, or NO_CHECK_ON_SELECTED to
	 *                               disabled auto checking selected items
	 */
	public void setCheckOnSelectedAfterMS(int checkOnSelectedAfterMS) {
		this.checkOnSelectedAfterMS = checkOnSelectedAfterMS;
	}

	public RecyclerView getRecyclerView() {
		return recyclerView;
	}

	public void setMultiCheckModeAllowed(boolean allowed) {
		mAllowMultiSelectMode = allowed;
		if (!allowed) {
			setMultiCheckMode(false);
		}
	}

	public boolean isMultiCheckModeAllowed() {
		return mAllowMultiSelectMode;
	}

	private void log(String s) {
		Log.d(TAG, getClass().getSimpleName() + "] " + s);
	}
}
