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

package com.biglybt.android.client.fragment;

import java.util.*;

import com.biglybt.android.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.DrawerActivity;
import com.biglybt.android.client.activity.TorrentViewActivity;
import com.biglybt.android.client.adapter.*;
import com.biglybt.android.client.adapter.TorrentListAdapter.TorrentFilter;
import com.biglybt.android.client.dialog.DialogFragmentDeleteTorrent;
import com.biglybt.android.client.dialog.DialogFragmentMoveData;
import com.biglybt.android.client.rpc.*;
import com.biglybt.android.client.session.*;
import com.biglybt.android.client.session.Session.RpcExecuter;
import com.biglybt.android.client.spanbubbles.SpanTags;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.NetworkState;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.android.widget.SwipeRefreshLayoutExtra;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.util.LongSparseArray;
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
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.*;
import android.widget.TextView;

/**
 * Handles a ListView that shows Torrents
 */
public class TorrentListFragment
	extends Fragment
	implements TorrentListReceivedListener, SessionListener,
	ActionModeBeingReplacedListener, TagListReceivedListener, View.OnKeyListener,
	SessionSettingsChangedListener, TorrentListRefreshingListener,
	NetworkState.NetworkStateListener, SideListHelper.SideSortAPI

{
	@Thunk
	static final boolean DEBUG = AndroidUtils.DEBUG;

	private static final String TAG = "TorrentList";

	private static final int SIDELIST_MAX_WIDTH = BiglyBTApp.getContext().getResources().getDimensionPixelSize(
			R.dimen.sidelist_max_width);

	private static final int SIDELIST_MIN_WIDTH = BiglyBTApp.getContext().getResources().getDimensionPixelSize(
			R.dimen.sidelist_min_width);

	// Shrink sidelist, typically for 7" Tablets in Portrait
	private static final int SIDELIST_COLLAPSE_UNTIL_WIDTH_DP = 500;

	private static final int SIDELIST_COLLAPSE_UNTIL_WIDTH_PX = AndroidUtilsUI.dpToPx(
			SIDELIST_COLLAPSE_UNTIL_WIDTH_DP);

	// Sidelist always full-width, typically for 9"-11" Tablets, 7" Tablets in
	// Landscape, and TVs
	private static final int SIDELIST_KEEP_EXPANDED_AT_DP = 610;

	// Rare case when there's not enough height.  Show only active sidelist
	// header
	// This would be for Dell Streak (800x480dp) if it was API >= 13
	// Can't be >= 540, since TVs are that.
	// Each row is 42dp.  42x4=168, plus top actionbar (64dp?) and our header
	// (20dp?) ~ 252 dp.  Want to show at least 6 rows of the list.  6x42=252
	private static final int SIDELIST_HIDE_UNSELECTED_HEADERS_MAX_DP = 500;

	private static final String ID_SORT_FILTER = "";

	public interface OnTorrentSelectedListener
		extends ActionModeBeingReplacedListener
	{
		void onTorrentSelectedListener(TorrentListFragment torrentListFragment,
				long[] ids, boolean inMultiMode);
	}

	@Thunk
	public RecyclerView listview;

	@Thunk
	ActionMode mActionMode;

	@Thunk
	public TorrentListAdapter torrentListAdapter;

	private Callback mActionModeCallback;

	@Thunk
	TextView tvFilteringBy;

	@Thunk
	TextView tvTorrentCount;

	@Thunk
	boolean actionModeBeingReplaced;

	private boolean rebuildActionMode;

	@Thunk
	OnTorrentSelectedListener mCallback;

	// >> SideList

	private RecyclerView listSideActions;

	private RecyclerView listSideTags;

	@Thunk
	SideActionsAdapter sideActionsAdapter;

	@Thunk
	SideTagAdapter sideTagAdapter;

	@Thunk
	SideListHelper sideListHelper;

	// << SideList

	private Boolean isSmall;

	private SparseArray<SortDefinition> sortDefinitions;

	@Thunk
	TextView tvEmpty;

	private String remoteProfileID;

	private Long tagUID_Active;

	private Session lastSession;

	private int defaultSortID;

	@Override
	public void onAttach(Context activity) {
		super.onAttach(activity);
		remoteProfileID = SessionManager.findRemoteProfileID(getActivity(), TAG);

		buildSortDefinitions();

		if (activity instanceof OnTorrentSelectedListener) {
			mCallback = (OnTorrentSelectedListener) activity;
		}

		FlexibleRecyclerSelectionListener rs = new FlexibleRecyclerSelectionListener<TorrentListAdapter, TorrentListAdapterItem>() {
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
			public void onItemCheckedChanged(TorrentListAdapter adapter,
					TorrentListAdapterItem item, boolean isChecked) {
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

				AndroidUtilsUI.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}
		};

		isSmall = getSession().getRemoteProfile().useSmallLists();
		torrentListAdapter = new TorrentListAdapter(activity, rs, isSmall) {
			@Override
			public void lettersUpdated(HashMap<String, Integer> mapLetters) {
				sideListHelper.lettersUpdated(mapLetters);
			}
		};
		torrentListAdapter.registerAdapterDataObserver(
				new RecyclerView.AdapterDataObserver() {
					@Override
					public void onItemRangeInserted(int positionStart, int itemCount) {
						updateTorrentCount();
					}

					@Override
					public void onItemRangeRemoved(int positionStart, int itemCount) {
						updateTorrentCount();
					}

					@Override
					public void onChanged() {
						updateTorrentCount();
					}
				});
		torrentListAdapter.setMultiCheckModeAllowed(
				!AndroidUtils.usesNavigationControl());
	}

	@Nullable
	public View getItemView(long id) {
		if (torrentListAdapter == null || listview == null) {
			return null;
		}
		RecyclerView.ViewHolder viewHolder = listview.findViewHolderForItemId(id);

		if (viewHolder == null) {
			return null;
		}
		return viewHolder.itemView;
	}

	@Override
	public void uiReady(TransmissionRPC rpc) {
		if (getActivity() == null) {
			return;
		}

		Session session = getSession();
		RemoteProfile remoteProfile = session.getRemoteProfile();

		tagUID_Active = getSession().tag.getDownloadStateUID(7);

		long filterBy = remoteProfile.getFilterBy();
		// Convert All Filter to tag if we have tags
		if (filterBy == TorrentListAdapter.FILTERBY_ALL
				&& session.getSupports(RPCSupports.SUPPORTS_TAGS)) {
			Long tagAllUID = session.tag.getTagAllUID();
			if (tagAllUID != null) {
				filterBy = tagAllUID;
			}
		}
		if (filterBy > 10) {
			Map<?, ?> tag = session.tag.getTag(filterBy);

			filterBy(filterBy, MapUtils.getMapString(tag, "name", "fooo"), false);
		} else if (filterBy >= 0) {
			final AndroidUtils.ValueStringArray filterByList = AndroidUtils.getValueStringArray(
					getResources(), R.array.filterby_list);
			for (int i = 0; i < filterByList.values.length; i++) {
				long val = filterByList.values[i];
				if (val == filterBy) {
					filterBy(filterBy, filterByList.strings[i], false);
					break;
				}
			}
		}

		sideListHelper.sortByConfig(remoteProfile, ID_SORT_FILTER, defaultSortID,
				sortDefinitions);

		if (sideActionsAdapter != null) {
			sideActionsAdapter.updateMenuItems();
		}
		getActivity().supportInvalidateOptionsMenu();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View fragView = inflater.inflate(R.layout.frag_torrent_list, container,
				false);

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
							Session session = getSession();
							session.torrent.addListReceivedListener(
									new TorrentListReceivedListener() {

										@Override
										public void rpcTorrentListReceived(String callID,
												List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
											AndroidUtilsUI.runOnUIThread(TorrentListFragment.this,
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
											Session session = getSession();
											session.torrent.removeListReceivedListener(this);
										}
									}, false);
							session.triggerRefresh(true);
						}
					});
			swipeRefresh.setOnExtraViewVisibilityChange(
					new SwipeRefreshLayoutExtra.OnExtraViewVisibilityChangeListener() {
						@Thunk
						Handler pullRefreshHandler;

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

		torrentListAdapter.setEmptyView(fragView.findViewById(R.id.first_list),
				fragView.findViewById(R.id.empty_list));

		listview = (RecyclerView) fragView.findViewById(R.id.listTorrents);
		listview.setLayoutManager(new PreCachingLayoutManager(getContext()));
		listview.setAdapter(torrentListAdapter);

		if (AndroidUtils.isTV()) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				listview.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_LEFT);
			}
			((FastScrollRecyclerView) listview).setEnableFastScrolling(false);
			((FlexibleRecyclerView) listview).setFixedVerticalHeight(
					AndroidUtilsUI.dpToPx(48));
			listview.setVerticalFadingEdgeEnabled(true);
			listview.setFadingEdgeLength(AndroidUtilsUI.dpToPx((int) (48 * 1.5)));
		}

		/* Handy code to watch the states of row 2
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

		return fragView;
	}

	private void setupSideListArea() {
		FragmentActivity activity = getActivity();
		View view = AndroidUtilsUI.getContentView(activity);

		Toolbar abToolBar = (Toolbar) activity.findViewById(R.id.actionbar);

		boolean showActionsArea = abToolBar == null;
		if (!showActionsArea) {
			View viewToHide = activity.findViewById(R.id.sideactions_header);
			if (viewToHide != null) {
				viewToHide.setVisibility(View.GONE);
			}
			viewToHide = activity.findViewById(R.id.sideactions_list);
			if (viewToHide != null) {
				viewToHide.setVisibility(View.GONE);
			}
		}

		boolean setupForDrawer = abToolBar != null
				&& (activity instanceof DrawerActivity);

		if (sideListHelper == null || !sideListHelper.isValid()) {
			sideListHelper = new SideListHelper(getActivity(), view,
					R.id.sidelist_layout, setupForDrawer ? 0 : SIDELIST_MIN_WIDTH,
					setupForDrawer ? 0 : SIDELIST_MAX_WIDTH,
					setupForDrawer ? 0 : SIDELIST_COLLAPSE_UNTIL_WIDTH_PX,
					setupForDrawer ? 0 : SIDELIST_KEEP_EXPANDED_AT_DP,
					SIDELIST_HIDE_UNSELECTED_HEADERS_MAX_DP) {
				@Override
				public void expandedStateChanged(boolean expanded) {
					super.expandedStateChanged(expanded);
					if (sideActionsAdapter != null) {
						sideActionsAdapter.notifyDataSetChanged();
					}
					if (expanded) {
						SideSortAdapter sideSortAdapter = sideListHelper.getSideSortAdapter();
						if (sideSortAdapter != null) {
							sideSortAdapter.setViewType(0);
						}
						SideFilterAdapter sideFilterAdapter = sideListHelper.getSideTextFilterAdapter();
						if (sideFilterAdapter != null) {
							sideFilterAdapter.setViewType(0);
						}
						if (sideTagAdapter != null) {
							sideTagAdapter.notifyDataSetChanged();
						}
					}
				}

				@Override
				public void expandedStateChanging(boolean expanded) {
					super.expandedStateChanging(expanded);
					if (!expanded) {
						SideSortAdapter sideSortAdapter = sideListHelper.getSideSortAdapter();
						if (sideSortAdapter != null) {
							sideSortAdapter.setViewType(1);
						}
						SideFilterAdapter sideFilterAdapter = sideListHelper.getSideTextFilterAdapter();
						if (sideFilterAdapter != null) {
							sideFilterAdapter.setViewType(1);
						}
					}
				}

			};
			if (!sideListHelper.isValid()) {
				return;
			}

			// Could have used a ExpandableListView.. oh well
			if (showActionsArea) {
				sideListHelper.addEntry(view, R.id.sideactions_header,
						R.id.sideactions_list);
			}
			sideListHelper.addEntry(view, R.id.sidesort_header, R.id.sidesort_list);
			sideListHelper.addEntry(view, R.id.sidetag_header, R.id.sidetag_list);
			sideListHelper.addEntry(view, R.id.sidetextfilter_header,
					R.id.sidetextfilter_list);
		}

		View sideListArea = view.findViewById(R.id.sidelist_layout);

		if (sideListArea != null && sideListArea.getVisibility() == View.VISIBLE) {
			sideListHelper.setupSideTextFilter(view, R.id.sidetextfilter_list,
					R.id.sidefilter_text, listview, torrentListAdapter.getFilter());

			setupSideTags(view);

			sideListHelper.setupSideSort(view, R.id.sidesort_list,
					R.id.sidelist_sort_current, this);

			if (showActionsArea) {
				setupSideActions(view);
			}

			sideListHelper.expandedStateChanging(sideListHelper.isExpanded());
			sideListHelper.expandedStateChanged(sideListHelper.isExpanded());
		} else if (DEBUG) {
			Log.d(TAG,
					"setupSideListArea: sidelist not visible -- not setting up (until "
							+ "drawer is opened)");
		}

		if (sideListHelper.hasSideTextFilterArea()) {
			torrentListAdapter.getFilter().setBuildLetters(true);
		}
	}

	private void setupSideActions(View view) {
		RecyclerView oldRV = listSideActions;
		listSideActions = (RecyclerView) view.findViewById(R.id.sideactions_list);
		if (listSideActions == null) {
			return;
		}
		if (oldRV == listSideActions) {
			return;
		}

		listSideActions.setLayoutManager(new PreCachingLayoutManager(getContext()));

		sideActionsAdapter = new SideActionsAdapter(getContext(), remoteProfileID,
				R.menu.menu_torrent_list, new int[] {
					R.id.action_refresh,
					R.id.action_add_torrent,
					R.id.action_search,
					R.id.action_swarm_discoveries,
					R.id.action_subscriptions,
					R.id.action_start_all,
					R.id.action_stop_all,
					R.id.action_settings,
					R.id.action_social,
					R.id.action_logout,
					R.id.action_shutdown
				}, new SideActionsAdapter.SideActionSelectionListener() {
					@Override
					public boolean isRefreshing() {
						Session session = getSession();
						return session.torrent.isRefreshingList();
					}

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
				}) {
			@Override
			public void prepareActionMenus(Menu menu) {
				Session session = getSession();
				TorrentViewActivity.prepareGlobalMenu(menu, session);
			}
		};
		listSideActions.setAdapter(sideActionsAdapter);
	}

	@NonNull
	public Session getSession() {
		FragmentActivity activity = getActivity();
		if (activity instanceof TorrentViewActivity) {
			lastSession = ((TorrentViewActivity) activity).getSession();
		} else {
			lastSession = SessionManager.getSession(remoteProfileID, null, null);
		}
		return lastSession;
	}

	private void setupSideTags(View view) {
		RecyclerView newListSideTags = (RecyclerView) view.findViewById(
				R.id.sidetag_list);
		if (newListSideTags != listSideTags) {
			listSideTags = newListSideTags;
			if (listSideTags == null) {
				return;
			}

			listSideTags.setLayoutManager(new PreCachingLayoutManager(getContext()));

			sideTagAdapter = new SideTagAdapter(getContext(), remoteProfileID,
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
		} else {
			sideTagAdapter.removeAllItems();
		}

		Session session = getSession();
		if (DEBUG) {
			Log.d(TAG,
					"setupSideTags: supports? "
							+ session.getSupports(RPCSupports.SUPPORTS_TAGS) + "/"
							+ session.tag.getTags());
		}
		if (!session.getSupports(RPCSupports.SUPPORTS_TAGS)) {
			// TRANSMISSION
			AndroidUtils.ValueStringArray filterByList = AndroidUtils.getValueStringArray(
					getResources(), R.array.filterby_list);

			for (int i = 0; i < filterByList.strings.length; i++) {
				long id = filterByList.values[i];
				Map map = new HashMap(1);
				map.put("uid", id);
				SideTagAdapter.SideTagInfo sideTagInfo = new SideTagAdapter.SideTagInfo(
						map);
				sideTagAdapter.addItem(sideTagInfo);
			}
		} else {
			List<Map<?, ?>> tags = session.tag.getTags();
			if (tags != null && tags.size() > 0) {
				tagListReceived(tags);
			}
		}
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
				return showTorrentContextMenu();
			}

		}
		return false;
	}

	@Thunk
	boolean showTorrentContextMenu() {
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

		return AndroidUtilsUI.popupContextMenu(getContext(), mActionModeCallback,
				s);
	}

	@Override
	public void sessionSettingsChanged(SessionSettings newSessionSettings) {
		Session session = getSession();
		boolean isSmallNew = session.getRemoteProfile().useSmallLists();
		if (isSmall != null && isSmallNew != isSmall) {
			// getActivity().recreate() will recreate the closing session config window
			Intent intent = getActivity().getIntent();
			getActivity().finish();
			startActivity(intent);
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
	public void rpcTorrentListRefreshingChanged(final boolean refreshing) {
		AndroidUtilsUI.runOnUIThread(this, new AndroidUtils.RunnableWithActivity() {
			@Override
			public void run() {
				if (sideActionsAdapter != null) {
					sideActionsAdapter.updateRefreshButton();
				}
			}
		});
	}

	@Override
	public void onlineStateChanged(boolean isOnline, boolean isOnlineMobile) {
		if (sideActionsAdapter == null) {
			return;
		}
		AndroidUtilsUI.runOnUIThread(this, new AndroidUtils.RunnableWithActivity() {
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

	private static class LastUpdatedInfo
	{
		final long sinceMS;

		final String s;

		public LastUpdatedInfo(long sinceMS, String s) {
			this.sinceMS = sinceMS;
			this.s = s;
		}

	}

	@Nullable
	@Thunk
	LastUpdatedInfo getLastUpdatedString() {
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return null;
		}
		Session session = getSession();
		long lastUpdated = session.torrent.getLastListReceivedOn();
		if (lastUpdated == 0) {
			return new LastUpdatedInfo(0, "");
		}
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
		if (sideListHelper != null) {
			sideListHelper.onSaveInstanceState(outState);
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
		if (sideListHelper != null) {
			sideListHelper.onRestoreInstanceState(savedInstanceState);
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
		AnalyticsTracker.getInstance(this).fragmentResume(this, TAG);
	}

	@Override
	public void onResume() {
		if (DEBUG) {
			Log.d(TAG, "onResume");
		}
		super.onResume();

		BiglyBTApp.getNetworkState().addListener(this);

		Session session = getSession();
		setupSideListArea();

		torrentListAdapter.setSession(session);
		session.torrent.addListReceivedListener(TAG, this);
		session.tag.addTagListReceivedListener(this);
		session.addSessionListener(this);
		session.addSessionSettingsChangedListeners(this);
		session.torrent.addTorrentListRefreshingListener(this, false);

		if (sideListHelper != null) {
			sideListHelper.onResume();
		}
	}

	@Override
	public void onPause() {
		BiglyBTApp.getNetworkState().removeListener(this);

		if (SessionManager.hasSession(remoteProfileID)) {
			Session session = getSession();
			session.tag.removeTagListReceivedListener(this);
			session.torrent.removeListReceivedListener(this);
			session.torrent.removeListRefreshingListener(this);
			session.removeSessionSettingsChangedListeners(this);
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
		tvEmpty = (TextView) activity.findViewById(R.id.tv_empty);
		if (tvEmpty != null) {
			tvEmpty.setText(R.string.torrent_list_empty);
		}

		super.onActivityCreated(savedInstanceState);
	}

	@Thunk
	void finishActionMode() {
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

		List<Map> list = new ArrayList<>(checkedItems.length);

		for (int position : checkedItems) {
			Map<?, ?> torrent = adapter.getTorrentItem(position);
			if (torrent != null) {
				list.add(torrent);
			}
		}

		return list.toArray(new Map[list.size()]);
	}

	@Thunk
	static long[] getCheckedIDs(TorrentListAdapter adapter,
			boolean includeSelected) {

		List<Long> list = getCheckedIDsList(adapter, includeSelected);

		long[] longs = new long[list.size()];
		for (int i = 0; i < list.size(); i++) {
			longs[i] = list.get(i);
		}

		return longs;
	}

	private static List<Long> getCheckedIDsList(TorrentListAdapter adapter,
			boolean includeSelected) {
		List<Long> list = new ArrayList<>();
		if (adapter == null) {
			return list;
		}
		int[] checkedItems = adapter.getCheckedItemPositions();

		if (checkedItems.length == 0) {
			if (!includeSelected) {
				return list;
			}
			int selectedPosition = adapter.getSelectedPosition();
			if (selectedPosition < 0) {
				return list;
			}
			long torrentID = adapter.getTorrentID(selectedPosition);
			if (torrentID >= 0) {
				list.add(torrentID);
			}
			return list;
		} else {
			for (int position : checkedItems) {
				long torrentID = adapter.getTorrentID(position);
				if (torrentID >= 0) {
					list.add(torrentID);
				}
			}
		}

		return list;
	}

	@Override
	public void rpcTorrentListReceived(String callID, List<?> addedTorrentMaps,
			List<?> removedTorrentIDs) {
		if ((addedTorrentMaps == null || addedTorrentMaps.size() == 0)
				&& (removedTorrentIDs == null || removedTorrentIDs.size() == 0)) {
			if (torrentListAdapter.isNeverSetItems()) {
				torrentListAdapter.triggerEmptyList();
			}
			return;
		}
		AndroidUtilsUI.runOnUIThread(this, new AndroidUtils.RunnableWithActivity() {
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

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onOptionsItemSelected " + item.getTitle());
		}

		if (sideListHelper != null && sideListHelper.onOptionsItemSelected(item)) {
			return true;
		}

		return handleFragmentMenuItems(item.getItemId())
				|| super.onOptionsItemSelected(item);
	}

	@Thunk
	boolean handleFragmentMenuItems(int itemId) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "HANDLE MENU FRAG " + itemId);
		}
		return handleTorrentMenuActions(remoteProfileID,
				getCheckedIDs(torrentListAdapter, true), getFragmentManager(), itemId);
	}

	public static boolean handleTorrentMenuActions(String remoteProfileID,
			final long[] ids, FragmentManager fm, int itemId) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "HANDLE TORRENTMENU FRAG " + itemId);
		}
		if (ids == null || ids.length == 0) {
			return false;
		}
		if (itemId == R.id.action_sel_remove) {
			Session session = SessionManager.getSession(remoteProfileID, null, null);
			for (long torrentID : ids) {
				Map<?, ?> map = session.torrent.getCachedTorrent(torrentID);
				long id = MapUtils.getMapLong(map, "id", -1);
				boolean isMagnetTorrent = TorrentUtils.isMagnetTorrent(
						session.torrent.getCachedTorrent(id));
				if (!isMagnetTorrent) {
					String name = MapUtils.getMapString(map, "name", "");
					// TODO: One at a time!
					DialogFragmentDeleteTorrent.open(fm, session, name, id);
				}
			}
			return true;
		} else if (itemId == R.id.action_sel_start) {
			Session session = SessionManager.getSession(remoteProfileID, null, null);
			session.torrent.startTorrents(ids, false);
			return true;
		} else if (itemId == R.id.action_sel_forcestart) {
			Session session = SessionManager.getSession(remoteProfileID, null, null);
			session.torrent.startTorrents(ids, true);
			return true;
		} else if (itemId == R.id.action_sel_stop) {
			Session session = SessionManager.getSession(remoteProfileID, null, null);
			session.torrent.stopTorrents(ids);
			return true;
		} else if (itemId == R.id.action_sel_relocate) {
			Session session = SessionManager.getSession(remoteProfileID, null, null);
			Map<?, ?> mapFirst = session.torrent.getCachedTorrent(ids[0]);
			DialogFragmentMoveData.openMoveDataDialog(mapFirst, session, fm);
			return true;
		} else if (itemId == R.id.action_sel_move_top) {
			Session session = SessionManager.getSession(remoteProfileID, null, null);
			session.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall("queue-move-top", ids, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_move_up) {
			Session session = SessionManager.getSession(remoteProfileID, null, null);
			session.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall("queue-move-up", ids, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_move_down) {
			Session session = SessionManager.getSession(remoteProfileID, null, null);
			session.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall("queue-move-down", ids, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_move_bottom) {
			Session session = SessionManager.getSession(remoteProfileID, null, null);
			session.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall("queue-move-bottom", ids, null);
				}
			});
			return true;
		}
		return false;

	}

	@Thunk
	void updateActionModeText(ActionMode mode) {
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
		mActionModeCallback = new Callback() {

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

				if (mode != null) {
					mode.setTitle(R.string.context_torrent_title);
				}
				getActivity().getMenuInflater().inflate(
						R.menu.menu_context_torrent_details, menu);

				TorrentDetailsFragment frag = (TorrentDetailsFragment) getActivity().getSupportFragmentManager().findFragmentById(
						R.id.frag_torrent_details);
				if (frag != null) {
					frag.onCreateActionMode(mode, menu);
				}

				if (sideListHelper == null || !sideListHelper.isValid()) {
					SubMenu subMenu = menu.addSubMenu(R.string.menu_global_actions);
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
					MenuItem add = menu.add(R.string.select_multiple_items);
					add.setCheckable(true);
					add.setChecked(torrentListAdapter.isMultiCheckMode());
					add.setOnMenuItemClickListener(
							new MenuItem.OnMenuItemClickListener() {
								@Override
								public boolean onMenuItemClick(MenuItem item) {
									boolean turnOn = !torrentListAdapter.isMultiCheckModeAllowed();

									torrentListAdapter.setMultiCheckModeAllowed(turnOn);
									if (turnOn) {
										torrentListAdapter.setMultiCheckMode(true);
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

				// Must be called first, because our drawer sets all menu items
				// visible.. :(
				getActivity().onPrepareOptionsMenu(menu);

				prepareContextMenu(menu);

				TorrentDetailsFragment frag = (TorrentDetailsFragment) getActivity().getSupportFragmentManager().findFragmentById(
						R.id.frag_torrent_details);
				if (frag != null) {
					frag.onPrepareActionMode(menu);
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
					if (frag.onActionItemClicked(item)) {
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
					AndroidUtilsUI.invalidateOptionsMenuHC(getActivity(), mActionMode);
				}
			}
		};
	}

	@Thunk
	void prepareContextMenu(Menu menu) {
		Session session = getSession();
		boolean isLocalHost = session.getRemoteProfile().isLocalHost();
		boolean isOnlineOrLocal = BiglyBTApp.getNetworkState().isOnline()
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
			boolean allMagnets = checkedTorrentMaps.length > 0;
			for (Map<?, ?> mapTorrent : checkedTorrentMaps) {
				int status = MapUtils.getMapInt(mapTorrent,
						TransmissionVars.FIELD_TORRENT_STATUS,
						TransmissionVars.TR_STATUS_STOPPED);
				boolean isMagnet = TorrentUtils.isMagnetTorrent(mapTorrent);
				if (!isMagnet) {
					allMagnets = false;
					canStart |= status == TransmissionVars.TR_STATUS_STOPPED;
					canStop |= status != TransmissionVars.TR_STATUS_STOPPED;
				}
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG,
							"prepareContextMenu: " + canStart + "/" + canStop + "/" + status);
				}
			}

			if (allMagnets) {
				AndroidUtilsUI.setManyMenuItemsVisible(false, menu, new int[] {
					R.id.action_sel_forcestart,
					R.id.action_sel_move,
					R.id.action_sel_relocate
				});
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

	@Thunk
	boolean showContextualActions(boolean forceRebuild) {
		if (AndroidUtils.isTV()) {
			// TV doesn't get action bar changes, because it's impossible to get to
			// with client control when you are on row 4000
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
			ActionBar ab = abActivity.getSupportActionBar();

			if (AndroidUtils.DEBUG_MENU) {
				Log.d(TAG,
						"showContextualActions: startAB. mActionMode = " + mActionMode
								+ "; isShowing=" + (ab == null ? null : ab.isShowing()));
			}

			actionModeBeingReplaced = true;

			mActionMode = abActivity.startSupportActionMode(mActionModeCallback);
			actionModeBeingReplaced = false;
			if (mActionMode != null) {
				mActionMode.setSubtitle(R.string.multi_select_tip);
				mActionMode.setTitle(R.string.context_torrent_title);
			}
		}
		if (mCallback != null) {
			mCallback.setActionModeBeingReplaced(mActionMode, false);
		}

		return true;
	}

	@Thunk
	void filterBy(final long filterMode, final String name, boolean save) {
		if (DEBUG) {
			Log.d(TAG, "FILTER BY " + name);
		}

		AndroidUtilsUI.runOnUIThread(this, new Runnable() {
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
					Session session = getSession();
					Map<?, ?> tag = session.tag.getTag(filterMode);
					SpanTags spanTags = new SpanTags();
					spanTags.init(getContext(), session, tvFilteringBy, null);
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
			Session session = getSession();
			session.getRemoteProfile().setFilterBy(filterMode);
			session.saveProfile();
		}
	}

	@Override
	public SortableAdapter getSortableAdapter() {
		return torrentListAdapter;
	}

	@Override
	public SparseArray<SortDefinition> getSortDefinitions() {
		return sortDefinitions;
	}

	@Override
	public SortDefinition getSortDefinition(int id) {
		return sortDefinitions.get(id);
	}

	@Override
	public String getSortFilterID() {
		return "";
	}

	private void buildSortDefinitions() {
		if (sortDefinitions != null) {
			return;
		}
		String[] sortNames = getContext().getResources().getStringArray(
				R.array.sortby_list);

		sortDefinitions = new SparseArray<>(sortNames.length);
		int i = 0;

		// <item>Queue Order</item>
		GroupedSortDefinition<TorrentListAdapterItem, Integer> sdQueue = new GroupedSortDefinition<TorrentListAdapterItem, Integer>(
				i, sortNames[i], new String[] {
					TransmissionVars.FIELD_TORRENT_IS_COMPLETE,
					TransmissionVars.FIELD_TORRENT_POSITION
				}, new Boolean[] {
					SortDefinition.SORT_NATURAL,
					SortDefinition.SORT_NATURAL
				}, SortDefinition.SORT_ASC) {
			@Override
			public Integer getSectionID(TorrentListAdapterItem o, boolean isAsc) {
				if (!(o instanceof TorrentListAdapterTorrentItem)) {
					return 0;
				}
				Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
						getSession());
				boolean complete = MapUtils.getMapBoolean(map,
						TransmissionVars.FIELD_TORRENT_IS_COMPLETE, false);
				long position = MapUtils.getMapLong(map,
						TransmissionVars.FIELD_TORRENT_POSITION, 1) - 1;
				return (int) ((position / 10) << 1) + (complete ? 1 : 0);
			}

			@Override
			public String getSectionName(Integer sectionID, boolean isAsc) {
				boolean complete = (sectionID & 0x1) == 1;
				int start = (sectionID >> 1) * 10 + 1;

				return getResources().getString(
						complete ? R.string.TorrentListSectionName_Queue_complete
								: R.string.TorrentListSectionName_Queue_incomplete,
						DisplayFormatters.formatNumber(isAsc ? start : start + 9),
						DisplayFormatters.formatNumber(!isAsc ? start : start + 9));
			}
		};
		sdQueue.setMinCountBeforeGrouping(1);
		sdQueue.setShowGroupCount(false);
		sortDefinitions.put(i, sdQueue);
		defaultSortID = i;

		i++; // <item>Activity</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							"ActiveSort",
							TransmissionVars.FIELD_TORRENT_DATE_ACTIVITY,
						}, new Boolean[] {
							SortDefinition.SORT_REVERSED,
							SortDefinition.SORT_REVERSED,
						}, SortDefinition.SORT_ASC) {
					@Override
					public Integer getSectionID(TorrentListAdapterItem o, boolean isAsc) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return 0;
						}
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								getSession());
						boolean active;
						List<?> listTagUIDs = MapUtils.getMapList(map,
								TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
						if (listTagUIDs != null && tagUID_Active != null) {
							active = listTagUIDs.contains(tagUID_Active);
						} else {
							long rateDL = MapUtils.getMapLong(map,
									TransmissionVars.FIELD_TORRENT_RATE_DOWNLOAD, 0);
							long rateUL = MapUtils.getMapLong(map,
									TransmissionVars.FIELD_TORRENT_RATE_UPLOAD, 0);
							active = rateDL > 0 && rateUL > 0;
						}
						if (!active) {
							long lastActiveOn = MapUtils.getMapLong(map,
									TransmissionVars.FIELD_TORRENT_DATE_ACTIVITY, 0);
							if (lastActiveOn > 0) {
								GregorianCalendar today = new GregorianCalendar();
								GregorianCalendar calendar = new GregorianCalendar();
								calendar.setTimeInMillis(lastActiveOn * 1000);
								if (today.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)) {
									int todayDOY = today.get(Calendar.DAY_OF_YEAR);
									int thisDOY = calendar.get(Calendar.DAY_OF_YEAR);
									if (todayDOY == thisDOY) {
										return -3;
									}
									if (todayDOY - 1 == thisDOY) {
										// Note: this will miss Dec 31st
										return -4;
									}
								}
								return (calendar.get(Calendar.YEAR) << 4)
										| calendar.get(Calendar.MONTH);
							}
						}
						return active ? -1 : -2;
					}

					@Override
					public String getSectionName(Integer sectionID, boolean isAsc) {
						switch (sectionID) {
							case -1:
								return getString(
										R.string.TorrentListSectionName_Activity_active);
							case -2:
								return getString(
										R.string.TorrentListSectionName_Activity_inactive);
							case -3:
								return getString(
										R.string.TorrentListSectionName_Activity_today);
							case -4:
								return getString(
										R.string.TorrentListSectionName_Activity_yesterday);
							default:
								GregorianCalendar calendar = new GregorianCalendar();
								calendar.set(Calendar.YEAR, (int) (sectionID >> 4));
								calendar.set(Calendar.MONTH, (int) (sectionID & 0xF));
								return getString(
										R.string.TorrentListSectionName_Activity_lastactive,
										DateFormat.format("MMMM, yyyy", calendar).toString());
						}
					}
				});

		i++; // <item>Age</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_DATE_ADDED
						}, SortDefinition.SORT_DESC) {
					@Override
					public Integer getSectionID(TorrentListAdapterItem o, boolean isAsc) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return 0;
						}
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								lastSession);
						long addedOn = MapUtils.getMapLong(map,
								TransmissionVars.FIELD_TORRENT_DATE_ADDED, 0);
						GregorianCalendar calendar = new GregorianCalendar();
						calendar.setTimeInMillis(addedOn * 1000);
						return (calendar.get(Calendar.YEAR) << 4)
								+ calendar.get(Calendar.MONTH);
					}

					@Override
					public String getSectionName(Integer sectionID, boolean isAsc) {
						GregorianCalendar calendar = new GregorianCalendar();
						calendar.set(Calendar.YEAR, (int) (sectionID >> 4));
						calendar.set(Calendar.MONTH, (int) (sectionID & 0xF));
						return DateFormat.format("MMMM, yyyy", calendar).toString();
					}
				});

		i++; // <item>Progress</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_PERCENT_DONE
						}, SortDefinition.SORT_ASC) {
					@Override
					public Integer getSectionID(TorrentListAdapterItem o, boolean isAsc) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return 0;
						}
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								getSession());
						float pctDone = MapUtils.getMapFloat(map,
								TransmissionVars.FIELD_TORRENT_PERCENT_DONE, 0);
						int startPct = ((int) (pctDone * 10)) * 10;

						return startPct;
					}

					@Override
					public String getSectionName(Integer sectionID, boolean isAsc) {
						if (sectionID < 10) {
							return getString(
									R.string.TorrentListSectionName_Progress_under10);
						}
						if (sectionID >= 100) {
							return getString(R.string.TorrentListSectionName_Progress_100);
						}
						long endPct = sectionID + 10;

						return getString(R.string.TorrentListSectionName_Progress,
								(isAsc ? sectionID : endPct), (!isAsc ? sectionID : endPct));
					}
				});

		i++; // <item>Ratio</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO
						}, SortDefinition.SORT_ASC) {
					@Override
					public Integer getSectionID(TorrentListAdapterItem o, boolean isAsc) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return 0;
						}
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								getSession());
						float ratio = MapUtils.getMapFloat(map,
								TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO, 0);
						return (int) ratio;
					}

					@Override
					public String getSectionName(Integer sectionID, boolean isAsc) {
						if (isAsc) {
							return "< " + (sectionID + 1) + ":1";
						}
						return "> " + sectionID + ":1";
					}
				});

		i++; // <item>Size</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_SIZE_WHEN_DONE
						}, SortDefinition.SORT_DESC) {
					@Override
					public Integer getSectionID(TorrentListAdapterItem o, boolean isAsc) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return 0;
						}
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								getSession());
						long bytes = MapUtils.getMapLong(map,
								TransmissionVars.FIELD_TORRENT_SIZE_WHEN_DONE, 0);

						if (bytes < 1024L * 1024L * 500) {
							return 0;
						} else if (bytes < 1024L * 1024L * 1) {
							return -1;
						} else {
							int gigsLower = (int) (bytes / 1024 / 1024 / 1024);
							return gigsLower;
						}
					}

					@Override
					public String getSectionName(Integer sectionID, boolean isAsc) {

						String start;
						String end;
						if (sectionID == 0) {
							start = "0";
							end = "500MB";
						} else if (sectionID == -1) {
							start = "500MB";
							end = "1GB";
						} else {
							long gigsLower = sectionID;
							start = gigsLower + "GB";
							end = (gigsLower + 1) + "GB";
						}
						if (isAsc) {
							return start + " to " + end;
						} else {
							return end + " to " + start;
						}
					}
				});

		i++; // <item>State</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_STATUS
						}, SortDefinition.SORT_ASC) {
					@Override
					public Integer getSectionID(TorrentListAdapterItem o, boolean isAsc) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return -1;
						}
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								getSession());
						int status = MapUtils.getMapInt(map,
								TransmissionVars.FIELD_TORRENT_STATUS, 0);
						return status;
					}

					@Override
					public String getSectionName(Integer sectionID, boolean isAsc) {
						int id;
						switch (sectionID) {
							case TransmissionVars.TR_STATUS_CHECK_WAIT:
							case TransmissionVars.TR_STATUS_CHECK:
								id = R.string.torrent_status_checking;
								break;

							case TransmissionVars.TR_STATUS_DOWNLOAD:
								id = R.string.torrent_status_download;
								break;

							case TransmissionVars.TR_STATUS_DOWNLOAD_WAIT:
								id = R.string.torrent_status_queued_dl;
								break;

							case TransmissionVars.TR_STATUS_SEED:
								id = R.string.torrent_status_seed;
								break;

							case TransmissionVars.TR_STATUS_SEED_WAIT:
								id = R.string.torrent_status_queued_ul;
								break;

							case TransmissionVars.TR_STATUS_STOPPED:
								id = R.string.torrent_status_stopped;
								break;

							default:
								id = -1;
								break;
						}
						if (id >= 0) {
							return getContext().getString(id);
						}
						return "" + id;
					}
				});

		i++; // <item>ETA</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_ETA,
							TransmissionVars.FIELD_TORRENT_PERCENT_DONE
						}, new Boolean[] {
							SortDefinition.SORT_NATURAL,
							SortDefinition.SORT_REVERSED
						}, SortDefinition.SORT_ASC) {
					@Override
					public Integer getSectionID(TorrentListAdapterItem o, boolean isAsc) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return -1;
						}
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								getSession());
						long etaSecs = MapUtils.getMapLong(map,
								TransmissionVars.FIELD_TORRENT_ETA, -1);
						if (etaSecs < 0) {
							float pctDone = MapUtils.getMapFloat(map,
									TransmissionVars.FIELD_TORRENT_PERCENT_DONE, 0);
							if (pctDone >= 1) {
								return 0;
							}
							return 1;
						}
						return 2;
					}

					@Override
					public String getSectionName(Integer sectionID, boolean isAsc) {
						switch (sectionID) {
							case 0:
								return getString(R.string.TorrentListSectionName_ETA_complete);
							case 1:
								return getString(R.string.TorrentListSectionName_ETA_none);
							case 2:
								return getString(R.string.TorrentListSectionName_ETA_available);
						}
						return "";
					}
				});

		i++; // <item>Count</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_FILE_COUNT,
							TransmissionVars.FIELD_TORRENT_SIZE_WHEN_DONE
						}, new Boolean[] {
							SortDefinition.SORT_NATURAL,
							SortDefinition.SORT_REVERSED
						}, SortDefinition.SORT_ASC) {
					@Override
					public Integer getSectionID(TorrentListAdapterItem o, boolean isAsc) {
						{
							if (!(o instanceof TorrentListAdapterTorrentItem)) {
								return -1;
							}
							// TODO: calling getSession all the time sucks
							Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
									getSession());
							int numFiles = MapUtils.getMapInt(map,
									TransmissionVars.FIELD_TORRENT_FILE_COUNT, 0);
							if (numFiles == 1) {
								return 1;
							} else if (numFiles < 4) {
								return 2;
							} else if (numFiles < 100) {
								return 3;
							} else if (numFiles <= 2000) {
								return 4;
							} else if (numFiles >= 0) {
								return 5;
							}
							return -1;
						}
					}

					@Override
					public String getSectionName(Integer sectionID, boolean isAsc) {
						if (sectionID == 1) {
							return getString(R.string.TorrentListSectionName_File_1);
						} else if (sectionID == 2) {
							return getString(R.string.TorrentListSectionName_File_few);
						} else if (sectionID == 3) {
							return getString(R.string.TorrentListSectionName_File_many);
						} else if (sectionID == 4) {
							return getString(R.string.TorrentListSectionName_File_hundreds);
						} else if (sectionID == 5) {
							return getString(R.string.TorrentListSectionName_File_thousands);
						}
						return "";
					}

				});
	}

	@Thunk
	void updateTorrentCount() {
		if (tvTorrentCount == null) {
			return;
		}
		AndroidUtilsUI.runOnUIThread(this, new Runnable() {
			public void run() {
				if (getActivity() == null) {
					return;
				}
				String s = "";
				int total = torrentListAdapter.getItemCount(
						TorrentListAdapter.VIEWTYPE_TORRENT);
				if (total != 0) {
					String constraint = torrentListAdapter.getFilter().getConstraint();
					if (constraint != null && constraint.length() > 0) {
						s = getResources().getQuantityString(R.plurals.torrent_count,
								(int) total, total);
					}
				} else {

					if (tvEmpty != null) {
						Session session = getSession();
						LongSparseArray<Map<?, ?>> torrentList = session.torrent.getListAsSparseArray();
						int size = torrentList.size();
						tvEmpty.setText(size > 0 ? R.string.list_filtered_empty
								: R.string.torrent_list_empty);
					}

				}
				tvTorrentCount.setText(s);
			}
		});
	}

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

	@Override
	public ActionMode getActionMode() {
		return mActionMode;
	}

	public void clearSelection() {
		finishActionMode();
	}

	@Thunk
	void updateCheckedIDs() {
		List<Long> checkedTorrentIDs = getCheckedIDsList(torrentListAdapter, false);
		if (mCallback != null) {

			long[] longs = new long[checkedTorrentIDs.size()];
			for (int i = 0; i < checkedTorrentIDs.size(); i++) {
				longs[i] = checkedTorrentIDs.get(i);
			}

			mCallback.onTorrentSelectedListener(TorrentListFragment.this, longs,
					torrentListAdapter.isMultiCheckMode());
		}
		if (checkedTorrentIDs.size() == 0 && mActionMode != null) {
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

		Session session = getSession();
		if (!canStop) {
			long[] ids = getCheckedIDs(torrentListAdapter, true);
			session.torrent.stopTorrents(ids);
		} else {
			long[] ids = getCheckedIDs(torrentListAdapter, true);
			session.torrent.startTorrents(ids, false);
		}
	}

	@Override
	public Callback getActionModeCallback() {
		return mActionModeCallback;
	}

	@Override
	public void tagListReceived(List<Map<?, ?>> tags) {
		if (sideTagAdapter == null || tags == null) {
			return;
		}
		List<SideTagAdapter.SideTagInfo> list = new ArrayList<>(tags.size());
		for (Map tag : tags) {
			if (MapUtils.getMapLong(tag, TransmissionVars.FIELD_TAG_COUNT, 0) > 0) {
				list.add(new SideTagAdapter.SideTagInfo(tag));
			}
		}
		sideTagAdapter.setItems(list,
				new FlexibleRecyclerAdapter.SetItemsCallBack<SideTagAdapter.SideTagInfo>() {
					@Override
					public boolean areContentsTheSame(SideTagAdapter.SideTagInfo oldItem,
							SideTagAdapter.SideTagInfo newItem) {

						if (oldItem.tag.size() != newItem.tag.size()) {
							return false;
						}
						for (Object key : oldItem.tag.keySet()) {
							Object oldVal = oldItem.tag.get(key);
							Object newVal = newItem.tag.get(key);
							if (oldVal == newVal) {
								continue;
							}
							if (oldVal == null || newVal == null) {
								return false;
							}
							if (!oldVal.equals(newVal)) {
								return false;
							}
						}
						return true;
					}

				});

		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (tvFilteringBy == null || getActivity() == null) {
					return;
				}
				tvFilteringBy.invalidate();
			}
		});
	}

	public void onDrawerOpened(View view) {
		setupSideListArea();
	}
}
