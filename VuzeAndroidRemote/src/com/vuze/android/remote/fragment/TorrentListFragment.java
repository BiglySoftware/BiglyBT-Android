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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.database.DataSetObserver;
import android.os.*;
import android.support.v4.app.*;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

import com.aelitis.azureus.util.MapUtils;
import com.google.analytics.tracking.android.MapBuilder;
import com.handmark.pulltorefresh.library.*;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnPullEventListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.State;
import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtils.ValueStringArray;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.R;
import com.vuze.android.remote.dialog.*;
import com.vuze.android.remote.dialog.DialogFragmentFilterBy.FilterByDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentMoveData.MoveDataDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentSortBy.SortByDialogListener;
import com.vuze.android.remote.fragment.TorrentListAdapter.TorrentFilter;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;

/**
 */
public class TorrentListFragment
	extends Fragment
	implements MoveDataDialogListener, TorrentListReceivedListener,
	FilterByDialogListener, SortByDialogListener, SessionInfoListener,
	ActionModeBeingReplacedListener
{
	public interface OnTorrentSelectedListener
	{
		public void onTorrentSelectedListener(
				TorrentListFragment torrentListFragment, long[] ids, boolean inMultiMode);
	}

	// Although MultiChoiceMode is available in 3.0/v11 (HONEYCOMB), it crashes
	// LV-909 on 3.1/v12 (HONEYCOMB).  Might work on 3.2/v13, but let's be safe
	// use appcompat for all Honeycomb (and below)
	private static final int VERSION_CODE_MULTIACTION = Build.VERSION_CODES.ICE_CREAM_SANDWICH;

	private OnTorrentSelectedListener mCallback;

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private static final String TAG = "TorrentList";

	private ListView listview;

	protected ActionModeWrapper mActionMode;

	private TorrentListAdapter adapter;

	private SessionInfo sessionInfo;

	private EditText filterEditText;

	private Callback mActionModeCallback;

	private TextView tvFilteringBy;

	private TextView tvTorrentCount;

	private PullToRefreshListView pullListView;

	private long lastUpdated;

	private boolean actionModeBeingReplaced;

	private boolean rebuildActionMode;

	long lastIdClicked = -1;

	private ActionModeWrapper.MultiChoiceModeListener multiChoiceModeListener;

	private long[] checkedIDs = {};

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (activity instanceof OnTorrentSelectedListener) {
			mCallback = (OnTorrentSelectedListener) activity;
		}

		adapter = new TorrentListAdapter(activity);
		adapter.registerDataSetObserver(new DataSetObserver() {
			@Override
			public void onChanged() {
				updateTorrentCount(adapter.getCount());

				long[] newCheckedIDs = getCheckedIDs(listview);
				if (newCheckedIDs.length == 0 && checkedIDs.length == 0) {
					return;
				}
				boolean redo = newCheckedIDs.length != checkedIDs.length;
				if (!redo) {
					// No redo if all ids are found at same spot
					for (long newID : newCheckedIDs) {
						boolean found = false;
						for (long torrentID : checkedIDs) {
							if (torrentID == newID) {
								found = true;
								break;
							}
						}
						if (!found) {
							redo = true;
							break;
						}
					}
				}

				if (redo) {
					long[] oldCheckedIDs = new long[checkedIDs.length];
					System.arraycopy(checkedIDs, 0, oldCheckedIDs, 0,
							oldCheckedIDs.length);
					int count = listview.getCount();
					listview.clearChoices();
					int numFound = 0;
					for (int i = 0; i < count; i++) {
						long itemIdAtPosition = listview.getItemIdAtPosition(i);
						for (long torrentID : oldCheckedIDs) {
							if (torrentID == itemIdAtPosition) {
								listview.setItemChecked(i, true);
								numFound++;
								break;
							}
						}
						if (numFound == oldCheckedIDs.length) {
							break;
						}
					}

					if (numFound != oldCheckedIDs.length) {
						updateCheckedIDs();
					}
				}
				AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}
		});

		if (activity instanceof SessionInfoGetter) {
			SessionInfoGetter getter = (SessionInfoGetter) activity;
			sessionInfo = getter.getSessionInfo();
			adapter.setSessionInfo(sessionInfo);
			sessionInfo.addTorrentListReceivedListener(TAG, this);

			sessionInfo.addRpcAvailableListener(this);
		}
	}

	/* (non-Javadoc)
	 */
	@Override
	public void uiReady(TransmissionRPC rpc) {
		/*
		sessionInfo.getRpc().simpleRpcCall("rcm-is-enabled" , new ReplyMapReceivedListener() {
			
			@Override
			public void rpcSuccess(String id, Map optionalMap) {
				System.out.println("rcm-is-enabled: " + optionalMap);
			}
			
			@Override
			public void rpcFailure(String id, String message) {
			}
			
			@Override
			public void rpcError(String id, Exception e) {
			}
		});
		*/
		
		if (getActivity() == null) {
			return;
		}

		rpc.getAllTorrents(TAG, null);

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

		View oListView = view.findViewById(R.id.listTorrents);
		if (oListView instanceof ListView) {
			listview = (ListView) oListView;
		} else if (oListView instanceof PullToRefreshListView) {
			pullListView = (PullToRefreshListView) oListView;
			listview = pullListView.getRefreshableView();
			pullListView.setOnPullEventListener(new OnPullEventListener<ListView>() {
				private Handler pullRefreshHandler;

				@Override
				public void onPullEvent(PullToRefreshBase<ListView> refreshView,
						State state, Mode direction) {
					if (state == State.PULL_TO_REFRESH) {
						if (pullRefreshHandler != null) {
							pullRefreshHandler.removeCallbacks(null);
							pullRefreshHandler = null;
						}
						pullRefreshHandler = new Handler(Looper.getMainLooper());

						pullRefreshHandler.postDelayed(new Runnable() {
							@Override
							public void run() {
								FragmentActivity activity = getActivity();
								if (activity == null) {
									return;
								}
								long sinceMS = System.currentTimeMillis() - lastUpdated;
								String since = DateUtils.getRelativeDateTimeString(activity,
										lastUpdated, DateUtils.SECOND_IN_MILLIS,
										DateUtils.WEEK_IN_MILLIS, 0).toString();
								String s = activity.getResources().getString(
										R.string.last_updated, since);
								pullListView.getLoadingLayoutProxy().setLastUpdatedLabel(s);

								if (pullRefreshHandler != null) {
									pullRefreshHandler.postDelayed(this,
											sinceMS < DateUtils.MINUTE_IN_MILLIS
													? DateUtils.SECOND_IN_MILLIS
													: sinceMS < DateUtils.HOUR_IN_MILLIS
															? DateUtils.MINUTE_IN_MILLIS
															: DateUtils.HOUR_IN_MILLIS);
								}
							}
						}, 0);
					} else if (state == State.RESET || state == State.REFRESHING) {
						if (pullRefreshHandler != null) {
							pullRefreshHandler.removeCallbacksAndMessages(null);
							pullRefreshHandler = null;
						}
					}
				}
			});
			pullListView.setOnRefreshListener(new OnRefreshListener<ListView>() {
				@Override
				public void onRefresh(PullToRefreshBase<ListView> refreshView) {
					if (sessionInfo == null) {
						return;
					}
					sessionInfo.triggerRefresh(true, new TorrentListReceivedListener() {
						@Override
						public void rpcTorrentListReceived(String callID,
								List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
							AndroidUtils.runOnUIThread(TorrentListFragment.this,
									new Runnable() {
										@Override
										public void run() {
											pullListView.onRefreshComplete();
										}
									});
						}
					});
				}

			});
		}
		filterEditText = (EditText) view.findViewById(R.id.filterText);

		listview.setItemsCanFocus(false);
		listview.setClickable(true);

		listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		listview.setAdapter(adapter);

		setupMultiChoiceListener();
		if (Build.VERSION.SDK_INT >= VERSION_CODE_MULTIACTION) {
			setupHoneyCombListView(listview);
		} else {
			setupOldListView(listview);
		}

		listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, final View view,
					int position, long id) {
				//Object item = parent.getItemAtPosition(position);

				if (DEBUG) {
					Log.d(
							TAG,
							position + "/" + id + "CLICKED; checked? "
									+ listview.isItemChecked(position) + "; last="
									+ lastIdClicked + "; sel? "
									+ AndroidUtils.isChecked(listview, position));
				}

				boolean isChecked = listview.isItemChecked(position)
						|| AndroidUtils.isChecked(listview, position);
				int choiceMode = listview.getChoiceMode();

				if (choiceMode == ListView.CHOICE_MODE_MULTIPLE) {
					multiChoiceModeListener.onItemCheckedStateChanged(mActionMode,
							position, id, isChecked);
				}

				if (choiceMode == ListView.CHOICE_MODE_MULTIPLE_MODAL) {
					lastIdClicked = -1;
					// CHOICE_MODE_MULTIPLE_MODAL doesn't check items
					listview.setItemChecked(position, !isChecked);
				} else {
					if (!isChecked) {
						listview.setItemChecked(position, true);
					}
					// always isChecked, so we can't use it to uncheck
					// maybe actionmode will help..
					if (mActionMode == null) {
						showContextualActions(false);
						lastIdClicked = id;
					} else if (lastIdClicked == id) {
						listview.setItemChecked(position, false);
						lastIdClicked = -1;
					} else {
						lastIdClicked = id;
					}
				}

				if (AndroidUtils.getCheckedItemCount(listview) == 0) {
					finishActionMode();
				}

				updateCheckedIDs();

				AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}

		});

		// Long click switches to multi-select mode
		listview.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				int[] checkedPositions = AndroidUtils.getCheckedPositions(listview);
				if (Build.VERSION.SDK_INT >= VERSION_CODE_MULTIACTION) {
					switchListViewToMulti_HC();
				} else {
					listview.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
					listview.setLongClickable(false);
					((ActionBarActivity) getActivity()).startSupportActionMode(new InternalOlderListener());
				}
				for (int pos : checkedPositions) {
					listview.setItemChecked(pos, true);
				}
				listview.setItemChecked(position, true);
				lastIdClicked = id;

				if (listview.getChoiceMode() == ListView.CHOICE_MODE_MULTIPLE) {
					multiChoiceModeListener.onItemCheckedStateChanged(mActionMode,
							position, id, true);
				}
				AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
				return true;
			}
		});
		
		listview.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		filterEditText.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
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

		setHasOptionsMenu(true);

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putString("filter_name", tvFilteringBy.getText().toString());
	}

	@Override
	public void onViewStateRestored(Bundle savedInstanceState) {
		super.onViewStateRestored(savedInstanceState);
		if (listview != null) {
			updateCheckedIDs();
		}

		if (savedInstanceState != null) {
			String filterName = savedInstanceState.getString("filter_name");
			if (filterName != null && tvFilteringBy != null) {
				tvFilteringBy.setText(filterName);
			}
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this, TAG);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		FragmentActivity activity = getActivity();
		tvFilteringBy = (TextView) activity.findViewById(R.id.wvFilteringBy);
		tvTorrentCount = (TextView) activity.findViewById(R.id.wvTorrentCount);

		super.onActivityCreated(savedInstanceState);
	}

	public void finishActionMode() {
		if (mActionMode != null) {
			mActionMode.finish();
		}
	}

	private static Map<?, ?>[] getCheckedTorrentMaps(ListView listview) {
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		Map<?, ?>[] torrentMaps = new Map<?, ?>[size];
		int pos = 0;
		for (int i = 0; i < size; i++) {
			int key = checked.keyAt(i);
			boolean value = checked.get(key);
			if (value) {
				try {
					Map<?, ?> mapTorrent = (Map<?, ?>) listview.getItemAtPosition(key);
					if (mapTorrent != null) {
						torrentMaps[pos] = mapTorrent;
						pos++;
					}
				} catch (IndexOutOfBoundsException e) {
					// HeaderViewListAdapter will not call our Adapter, but throw OOB
				}
			}
		}
		if (pos < size) {
			Map<?, ?>[] torrents = new Map<?, ?>[pos];
			System.arraycopy(torrentMaps, 0, torrents, 0, pos);
			return torrents;
		}
		return torrentMaps;
	}

	private static long[] getCheckedIDs(ListView listview) {
		if (listview == null) {
			return new long[] {};
		}
		SparseBooleanArray checked = listview.getCheckedItemPositions();
		int size = checked.size(); // number of name-value pairs in the array
		long[] moreIDs = new long[size];
		int pos = 0;
		for (int i = 0; i < size; i++) {
			int key = checked.keyAt(i);
			boolean value = checked.get(key);
			if (value) {
				try {
					Map<?, ?> mapTorrent = (Map<?, ?>) listview.getItemAtPosition(key);
					long id = MapUtils.getMapLong(mapTorrent, "id", -1);
					if (id >= 0) {
						moreIDs[pos] = id;
						pos++;
					}
				} catch (IndexOutOfBoundsException e) {
					// HeaderViewListAdapter will not call our Adapter, but throw OOB
				}
			}
		}
		if (pos < size) {
			long[] ids = new long[pos];
			System.arraycopy(moreIDs, 0, ids, 0, pos);
			return ids;
		}
		return moreIDs;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.rpc.TorrentListReceivedListener#rpcTorrentListReceived(java.util.List)
	 */
	@Override
	public void rpcTorrentListReceived(String callID, List<?> addedTorrentMaps,
			List<?> removedTorrentIDs) {
		lastUpdated = System.currentTimeMillis();
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

	@TargetApi(VERSION_CODE_MULTIACTION)
	private void switchListViewToMulti_HC() {
		// CHOICE_MODE_MULTIPLE_MODAL is for CAB
		listview.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
	}

	private void setupMultiChoiceListener() {
		multiChoiceModeListener = new ActionModeWrapper.MultiChoiceModeListener() {

			// Called when the action mode is created; startActionMode() was called
			@Override
			public boolean onCreateActionMode(ActionModeWrapper mode, Menu menu) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "MULTI:ON CREATEACTIONMODE");
				}
				mActionMode = mode;
				// Inflate a menu resource providing context menu items
				MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.menu_context_torrent_details, menu);
				mActionMode.setTitle(R.string.context_torrent_title);

				SubMenu subMenu = menu.addSubMenu("Global Actions");
				subMenu.setIcon(R.drawable.ic_menu_more);
				MenuItemCompat.setShowAsAction(subMenu.getItem(),
						MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
				getActivity().onCreateOptionsMenu(subMenu);

				return true;
			}

			// Called each time the action mode is shown. Always called after onCreateActionMode, but
			// may be called multiple times if the mode is invalidated.
			@Override
			public boolean onPrepareActionMode(ActionModeWrapper mode, Menu menu) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "MULTI:onPrepareActionMode");
				}

				prepareContextMenu(menu);

				getActivity().onPrepareOptionsMenu(menu);

				AndroidUtils.fixupMenuAlpha(menu);

				return true;
			}

			// Called when the user selects a contextual menu item
			@Override
			public boolean onActionItemClicked(ActionModeWrapper mode, MenuItem item) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "MULTI:onActionItemClicked");
				}
				if (getActivity().onOptionsItemSelected(item)) {
					return true;
				}
				if (TorrentListFragment.this.handleFragmentMenuItems(item.getItemId())) {
					return true;
				}
				return false;
			}

			// Called when the user exits the action mode
			@Override
			public void onDestroyActionMode(ActionModeWrapper mode) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "MULTI:onDestroyActionMode. BeingReplaced?"
							+ actionModeBeingReplaced);
				}
				if (!actionModeBeingReplaced) {
					AndroidUtils.clearChecked(listview);
					lastIdClicked = -1;
					mActionMode = null;
					listview.post(new Runnable() {
						@Override
						public void run() {
							listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
							updateCheckedIDs();
						}
					});
				}
			}

			@Override
			public void onItemCheckedStateChanged(ActionModeWrapper mode,
					int position, long id, boolean checked) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "MULTI:CHECK CHANGE");
				}

				if (mode != null) {
					String subtitle = getResources().getString(
							R.string.context_torrent_subtitle_selected,
							AndroidUtils.getCheckedItemCount(listview));
					mode.setSubtitle(subtitle);
				}
				updateCheckedIDs();
			}
		};
	}

	private void setupOldListView(ListView lv) {

	}

	@TargetApi(VERSION_CODE_MULTIACTION)
	private void setupHoneyCombListView(ListView lv) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "MULTI:setup");
		}

		lv.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

			@Override
			public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu) {
				return multiChoiceModeListener.onCreateActionMode(
						new ActionModeWrapper(mode), menu);
			}

			@Override
			public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {
				return multiChoiceModeListener.onPrepareActionMode(
						new ActionModeWrapper(mode), menu);
			}

			@Override
			public boolean onActionItemClicked(android.view.ActionMode mode,
					MenuItem item) {
				return multiChoiceModeListener.onActionItemClicked(
						new ActionModeWrapper(mode), item);
			}

			@Override
			public void onDestroyActionMode(android.view.ActionMode mode) {
				multiChoiceModeListener.onDestroyActionMode(new ActionModeWrapper(mode));
			}

			@Override
			public void onItemCheckedStateChanged(android.view.ActionMode mode,
					int position, long id, boolean checked) {
				multiChoiceModeListener.onItemCheckedStateChanged(
						new ActionModeWrapper(mode), position, id, checked);
			}
		});

	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (handleFragmentMenuItems(item.getItemId())) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public boolean handleFragmentMenuItems(int itemId) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "HANDLE MENU FRAG " + itemId);
		}
		if (sessionInfo == null) {
			return false;
		}
		switch (itemId) {
			case R.id.action_filterby:
				DialogFragmentFilterBy.openFilterByDialog(this,
						sessionInfo.getRemoteProfile().getID());
				return true;

			case R.id.action_filter:
				boolean newVisibility = filterEditText.getVisibility() != View.VISIBLE;
				filterEditText.setVisibility(newVisibility ? View.VISIBLE : View.GONE);
				if (newVisibility) {
					filterEditText.requestFocus();
					InputMethodManager mgr = (InputMethodManager) getActivity().getSystemService(
							Context.INPUT_METHOD_SERVICE);
					mgr.showSoftInput(filterEditText, InputMethodManager.SHOW_IMPLICIT);
					VuzeEasyTracker.getInstance(this).send(
							MapBuilder.createEvent("uiAction", "ViewShown", "FilterBox", null).build());
				} else {
					InputMethodManager mgr = (InputMethodManager) getActivity().getSystemService(
							Context.INPUT_METHOD_SERVICE);
					mgr.hideSoftInputFromWindow(filterEditText.getWindowToken(), 0);
				}
				return true;

			case R.id.action_sortby:
				DialogFragmentSortBy.open(getFragmentManager(), this);
				return true;

		}
		return handleTorrentMenuActions(sessionInfo, getCheckedIDs(listview),
				getFragmentManager(), itemId);
	}

	public static boolean handleTorrentMenuActions(SessionInfo sessionInfo,
			final long[] ids, FragmentManager fm, int itemId) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "HANDLE MENU FRAG " + itemId);
		}
		if (sessionInfo == null || ids == null || ids.length == 0) {
			return false;
		}
		switch (itemId) {
			case R.id.action_sel_remove: {
				for (long torrentID : ids) {
					Map<?, ?> map = sessionInfo.getTorrent(torrentID);
					long id = MapUtils.getMapLong(map, "id", -1);
					String name = MapUtils.getMapString(map, "name", "");
					// TODO: One at a time!
					DialogFragmentDeleteTorrent.open(fm, sessionInfo, name, id);
				}
				return true;
			}
			case R.id.action_sel_start: {
				sessionInfo.executeRpc(new RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						rpc.startTorrents(TAG, ids, false, null);
					}
				});
				return true;
			}
			case R.id.action_sel_forcestart: {
				sessionInfo.executeRpc(new RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						rpc.startTorrents(TAG, ids, true, null);
					}
				});
				return true;
			}
			case R.id.action_sel_stop: {
				sessionInfo.executeRpc(new RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						rpc.stopTorrents(TAG, ids, null);
					}
				});
				return true;
			}
			case R.id.action_sel_relocate: {
				Map<?, ?> mapFirst = sessionInfo.getTorrent(ids[0]);
				AndroidUtils.openMoveDataDialog(mapFirst, sessionInfo, fm);
				return true;
			}
			case R.id.action_sel_move_top: {
				sessionInfo.executeRpc(new RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						rpc.simpleRpcCall("queue-move-top", ids, null);
					}
				});
				return true;
			}
			case R.id.action_sel_move_up: {
				sessionInfo.executeRpc(new RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						rpc.simpleRpcCall("queue-move-up", ids, null);
					}
				});
				return true;
			}
			case R.id.action_sel_move_down: {
				sessionInfo.executeRpc(new RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						rpc.simpleRpcCall("queue-move-down", ids, null);
					}
				});
				return true;
			}
			case R.id.action_sel_move_bottom: {
				sessionInfo.executeRpc(new RpcExecuter() {
					@Override
					public void executeRpc(TransmissionRPC rpc) {
						rpc.simpleRpcCall("queue-move-bottom", ids, null);
					}
				});
				return true;
			}
		}
		return false;
	}

	private void setupActionModeCallback() {
		mActionModeCallback = new Callback() {

			// Called when the action mode is created; startActionMode() was called
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "onCreateActionMode");
				}

				// Inflate a menu resource providing context menu items
				MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.menu_context_torrent_details, menu);

				TorrentDetailsFragment frag = (TorrentDetailsFragment) getActivity().getSupportFragmentManager().findFragmentById(
						R.id.frag_torrent_details);
				if (frag != null) {
					frag.onCreateActionMode(mode, menu);
				}

				SubMenu subMenu = menu.addSubMenu("Global Actions");
				subMenu.setIcon(R.drawable.ic_menu_more);
				MenuItemCompat.setShowAsAction(subMenu.getItem(),
						MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
				getActivity().onCreateOptionsMenu(subMenu);

				return true;
			}

			// Called each time the action mode is shown. Always called after onCreateActionMode, but
			// may be called multiple times if the mode is invalidated.
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "onPrepareActionMode");
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
				if (TorrentListFragment.this.handleFragmentMenuItems(item.getItemId())) {
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
				mActionMode = null;

				if (!actionModeBeingReplaced) {
					listview.clearChoices();
					updateCheckedIDs();
					// Not sure why ListView doesn't invalidate by default
					adapter.notifyDataSetInvalidated();
				}
			}
		};
	}

	protected void prepareContextMenu(Menu menu) {
		MenuItem menuMove = menu.findItem(R.id.action_sel_move);
		int itemCount = AndroidUtils.getCheckedItemCount(listview);
		menuMove.setEnabled(itemCount > 0);

		Map<?, ?>[] checkedTorrentMaps = getCheckedTorrentMaps(listview);
		boolean canStart = false;
		boolean canStop = false;
		for (Map<?, ?> mapTorrent : checkedTorrentMaps) {
			int status = MapUtils.getMapInt(mapTorrent,
					TransmissionVars.FIELD_TORRENT_STATUS,
					TransmissionVars.TR_STATUS_STOPPED);
			canStart |= status == TransmissionVars.TR_STATUS_STOPPED;
			canStop |= status != TransmissionVars.TR_STATUS_STOPPED;
		}
		MenuItem menuStart = menu.findItem(R.id.action_sel_start);
		menuStart.setVisible(canStart);

		MenuItem menuStop = menu.findItem(R.id.action_sel_stop);
		menuStop.setVisible(canStop);
	}

	private boolean showContextualActions(boolean forceRebuild) {
		if (mActionMode != null && !forceRebuild) {
			if (AndroidUtils.DEBUG_MENU) {
				Log.d(TAG, "showContextualActions: invalidate existing");
			}
			mActionMode.invalidate();
			return false;
		}

		// Start the CAB using the ActionMode.Callback defined above
		FragmentActivity activity = getActivity();
		if (activity instanceof ActionBarActivity) {
			if (AndroidUtils.DEBUG_MENU) {
				Log.d(TAG, "showContextualActions: startAB");
			}
			ActionBarActivity abActivity = (ActionBarActivity) activity;

			actionModeBeingReplaced = true;
			ActionMode am = abActivity.startSupportActionMode(mActionModeCallback);
			actionModeBeingReplaced = false;
			mActionMode = new ActionModeWrapper(am);
			mActionMode.setSubtitle(R.string.multi_select_tip);
			mActionMode.setTitle(R.string.context_torrent_title);
		}

		return true;
	}

	/*
	private void refreshRow(Map<?, ?> mapTorrent) {
		int position = adapter.getPosition(mapTorrent);
		if (position < 0) {
			return;
		}
		View view = listview.getChildAt(position);
		if (view == null) {
			return;
		}
		adapter.refreshView(position, view, listview);
	}
	*/

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentFilterBy.FilterByDialogListener#filterBy(long, java.lang.String, boolean)
	 */
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
					tvFilteringBy.setText(name);
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
	public void setActionModeBeingReplaced(boolean actionModeBeingReplaced) {
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
		if (rebuildActionMode) {
			rebuildActionMode = false;

			// Restore Selection
			long[] oldcheckedIDs = new long[checkedIDs.length];
			System.arraycopy(checkedIDs, 0, oldcheckedIDs, 0, oldcheckedIDs.length);
			int count = listview.getCount();
			listview.clearChoices();
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
		}
	}

	public void clearSelection() {
		finishActionMode();
	}

	private class InternalOlderListener
		implements android.support.v7.view.ActionMode.Callback
	{

		@Override
		public boolean onCreateActionMode(android.support.v7.view.ActionMode mode,
				Menu menu) {
			mActionMode = new ActionModeWrapper(mode);
			return multiChoiceModeListener.onCreateActionMode(mActionMode, menu);
		}

		@Override
		public boolean onPrepareActionMode(android.support.v7.view.ActionMode mode,
				Menu menu) {
			return multiChoiceModeListener.onPrepareActionMode(mActionMode, menu);
		}

		@Override
		public boolean onActionItemClicked(android.support.v7.view.ActionMode mode,
				MenuItem item) {
			return multiChoiceModeListener.onActionItemClicked(mActionMode, item);
		}

		@Override
		public void onDestroyActionMode(android.support.v7.view.ActionMode mode) {
			multiChoiceModeListener.onDestroyActionMode(mActionMode);
			listview.setLongClickable(true);
			if (!actionModeBeingReplaced) {
				listview.clearChoices();
			}
			listview.requestLayout();
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentMoveData.MoveDataDialogListener#moveDataTo(long, java.lang.String)
	 */
	@Override
	public void moveDataTo(final long id, final String s) {
		if (sessionInfo == null) {
			return;
		}
		sessionInfo.executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.moveTorrent(id, s, null);
			}
		});

		VuzeEasyTracker.getInstance(this).send(
				MapBuilder.createEvent("RemoteAction", "MoveData", null, null).build());
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentMoveData.MoveDataDialogListener#moveDataHistoryChanged(java.util.ArrayList)
	 */
	@Override
	public void moveDataHistoryChanged(ArrayList<String> history) {
		if (sessionInfo == null) {
			return;
		}
		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		if (remoteProfile == null) {
			return;
		}
		remoteProfile.setSavePathHistory(history);
		sessionInfo.saveProfile();
	}

	private void updateCheckedIDs() {
		checkedIDs = getCheckedIDs(listview);
		if (mCallback != null) {
			int choiceMode = listview.getChoiceMode();
			mCallback.onTorrentSelectedListener(TorrentListFragment.this,
					checkedIDs, choiceMode != ListView.CHOICE_MODE_SINGLE);
		}
		if (checkedIDs.length == 0 && mActionMode != null) {
			mActionMode.finish();
		}
	}

	@Override
	public void rebuildActionMode() {
		showContextualActions(true);
	}

	public void startStopTorrents() {
		Map<?, ?>[] checkedTorrentMaps = getCheckedTorrentMaps(listview);
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
					long[] ids = getCheckedIDs(listview);
					rpc.stopTorrents(TAG, ids, null);
				}
			});
		} else {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					long[] ids = getCheckedIDs(listview);
					rpc.startTorrents(TAG, ids, false, null);
				}
			});
		}
	}

}
