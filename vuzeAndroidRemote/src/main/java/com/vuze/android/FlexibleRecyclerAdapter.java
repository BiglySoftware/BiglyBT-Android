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
 *  * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
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

public abstract class FlexibleRecyclerAdapter<VH extends RecyclerView.ViewHolder, T>
	extends RecyclerView.Adapter<VH>
	implements FlexibleRecyclerViewHolder.RecyclerSelectorInternal<VH>
{

	private static final String TAG = "FlexibleRecyclerAdapter";

	public static final int NO_CHECK_ON_SELECTED = -1;

	private final Object mLock = new Object();

	/** List of they keys of all entries displayed, in the display order */
	private List<T> mItems = new ArrayList<>();

	private int selectedPosition = -1;

	private VH selectedHolder;

	private FlexibleRecyclerSelectionListener selector;

	private ArrayList<Integer> checkedItems = new ArrayList<>();

	private boolean mIsMultiSelectMode;

	private int checkOnSelectedAfterMS = NO_CHECK_ON_SELECTED;

	private Runnable runnableDelayedCheck;

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

	public void setRecyclerSelector(FlexibleRecyclerSelectionListener rs) {
		selector = rs;
	}

	public void notifyDataSetInvalidated() {
		notifyItemRangeChanged(0, getItemCount());
	}

	/**
	 * Saves the state of the current selection on the items.
	 *
	 * @param outState Current state
	 */
	public void onSaveInstanceState(Bundle outState) {
		outState.putIntegerArrayList(TAG + ".checked", checkedItems);
		outState.putInt(TAG + ".selPos", selectedPosition);
	}

	/**
	 * Restores the previous state of the selection on the items.
	 *
	 * @param savedInstanceState Previous state
	 */
	public void onRestoreInstanceState(Bundle savedInstanceState,
			RecyclerView rv) {
		checkedItems = savedInstanceState.getIntegerArrayList(TAG + ".checked");
		selectedPosition = savedInstanceState.getInt(TAG + ".selPos", -1);
		if (selectedPosition >= 0) {
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
		return mItems != null && mItems.size() > 0 ? mItems.indexOf(item) : -1;
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

	@Override
	public final VH onCreateViewHolder(ViewGroup parent, int viewType) {
		VH vh = onCreateFlexibleViewHolder(parent, viewType);
		return vh;
	}

	@Override
	public final void onBindViewHolder(VH holder, int position,
			List<Object> payloads) {
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
		return mItems != null ? mItems.size() : 0;
	}

	public boolean isEmpty() {
		return getItemCount() == 0;
	}

	public void updateItem(int position, T item) {
		if (position < 0) {
			Log.w(TAG, "Cannot updateItem on negative position");
			return;
		}
		synchronized (mLock) {
			mItems.set(position, item);
		}
		Log.v(TAG, "updateItem notifyItemChanged on position " + position);
		notifyItemChanged(position);
	}

	public void addItem(T item) {
		int position;
		synchronized (mLock) {
			mItems.add(item);
			position = mItems.size() - 1;
		}
		notifyItemInserted(position);
	}

	/**
	 * Insert given Item at position or Add Item at last position.
	 *
	 * @param position Position of the item to add
	 * @param item     The item to add
	 */
	public void addItem(int position, T item) {
		if (position < 0) {
			Log.w(TAG, "Cannot addItem on negative position");
			return;
		}
		//Insert Item
		if (position < mItems.size()) {
			Log.v(TAG, "addItem notifyItemInserted on position " + position);
			synchronized (mLock) {
				mItems.add(position, item);
			}
		} else { //Add Item at the last position
			Log.v(TAG, "addItem notifyItemInserted on last position");
			synchronized (mLock) {
				mItems.add(item);
				position = mItems.size() - 1;
			}
		}
		notifyItemInserted(position);
	}

	public void removeAllItems() {
		int count;
		synchronized (mLock) {
			count = mItems.size();
			mItems.clear();
		}
		notifyItemRangeRemoved(0, count);
	}

	protected void setItems(List<T> items) {
		boolean typeChanged = false;
		int oldCount;
		int newCount;
		synchronized (mLock) {
			oldCount = mItems.size();
			newCount = items.size();

			mItems.clear();
			mItems.addAll(items);
		}
		if (oldCount > newCount) {
			// prevent
			// java.lang.IndexOutOfBoundsException: Inconsistency detected. Invalid view holder adapter positionViewHolder
			notifyItemRangeRemoved(oldCount, oldCount - newCount);
		} else if (newCount > oldCount) {
			notifyItemRangeInserted(newCount, newCount - oldCount);
		}
		notifyItemRangeChanged(0, newCount);
	}

	/**
	 * Override this if you have multiple view types
	 *
	 * @param item
	 * @return
	 */
	public int getItemViewType(T item) {
		return 0;
	}

	public void sortItems(Comparator<Object> sorter) {
		synchronized (mLock) {
			// java.lang.IllegalArgumentException: Comparison method violates its
			// general contract!
			try {
				Collections.sort(mItems, sorter);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}

		notifyDataSetInvalidated();
	}

	///////////////////////
	// Selection Functions
	///////////////////////

	@Override
	public void onItemClick(VH holder, View view) {
		int position = holder.getAdapterPosition();
		boolean alreadyChecked = checkedItems.contains(position);
		// clear previous selection when not in multimode
		if (!mIsMultiSelectMode && getCheckedItemCount() > 0) {
			if (getCheckedItemCount() > 1 || checkedItems.get(0) != position) {
				clearChecked();
			}
		}
		if (mIsMultiSelectMode || !alreadyChecked) {
			toggleItemChecked(holder);
		}
		if (selector != null) {
			selector.onItemClick(position);
		}
	}

	@Override
	public boolean onItemLongClick(VH holder, View view) {
		int position = holder.getAdapterPosition();

		if (selector != null) {
			if (selector.onItemLongClick(position)) {
				return true;
			}
		}
		if (!mIsMultiSelectMode) {
			mIsMultiSelectMode = true;
		}

		toggleItemChecked(holder, true);
		return true;
	}

	public boolean isItemSelected(int position) {
		return position != -1 && position == selectedPosition;
	}

	public void onFocusChange(VH holder, View v, boolean hasFocus) {
		final int position = holder.getAdapterPosition();
		if (hasFocus) {
			if (selectedHolder != null && selectedHolder != holder
					&& selectedHolder.getAdapterPosition() == selectedPosition) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Focus Changed; Unselect previous position of "
							+ selectedPosition);
				}
				selectedHolder.itemView.setSelected(false);
				notifyItemChanged(selectedPosition);
			}
			selectedHolder = holder;
			selectedPosition = position;
			holder.itemView.setSelected(true);
		}
		boolean isChecked = checkedItems.contains(position);
		if (selector != null && hasFocus) {
			selector.onItemSelected(position, isChecked);
		}

		// Check item on selection
		if (checkOnSelectedAfterMS >= 0) {
			if (runnableDelayedCheck != null) {
				v.getRootView().removeCallbacks(runnableDelayedCheck);
				runnableDelayedCheck = null;
			}

			if (isMultiSelectMode() || isChecked) {
				// Multiselect: We don't want to auto-check when focus changes
				// Already checked? If we aren't multimode, then something has already done our job (such as a tap)
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

	public boolean isItemChecked(int position) {
		return checkedItems.contains(position);
	}

	private void toggleItemChecked(RecyclerView.ViewHolder holder) {
		Integer position = holder.getAdapterPosition();
		boolean nowChecked;
		if (checkedItems.contains(position)) {
			checkedItems.remove(position);
			nowChecked = false;
			if (checkedItems.size() == 0) {
				setMultiSelectMode(false);
			}
		} else {
			checkedItems.add(position);
			nowChecked = true;
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "toggleItemChecked to " + nowChecked + " for " + position + ";"
					+ AndroidUtils.getCompressedStackTrace(4));
		}
		AndroidUtilsUI.setViewChecked(holder.itemView, nowChecked);

		notifyItemChanged(position);
		if (selector != null) {
			selector.onItemCheckedChanged(position, nowChecked);
		}

	}

	private void toggleItemChecked(RecyclerView.ViewHolder holder, boolean on) {
		Integer position = holder.getAdapterPosition();
		boolean alreadyChecked = checkedItems.contains(position);
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "toggleItemChecked to " + on + " for " + position + "; was "
					+ alreadyChecked + ";" + AndroidUtils.getCompressedStackTrace(4));
		}
		if (on != alreadyChecked) {
			if (on) {
				checkedItems.add(position);
			} else {
				checkedItems.remove(position);
				if (checkedItems.size() == 0) {
					setMultiSelectMode(false);
				}
			}
			AndroidUtilsUI.setViewChecked(holder.itemView, on);
			notifyItemChanged(position);

			if (selector != null) {
				selector.onItemCheckedChanged(position, on);
			}
		}
	}

	/**
	 * Flips the checked state of an item
	 *
	 * @param position The position of the item to flip the state of
	 */
	// doesn't immediately update check state visually
	public void toggleItemChecked(Integer position) {
		toggleItemChecked(position, true);
	}

	public void toggleItemChecked(Integer position, boolean notifySelector) {
		boolean checked;
		if (checkedItems.contains(position)) {
			checkedItems.remove(position);
			checked = false;
			if (checkedItems.size() == 0) {
				setMultiSelectMode(false);
			}
		} else {
			checkedItems.add(position);
			checked = true;
		}

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "toggleItemChecked to " + checked + " for " + position + ";"
					+ AndroidUtils.getCompressedStackTrace(4));
		}
		notifyItemChanged(position);

		if (selector != null && notifySelector) {
			selector.onItemCheckedChanged(position, checked);
		}
	}

	// doesn't immediately update check state visually
	public void setItemChecked(Integer position, boolean checked) {
		boolean alreadyChecked = checkedItems.contains(position);
		if (checked != alreadyChecked) {
			if (checked) {
				checkedItems.add(position);
			} else {
				checkedItems.remove(position);
				if (checkedItems.size() == 0) {
					setMultiSelectMode(false);
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
				selector.onItemCheckedChanged(position, checked);
			}
		}
	}

	public void clearChecked() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Clear Checked " + checkedItems.size()
					+ " and selected position " + selectedPosition);
		}
		Integer[] positions = checkedItems.toArray(
				new Integer[checkedItems.size()]);
		for (Integer item : positions) {
			toggleItemChecked(item, false);
			notifyItemChanged(item);
		}
		if (selectedPosition >= 0) {
			// put in varaible in case notify is immediate and notification checks
			// if item is selected
			int i = selectedPosition;
			selectedPosition = -1;
			notifyItemChanged(i);
		}
	}

	public int getCheckedItemCount() {
		return checkedItems.size();
	}

	public boolean isMultiSelectMode() {
		return mIsMultiSelectMode;
	}

	public void setMultiSelectMode(boolean on) {
		mIsMultiSelectMode = on;
		if (!on) {
			clearChecked();
		}
	}

	/**
	 * @return The positions of the checked items
	 */
	public Integer[] getCheckedItemPositions() {
		return checkedItems.toArray(new Integer[checkedItems.size()]);
	}

	/**
	 * Get the selected position.  May not be checked.
	 * Will not be focused if RecyclerView is not focus.
	 *
	 * @return
	 */
	public int getSelectedPosition() {
		return selectedPosition;
	}

	/**
	 * Retrieves the delay time before a seleced item becomes checked while in
	 * single select mode.  Multi-select mode ignores this parameter.
	 *
	 * @return Time in MS, or NO_CHECK_ON_SELECTED to disabled auto checking selected items
	 */
	public int getCheckOnSelectedAfterMS() {
		return checkOnSelectedAfterMS;
	}

	/**
	 * Sets the delay time before a selected item becomes checked while in
	 * single select mode.  Multi-select mode ignores this parameter.
	 *
	 * @param checkOnSelectedAfterMS
	 * Time in MS, or NO_CHECK_ON_SELECTED to disabled auto checking selected items
	 */
	public void setCheckOnSelectedAfterMS(int checkOnSelectedAfterMS) {
		this.checkOnSelectedAfterMS = checkOnSelectedAfterMS;
	}
}
