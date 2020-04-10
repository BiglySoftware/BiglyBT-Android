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

package com.biglybt.android.client.sidelist;

import android.animation.Animator;
import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.*;
import android.view.View.OnLayoutChangeListener;
import android.view.animation.*;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.*;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.SubMenuBuilder;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.biglybt.android.adapter.*;
import com.biglybt.android.adapter.DelayedFilter.PerformingFilteringListener;
import com.biglybt.android.adapter.FlexibleRecyclerAdapter.OnSetItemsCompleteListener;
import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.DrawerActivity;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.sidelist.SideTextFilterAdapter.SideTextFilterInfo;
import com.biglybt.android.util.AnimatorEndListener;
import com.biglybt.android.util.OnSwipeTouchListener;
import com.biglybt.android.widget.FlingLinearLayout;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.util.Thunk;

import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * Builds and manages a side list consisting of expandable groups. Provides
 * and handles common sort and text filters sections.
 *
 * Created by TuxPaper on 6/14/16.
 */
public class SideListHelper<ADAPTERITEM extends Comparable<ADAPTERITEM>>
	implements OnSetItemsCompleteListener<FlexibleRecyclerAdapter>,
	LettersUpdatedListener, DefaultLifecycleObserver, PerformingFilteringListener,
	OnLayoutChangeListener
{
	private static final String TAG = "SideListHelper";

	private static final String ENTRY_SORT = "sort";

	private static final String ENTRY_FILTER = "filter";

	private static final String ENTRY_TEXTFILTER = "textfilter";

	private static final String ENTRY_ACTION = "action";

	private final SideListHelperListener sideListHelperListener;

	private Lifecycle lifecycle;

	@NonNull
	@Thunk
	final FragmentActivity activity;

	private final View parentView;

	private boolean isInDrawer;

	private FlexibleRecyclerAdapter mainAdapter;

	@Thunk
	SortableAdapter<ADAPTERITEM> sortableAdapter;

	@Thunk
	final SessionGetter sessionGetter;

	@Thunk
	OnSwipeTouchListener expandTouchListener;

	@Thunk
	LinearLayout sideListArea;

	@Thunk
	Boolean sidelistIsExpanded = null;

	@Thunk
	boolean inManualExpandState = false;

	@Thunk
	Boolean sidelistInFocus = null;

	@Thunk
	SideListEntry activeEntry = null;

	@Thunk
	boolean hideUnselectedSideHeaders = false;

	@Thunk
	final Map<String, SideListEntry> mapEntries = new HashMap<>();

	private int collapseUntilWidthPx;

	private int keepExpandedAtParentWidthDp;

	private int collapsedWidthPx = -1;

	private int maxWidthPx;

	private int lastWidth = -1;

	// Rare case when there's not enough height.  Show only active sidelist
	// header
	// This would be for Dell Streak (800x480dp) if it was API >= 13
	// Can't be >= 540, since TVs are that.
	// For reference, TorrentActivity's SideList:
	// Each row is 42dp.  42x4=168, plus top actionbar (64dp?) and our header
	// (20dp?) ~ 252 dp.  Want to show at least 5 rows of the list.  5x42=210+252=462
	@Thunk
	final static int SIDELIST_HIDE_UNSELECTED_HEADERS_UNTIL_DP = 462;

	private final static int SIDELIST_DURATION_MS = 300;

	private Animation.AnimationListener animationListener;

	// >> SideSort
	@Thunk
	TextView tvSortCurrent;

	@Thunk
	SideSortAdapter sideSortAdapter;

	// << SideSort

	// >> SideTextFilter
	@Thunk
	TextView tvSideFilterText;

	@Thunk
	SideTextFilterAdapter sideTextFilterAdapter;

	private String ourSideFilterText;

	// << SideTextFilter

	// >> SideActions
	private SideActionsAdapter sideActionsAdapter;

	@Thunk
	private BufferedTextWatcher sideTextFilterWatcher = new BufferedTextWatcher();

	@Thunk
	boolean sortInProgress;

	private TextView tvFilterCurrent;

	private Fragment forFragment;

	private boolean isResizing;

	private boolean needsReset;

	// << SideActions

	@SuppressLint("ClickableViewAccessibility")
	public SideListHelper(SideListHelperListener sideListHelperListener,
			@NonNull FragmentActivity activity, Fragment controllingFragment,
			@IdRes int sideListAreaID, SessionGetter sessionGetter) {
		this.sideListHelperListener = sideListHelperListener;
		this.activity = activity;
		this.parentView = AndroidUtilsUI.requireContentView(activity);
		this.sessionGetter = sessionGetter;

		isInDrawer = false;
		if (activity instanceof DrawerActivity) {
			DrawerActivity drawerActivity = (DrawerActivity) activity;
			DrawerLayout drawerLayout = drawerActivity.getDrawerLayout();
			if (drawerLayout != null) {
				View viewInDrawer = drawerLayout.findViewById(sideListAreaID);
				if (viewInDrawer != null) {
					View viewInActivity = activity.findViewById(sideListAreaID);
					isInDrawer = viewInActivity == null || viewInDrawer == viewInActivity;
					if (isInDrawer) {
						View drawerView = drawerActivity.getDrawerView();
						if (drawerView != null) {
							maxWidthPx = drawerView.getLayoutParams().width;
						}
						collapsedWidthPx = 0;
					}
				}
			}
		}

		sideListArea = parentView.findViewById(sideListAreaID);
		if (sideListArea != null) {
			if (!isInDrawer || maxWidthPx == 0) {
				maxWidthPx = sideListArea.getLayoutParams().width;
				if (maxWidthPx == 0) {
					maxWidthPx = activity.getResources().getDimensionPixelSize(
							R.dimen.sidelist_max_width);
				}
			}
			if (!isInDrawer || collapsedWidthPx == 0) {
				collapsedWidthPx = ViewCompat.getMinimumWidth(sideListArea);
				if (collapsedWidthPx == 0) {
					collapsedWidthPx = activity.getResources().getDimensionPixelSize(
							R.dimen.sidelist_min_width);
				}
			}
			if (!AndroidUtils.hasTouchScreen()) {
				// Switch SideList width based on focus.  For touch screens, we use
				// touch events.  For non-touch screens (TV) we watch for focus changes
				ViewTreeObserver vto = sideListArea.getViewTreeObserver();
				vto.addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {

					boolean isChildOfSideList = AndroidUtilsUI.isChildOf(newFocus,
							sideListArea);
					boolean isHeader = AndroidUtilsUI.childOrParentHasTag(newFocus,
							"sideheader");
					if ((sidelistIsExpanded == null || sidelistIsExpanded)
							&& !isChildOfSideList) {
						//left focus
						sidelistInFocus = false;
						expandSideListWidth(false, false);
					} else if ((sidelistIsExpanded == null || !sidelistIsExpanded)
							&& isHeader) {
						sidelistInFocus = true;
						expandSideListWidth(true, false);
					}
				});
			}

			expandTouchListener = new OnSwipeTouchListener(activity) {

				@Override
				public void onSwipeLeft() {
					expandSideListWidth(false, true);
				}

				@Override
				public void onSwipeRight() {
					expandSideListWidth(true, true);
				}
			};
			if (sideListArea instanceof FlingLinearLayout) {
				((FlingLinearLayout) sideListArea).setOnSwipeListener(
						(view, direction) -> expandSideListWidth(
								direction == FlingLinearLayout.LEFT_TO_RIGHT, true));
			} else {
				sideListArea.setOnTouchListener(expandTouchListener);
			}

			animationListener = new Animation.AnimationListener() {

				@Override
				public void onAnimationStart(Animation animation) {
				}

				@Override
				public void onAnimationEnd(Animation animation) {
					if (!sidelistIsExpanded && !AndroidUtils.isTV(null)) {
						for (SideListEntry entry : mapEntries.values()) {
							entry.setHeaderTextVisibility(View.GONE);
						}
					}

					expandedStateChanged(sidelistIsExpanded);
				}

				@Override
				public void onAnimationRepeat(Animation animation) {
				}
			};
			LayoutTransition layoutTransition = new LayoutTransition();
			layoutTransition.setDuration(400);
			layoutTransition.setAnimateParentHierarchy(false);
			sideListArea.setLayoutTransition(layoutTransition);

			createEntries(parentView);
		} else {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "setupSideListArea: no sidelistArea");
			}
		}

		// used to be in onStart()
		if (activity instanceof AppCompatActivity) {
			AppCompatActivity abActivity = (AppCompatActivity) activity;

			if (isInDrawer) {
				ActionBar ab = abActivity.getSupportActionBar();
				if (ab != null) {
					ab.setDisplayHomeAsUpEnabled(true);
					ab.setHomeButtonEnabled(true);
				}
			}
		}

		setControllingFragment(controllingFragment, true);
	}

	void setControllingFragment(Fragment fragment, boolean force) {
		if (fragment == forFragment && !force) {
			return;
		}
		if (lifecycle != null) {
			lifecycle.removeObserver(this);
		}

		lifecycle = forFragment == null ? activity.getLifecycle()
				: forFragment.getLifecycle();

		forFragment = fragment;
		lifecycle.addObserver(this);
		if (forFragment instanceof SideListHelperListener) {
			setMainAdapter(((SideListHelperListener) forFragment).getMainAdapter());
		} else if (activity instanceof SideListHelperListener) {
			setMainAdapter(((SideListHelperListener) activity).getMainAdapter());
		}
	}

	public void setMainAdapter(FlexibleRecyclerAdapter mainAdapter) {
		if (AndroidUtils.DEBUG_ADAPTER) {
			Log.d(TAG, "setMainAdapter(" + mainAdapter + ") was " + this.mainAdapter);
		}
		if (this.mainAdapter == mainAdapter && mainAdapter != null) {
			return;
		}
		if (this.mainAdapter != null) {
			this.mainAdapter.removeOnSetItemsCompleteListener(this);
		}
		this.mainAdapter = mainAdapter;

		if (mainAdapter == null) {
			if (AndroidUtils.DEBUG) {
				Log.w(TAG, "setupSideListArea: No MainAdapter "
						+ AndroidUtils.getCompressedStackTrace());
			}
			clear();
			resetSideEntries();
			return;
		}
		if (mainAdapter instanceof SortableAdapter) {
			this.sortableAdapter = (SortableAdapter<ADAPTERITEM>) mainAdapter;
			ComparatorMapFields<ADAPTERITEM> sorter = sortableAdapter.getSorter();
			setCurrentSortUI(sorter.getSortDefinition(), sorter.isAsc(), false);
			sortableAdapter.setPerformingFilteringListener(true, this);
		} else {
			sortableAdapter = null;
		}
		mainAdapter.addOnSetItemsCompleteListener(this);
		sideActionsAdapter = null;

		parentView.addOnLayoutChangeListener(this);

		resetSideEntries();

		if (sortableAdapter != null) {
			sortableAdapter.getFilter().refilter(false);
		}
	}

	public void setDimensionLimits(int SIDELIST_COLLAPSE_UNTIL_WIDTH_PX,
			int SIDELIST_KEEP_EXPANDED_AT_DP) {
		this.collapseUntilWidthPx = SIDELIST_COLLAPSE_UNTIL_WIDTH_PX;
		this.keepExpandedAtParentWidthDp = SIDELIST_KEEP_EXPANDED_AT_DP;
	}

	@Override
	public void onSetItemsComplete(FlexibleRecyclerAdapter adapter) {
		if (adapter != mainAdapter) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				Log.d(TAG, "onSetItemsComplete for " + adapter
						+ ": skip, mainAdapter has changed to " + mainAdapter);
			}
			return;
		}
		if (sideSortAdapter != null && sortableAdapter != null) {
			ComparatorMapFields<ADAPTERITEM> sorter = sortableAdapter.getSorter();

			if (sorter != null) {
				setCurrentSortUI(sideSortAdapter.getCurrentSort(), sorter.isAsc(),
						false);
			}
		}

		if (mainAdapter instanceof SortableAdapter) {
			CharSequence constraint = ((SortableAdapter) mainAdapter).getFilter().getConstraint();
			boolean hasConstraint = constraint != null && constraint.length() > 0;

			activity.runOnUiThread(() -> {
				final View textFilterHeader = activity.findViewById(
						R.id.sidetextfilter_header);
				if (textFilterHeader != null) {
					if (hideUnselectedSideHeaders && activeEntry != null
							&& activeEntry != mapEntries.get(ENTRY_TEXTFILTER)) {
						textFilterHeader.setVisibility(View.GONE);
					} else {
						textFilterHeader.setVisibility(
								(getLetterFilter() != null && getLetterFilter().showLetterUI())
										|| hasConstraint ? View.VISIBLE : View.GONE);
					}
				}
			});
		}

	}

	public boolean flipExpandState() {
		return expandSideListWidth(!isExpanded(), true);
	}

	@Thunk
	public boolean expandSideListWidth(Boolean expand, boolean userInitiated) {
		if (inManualExpandState && !userInitiated) {
			return false;
		}

		if (sideListArea == null || keepExpandedAtParentWidthDp == 0
				|| isInDrawer) {
			return false;
		}
		int width = parentView.getWidth();
		boolean noExpanding = width < collapseUntilWidthPx;
		// We have a Motorola Xoom on Android 4.0.4 that can't handle shrinking
		// (torrent list view overlays)
		boolean noShrinking = Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN;

		if (expand == null) {
			if (noExpanding && noShrinking) {
				return false;
			}
			expand = width >= AndroidUtilsUI.dpToPx(keepExpandedAtParentWidthDp);
		}
		if (!userInitiated) {
			boolean canExpand = width >= AndroidUtilsUI.dpToPx(
					keepExpandedAtParentWidthDp);
			if (canExpand && !expand) {
				return false;
			}
		}

		if (sidelistIsExpanded != null) {
			// before listening to caller, do our checks
			if (sidelistIsExpanded && noExpanding && !noShrinking) {
				expand = false;
			}
			if (!sidelistIsExpanded && noShrinking && !noExpanding) {
				expand = true;
			}
		}

		if (expand && noExpanding && !noShrinking) {
			expand = false;
		}
		if (!expand && noShrinking && !noExpanding) {
			expand = true;
		}

		if (sidelistIsExpanded != null && expand == sidelistIsExpanded) {
			return false;
		}

		inManualExpandState = userInitiated;

		expandedStateChanging(expand);
		sidelistIsExpanded = expand;

		if (sidelistIsExpanded) {
			for (SideListEntry entry : mapEntries.values()) {
				entry.setHeaderTextVisibility(View.VISIBLE);
			}
		}
		if (expand) {
			sizeTo(sideListArea, maxWidthPx, SIDELIST_DURATION_MS, animationListener);
		} else {
			sizeTo(sideListArea, collapsedWidthPx, SIDELIST_DURATION_MS,
					animationListener);
		}

		return true;
	}

	@Thunk
	void expandedStateChanged(boolean expanded) {
		if (AndroidUtils.DEBUG_ADAPTER) {
			log("expandedStateChanged " + AndroidUtils.getCompressedStackTrace());
		}
		if (expanded) {
			if (sideSortAdapter != null) {
				sideSortAdapter.setViewType(0);
			}
			if (sideTextFilterAdapter != null) {
				sideTextFilterAdapter.setViewType(0);
			}
		}
		if (sideActionsAdapter != null) {
			sideActionsAdapter.setSmall(!expanded);
		}
		if (sideListHelperListener != null) {
			sideListHelperListener.sideListExpandListChanged(expanded);
		}
		isResizing = false;
		if (needsReset) {
			resetSideEntries();
		}
	}

	private void expandedStateChanging(boolean expanded) {
		isResizing = true;
		if (AndroidUtils.DEBUG_ADAPTER) {
			Log.d(TAG,
					"expandedStateChanging: " + expanded
							+ "; set ViewTypes, refresh actions "
							+ AndroidUtils.getCompressedStackTrace(3));
		}
		if (!expanded) {
			if (sideSortAdapter != null) {
				sideSortAdapter.setViewType(1);
			}
			if (sideTextFilterAdapter != null) {
				sideTextFilterAdapter.setViewType(1);
			}
		}
		if (sideActionsAdapter != null) {
			sideActionsAdapter.notifyDataSetChanged();
		}
		if (sideListHelperListener != null) {
			sideListHelperListener.sideListExpandListChanging(expanded);
		}
	}

	private static void sizeTo(@NonNull final View v, int finalWidth,
			int durationMS, Animation.AnimationListener listener) {
		if (finalWidth < 0) {
			Log.w(TAG, "sizeTo: finalWidth < 0 at " + finalWidth);
			return;
		}
		final int initalWidth = v.getMeasuredWidth();

		final int diff = finalWidth - initalWidth;

		final int multiplier = diff < 0 ? -1 : 0;

		Animation a = new Animation() {
			@Override
			protected void applyTransformation(float interpolatedTime,
					Transformation t) {
				v.getLayoutParams().width = initalWidth
						+ ((int) (diff * interpolatedTime));
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
						&& !v.isInLayout()) {
					v.requestLayout();
				}
			}

			@Override
			public boolean willChangeBounds() {
				return true;
			}
		};

		if (listener != null) {
			a.setAnimationListener(listener);
		}

		if (durationMS < 0) {
			// 1dp/ms
			a.setDuration((int) ((diff * multiplier)
					/ v.getContext().getResources().getDisplayMetrics().density));
		} else {
			a.setDuration(durationMS);
		}
		v.startAnimation(a);
	}

	public boolean isValid() {
		return sideListArea != null;
	}

	@Thunk
	void hideAllBodies() {
		for (SideListEntry entry : mapEntries.values()) {
			entry.setBodyVisibility(View.GONE);
		}
	}

	@Override
	public void onLayoutChange(View v, int left, int top, int right, int bottom,
			int oldLeft, int oldTop, int oldRight, int oldBottom) {
		if (!isInDrawer) {
			int width = right - left;
			if (width != lastWidth) {
				lastWidth = width;
				expandSideListWidth(sidelistInFocus, false);
			}
		}
		int height = sideListArea == null ? 0 : sideListArea.getHeight();
		//  Height will be 0 if launched while screen is off
		if (height <= 0) {
			height = bottom - top;
		}
		boolean hide = height < AndroidUtilsUI.dpToPx(
				SIDELIST_HIDE_UNSELECTED_HEADERS_UNTIL_DP);
		if (hide != hideUnselectedSideHeaders) {
			hideUnselectedSideHeaders = hide;
			resetSideEntries();
		}
	}

	private class SideListEntry
		implements View.OnClickListener
	{
		@NonNull
		private final ViewGroup header;

		@NonNull
		@Thunk
		final ViewGroup body;

		private final String id;

		boolean alwaysHidden = false;

		SideListEntry(String id, @NonNull ViewGroup vgHeader,
				@NonNull ViewGroup vgBody) {
			this.header = vgHeader;
			this.body = vgBody;
			this.id = id;
			vgHeader.setOnClickListener(this);
			if (!(body instanceof FlingLinearLayout)) {
				body.setOnTouchListener(expandTouchListener);
			}
			RecyclerView recyclerView = getRecyclerView();
			if (recyclerView != null) {
				recyclerView.setLayoutManager(new PreCachingLayoutManager(activity));
			}
		}

		RecyclerView getRecyclerView() {
			if (body instanceof RecyclerView) {
				return (RecyclerView) body;
			}
			return null;
		}

		@Override
		public void onClick(View v) {
			boolean doTrigger = true;
			boolean same = activeEntry == this;
			if (same) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "onClick: Hide All Bodies");
				}
				if (activeEntry != null && sideListArea != null) {
					LayoutTransition transition = new LayoutTransition();
					transition.setAnimateParentHierarchy(false);
					sideListArea.setLayoutTransition(transition);
					hideAllBodies();
					// Could just set the active GONE, since it's the only one that
					// should be visible.  The problem is "should" isn't "will"
					//sidebarViewActive.setVisibility(View.GONE);
				}
				activeEntry = null;
				if (hideUnselectedSideHeaders) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "onClick: Hide Headers");
					}
					for (SideListEntry entry : mapEntries.values()) {
						entry.header.setVisibility(
								entry.alwaysHidden ? View.GONE : View.VISIBLE);
					}
				}
			} else {
				if (activeEntry != null) {

					// 1) Make current view invisible
					// 2) Move header(s) up or down
					// 3) Show new view
					activeEntry.setBodyVisibility(View.INVISIBLE);

					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "onClick: Animate new view in");
					}
					doTrigger = false;
					ViewGroup parent = (ViewGroup) activeEntry.body.getParent();
					int iOld = parent.indexOfChild(activeEntry.body);
					int iNew = parent.indexOfChild(body);
					int direction = iNew > iOld ? 1 : -1;
					int y = direction * -1 * activeEntry.body.getHeight();

					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "onClick: " + iOld + "/" + iNew);
					}
					// headers are one position up in parent

					List<View> viewsToMove = new ArrayList<>(1);
					if (direction > 0) {
						// If new is lower, we need tomove the header of new, and
						// any headers above it, up to the header of old.
						for (int i = iNew - 1; i > iOld; i--) {
							View view1 = parent.getChildAt(i);
							if (view1 == null) {
								continue;
							}
							if ("sideheader".equals(view1.getTag())) {
								viewsToMove.add(view1);
							}
						}
					} else {
						// if new is higher, we need to move the header of old, and
						// and headers above it, up to header of new.
						for (int i = iOld - 1; i > iNew; i--) {
							View view1 = parent.getChildAt(i);
							if (view1 == null) {
								continue;
							}
							if ("sideheader".equals(view1.getTag())) {
								viewsToMove.add(view1);
							}
						}
					}

					for (final View header : viewsToMove) {
						Animator.AnimatorListener l = new AnimatorEndListener() {
							final ViewGroup old = activeEntry.body;

							@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
							@Override
							public void onAnimationEnd(Animator animation) {
								if (activity.isFinishing()) {
									return;
								}
								header.setTranslationY(0);
								sideListArea.setLayoutTransition(null);
								// These two don't need to be called everytime
								old.setVisibility(View.GONE);
								body.setAlpha(0.0f);
								setBodyVisibility(View.VISIBLE);
								body.animate().alpha(1.0f);

								sectionVisibiltyChanged(SideListEntry.this);
							}
						};

						header.animate().translationY(y).setListener(l).setDuration(300);
					}
				} else { // sidebarviewactive is null
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "onClick: show body (none visible yet)");
					}
					LayoutTransition transition = new LayoutTransition();
					transition.setAnimateParentHierarchy(false);
					sideListArea.setLayoutTransition(transition);
					setBodyVisibility(View.VISIBLE);
				}

				activeEntry = this;

				if (hideUnselectedSideHeaders) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "onClick: Hide Headers");
					}
					for (SideListEntry entry : mapEntries.values()) {
						entry.header.setVisibility(entry.header == v && !entry.alwaysHidden
								? View.VISIBLE : View.GONE);
					}
				}
			}

			if (doTrigger) {
				sectionVisibiltyChanged(activeEntry);
			}

		}

		void setHeaderTextVisibility(int visibility) {
			View sideheader_text = header.findViewWithTag("sideheader_text");
			if (sideheader_text != null) {
				sideheader_text.setVisibility(visibility);
			}
		}

		void setBodyVisibility(int visibility) {
			if (body.getVisibility() == visibility) {
				return;
			}
			body.setVisibility(visibility);

			boolean disappearing = visibility == View.GONE;

			if (disappearing && activeEntry == this) {
				activeEntry = null;
				sectionVisibiltyChanged(null);
			}

			// logic should be in a child class of SideListEntry. ie SideListEntryTextFilter
			if (id.equals(ENTRY_TEXTFILTER) && sortableAdapter != null
					&& sideSortAdapter != null) {
				sortableAdapter.getFilter().setBuildLetters(!disappearing);
				if (!disappearing) {
					sortableAdapter.getFilter().refilter(true);
				} else {
					sideTextFilterAdapter.removeAllItems();
				}
			}
		}

		void setVisibility(int visibility) {
			boolean disappearing = visibility == View.GONE;
			if (disappearing || !hideUnselectedSideHeaders || activeEntry == null) {
				header.setVisibility(visibility);
			}
			if (activeEntry == this) {
				setBodyVisibility(visibility);
			}
		}

		void clear() {
			RecyclerView recyclerView = getRecyclerView();
			if (AndroidUtils.DEBUG) {
				log("clear " + id + "; " + recyclerView);
			}
			if (recyclerView != null) {
				recyclerView.setAdapter(null);
			}
		}

		void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
			RecyclerView recyclerView = getRecyclerView();
			if (recyclerView == null) {
				return;
			}
			RecyclerView.Adapter adapter = recyclerView.getAdapter();
			if (adapter instanceof FlexibleRecyclerAdapter) {
				((FlexibleRecyclerAdapter) adapter).onRestoreInstanceState(
						savedInstanceState);
			}
		}

		void onSaveInstanceState(@NonNull Bundle outState) {
			RecyclerView recyclerView = getRecyclerView();
			if (recyclerView == null) {
				return;
			}
			RecyclerView.Adapter adapter = recyclerView.getAdapter();
			if (adapter instanceof FlexibleRecyclerAdapter) {
				((FlexibleRecyclerAdapter) adapter).onSaveInstanceState(outState);
			}
		}

		public void setAlwaysHidden(boolean b) {
			alwaysHidden = b;
			setVisibility(b ? View.GONE : View.VISIBLE);
		}
	}

	public void addEntry(@NonNull String id, @NonNull View view,
			@IdRes int id_header, @IdRes int id_body) {
		ViewGroup vgHeader = view.findViewById(id_header);
		final ViewGroup vgBody = view.findViewById(id_body);
		if (vgBody == null || vgHeader == null) {
			return;
		}

		SideListEntry entry = new SideListEntry(id, vgHeader, vgBody);
		mapEntries.put(id, entry);

		if (vgBody.getVisibility() == View.VISIBLE && activeEntry != entry) {
			activeEntry = entry;
			sectionVisibiltyChanged(activeEntry);
		}

		// Manually set the next focus down of the body, and the next
		// focus up of the header.  This fixes a case where the
		// body is a very short listview and Android decides the next down
		// view is too far down and picks the closest view to the right/left.
		// Similar case for moving up, if the new vgBody is too far away from the
		// current vgHeader, Android will choose a widget left/right.
		LinearLayout parent = (LinearLayout) vgHeader.getParent();
		int pos = parent.indexOfChild(vgHeader);
		int i = pos + 1;
		View nextView = parent.getChildAt(i);
		while (nextView != null && !"sideheader".equals(nextView.getTag())) {
			i++;
			nextView = parent.getChildAt(i);
		}
		if (nextView != null) {
			vgBody.setNextFocusDownId(nextView.getId());
		}

		i = pos - 1;
		View prevView = parent.getChildAt(i);
		while (prevView != null && "sideheader".equals(prevView.getTag())) {
			i--;
			prevView = parent.getChildAt(i);
		}
		if (prevView != null) {
			vgHeader.setNextFocusUpId(prevView.getId());
		}
	}

	@Thunk
	void sectionVisibiltyChanged(@Nullable SideListEntry entryNewlyVisible) {
		SideListEntry entryTextFilter = mapEntries.get(ENTRY_TEXTFILTER);
		boolean isSideTextFilterVisible = entryNewlyVisible == entryTextFilter;
		if (tvSideFilterText != null) {
			tvSideFilterText.setVisibility(
					tvSideFilterText.getText().length() == 0 && !isSideTextFilterVisible
							? View.GONE : View.VISIBLE);
		}
	}

	@Override
	public void onResume(@NonNull LifecycleOwner owner) {
		if (forFragment == null && (activity instanceof SideListHelperListener)) {
			setMainAdapter(((SideListHelperListener) activity).getMainAdapter());
		}
	}

	@Override
	public void onStart(@NonNull LifecycleOwner owner) {
		expandSideListWidth(sidelistInFocus, false);
	}

	@Override
	public void onStop(@NonNull LifecycleOwner owner) {
		setMainAdapter(null);
	}

