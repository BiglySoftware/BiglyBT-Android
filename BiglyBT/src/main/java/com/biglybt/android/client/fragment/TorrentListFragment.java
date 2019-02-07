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
import java.util.concurrent.ConcurrentHashMap;

import com.biglybt.android.adapter.FlexibleRecyclerSelectionListener;
import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.DrawerActivity;
import com.biglybt.android.client.activity.TorrentViewActivity;
import com.biglybt.android.client.adapter.*;
import com.biglybt.android.client.dialog.DialogFragmentDeleteTorrent;
import com.biglybt.android.client.dialog.DialogFragmentMoveData;
import com.biglybt.android.client.rpc.*;
import com.biglybt.android.client.session.*;
import com.biglybt.android.client.sidelist.*;
import com.biglybt.android.client.spanbubbles.SpanTags;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.NetworkState;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.android.widget.SwipeRefreshLayoutExtra;
import com.biglybt.util.Thunk;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.view.ActionMode.Callback;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.*;
import android.widget.TextView;

/**
 * Handles a ListView that shows Torrents
 */
public class TorrentListFragment
	extends SideListFragment
	implements TorrentListReceivedListener, SessionListener,
	ActionModeBeingReplacedListener, TagListReceivedListener, View.OnKeyListener,
	SessionSettingsChangedListener, TorrentListRefreshingListener,
	NetworkState.NetworkStateListener
{
	@Thunk
	static final boolean DEBUG = AndroidUtils.DEBUG;

	private static final String TAG = "TorrentList";

	// Shrink sidelist, typically for 7" Tablets in Portrait
	private static final int SIDELIST_COLLAPSE_UNTIL_WIDTH_DP = 500;

	private static final int SIDELIST_COLLAPSE_UNTIL_WIDTH_PX = AndroidUtilsUI.dpToPx(
			SIDELIST_COLLAPSE_UNTIL_WIDTH_DP);

	// Sidelist always full-width, typically for 9"-11" Tablets, 7" Tablets in
	// Landscape, and TVs
	private static final int SIDELIST_KEEP_EXPANDED_AT_DP = 610;

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

	private RecyclerView listSideTags;

	@Thunk
	SideTagAdapter sideTagAdapter;

	private SideActionSelectionListener sideActionSelectionListener;

	// << SideList

	private Boolean isSmall;

	@Thunk
	TextView tvEmpty;

	@Override
	public void onAttachWithSession(Context context) {
		super.onAttachWithSession(context);

		if (context instanceof OnTorrentSelectedListener) {
			mCallback = (OnTorrentSelectedListener) context;
		}

		FlexibleRecyclerSelectionListener<TorrentListAdapter, TorrentListHolder, TorrentListAdapterItem> rs = new FlexibleRecyclerSelectionListener<TorrentListAdapter, TorrentListHolder, TorrentListAdapterItem>() {
			@Override
			public void onItemSelected(TorrentListAdapter adapter, final int position,
					boolean isChecked) {
			}

			@Override
			public void onItemClick(TorrentListAdapter adapter, int position) {
			}

			@Override
			public boolean onItemLongClick(TorrentListAdapter adapter, int position) {
				return AndroidUtils.usesNavigationControl()
						&& adapter.getItemViewType(
								position) == TorrentListAdapter.VIEWTYPE_TORRENT
						&& showTorrentContextMenu();
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

		isSmall = session.getRemoteProfile().useSmallLists();
		torrentListAdapter = new TorrentListAdapter(context, getLifecycle(), this,
				rs, isSmall);
		torrentListAdapter.addOnSetItemsCompleteListener(
				adapter -> updateTorrentCount());
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
	public void sessionReadyForUI(TransmissionRPC rpc) {
		AndroidUtilsUI.runOnUIThread(this, false,
				activity -> uiSessionReadyForUI(rpc));
	}

	@UiThread
	private void uiSessionReadyForUI(TransmissionRPC rpc) {
		RemoteProfile remoteProfile = session.getRemoteProfile();

		long filterBy = remoteProfile.getFilterBy();
		// Convert All Filter to tag if we have tags
		if (filterBy == TorrentListFilter.FILTERBY_ALL
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

		SideListActivity sideListActivity = getSideListActivity();
		if (sideListActivity != null) {
			sideListActivity.updateSideActionMenuItems();
		}

		requireActivity().invalidateOptionsMenu();
	}

	@Override
	public View onCreateViewWithSession(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View fragView = inflater.inflate(R.layout.frag_torrent_list, container,
				false);

		setupActionModeCallback();

		final SwipeRefreshLayoutExtra swipeRefresh = fragView.findViewById(
				R.id.swipe_container);
		if (swipeRefresh != null) {
			swipeRefresh.setExtraLayout(R.layout.swipe_layout_extra);

			/* getLastUpdateString uses DateUtils.getRelativeDateTimeString, which
			 * for some reason sometimes takes 4s to initialize.  That's bad when
			 * initializing a view.			
			LastUpdatedInfo lui = getLastUpdatedString();
			if (lui != null) {
				View extraView = swipeRefresh.getExtraView();
				if (extraView != null) {
					TextView tvSwipeText = extraView.findViewById(R.id.swipe_text);
					tvSwipeText.setText(lui.s);
				}
			}
			 */
			swipeRefresh.setOnRefreshListener(
					new SwipeRefreshLayout.OnRefreshListener() {
						@Override
						public void onRefresh() {
							session.torrent.addListReceivedListener(
									new TorrentListReceivedListener() {

										@Override
										public void rpcTorrentListReceived(String callID,
												List<?> addedTorrentMaps, List<String> fields,
												final int[] fileIndexes, List<?> removedTorrentIDs) {
											AndroidUtilsUI.runOnUIThread(TorrentListFragment.this,
													false, activity -> {
														swipeRefresh.setRefreshing(false);
														LastUpdatedInfo lui = getLastUpdatedString();
														View extraView = swipeRefresh.getExtraView();
														if (extraView != null) {
															TextView tvSwipeText = extraView.findViewById(
																	R.id.swipe_text);
															tvSwipeText.setText(lui == null ? "" : lui.s);
														}
													});
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
									TextView tvSwipeText = view.findViewById(R.id.swipe_text);
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

		listview = fragView.findViewById(R.id.listTorrents);
		PreCachingLayoutManager layoutManager = new PreCachingLayoutManager(
				getContext());
		listview.setLayoutManager(layoutManager);
		listview.setAdapter(torrentListAdapter);

		if (AndroidUtils.isTV(getContext())) {
			listview.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_LEFT);
			if (listview instanceof FastScrollRecyclerView) {
				((FastScrollRecyclerView) listview).setEnableFastScrolling(false);
			}
			layoutManager.setFixedVerticalHeight(AndroidUtilsUI.dpToPx(48));
			listview.setVerticalFadingEdgeEnabled(true);
			listview.setFadingEdgeLength(AndroidUtilsUI.dpToPx((int) (48 * 1.5)));
		}

		setHasOptionsMenu(true);

		return fragView;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		if (DEBUG) {
			log(TAG, "onActivityCreated");
		}
		FragmentActivity activity = requireActivity();
		tvFilteringBy = activity.findViewById(R.id.wvFilteringBy);
		tvTorrentCount = activity.findViewById(R.id.wvTorrentCount);
		tvEmpty = activity.findViewById(R.id.tv_empty);
		if (tvEmpty != null) {
			tvEmpty.setText(R.string.torrent_list_empty);
		}

		Toolbar abToolBar = requireActivity().findViewById(R.id.actionbar);
		boolean canShowSideActionsArea = abToolBar == null
				|| abToolBar.getVisibility() == View.GONE;

		sideActionSelectionListener = canShowSideActionsArea ?

				new SideActionSelectionListener() {
					@Override
					public Session getSession() {
						return session;
					}

					@Override
					public void prepareActionMenus(Menu menu) {
						TorrentViewActivity.prepareGlobalMenu(menu, session);

						int totalUnfiltered = session.torrent.getCount();

						MenuItem[] itemsToShow_min1unfiltered = {
							menu.findItem(R.id.action_start_all),
							menu.findItem(R.id.action_stop_all)
						};

						for (MenuItem menuItem : itemsToShow_min1unfiltered) {
							if (menuItem == null) {
								continue;
							}
							menuItem.setVisible(totalUnfiltered > 1);
						}
					}

					@Override
					public MenuBuilder getMenuBuilder() {
						Context context = requireContext();
						@SuppressLint("RestrictedApi")
						MenuBuilder menuBuilder = new MenuBuilder(context);
						new MenuInflater(context).inflate(R.menu.menu_torrent_list,
								menuBuilder);
						return menuBuilder;
					}

					@Override
					public int[] getRestrictToMenuIDs() {
						return new int[] {
							R.id.action_refresh,
							R.id.action_add_torrent,
							R.id.action_search,
							R.id.action_swarm_discoveries,
							R.id.action_subscriptions,
							R.id.action_start_all,
							R.id.action_stop_all,
							R.id.action_settings,
							R.id.action_giveback,
							R.id.action_logout,
							R.id.action_shutdown
						};
					}

					@Override
					public boolean isRefreshing() {
						return session.torrent.isRefreshingList();
					}

					@Override
					public void onItemClick(SideActionsAdapter adapter, int position) {
						SideActionsAdapter.SideActionsInfo item = adapter.getItem(position);
						if (item == null) {
							return;
						}
						requireActivity().onOptionsItemSelected(item.menuItem);
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
				} : null;

		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public SortableRecyclerAdapter getMainAdapter() {
		return torrentListAdapter;
	}

	@Override
	public SideActionSelectionListener getSideActionSelectionListener() {
		return sideActionSelectionListener;
	}

	@Override
	public void sideListExpandListChanging(boolean expanded) {
		super.sideListExpandListChanging(expanded);
		if (expanded) {
			if (sideTagAdapter != null) {
				sideTagAdapter.notifyDataSetChanged();
			}
		}
	}

	@Override
	public void onSideListHelperVisibleSetup(View view) {
		super.onSideListHelperVisibleSetup(view);

		setupSideTags(view);
	}

	@Override
	public void onSideListHelperCreated(SideListHelper sideListHelper) {
		super.onSideListHelperCreated(sideListHelper);
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		View view = AndroidUtilsUI.getContentView(activity);

		Toolbar abToolBar = activity.findViewById(R.id.actionbar);

		boolean setupForDrawer = abToolBar != null
				&& (activity instanceof DrawerActivity)
				&& ((DrawerActivity) activity).getDrawerLayout() != null;
		sideListHelper.setDimensionLimits(
				setupForDrawer ? 0 : SIDELIST_COLLAPSE_UNTIL_WIDTH_PX,
				setupForDrawer ? 0 : SIDELIST_KEEP_EXPANDED_AT_DP);

		sideListHelper.addEntry("tag", view, R.id.sidetag_header,
				R.id.sidetag_list);
	}

	@Override
	public boolean showFilterEntry() {
		return true;
	}

	private void setupSideTags(View view) {
		RecyclerView newListSideTags = view.findViewById(R.id.sidetag_list);
		if (newListSideTags != listSideTags) {
			listSideTags = newListSideTags;
			if (listSideTags == null) {
				return;
			}

			listSideTags.setLayoutManager(new PreCachingLayoutManager(getContext()));

			sideTagAdapter = new SideTagAdapter(getLifecycle(), remoteProfileID,
					new FlexibleRecyclerSelectionListener<SideTagAdapter, SideTagAdapter.SideTagHolder, SideTagAdapter.SideTagInfo>() {
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

							filterBy(item.id, MapUtils.getMapString(
									session.tag.getTag(item.id), "name", ""), true);
						}
					});

			listSideTags.setAdapter(sideTagAdapter);
		} else {
			sideTagAdapter.removeAllItems();
		}

		if (DEBUG) {
			List<Map<?, ?>> tags = session.tag.getTags();
			log(TAG, "setupSideTags: supportsTag? "
					+ session.getSupports(RPCSupports.SUPPORTS_TAGS) + "/" + (tags == null
							? "null" : tags.size() > 20 ? tags.size() : tags.toString()));
		}
		if (!session.getSupports(RPCSupports.SUPPORTS_TAGS)) {
			// TRANSMISSION
			AndroidUtils.ValueStringArray filterByList = AndroidUtils.getValueStringArray(
					getResources(), R.array.filterby_list);

			int num = filterByList.strings.length;
			if (num > 0) {
				SideTagAdapter.SideTagInfo[] tagsToAdd = new SideTagAdapter.SideTagInfo[num];
				for (int i = 0; i < num; i++) {
					long id = filterByList.values[i];
					Map<String, Object> map = new ConcurrentHashMap<>(1);
					map.put("uid", id);
					tagsToAdd[i] = new SideTagAdapter.SideTagInfo(map);
				}
				sideTagAdapter.addItem(tagsToAdd);
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

			case KeyEvent.KEYCODE_PROG_YELLOW:
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: {
				SideListActivity sideListActivity = getSideListActivity();
				if (sideListActivity != null) {
					sideListActivity.flipSideListExpandState();
				}
				return true;
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
		boolean isSmallNew = session.getRemoteProfile().useSmallLists();
		if (isSmall != null && isSmallNew != isSmall) {
			// getActivity().recreate() will recreate the closing session config window
			FragmentActivity activity = requireActivity();
			Intent intent = activity.getIntent();
			activity.finish();
			startActivity(intent);
		}
		isSmall = isSmallNew;

		SideListActivity sideListActivity = getSideListActivity();
		if (sideListActivity != null) {
			sideListActivity.updateSideActionMenuItems();
		}
	}

	@Override
	public void speedChanged(long downloadSpeed, long uploadSpeed) {

	}

	@Override
	public void rpcTorrentListRefreshingChanged(final boolean refreshing) {
		AndroidUtilsUI.runOnUIThread(this, false, activity -> {
			SideListActivity sideListActivity = getSideListActivity();
			if (sideListActivity != null) {
				sideListActivity.updateSideListRefreshButton();
			}
		});
	}

	@Override
	public void onlineStateChanged(boolean isOnline, boolean isOnlineMobile) {
		AndroidUtilsUI.runOnUIThread(this, false, activity -> {
			SideListActivity sideListActivity = getSideListActivity();
			if (sideListActivity != null) {
				sideListActivity.updateSideActionMenuItems();
			}
		});
	}

	private static class LastUpdatedInfo
	{
		final long sinceMS;

		final String s;

		LastUpdatedInfo(long sinceMS, String s) {
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
	public void onSaveInstanceState(@NonNull Bundle outState) {
		if (DEBUG) {
			log(TAG, "onSaveInstanceState " + isAdded());
		}
		if (torrentListAdapter != null) {
			torrentListAdapter.onSaveInstanceState(outState);
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onViewStateRestored(Bundle savedInstanceState) {
		if (DEBUG) {
			log(TAG, "onViewStateRestored");
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
	public void onResume() {
		if (DEBUG) {
			log(TAG, "onResume");
		}
		super.onResume();

		BiglyBTApp.getNetworkState().addListener(this);

		session.torrent.addListReceivedListener(TAG, this);
		session.tag.addTagListReceivedListener(this);
		session.addSessionListener(this);
		session.addSessionSettingsChangedListeners(this);
		session.torrent.addTorrentListRefreshingListener(this, false);
	}

	@Override
	public void onPause() {
		BiglyBTApp.getNetworkState().removeListener(this);

		session.tag.removeTagListReceivedListener(this);
		session.torrent.removeListReceivedListener(this);
		session.torrent.removeListRefreshingListener(this);
		session.removeSessionSettingsChangedListeners(this);
		super.onPause();
	}

	@Thunk
	void finishActionMode() {
		if (mActionMode != null) {
			mActionMode.finish();
			mActionMode = null;
		}
		torrentListAdapter.clearChecked();
	}

	@NonNull
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

		return list.toArray(new Map[0]);
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
			List<String> fields, final int[] fileIndexes, List<?> removedTorrentIDs) {
		if ((addedTorrentMaps == null || addedTorrentMaps.size() == 0)
				&& (removedTorrentIDs == null || removedTorrentIDs.size() == 0)) {
			if (torrentListAdapter.isNeverSetItems()) {
				torrentListAdapter.triggerEmptyList();
			}
			return;
		}
		AndroidUtilsUI.runOnUIThread(this, false, activity -> {
			if (torrentListAdapter == null) {
				return;
			}
			torrentListAdapter.refreshDisplayList();
		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (AndroidUtils.DEBUG_MENU) {
			log(TAG, "onOptionsItemSelected " + item.getTitle());
		}

		return super.onOptionsItemSelected(item) || handleFragmentMenuItems(item);
	}

	@Thunk
	boolean handleFragmentMenuItems(MenuItem menuItem) {
		if (AndroidUtils.DEBUG_MENU) {
			log(TAG, "HANDLE MENU FRAG " + menuItem.getItemId());
		}
		return handleTorrentMenuActions(session,
				getCheckedIDs(torrentListAdapter, true), getFragmentManager(),
				menuItem);
	}

	public static boolean handleTorrentMenuActions(Session session,
			final long[] ids, FragmentManager fm, MenuItem menuItem) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "HANDLE TORRENTMENU FRAG " + menuItem.getItemId());
		}
		if (ids == null || ids.length == 0) {
			return false;
		}

		switch (menuItem.getItemId()) {
			case R.id.action_sel_remove: {
				for (final long torrentID : ids) {
					Map<?, ?> map = session.torrent.getCachedTorrent(torrentID);
					long id = MapUtils.getMapLong(map, "id", -1);
					boolean isMagnetTorrent = TorrentUtils.isMagnetTorrent(
							session.torrent.getCachedTorrent(id));
					if (!isMagnetTorrent) {
						String name = MapUtils.getMapString(map, "name", "");
						// TODO: One at a time!
						DialogFragmentDeleteTorrent.open(fm, session, name, id);
					} else {
						session.torrent.removeTorrent(new long[] {
							id
						}, true, (SuccessReplyMapRecievedListener) (id1, optionalMap) -> {
							// removeTorrent will call getRecentTorrents, but alas,
							// magnet torrent removal isn't listed there (bug in xmwebui)
							session.torrent.clearTorrentFromCache(torrentID);
						});
					}
				}
				return true;
			}
			case R.id.action_sel_start: {
				session.torrent.startTorrents(ids, false);
				return true;
			}
			case R.id.action_sel_forcestart: {
				session.torrent.startTorrents(ids, true);
				return true;
			}
			case R.id.action_sel_stop: {
				session.torrent.stopTorrents(ids);
				return true;
			}
			case R.id.action_sel_verify: {
				session.torrent.verifyTorrents(ids);
				return true;
			}
			case R.id.action_sel_relocate: {
				Map<?, ?> mapFirst = session.torrent.getCachedTorrent(ids[0]);
				DialogFragmentMoveData.openMoveDataDialog(mapFirst, session, fm);
				return true;
			}
			case R.id.action_sel_move_top: {
				session.executeRpc(rpc -> rpc.simpleRpcCall(
						TransmissionVars.METHOD_Q_MOVE_TOP, ids, null));
				return true;
			}
			case R.id.action_sel_move_up: {
				session.executeRpc(
						rpc -> rpc.simpleRpcCall("queue-move-up", ids, null));
				return true;
			}
			case R.id.action_sel_move_down: {
				session.executeRpc(
						rpc -> rpc.simpleRpcCall("queue-move-down", ids, null));
				return true;
			}
			case R.id.action_sel_move_bottom: {
				session.executeRpc(rpc -> rpc.simpleRpcCall(
						TransmissionVars.METHOD_Q_MOVE_BOTTOM, ids, null));
				return true;
			}
		}
		return false;

	}

	@Thunk
	void updateActionModeText(ActionMode mode) {
		if (AndroidUtils.DEBUG_MENU) {
			log(TAG, "MULTI:CHECK CHANGE");
		}

		if (mode != null && isAdded()) {
			String subtitle = getString(R.string.context_torrent_subtitle_selected,
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
					log(TAG, "onCreateActionMode");
				}

				if (mode == null && torrentListAdapter.getCheckedItemCount() == 0
						&& torrentListAdapter.getSelectedPosition() < 0) {
					return false;
				}

				if (mode != null) {
					mode.setTitle(R.string.context_torrent_title);
				}
				FragmentActivity activity = requireActivity();
				activity.getMenuInflater().inflate(R.menu.menu_context_torrent_details,
						menu);

				TorrentDetailsFragment frag = (TorrentDetailsFragment) activity.getSupportFragmentManager().findFragmentById(
						R.id.frag_torrent_details);
				if (frag != null) {
					frag.onCreateActionMode(mode, menu);
				}

				SubMenu subMenu = menu.addSubMenu(R.string.menu_global_actions);
				subMenu.setIcon(R.drawable.ic_menu_white_24dp);
				subMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

				try {
					// Place "Global" actions on top bar in collapsed menu
					MenuInflater mi = mode == null ? activity.getMenuInflater()
							: mode.getMenuInflater();
					mi.inflate(R.menu.menu_torrent_list, subMenu);
					onPrepareOptionsMenu(subMenu);
				} catch (UnsupportedOperationException e) {
					Log.e(TAG, e.getMessage());
					menu.removeItem(subMenu.getItem().getItemId());
				}

				if (AndroidUtils.usesNavigationControl()) {
					MenuItem add = menu.add(R.string.select_multiple_items);
					add.setCheckable(true);
					add.setChecked(torrentListAdapter.isMultiCheckMode());
					add.setOnMenuItemClickListener(item -> {
						boolean turnOn = !torrentListAdapter.isMultiCheckModeAllowed();

						torrentListAdapter.setMultiCheckModeAllowed(turnOn);
						if (turnOn) {
							torrentListAdapter.setMultiCheckMode(true);
							torrentListAdapter.setItemChecked(
									torrentListAdapter.getSelectedPosition(), true);
						}
						return true;
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
					log(TAG, "MULTI:onPrepareActionMode " + mode);
				}

				// Must be called first, because our drawer sets all menu items
				// visible.. :(
				FragmentActivity activity = requireActivity();
				activity.onPrepareOptionsMenu(menu);

				prepareContextMenu(menu);

				TorrentDetailsFragment frag = (TorrentDetailsFragment) activity.getSupportFragmentManager().findFragmentById(
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
					log(TAG, "onActionItemClicked " + item.getTitle());
				}

				FragmentActivity activity = requireActivity();
				if (activity.onOptionsItemSelected(item)) {
					return true;
				}
				TorrentDetailsFragment frag = (TorrentDetailsFragment) activity.getSupportFragmentManager().findFragmentById(
						R.id.frag_torrent_details);
				if (frag != null && frag.onActionItemClicked(item)) {
					return true;
				}
				if (TorrentListFragment.this.handleFragmentMenuItems(item)) {
					return true;
				}

				return false;
			}

			// Called when the user exits the action mode
			@Override
			public void onDestroyActionMode(ActionMode mode) {
				if (AndroidUtils.DEBUG_MENU) {
					log(TAG,
							"onDestroyActionMode. BeingReplaced?" + actionModeBeingReplaced);
				}

				mActionMode = null;

				if (!actionModeBeingReplaced) {
					listview.post(() -> {
						torrentListAdapter.setMultiCheckMode(false);
						torrentListAdapter.clearChecked();
						updateCheckedIDs();
					});

					listview.post(() -> {
						if (mCallback != null) {
							mCallback.actionModeBeingReplacedDone();
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
		prepareTorrentMenuItems(menu, getCheckedTorrentMaps(torrentListAdapter),
				getSession());
	}

	public static void prepareTorrentMenuItems(Menu menu, Map[] torrents,
			Session session) {
		boolean isLocalHost = session.getRemoteProfile().isLocalHost();
		boolean isOnlineOrLocal = BiglyBTApp.getNetworkState().isOnline()
				|| isLocalHost;

		int numTorrents = session.torrent.getCount();

		MenuItem menuMove = menu.findItem(R.id.action_sel_move);
		if (menuMove != null) {
			boolean enabled = isOnlineOrLocal && torrents.length > 0
					&& numTorrents > 1;
			menuMove.setEnabled(enabled);
		}

		boolean canStart = false;
		boolean canStop = false;
		boolean isMagnet;
		if (isOnlineOrLocal) {
			boolean allMagnets = torrents.length > 0;
			for (Map<?, ?> mapTorrent : torrents) {
				isMagnet = TorrentUtils.isMagnetTorrent(mapTorrent);
				if (!isMagnet) {
					allMagnets = false;
					canStart |= TorrentUtils.canStart(mapTorrent, session);
					canStop |= TorrentUtils.canStop(mapTorrent, session);
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
			Log.d(TAG, "prepareContextMenu: " + canStart + "/" + canStop + " via "
					+ AndroidUtils.getCompressedStackTrace());
		}

		MenuItem menuStart = menu.findItem(R.id.action_sel_start);
		if (menuStart != null) {
			menuStart.setVisible(canStart);
		}

		MenuItem menuStop = menu.findItem(R.id.action_sel_stop);
		if (menuStop != null) {
			menuStop.setVisible(canStop);
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
		if (AndroidUtils.isTV(getContext())) {
			// TV doesn't get action bar changes, because it's impossible to get to
			// with client control when you are on row 4000
			return false;
		}
		if (mActionMode != null && !forceRebuild) {
			if (AndroidUtils.DEBUG_MENU) {
				log(TAG, "showContextualActions: invalidate existing");
			}
			mActionMode.invalidate();
			return false;
		}

		if (torrentListAdapter != null
				&& torrentListAdapter.getCheckedItemCount() == 0) {
			if (mActionMode != null) {
				mActionMode.finish();
				mActionMode = null;
			}
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
				log(TAG, "showContextualActions: startAB. mActionMode = " + mActionMode
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
			log(TAG, "FILTER BY " + name);
		}

		AndroidUtilsUI.runOnUIThread(this, false, activity -> {
			if (torrentListAdapter == null) {
				if (DEBUG) {
					log(TAG, "No torrentListAdapter in filterBy");
				}
				return;
			}
			// java.lang.RuntimeException: Can't create handler inside thread that
			// has not called Looper.prepare()
			TorrentListFilter filter = torrentListAdapter.getTorrentFilter();
			filter.setFilterMode(filterMode);
			if (tvFilteringBy != null) {
				Session session = getSession();
				Map<?, ?> tag = session.tag.getTag(filterMode);
				SpanTags spanTags = new SpanTags();
				spanTags.init(getContext(), tvFilteringBy, null);
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
					log(TAG, "null field in filterBy");
				}
			}
		});
		if (save) {
			Session session = getSession();
			session.getRemoteProfile().setFilterBy(filterMode);
			session.saveProfile();
		}
	}

	@Thunk
	void updateTorrentCount() {
		AndroidUtilsUI.runOnUIThread(this, false, activity -> {
			String s = "";
			int total = torrentListAdapter.getItemCount(
					TorrentListAdapter.VIEWTYPE_TORRENT);

			SideListActivity sideListActivity = getSideListActivity();
			if (sideListActivity != null) {
				sideListActivity.updateSideActionMenuItems();
			}

			CharSequence constraint = torrentListAdapter.getFilter().getConstraint();
			boolean constraintEmpty = constraint == null || constraint.length() == 0;

			if (total != 0) {
				if (!constraintEmpty) {
					s = getResources().getQuantityString(R.plurals.torrent_count, total,
							total);
				}
			} else {

				if (tvEmpty != null) {
					Session session = getSession();
					int size = session.torrent.getCount();
					tvEmpty.setText(size > 0 ? R.string.list_filtered_empty
							: R.string.torrent_list_empty);
				}

			}
			if (tvTorrentCount != null) {
				tvTorrentCount.setText(s);
			}
		});
	}

	@Override
	public void setActionModeBeingReplaced(ActionMode actionMode,
			boolean actionModeBeingReplaced) {
		if (AndroidUtils.DEBUG_MENU) {
			log(TAG,
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
			log(TAG, "actionModeBeingReplacedDone: rebuild? " + rebuildActionMode);
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
			mActionMode = null;
		}
	}

	@Override
	public void rebuildActionMode() {
		showContextualActions(true);
	}

	public void startStopTorrents() {
		Map<?, ?>[] checkedTorrentMaps = getCheckedTorrentMaps(torrentListAdapter);
		if (checkedTorrentMaps.length == 0) {
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
		sideTagAdapter.setItems(list, null, (oldItem, newItem) -> {

			if (oldItem.id != newItem.id) {
				return false;
			}

			final Session session = getSession();
			Map<?, ?> oldTag = session.tag.getTag(oldItem.id);
			Map<?, ?> newTag = session.tag.getTag(newItem.id);
			if (oldTag == null || newTag == null) {
				return oldTag == newTag;
			}
			if (oldTag.size() != newTag.size()) {
				return false;
			}
			Object[] tagKeys;
			synchronized (oldTag) {
				tagKeys = oldTag.keySet().toArray();
			}
			for (Object key : tagKeys) {
				Object oldVal = oldTag.get(key);
				Object newVal = newTag.get(key);
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
		});

		AndroidUtilsUI.runOnUIThread(this, false, activity -> {
			if (tvFilteringBy == null) {
				return;
			}
			tvFilteringBy.invalidate();
		});
	}
}
