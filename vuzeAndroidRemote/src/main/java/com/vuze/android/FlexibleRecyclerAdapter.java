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
 * </p>
 * TODO: When mItems is changed, need to update selectedPosition
 *
 * @param <VH> ViewHolder class for an item
 * @param <T>  Data representation class of an item
 */
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

	private T selectedItem;

	private FlexibleRecyclerSelectionListener selector;

	private ArrayList<Integer> checkedItems = new ArrayList<>();

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
			Log.d(TAG, "setItems: invalidate all");
		}
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
		if (savedInstanceState == null) {
			return;
		}
		checkedItems = savedInstanceState.getIntegerArrayList(TAG + ".checked");
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

	//int countC = 0;  // For tracking/debuging if we are creating too many holders
	//int countB = 0;  // instead of just rebinding them

	@Override
	public final VH onCreateViewHolder(ViewGroup parent, int viewType) {
		//Log.d(TAG, "onCreateViewHolder: " + (++countC));
		VH vh = onCreateFlexibleViewHolder(parent, viewType);
		return vh;
	}

	@Override
	public final void onBindViewHolder(VH holder, int position,
			List<Object> payloads) {
		//Log.d(TAG, "onBindViewHolder: " + (++countB));
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
						Log.d(TAG, "addItem: delayed");
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
			Log.d(TAG, "addItem: " + position);
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
						Log.d(TAG, "addItem: delayed. " + finalPosition);
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
			Log.d(TAG, "addItem: " + position);
		}
		notifyItemInserted(position);
	}

	public void removeItem(final int position) {
		if (!AndroidUtilsUI.isUIThread()) {
			recyclerView.post(new Runnable() {
				@Override
				public void run() {
					removeItem(position);
				}
			});
			return;
		}
		if (position < 0) {
			return;
		}
		synchronized (mLock) {
			mItems.remove(position);
			if (selectedPosition >= 0 && selectedPosition > position) {
				selectedPosition--;
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
						Log.d(TAG, "removeAllItems: delayed");
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
			selectedPosition = -1;
			selectedItem = null;
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			Log.d(TAG, "removeAllItems: " + count);
		}
		notifyItemRangeRemoved(0, count);
	}

	public void setItems(final List<T> items) {
		if (!AndroidUtilsUI.isUIThread()) {
			recyclerView.post(new Runnable() {
				@Override
				public void run() {
					if (AndroidUtils.DEBUG_ADAPTER) {
						Log.d(TAG, "setItems: delay");
					}
					setItems(items);
				}
			});
			return;
		}
		int oldCount;
		int newCount;
		synchronized (mLock) {
			oldCount = mItems.size();
			newCount = items.size();

			mItems = new ArrayList<>();
			mItems.clear();
			mItems.addAll(items);
		}
		if (selectedItem != null) {
			selectedPosition = mItems.indexOf(selectedItem);
		}

		if (AndroidUtils.DEBUG_ADAPTER) {
			Log.d(TAG,
					"setItems: oldCount=" + oldCount + ";new=" + newCount + ";" + this);
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
				Log.d(TAG, "setItems: remove from " + newCount + " size "
						+ (oldCount - newCount));
				Log.d(TAG, "setItems: change from 0, size " + newCount);
			}
		} else if (newCount > oldCount) {
			notifyItemRangeInserted(oldCount, newCount - oldCount);
			notifyItemRangeChanged(0, oldCount);
			if (AndroidUtils.DEBUG_ADAPTER) {
				Log.d(TAG, "setItems: insert from " + oldCount + " size "
						+ (newCount - oldCount));
				Log.d(TAG, "setItems: change 0 to " + oldCount);
			}
		} else {
			notifyDataSetInvalidated();
		}
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
			if (selectedItem != null) {
				selectedPosition = mItems.indexOf(selectedItem);
			}
		}

		recyclerView.post(new Runnable() {
			@Override
			public void run() {
				notifyDataSetInvalidated();
			}
		});
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
		int position = holder.getAdapterPosition();

		if (!mIsMultiSelectMode) {
			if (mAllowMultiSelectMode) {
				mIsMultiSelectMode = true;
			} else {
				if (getCheckedItemCount() > 0
						&& (getCheckedItemCount() > 1 || checkedItems.get(0) != position)) {
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
			Log.d(TAG, "onFocusChange: " + hasFocus + ";" + this + ";"
					+ AndroidUtils.getCompressedStackTrace());
		}
		final int position = holder.getAdapterPosition();
		if (hasFocus) {
			setItemSelected(position, holder);
		}

		// Check item on selection
		if (checkOnSelectedAfterMS >= 0) {
			if (runnableDelayedCheck != null) {
				v.getRootView().removeCallbacks(runnableDelayedCheck);
				runnableDelayedCheck = null;
			}

			boolean isChecked = checkedItems.contains(position);
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
				Log.d(TAG, "setItemSelected: Unselect previous position of "
						+ selectedPosition);
			}
			selectedHolder.itemView.setSelected(false);
			notifyItemChanged(selectedPosition);
		}
		selectedPosition = position;
		selectedItem = getItem(selectedPosition);
		holder.itemView.setSelected(selectedItem != null);

		if (AndroidUtils.DEBUG_ADAPTER) {
			Log.d(TAG, "setItemSelected: changed selected to " + selectedPosition);
		}

		if (selector != null) {
			boolean isChecked = checkedItems.contains(position);
			selector.onItemSelected(this, position, isChecked);
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
				setMultiCheckMode(false);
			}
		} else {
			checkedItems.add(position);
			nowChecked = true;
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "toggleItemChecked to " + nowChecked + " for " + position);
		}
		AndroidUtilsUI.setViewChecked(holder.itemView, nowChecked);

		notifyItemChanged(position);
		if (selector != null) {
			selector.onItemCheckedChanged(this, position, nowChecked);
		}

	}

	private void toggleItemChecked(RecyclerView.ViewHolder holder, boolean on) {
		Integer position = holder.getAdapterPosition();
		boolean alreadyChecked = checkedItems.contains(position);
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "toggleItemChecked to " + on + " for " + position + "; was "
					+ alreadyChecked);
		}
		if (on != alreadyChecked) {
			if (on) {
				checkedItems.add(position);
			} else {
				checkedItems.remove(position);
				if (checkedItems.size() == 0) {
					setMultiCheckMode(false);
				}
			}
			AndroidUtilsUI.setViewChecked(holder.itemView, on);
			notifyItemChanged(position);

			if (selector != null) {
				selector.onItemCheckedChanged(this, position, on);
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

	private void toggleItemChecked(Integer position, boolean notifySelector) {
		boolean checked;
		if (checkedItems.contains(position)) {
			checkedItems.remove(position);
			checked = false;
			if (checkedItems.size() == 0) {
				setMultiCheckMode(false);
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
			selector.onItemCheckedChanged(this, position, checked);
		}
	}

	// doesn't immediately update check state visually
	public void setItemChecked(Integer position, boolean checked) {
		if (position < 0) {
			return;
		}
		boolean alreadyChecked = checkedItems.contains(position);
		if (checked != alreadyChecked) {
			if (checked) {
				checkedItems.add(position);
			} else {
				checkedItems.remove(position);
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
				selector.onItemCheckedChanged(this, position, checked);
			}
		}
	}

	/**
	 * Unchecks all checked items
	 */
	public void clearChecked() {
		if (AndroidUtils.DEBUG_ADAPTER) {
			Log.d(TAG, "Clear " + checkedItems.size() + " checked via "
					+ AndroidUtils.getCompressedStackTrace(3));
		}
		Integer[] positions = checkedItems.toArray(
				new Integer[checkedItems.size()]);
		for (Integer item : positions) {
			toggleItemChecked(item, false);
			notifyItemChanged(item);
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
			mIsMultiSelectMode = false;
		}
		// TODO: clear multiple checks
	}

	public boolean isMultiCheckModeAllowed() {
		return mAllowMultiSelectMode;
	}
}
