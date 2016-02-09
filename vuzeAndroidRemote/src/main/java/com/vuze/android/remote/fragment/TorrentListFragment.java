/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.vuze.android.remote.fragment;

import java.util.*;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.support.v7.widget.*;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.ValueStringArray;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.dialog.*;
import com.vuze.android.remote.dialog.DialogFragmentFilterBy.FilterByDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentSortBy.SortByDialogListener;
import com.vuze.android.remote.fragment.TorrentListAdapter.TorrentFilter;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.util.MapUtils;

/**
 * Handles a ListView that shows Torrents
 */
public class TorrentListFragment
	extends Fragment
	implements TorrentListReceivedListener, FilterByDialogListener,
	SortByDialogListener, SessionInfoListener, ActionModeBeingReplacedListener
{
	private RecyclerView listview;

	public interface OnTorrentSelectedListener
		extends ActionModeBeingReplacedListener
	{
		void onTorrentSelectedListener(TorrentListFragment torrentListFragment,
				long[] ids, boolean inMultiMode);
	}

	private OnTorrentSelectedListener mCallback;

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private static final String TAG = "TorrentList";

	protected ActionMode mActionMode;

	private TorrentListAdapter<TorrentListViewHolder> adapter;

	private SessionInfo sessionInfo;

	private EditText filterEditText;

	private Callback mActionModeCallbackV7;

	private TextView tvFilteringBy;

	private TextView tvTorrentCount;

	private boolean actionModeBeingReplaced;

	private boolean rebuildActionMode;

	long lastIdClicked = -1;

	private Toolbar tb;

	@Override
	public void onAttach(Context activity) {
		super.onAttach(activity);

		if (activity instanceof OnTorrentSelectedListener) {
			mCallback = (OnTorrentSelectedListener) activity;
		}

		FlexibleRecyclerSelectionListener rs = new FlexibleRecyclerSelectionListener() {
			@Override
			public void onItemSelected(final int position, boolean isChecked) {
			}

			@Override
			public void onItemClick(int position) {
			}

			@Override
			public boolean onItemLongClick(int position) {
				return false;
			}

			@Override
			public void onItemCheckedChanged(int position, boolean isChecked) {
				if (mActionMode == null && isChecked) {
					showContextualActions(false);
				}

				if (adapter.getCheckedItemCount() == 0) {
					finishActionMode();
				}

				if (adapter.isMultiSelectMode()) {
					updateActionModeText(mActionMode);
				}
				updateCheckedIDs();

				AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}
		};

		adapter = new TorrentListAdapter<>(activity, rs);
		adapter.setCheckOnSelectedAfterMS(200);
		adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
			@Override
			public void onChanged() {
				super.onChanged();
				updateTorrentCount(adapter.getItemCount());
			}
		});
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
			sortBy(sortBy, sortOrder, false);
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
	 * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.frag_torrent_list, container, false);

		setupActionModeCallback();

		final SwipeTextRefreshLayout swipeRefresh = (SwipeTextRefreshLayout) view.findViewById(
				R.id.swipe_container);
		if (swipeRefresh != null) {
			LastUpdatedInfo lui = getLastUpdatedString();
			if (lui != null) {
				swipeRefresh.getTextView().setText(lui.s);
			}
			swipeRefresh.setOnRefreshListener(
					new SwipeRefreshLayout.OnRefreshListener() {
						@Override
						public void onRefresh() {
							if (sessionInfo == null) {
								return;
							}
							sessionInfo.triggerRefresh(true,
									new TorrentListReceivedListener() {
								@Override
								public void rpcTorrentListReceived(String callID,
										List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
									AndroidUtils.runOnUIThread(TorrentListFragment.this,
											new Runnable() {
										@Override
										public void run() {
											swipeRefresh.setRefreshing(false);
											LastUpdatedInfo lui = getLastUpdatedString();
											swipeRefresh.getTextView().setText(lui.s);
										}
									});
								}
							});

						}
					});
			swipeRefresh.setOnTextVisibilityChange(
					new SwipeTextRefreshLayout.OnTextVisibilityChangeListener() {
						private Handler pullRefreshHandler;

						@Override
						public void onTextVisibilityChange(TextView tv, int visibility) {
							{
								if (visibility == View.VISIBLE) {
									if (pullRefreshHandler != null) {
										pullRefreshHandler.removeCallbacks(null);
										pullRefreshHandler = null;
									}
									pullRefreshHandler = new Handler(Looper.getMainLooper());

									pullRefreshHandler.postDelayed(new Runnable() {
										@Override
										public void run() {
											LastUpdatedInfo lui = getLastUpdatedString();
											if (lui == null) {
												return;
											}
											swipeRefresh.getTextView().setText(lui.s);

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
								} else {
									if (pullRefreshHandler != null) {
										pullRefreshHandler.removeCallbacksAndMessages(null);
										pullRefreshHandler = null;
									}
								}
							}
						}
					});
		}

		listview = (RecyclerView) view.findViewById(R.id.listTorrents);
		listview.setLayoutManager(new LinearLayoutManager(getContext()));
		listview.setAdapter(adapter);
		((SimpleItemAnimator) listview.getItemAnimator()).setSupportsChangeAnimations(
				false);

//		if (AndroidUtils.isTV()) {
//			registerForContextMenu(listview);
//		}

		filterEditText = (EditText) view.findViewById(R.id.filterText);
		filterEditText.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				Filter filter = adapter.getFilter();
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

		/** Handy code to watch the states of row 2
		listview.postDelayed(new Runnable() {
			String oldS = "";
		
			@Override
			public void run() {
		
				String s = (listview.getChildCount() < 3 ? ""
						: AndroidUtils.getStatesString(listview.getChildAt(2).getDrawableState()));
		
				if (!s.equals(oldS)) {
					oldS = s;
					Log.e(TAG, "States of 2: " + s);
				}
		
				listview.postDelayed(this, 500);
			}
		}, 500);
		*/

		setHasOptionsMenu(true);

		return view;
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
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onViewStateRestored(Bundle savedInstanceState) {
		if (DEBUG) {
			Log.d(TAG, "onViewStateRestored");
		}
		super.onViewStateRestored(savedInstanceState);
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

		if (getActivity() instanceof SessionInfoGetter) {
			SessionInfoGetter getter = (SessionInfoGetter) getActivity();
			sessionInfo = getter.getSessionInfo();
			adapter.setSessionInfo(sessionInfo);
			sessionInfo.addTorrentListReceivedListener(TAG, this);

			sessionInfo.addRpcAvailableListener(this);
		}
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onPause()
	 */
	@Override
	public void onPause() {
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
		adapter.clearChecked();
	}

	private static Map<?, ?>[] getCheckedTorrentMaps(TorrentListAdapter adapter) {
		if (adapter == null) {
			return new Map[0];
		}
		Integer[] checkedItems = adapter.getCheckedItemPositions();

		List<Map> list = new ArrayList<>();

		for (Integer position : checkedItems) {
			Map<?, ?> torrent = adapter.getTorrentItem(position);
			if (torrent != null) {
				list.add(torrent);
			}
		}

		return list.toArray(new Map[list.size()]);
	}

	private static long[] getCheckedIDs(
			TorrentListAdapter<TorrentListViewHolder> adapter) {
		if (adapter == null) {
			return new long[] {};
		}
		Integer[] checkedItems = adapter.getCheckedItemPositions();

		List<Long> list = new ArrayList<>();
		for (Integer position : checkedItems) {
			long torrentID = adapter.getTorrentID(position);
			if (torrentID >= 0) {
				list.add(torrentID);
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
				if (adapter == null) {
					return;
				}
				adapter.refreshDisplayList();
			}
		});
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onOptionsItemSelected " + item.getTitle());
		}
		return handleFragmentMenuItems(item.getItemId())
				|| super.onOptionsItemSelected(item);
	}

	public boolean handleFragmentMenuItems(int itemId) {
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
		return handleTorrentMenuActions(sessionInfo, getCheckedIDs(adapter),
				getFragmentManager(), itemId);
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
		} else if (itemId == R.id.action_sel_relocate) {
			Map<?, ?> mapFirst = sessionInfo.getTorrent(ids[0]);
			DialogFragmentMoveData.openMoveDataDialog(mapFirst, sessionInfo, fm);
			return true;
		} else if (itemId == R.id.action_sel_move_top) {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall("queue-move-top", ids, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_move_up) {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall("queue-move-up", ids, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_move_down) {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall("queue-move-down", ids, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_move_bottom) {
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
					adapter.getCheckedItemCount());
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

				if (mode == null && adapter.getCheckedItemCount() == 0) {
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

				SubMenu subMenu = origMenu.addSubMenu(R.string.menu_global_actions);
				subMenu.setIcon(R.drawable.ic_menu_more);
				MenuItemCompat.setShowAsAction(subMenu.getItem(),
						MenuItemCompat.SHOW_AS_ACTION_NEVER);

				try {
					// Place "Global" actions on top bar in collapsed menu
					MenuInflater mi = mode == null ? getActivity().getMenuInflater()
							: mode.getMenuInflater();
					mi.inflate(R.menu.menu_torrent_list, subMenu);
				} catch (UnsupportedOperationException e) {
					Log.e(TAG, e.getMessage());
					menu.removeItem(subMenu.getItem().getItemId());
				}

				return true;
			}

			// Called each time the action mode is shown. Always called after onCreateActionMode, but
			// may be called multiple times if the mode is invalidated.
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {

				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "MULTI:onPrepareActionMode " + mode);
				}
				if (tb != null) {
					menu = tb.getMenu();
				}

				prepareContextMenu(menu);

				TorrentDetailsFragment frag = (TorrentDetailsFragment) getActivity().getSupportFragmentManager().findFragmentById(
						R.id.frag_torrent_details);
				if (frag != null) {
					frag.onPrepareActionMode(mode, menu);
				}

				getActivity().onPrepareOptionsMenu(menu);

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
					adapter.setMultiSelectMode(false);
					lastIdClicked = -1;
					listview.post(new Runnable() {
						@Override
						public void run() {
							adapter.setMultiSelectMode(false);
							updateCheckedIDs();
							// Not sure why ListView doesn't invalidate by default
							adapter.notifyDataSetInvalidated();
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
			boolean enabled = isOnlineOrLocal && adapter.getCheckedItemCount() > 0;
			menuMove.setEnabled(enabled);
		}

		Map<?, ?>[] checkedTorrentMaps = getCheckedTorrentMaps(adapter);
		boolean canStart = false;
		boolean canStop = false;
		if (isOnlineOrLocal) {
			for (Map<?, ?> mapTorrent : checkedTorrentMaps) {
				int status = MapUtils.getMapInt(mapTorrent,
						TransmissionVars.FIELD_TORRENT_STATUS,
						TransmissionVars.TR_STATUS_STOPPED);
				canStart |= status == TransmissionVars.TR_STATUS_STOPPED;
				canStop |= status != TransmissionVars.TR_STATUS_STOPPED;
			}
		}
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "prepareContextMenu: " + canStart + "/" + canStop);
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
				if (adapter == null) {
					if (DEBUG) {
						Log.d(TAG, "No adapter in filterBy");
					}
					return;
				}
				// java.lang.RuntimeException: Can't create handler inside thread that has not called Looper.prepare()
				TorrentFilter filter = adapter.getFilter();
				filter.setFilterMode(filterMode);
				if (tvFilteringBy != null) {
					Map<?, ?> tag = sessionInfo.getTag(filterMode);
					SpanTags spanTags = new SpanTags();
					spanTags.init(getContext(), sessionInfo, tvFilteringBy, null);
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
	 * @see com.vuze.android.remote.dialog.DialogFragmentSortBy.SortByDialogListener#sortBy(java.lang.String[], java.lang.Boolean[], boolean)
	 */
	@Override
	public void sortBy(final String[] sortFieldIDs, final Boolean[] sortOrderAsc,
			boolean save) {
		if (DEBUG) {
			Log.d(TAG, "SORT BY " + Arrays.toString(sortFieldIDs));
		}
		AndroidUtils.runOnUIThread(this, new Runnable() {
			public void run() {
				if (adapter == null) {
					return;
				}
				adapter.setSort(sortFieldIDs, sortOrderAsc);
			}
		});

		if (save && sessionInfo != null) {
			sessionInfo.getRemoteProfile().setSortBy(sortFieldIDs, sortOrderAsc);
			sessionInfo.saveProfile();
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentSortBy.SortByDialogListener#flipSortOrder()
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
		sortBy(remoteProfile.getSortBy(), sortOrder, true);
	}

	private void updateTorrentCount(final long total) {
		if (tvTorrentCount == null) {
			return;
		}
		AndroidUtils.runOnUIThread(this, new Runnable() {
			public void run() {
				if (total == 0) {
					tvTorrentCount.setText("");
				} else {
					tvTorrentCount.setText(total + " torrents");
				}
			}
		});
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#setActionModeBeingReplaced(boolean)
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
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#actionModeBeingReplacedDone()
	 */
	@Override
	public void actionModeBeingReplacedDone() {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "actionModeBeingReplacedDone: rebuild? " + rebuildActionMode);
		}
		if (rebuildActionMode) {
			rebuildActionMode = false;

			rebuildActionMode();
			adapter.setMultiSelectMode(false);
			/*
				// Restore Selection
				long[] oldcheckedIDs = new long[checkedTorrentIDs.length];
				System.arraycopy(checkedTorrentIDs, 0, oldcheckedIDs, 0, oldcheckedIDs.length);
				int count = adapter.getItemCount();
				adapter.clearChecked();
				int numFound = 0;
				for (int i = 0; i < count; i++) {
					long itemIdAtPosition = listview.getItemIdAtPosition(i);
					for (long torrentID : oldcheckedIDs) {
						if (torrentID == itemIdAtPosition) {
							listview.setItemChecked(i, true);
							numFound++;
							break;
						}
					}
					if (numFound == oldcheckedIDs.length) {
						break;
					}
				}
			*/
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#getActionMode()
	 */
	@Override
	public ActionMode getActionMode() {
		return mActionMode;
	}

	public void clearSelection() {
		finishActionMode();
	}

	private void updateCheckedIDs() {
		long[] checkedTorrentIDs = getCheckedIDs(adapter);
		if (mCallback != null) {
			mCallback.onTorrentSelectedListener(TorrentListFragment.this,
					checkedTorrentIDs, adapter.isMultiSelectMode());
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
		Map<?, ?>[] checkedTorrentMaps = getCheckedTorrentMaps(adapter);
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
					long[] ids = getCheckedIDs(adapter);
					rpc.stopTorrents(TAG, ids, null);
				}
			});
		} else {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					long[] ids = getCheckedIDs(adapter);
					rpc.startTorrents(TAG, ids, false, null);
				}
			});
		}
	}

	@Override
	public Callback getActionModeCallback() {
		return mActionModeCallbackV7;
	}
}
