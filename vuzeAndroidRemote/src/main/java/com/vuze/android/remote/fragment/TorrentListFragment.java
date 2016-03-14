/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
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

package com.vuze.android.remote.fragment;

import static com.vuze.android.remote.adapter.SideFilterAdapter.SideFilterInfo;

import java.util.*;

import android.animation.LayoutTransition;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.*;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.view.menu.SubMenuBuilder;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.MenuDialogHelper;
import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.ValueStringArray;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.activity.TorrentViewActivity;
import com.vuze.android.remote.adapter.*;
import com.vuze.android.remote.dialog.*;
import com.vuze.android.remote.dialog.DialogFragmentFilterBy.FilterByDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentSortBy.SortByDialogListener;
import com.vuze.android.remote.adapter.TorrentListAdapter.TorrentFilter;
import com.vuze.android.remote.rpc.*;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.util.MapUtils;

/**
 * Handles a ListView that shows Torrents
 */
public class TorrentListFragment
	extends Fragment
	implements TorrentListReceivedListener, FilterByDialogListener,
	SortByDialogListener, SessionInfoListener, ActionModeBeingReplacedListener,
	TagListReceivedListener, Animation.AnimationListener, View.OnKeyListener,
	SessionSettingsChangedListener, TorrentListRefreshingListener,
	NetworkState.NetworkStateListener

