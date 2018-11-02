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

import java.io.File;
import java.net.URLDecoder;
import java.util.*;

import com.biglybt.android.adapter.FlexibleRecyclerSelectionListener;
import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.ImageViewer;
import com.biglybt.android.client.activity.TorrentOpenOptionsActivity;
import com.biglybt.android.client.activity.VideoViewer;
import com.biglybt.android.client.adapter.*;
import com.biglybt.android.client.dialog.DialogFragmentSizeRange;
import com.biglybt.android.client.rpc.ReplyMapReceivedListener;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.sidelist.SideListActivity;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.widget.CustomToast;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.android.widget.SwipeRefreshLayoutExtra;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.*;
import android.content.res.Resources;
import android.net.Uri;
import android.os.*;
import android.support.annotation.MenuRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.*;
import android.webkit.MimeTypeMap;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Shows the list of files with a torrent
 */
public class FilesFragment
	extends TorrentDetailPage
	implements ActionModeBeingReplacedListener, View.OnKeyListener,
	DialogFragmentSizeRange.SizeRangeDialogListener,
	SwipeRefreshLayoutExtra.OnExtraViewVisibilityChangeListener
{
	@Thunk
	static final String TAG = "FilesFragment";

	/**
	 * Launching an Intent without a Mime will result in a different list
	 * of apps then one including the Mime type.  Sometimes one is better than
	 * the other, especially with URLs :(
	 * <p/>
	 * Pros for setting MIME:
	 * - In theory should provide more apps
	 * <p/>
	 * Cons for setting MIME:
	 * - the Web browser will not show as an app, but rather a
	 * html viewer app, if you are lucky
	 * - A lot of apps that accept MIME types can't handle URLs and fail
	 */
	private static final boolean tryLaunchWithMimeFirst = false;

	private final RecyclerView.OnScrollListener onScrollListener;

	@Thunk
	RecyclerView listview;

	@Thunk
	FilesTreeAdapter adapter;

	private Callback mActionModeCallback;

	@Thunk
	ActionMode mActionMode;

	ReplyMapReceivedListener hideProgressOnRpcReceive = new ReplyMapReceivedListener() {
		@Override
		public void rpcSuccess(String id, Map<?, ?> optionalMap) {
			hideProgressBar();
		}

		@Override
		public void rpcError(String id, Exception e) {
			hideProgressBar();
		}

		@Override
		public void rpcFailure(String id, String message) {
			hideProgressBar();
		}
	};

	@Thunk
	ActionModeBeingReplacedListener parentActionModeListener;

	@Thunk
	long lastUpdated = 0;

	@Thunk
	TextView tvScrollTitle;

	private TextView tvSummary;

	@Thunk
	CompoundButton btnEditMode;

	@Thunk
	Handler pullRefreshHandler;

	private MenuBuilder actionmenuBuilder;

	private TextView tvFilterSizeCurrent;

	private TextView tvFilterCurrent;

	private CompoundButton btnShowOnlyWanted;

	private CompoundButton btnShowOnlyComplete;

	private boolean isTorrentOpenOptions;

	private SwipeRefreshLayoutExtra swipeRefresh;

	public FilesFragment() {
		super();
		onScrollListener = new RecyclerView.OnScrollListener() {
			int firstVisibleItem = 0;

			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx,
					int dy) {
				super.onScrolled(recyclerView, dx, dy);
				if (tvScrollTitle == null) {
					return;
				}
				LinearLayoutManager lm = (LinearLayoutManager) listview.getLayoutManager();
				if (lm == null) {
					return;
				}
				int firstVisibleItem = lm.findFirstCompletelyVisibleItemPosition();
				if (firstVisibleItem == this.firstVisibleItem) {
					return;
				}
				this.firstVisibleItem = firstVisibleItem;
				FilesAdapterItem item = adapter.getItem(firstVisibleItem);

				if (item == null) {
					return;
				}

				tvScrollTitle.setText(item.parent != null ? item.parent.folder : "");
			}
		};

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (AndroidUtils.DEBUG) {
			log(TAG, "onCreate " + this);
		}
		super.onCreate(savedInstanceState);

		setupActionModeCallback();
	}

	@Nullable
	@Override
	public View onCreateViewWithSession(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

		FragmentActivity activity = requireActivity();

		isTorrentOpenOptions = activity instanceof TorrentOpenOptionsActivity;

		Map<?, ?> torrent = null;
		View view;
		if (isTorrentOpenOptions) {
			torrentID = TorrentUtils.getTorrentID(activity);
			if (torrentID < 0) {
				return null;
			}

			Session session = getSession();
			torrent = session.torrent.getCachedTorrent(torrentID);
			if (torrent == null) {
				// In theory TorrentOpenOptionsActivity handled this NPE already
				return null;
			}

			view = inflater.inflate(AndroidUtils.isTV(getContext())
					? R.layout.openoptions_files_tv : R.layout.openoptions_files,
					container, false);
		} else {
			view = inflater.inflate(R.layout.frag_torrent_files, container, false);
		}

		tvScrollTitle = view.findViewById(R.id.files_scrolltitle);
		tvSummary = view.findViewById(R.id.files_summary);

		swipeRefresh = view.findViewById(R.id.swipe_container);
		if (swipeRefresh != null) {
			swipeRefresh.setExtraLayout(R.layout.swipe_layout_extra);
			swipeRefresh.setOnRefreshListener(this::triggerRefresh);
			swipeRefresh.setOnExtraViewVisibilityChange(this);
		}

		FlexibleRecyclerSelectionListener<FilesTreeAdapter, FilesTreeViewHolder, FilesAdapterItem> rs = new FilesRecyclerSelectionListener();

		adapter = new FilesTreeAdapter(getLifecycle(), this, rs);
		adapter.setInEditMode(isTorrentOpenOptions);
		adapter.setMultiCheckModeAllowed(false);
		adapter.setCheckOnSelectedAfterMS(100);
		adapter.addOnSetItemsCompleteListener(this::onSetItemsComplete);
		if (torrent != null) {
			if (torrent.containsKey(TransmissionVars.FIELD_TORRENT_FILES)) {
				adapter.setTorrentID(torrentID, false);
			} else {
				session.torrent.getFileInfo(TAG, torrentID, null,
						(callID, addedTorrentMaps, fileIndexes,
								removedTorrentIDs) -> AndroidUtilsUI.runOnUIThread(
										FilesFragment.this, false,
										_activity -> adapter.setTorrentID(torrentID, false)));
			}
		}

		listview = view.findViewById(R.id.files_list);
		PreCachingLayoutManager layoutManager = new PreCachingLayoutManager(
				getContext());
		listview.setLayoutManager(layoutManager);
		listview.setAdapter(adapter);

		if (AndroidUtils.isTV(getContext())) {
			if (listview instanceof FastScrollRecyclerView) {
				((FastScrollRecyclerView) listview).setEnableFastScrolling(false);
			}
			layoutManager.setFixedVerticalHeight(AndroidUtilsUI.dpToPx(48));
			listview.setVerticalFadingEdgeEnabled(true);
			listview.setFadingEdgeLength(AndroidUtilsUI.dpToPx((int) (48 * 1.5)));
		}

		listview.setOnKeyListener((v, keyCode, event) -> {
			if (event.getAction() != KeyEvent.ACTION_DOWN) {
				return false;
			}
			switch (keyCode) {
				case KeyEvent.KEYCODE_MEDIA_PLAY:
				case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: {
					launchOrStreamFile();
					return true;
				}
			}

			return false;
		});

		return view;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		FragmentActivity activity = requireActivity();

		if (activity instanceof ActionModeBeingReplacedListener) {
			parentActionModeListener = (ActionModeBeingReplacedListener) activity;
		}

		btnEditMode = activity.findViewById(R.id.files_editmode);
		if (btnEditMode != null) {
			btnEditMode.setOnClickListener(v -> {
				if (adapter == null) {
					return;
				}
				adapter.setInEditMode(btnEditMode.isChecked());
			});
		}

		btnShowOnlyWanted = activity.findViewById(R.id.files_showonlywanted);
		if (btnShowOnlyWanted != null) {
			btnShowOnlyWanted.setOnClickListener(v -> {
				if (adapter == null) {
					return;
				}
				FilesTreeFilter filter = adapter.getFilter();
				filter.setShowOnlyWanted(btnShowOnlyWanted.isChecked());
				filter.refilter();
				updateFilterTexts();
			});
		}

		btnShowOnlyComplete = activity.findViewById(R.id.files_showonlycomplete);
		if (btnShowOnlyComplete != null) {
			btnShowOnlyComplete.setOnClickListener(v -> {
				if (adapter == null) {
					return;
				}
				FilesTreeFilter filter = adapter.getFilter();
				filter.setShowOnlyComplete(btnShowOnlyComplete.isChecked());
				filter.refilter();
				updateFilterTexts();
			});
		}

		View viewFileSizeRow = activity.findViewById(R.id.sidefilter_filesize);
		if (viewFileSizeRow != null) {
			viewFileSizeRow.setOnKeyListener(
					(v, keyCode, event) -> handleFileSizeRowKeyListener(keyCode, event));
			viewFileSizeRow.setOnClickListener(v -> fileSizeRow_clicked());
		}

		View viewClear = activity.findViewById(R.id.sidefilter_clear);
		if (viewClear != null) {
			viewClear.setOnClickListener(v -> clearFilters_clicked());
		}
	}

	@Override
	public void onSideListHelperVisibleSetup(View view) {
		super.onSideListHelperVisibleSetup(view);
		tvFilterSizeCurrent = view.findViewById(R.id.rcm_filter_size_current);
		tvFilterCurrent = view.findViewById(R.id.sidefilter_current);

		updateFilterTexts();
	}

	public void fileSizeRow_clicked() {
		if (adapter == null) {
			return;
		}
		FilesTreeFilter filter = adapter.getFilter();
		long[] sizeRange = filter.getFilterSizes();

		DialogFragmentSizeRange.openDialog(getFragmentManager(), this, TAG,
				remoteProfileID, filter.getMaxSize(), sizeRange[0], sizeRange[1]);
	}

	@Override
	public void onSizeRangeChanged(String callbackID, long start, long end) {
		if (adapter == null) {
			return;
		}
		FilesTreeFilter filter = adapter.getFilter();
		filter.setFilterSizes(start, end);
		filter.refilter();
		updateFilterTexts();
	}

	@Thunk
	boolean handleFileSizeRowKeyListener(int keyCode, KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}
		if (adapter == null) {
			return false;
		}

		if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP
				|| keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
			FilesTreeFilter adapterFilter = adapter.getFilter();
			long[] filter = adapterFilter.getFilterSizes();

			if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
				filter[0] += 1024 * 1024L * 100; // 100M
			}
			if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
				filter[0] -= 1024 * 1024L * 100; // 100M
				if (filter[0] < 0) {
					filter[0] = 0;
				}
			}

			adapterFilter.setFilterSizes(filter[0], filter[1]);
			adapterFilter.refilter();
			updateFilterTexts();
			return true;
		}
		return false;
	}

	public void clearFilters_clicked() {

		FilesTreeFilter filter = adapter.getFilter();

		filter.clearFilter();
		filter.refilter();
		updateFilterTexts();
	}

	@Thunk
	void updateFilterTexts() {
		if (!AndroidUtilsUI.isUIThread()) {
			requireActivity().runOnUiThread(this::updateFilterTexts);
			return;
		}

		if (adapter == null) {
			return;
		}

		String sCombined = "";

		Resources resources = getResources();

		String filterSizeText;

		FilesTreeFilter filter = adapter.getFilter();
		long[] sizeRange = filter.getFilterSizes();
		if (sizeRange[0] <= 0 && sizeRange[1] <= 0) {
			filterSizeText = resources.getString(R.string.filter_size_none);
		} else {
			if (sizeRange[1] > 0 && sizeRange[0] > 0) {
				filterSizeText = resources.getString(R.string.filter_size,
						DisplayFormatters.formatByteCountToKiBEtc(sizeRange[0], true),
						DisplayFormatters.formatByteCountToKiBEtc(sizeRange[1], true));
			} else if (sizeRange[1] > 0) {
				filterSizeText = resources.getString(R.string.filter_size_upto,
						DisplayFormatters.formatByteCountToKiBEtc(sizeRange[1], true));
			} else {
				filterSizeText = resources.getString(R.string.filter_size_atleast,
						DisplayFormatters.formatByteCountToKiBEtc(sizeRange[0], true));
			}
			if (sCombined.length() > 0) {
				sCombined += "\n";
			}
			sCombined += filterSizeText;
		}

		if (filter.isShowOnlyComplete()) {
			if (sCombined.length() > 0) {
				sCombined += "\n";
			}
			sCombined += getString(R.string.filter_only_complete);
		}

		if (filter.isShowOnlyWanted()) {
			if (sCombined.length() > 0) {
				sCombined += "\n";
			}
			sCombined += getString(R.string.filter_only_wanted);
		}

		if (tvFilterSizeCurrent != null) {
			tvFilterSizeCurrent.setText(filterSizeText);
		}

		if (tvFilterCurrent != null) {
			tvFilterCurrent.setText(sCombined);
		}

	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		if (adapter != null) {
			adapter.onSaveInstanceState(outState);
		}
		outState.putLong("torrentID", torrentID);
	}

	@Override
	public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
		super.onViewStateRestored(savedInstanceState);
		if (adapter != null) {
			adapter.onRestoreInstanceState(savedInstanceState, listview);
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

				if (adapter.getSelectedPosition() < 0) {
					return false;
				}

				FragmentActivity activity = getActivity();
				if (activity == null || activity.isFinishing()) {
					return false;
				}

				FilesAdapterItem item = adapter.getSelectedItem();
				if (item == null) {
					return false;
				}
				@MenuRes
				int menuRes = (item instanceof FilesAdapterItemFile)
						? R.menu.menu_context_torrent_files
						: R.menu.menu_context_torrent_folder;

				activity.getMenuInflater().inflate(menuRes, menu);

				if (mode == null) {
					onPrepareOptionsMenu(menu);
				}

				return true;
			}

			// Called each time the action mode is shown. Always called after
			// onCreateActionMode, but
			// may be called multiple times if the mode is invalidated.
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				if (AndroidUtils.DEBUG_MENU) {
					log(TAG, "onPrepareActionMode");
				}

				return prepareContextMenu(menu);
			}

			// Called when the user selects a contextual menu item
			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				return handleMenu(item);
			}

			// Called when the user exits the action mode
			@Override
			public void onDestroyActionMode(ActionMode mode) {
				destroyActionMode();
			}
		};
	}

	@Thunk
	void destroyActionMode() {
		if (AndroidUtils.DEBUG_MENU) {
			log(TAG, "destroyActionMode");
		}
		if (mActionMode == null) {
			return;
		}
		mActionMode = null;

		adapter.clearChecked();

		// delay so actionmode finishes up
		listview.post(() -> {
			if (parentActionModeListener != null) {
				parentActionModeListener.actionModeBeingReplacedDone();
			}
		});
	}

	@Override
	@Thunk
	protected boolean prepareContextMenu(Menu menu) {
		super.prepareContextMenu(menu);

		if (torrentID < 0) {
			return false;
		}

		Resources resources = getResources();

		boolean isSelectedComplete = false;
		boolean canFlipToWant = false;
		boolean canFlipToUnwant = false;

		Map<?, ?> mapFile = getFocusedFile();
		FilesAdapterItem selectedItem = adapter.getSelectedItem();
		int filteredCount = adapter.getFilter().getFilteredFileCount();

		MenuItem menuFileGroup = menu.findItem(R.id.menu_group_context);
		boolean isGroupForMultipleFiles = menuFileGroup != null
				&& filteredCount > 1;
		boolean isGroupForOneFile = menuFileGroup != null && filteredCount == 1;
		boolean isGroupForAllFiles = menuFileGroup != null
				&& filteredCount == adapter.getFilter().getUnfilteredFileCount();
		boolean isGroupForFilteredFiles = isGroupForMultipleFiles
				&& !isGroupForAllFiles;
		boolean isSelectedFolder = selectedItem instanceof FilesAdapterItemFolder;
		boolean isSelectedEmpty = !isSelectedFolder
				&& (mapFile == null || mapFile.size() == 0);

		if (isGroupForMultipleFiles || isGroupForOneFile) {
			// could calculate based on files
			canFlipToUnwant = true;
			canFlipToWant = true;
		} else if (isSelectedFolder) {
			FilesAdapterItemFolder folder = (FilesAdapterItemFolder) selectedItem;
			canFlipToWant = folder.numFilesWanted < folder.getNumFiles();
			canFlipToUnwant = folder.numFilesWanted > 0;
		} else if (!isSelectedEmpty) {
			boolean wanted = MapUtils.getMapBoolean(mapFile,
					TransmissionVars.FIELD_FILESTATS_WANTED, true);
			canFlipToWant = !wanted;
			canFlipToUnwant = wanted;
		}

		if (mapFile != null) {
			long bytesCompleted = MapUtils.getMapLong(mapFile,
					TransmissionVars.FIELD_FILESTATS_BYTES_COMPLETED, 0);
			long length = MapUtils.getMapLong(mapFile,
					TransmissionVars.FIELD_FILES_LENGTH, -1);
			//System.out.println("mapFIle=" + mapFile);
			isSelectedComplete = bytesCompleted == length;
		}

		if (menuFileGroup != null) {
			menuFileGroup.setVisible(isGroupForMultipleFiles);
			if (isGroupForMultipleFiles) {
				String headerTitle;
				if (isGroupForAllFiles) {
					int numFiles = adapter.getFilter().getUnfilteredFileCount();
					headerTitle = getString(R.string.sideactions_all_file_header,
							DisplayFormatters.formatNumber(numFiles));
				} else {
					headerTitle = getString(R.string.sideactions_filtered_file_header,
							DisplayFormatters.formatNumber(filteredCount));
				}
				menuFileGroup.setTitle(headerTitle);
			}
		}

		if (isTorrentOpenOptions) {
			MenuItem menuTorrentGroup = menu.findItem(R.id.menu_group_torrent);
			if (menuTorrentGroup != null) {
				menuTorrentGroup.setVisible(false);
			}
		}

		boolean isLocalHost = session.getRemoteProfile().isLocalHost();
		boolean isOnlineOrLocal = BiglyBTApp.getNetworkState().isOnline()
				|| isLocalHost;

		MenuItem menuLaunch = menu.findItem(R.id.action_sel_launch);
		if (menuLaunch != null) {
			if (!isSelectedFolder && !isSelectedEmpty && isLocalHost
					&& isSelectedComplete) {
				menuLaunch.setVisible(true);
			} else {
				menuLaunch.setVisible(false);
			}
		}

		MenuItem menuLaunchStream = menu.findItem(R.id.action_sel_launch_stream);
		if (menuLaunchStream != null) {
			boolean canStream = !isSelectedFolder && !isSelectedEmpty
					&& isSelectedComplete
					&& mapFile.containsKey(TransmissionVars.FIELD_FILES_CONTENT_URL);
			canStream &= isOnlineOrLocal;
			menuLaunchStream.setVisible(canStream);
		}

		MenuItem menuSave = menu.findItem(R.id.action_sel_save);
		if (menuSave != null) {
			boolean canSave = !isLocalHost && !isSelectedFolder && !isSelectedEmpty
					&& isOnlineOrLocal && isSelectedComplete
					&& mapFile.containsKey(TransmissionVars.FIELD_FILES_CONTENT_URL);
			menuSave.setVisible(canSave);
		}

		// TODO: We could handle folders
		boolean canSetPriority = (isGroupForFilteredFiles && !isGroupForAllFiles
				&& isOnlineOrLocal)
				|| !isSelectedFolder && !isSelectedEmpty && isOnlineOrLocal
						&& !isSelectedComplete;

		int priority = MapUtils.getMapInt(mapFile,
				TransmissionVars.FIELD_TORRENT_FILES_PRIORITY,
				TransmissionVars.TR_PRI_NORMAL);
		MenuItem menuPriorityUp = menu.findItem(R.id.action_sel_priority_up);
		if (menuPriorityUp != null) {
			menuPriorityUp.setVisible(
					canSetPriority && priority < TransmissionVars.TR_PRI_HIGH);
		}
		MenuItem menuPriorityDown = menu.findItem(R.id.action_sel_priority_down);
		if (menuPriorityDown != null) {
			menuPriorityDown.setVisible(
					canSetPriority && priority > TransmissionVars.TR_PRI_LOW);
		}

		MenuItem menuAllPriorityUp = menu.findItem(
				R.id.action_filtered_priority_up);
		if (menuAllPriorityUp != null) {
			menuAllPriorityUp.setVisible(canSetPriority && isGroupForFilteredFiles);
		}
		MenuItem menuAllPriorityDown = menu.findItem(
				R.id.action_filtered_priority_down);
		if (menuAllPriorityDown != null) {
			menuAllPriorityDown.setVisible(canSetPriority && isGroupForFilteredFiles);
		}

		///

		MenuItem menuUnwantSelected = menu.findItem(R.id.action_sel_file_unwanted);
		if (menuUnwantSelected != null) {
			menuUnwantSelected.setVisible(canFlipToUnwant && isOnlineOrLocal);
		}
		MenuItem menuWantSelected = menu.findItem(R.id.action_sel_file_wanted);
		if (menuWantSelected != null) {
			menuWantSelected.setVisible(canFlipToWant && isOnlineOrLocal);
		}

		MenuItem menuUnwantFiltered = menu.findItem(
				R.id.action_filtered_files_unwanted);
		if (menuUnwantFiltered != null) {
			menuUnwantFiltered.setVisible(canFlipToUnwant && isOnlineOrLocal);
		}
		MenuItem menuWantFiltered = menu.findItem(
				R.id.action_filtered_files_wanted);
		if (menuWantFiltered != null) {
			menuWantFiltered.setVisible(canFlipToWant && isOnlineOrLocal);
		}

		MenuItem menuUnwantFilteredFolder = menu.findItem(
				R.id.action_sel_folder_filtered_unwanted);
		if (menuUnwantFilteredFolder != null) {
			boolean visible = isSelectedFolder && canFlipToUnwant && isOnlineOrLocal;
			if (visible) {
				FilesAdapterItemFolder folder = (FilesAdapterItemFolder) selectedItem;
				int numFilteredFiles = folder.getNumFilteredFiles();
				if (numFilteredFiles == folder.getNumFiles()
						|| folder.numFilesFilteredWanted == 0) {
					visible = false;
				} else {
					menuUnwantFilteredFolder.setTitle(resources.getQuantityString(
							R.plurals.action_sel_folder_filtered_unwanted, numFilteredFiles,
							DisplayFormatters.formatNumber(numFilteredFiles)));
				}
			}
			menuUnwantFilteredFolder.setVisible(visible);
		}
		MenuItem menuWantFilteredFolder = menu.findItem(
				R.id.action_sel_folder_filtered_wanted);
		if (menuWantFilteredFolder != null) {
			boolean visible = isSelectedFolder && canFlipToWant && isOnlineOrLocal;
			if (visible) {
				FilesAdapterItemFolder folder = (FilesAdapterItemFolder) selectedItem;
				int numFilteredFiles = folder.getNumFilteredFiles();
				if (numFilteredFiles == folder.getNumFiles()
						|| numFilteredFiles == folder.numFilesFilteredWanted) {
					visible = false;
				} else {
					menuWantFilteredFolder.setTitle(resources.getQuantityString(
							R.plurals.action_sel_folder_filtered_wanted, numFilteredFiles,
							DisplayFormatters.formatNumber(numFilteredFiles)));
				}
			}
			menuWantFilteredFolder.setVisible(visible);
		}

		MenuItem menuUnwantAllFolder = menu.findItem(
				R.id.action_sel_folder_all_unwanted);
		if (menuUnwantAllFolder != null) {
			boolean visible = isSelectedFolder && canFlipToUnwant && isOnlineOrLocal;
			if (visible) {
				FilesAdapterItemFolder folder = (FilesAdapterItemFolder) selectedItem;
				if (folder.numFilesWanted == 0) {
					visible = false;
				} else {
					int numFiles = folder.getNumFiles();
					String s = resources.getQuantityString(
							R.plurals.action_sel_folder_all_unwanted, numFiles,
							DisplayFormatters.formatNumber(numFiles));
					menuUnwantAllFolder.setTitle(s);
				}
			}
			menuUnwantAllFolder.setVisible(visible);
		}
		MenuItem menuWantAllFolder = menu.findItem(
				R.id.action_sel_folder_all_wanted);
		if (menuWantAllFolder != null) {
			boolean visible = isSelectedFolder && canFlipToWant && isOnlineOrLocal;
			if (visible) {
				FilesAdapterItemFolder folder = (FilesAdapterItemFolder) selectedItem;
				int numFiles = folder.getNumFiles();
				if (folder.numFilesWanted == numFiles) {
					visible = false;
				} else {
					String s = resources.getQuantityString(
							R.plurals.action_sel_folder_all_wanted, numFiles,
							DisplayFormatters.formatNumber(numFiles));
					menuWantAllFolder.setTitle(s);
				}
			}
			menuWantAllFolder.setVisible(visible);
		}

		///

		MenuItem menuExpand = menu.findItem(R.id.action_expand);
		if (menuExpand != null) {
			menuExpand.setVisible(AndroidUtils.isTV(getContext())
					&& adapter.getSelectedItem() instanceof FilesAdapterItemFolder);
		}

		AndroidUtils.fixupMenuAlpha(menu);
		return true;
	}

	@Override
	@Thunk
	protected boolean handleMenu(MenuItem menuItem) {
		if (super.handleMenu(menuItem)) {
			return true;
		}

		if (torrentID < 0) {
			return false;
		}
		int itemId = menuItem.getItemId();
		switch (itemId) {
			case R.id.action_sel_launch: {
				Map<?, ?> selectedFile = getFocusedFile();
				return selectedFile != null && launchLocalFile(selectedFile);
			}
			case R.id.action_sel_launch_stream: {
				Map<?, ?> selectedFile = getFocusedFile();
				return selectedFile != null && streamFile(selectedFile);
			}
			case R.id.action_sel_save: {
				Map<?, ?> selectedFile = getFocusedFile();
				return saveFile(selectedFile);
			}
			case R.id.action_sel_file_wanted: {
				showProgressBar();
				FilesAdapterItem selectedItem = adapter.getSelectedItem();
				if (selectedItem instanceof FilesAdapterItemFile) {
					adapter.setWantState(true, hideProgressOnRpcReceive,
							(FilesAdapterItemFile) selectedItem);
				}
				return true;
			}
			case R.id.action_filtered_files_wanted: {
				showProgressBar();
				FilesAdapterItemFile[] items = adapter.getFilteredFileItems();
				adapter.setWantState(true, hideProgressOnRpcReceive, items);
				return true;
			}
			case R.id.action_sel_folder_filtered_wanted:
			case R.id.action_sel_folder_all_wanted: {
				showProgressBar();
				FilesAdapterItem selectedItem = adapter.getSelectedItem();
				if (selectedItem instanceof FilesAdapterItemFolder) {
					boolean filtered = itemId == R.id.action_sel_folder_filtered_wanted;
					adapter.setWantState(true, filtered, hideProgressOnRpcReceive,
							(FilesAdapterItemFolder) selectedItem);
				}
				return true;
			}
			case R.id.action_sel_file_unwanted: {
				// TODO: Delete Prompt
				showProgressBar();
				FilesAdapterItem selectedItem = adapter.getSelectedItem();
				if (selectedItem instanceof FilesAdapterItemFile) {
					adapter.setWantState(false, hideProgressOnRpcReceive,
							(FilesAdapterItemFile) selectedItem);
				}
				return true;
			}
			case R.id.action_filtered_files_unwanted: {
				// TODO: Delete Prompt
				showProgressBar();
				FilesAdapterItemFile[] items = adapter.getFilteredFileItems();
				adapter.setWantState(false, hideProgressOnRpcReceive, items);
				return true;
			}
			case R.id.action_sel_folder_filtered_unwanted:
			case R.id.action_sel_folder_all_unwanted: {
				showProgressBar();
				FilesAdapterItem selectedItem = adapter.getSelectedItem();
				if (selectedItem instanceof FilesAdapterItemFolder) {
					boolean filtered = itemId == R.id.action_sel_folder_filtered_unwanted;
					adapter.setWantState(false, filtered, hideProgressOnRpcReceive,
							(FilesAdapterItemFolder) selectedItem);
				}
				return true;
			}
			case R.id.action_filtered_priority_up:
			case R.id.action_sel_priority_up: {
				int[] fileIndexes;
				int priority;

				boolean isAll = itemId == R.id.action_filtered_priority_up;

				if (isAll) {
					fileIndexes = adapter.getFilteredFileIndexes();
					priority = TransmissionVars.TR_PRI_HIGH;
				} else {
					fileIndexes = new int[] {
						getFocusedFileIndex()
					};

					Map<?, ?> selectedFile = getFocusedFile();

					priority = MapUtils.getMapInt(selectedFile,
							TransmissionVars.FIELD_TORRENT_FILES_PRIORITY,
							TransmissionVars.TR_PRI_NORMAL);
					if (priority >= TransmissionVars.TR_PRI_HIGH) {
						return true;
					} else {
						priority += 1;
					}
				}

				showProgressBar();
				final int fpriority = priority;
				session.executeRpc(rpc -> rpc.setFilePriority(TAG, torrentID,
						fileIndexes, fpriority, hideProgressOnRpcReceive));
				return true;
			}
			case R.id.action_filtered_priority_down:
			case R.id.action_sel_priority_down: {
				int[] fileIndexes;
				int priority;

				boolean isAll = itemId == R.id.action_filtered_priority_down;

				if (isAll) {
					fileIndexes = adapter.getFilteredFileIndexes();
					priority = TransmissionVars.TR_PRI_LOW;
				} else {
					fileIndexes = new int[] {
						getFocusedFileIndex()
					};

					Map<?, ?> selectedFile = getFocusedFile();
					priority = MapUtils.getMapInt(selectedFile,
							TransmissionVars.FIELD_TORRENT_FILES_PRIORITY,
							TransmissionVars.TR_PRI_NORMAL);
					if (priority <= TransmissionVars.TR_PRI_LOW) {
						return true;
					} else {
						priority -= 1;
					}
				}
				showProgressBar();
				int fPriority = priority;
				session.executeRpc(rpc -> rpc.setFilePriority(TAG, torrentID,
						fileIndexes, fPriority, hideProgressOnRpcReceive));
				return true;
			}
			case R.id.action_expand: {
				FilesAdapterItemFolder folder = (FilesAdapterItemFolder) adapter.getSelectedItem();
				adapter.setExpandState(folder, !folder.expand);
				return true;
			}
		}
		return false;
	}

	@Thunk
	boolean saveFile(Map<?, ?> selectedFile) {
		if (selectedFile == null) {
			return false;
		}
		if (session.getRemoteProfile().isLocalHost()) {
			return false;
		}
		final String contentURL = getContentURL(selectedFile);
		if (contentURL == null || contentURL.length() == 0) {
			return false;
		}

		final File directory = AndroidUtils.getDownloadDir();
		// name in map may contain relative directory
		String name = AndroidUtils.getFileName(MapUtils.getMapString(selectedFile,
				TransmissionVars.FIELD_FILES_NAME, "foo.txt"));
		final File outFile = new File(directory, name);

		if (BiglyBTApp.getNetworkState().isOnlineMobile()) {
			Context context = getContext();
			if (context != null) {
				String message = getString(R.string.on_mobile, getString(
						R.string.save_content, TextUtils.htmlEncode(outFile.getName())));
				AlertDialog.Builder builder = new AlertDialog.Builder(
						context).setMessage(
								AndroidUtils.fromHTML(message)).setPositiveButton(
										android.R.string.yes,
										(dialog, which) -> saveFile(contentURL,
												outFile)).setNegativeButton(android.R.string.no, null);
				builder.show();
				return true;
			}
		}

		saveFile(contentURL, outFile);

		return true;
	}

	private String getContentURL(Map<?, ?> selectedFile) {
		long contentPort = session.getContentPort();
		if (contentPort > 0) {
			Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
			if (torrent == null) {
				return null;
			}

			String hash = MapUtils.getMapString(torrent,
					TransmissionVars.FIELD_TORRENT_HASH_STRING, null);
			if (hash == null) {
				return null;
			}

			int fileIndex = MapUtils.getMapInt(selectedFile,
					TransmissionVars.FIELD_FILES_INDEX, 0);
			String fileName = MapUtils.getMapString(selectedFile,
					TransmissionVars.FIELD_FILES_NAME, null);
			if (fileName == null) {
				return null;
			}

			String name = hash + "-" + fileIndex
					+ AndroidUtils.getFileExtension(fileName);
			return session.getBaseURL() + ":" + contentPort + "/Content/" + name; //NON-NLS
		}

		String contentURL = MapUtils.getMapString(selectedFile,
				TransmissionVars.FIELD_FILES_CONTENT_URL, null);
		if (contentURL == null || contentURL.length() == 0) {
			return contentURL;
		}
		if (contentURL.charAt(0) == ':' || contentURL.charAt(0) == '/') {
			contentURL = session.getBaseURL() + contentURL;
		}
		if (contentURL.contains("/localhost:")) { //NON-NLS
			return contentURL.replaceAll("/localhost:", //NON-NLS
					"/" + BiglyBTApp.getNetworkState().getActiveIpAddress() + ":");
		}

		return contentURL;
	}

	@Thunk
	void saveFile(final String contentURL, final File outFile) {
		requestPermissions(new String[] {
			Manifest.permission.WRITE_EXTERNAL_STORAGE
		}, () -> reallySaveFile(contentURL, outFile),
				() -> CustomToast.showText(R.string.content_saved_failed_perms_denied,
						Toast.LENGTH_LONG));
	}

	@Thunk
	void reallySaveFile(final String contentURL, final File outFile) {
		DownloadManager manager = (DownloadManager) BiglyBTApp.getContext().getSystemService(
				Context.DOWNLOAD_SERVICE);
		if (manager == null) {
			// TODO: Warn
			return;
		}
		DownloadManager.Request request = new DownloadManager.Request(
				Uri.parse(contentURL));
		request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
				outFile.getName());
		request.allowScanningByMediaScanner();
		request.setNotificationVisibility(
				DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

		Context context = BiglyBTApp.getContext();
		if (AndroidUtils.isTV(context)
				|| !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
			String s = getResources().getString(R.string.content_saving,
					TextUtils.htmlEncode(outFile.getName()),
					TextUtils.htmlEncode(outFile.getParent()));
			CustomToast.showText(AndroidUtils.fromHTML(s), Toast.LENGTH_SHORT);
		}

		manager.enqueue(request);
	}

	private boolean streamFile(final Map<?, ?> selectedFile) {
		FragmentActivity activity = getActivity();

		final String contentURL = getContentURL(selectedFile);
		if (BiglyBTApp.getNetworkState().isOnlineMobile() && activity != null) {
			String name = MapUtils.getMapString(selectedFile,
					TransmissionVars.FIELD_FILES_NAME, null);

			String message = getString(R.string.on_mobile,
					getString(R.string.stream_content, TextUtils.htmlEncode(name)));
			AlertDialog.Builder builder = new AlertDialog.Builder(
					activity).setMessage(
							AndroidUtils.fromHTML(message)).setPositiveButton(
									android.R.string.yes,
									(dialog, which) -> reallyStreamFile(selectedFile,
											contentURL)).setNegativeButton(android.R.string.no, null);
			builder.show();
		} else {
			return reallyStreamFile(selectedFile, contentURL);
		}
		return true;
	}

	private boolean launchLocalFile(Map<?, ?> selectedFile) {

		String fullPath = MapUtils.getMapString(selectedFile,
				TransmissionVars.FIELD_FILES_FULL_PATH, null);
		if (fullPath != null && fullPath.length() > 0) {
			File file = new File(fullPath);
			if (file.exists()) {
				Uri uri = Uri.fromFile(file);
				return reallyStreamFile(selectedFile, uri.toString());
			} else {
				if (AndroidUtils.DEBUG) {
					log(TAG, "Launch: File Not Found: " + fullPath);
				}
			}
		}

		return false;
	}

	@Thunk
	boolean reallyStreamFile(Map<?, ?> selectedFile, final String contentURL) {
		if (contentURL == null || contentURL.length() == 0) {
			return true;
		}
		Context context = getContext();

		Uri uri;
		if (contentURL.startsWith("file://") && context != null) {
			try {
				uri = FileProvider.getUriForFile(context, "com.biglybt.files",
						new File(URLDecoder.decode(contentURL.substring(7), "utf8")));
			} catch (Throwable e) {
				// For IllegalArgumentException, see
				// https://stackoverflow.com/questions/40318116/fileprovider-and-secondary-external-storage
				// and
				// https://issuetracker.google.com/issues/37125252
				e.printStackTrace();
				uri = Uri.parse(contentURL);
			}
		} else {
			uri = Uri.parse(contentURL);
		}

		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		String name = MapUtils.getMapString(selectedFile,
				TransmissionVars.FIELD_FILES_NAME, "video");
		intent.putExtra("title", name); //NON-NLS

		String extension = MimeTypeMap.getFileExtensionFromUrl(
				contentURL).toLowerCase(Locale.US);
		String mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
				extension);
		if (mimetype != null && tryLaunchWithMimeFirst) {
			intent.setType(mimetype);
		}
		Class<?> fallBackIntentClass = VideoViewer.class;
		if (mimetype != null && mimetype.startsWith("image")) {
			fallBackIntentClass = ImageViewer.class;
		}

		final PackageManager packageManager = context == null ? null
				: context.getPackageManager();
		if (context != null) {
			List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
					PackageManager.MATCH_DEFAULT_ONLY);
			if (AndroidUtils.DEBUG) {
				log(TAG, "num intents " + list.size());
				for (ResolveInfo info : list) {
					ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
					log(TAG, info.toString() + "/" + (componentInfo == null ? "null"
							: (componentInfo.name + "/" + componentInfo)));
				}
			}
			for (Iterator<ResolveInfo> it = list.iterator(); it.hasNext();) {
				ResolveInfo info = it.next();
				ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
				if (componentInfo != null
						&& "com.amazon.tv.legal.notices.BuellerTermsOfUseSettingsActivity".equals(
								componentInfo.name)) {
					it.remove();
				} else {
					String packageName = info.activityInfo.packageName;
					context.grantUriPermission(packageName, uri,
							Intent.FLAG_GRANT_READ_URI_PERMISSION);
				}
			}

			if (list.size() == 0) {
				// Intent will launch, but show message to the user:
				// "Opening web browser links is not supported"
				intent.setClass(context, fallBackIntentClass);
			}
			if (list.size() == 1) {
				ResolveInfo info = list.get(0);
				ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
				if ((componentInfo != null && componentInfo.name != null)
						&& ("com.amazon.unifiedshare.actionchooser.BuellerShareActivity".equals(
								componentInfo.name)
								|| componentInfo.name.startsWith(
										"com.google.android.tv.frameworkpackagestubs.Stubs"))) {
					intent.setClass(context, fallBackIntentClass);
				} else {
					ActivityInfo activity = info.activityInfo;
					ComponentName componentName = new ComponentName(
							activity.applicationInfo.packageName, activity.name);
					intent.setComponent(componentName);
					if (AndroidUtils.DEBUG) {
						log(TAG, "setting component to " + componentName);
					}
				}
			}
		}

		try {
			startActivity(intent);
			if (AndroidUtils.DEBUG) {
				log(TAG, "Started " + uri + " MIME: " + intent.getType());
			}
		} catch (java.lang.SecurityException es) {
			if (AndroidUtils.DEBUG) {
				log(TAG, "ERROR launching. " + es.toString());
			}

			if (mimetype != null) {
				try {
					Intent intent2 = new Intent(Intent.ACTION_VIEW, uri);
					intent2.putExtra("title", name); //NON-NLS
					if (!tryLaunchWithMimeFirst) {
						intent2.setType(mimetype);
					}

					if (packageManager != null) {
						List<ResolveInfo> list = packageManager.queryIntentActivities(
								intent2, PackageManager.MATCH_DEFAULT_ONLY);
						if (AndroidUtils.DEBUG) {
							log(TAG, "num intents " + list.size());
							for (ResolveInfo info : list) {
								ComponentInfo componentInfo = AndroidUtils.getComponentInfo(
										info);
								log(TAG, info.toString() + "/" + (componentInfo == null ? "null"
										: (componentInfo.name + "/" + componentInfo)));
							}
						}
					}

					startActivity(intent2);
					if (AndroidUtils.DEBUG) {
						log(TAG, "Started with" + (intent2.getType() == null ? " no" : " ")
								+ " mime: " + uri);
					}
					return true;
				} catch (Throwable ex2) {
					if (AndroidUtils.DEBUG) {
						log(TAG, "no intent for view. " + ex2.toString());
					}
				}
			}

			CustomToast.showText(R.string.intent_security_fail, Toast.LENGTH_LONG);
		} catch (android.content.ActivityNotFoundException ex) {
			if (AndroidUtils.DEBUG) {
				log(TAG, "no intent for view. " + ex.toString());
			}

			if (mimetype != null) {
				try {
					Intent intent2 = new Intent(Intent.ACTION_VIEW, uri);
					intent2.putExtra("title", name); //NON-NLS
					if (!tryLaunchWithMimeFirst) {
						intent2.setType(mimetype);
					}
					startActivity(intent2);
					if (AndroidUtils.DEBUG) {
						log(TAG, "Started (no mime set) " + uri);
					}
					return true;
				} catch (android.content.ActivityNotFoundException ex2) {
					if (AndroidUtils.DEBUG) {
						log(TAG, "no intent for view. " + ex2.toString());
					}
				}
			}

			CustomToast.showText(R.string.no_intent, Toast.LENGTH_SHORT);
		}
		return true;
	}

	@Thunk
	int getFocusedFileIndex() {
		Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
		if (torrent == null) {
			return -1;
		}
		List<?> listFiles = MapUtils.getMapList(torrent,
				TransmissionVars.FIELD_TORRENT_FILES, null);
		int selectedPosition = adapter.getSelectedPosition();
		long id = adapter.getItemId(selectedPosition);
		if (listFiles == null || id < 0 || id >= listFiles.size()) {
			return -1;
		}
		if (AndroidUtils.DEBUG) {
			log(TAG, "FocusedFile #" + id);
		}
		Object object = listFiles.get((int) id);
		if (object instanceof Map<?, ?>) {
			return (int) id;
		}
		return -1;
	}

	private Map<?, ?> getFocusedFile() {
		FilesAdapterItem selectedItem = adapter.getSelectedItem();
		if (selectedItem == null) {
			return null;
		}
		if (selectedItem instanceof FilesAdapterItemFolder) {
			return null;
		}

		return selectedItem.getMap(session, torrentID);
	}

	@Thunk
	boolean showContextualActions() {
		if (AndroidUtils.isTV(getContext())) {
			// TV doesn't get action bar changes, because it's impossible to get to
			// with client control when you are on row 4000
			return false;
		}
		if (mActionMode != null) {
			if (AndroidUtils.DEBUG_MENU) {
				log(TAG, "showContextualActions: invalidate existing");
			}
			Map<?, ?> selectedFile = getFocusedFile();
			String name = MapUtils.getMapString(selectedFile,
					TransmissionVars.FIELD_FILES_NAME, null);
			mActionMode.setSubtitle(name);

			mActionMode.invalidate();
			return false;
		}

		if (parentActionModeListener != null) {
			parentActionModeListener.setActionModeBeingReplaced(null, true);
		}
		AppCompatActivity activity = (AppCompatActivity) getActivity();
		if (activity == null) {
			return false;
		}
		if (AndroidUtils.DEBUG_MENU) {
			log(TAG,
					"showContextualActions: startAB. mActionMode = " + mActionMode
							+ "; isShowing=" + (activity.getSupportActionBar() == null
									? "null" : activity.getSupportActionBar().isShowing()));
		}
		// Start the CAB using the ActionMode.Callback defined above
		mActionMode = activity.startSupportActionMode(mActionModeCallback);
		if (mActionMode == null) {
			log(TAG, "showContextualActions: startSupportsActionMode returned null");
			return false;
		}

		mActionMode.setTitle(R.string.context_file_title);
		Map<?, ?> selectedFile = getFocusedFile();
		String name = MapUtils.getMapString(selectedFile,
				TransmissionVars.FIELD_FILES_NAME, null);
		mActionMode.setSubtitle(name);
		if (parentActionModeListener != null) {
			parentActionModeListener.setActionModeBeingReplaced(mActionMode, false);
		}
		return true;
	}

	@Thunk
	void finishActionMode() {
		if (mActionMode != null) {
			mActionMode.finish();
			mActionMode = null;
		}
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void rpcTorrentListReceived(String callID,
			final List<?> addedTorrentMaps, final int[] fileIndexes,
			List<?> removedTorrentIDs) {
		super.rpcTorrentListReceived(callID, addedTorrentMaps, fileIndexes,
				removedTorrentIDs);

		boolean found = false;
		for (Object item : addedTorrentMaps) {
			if (!(item instanceof Map)) {
				continue;
			}
			Map mapTorrent = (Map) item;
			Object key = mapTorrent.get("id");

			if (key instanceof Number) {
				found = ((Number) key).longValue() == torrentID;
				if (found) {
					if (AndroidUtils.DEBUG) {
						log(TAG, "TorrentListReceived, contains torrent #" + torrentID);
					}
					break;
				}
			}
		}
		if (!found) {
			if (AndroidUtils.DEBUG) {
				log(TAG, "TorrentListReceived, does not contain torrent #" + torrentID);
			}
			return;
		}
		// Not accurate when we are triggered because of addListener
		lastUpdated = System.currentTimeMillis();

		AndroidUtilsUI.runOnUIThread(this, false, activity -> {
			if (adapter != null) {
				if (AndroidUtils.DEBUG) {
					log(TAG, "TorrentListReceived, existing adapter count "
							+ adapter.getItemCount());
				}
				adapter.setTorrentID(torrentID, true);
			}
			AndroidUtilsUI.invalidateOptionsMenuHC(activity, mActionMode);
		});
	}

	@Override
	public void triggerRefresh() {
		if (torrentID < 0) {
			adapter.removeAllItems();
			return;
		}
		if (isRefreshing()) {
			if (AndroidUtils.DEBUG) {
				log(TAG, "Skipping Refresh");
			}
			return;
		}

		setRefreshing(true);
		session.torrent.getFileInfo(TAG, torrentID, null, (id, added, idxs,
				removed) -> AndroidUtilsUI.runOnUIThread(this, false, a -> {
					if (swipeRefresh != null) {
						swipeRefresh.setRefreshing(false);
					}
					setRefreshing(false);
				}));
	}

	@Override
	public void pageActivated() {
		listview.addOnScrollListener(onScrollListener);
		super.pageActivated();

		FragmentActivity activity = getActivity();
		if (activity == null || activity.isFinishing()) {
			return;
		}
		View filtersArea = activity.findViewById(R.id.sidefilter_files_group);
		if (filtersArea != null) {
			filtersArea.setVisibility(View.VISIBLE);
		}
		adapter.getFilter().refilter();
	}

	@Override
	public void pageDeactivated() {
		listview.removeOnScrollListener(onScrollListener);
		finishActionMode();
		super.pageDeactivated();

		FragmentActivity activity = getActivity();
		if (activity == null || activity.isFinishing()) {
			return;
		}
		View filtersArea = activity.findViewById(R.id.sidefilter_files_group);
		if (filtersArea != null) {
			filtersArea.setVisibility(View.GONE);
		}
	}

	public void launchOrStreamFile() {
		Map<?, ?> selectedFile = getFocusedFile();
		if (selectedFile == null) {
			return;
		}
		boolean isLocalHost = session.getRemoteProfile().isLocalHost();
		if (isLocalHost) {
			launchLocalFile(selectedFile);
		} else {
			streamFile(selectedFile);
		}
	}

	// >>> ActionModeBeingReplacedListener

	@Override
	public void setActionModeBeingReplaced(ActionMode actionMode,
			boolean actionModeBeingReplaced) {
	}

	@Override
	public void actionModeBeingReplacedDone() {
	}

	@Override
	public void rebuildActionMode() {
	}

	@Override
	public ActionMode getActionMode() {
		return mActionMode;
	}

	@Override
	public Callback getActionModeCallback() {
		return mActionModeCallback;
	}

	// << ActionModeBeingReplacedListener

	@Override
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}
		switch (keyCode) {
			// NOTE:
			// KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_MENU);
			case KeyEvent.KEYCODE_MENU:
			case KeyEvent.KEYCODE_BUTTON_X:
			case KeyEvent.KEYCODE_INFO: {
				if (showFileContextMenu()) {
					return true;
				}
				break;
			}
		}

		return false;
	}

	@Thunk
	boolean showFileContextMenu() {
		int selectedPosition = adapter.getSelectedPosition();
		if (selectedPosition < 0) {
			return false;
		}
		FilesAdapterItem item = adapter.getItem(selectedPosition);
		int id = item instanceof FilesAdapterItemFile ? R.string.file_actions_for
				: R.string.folder_actions_for;

		String s = getResources().getString(id, item.name);
		return AndroidUtilsUI.popupContextMenu(getContext(), mActionModeCallback,
				s);
	}

	@Override
	public void onExtraViewVisibilityChange(final View view, int visibility) {
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
				FragmentActivity activity = getActivity();
				if (activity == null) {
					return;
				}
				long sinceMS = System.currentTimeMillis() - lastUpdated;
				String since = DateUtils.getRelativeDateTimeString(activity,
						lastUpdated, DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS,
						0).toString();
				String s = activity.getResources().getString(R.string.last_updated,
						since);

				TextView tvSwipeText = view.findViewById(R.id.swipe_text);
				tvSwipeText.setText(s);

				if (pullRefreshHandler != null) {
					pullRefreshHandler.postDelayed(this,
							sinceMS < DateUtils.MINUTE_IN_MILLIS ? DateUtils.SECOND_IN_MILLIS
									: sinceMS < DateUtils.HOUR_IN_MILLIS
											? DateUtils.MINUTE_IN_MILLIS : DateUtils.HOUR_IN_MILLIS);
				}
			}
		}, 0);
	}

	@Override
	public SortableRecyclerAdapter getMainAdapter() {
		return adapter;
	}

	@Override
	public boolean showFilterEntry() {
		return true;
	}

	@SuppressLint("RestrictedApi")
	@Override
	public MenuBuilder getActionMenuBuilder() {
		if (actionmenuBuilder == null) {
			Context context = getContext();
			if (context == null) {
				return null;
			}
			actionmenuBuilder = new MenuBuilder(context);

			if (!isTorrentOpenOptions && TorrentUtils.isAllowRefresh(session)) {
				MenuItem item = actionmenuBuilder.add(0, R.id.action_refresh, 0,
						R.string.action_refresh);
				item.setIcon(R.drawable.ic_refresh_white_24dp);
			}

			SubMenu subMenuForFile = actionmenuBuilder.addSubMenu(0,
					R.id.menu_group_context, 0, "");
			new MenuInflater(context).inflate(R.menu.menu_all_torrent_files,
					subMenuForFile);
		}

		return actionmenuBuilder;
	}

	private void onSetItemsComplete(FilesTreeAdapter adapter) {
		if (tvScrollTitle != null) {
			boolean showScrollTitle = isTorrentOpenOptions || (adapter.useTree
					&& adapter.getItemCount(FilesTreeAdapter.TYPE_FOLDER) > 0);
			tvScrollTitle.setVisibility(showScrollTitle ? View.VISIBLE : View.GONE);
		}
		if (tvSummary != null && adapter != null) {
			tvSummary.setText(DisplayFormatters.formatByteCountToKiBEtc(
					adapter.getTotalSizeWanted()));
		}
		SideListActivity sideListActivity = getSideListActivity();
		if (sideListActivity != null) {
			sideListActivity.updateSideActionMenuItems();
		}
	}

	private class FilesRecyclerSelectionListener
		implements
		FlexibleRecyclerSelectionListener<FilesTreeAdapter, FilesTreeViewHolder, FilesAdapterItem>
	{

		@Override
		public void onItemClick(FilesTreeAdapter adapter, int adapterPosition) {
			if (!AndroidUtils.usesNavigationControl()) {
				return;
			}
			FilesAdapterItem oItem = adapter.getItem(adapterPosition);
			if (adapter.isInEditMode()) {
				showProgressBar();
				if (oItem instanceof FilesAdapterItemFolder) {
					adapter.setWantState(null, true, hideProgressOnRpcReceive,
							(FilesAdapterItemFolder) oItem);
				} else if (oItem instanceof FilesAdapterItemFile) {
					adapter.setWantState(null, hideProgressOnRpcReceive,
							(FilesAdapterItemFile) oItem);
				} else {
					hideProgressOnRpcReceive.rpcFailure(null, null);
				}
				return;
			}
			if (oItem instanceof FilesAdapterItemFolder) {
				FilesAdapterItemFolder folder = (FilesAdapterItemFolder) oItem;
				adapter.setExpandState(folder, !folder.expand);
			} else {
				showFileContextMenu();
			}
		}

		@Override
		public boolean onItemLongClick(FilesTreeAdapter adapter, int position) {
			return showFileContextMenu();
		}

		@Override
		public void onItemSelected(FilesTreeAdapter adapter, int position,
				boolean isChecked) {
		}

		@Override
		public void onItemCheckedChanged(FilesTreeAdapter adapter,
				FilesAdapterItem item, boolean isChecked) {

			if (adapter.getCheckedItemCount() == 0) {
				finishActionMode();
			} else {
				// Update the subtitle with file name
				showContextualActions();
			}

			AndroidUtilsUI.invalidateOptionsMenuHC(getActivity(), mActionMode);
		}
	}

}