//	public boolean onOptionsItemSelected(MenuItem item) {
//		if (item != null && item.getItemId() == android.R.id.home) {
//			// Respond to the action bar's Up/Home button
//			
//			boolean expand = true;
//			if (activity instanceof DrawerActivity) {
//				DrawerActivity abActivity = (DrawerActivity) activity;
//				expand = abActivity.getDrawerLayout() == null;
//			}
//
//			if (expand) {
//				expandSideListWidth(sidelistIsExpanded == null || !sidelistIsExpanded,
//						true);
//				return true;
//			}
//		}
//
//		return false;
//	}

	private void setupSideTextFilter(TextView tvSideFilterText) {
		SideListEntry entry = mapEntries.get(ENTRY_TEXTFILTER);
		if (entry == null) {
			return;
		}
		RecyclerView recyclerView = entry.getRecyclerView();

		if (getLetterFilter() == null) {
			entry.setAlwaysHidden(true);
			if (tvSideFilterText != null) {
				tvSideFilterText.setVisibility(View.GONE);
			}
			return;
		}

		if (tvSideFilterText != null && tvSideFilterText.length() > 0) {
			tvSideFilterText.setVisibility(View.VISIBLE);
			LetterFilter letterFilter = getLetterFilter();
			if (letterFilter != null) {
				letterFilter.refilter(true);
			}
		}

		//This was in TorrentListFragment.. not sure if we need it
		//listSideTextFilter.setItemAnimator(new DefaultItemAnimator());

		if (this.tvSideFilterText != tvSideFilterText) {
			if (this.tvSideFilterText != null) {
				this.tvSideFilterText.removeTextChangedListener(sideTextFilterWatcher);
			}
			this.tvSideFilterText = tvSideFilterText;

			tvSideFilterText.addTextChangedListener(sideTextFilterWatcher);
		}

		if (recyclerView == null) {
			entry.setAlwaysHidden(true);
			return;
		}

		entry.setAlwaysHidden(false);

		sideTextFilterAdapter = new SideTextFilterAdapter(
				new FlexibleRecyclerSelectionListener<SideTextFilterAdapter, SideTextFilterAdapter.SideFilterViewHolder, SideTextFilterInfo>() {
					@Override
					public void onItemCheckedChanged(SideTextFilterAdapter adapter,
							SideTextFilterInfo item, boolean isChecked) {
						if (!isChecked) {
							return;
						}
						adapter.setItemChecked(item, false);

						String s = item.letters;
						if (s.equals(FilterConstants.LETTERS_NUMBERS)) {
							getLetterFilter().setCompactDigits(false);
							getLetterFilter().refilter(false);
							return;
						}
						if (s.equals(FilterConstants.LETTERS_NON)) {
							getLetterFilter().setCompactOther(false);
							getLetterFilter().refilter(false);
							return;
						}
						if (s.equals(FilterConstants.LETTERS_PUNCTUATION)) {
							getLetterFilter().setCompactPunctuation(false);
							getLetterFilter().refilter(false);
							return;
						}
						if (s.equals(FilterConstants.LETTERS_BS)) {
							CharSequence text = tvSideFilterText.getText();
							if (text.length() > 0) {
								text = text.subSequence(0, text.length() - 1);
								tvSideFilterText.setText(text);
							} else {
								getLetterFilter().setCompactPunctuation(true);
								getLetterFilter().setCompactDigits(true);
								getLetterFilter().setCompactOther(true);
								getLetterFilter().refilter(false);
							}
							return;
						}
						s = tvSideFilterText.getText() + s;
						tvSideFilterText.setText(s);
					}

					@Override
					public boolean onItemLongClick(SideTextFilterAdapter adapter,
							int position) {
						return false;
					}

					@Override
					public void onItemSelected(SideTextFilterAdapter adapter,
							int position, boolean isChecked) {

					}

					@Override
					public void onItemClick(SideTextFilterAdapter adapter, int position) {

					}
				});
		recyclerView.setAdapter(sideTextFilterAdapter);

		if (mainAdapter instanceof SortableRecyclerAdapter) {
			((SortableRecyclerAdapter) mainAdapter).setLettersUpdatedListener(this);
		}
	}

	private void setupSideSort(TextView tvSortCurrentNew) {
		SideListEntry entry = mapEntries.get(ENTRY_SORT);
		if (entry == null) {
			return;
		}

		RecyclerView recyclerView = entry.getRecyclerView();
		if (recyclerView == null) {
			entry.setAlwaysHidden(true);
			return;
		}

		List<SideSortAdapter.SideSortInfo> list = new ArrayList<>();
		boolean showEntry = sortableAdapter != null;

		if (showEntry) {
			SparseArray<SortDefinition> sortDefinitions = sortableAdapter.getFilter().getSortDefinitions();
			for (int i = 0, size = sortDefinitions.size(); i < size; i++) {
				SortDefinition sortDefinition = sortDefinitions.get(i);
				list.add(new SideSortAdapter.SideSortInfo(i, sortDefinition.id,
						sortDefinition.name, sortDefinition.resAscending,
						sortDefinition.resDescending));
			}

			showEntry = list.size() > 0;
		}

		entry.setAlwaysHidden(!showEntry);

		tvSortCurrent = tvSortCurrentNew;

		sideSortAdapter = new SideSortAdapter(
				new FlexibleRecyclerSelectionListener<SideSortAdapter, SideSortAdapter.SideSortHolder, SideSortAdapter.SideSortInfo>() {
					@Override
					public void onItemClick(SideSortAdapter adapter, int position) {
					}

					@Override
					public boolean onItemLongClick(SideSortAdapter adapter,
							int position) {
						return false;
					}

					@Override
					public void onItemSelected(SideSortAdapter adapter, int position,
							boolean isChecked) {

					}

					@Override
					public void onItemCheckedChanged(SideSortAdapter adapter,
							SideSortAdapter.SideSortInfo item, boolean isChecked) {

						if (!isChecked) {
							return;
						}
						adapter.setItemChecked(item, false);

						if (sortableAdapter != null) {
							SparseArray<SortDefinition> sortDefinitions = sortableAdapter.getFilter().getSortDefinitions();
							SortDefinition sortDefinition = sortDefinitions.get(
									(int) item.id);
							if (sortDefinition != null) {
								if (sortDefinition.equals(adapter.getCurrentSort())) {
									flipSortOrder();
								} else {
									sortBy(sortDefinition, sortDefinition.isSortAsc());
								}
							}
						}

					}
				});
		sideSortAdapter.setItems(list, null, (oldItem, newItem) -> true);
		recyclerView.setAdapter(sideSortAdapter);
		if (sortableAdapter != null) {
			ComparatorMapFields<ADAPTERITEM> sorter = sortableAdapter.getSorter();
			setCurrentSortUI(sorter.getSortDefinition(), sorter.isAsc(), false);
		}
	}

	@Thunk
	void setCurrentSortUI(final SortDefinition sortDefinition,
			final boolean isAsc, final boolean inProgress) {
		if (sortInProgress != inProgress) {
			sortInProgress = inProgress;
			updateRefreshButton();
		}
		activity.runOnUiThread(() -> {
			if (activity.isFinishing()) {
				return;
			}
			if (sideSortAdapter != null) {
				sideSortAdapter.setCurrentSort(sortDefinition, isAsc);
			} else {
				Log.e(TAG, "setCurrentSortUI: no sideSortAdapter");
			}
			if (tvSortCurrent != null && sortDefinition != null) {
				String s = sortDefinition.name + " " + (isAsc ? "▲" : "▼");
				tvSortCurrent.setText(s);
				if (inProgress) {
					pulsateTextView(tvSortCurrent);
				} else {
					unpulsateTextView(tvSortCurrent);
				}
			}
		});
	}

	private static void pulsateTextView(TextView tv) {
		if (tv == null) {
			return;
		}
		Animation animation = new AlphaAnimation(1f, 0.1f);
		animation.setInterpolator(new LinearInterpolator());
		animation.setDuration(500);
		animation.setStartOffset(200);

		animation.setRepeatMode(Animation.REVERSE);
		animation.setRepeatCount(Animation.INFINITE);
		tv.startAnimation(animation);
	}

	private static void unpulsateTextView(TextView tv) {
		if (tv == null) {
			return;
		}
		tv.setAlpha(1);
		tv.clearAnimation();
	}

	@Thunk
	void sortBy(final SortDefinition sortDefinition, final boolean isAsc) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "SORT BY " + sortDefinition + (isAsc ? " (Asc)" : " (Desc)")
					+ ", sortableAdapter=" + sortableAdapter);
		}
		if (sortableAdapter != null) {
			setCurrentSortUI(sortDefinition, isAsc, true);
			sortableAdapter.setSortDefinition(sortDefinition, isAsc);
		}
	}

	@Thunk
	void flipSortOrder() {
		if (sortableAdapter == null) {
			return;
		}

		ComparatorMapFields<ADAPTERITEM> sorter = sortableAdapter.getSorter();

		if (sorter == null) {
			return;
		}

		setCurrentSortUI(sideSortAdapter.getCurrentSort(), !sorter.isAsc(), true);
		sortableAdapter.setSortDefinition(sorter.getSortDefinition(),
				!sorter.isAsc());
	}

	/**
	 * Map of available (next) letters and count of hits, is available.
	 * Updates the SideFilterText list with new map
	 * <p/>
	 * Call this from lettersUpdated(HashMap<String, Integer>) of the adapter
	 * you want to show the letters for
	 *
	 */
	@Override
	@UiThread
	public void lettersUpdated(HashMap<String, Integer> mapLetters) {
		LetterFilter letterFilter = getLetterFilter();
		if (letterFilter == null) {
			return;
		}

		if (sideTextFilterAdapter == null) {
			Log.e(TAG, "lettersUpdated: No sideTextFilterAdapter. "
					+ AndroidUtils.getCompressedStackTrace());
			return;
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			Log.d(TAG, "lettersUpdated: " + mapLetters.size());
		}
		String[] keys = mapLetters.keySet().toArray(new String[0]);
		Arrays.sort(keys, (lhs, rhs) -> {
			int rsh_length = rhs.length();
			if ((rsh_length > 1) == (lhs.length() > 1)) {
				return lhs.compareTo(rhs);
			}
			return rsh_length > 1 ? -1 : 1;
		});
		final ArrayList<SideTextFilterInfo> list = new ArrayList<>();
		for (String c : keys) {
			Integer count = mapLetters.get(c);
			if (count != null) {
				SideTextFilterInfo info = new SideTextFilterInfo(c, count);
				list.add(info);
			}
		}
		if (tvSideFilterText.getText().length() > 0
				|| !letterFilter.getCompactDigits()
				|| !letterFilter.getCompactNonLetters()
				|| !letterFilter.getCompactPunctuation()) {
			list.add(0, new SideTextFilterInfo(FilterConstants.LETTERS_BS, 0));
		}

		activity.runOnUiThread(() -> {
			if (activity.isFinishing()) {
				return;
			}
			SideListEntry entry = mapEntries.get(ENTRY_TEXTFILTER);
			if (entry == null) {
				return;
			}
			RecyclerView recyclerView = entry.getRecyclerView();
			if (recyclerView == null) {
				return;
			}
			boolean hadFocus = AndroidUtilsUI.isChildOf(activity.getCurrentFocus(),
					recyclerView);
			sideTextFilterAdapter.setItems(list, null,
					(oldItem, newItem) -> oldItem.count == newItem.count);

			if (hadFocus) {
				recyclerView.post(recyclerView::requestFocus);
			}
		});
	}

	public boolean isExpanded() {
		return sidelistIsExpanded == null ? true : sidelistIsExpanded;
	}

	public void onSaveInstanceState(@NonNull Bundle outState) {
		for (SideListEntry entry : mapEntries.values()) {
			entry.onSaveInstanceState(outState);
		}
		if (sidelistIsExpanded != null) {
			outState.putBoolean(TAG + ".isExpanded", sidelistIsExpanded);
		}
	}

	public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		for (SideListEntry entry : mapEntries.values()) {
			entry.onRestoreInstanceState(savedInstanceState);
		}
		Object o = savedInstanceState.get(TAG + ".isExpanded");
		if (o instanceof Boolean) {
			sidelistIsExpanded = (Boolean) o;
		}
	}

	private void setupSideFilters(TextView tvFilterCurrent) {
		this.tvFilterCurrent = tvFilterCurrent;
		boolean showFilterEntry = sideListHelperListener != null
				&& sideListHelperListener.showFilterEntry();
		SideListEntry entry = mapEntries.get(ENTRY_FILTER);
		if (entry == null) {
			return;
		}
		entry.setAlwaysHidden(!showFilterEntry);
	}

	private void setupSideActions() {
		SideListEntry entry = mapEntries.get(ENTRY_ACTION);
		if (entry == null) {
			return;
		}

		RecyclerView recyclerView = entry.getRecyclerView();
		boolean hide = recyclerView == null;
		if (hide) {
			entry.setAlwaysHidden(true);
			return;
		}

		SideActionSelectionListener sideActionSelectionListener = sideListHelperListener.getSideActionSelectionListener();

		SideActionSelectionListener oldListener;
		if (sideActionsAdapter == null) {
			oldListener = null;
		} else {
			oldListener = sideActionsAdapter.getSideActionSelectionListener();
			//noinspection RedundantClassCall
			if (SideActionSelectionListenerDelegate.class.isInstance(oldListener)) {
				//noinspection unchecked
				oldListener = ((SideActionSelectionListenerDelegate) oldListener).delegate;
			}
		}

		entry.setAlwaysHidden(sideActionSelectionListener == null);

		boolean changed = sideActionSelectionListener != oldListener;

		if (!changed) {
			return;
		}

		sideActionsAdapter = sideActionSelectionListener == null ? null
				: new SideActionsAdapter(new SideActionSelectionListenerDelegate(
						sideActionSelectionListener));
		recyclerView.setAdapter(sideActionsAdapter);
	}

	protected class SideActionSelectionListenerDelegate
		implements SideActionSelectionListener
	{
		final SideActionSelectionListener delegate;

		@Override
		public Session getSession() {
			return sessionGetter.getSession();
		}

		SideActionSelectionListenerDelegate(SideActionSelectionListener delegate) {
			this.delegate = delegate;
		}

		@Override
		public boolean isRefreshing() {
			return sortInProgress || delegate.isRefreshing();
		}

		@Override
		public void prepareActionMenus(Menu menu) {
			delegate.prepareActionMenus(menu);
		}

		@Override
		public MenuBuilder getMenuBuilder() {
			return delegate.getMenuBuilder();
		}

		@Nullable
		@Override
		public int[] getRestrictToMenuIDs() {
			return delegate.getRestrictToMenuIDs();
		}

		@Override
		public void onItemClick(SideActionsAdapter adapter, int position) {
			SideActionsAdapter.SideActionsInfo item = adapter.getItem(position);
			if (item != null && item.menuItem != null && item.menuItem.hasSubMenu()) {
				AndroidUtilsUI.popupSubMenu((SubMenuBuilder) item.menuItem.getSubMenu(),
						new ActionMode.Callback() {
							@Override
							public boolean onCreateActionMode(ActionMode mode, Menu menu) {
								return true;
							}

							@Override
							public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
								return activity.onPrepareOptionsMenu(menu);
							}

							@Override
							public boolean onActionItemClicked(ActionMode mode,
									MenuItem item) {
								return activity.onOptionsItemSelected(item);
							}

							@Override
							public void onDestroyActionMode(ActionMode mode) {

							}
						}, item.menuItem.getTitle());
				return;
			}
			delegate.onItemClick(adapter, position);
		}

		@Override
		public boolean onItemLongClick(SideActionsAdapter adapter, int position) {
			return delegate.onItemLongClick(adapter, position);
		}

		@Override
		public void onItemSelected(SideActionsAdapter adapter, int position,
				boolean isChecked) {
			delegate.onItemSelected(adapter, position, isChecked);
		}

		@Override
		public void onItemCheckedChanged(SideActionsAdapter adapter,
				SideActionsAdapter.SideActionsInfo item, boolean isChecked) {
			delegate.onItemCheckedChanged(adapter, item, isChecked);
		}
	}

	public void updateSideActionMenuItems() {
		if (sideActionsAdapter != null) {
			sideActionsAdapter.updateMenuItems();
		}
	}

	@AnyThread
	public void clear() {
		if (AndroidUtils.DEBUG) {
			log("clear via " + AndroidUtils.getCompressedStackTrace());
		}
		sideActionsAdapter = null;

		sideTextFilterAdapter = null;

		mainAdapter = null;
		sortableAdapter = null;
		sortInProgress = false;

		activity.runOnUiThread(() -> {
			// clear entries, even if activity is finishing, so that
			// any recycler views will dispatch a onDetachFromRecyclerView, ensuring
			// any extra cleanup is done.
			for (SideListEntry entry : mapEntries.values()) {
				entry.clear();
			}

			if (activity.isFinishing()) {
				return;
			}
			if (AndroidUtils.DEBUG) {
				log("clear (UIThread)");
			}
			if (tvSideFilterText != null && sideTextFilterWatcher != null) {
				sideTextFilterWatcher.clear();
				tvSideFilterText.removeTextChangedListener(sideTextFilterWatcher);
				tvSideFilterText = null;
			}

			if (tvSortCurrent != null) {
				unpulsateTextView(tvSortCurrent);
				tvSortCurrent.setText("");
			}
			if (tvSideFilterText != null) {
				unpulsateTextView(tvSideFilterText);
				tvSideFilterText.setText("");
			}
			if (tvFilterCurrent != null) {
				unpulsateTextView(tvFilterCurrent);
				tvFilterCurrent.setText("");
			}
			//commonPostSetup(parentView);
		});

	}

	private void createEntries(@NonNull View mainView) {
		addEntry(ENTRY_SORT, mainView, R.id.sidesort_header, R.id.sidesort_list);
		addEntry(ENTRY_FILTER, mainView, R.id.sidefilter_header,
				R.id.sidefilter_list);
		addEntry(ENTRY_TEXTFILTER, mainView, R.id.sidetextfilter_header,
				R.id.sidetextfilter_list);

		addEntry(ENTRY_ACTION, mainView, R.id.sideactions_header,
				R.id.sideactions_list);

		if (sideListHelperListener != null) {
			sideListHelperListener.onSideListHelperCreated(this);
		}
	}

	private void resetSideEntries() {
		if (isResizing) {
			// Can't reset side entries while resize animation occurs.  Eventually
			// crashes with a
			// ava.lang.IllegalArgumentException: Tmp detached view should be removed from RecyclerView before it can be recycled
			// and no trace into our code. Something related to filling the RecyclerView
			// which has item animations, while the view itself is being movied via
			// animation.
			if (AndroidUtils.DEBUG_ADAPTER) {
				log("skip resetside " + AndroidUtils.getCompressedStackTrace());
			}
			needsReset = true;
			return;
		}
		needsReset = false;
		if (sideListArea != null && sideListArea.getVisibility() == View.VISIBLE) {
			setupSideTextFilter(parentView.findViewById(R.id.sidefilter_text));

			setupSideSort(parentView.findViewById(R.id.sidelist_sort_current));

			setupSideActions();

			setupSideFilters(parentView.findViewById(R.id.sidefilter_current));

			if (sideListHelperListener != null) {
				sideListHelperListener.onSideListHelperVisibleSetup(parentView);
			}

			expandSideListWidth(sidelistInFocus, false);
		} else if (AndroidUtils.DEBUG) {
			Log.d(TAG,
					"setupSideListArea: sidelist not visible -- not setting up (until "
							+ "drawer is opened)");
		}

//		if (mapEntries.containsKey(ENTRY_TEXTFILTER)
//				&& (mainAdapter instanceof SortableRecyclerAdapter)) {
//			((SortableRecyclerAdapter) mainAdapter).getFilter().setBuildLetters(true);
//		}

		if (sideListHelperListener != null) {
			sideListHelperListener.onSideListHelperPostSetup(this);
		}
	}

	@AnyThread
	public void updateRefreshButton() {
		if (sideActionsAdapter == null) {
			return;
		}
		sideActionsAdapter.updateRefreshButton();
	}

	class BufferedTextWatcher
		implements TextWatcher
	{
		@NonNull
		CharSequence lastString = "";

		void clear() {
			lastString = "";
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			if (lastString.equals(s)) {
				return;
			}
			LetterFilter letterFilter = getLetterFilter();
			if (letterFilter == null) {
				return;
			}
			letterFilter.setConstraint(s.toString().toUpperCase());
			letterFilter.refilter(false);
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
		}

		@Override
		public void afterTextChanged(Editable s) {
		}
	}

	@Override
	@UiThread
	public void performingFilteringChanged(
			@DelayedFilter.FilterState int filterState,
			@DelayedFilter.FilterState int oldState) {
		if (!AndroidUtilsUI.isUIThread()) {
			AndroidUtilsUI.runOnUIThread(activity, false,
					(validActivity) -> performingFilteringChanged(filterState, oldState));
			return;
		}

		if (AndroidUtils.DEBUG_ADAPTER) {
			log("performingFilteringChanged: "
					+ DelayedFilter.FILTERSTATE_DEBUGSTRINGS[filterState] + " via "
					+ AndroidUtils.getCompressedStackTrace());
		}
		if (activity.isFinishing()) {
			return;
		}

		ProgressBar spinner = activity.findViewById(R.id.sideaction_spinner);
		if (spinner != null) {
			if (((View) spinner.getParent()).getVisibility() == View.GONE) {
				spinner = null;
			}
		}

		switch (filterState) {
			case DelayedFilter.FILTERSTATE_FILTERING: {
				unpulsateTextView(tvSortCurrent);
				if (tvFilterCurrent != null) {
					if (tvFilterCurrent.getText().length() == 0) {
						ourSideFilterText = "Filtering..";
						tvFilterCurrent.setAlpha(0);
						tvFilterCurrent.setText(ourSideFilterText);
					}
					pulsateTextView(tvFilterCurrent);
				}
				break;
			}
			case DelayedFilter.FILTERSTATE_SORTING: {
				pulsateTextView(tvSortCurrent);
				if (tvFilterCurrent != null) {
					unpulsateTextView(tvFilterCurrent);
					if (tvFilterCurrent.getText().equals(ourSideFilterText)) {
						tvFilterCurrent.setText("");
					}
				}
				break;
			}
			case DelayedFilter.FILTERSTATE_PUBLISHING: {
				if (spinner != null) {
					spinner.setVisibility(View.VISIBLE);
					spinner.forceLayout();
				}
				if (tvFilterCurrent != null) {
					unpulsateTextView(tvFilterCurrent);
					if (tvFilterCurrent.getText().equals(ourSideFilterText)) {
						tvFilterCurrent.setText("");
					}
				}
				if (spinner != null) {
					unpulsateTextView(tvSortCurrent);
				}
				break;
			}

			case DelayedFilter.FILTERSTATE_IDLE: {
				if (spinner != null) {
					spinner.setVisibility(View.GONE);
					spinner.forceLayout();
				}
				unpulsateTextView(tvSortCurrent);
				if (tvFilterCurrent != null) {
					unpulsateTextView(tvFilterCurrent);
					if (tvFilterCurrent.getText().equals(ourSideFilterText)) {
						tvFilterCurrent.setText("");
					}
				}
				break;
			}
		}

	}

	private String classSimpleName;

	@SuppressLint("LogConditional")
	private void log(int priority, @NonNls String s) {
		if (!AndroidUtils.DEBUG) {
			return;
		}
		if (classSimpleName == null || "NULL".equals(classSimpleName)) {
			classSimpleName = AndroidUtils.getSimpleName(activity.getClass()) + "@"
					+ Integer.toHexString(activity.hashCode());
		}
		String tag = TAG + "@" + Integer.toHexString(hashCode());
		Log.println(priority, classSimpleName, tag + ": " + s);
	}

	@SuppressLint("LogConditional")
	@Thunk
	void log(@NonNls String s) {
		if (!AndroidUtils.DEBUG) {
			return;
		}
		if (classSimpleName == null || "NULL".equals(classSimpleName)) {
			classSimpleName = AndroidUtils.getSimpleName(activity.getClass()) + "@"
					+ Integer.toHexString(activity.hashCode());
		}
		String tag = TAG + "@" + Integer.toHexString(hashCode());
		Log.d(classSimpleName, tag + ": " + s);
	}

	@Thunk
	LetterFilter getLetterFilter() {
		return mainAdapter instanceof SortableRecyclerAdapter
				? ((SortableRecyclerAdapter) mainAdapter).getFilter() : null;
	}
}