{
	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private static final String TAG = "TorrentList";

	public final static String LETTERS_BS = "\u232B";

	public final static String LETTERS_NUMBERS = "0-9";

	public final static String LETTERS_PUNCTUATION = "Punctuation";

	public final static String LETTERS_NON = "Other";

	public static final int SIDELIST_MAX_WIDTH = VuzeRemoteApp.getContext().getResources().getDimensionPixelSize(
			R.dimen.sidelist_max_width);

	private static final int SIDELIST_MIN_WIDTH = VuzeRemoteApp.getContext().getResources().getDimensionPixelSize(
			R.dimen.sidelist_min_width);

	public static final int SIDELIST_DURATION_MS = 300;

	// Shrink sidelist, typically for 7" Tablets in Portrait
	private static final int SIDELIST_COLLAPSE_UNTIL_WIDTH_PX = AndroidUtilsUI.dpToPx(
			500);

	// Sidelist always full-width, typically for 9"-11" Tablets, 7" Tablets in
	// Landscape, and TVs
	private static final int SIDELIST_KEEP_EXPANDED_AT_PX = AndroidUtilsUI.dpToPx(
			768);

	// Rare case when there's not enough height.  Show only active sidelist
	// header
	// This would be for Dell Streak (800x480dp) if it was API >= 13
	// Can't be >= 540, since TVs are that.
	// Each row is 42dp.  42x4=168, plus top actionbar (64dp?) and our header
	// (20dp?) ~ 252 dp.  Want to show at least 6 rows of the list.  6x42=252
	private static final int SIDELIST_HIDE_UNSELECTED_HEADERS_MAX_PX = AndroidUtilsUI.dpToPx(
			500);

	public interface OnTorrentSelectedListener
		extends ActionModeBeingReplacedListener
	{
		void onTorrentSelectedListener(TorrentListFragment torrentListFragment,
				long[] ids, boolean inMultiMode);
	}

	private RecyclerView listview;

	protected ActionMode mActionMode;

	private TorrentListAdapter torrentListAdapter;

	private SessionInfo sessionInfo;

	private EditText filterEditText;

	private Callback mActionModeCallbackV7;

	private TextView tvFilteringBy;

	private TextView tvTorrentCount;

	private boolean actionModeBeingReplaced;

	private boolean rebuildActionMode;

	private Toolbar tb;

	private OnTorrentSelectedListener mCallback;

	// >> SideList.. could probably some things in a Map

	private LinearLayout sideListArea;

	private TextView tvSideFilterText;

	private RecyclerView listSideActions;

	private RecyclerView listSideSort;

	private RecyclerView listSideTags;

	private RecyclerView listSideFilter;

	private SideActionsAdapter sideActionsAdapter;

	private SideSortAdapter sideSortAdapter;

	private SideTagAdapter sideTagAdapter;

	private SideFilterAdapter sideFilterAdapter;

	private Boolean sidelistIsExpanded = null;

	private ViewGroup sidebarViewActive = null;

	private Boolean sidelistInFocus = null;

	private boolean hideUnselectedSideHeaders = false;

	private List<ViewGroup> listHeaderViewGroups = new ArrayList<>();
	// << SideList

	private View fragView;

	private Boolean isSmall;

	@Override
	public void onAttach(Context activity) {
		super.onAttach(activity);

		if (activity instanceof OnTorrentSelectedListener) {
			mCallback = (OnTorrentSelectedListener) activity;
		}

		FlexibleRecyclerSelectionListener rs = new FlexibleRecyclerSelectionListener<TorrentListAdapter, Long>() {
			@Override
			public void onItemSelected(TorrentListAdapter adapter, final int position,
					boolean isChecked) {
			}

			@Override
			public void onItemClick(TorrentListAdapter adapter, int position) {
			}

			@Override
			public boolean onItemLongClick(TorrentListAdapter adapter, int position) {
				if (AndroidUtils.usesNavigationControl()) {
					return showTorrentContextMenu();
				}
				return false;
			}

			@Override
			public void onItemCheckedChanged(TorrentListAdapter adapter, Long item,
					boolean isChecked) {
				if (mActionMode == null && isChecked) {
					showContextualActions(false);
				}

				if (adapter.getCheckedItemCount() == 0) {
					finishActionMode();
				}

				if (adapter.isMultiCheckMode()) {
					updateActionModeText(mActionMode);
				}
				updateCheckedIDs();

				AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}
		};

		torrentListAdapter = new TorrentListAdapter(activity, rs) {
			@Override
			public void lettersUpdated(HashMap<String, Integer> mapLetters) {
				if (sideFilterAdapter != null) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "lettersUpdated: ");
					}
					String[] keys = mapLetters.keySet().toArray(
							new String[mapLetters.size()]);
					Arrays.sort(keys, new Comparator<String>() {
						@Override
						public int compare(String lhs, String rhs) {
							int rsh_length = rhs.length();
							if ((rsh_length > 1) == (lhs.length() > 1)) {
								return lhs.compareTo(rhs);
							}
							return rsh_length > 1 ? -1 : 1;
						}
					});
					final ArrayList<SideFilterAdapter.SideFilterInfo> list = new ArrayList<>();
					for (String c : keys) {
						Integer count = mapLetters.get(c);
						SideFilterInfo info = new SideFilterInfo(c, count);
						list.add(info);
					}
					TorrentFilter filter = torrentListAdapter.getFilter();
					if (tvSideFilterText.getText().length() > 0
							|| !filter.getCompactDigits() || !filter.getCompactNonLetters()
							|| !filter.getCompactPunctuation()) {
						list.add(0, new SideFilterInfo(LETTERS_BS, 0));
					}

					getActivity().runOnUiThread(new Runnable() {
						@Override
						public void run() {
							FragmentActivity activity = getActivity();
							if (activity == null) {
								return;
							}
							boolean hadFocus = isChildOf(activity.getCurrentFocus(),
									listSideFilter);
							sideFilterAdapter.setItems(list);
							if (hadFocus) {
								listSideFilter.post(new Runnable() {
									@Override
									public void run() {
										listSideFilter.requestFocus();
									}
								});
							}
						}
					});
				}
			}
		};
		if (sideFilterAdapter != null) {
			torrentListAdapter.getFilter().setBuildLetters(true);
		}
		torrentListAdapter.registerAdapterDataObserver(
				new RecyclerView.AdapterDataObserver() {
					@Override
					public void onItemRangeInserted(int positionStart, int itemCount) {
						updateTorrentCount(torrentListAdapter.getItemCount());
					}

					@Override
					public void onItemRangeRemoved(int positionStart, int itemCount) {
						updateTorrentCount(torrentListAdapter.getItemCount());
					}

					@Override
					public void onChanged() {
						updateTorrentCount(torrentListAdapter.getItemCount());
					}
				});
		torrentListAdapter.setMultiCheckModeAllowed(
				!AndroidUtils.usesNavigationControl());
	}

	@Override
	public void uiReady(TransmissionRPC rpc) {
		if (getActivity() == null) {
			return;
		}

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();

		String[] sortBy = remoteProfile.getSortBy();
		Boolean[] sortOrder = remoteProfile.getSortOrder();
		if (sortBy != null) {
			int which = TorrentUtils.findSordIdFromTorrentFields(getContext(),
					sortBy);
			sortBy(sortBy, sortOrder, which, false);
		}

		long filterBy = remoteProfile.getFilterBy();
		if (filterBy > 10) {
			Map<?, ?> tag = sessionInfo.getTag(filterBy);

			filterBy(filterBy, MapUtils.getMapString(tag, "name", "fooo"), false);
		} else if (filterBy >= 0) {
			final ValueStringArray filterByList = AndroidUtils.getValueStringArray(
					getResources(), R.array.filterby_list);
			for (int i = 0; i < filterByList.values.length; i++) {
				long val = filterByList.values[i];
				if (val == filterBy) {
					filterBy(filterBy, filterByList.strings[i], false);
					break;
				}
			}
		}

	}

	@Override
	public void transmissionRpcAvailable(SessionInfo sessionInfo) {
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onCreateView(android.view
	 * .LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		fragView = inflater.inflate(R.layout.frag_torrent_list, container, false);

		setupActionModeCallback();

		final SwipeRefreshLayoutExtra swipeRefresh = (SwipeRefreshLayoutExtra) fragView.findViewById(
				R.id.swipe_container);
		if (swipeRefresh != null) {
			swipeRefresh.setExtraLayout(R.layout.swipe_layout_extra);

			LastUpdatedInfo lui = getLastUpdatedString();
			if (lui != null) {
				View extraView = swipeRefresh.getExtraView();
				if (extraView != null) {
					TextView tvSwipeText = (TextView) extraView.findViewById(
							R.id.swipe_text);
					tvSwipeText.setText(lui.s);
				}
			}
			swipeRefresh.setOnRefreshListener(
					new SwipeRefreshLayout.OnRefreshListener() {
						@Override
						public void onRefresh() {
							if (sessionInfo == null) {
								return;
							}
							sessionInfo.addTorrentListReceivedListener(
									new TorrentListReceivedListener() {

								@Override
								public void rpcTorrentListReceived(String callID,
										List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
									AndroidUtils.runOnUIThread(TorrentListFragment.this,
											new Runnable() {
										@Override
										public void run() {
											if (getActivity() == null) {
												return;
											}
											swipeRefresh.setRefreshing(false);
											LastUpdatedInfo lui = getLastUpdatedString();
											View extraView = swipeRefresh.getExtraView();
											if (extraView != null) {
												TextView tvSwipeText = (TextView) extraView.findViewById(
														R.id.swipe_text);
												tvSwipeText.setText(lui.s);
											}
										}
									});
									sessionInfo.removeTorrentListReceivedListener(this);
								}
							}, false);
							sessionInfo.triggerRefresh(true);
						}
					});
			swipeRefresh.setOnExtraViewVisibilityChange(
					new SwipeRefreshLayoutExtra.OnExtraViewVisibilityChangeListener() {
						private Handler pullRefreshHandler;

						@Override
						public void onExtraViewVisibilityChange(final View view,
								int visibility) {
							if (pullRefreshHandler != null) {
								pullRefreshHandler.removeCallbacksAndMessages(null);
								pullRefreshHandler = null;
							}
							if (visibility != View.VISIBLE) {
								return;
							}

							pullRefreshHandler = new Handler(Looper.getMainLooper());

							pullRefreshHandler.postDelayed(new Runnable() {
								@Override
								public void run() {
									if (getActivity() == null) {
										return;
									}
									LastUpdatedInfo lui = getLastUpdatedString();
									if (lui == null) {
										return;
									}
									TextView tvSwipeText = (TextView) view.findViewById(
											R.id.swipe_text);
									tvSwipeText.setText(lui.s);

									if (pullRefreshHandler != null) {
										pullRefreshHandler.postDelayed(this,
												lui.sinceMS < DateUtils.MINUTE_IN_MILLIS
														? DateUtils.SECOND_IN_MILLIS
														: lui.sinceMS < DateUtils.HOUR_IN_MILLIS
																? DateUtils.MINUTE_IN_MILLIS
																: DateUtils.HOUR_IN_MILLIS);
									}
								}
							}, 0);
						}
					});
		}

		listview = (RecyclerView) fragView.findViewById(R.id.listTorrents);
		listview.setLayoutManager(new PreCachingLayoutManager(getContext()));
		listview.setAdapter(torrentListAdapter);

		filterEditText = (EditText) fragView.findViewById(R.id.filterText);
		filterEditText.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				Filter filter = torrentListAdapter.getFilter();
				filter.filter(s);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		setupSideListArea(fragView);

		/** Handy code to watch the states of row 2
		 listview.postDelayed(new Runnable() {
		 String oldS = "";
		
		 @Override public void run() {
		
		 String s = (listview.getChildCount() < 3 ? ""
		 : AndroidUtils.getStatesString(listview.getChildAt(2).getDrawableState
		 ()));
		
		 if (!s.equals(oldS)) {
		 oldS = s;
		 Log.e(TAG, "States of 2: " + s);
		 }
		
		 listview.postDelayed(this, 500);
		 }
		 }, 500);
		 */

		setHasOptionsMenu(true);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			onCreateViewHC(fragView);
		}

		if (sideListArea != null) {
			fragView.post(new Runnable() {
				@Override
				public void run() {
					FragmentActivity activity = getActivity();
					if (activity == null) {
						return;
					}
					Window window = activity.getWindow();
					if (window == null) {
						return;
					}
					int dpHeight = window.getDecorView().getHeight();
					hideUnselectedSideHeaders = dpHeight < SIDELIST_HIDE_UNSELECTED_HEADERS_MAX_PX;
					expandSideListWidth(sidelistInFocus);
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "onAttach: hide? " + hideUnselectedSideHeaders + ";"
								+ dpHeight);
					}
				}
			});
		}

		return fragView;

	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void onCreateViewHC(View fragView) {
		if (sideListArea != null) {
			fragView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				int lastWidth = -1;

				@Override
				public void onLayoutChange(View v, int left, int top, int right,
						int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
					int width = right - left;
					if (width != lastWidth) {
						lastWidth = width;
						expandSideListWidth(sidelistInFocus);
					}
				}
			});
		}
	}

	private void setupSideListArea(View view) {
		sideListArea = (LinearLayout) view.findViewById(R.id.sidelist_layout);
		if (sideListArea == null) {
			return;
		}

		if (AndroidUtils.isTV()) {
			sideListArea.setPadding(0, 0, 0, AndroidUtilsUI.dpToPx(16));
		}

		FragmentActivity activity = getActivity();
		if (activity instanceof TorrentViewActivity) {
			((TorrentViewActivity) activity).setBottomToolbarEnabled(false);
			((TorrentViewActivity) activity).setShowGlobalActionBar(false);
		}

		if (!AndroidUtils.hasTouchScreen()) {
			// Switch SideList width based on focus.  For touch screens, we use
			// touch events.  For non-touch screens (TV) we watch for focus changes
			ViewTreeObserver vto = sideListArea.getViewTreeObserver();
			vto.addOnGlobalFocusChangeListener(
					new ViewTreeObserver.OnGlobalFocusChangeListener() {

						@Override
						public void onGlobalFocusChanged(View oldFocus, View newFocus) {

							boolean isChildOfSideList = isChildOf(newFocus, sideListArea);
							boolean isHeader = childOrParentHasTag(newFocus, "sideheader");
							if ((sidelistIsExpanded == null || sidelistIsExpanded)
									&& !isChildOfSideList) {
								//left focus
								sidelistInFocus = false;
								expandSideListWidth(false);
							} else if ((sidelistIsExpanded == null || !sidelistIsExpanded)
									&& isHeader && newFocus != listSideFilter) {
								sidelistInFocus = true;
								expandSideListWidth(true);
							}
						}
					});
		}

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			LayoutTransition layoutTransition = new LayoutTransition();
			layoutTransition.setDuration(300);
			sideListArea.setLayoutTransition(layoutTransition);
		}

		// Could have used a ExpandableListView.. oh well
		setupExpando(view, R.id.sidesort_header, R.id.sidesort_layout);
		setupExpando(view, R.id.sidetag_header, R.id.sidetag_layout);
		setupExpando(view, R.id.sidefilter_header, R.id.sidefilter_layout);
		setupExpando(view, R.id.sideactions_header, R.id.sideactions_layout);

		setupSideFilter(view);
		setupSideTags(view);
		setupSideSort(view);
		setupSideActions(view);
	}

	private boolean expandSideListWidth(Boolean expand) {
		int width = fragView.getWidth();
		boolean noExpanding = width < SIDELIST_COLLAPSE_UNTIL_WIDTH_PX;
		// We have a Motorola Xoom on Android 4.0.4 that can't handle shrinking
		// (torrent list view overlays)
		boolean noShrinking = width >= SIDELIST_KEEP_EXPANDED_AT_PX
				|| Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN;

		if (expand == null) {
			if (noExpanding && noShrinking) {
				return false;
			}
			expand = noShrinking;
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

		if (expand) {
			sizeTo(sideListArea, SIDELIST_MAX_WIDTH, SIDELIST_DURATION_MS, this);
		} else {
			sizeTo(sideListArea, SIDELIST_MIN_WIDTH, SIDELIST_DURATION_MS, this);
		}
		sidelistIsExpanded = expand;
		return true;
	}

	private void setupSideActions(View view) {
		listSideActions = (RecyclerView) view.findViewById(R.id.sideactions_list);
		if (listSideActions == null) {
			return;
		}

		listSideActions.setLayoutManager(new PreCachingLayoutManager(getContext()));

		sideActionsAdapter = new SideActionsAdapter(getContext(), sessionInfo,
				new FlexibleRecyclerSelectionListener<SideActionsAdapter, SideActionsAdapter.SideActionsInfo>() {
					@Override
					public void onItemClick(SideActionsAdapter adapter, int position) {
						SideActionsAdapter.SideActionsInfo item = adapter.getItem(position);
						if (item == null) {
							return;
						}
						if (getActivity().onOptionsItemSelected(item.menuItem)) {
							return;
						}
						int itemId = item.menuItem.getItemId();
						if (itemId == R.id.action_social) {
							MenuBuilder menuBuilder = new MenuBuilder(getContext());
							MenuInflater menuInflater = getActivity().getMenuInflater();
							menuInflater.inflate(R.menu.menu_torrent_list, menuBuilder);
							getActivity().onPrepareOptionsMenu(menuBuilder);
							MenuItem itemSocial = menuBuilder.findItem(R.id.action_social);
							if (itemSocial != null) {
								SubMenu subMenu = itemSocial.getSubMenu();
								if (subMenu instanceof SubMenuBuilder) {
									((SubMenuBuilder) subMenu).setCallback(
											new MenuBuilder.Callback() {
										@Override
										public boolean onMenuItemSelected(MenuBuilder menu,
												MenuItem item) {
											return getActivity().onOptionsItemSelected(item);
										}

										@Override
										public void onMenuModeChange(MenuBuilder menu) {

										}
									});
									MenuDialogHelper menuDialogHelper = new MenuDialogHelper(
											(SubMenuBuilder) subMenu);
									menuDialogHelper.show(null);
								}
							}
						}
					}

					@Override
					public boolean onItemLongClick(SideActionsAdapter adapter,
							int position) {
						return false;
					}

					@Override
					public void onItemSelected(SideActionsAdapter adapter, int position,
							boolean isChecked) {

					}

					@Override
					public void onItemCheckedChanged(SideActionsAdapter adapter,
							SideActionsAdapter.SideActionsInfo item, boolean isChecked) {

					}
				});
		listSideActions.setAdapter(sideActionsAdapter);
	}

	private void setupSideSort(View view) {
		listSideSort = (RecyclerView) view.findViewById(R.id.sidesort_list);
		if (listSideSort == null) {
			return;
		}

		listSideSort.setLayoutManager(new PreCachingLayoutManager(getContext()));

		sideSortAdapter = new SideSortAdapter(getContext(),
				new FlexibleRecyclerSelectionListener<SideSortAdapter, SideSortAdapter.SideSortInfo>() {
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

						SortByFields sortByFields = TorrentUtils.getSortByFields(
								getContext())[((int) item.id)];
						if (sortByFields != null) {
							if (item.id == adapter.getCurrentSort()) {
								flipSortOrder();
							} else {
								sortBy(sortByFields.sortFieldIDs, sortByFields.sortOrderAsc,
										(int) item.id, true);
							}
						}

					}
				});
		if (AndroidUtils.isTV()) {
			sideSortAdapter.setPaddingLeft(AndroidUtilsUI.dpToPx(16 - 4));
		}
		listSideSort.setAdapter(sideSortAdapter);
	}

	private void setupSideTags(View view) {
		listSideTags = (RecyclerView) view.findViewById(R.id.sidetag_list);
		if (listSideTags == null) {
			return;
		}

		listSideTags.setLayoutManager(new PreCachingLayoutManager(getContext()));

		if (getActivity() instanceof SessionInfoGetter) {
			SessionInfoGetter getter = (SessionInfoGetter) getActivity();
			sessionInfo = getter.getSessionInfo();
			sideTagAdapter = new SideTagAdapter(getContext(), sessionInfo,
					new FlexibleRecyclerSelectionListener<SideTagAdapter, SideTagAdapter.SideTagInfo>() {
						@Override
						public void onItemClick(SideTagAdapter adapter, int position) {
						}

						@Override
						public boolean onItemLongClick(SideTagAdapter adapter,
								int position) {
							return false;
						}

						@Override
						public void onItemSelected(SideTagAdapter adapter, int position,
								boolean isChecked) {
						}

						@Override
						public void onItemCheckedChanged(SideTagAdapter adapter,
								SideTagAdapter.SideTagInfo item, boolean isChecked) {

							if (!isChecked) {
								return;
							}
							adapter.setItemChecked(item, false);

							filterBy(item.id, MapUtils.getMapString(item.tag, "name", ""),
									true);
						}
					});

			listSideTags.setAdapter(sideTagAdapter);

			if (!sessionInfo.getSupportsTags()) {
				// TRANSMISSION
				ValueStringArray filterByList = AndroidUtils.getValueStringArray(
						getResources(), R.array.filterby_list);

				for (int i = 0; i < filterByList.strings.length; i++) {
					long id = filterByList.values[i];
					Map map = new HashMap();
					map.put("uid", id);
					SideTagAdapter.SideTagInfo sideTagInfo = new SideTagAdapter.SideTagInfo(
							map);
					sideTagAdapter.addItem(sideTagInfo);
				}
			}
		}
	}

	private void setupSideFilter(View view) {
		listSideFilter = (RecyclerView) view.findViewById(R.id.sidefilter_list);
		if (listSideFilter == null) {
			return;
		}
		if (torrentListAdapter != null) {
			torrentListAdapter.getFilter().setBuildLetters(true);
		}

		tvSideFilterText = (TextView) view.findViewById(R.id.sidefilter_text);
		if (AndroidUtils.isTV()) {
			ViewGroup.LayoutParams lp = tvSideFilterText.getLayoutParams();
			if (lp instanceof LinearLayout.LayoutParams) {
				((LinearLayout.LayoutParams) lp).setMargins(AndroidUtilsUI.dpToPx(16),
						0, AndroidUtilsUI.dpToPx(16), 0);
			}
		}

		tvSideFilterText.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				Filter filter = torrentListAdapter.getFilter();
				filter.filter(s);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		listSideFilter.setLayoutManager(new PreCachingLayoutManager(getContext()));

		sideFilterAdapter = new SideFilterAdapter(getContext(),
				new FlexibleRecyclerSelectionListener<SideFilterAdapter, SideFilterAdapter.SideFilterInfo>() {
					@Override
					public void onItemCheckedChanged(SideFilterAdapter adapter,
							SideFilterAdapter.SideFilterInfo item, boolean isChecked) {
						if (!isChecked) {
							return;
						}
						adapter.setItemChecked(item, false);

						String s = item.letters;
						if (s.equals(LETTERS_NUMBERS)) {
							TorrentFilter filter = torrentListAdapter.getFilter();
							filter.setCompactDigits(false);
							filter.refilter();
							return;
						}
						if (s.equals(LETTERS_NON)) {
							TorrentFilter filter = torrentListAdapter.getFilter();
							filter.setCompactOther(false);
							filter.refilter();
							return;
						}
						if (s.equals(LETTERS_PUNCTUATION)) {
							TorrentFilter filter = torrentListAdapter.getFilter();
							filter.setCompactPunctuation(false);
							filter.refilter();
							return;
						}
						if (s.equals(LETTERS_BS)) {
							CharSequence text = tvSideFilterText.getText();
							if (text.length() > 0) {
								text = text.subSequence(0, text.length() - 1);
								tvSideFilterText.setText(text);
							} else {
								TorrentFilter filter = torrentListAdapter.getFilter();
								filter.setCompactPunctuation(true);
								filter.setCompactDigits(true);
								filter.setCompactOther(true);
								filter.refilter();
							}
							return;
						}
						s = tvSideFilterText.getText() + s;
						tvSideFilterText.setText(s);
					}

					@Override
					public boolean onItemLongClick(SideFilterAdapter adapter,
							int position) {
						return false;
					}

					@Override
					public void onItemSelected(SideFilterAdapter adapter, int position,
							boolean isChecked) {

					}

					@Override
					public void onItemClick(SideFilterAdapter adapter, int position) {

					}
				});
		listSideFilter.setAdapter(sideFilterAdapter);

	}

	private void setupExpando(View view, int id_header, int id_body) {
		ViewGroup vgHeader = (ViewGroup) view.findViewById(id_header);
		final ViewGroup vgBody = (ViewGroup) view.findViewById(id_body);
		if (vgBody == null || vgHeader == null) {
			return;
		}
		listHeaderViewGroups.add(vgHeader);
		vgHeader.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				sidelistInFocus = true;
				// Consume touch event if user clicked the active sidelist view to
				// expand it
				// Otherwise, the active sidelist content would be collaped
				return expandSideListWidth(true) && sidebarViewActive == vgBody;
			}
		});
		vgHeader.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!sidelistIsExpanded) {
					return;
				}
				boolean same = sidebarViewActive == vgBody;
				if (sidebarViewActive != null) {
					sidebarViewActive.setVisibility(View.GONE);
				}
				if (same) {
					sidebarViewActive = null;
					if (hideUnselectedSideHeaders) {
						for (ViewGroup vgHeader : listHeaderViewGroups) {
							vgHeader.setVisibility(View.VISIBLE);
						}
					}
				} else {
					vgBody.setVisibility(View.VISIBLE);
					sidebarViewActive = vgBody;

					if (hideUnselectedSideHeaders) {
						for (ViewGroup vgHeader : listHeaderViewGroups) {
							vgHeader.setVisibility(vgHeader == v ? View.VISIBLE : View.GONE);
						}
					}
				}

				if (tvSideFilterText != null && listSideFilter != null) {
					tvSideFilterText.setVisibility(
							tvSideFilterText.getText().length() == 0
									&& ((View) listSideFilter.getParent()).getVisibility() == View.GONE
											? View.GONE : View.VISIBLE);
				}

			}
		});
	}

	public static boolean childOrParentHasTag(View child, String tag) {
		if (child == null || tag == null) {
			return false;
		}
		if (tag.equals(child.getTag())) {
			return true;
		}
		ViewParent parent = child.getParent();
		while (parent != null) {

			if ((parent instanceof View) && tag.equals(((View) parent).getTag())) {
				return true;
			}
			parent = parent.getParent();
		}
		return false;

	}

	public static boolean isChildOf(View child, ViewGroup vg) {
		if (child == null || vg == null) {
			return false;
		}
		ViewParent parent = child.getParent();
		while (parent != null) {
			if (parent == vg) {
				return true;
			}
			parent = parent.getParent();
		}
		return false;
	}

	public static void sizeTo(final View v, int finalWidth, int durationMS,
			Animation.AnimationListener listener) {
		final int initalWidth = v.getMeasuredWidth();

		final int diff = finalWidth - initalWidth;

		final int multiplier = diff < 0 ? -1 : 0;

		Animation a = new Animation() {
			@Override
			protected void applyTransformation(float interpolatedTime,
					Transformation t) {
				v.getLayoutParams().width = initalWidth
						+ ((int) (diff * interpolatedTime));
				v.requestLayout();
			}

			@Override
			public boolean willChangeBounds() {
				return true;
			}
		};

		a.setAnimationListener(listener);

		if (durationMS < 0) {
			// 1dp/ms
			a.setDuration((int) ((diff * multiplier)
					/ v.getContext().getResources().getDisplayMetrics().density));
		} else {
			a.setDuration(durationMS);
		}
		v.startAnimation(a);
	}

	@Override
	public void onAnimationStart(Animation animation) {

	}

	@Override
	public void onAnimationEnd(Animation animation) {
		if (sideActionsAdapter != null) {
			sideActionsAdapter.notifyDataSetChanged();
		}
		if (sideSortAdapter != null) {
			sideSortAdapter.notifyDataSetInvalidated();
		}
	}

	@Override
	public void onAnimationRepeat(Animation animation) {

	}

	@Override
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_UP) {
			return false;
		}
		switch (keyCode) {
			// NOTE:
			// KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_MENU);
			case KeyEvent.KEYCODE_MENU:
			case KeyEvent.KEYCODE_BUTTON_X:
			case KeyEvent.KEYCODE_INFO: {
				if (tb == null) {
					return showTorrentContextMenu();
				}
				break;
			}

		}
		return false;
	}

	private boolean showTorrentContextMenu() {
		int selectedPosition = torrentListAdapter.getSelectedPosition();
		if (selectedPosition < 0) {
			return false;
		}
		String s;
		int checkedItemCount = torrentListAdapter.getCheckedItemCount();
		if (checkedItemCount <= 1) {
			Map<?, ?> item = torrentListAdapter.getTorrentItem(selectedPosition);
			s = getResources().getString(R.string.torrent_actions_for,
					MapUtils.getMapString(item, "name", "???"));
		} else {
			s = getResources().getQuantityString(
					R.plurals.torrent_actions_for_multiple, checkedItemCount,
					checkedItemCount);
		}

		return AndroidUtilsUI.popupContextMenu(getContext(), this, s);
	}

	@Override
	public void sessionSettingsChanged(SessionSettings newSessionSettings) {
		boolean isSmallNew = sessionInfo.getRemoteProfile().useSmallLists();
		if (isSmall != null && isSmallNew != isSmall) {
			torrentListAdapter.setViewType(isSmallNew ? 1 : 0);
		}
		isSmall = isSmallNew;

		if (sideActionsAdapter != null) {
			sideActionsAdapter.updateMenuItems();
		}
	}

	@Override
	public void speedChanged(long downloadSpeed, long uploadSpeed) {

	}

	@Override
	public void rpcTorrentListRefreshingChanged(boolean refreshing) {
		AndroidUtils.runOnUIThread(this, new AndroidUtils.RunnableWithActivity() {
			@Override
			public void run() {
				if (getActivity() == null) {
					return;
				}
				if (sideActionsAdapter != null) {
					sideActionsAdapter.updateRefreshButton();
				}
			}
		});
	}

	@Override
	public void onlineStateChanged(boolean isOnline) {
		if (sideActionsAdapter == null) {
			return;
		}
		AndroidUtils.runOnUIThread(this, new AndroidUtils.RunnableWithActivity() {
			@Override
			public void run() {
				if (getActivity() == null) {
					return;
				}
				if (sideActionsAdapter != null) {
					sideActionsAdapter.updateMenuItems();
				}
			}
		});
	}

	private class LastUpdatedInfo
	{
		long sinceMS;

		String s;

		public LastUpdatedInfo(long sinceMS, String s) {
			this.sinceMS = sinceMS;
			this.s = s;
		}
	}

	private LastUpdatedInfo getLastUpdatedString() {
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return null;
		}
		long lastUpdated = sessionInfo == null ? 0
				: sessionInfo.getLastTorrentListReceivedOn();
		long sinceMS = System.currentTimeMillis() - lastUpdated;
		String since = DateUtils.getRelativeDateTimeString(activity, lastUpdated,
				DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString();
		String s = activity.getResources().getString(R.string.last_updated, since);

		return new LastUpdatedInfo(sinceMS, s);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (DEBUG) {
			Log.d(TAG, "onSaveInstanceState");
		}
		if (torrentListAdapter != null) {
			torrentListAdapter.onSaveInstanceState(outState);
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onViewStateRestored(Bundle savedInstanceState) {
		if (DEBUG) {
			Log.d(TAG, "onViewStateRestored");
		}
		super.onViewStateRestored(savedInstanceState);
		if (torrentListAdapter != null) {
			torrentListAdapter.onRestoreInstanceState(savedInstanceState, listview);
		}
		if (listview != null) {
			updateCheckedIDs();
		}
	}

	@Override
	public void onStart() {
		if (DEBUG) {
			Log.d(TAG, "onStart");
		}
		super.onStart();
		VuzeEasyTracker.getInstance(this).fragmentStart(this, TAG);
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onResume()
	 */
	@Override
	public void onResume() {
		if (DEBUG) {
			Log.d(TAG, "onResume");
		}
		super.onResume();

		VuzeRemoteApp.getNetworkState().addListener(this);

		if (getActivity() instanceof SessionInfoGetter) {
			SessionInfoGetter getter = (SessionInfoGetter) getActivity();
			sessionInfo = getter.getSessionInfo();
			torrentListAdapter.setSessionInfo(sessionInfo);
			sessionInfo.addTorrentListReceivedListener(TAG, this);
			if (listSideTags != null) {
				sessionInfo.addTagListReceivedListener(this);
			}
			sessionInfo.addRpcAvailableListener(this);
			sessionInfo.addSessionSettingsChangedListeners(this);
			sessionInfo.addTorrentListRefreshingListener(this, false);
		}
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onPause()
	 */
	@Override
	public void onPause() {
		VuzeRemoteApp.getNetworkState().removeListener(this);

		if (sessionInfo != null) {
			if (listSideTags != null) {
				sessionInfo.removeTagListReceivedListener(this);
			}
			sessionInfo.removeTorrentListReceivedListener(this);
			sessionInfo.removeTorrentListRefreshingListener(this);
			sessionInfo.removeSessionSettingsChangedListeners(this);
		}
		super.onPause();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		if (DEBUG) {
			Log.d(TAG, "onActivityCreated");
		}
		FragmentActivity activity = getActivity();
		tvFilteringBy = (TextView) activity.findViewById(R.id.wvFilteringBy);
		tvTorrentCount = (TextView) activity.findViewById(R.id.wvTorrentCount);
		tb = (Toolbar) activity.findViewById(R.id.toolbar_bottom);

		super.onActivityCreated(savedInstanceState);
	}

	public void finishActionMode() {
		if (mActionMode != null) {
			mActionMode.finish();
			mActionMode = null;
		}
		torrentListAdapter.clearChecked();
	}

	private static Map<?, ?>[] getCheckedTorrentMaps(TorrentListAdapter adapter) {
		if (adapter == null) {
			return new Map[0];
		}
		int[] checkedItems = adapter.getCheckedItemPositions();
		if (checkedItems.length == 0) {
			int selectedPosition = adapter.getSelectedPosition();
			if (selectedPosition < 0) {
				return new Map[0];
			}
			checkedItems = new int[] {
				selectedPosition
			};
		}

		List<Map> list = new ArrayList<>();

		for (int position : checkedItems) {
			Map<?, ?> torrent = adapter.getTorrentItem(position);
			if (torrent != null) {
				list.add(torrent);
			}
		}

		return list.toArray(new Map[list.size()]);
	}

	private static long[] getCheckedIDs(TorrentListAdapter adapter,
			boolean includeSelected) {
		if (adapter == null) {
			return new long[] {};
		}
		int[] checkedItems = adapter.getCheckedItemPositions();

		List<Long> list = new ArrayList<>();
		if (checkedItems.length == 0) {
			if (!includeSelected) {
				return new long[] {};
			}
			int selectedPosition = adapter.getSelectedPosition();
			if (selectedPosition < 0) {
				return new long[0];
			}
			long torrentID = adapter.getTorrentID(selectedPosition);
			return torrentID < 0 ? new long[0] : new long[] {
				torrentID
			};
		} else {
			for (int position : checkedItems) {
				long torrentID = adapter.getTorrentID(position);
				if (torrentID >= 0) {
					list.add(torrentID);
				}
			}
		}

		long[] longs = new long[list.size()];
		for (int i = 0; i < list.size(); i++) {
			longs[i] = list.get(i);
		}

		return longs;
	}

	@Override
	public void rpcTorrentListReceived(String callID, List<?> addedTorrentMaps,
			List<?> removedTorrentIDs) {
		if ((addedTorrentMaps == null || addedTorrentMaps.size() == 0)
				&& (removedTorrentIDs == null || removedTorrentIDs.size() == 0)) {
			return;
		}
		AndroidUtils.runOnUIThread(this, new AndroidUtils.RunnableWithActivity() {
			@Override
			public void run() {
				if (getActivity() == null) {
					return;
				}
				if (torrentListAdapter == null) {
					return;
				}
				torrentListAdapter.refreshDisplayList();
			}
		});
	}

	/* (non-Javadoc)
		 * @see android.support.v4.app.Fragment#onOptionsItemSelected(android.view
		 * .MenuItem)
		 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onOptionsItemSelected " + item.getTitle());
		}
		return handleFragmentMenuItems(item.getItemId())
				|| super.onOptionsItemSelected(item);
	}

	private boolean handleFragmentMenuItems(int itemId) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "HANDLE MENU FRAG " + itemId);
		}
		if (sessionInfo == null) {
			return false;
		}

		if (itemId == R.id.action_filterby) {
			DialogFragmentFilterBy.openFilterByDialog(this, sessionInfo,
					sessionInfo.getRemoteProfile().getID());
			return true;
		} else if (itemId == R.id.action_filter) {
			boolean newVisibility = filterEditText.getVisibility() != View.VISIBLE;
			filterEditText.setVisibility(newVisibility ? View.VISIBLE : View.GONE);
			if (newVisibility) {
				filterEditText.requestFocus();
				InputMethodManager mgr = (InputMethodManager) getActivity().getSystemService(
						Context.INPUT_METHOD_SERVICE);
				mgr.showSoftInput(filterEditText, InputMethodManager.SHOW_IMPLICIT);
				VuzeEasyTracker.getInstance(this).sendEvent("uiAction", "ViewShown",
						"FilterBox", null);
			} else {
				InputMethodManager mgr = (InputMethodManager) getActivity().getSystemService(
						Context.INPUT_METHOD_SERVICE);
				mgr.hideSoftInputFromWindow(filterEditText.getWindowToken(), 0);
			}
			return true;
		} else if (itemId == R.id.action_sortby) {
			DialogFragmentSortBy.open(getFragmentManager(), this);
			return true;
		}
		return handleTorrentMenuActions(sessionInfo,
				getCheckedIDs(torrentListAdapter, true), getFragmentManager(), itemId);
	}

	public static boolean handleTorrentMenuActions(SessionInfo sessionInfo,
			final long[] ids, FragmentManager fm, int itemId) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "HANDLE TORRENTMENU FRAG " + itemId);
		}
		if (sessionInfo == null || ids == null || ids.length == 0) {
			return false;
		}
		if (itemId == R.id.action_sel_remove) {
			for (long torrentID : ids) {
				Map<?, ?> map = sessionInfo.getTorrent(torrentID);
				long id = MapUtils.getMapLong(map, "id", -1);
				String name = MapUtils.getMapString(map, "name", "");
				// TODO: One at a time!
				DialogFragmentDeleteTorrent.open(fm, sessionInfo, name, id);
			}
			return true;
		} else if (itemId == R.id.action_sel_start) {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.startTorrents(TAG, ids, false, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_forcestart) {
			sessionInfo.executeRpc(new RpcExecuter() {

				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.startTorrents(TAG, ids, true, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_stop) {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.stopTorrents(TAG, ids, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_relocate)

		{
			Map<?, ?> mapFirst = sessionInfo.getTorrent(ids[0]);
			DialogFragmentMoveData.openMoveDataDialog(mapFirst, sessionInfo, fm);
			return true;
		} else if (itemId == R.id.action_sel_move_top)

		{
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall("queue-move-top", ids, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_move_up)

		{
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall("queue-move-up", ids, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_move_down)

		{
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall("queue-move-down", ids, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_move_bottom)

		{
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall("queue-move-bottom", ids, null);
				}
			});
			return true;
		}
		return false;

	}

	public void updateActionModeText(ActionMode mode) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "MULTI:CHECK CHANGE");
		}

		if (mode != null) {
			String subtitle = getResources().getString(
					R.string.context_torrent_subtitle_selected,
					torrentListAdapter.getCheckedItemCount());
			mode.setSubtitle(subtitle);
		}
	}

	private void setupActionModeCallback() {
		mActionModeCallbackV7 = new Callback() {

			// Called when the action mode is created; startActionMode() was called
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {

				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "onCreateActionMode");
				}

				if (mode == null && torrentListAdapter.getCheckedItemCount() == 0
						&& torrentListAdapter.getSelectedPosition() < 0) {
					return false;
				}

				Menu origMenu = menu;
				if (tb != null) {
					menu = tb.getMenu();
				}
				if (mode != null) {
					mActionMode = (mode instanceof ActionModeWrapperV7) ? mode
							: new ActionModeWrapperV7(mode, tb, getActivity());

					mActionMode.setTitle(R.string.context_torrent_title);
				}
				ActionBarToolbarSplitter.buildActionBar(getActivity(), this,
						R.menu.menu_context_torrent_details, menu, tb);

				TorrentDetailsFragment frag = (TorrentDetailsFragment) getActivity().getSupportFragmentManager().findFragmentById(
						R.id.frag_torrent_details);
				if (frag != null) {
					frag.onCreateActionMode(mode, menu);
				}

				if (sideListArea == null) {
					SubMenu subMenu = origMenu.addSubMenu(R.string.menu_global_actions);
					subMenu.setIcon(R.drawable.ic_menu_white_24dp);
					MenuItemCompat.setShowAsAction(subMenu.getItem(),
							MenuItemCompat.SHOW_AS_ACTION_NEVER);

					try {
						// Place "Global" actions on top bar in collapsed menu
						MenuInflater mi = mode == null ? getActivity().getMenuInflater()
								: mode.getMenuInflater();
						mi.inflate(R.menu.menu_torrent_list, subMenu);
						onPrepareOptionsMenu(subMenu);
					} catch (UnsupportedOperationException e) {
						Log.e(TAG, e.getMessage());
						menu.removeItem(subMenu.getItem().getItemId());
					}
				}

				if (AndroidUtils.usesNavigationControl()) {
					MenuItem add = origMenu.add(R.string.select_multiple_items);
					add.setCheckable(true);
					add.setChecked(torrentListAdapter.isMultiCheckMode());
					add.setOnMenuItemClickListener(
							new MenuItem.OnMenuItemClickListener() {
						@Override
						public boolean onMenuItemClick(MenuItem item) {
							boolean turnOn = !torrentListAdapter.isMultiCheckModeAllowed();

							torrentListAdapter.setMultiCheckModeAllowed(turnOn);
							if (turnOn) {
								torrentListAdapter.setMultiCheckMode(turnOn);
								torrentListAdapter.setItemChecked(
										torrentListAdapter.getSelectedPosition(), true);
							}
							return true;
						}
					});
				}

				return true;
			}

			// Called each time the action mode is shown. Always called after
			// onCreateActionMode, but
			// may be called multiple times if the mode is invalidated.
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {

				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "MULTI:onPrepareActionMode " + mode);
				}
				if (tb != null) {
					menu = tb.getMenu();
				}

				// Must be called first, because our drawer sets all menu items
				// visible.. :(
				getActivity().onPrepareOptionsMenu(menu);

				prepareContextMenu(menu);

				TorrentDetailsFragment frag = (TorrentDetailsFragment) getActivity().getSupportFragmentManager().findFragmentById(
						R.id.frag_torrent_details);
				if (frag != null) {
					frag.onPrepareActionMode(mode, menu);
				}

				AndroidUtils.fixupMenuAlpha(menu);

				return true;
			}

			// Called when the user selects a contextual menu item
			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "onActionItemClicked " + item.getTitle());
				}

				if (TorrentListFragment.this.handleFragmentMenuItems(
						item.getItemId())) {
					return true;
				}
				if (getActivity().onOptionsItemSelected(item)) {
					return true;
				}
				TorrentDetailsFragment frag = (TorrentDetailsFragment) getActivity().getSupportFragmentManager().findFragmentById(
						R.id.frag_torrent_details);
				if (frag != null) {
					if (frag.onActionItemClicked(mode, item)) {
						return true;
					}
				}
				return false;
			}

			// Called when the user exits the action mode
			@Override
			public void onDestroyActionMode(ActionMode mode) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG,
							"onDestroyActionMode. BeingReplaced?" + actionModeBeingReplaced);
				}

				mActionMode = null;

				if (!actionModeBeingReplaced) {
					listview.post(new Runnable() {
						@Override
						public void run() {
							torrentListAdapter.setMultiCheckMode(false);
							torrentListAdapter.clearChecked();
							updateCheckedIDs();
						}
					});

					listview.post(new Runnable() {
						@Override
						public void run() {
							if (mCallback != null) {
								mCallback.actionModeBeingReplacedDone();
							}
						}
					});

					listview.setLongClickable(true);
					listview.requestLayout();
					AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
				}
			}
		};
	}

	protected void prepareContextMenu(Menu menu) {
		boolean isLocalHost = sessionInfo != null
				&& sessionInfo.getRemoteProfile().isLocalHost();
		boolean isOnlineOrLocal = VuzeRemoteApp.getNetworkState().isOnline()
				|| isLocalHost;

		MenuItem menuMove = menu.findItem(R.id.action_sel_move);
		if (menuMove != null) {
			boolean enabled = isOnlineOrLocal
					&& (torrentListAdapter.getCheckedItemCount() > 0
							|| torrentListAdapter.getSelectedPosition() >= 0);
			menuMove.setEnabled(enabled);
		}

		Map<?, ?>[] checkedTorrentMaps = getCheckedTorrentMaps(torrentListAdapter);
		boolean canStart = false;
		boolean canStop = false;
		if (isOnlineOrLocal) {
			for (Map<?, ?> mapTorrent : checkedTorrentMaps) {
				int status = MapUtils.getMapInt(mapTorrent,
						TransmissionVars.FIELD_TORRENT_STATUS,
						TransmissionVars.TR_STATUS_STOPPED);
				canStart |= status == TransmissionVars.TR_STATUS_STOPPED;
				canStop |= status != TransmissionVars.TR_STATUS_STOPPED;
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG,
							"prepareContextMenu: " + canStart + "/" + canStop + "/" + status);
				}
			}
		}
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "prepareContextMenu: " + canStart + "/" + canStop);
		}

		MenuItem menuStart = menu.findItem(R.id.action_sel_start);
		if (menuStart != null) {
			menuStart.setVisible(canStart);
			menuStart.setEnabled(canStart);
		} else {
			Log.d(TAG, "prepareContextMenu: No Start Menu!");
		}

		MenuItem menuStop = menu.findItem(R.id.action_sel_stop);
		if (menuStop != null) {
			menuStop.setVisible(canStop);
			menuStop.setEnabled(canStop);
		}

		AndroidUtilsUI.setManyMenuItemsEnabled(isOnlineOrLocal, menu, new int[] {
			R.id.action_sel_remove,
			R.id.action_sel_forcestart,
			R.id.action_sel_move,
			R.id.action_sel_relocate
		});
	}

	private boolean showContextualActions(boolean forceRebuild) {
		if (AndroidUtils.isTV()) {
			// TV doesn't get action bar changes, because it's impossible to get to
			// with remote control when you are on row 4000
			return false;
		}
		if (mActionMode != null && !forceRebuild) {
			if (AndroidUtils.DEBUG_MENU) {
				Log.d(TAG, "showContextualActions: invalidate existing");
			}
			mActionMode.invalidate();
			return false;
		}

		if (mCallback != null) {
			mCallback.setActionModeBeingReplaced(mActionMode, true);
		}
		// Start the CAB using the ActionMode.Callback defined above
		FragmentActivity activity = getActivity();
		if (activity instanceof AppCompatActivity) {
			AppCompatActivity abActivity = (AppCompatActivity) activity;
			if (AndroidUtils.DEBUG_MENU) {
				ActionBar ab = abActivity.getSupportActionBar();
				Log.d(TAG,
						"showContextualActions: startAB. mActionMode = " + mActionMode
								+ "; isShowing=" + (ab == null ? null : ab.isShowing()));
			}

			actionModeBeingReplaced = true;

			ActionMode am = abActivity.startSupportActionMode(mActionModeCallbackV7);
			actionModeBeingReplaced = false;
			mActionMode = new ActionModeWrapperV7(am, tb, getActivity());
			mActionMode.setSubtitle(R.string.multi_select_tip);
			mActionMode.setTitle(R.string.context_torrent_title);
		}
		if (mCallback != null) {
			mCallback.setActionModeBeingReplaced(mActionMode, false);
		}

		return true;
	}

	@Override
	public void filterBy(final long filterMode, final String name, boolean save) {
		if (DEBUG) {
			Log.d(TAG, "FILTER BY " + name);
		}

		AndroidUtils.runOnUIThread(this, new Runnable() {
			public void run() {
				if (getActivity() == null) {
					return;
				}
				if (torrentListAdapter == null) {
					if (DEBUG) {
						Log.d(TAG, "No torrentListAdapter in filterBy");
					}
					return;
				}
				// java.lang.RuntimeException: Can't create handler inside thread that
				// has not called Looper.prepare()
				TorrentFilter filter = torrentListAdapter.getFilter();
				filter.setFilterMode(filterMode);
				if (tvFilteringBy != null) {
					Map<?, ?> tag = sessionInfo.getTag(filterMode);
					SpanTags spanTags = new SpanTags();
					spanTags.init(getContext(), sessionInfo, tvFilteringBy, null);
					spanTags.setCountFontRatio(0.8f);
					if (tag == null) {
						spanTags.addTagNames(Collections.singletonList(name));
					} else {
						ArrayList<Map<?, ?>> arrayList = new ArrayList<>(1);
						arrayList.add(tag);
						spanTags.setTagMaps(arrayList);
					}
					spanTags.setShowIcon(false);
					spanTags.updateTags();
				} else {
					if (DEBUG) {
						Log.d(TAG, "null field in filterBy");
					}
				}
			}
		});
		if (save) {
			sessionInfo.getRemoteProfile().setFilterBy(filterMode);
			sessionInfo.saveProfile();
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentSortBy
	 * .SortByDialogListener#sortBy(java.lang.String[], java.lang.Boolean[],
	 * boolean)
	 */
	@Override
	public void sortBy(final String[] sortFieldIDs, final Boolean[] sortOrderAsc,
			final int which, boolean save) {
		if (DEBUG) {
			Log.d(TAG, "SORT BY " + Arrays.toString(sortFieldIDs));
		}
		if (torrentListAdapter != null) {
			torrentListAdapter.setSort(sortFieldIDs, sortOrderAsc);
		}
		AndroidUtils.runOnUIThread(this, new Runnable() {
			public void run() {
				if (getActivity() == null) {
					return;
				}
				if (sideSortAdapter != null) {
					sideSortAdapter.setCurrentSort(which, sortOrderAsc[0]);
				}

			}
		});

		if (save && sessionInfo != null) {
			sessionInfo.getRemoteProfile().setSortBy(sortFieldIDs, sortOrderAsc);
			sessionInfo.saveProfile();
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentSortBy
	 * .SortByDialogListener#flipSortOrder()
	 */
	@Override
	public void flipSortOrder() {
		if (sessionInfo == null) {
			return;
		}
		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		if (remoteProfile == null) {
			return;
		}
		Boolean[] sortOrder = remoteProfile.getSortOrder();
		if (sortOrder == null) {
			return;
		}
		for (int i = 0; i < sortOrder.length; i++) {
			sortOrder[i] = !sortOrder[i];
		}
		sortBy(remoteProfile.getSortBy(), sortOrder,
				TorrentUtils.findSordIdFromTorrentFields(getContext(),
						remoteProfile.getSortBy()),
				true);
	}

	private void updateTorrentCount(final long total) {
		if (tvTorrentCount == null) {
			return;
		}
		AndroidUtils.runOnUIThread(this, new Runnable() {
			public void run() {
				if (getActivity() == null) {
					return;
				}
				String s = "";
				if (total != 0) {
					String constraint = torrentListAdapter.getFilter().getConstraint();
					if (constraint != null && constraint.length() > 0) {
						s = getResources().getQuantityString(R.plurals.torrent_count,
								(int) total, new Object[] {
									total
						});
					}
				}
				tvTorrentCount.setText(s);
			}
		});
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment
	 * .ActionModeBeingReplacedListener#setActionModeBeingReplaced(boolean)
	 */
	@Override
	public void setActionModeBeingReplaced(ActionMode actionMode,
			boolean actionModeBeingReplaced) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG,
					"setActionModeBeingReplaced: replaced? " + actionModeBeingReplaced
							+ "; hasActionMode? " + (mActionMode != null));
		}
		this.actionModeBeingReplaced = actionModeBeingReplaced;
		if (actionModeBeingReplaced) {
			rebuildActionMode = mActionMode != null;
			if (rebuildActionMode) {
				mActionMode.finish();
				mActionMode = null;
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment
	 * .ActionModeBeingReplacedListener#actionModeBeingReplacedDone()
	 */
	@Override
	public void actionModeBeingReplacedDone() {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "actionModeBeingReplacedDone: rebuild? " + rebuildActionMode);
		}
		if (rebuildActionMode) {
			rebuildActionMode = false;

			rebuildActionMode();
			torrentListAdapter.setMultiCheckMode(false);
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment
	 * .ActionModeBeingReplacedListener#getActionMode()
	 */
	@Override
	public ActionMode getActionMode() {
		return mActionMode;
	}

	public void clearSelection() {
		finishActionMode();
	}

	private void updateCheckedIDs() {
		long[] checkedTorrentIDs = getCheckedIDs(torrentListAdapter, false);
		if (mCallback != null) {
			mCallback.onTorrentSelectedListener(TorrentListFragment.this,
					checkedTorrentIDs, torrentListAdapter.isMultiCheckMode());
		}
		if (checkedTorrentIDs.length == 0 && mActionMode != null) {
			mActionMode.finish();
		}
	}

	@Override
	public void rebuildActionMode() {
		showContextualActions(true);
	}

	public void startStopTorrents() {
		Map<?, ?>[] checkedTorrentMaps = getCheckedTorrentMaps(torrentListAdapter);
		if (checkedTorrentMaps == null || checkedTorrentMaps.length == 0) {
			return;
		}
		//boolean canStart = false;
		boolean canStop = false;
		for (Map<?, ?> mapTorrent : checkedTorrentMaps) {
			int status = MapUtils.getMapInt(mapTorrent,
					TransmissionVars.FIELD_TORRENT_STATUS,
					TransmissionVars.TR_STATUS_STOPPED);
			//canStart |= status == TransmissionVars.TR_STATUS_STOPPED;
			canStop |= status != TransmissionVars.TR_STATUS_STOPPED;
		}

		if (!canStop) {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					long[] ids = getCheckedIDs(torrentListAdapter, true);
					rpc.stopTorrents(TAG, ids, null);
				}
			});
		} else {
			sessionInfo.executeRpc(new RpcExecuter() {

				@Override
				public void executeRpc(TransmissionRPC rpc) {
					long[] ids = getCheckedIDs(torrentListAdapter, true);
					rpc.startTorrents(TAG, ids, false, null);
				}

			});
		}
	}

	@Override
	public Callback getActionModeCallback() {
		return mActionModeCallbackV7;
	}

	private boolean isViewContains(View view, int rx, int ry) {
		Rect rect = new Rect();
		view.getGlobalVisibleRect(rect);

		return rect.contains(rx, ry);
	}

	public void onTouchEvent(MotionEvent event) {
		if (sideListArea == null) {
			return;
		}
		int action = event.getAction() & 0xff;
		if (action != MotionEvent.ACTION_DOWN
				&& action != MotionEvent.ACTION_POINTER_DOWN) {
			return;
		}
		boolean newHasFocus = isViewContains(sideListArea, (int) event.getX(),
				(int) event.getY());
		if ((sidelistIsExpanded == null || sidelistIsExpanded) && !newHasFocus) {
			//left focus
			sidelistInFocus = false;
			expandSideListWidth(false);
			// uncomment this if you want the sidelist to expand upon touch of
			// sidelist area
//		} else if ((sidelistIsExpanded == null || !sidelistIsExpanded)
//				&& newHasFocus) {
//			expandSideListWidth(true);
//			sidelistInFocus = true;
		}
	}

	@Override
	public void tagListReceived(List<Map<?, ?>> tags) {
		if (sideTagAdapter == null || tags == null) {
			return;
		}
		List<SideTagAdapter.SideTagInfo> list = new ArrayList<>();
		for (Map tag : tags) {
			if (MapUtils.getMapLong(tag, "count", 0) > 0) {
				list.add(new SideTagAdapter.SideTagInfo(tag));
			}
		}
		sideTagAdapter.setItems(list);
	}

}
