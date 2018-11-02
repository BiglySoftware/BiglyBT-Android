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

import java.util.*;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.util.Thunk;

import android.annotation.SuppressLint;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.os.*;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.util.DiffUtil;
import android.support.v7.util.ListUpdateCallback;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

/**
 * This adapter requires only having one RecyclerView attached to it.
 *
 * @param <VH> ViewHolder class for an item
 * @param <T>  Data representation class of an item
 */
public abstract class FlexibleRecyclerAdapter<ADAPTERTYPE extends RecyclerView.Adapter<VH>, VH extends RecyclerView.ViewHolder, T extends Comparable<T>>
	extends RecyclerView.Adapter<VH>
	implements FlexibleRecyclerViewHolder.RecyclerSelectorInternal<VH>,
	LifecycleObserver
{
	private final String TAG;

	public static final int NO_CHECK_ON_SELECTED = -1;

	private static final String KEY_SUFFIX_CHECKED = ".checked";

	private static final String KEY_SUFFIX_SEL_POS = ".selPos";

	private static final String KEY_SUFFIX_FIRST_POS = ".firstPos";

	@Thunk
	static final long MAX_DIFFUTIL_MS = AndroidUtils.DEBUG ? 10000 : 800;

	@Thunk
	final Object mLock = new Object();

	// AS thinks "(ADAPTERTYPE) this" is a unchecked cast, when it obviously isn't
	// Instead of suppressing the warning everytime, we do it once here.
	@SuppressWarnings("unchecked")
	final ADAPTERTYPE thisAdapter = (ADAPTERTYPE) this;

	/** List of they keys of all entries displayed, in the display order */
	@Thunk
	List<T> mItems = new ArrayList<>();

	@Thunk
	int selectedPosition = -1;

	@Thunk
	T selectedItem;

	@Thunk
	final Lifecycle lifecycle;

	@Thunk
	FlexibleRecyclerSelectionListener<ADAPTERTYPE, VH, T> selector;

	private final List<T> checkedItems = new ArrayList<>();

	private boolean mIsMultiSelectMode;

	private int checkOnSelectedAfterMS = NO_CHECK_ON_SELECTED;

	@Thunk
	Runnable runnableDelayedCheck;

	@Thunk
	RecyclerView recyclerView;

	private boolean mAllowMultiSelectMode = true;

	private boolean mAlwaysMultiSelectMode = false;

	@Thunk
	View emptyView;

	private View initialView;

	private boolean neverSetItems = true;

	@Thunk
	long lastSetItemsOn;

	@Thunk
	SetItemsAsyncTask setItemsAsyncTask;

	@Thunk
	boolean skipDiffUtil;

	@Thunk
	SparseIntArray countsByViewType;

	private final List<OnSetItemsCompleteListener> listOnSetItemsCompleteListener = new ArrayList<>();

	private String classSimpleName;

	public interface OnSetItemsCompleteListener<AdapterType>
	{
		/** triggered when the items list has been updated, or when the number of items has changed */
		void onSetItemsComplete(AdapterType adapter);
	}

	private List<T> initialItems;

	private SparseIntArray initialCountsByViewType;

	private SetItemsCallBack<T> initialCallBack;

	private OnSetItemsCompleteListener<ADAPTERTYPE> setItemsCompleteListener;

	public FlexibleRecyclerAdapter(String TAG, Lifecycle lifecycle,
			FlexibleRecyclerSelectionListener<ADAPTERTYPE, VH, T> rs) {
		super();
		this.lifecycle = lifecycle;
		selector = rs;
		this.TAG = TAG;

		lifecycle.addObserver(this);
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
	public void onLifeCycleCreate() {
		if (initialItems != null) {
			setItems(initialItems, initialCountsByViewType, initialCallBack);
			initialItems = null;
			initialCountsByViewType = null;
			initialCallBack = null;
		}
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
	public void onLifeCycleDestroy() {
		// Note: ON_STOP may never get called
		// There's a case where it goes from ON_CREATED to ON_DESTROY
		if (setItemsAsyncTask != null) {
			if (AndroidUtils.DEBUG_ADAPTER && !setItemsAsyncTask.isCancelled()) {
				log(TAG, "onLifeCycleDestroy: cancel asyncTask");
			}
			setItemsAsyncTask.cancel(true);
		}
		selector = null;
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_STOP)
	public void onLifeCycleStop() {
		if (setItemsAsyncTask != null) {
			if (AndroidUtils.DEBUG_ADAPTER && !setItemsAsyncTask.isCancelled()) {
				log(TAG, "onLifeCycleStop: cancel asyncTask");
			}
			setItemsAsyncTask.cancel(true);
		}
	}

	public boolean isLifeCycleAtLeast(Lifecycle.State state) {
		return lifecycle.getCurrentState().isAtLeast(state);
	}

	@SuppressWarnings("WeakerAccess")
	public long getLastSetItemsOn() {
		return lastSetItemsOn;
	}

	@Override
	public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onAttachedToRecyclerView(recyclerView);
		if (this.recyclerView != null) {
			log(Log.ERROR, TAG,
					"Multiple RecyclerViews not allowed on Adapter " + this);
		}
		this.recyclerView = recyclerView;
	}

	@Override
	public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onDetachedFromRecyclerView(recyclerView);
		this.recyclerView = null;
	}

	public void notifyDataSetInvalidated() {
		int count = getItemCount();
		if (AndroidUtils.DEBUG_ADAPTER) {
			log(TAG, "setItems: invalidate all (" + count + ") "
					+ AndroidUtils.getCompressedStackTrace());
		}
		notifyItemRangeChanged(0, count);
		if (count == 0) {
			neverSetItems = false;
			checkEmpty();
		}
	}

	/**
	 * @return The positions of the checked items
	 */
	public int[] getCheckedItemPositions() {
		synchronized (mLock) {
			int[] positions = new int[checkedItems.size()];
			for (int i = 0; i < positions.length; i++) {
				positions[i] = getPositionForItem(checkedItems.get(i));
			}
			return positions;
		}
	}

	/**
	 * Returns a copy of checked items list
	 */
	public List<T> getCheckedItems() {
		return new ArrayList<>(checkedItems);
	}

	private void setCheckedPositions(@Nullable int[] positions) {
		// TODO: notify before clearing
		synchronized (mLock) {
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
	}

	/**
	 * Saves the state of the current selection on the items.
	 *
	 * @param outState Current state
	 */
	public void onSaveInstanceState(Bundle outState) {
		outState.putIntArray(TAG + KEY_SUFFIX_CHECKED, getCheckedItemPositions());
		outState.putInt(TAG + KEY_SUFFIX_SEL_POS, selectedPosition);
		if (recyclerView instanceof FlexibleRecyclerView) {
			int pos = ((FlexibleRecyclerView) recyclerView).findFirstVisibleItemPosition();
			if (pos >= 0) {
				outState.putInt(TAG + KEY_SUFFIX_FIRST_POS, pos);
			}
		}
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

		int[] checkedPositions = savedInstanceState.getIntArray(
				TAG + KEY_SUFFIX_CHECKED);
		setCheckedPositions(checkedPositions);
		selectedPosition = savedInstanceState.getInt(TAG + KEY_SUFFIX_SEL_POS, -1);
		if (selectedPosition >= 0) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG, "onRestoreInstanceState: scroll to #" + selectedPosition);
			}
			selectedItem = getItem(selectedPosition);
			rv.scrollToPosition(selectedPosition);
		} else {
			int firstPosition = savedInstanceState.getInt(TAG + KEY_SUFFIX_FIRST_POS,
					-1);
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG, "onRestoreInstanceState: scroll to first, #" + firstPosition);
			}
			rv.scrollToPosition(firstPosition);
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
		// Direct comparison failed, maybe item is in list, but as a different
		// object
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
	@SuppressWarnings("unused")
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

	//int countC = 0; // For tracking/debuging if we are creating too many
	// holders
	//int countB = 0; // instead of just rebinding them

	@NonNull
	@Override
	public final VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		//log(TAG, "onCreateViewHolder: " + (++countC));
		return onCreateFlexibleViewHolder(parent, viewType);
	}

	@Override
	public final void onBindViewHolder(@NonNull VH holder, int position,
			@NonNull List<Object> payloads) {
		super.onBindViewHolder(holder, position, payloads);
		//log(TAG, "onBindViewHolder: " + (++countB));
		onBindFlexibleViewHolder(holder, position);
	}

	public abstract VH onCreateFlexibleViewHolder(ViewGroup parent, int viewType);

	@SuppressWarnings("ResourceType")
	@Override
	public final void onBindViewHolder(@NonNull VH holder, int position) {
		onBindFlexibleViewHolder(holder, position);

		RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
		if (layoutManager instanceof PreCachingLayoutManager) {
			int fixedVerticalHeight = ((PreCachingLayoutManager) layoutManager).getFixedVerticalHeight();
			if (fixedVerticalHeight > 0) {
				// Torrent List goes to bottom of TV screen, past the overscan area
				// Adjust last item to have overscan gap, to ensure user can view
				// the whole row

				// Setting bottomMargin on itemView doesn't work on FireTV
				// Try layoutRow instead.  The side affect is that we will be extending
				// the selector state color to the bottom of the screen, which doesn't
				// look great
				//View v = holder.layoutRow == null ? holder.itemView : holder
				// .layoutRow;
				View v = holder.itemView;
				ViewGroup.LayoutParams lp = v.getLayoutParams();
				int paddingBottom = position + 1 == getItemCount() ? fixedVerticalHeight
						: 0;

				if (lp instanceof RecyclerView.LayoutParams) {
					((RecyclerView.LayoutParams) lp).bottomMargin = paddingBottom;
				} else if (lp instanceof RelativeLayout.LayoutParams) {
					((RelativeLayout.LayoutParams) lp).bottomMargin = paddingBottom;
				} else if (lp instanceof FrameLayout.LayoutParams) {
					// shouldn't happen, but this is the layout param type for the row
					((FrameLayout.LayoutParams) lp).bottomMargin = paddingBottom;
				}
				v.requestLayout();

			}
		}
		boolean checked = isItemChecked(position);
		boolean selected = isItemSelected(position);
		holder.itemView.setSelected(selected);
		AndroidUtilsUI.setViewChecked(holder.itemView, checked);
	}

	public abstract void onBindFlexibleViewHolder(VH holder, final int position);

	@Override
	public int getItemCount() {
		return mItems.size();
	}

	/**
	 * Returns the total number of items in the data set held by the adapter for
	 * a specific viewType.
	 * 
	 * @param viewType The view type to count
	 * @return The total number of items of the given view type in this adapter
	 */
	public int getItemCount(int viewType) {
		if (countsByViewType == null || countsByViewType.size() == 0) {
			// TODO: We could walk mItems and build
			return mItems.size();
		}
		int i = countsByViewType.get(viewType, -1);
		if (i >= 0) {
			return i;
		}
		return mItems.size();
	}

	@SuppressWarnings("unused")
	public boolean isEmpty() {
		return getItemCount() == 0;
	}

	@SuppressWarnings({
		"WeakerAccess",
		"unused"
	})
	public void updateItem(final int position, final T item) {
		if (!AndroidUtilsUI.isUIThread()) {
			new Handler(Looper.getMainLooper()).post(
					() -> updateItem(position, item));
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
			log(Log.VERBOSE, TAG, "updateItem: " + position);
		}
		notifyItemChanged(position);
	}

	@SafeVarargs
	public final void addItem(final T... items) {
		if (!AndroidUtilsUI.isUIThread()) {
			new Handler(Looper.getMainLooper()).post(() -> {
				if (AndroidUtils.DEBUG_ADAPTER) {
					log(TAG, "addItem: delayed");
				}
				addItem(items);
			});
			return;
		}
		int position;
		int count = 0;
		synchronized (mLock) {
			position = mItems.size();
			for (T item : items) {
				mItems.add(item);
				count++;
			}
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			log(TAG, "addItem: " + position);
		}
		notifyItemRangeInserted(position, count);
		triggerOnSetItemsCompleteListeners();
	}

	/**
	 * Insert given Item at position or Add Item at last position.
	 *
	 * @param position Position of the item to add
	 * @param item     The item to add
	 */
	@SuppressWarnings({
		"WeakerAccess",
		"unused"
	})
	public void addItem(int position, final T item) {
		if (!AndroidUtilsUI.isUIThread()) {
			final int finalPosition = position;
			new Handler(Looper.getMainLooper()).post(() -> {
				if (AndroidUtils.DEBUG_ADAPTER) {
					log(TAG, "addItem: delayed. " + finalPosition);
				}
				addItem(finalPosition, item);
			});
			return;
		}
		if (position < 0) {
			log(Log.WARN, TAG, "Cannot addItem on negative position");
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
			log(TAG, "addItem: " + position);
		}
		notifyItemInserted(position);
		triggerOnSetItemsCompleteListeners();
	}

	@SuppressWarnings({
		"WeakerAccess",
		"unused"
	})
	public void removeItem(final int position) {
		if (position < 0) {
			return;
		}

		if (!AndroidUtilsUI.isUIThread()) {
			new Handler(Looper.getMainLooper()).post(() -> removeItem(position));
			return;
		}

		T itemRemoved;
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
				selector.onItemSelected(thisAdapter, selectedPosition,
						isItemChecked(selectedItem));
			}
		}

		synchronized (mLock) {
			if (checkedItems.size() > 0) {
				boolean removed = checkedItems.remove(itemRemoved);
				if (removed && selector != null) {
					selector.onItemCheckedChanged(thisAdapter, itemRemoved, false);
				}
			}
		}

		notifyItemRangeRemoved(position, 1);
		triggerOnSetItemsCompleteListeners();
	}

	public void removeItems(final int position, final int count) {
		if (position < 0 || count <= 0) {
			return;
		}

		if (!AndroidUtilsUI.isUIThread()) {
			new Handler(Looper.getMainLooper()).post(
					() -> removeItems(position, count));
			return;
		}

		int numRemoved;
		long now = System.currentTimeMillis();
		synchronized (mLock) {
			List<T> toClear = mItems.subList(position, position + count);
			if (selector != null) {
				for (T itemRemoving : toClear) {
					if (checkedItems.remove(itemRemoving)) {
						selector.onItemCheckedChanged(thisAdapter, itemRemoving, false);
					}
				}
			}
			numRemoved = toClear.size();
			toClear.clear();
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			long diff = System.currentTimeMillis() - now;
			log(TAG, "remove " + count + " starting at " + position + " took " + diff
					+ "ms");
		}

		if (selectedPosition >= position
				&& selectedPosition < position + numRemoved) {
			selectedItem = null;
			selectedPosition = -1;
		} else if (selectedPosition > position) {
			selectedPosition -= numRemoved;
			if (selector != null && selectedItem != null) {
				selector.onItemSelected(thisAdapter, selectedPosition,
						isItemChecked(selectedItem));
			}
		}

		notifyItemRangeRemoved(position, numRemoved);
		triggerOnSetItemsCompleteListeners();
	}

	public void removeAllItems() {
		if (AndroidUtilsUI.runIfNotUIThread(this::removeAllItems)) {
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
		synchronized (mLock) {
			if (checkedItems.size() > 0) {
				if (selector != null) {
					for (T checkedItem : checkedItems) {
						selector.onItemCheckedChanged(thisAdapter, checkedItem, false);
					}
				}
				checkedItems.clear();
			}
			countsByViewType = new SparseIntArray(0);
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			log(TAG, "removeAllItems: " + count);
		}
		if (count > 0) {
			notifyItemRangeRemoved(0, count);
		}
		triggerOnSetItemsCompleteListeners();
	}

	public interface SetItemsCallBack<T>
	{
		/**
		 * Called by the DiffUtil when it wants to check whether two items have the same data.
		 * DiffUtil uses this information to detect if the contents of an item has changed.
		 * <p>
		 * DiffUtil uses this method to check equality instead of {@link Object#equals(Object)}
		 * so that you can change its behavior depending on your UI.
		 * For example, if you are using DiffUtil with a
		 * {@link android.support.v7.widget.RecyclerView.Adapter RecyclerView.Adapter}, you should
		 * return whether the items' visual representations are the same.
		 * <p>
		 * This method is called only if {@link android.support.v7.util.DiffUtil.Callback#areItemsTheSame(int, int)} returns
		 * {@code true} for these items.
		 *
		 * @param oldItem The item in the old list
		 * @param newItem The item in the new list which replaces the oldItem
		 * @return True if the contents of the items are the same or false if they are different.
		 */
		boolean areContentsTheSame(T oldItem, T newItem);
	}

	private class SetItemsAsyncTask
		extends AsyncTask<Void, Void, Void>
	{
		private final ADAPTERTYPE adapter;

		@Thunk
		final List<T> newItems;

		@Thunk
		final SetItemsCallBack<T> callback;

		@Thunk
		DiffUtil.DiffResult diffResult;

		@Thunk
		List<T> notifyUncheckedList;

		private boolean complete = false;

		SetItemsAsyncTask(ADAPTERTYPE adapter, List<T> items,
				final SetItemsCallBack<T> callback) {
			this.adapter = adapter;
			this.newItems = items;

			this.callback = callback;
		}

		@Override
		protected Void doInBackground(Void... params) {
			long start = 0;
			if (AndroidUtils.DEBUG_ADAPTER) {
				start = System.currentTimeMillis();
				log(TAG, "SetItemsAsyncTask: " + newItems.size() + "/" + callback);
			}

			int oldCount;
			int newCount;
			final List<T> oldItems;
			synchronized (mLock) {
				oldItems = new ArrayList<>(mItems);
				if (isCancelled() || !lifecycle.getCurrentState().isAtLeast(
						Lifecycle.State.CREATED)) {
					// Cancel check here, because onItemListChanging might do something
					// assuming everything is in a good state (like the fragment still
					// being attached)
					if (AndroidUtils.DEBUG_ADAPTER) {
						log(TAG, "SetItemsAsyncTask: skip. cancelled? " + isCancelled());
					}
					return null;
				}

				oldCount = oldItems.size();
				newCount = newItems.size();
			}

			diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
				@Override
				public int getOldListSize() {
					return oldItems.size();
				}

				@Override
				public int getNewListSize() {
					return newItems.size();
				}

				@Override
				public boolean areItemsTheSame(int oldItemPosition,
						int newItemPosition) {
					// oldItems.get(oldItemPosition).compareTo(items.get(newItemPosition)) == 0
					// is slower than the code below
					T oldItem = oldItems.get(oldItemPosition);
					T newItem = newItems.get(newItemPosition);
					return oldItem.compareTo(newItem) == 0;
				}

				@Override
				public boolean areContentsTheSame(int oldItemPosition,
						int newItemPosition) {
					return callback.areContentsTheSame(oldItems.get(oldItemPosition),
							newItems.get(newItemPosition));
				}
			});

			if (isCancelled()) {
				if (AndroidUtils.DEBUG_ADAPTER) {
					log(TAG, "SetItemsAsyncTask CANCELLED " + this + " after "
							+ (System.currentTimeMillis() - start) + "ms");
				}
				return null;
			}

			synchronized (mLock) {
				mItems = newItems;

				if (selectedItem != null) {
					// relink, since we may have a new object with the same stableId
					selectedPosition = getPositionForItem(selectedItem);
					selectedItem = getItem(selectedPosition);
				}

				notifyUncheckedList = relinkCheckedItems();
			}

			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG,
						"SetItemsAsyncTask: oldCount=" + oldCount + ";new=" + newCount + ";"
								+ this + " in " + (System.currentTimeMillis() - start) + "ms");

				diffResult.dispatchUpdatesTo(new ListUpdateCallback() {
					@Override
					public void onInserted(int position, int count) {
						log(TAG, "-->Insert " + count + " at " + position);
					}

					@Override
					public void onRemoved(int position, int count) {
						log(TAG, "-->Remove " + count + " at " + position);
					}

					@Override
					public void onMoved(int fromPosition, int toPosition) {
						log(TAG, "-->move " + fromPosition + " to " + toPosition);
					}

					@Override
					public void onChanged(int position, int count, Object payload) {
						T t = position < newItems.size() ? newItems.get(position) : null;
						log(TAG, "-->Change " + count + " at " + position + "; " + t);
					}
				});
			}

			complete = true;
			return null;
		}

		@Override
		protected void onPostExecute(Void aVoid) {
			if (selector != null) {
				for (T item : notifyUncheckedList) {
					selector.onItemCheckedChanged(adapter, item, false);
				}
			}

			if (recyclerView != null) {
				boolean isAtTop = recyclerView.computeVerticalScrollOffset() == 0;
				diffResult.dispatchUpdatesTo(adapter);
				if (isAtTop) {
					// it's really confusing when you are at the top, flip sort,
					// and nothing changes (the scrollbar does, but who notices that?)
					recyclerView.scrollToPosition(0);
				}
			}

			lastSetItemsOn = System.currentTimeMillis();
			triggerOnSetItemsCompleteListeners();
		}

		@SuppressWarnings("MethodDoesntCallSuperMethod")
		@Override
		protected void onCancelled(Void aVoid) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG, this + " onCancelled. Complete? " + complete + " via "
						+ AndroidUtils.getCompressedStackTrace());
			}

			if (complete) {
				// oops, doInBackground actually completed, we better fire off the
				// diffResult
				onPostExecute(aVoid);
			}
		}

		public boolean isComplete() {
			return complete || isCancelled();
		}

	}

	/**
	 *
	 * @return If items were set immediately.  False if items will be set async
	 */
	public boolean setItems(final List<T> items, SparseIntArray countsByViewType,
			SetItemsCallBack<T> callback) {
		if (!lifecycle.getCurrentState().isAtLeast(Lifecycle.State.CREATED)) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG,
						"setItems cancelled; not attached. " + lifecycle.getCurrentState());
			}
			initialItems = items;
			initialCountsByViewType = countsByViewType;
			initialCallBack = callback;
			return true;
		} else {
			this.countsByViewType = countsByViewType;
		}
		neverSetItems = false;

		if (skipDiffUtil) {
			setItems_noDiffUtil(items);
			return true;
		}

		if (setItemsAsyncTask != null && !setItemsAsyncTask.isComplete()
				&& !setItemsAsyncTask.isCancelled()) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG,
						"cancel old setItemsAsyncTask. already? "
								+ setItemsAsyncTask.isCancelled() + " c?"
								+ setItemsAsyncTask.isComplete() + " via "
								+ AndroidUtils.getCompressedStackTrace());
			}
			setItemsAsyncTask.cancel(true);
		}
		setItemsAsyncTask = new SetItemsAsyncTask(thisAdapter, items, callback);
		final SetItemsAsyncTask ourTask = setItemsAsyncTask;
		final List<T> oldItems = mItems;
		try {
			setItemsAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		} catch (IllegalStateException ex) {
			setItems_noDiffUtil(items);
			return true;
		}

		new Thread(() -> {

			try {
				Thread.sleep(MAX_DIFFUTIL_MS);
			} catch (InterruptedException ignored) {
			}

			if (ourTask != setItemsAsyncTask || ourTask.isComplete()
					|| oldItems != mItems) {
				return;
			}

			// Taking too long, cancel and turn off diff
			ourTask.cancel(true);

			skipDiffUtil = true;
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG, "Turning off DiffUtil");
			}
			setItems_noDiffUtil(items);
		}, "setItems").start();
		return false;
	}

	@Thunk
	void setItems_noDiffUtil(final List<T> items) {
		neverSetItems = false;
		if (!AndroidUtilsUI.isUIThread()) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG, "setItems: delay " + recyclerView);
			}
			new Handler(Looper.getMainLooper()).postAtFrontOfQueue(
					() -> setItems_noDiffUtil(items));
			return;
		}

		if (AndroidUtils.DEBUG_ADAPTER) {
			log(TAG, "setItems via skipDiff");
		}

		List<T> notifyUncheckedList;
		int oldCount;
		int newCount;
		synchronized (mLock) {
			oldCount = mItems.size();
			newCount = items.size();

			mItems = items;

			if (selectedItem != null) {
				// relink, since we may have a new object with the same stableId
				selectedPosition = getPositionForItem(selectedItem);
				selectedItem = getItem(selectedPosition);
			}

			notifyUncheckedList = relinkCheckedItems();
		}

		if (selector != null) {
			for (T item : notifyUncheckedList) {
				selector.onItemCheckedChanged(thisAdapter, item, false);
			}
		}

		if (AndroidUtils.DEBUG_ADAPTER) {
			log(TAG,
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
				log(TAG, "setItems: remove from " + newCount + " size "
						+ (oldCount - newCount));
				log(TAG, "setItems: change from 0, size " + newCount);
			}
		} else if (newCount > oldCount) {
			notifyItemRangeInserted(oldCount, newCount - oldCount);
			if (oldCount != 0) {
				notifyItemRangeChanged(0, oldCount);
			}
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG, "setItems: insert from " + oldCount + " size "
						+ (newCount - oldCount));
				if (oldCount != 0) {
					log(TAG, "setItems: change 0 to " + oldCount);
				}
			}
		} else {
			notifyDataSetInvalidated();
		}

		triggerOnSetItemsCompleteListeners();
	}

	protected void triggerOnSetItemsCompleteListeners() {
		OnSetItemsCompleteListener[] listeners = listOnSetItemsCompleteListener.toArray(
				new OnSetItemsCompleteListener[0]);
		for (OnSetItemsCompleteListener listener : listeners) {
			listener.onSetItemsComplete(this);
		}
	}

	@Thunk
	List<T> relinkCheckedItems() {
		synchronized (mLock) {
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
	}

	///////////////////////
	// Selection Functions
	///////////////////////

	@Override
	public void onItemClick(VH holder, View view) {
		int position = holder.getLayoutPosition();
		if (!isItemCheckable(position)) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG, "Skip setting item checked: not checkable for " + position);
			}
			return;
		}
		boolean alreadyChecked = isItemChecked(position);
		// clear previous selection when not in multimode
		synchronized (mLock) {
			if (!mIsMultiSelectMode && getCheckedItemCount() > 0) {
				if (getCheckedItemCount() > 1
						|| getPositionForItem(checkedItems.get(0)) != position) {
					clearChecked();
				}
			}
		}

		setItemSelected(position, holder);

		if (mIsMultiSelectMode || !alreadyChecked) {
			toggleItemChecked(holder);
		}

		if (selector != null) {
			selector.onItemClick(thisAdapter, position);
		}
	}

	@Override
	public boolean onItemLongClick(VH holder, View view) {
		int position = holder.getLayoutPosition();

		if (!mIsMultiSelectMode) {
			if (mAllowMultiSelectMode) {
				mIsMultiSelectMode = true;
			} else {
				synchronized (mLock) {
					if (getCheckedItemCount() > 0 && (getCheckedItemCount() > 1
							|| getPositionForItem(checkedItems.get(0)) != position)) {
						clearChecked();
					}
				}
			}
		}
		setItemSelected(position, holder);

		if (selector != null) {
			if (selector.onItemLongClick(thisAdapter, position)) {
				return true;
			}
		}

		// Only toggle checked if selector didn't handle it
		toggleItemChecked(holder, true);

		return true;
	}

	@SuppressWarnings("WeakerAccess")
	public boolean isItemSelected(int position) {
		return position != -1 && position == selectedPosition;
	}

	@Override
	public void onFocusChange(VH holder, View v, boolean hasFocus) {
		if (AndroidUtils.DEBUG_ADAPTER && AndroidUtils.DEBUG_ANNOY) {
			log(TAG, "onFocusChange: " + hasFocus + ";" + this + ";"
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
			if (mIsMultiSelectMode || isChecked) {
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
				log(TAG, "setItemSelected: Unselect previous position of "
						+ selectedPosition);
			}
			selectedHolder.itemView.setSelected(false);
			notifyItemChanged(selectedPosition);
		}
		selectedPosition = position;
		selectedItem = getItem(selectedPosition);
		holder.itemView.setSelected(selectedItem != null);

		if (AndroidUtils.DEBUG_ADAPTER) {
			log(TAG, "setItemSelected: changed selected to " + selectedPosition);
		}

		if (selector != null) {
			selector.onItemSelected(thisAdapter, position, isItemChecked(position));
		}
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isItemCheckable(int position) {
		return true;
	}

	public boolean isItemChecked(int position) {
		synchronized (mLock) {
			return checkedItems.contains(getItem(position));
		}
	}

	private boolean isItemChecked(T item) {
		synchronized (mLock) {
			return checkedItems.contains(item);
		}
	}

	private void toggleItemChecked(RecyclerView.ViewHolder holder) {
		Integer position = holder.getLayoutPosition();
		boolean nowChecked;
		T item = getItem(position);
		if (item == null) {
			return;
		}
		if (!isItemCheckable(position)) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG, "Skip toggleItemChecked: not checkable for " + position);
			}
			return;
		}
		synchronized (mLock) {
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
		}
		if (AndroidUtils.DEBUG) {
			log(TAG, "toggleItemChecked to " + nowChecked + " for " + position);
		}
		AndroidUtilsUI.setViewChecked(holder.itemView, nowChecked);

		notifyItemChanged(position);
		if (selector != null) {
			selector.onItemCheckedChanged(thisAdapter, item, nowChecked);
		}

	}

	private void toggleItemChecked(RecyclerView.ViewHolder holder,
			@SuppressWarnings("SameParameterValue") boolean on) {
		Integer position = holder.getLayoutPosition();
		T item = getItem(position);
		boolean alreadyChecked;
		synchronized (mLock) {
			alreadyChecked = checkedItems.contains(item);
		}
		if (AndroidUtils.DEBUG) {
			log(TAG, "toggleItemChecked to " + on + " for " + position + "; was "
					+ alreadyChecked);
		}
		if (on != alreadyChecked) {
			synchronized (mLock) {
				if (on) {
					checkedItems.add(item);
				} else {
					checkedItems.remove(item);
					if (checkedItems.size() == 0) {
						setMultiCheckMode(false);
					}
				}
			}
			AndroidUtilsUI.setViewChecked(holder.itemView, on);
			notifyItemChanged(position);

			if (selector != null) {
				selector.onItemCheckedChanged(thisAdapter, item, on);
			}
		}
	}

	/**
	 * Flips the checked state of an item
	 *
	 * @param position The position of the item to flip the state of
	 */
	// doesn't immediately update check state visually
	@SuppressWarnings("unused")
	public void toggleItemChecked(int position) {
		toggleItemChecked(position, true);
	}

	private void toggleItemChecked(Integer position, boolean notifySelector) {
		boolean checked;
		T item = getItem(position);
		synchronized (mLock) {
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
		}

		if (AndroidUtils.DEBUG) {
			log(TAG, "toggleItemChecked to " + checked + " for " + position + ";"
					+ AndroidUtils.getCompressedStackTrace(8));
		}
		notifyItemChanged(position);

		if (selector != null && notifySelector) {
			selector.onItemCheckedChanged(thisAdapter, item, checked);
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
		boolean alreadyChecked;
		synchronized (mLock) {
			alreadyChecked = checkedItems.contains(item);
		}
		if (checked != alreadyChecked) {
			synchronized (mLock) {
				if (checked) {
					checkedItems.add(item);
				} else {
					checkedItems.remove(item);
					if (checkedItems.size() == 0) {
						setMultiCheckMode(false);
					}
				}
			}
			if (AndroidUtils.DEBUG) {
				log(TAG, "setItemChecked to " + checked + " for " + position + "; was "
						+ alreadyChecked + ";" + AndroidUtils.getCompressedStackTrace(4));
			}

			notifyItemChanged(position);
			if (selector != null) {
				selector.onItemCheckedChanged(thisAdapter, item, checked);
			}
		}
	}

	/**
	 * Unchecks all checked items
	 */
	public void clearChecked() {
		if (AndroidUtils.DEBUG_ADAPTER) {
			log(TAG, "Clear " + checkedItems.size() + " checked via "
					+ AndroidUtils.getCompressedStackTrace(4));
		}
		ArrayList<T> checkedItemsCopy = new ArrayList<>(checkedItems);
		for (T checkedItem : checkedItemsCopy) {
			int position = getPositionForItem(checkedItem);
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
		if (mAlwaysMultiSelectMode && !on) {
			return;
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			log(TAG, "setMultiCheckMode " + on + "; "
					+ AndroidUtils.getCompressedStackTrace(4));
		}
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
	@SuppressWarnings("unused")
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

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isMultiCheckModeAllowed() {
		return mAllowMultiSelectMode;
	}

	@SuppressLint("LogConditional")
	public void log(String TAG, String s) {
		if (classSimpleName == null) {
			classSimpleName = AndroidUtils.getSimpleName(getClass()) + "@"
					+ Integer.toHexString(hashCode());
		}
		Log.d(classSimpleName, TAG + ": " + s);
	}

	@SuppressLint("LogConditional")
	public void log(int prority, String TAG, String s) {
		if (classSimpleName == null) {
			classSimpleName = AndroidUtils.getSimpleName(getClass()) + "@"
					+ Integer.toHexString(hashCode());
		}
		Log.println(prority, classSimpleName, TAG + ": " + s);
	}

	@SuppressWarnings("unused")
	public boolean isAlwaysMultiSelectMode() {
		return mAlwaysMultiSelectMode;
	}

	public void setAlwaysMultiSelectMode(boolean mAlwaysMultiSelectMode) {
		this.mAlwaysMultiSelectMode = mAlwaysMultiSelectMode;
	}

	public void setEmptyView(View _initialView, View _emptyView) {
		this.emptyView = _emptyView;
		this.initialView = _initialView;

		if (emptyView == null) {
			if (setItemsCompleteListener != null) {
				removeOnSetItemsCompleteListener(setItemsCompleteListener);
				setItemsCompleteListener = null;
			}
		} else {
			setItemsCompleteListener = adapter -> checkEmpty();
			addOnSetItemsCompleteListener(setItemsCompleteListener);
		}

		if (neverSetItems && initialView != null) {
			initialView.setVisibility(View.VISIBLE);
			View view = initialView.findViewById(R.id.wait_logo);
			if (view != null) {
				Animation animation = new AlphaAnimation(0.1f, 1f);
				animation.setInterpolator(new LinearInterpolator());
				animation.setDuration(1500);

				animation.setRepeatMode(Animation.REVERSE);
				animation.setRepeatCount(Animation.INFINITE);
				view.startAnimation(animation);
			}
		} else {
			checkEmpty();
		}
	}

	public boolean isNeverSetItems() {
		return neverSetItems;
	}

	public void triggerEmptyList() {
		neverSetItems = false;
		checkEmpty();
	}

	@Thunk
	void checkEmpty() {
		if (!AndroidUtilsUI.isUIThread()) {
			recyclerView.post(this::checkEmpty);
			return;
		}
		if (initialView != null && initialView.getVisibility() == View.VISIBLE) {
			if (AndroidUtils.DEBUG) {
				log(TAG, "checkEmpty: setInitialView gone "
						+ AndroidUtils.getCompressedStackTrace());
			}
			initialView.setVisibility(View.GONE);

			View view = initialView.findViewById(R.id.wait_logo);
			if (view != null) {
				view.setAnimation(null);
			}
		}
		if (emptyView == null || recyclerView == null) {
			return;
		}
		boolean shouldShowEmptyView = getItemCount() == 0;
		boolean showingEmptyView = emptyView.getVisibility() == View.VISIBLE;
		if (showingEmptyView != shouldShowEmptyView) {
			if (AndroidUtils.DEBUG) {
				log(TAG, "checkEmpty: swith showEmptyView to " + shouldShowEmptyView
						+ "; " + AndroidUtils.getCompressedStackTrace());
			}
			emptyView.setVisibility(shouldShowEmptyView ? View.VISIBLE : View.GONE);
			recyclerView.setVisibility(
					shouldShowEmptyView ? View.GONE : View.VISIBLE);
		}
	}

	public void addOnSetItemsCompleteListener(
			OnSetItemsCompleteListener<ADAPTERTYPE> l) {
		if (!listOnSetItemsCompleteListener.contains(l)) {
			listOnSetItemsCompleteListener.add(l);
		}
	}

	public void removeOnSetItemsCompleteListener(
			OnSetItemsCompleteListener<ADAPTERTYPE> l) {
		listOnSetItemsCompleteListener.remove(l);
	}

	protected List<T> getAllItems() {
		return mItems;
	}
}
