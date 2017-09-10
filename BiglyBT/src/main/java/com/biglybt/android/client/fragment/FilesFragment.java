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

import com.biglybt.android.FlexibleRecyclerSelectionListener;
import com.biglybt.android.FlexibleRecyclerView;
import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.ImageViewer;
import com.biglybt.android.client.activity.VideoViewer;
import com.biglybt.android.client.adapter.*;
import com.biglybt.android.client.rpc.TorrentListReceivedListener;
import com.biglybt.android.client.rpc.TransmissionRPC;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.widget.CustomToast;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.android.widget.SwipeRefreshLayoutExtra;
import com.biglybt.util.Thunk;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import android.Manifest;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.DownloadManager;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.*;
import android.content.res.Resources;
import android.net.Uri;
import android.os.*;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.*;
import android.webkit.MimeTypeMap;
import android.widget.*;

/**
 * Shows the list of files with a torrent
 * <p/>
 * NOTE: There's duplicate code in OpenOptionsFileFragment.  Untill common
 * code is merged,
 * changes here should be done there.
 * <p/>
 * TODO: Move progressbar logic out so all {@link TorrentDetailPage} can use it
 */
public class FilesFragment
	extends TorrentDetailPage
	implements ActionModeBeingReplacedListener, View.OnKeyListener,
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

	@Thunk
	final Object mLock = new Object();

	@Thunk
	int numProgresses = 0;

	@Thunk
	ActionModeBeingReplacedListener mCallback;

	@Thunk
	ProgressBar progressBar;

	private boolean showProgressBarOnAttach = false;

	@Thunk
	long lastUpdated = 0;

	@Thunk
	boolean refreshing;

	@Thunk
	View viewAreaToggleEditMode;

	@Thunk
	TextView tvScrollTitle;

	@Thunk
	CompoundButton btnEditMode;

	@Thunk
	Handler pullRefreshHandler;

	public FilesFragment() {
		super();
		onScrollListener = new RecyclerView.OnScrollListener() {
			int firstVisibleItem = 0;

			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);
				LinearLayoutManager lm = (LinearLayoutManager) listview.getLayoutManager();
				int firstVisibleItem = lm.findFirstCompletelyVisibleItemPosition();
				if (firstVisibleItem != this.firstVisibleItem) {
					this.firstVisibleItem = firstVisibleItem;
					FilesAdapterDisplayObject itemAtPosition = adapter.getItem(
							firstVisibleItem);
//					Log.d(TAG, "itemAt" + firstVisibleItem + " is " + itemAtPosition);
//					Log.d(TAG, "tvScrollTitle=" + tvScrollTitle);
//					Log.d(TAG, "viewAreaToggleEditMode=" + viewAreaToggleEditMode);

					if (itemAtPosition == null) {
						return;
					}
					if (itemAtPosition.parent != null) {
						if (viewAreaToggleEditMode != null) {
							viewAreaToggleEditMode.setVisibility(View.GONE);
						}
						if (tvScrollTitle != null) {
							tvScrollTitle.setVisibility(View.VISIBLE);
							tvScrollTitle.setText(itemAtPosition.parent.folder);
						}
					} else {
						if (viewAreaToggleEditMode != null) {
							viewAreaToggleEditMode.setVisibility(View.VISIBLE);
						}
						if (tvScrollTitle != null) {
							if (viewAreaToggleEditMode != null) {
								tvScrollTitle.setVisibility(View.INVISIBLE);
							}
							tvScrollTitle.setText("");
						}
					}
				}
			}
		};

	}

	@Override
	public void onAttach(Context context) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onAttach " + this + " to " + context);
		}
		super.onAttach(context);

		FragmentActivity activity = getActivity();

		progressBar = (ProgressBar) activity.findViewById(
				R.id.details_progress_bar);

		if (showProgressBarOnAttach) {
			showProgressBar();
		}

		if (context instanceof ActionModeBeingReplacedListener) {
			mCallback = (ActionModeBeingReplacedListener) context;
		}
	}

	@Thunk
	void showProgressBar() {
		synchronized (mLock) {
			numProgresses++;
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "showProgress " + numProgresses);
			}
		}
		FragmentActivity activity = getActivity();
		if (activity == null || progressBar == null) {
			showProgressBarOnAttach = true;
			return;
		}

		progressBar.postDelayed(new Runnable() {
			@Override
			public void run() {
				FragmentActivity activity = getActivity();
				if (activity == null || numProgresses <= 0) {
					return;
				}
				progressBar.setVisibility(View.VISIBLE);
			}
		}, 600);
	}

	@Thunk
	void hideProgressBar() {
		synchronized (mLock) {
			numProgresses--;
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "hideProgress " + numProgresses);
			}
			if (numProgresses <= 0) {
				numProgresses = 0;
			} else {
				return;
			}
		}
		FragmentActivity activity = getActivity();
		if (activity == null || progressBar == null) {
			showProgressBarOnAttach = false;
			return;
		}
		AndroidUtilsUI.runOnUIThread(this, new Runnable() {
			@Override
			public void run() {
				progressBar.setVisibility(View.GONE);
			}
		});
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onCreate " + this);
		}
		super.onCreate(savedInstanceState);

		setupActionModeCallback();
	}

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onCreateview " + this);
		}

		View view = inflater.inflate(R.layout.frag_torrent_files, container, false);

		viewAreaToggleEditMode = view.findViewById(R.id.files_area_toggleditmode);
		tvScrollTitle = (TextView) view.findViewById(R.id.files_scrolltitle);

		btnEditMode = (CompoundButton) view.findViewById(R.id.files_editmode);
		if (btnEditMode != null) {
			btnEditMode.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					if (adapter == null) {
						return;
					}
					adapter.setInEditMode(btnEditMode.isChecked());
				}
			});
		}

		final SwipeRefreshLayoutExtra swipeRefresh = (SwipeRefreshLayoutExtra) view.findViewById(
				R.id.swipe_container);
		if (swipeRefresh != null) {
			swipeRefresh.setExtraLayout(R.layout.swipe_layout_extra);
			swipeRefresh.setOnRefreshListener(
					new SwipeRefreshLayout.OnRefreshListener() {

						@Override
						public void onRefresh() {
							Session session = getSession();
							showProgressBar();
							session.torrent.getFileInfo(TAG, torrentID, null,
									new TorrentListReceivedListener() {

										@Override
										public void rpcTorrentListReceived(String callID,
												List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
											AndroidUtilsUI.runOnUIThread(FilesFragment.this,
													new Runnable() {
														@Override
														public void run() {
															swipeRefresh.setRefreshing(false);
														}
													});
										}
									});

						}
					});
			swipeRefresh.setOnExtraViewVisibilityChange(this);
		}

		FlexibleRecyclerSelectionListener rs = new FlexibleRecyclerSelectionListener<FilesTreeAdapter, FilesAdapterDisplayObject>() {
			@Override
			public void onItemSelected(FilesTreeAdapter adapter, final int position,
					boolean isChecked) {
			}

			@Override
			public void onItemClick(FilesTreeAdapter adapter, int position) {
				if (AndroidUtils.usesNavigationControl()) {
					FilesAdapterDisplayObject oItem = adapter.getItem(position);
					if (adapter.isInEditMode()) {
						adapter.flipWant(oItem);
						return;
					}
					if (oItem instanceof FilesAdapterDisplayFolder) {
						FilesAdapterDisplayFolder oFolder = (FilesAdapterDisplayFolder) oItem;
						oFolder.expand = !oFolder.expand;
						adapter.notifyItemChanged(adapter.getPositionForItem(oFolder));
						adapter.getFilter().filter("");
					} else {
						showFileContextMenu();
					}
				}
			}

			@Override
			public boolean onItemLongClick(FilesTreeAdapter adapter, int position) {
				if (AndroidUtils.usesNavigationControl()) {
					if (showFileContextMenu()) {
						return true;
					}
				}
				return false;
			}

			@Override
			public void onItemCheckedChanged(FilesTreeAdapter adapter,
					FilesAdapterDisplayObject item, boolean isChecked) {

				if (adapter.getCheckedItemCount() == 0) {
					finishActionMode();
				} else {
					// Update the subtitle with file name
					showContextualActions();
				}

				AndroidUtilsUI.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}
		};

		adapter = new FilesTreeAdapter(this.getActivity(), remoteProfileID, rs);
		adapter.setMultiCheckModeAllowed(false);
		adapter.setCheckOnSelectedAfterMS(100);

		listview = (RecyclerView) view.findViewById(R.id.files_list);
		listview.setLayoutManager(new PreCachingLayoutManager(getContext()));
		listview.setAdapter(adapter);

		if (AndroidUtils.isTV()) {
			((FastScrollRecyclerView) listview).setEnableFastScrolling(false);
			((FlexibleRecyclerView) listview).setFixedVerticalHeight(
					AndroidUtilsUI.dpToPx(48));
			listview.setVerticalFadingEdgeEnabled(true);
			listview.setFadingEdgeLength(AndroidUtilsUI.dpToPx((int) (48 * 1.5)));
		}

		listview.setOnKeyListener(new View.OnKeyListener() {

			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				{
					if (event.getAction() != KeyEvent.ACTION_DOWN) {
						return false;
					}
					switch (keyCode) {
						case KeyEvent.KEYCODE_DPAD_RIGHT: {
							// expand
							int i = adapter.getSelectedPosition();
							FilesAdapterDisplayObject item = adapter.getItem(i);
							if (item instanceof FilesAdapterDisplayFolder) {
								if (!((FilesAdapterDisplayFolder) item).expand) {
									((FilesAdapterDisplayFolder) item).expand = true;
									adapter.notifyItemChanged(adapter.getPositionForItem(item));
									adapter.getFilter().filter("");
									return true;
								}
							}
							break;
						}

						case KeyEvent.KEYCODE_DPAD_LEFT: {
							// collapse
							int i = adapter.getSelectedPosition();
							FilesAdapterDisplayObject item = adapter.getItem(i);
							if (item instanceof FilesAdapterDisplayFolder) {
								if (((FilesAdapterDisplayFolder) item).expand) {
									((FilesAdapterDisplayFolder) item).expand = false;
									adapter.notifyItemChanged(adapter.getPositionForItem(item));
									adapter.getFilter().filter("");
									return true;
								}
							}
							break;
						}

						case KeyEvent.KEYCODE_MEDIA_PLAY:
						case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: {
							launchOrStreamFile();
							return true;
						}
					}

					return false;
				}
			}
		});

		return view;
	}

	@Override
	public void updateTorrentID(final long torrentID, boolean isTorrent,
			boolean wasTorrent, boolean torrentIdChanged) {
		if (torrentIdChanged) {
			adapter.removeAllItems();
		}

		if (!wasTorrent && isTorrent) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "setTorrentID: add listener");
			}
			Session session = getSession();
			session.torrent.addListReceivedListener(this, false);
		} else if (wasTorrent && !isTorrent) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "setTorrentID: remove listener");
			}
			Session session = getSession();
			session.torrent.removeListReceivedListener(this);
		}

		//System.out.println("torrent is " + torrent);
		Session session = getSession();
		if (isTorrent) {
			Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
			if (torrent == null) {
				Log.e(TAG, "setTorrentID: No torrent #" + torrentID);
			} else {

				if (torrent.containsKey(TransmissionVars.FIELD_TORRENT_FILES)) {
					// already has files.. we are good to go, although might be a bit
					// outdated
					adapter.setTorrentID(torrentID);
				} else {
					triggerRefresh();
				}
			}
		} else {
			synchronized (mLock) {
				numProgresses = 1;
				hideProgressBar();
			}
		}

		if (torrentIdChanged) {
			adapter.clearChecked();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
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
					Log.d(TAG, "onCreateActionMode");
				}

				if (adapter.getSelectedPosition() < 0) {
					return false;
				}

				getActivity().getMenuInflater().inflate(
						R.menu.menu_context_torrent_files, menu);

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
					Log.d(TAG, "onPrepareActionMode");
				}

				return prepareContextMenu(menu);
			}

			// Called when the user selects a contextual menu item
			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				return handleMenu(item.getItemId());
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
			Log.d(TAG, "destroyActionMode");
		}
		if (mActionMode == null) {
			return;
		}
		mActionMode = null;

		adapter.clearChecked();

		// delay so actionmode finishes up
		listview.post(new Runnable() {

			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.actionModeBeingReplacedDone();
				}
			}
		});
	}

	@Thunk
	boolean prepareContextMenu(Menu menu) {
		Session session = getSession();
		if (torrentID < 0) {
			return false;
		}

		boolean isComplete = false;
		Map<?, ?> mapFile = getFocusedFile();
		boolean enable = mapFile != null && mapFile.size() > 0;
		if (mapFile != null) {
			long bytesCompleted = MapUtils.getMapLong(mapFile,
					TransmissionVars.FIELD_FILESTATS_BYTES_COMPLETED, 0);
			long length = MapUtils.getMapLong(mapFile,
					TransmissionVars.FIELD_FILES_LENGTH, -1);
			//System.out.println("mapFIle=" + mapFile);
			isComplete = bytesCompleted == length;
		}

		boolean isLocalHost = session.getRemoteProfile().isLocalHost();
		boolean isOnlineOrLocal = BiglyBTApp.getNetworkState().isOnline()
				|| isLocalHost;

		MenuItem menuLaunch = menu.findItem(R.id.action_sel_launch);
		if (menuLaunch != null) {
			if (enable && session.getRemoteProfile().isLocalHost()) {
				boolean canLaunch = isComplete;
				canLaunch &= isOnlineOrLocal;
				menuLaunch.setEnabled(canLaunch);
				menuLaunch.setVisible(true);
			} else {
				menuLaunch.setVisible(false);
			}
		}

		MenuItem menuLaunchStream = menu.findItem(R.id.action_sel_launch_stream);
		if (menuLaunchStream != null) {
			boolean canStream = enable && isComplete
					&& mapFile.containsKey(TransmissionVars.FIELD_FILES_CONTENT_URL);
			canStream &= isOnlineOrLocal;
			menuLaunchStream.setEnabled(canStream);
		}

		MenuItem menuSave = menu.findItem(R.id.action_sel_save);
		if (menuSave != null) {
			boolean visible = !isLocalHost;
			menuSave.setVisible(visible);
			if (visible) {
				boolean canSave = enable && isOnlineOrLocal && isComplete
						&& mapFile.containsKey(TransmissionVars.FIELD_FILES_CONTENT_URL);
				menuSave.setEnabled(canSave);
			}
		}

		int priority = MapUtils.getMapInt(mapFile,
				TransmissionVars.FIELD_TORRENT_FILES_PRIORITY,
				TransmissionVars.TR_PRI_NORMAL);
		MenuItem menuPriorityUp = menu.findItem(R.id.action_sel_priority_up);
		if (menuPriorityUp != null) {
			menuPriorityUp.setEnabled(enable && isOnlineOrLocal && !isComplete
					&& priority < TransmissionVars.TR_PRI_HIGH);
		}
		MenuItem menuPriorityDown = menu.findItem(R.id.action_sel_priority_down);
		if (menuPriorityDown != null) {
			menuPriorityDown.setEnabled(enable && isOnlineOrLocal && !isComplete
					&& priority > TransmissionVars.TR_PRI_LOW);
		}

		boolean wanted = MapUtils.getMapBoolean(mapFile,
				TransmissionVars.FIELD_FILESTATS_WANTED, true);
		MenuItem menuUnwant = menu.findItem(R.id.action_sel_unwanted);
		if (menuUnwant != null) {
			menuUnwant.setVisible(wanted);
			menuUnwant.setEnabled(enable && isOnlineOrLocal);
		}
		MenuItem menuWant = menu.findItem(R.id.action_sel_wanted);
		if (menuWant != null) {
			menuWant.setVisible(!wanted);
			menuWant.setEnabled(enable && isOnlineOrLocal);
		}

		AndroidUtils.fixupMenuAlpha(menu);
		return true;
	}

	@Thunk
	boolean handleMenu(int itemId) {
		Session session = getSession();
		if (torrentID < 0) {
			return false;
		}
		if (itemId == R.id.action_sel_launch) {
			Map<?, ?> selectedFile = getFocusedFile();
			return selectedFile != null && launchLocalFile(selectedFile);
		} else if (itemId == R.id.action_sel_launch_stream) {
			Map<?, ?> selectedFile = getFocusedFile();
			return selectedFile != null && streamFile(selectedFile);
		} else if (itemId == R.id.action_sel_save) {
			Map<?, ?> selectedFile = getFocusedFile();
			return saveFile(selectedFile);
		} else if (itemId == R.id.action_sel_wanted) {
			showProgressBar();
			session.torrent.setFileWantState(TAG, torrentID, new int[] {
				getFocusedFileIndex()
			}, true, null);
			return true;
		} else if (itemId == R.id.action_sel_unwanted) {
			// TODO: Delete Prompt
			showProgressBar();
			session.torrent.setFileWantState(TAG, torrentID, new int[] {
				getFocusedFileIndex()
			}, false, null);
			return true;
		} else if (itemId == R.id.action_sel_priority_up) {

			Map<?, ?> selectedFile = getFocusedFile();

			int priority = MapUtils.getMapInt(selectedFile,
					TransmissionVars.FIELD_TORRENT_FILES_PRIORITY,
					TransmissionVars.TR_PRI_NORMAL);
			if (priority >= TransmissionVars.TR_PRI_HIGH) {
				return true;
			} else {
				priority += 1;
			}

			showProgressBar();
			final int fpriority = priority;
			session.executeRpc(new Session.RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.setFilePriority(TAG, torrentID, new int[] {
						getFocusedFileIndex()
					}, fpriority, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_priority_down) {
			Map<?, ?> selectedFile = getFocusedFile();
			int priority = MapUtils.getMapInt(selectedFile,
					TransmissionVars.FIELD_TORRENT_FILES_PRIORITY,
					TransmissionVars.TR_PRI_NORMAL);
			if (priority <= TransmissionVars.TR_PRI_LOW) {
				return true;
			} else {
				priority -= 1;
			}
			showProgressBar();
			final int fpriority = priority;
			session.executeRpc(new Session.RpcExecuter() {

				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.setFilePriority(TAG, torrentID, new int[] {
						getFocusedFileIndex()
					}, fpriority, null);
				}

			});
			return true;
		}
		return false;
	}

	@Thunk
	boolean saveFile(Map<?, ?> selectedFile) {
		if (selectedFile == null) {
			return false;
		}
		Session session = getSession();
		if (session.getRemoteProfile().isLocalHost()) {
			return false;
		}
		final String contentURL = getContentURL(selectedFile);
		if (contentURL == null || contentURL.length() == 0) {
			return false;
		}

		final File directory = AndroidUtils.getDownloadDir();
		// name in map may contain relative directory
		String name = AndroidUtils.getFileName(
				MapUtils.getMapString(selectedFile, "name", "foo.txt"));
		final File outFile = new File(directory, name);

		if (BiglyBTApp.getNetworkState().isOnlineMobile()) {
			Resources resources = getActivity().getResources();
			String message = resources.getString(R.string.on_mobile,
					resources.getString(R.string.save_content,
							TextUtils.htmlEncode(outFile.getName())));
			Builder builder = new AlertDialog.Builder(getActivity()).setMessage(
					AndroidUtils.fromHTML(message)).setPositiveButton(
							android.R.string.yes, new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									saveFile(contentURL, outFile);
								}
							}).setNegativeButton(android.R.string.no, null);
			builder.show();
			return true;
		}

		saveFile(contentURL, outFile);

		return true;
	}

	private String getContentURL(Map<?, ?> selectedFile) {
		Session session = getSession();
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
			return session.getBaseURL() + ":" + contentPort + "/Content/" + name;
		}

		String contentURL = MapUtils.getMapString(selectedFile,
				TransmissionVars.FIELD_FILES_CONTENT_URL, null);
		if (contentURL == null || contentURL.length() == 0) {
			return contentURL;
		}
		if (contentURL.charAt(0) == ':' || contentURL.charAt(0) == '/') {
			contentURL = session.getBaseURL() + contentURL;
		}
		if (contentURL.contains("/localhost:")) {
			return contentURL.replaceAll("/localhost:",
					"/" + BiglyBTApp.getNetworkState().getActiveIpAddress() + ":");
		}

		return contentURL;
	}

	@Thunk
	void saveFile(final String contentURL, final File outFile) {
		requestPermissions(new String[] {
			Manifest.permission.WRITE_EXTERNAL_STORAGE
		}, new Runnable() {
			@Override
			public void run() {
				reallySaveFile(contentURL, outFile);
			}
		}, new Runnable() {
			@Override
			public void run() {
				CustomToast.showText(R.string.content_saved_failed_perms_denied,
						Toast.LENGTH_LONG);
			}
		});
	}

	@Thunk
	void reallySaveFile(final String contentURL, final File outFile) {
		DownloadManager manager = (DownloadManager) getActivity().getSystemService(
				Context.DOWNLOAD_SERVICE);
		DownloadManager.Request request = new DownloadManager.Request(
				Uri.parse(contentURL));
		request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
				outFile.getName());
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			request.allowScanningByMediaScanner();
			request.setNotificationVisibility(
					DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
		}

		if (AndroidUtils.isTV() || !NotificationManagerCompat.from(
				getContext()).areNotificationsEnabled()) {
			String s = getResources().getString(R.string.content_saving,
					TextUtils.htmlEncode(outFile.getName()),
					TextUtils.htmlEncode(outFile.getParent()));
			CustomToast.showText(AndroidUtils.fromHTML(s), Toast.LENGTH_SHORT);
		}

		manager.enqueue(request);
	}

	private boolean streamFile(final Map<?, ?> selectedFile) {

		final String contentURL = getContentURL(selectedFile);
		if (BiglyBTApp.getNetworkState().isOnlineMobile()) {
			String name = MapUtils.getMapString(selectedFile, "name", null);

			Resources resources = getActivity().getResources();
			String message = resources.getString(R.string.on_mobile,
					resources.getString(R.string.stream_content,
							TextUtils.htmlEncode(name)));
			Builder builder = new AlertDialog.Builder(getActivity()).setMessage(
					AndroidUtils.fromHTML(message)).setPositiveButton(
							android.R.string.yes, new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									reallyStreamFile(selectedFile, contentURL);
								}
							}).setNegativeButton(android.R.string.no, null);
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
					Log.d(TAG, "Launch: File Not Found: " + fullPath);
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
		if (contentURL.startsWith("file://")) {
			try {
				uri = FileProvider.getUriForFile(getContext(),
						"com.biglybt.files",
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
		String name = MapUtils.getMapString(selectedFile, "name", "video");
		intent.putExtra("title", name);

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

		final PackageManager packageManager = getActivity().getPackageManager();
		List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
				PackageManager.MATCH_DEFAULT_ONLY);
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "num intents " + list.size());
			for (ResolveInfo info : list) {
				ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
				Log.d(TAG, info.toString() + "/" + (componentInfo == null ? "null"
						: (componentInfo.name + "/" + componentInfo)));
			}
		}
		for (Iterator<ResolveInfo> it = list.iterator(); it.hasNext();) {
			ResolveInfo info = it.next();
			ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
			if (componentInfo != null && componentInfo.name != null
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
			intent.setClass(getActivity(), fallBackIntentClass);
		}
		if (list.size() == 1) {
			ResolveInfo info = list.get(0);
			ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
			if ((componentInfo != null && componentInfo.name != null)
					&& ("com.amazon.unifiedshare.actionchooser.BuellerShareActivity".equals(
							componentInfo.name)
							|| componentInfo.name.startsWith(
									"com.google.android.tv.frameworkpackagestubs.Stubs"))) {
				intent.setClass(getActivity(), fallBackIntentClass);
			} else {
				ActivityInfo activity = info.activityInfo;
				ComponentName componentName = new ComponentName(
						activity.applicationInfo.packageName, activity.name);
				intent.setComponent(componentName);
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "setting component to " + componentName);
				}
			}
		}

		try {
			startActivity(intent);
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Started " + uri + " MIME: " + intent.getType());
			}
		} catch (java.lang.SecurityException es) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "ERROR launching. " + es.toString());
			}

			if (mimetype != null) {
				try {
					Intent intent2 = new Intent(Intent.ACTION_VIEW, uri);
					intent2.putExtra("title", name);
					if (!tryLaunchWithMimeFirst) {
						intent2.setType(mimetype);
					}

					list = packageManager.queryIntentActivities(intent2,
							PackageManager.MATCH_DEFAULT_ONLY);
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "num intents " + list.size());
						for (ResolveInfo info : list) {
							ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
							Log.d(TAG, info.toString() + "/" + (componentInfo == null ? "null"
									: (componentInfo.name + "/" + componentInfo)));
						}
					}

					startActivity(intent2);
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "Started with"
								+ (intent2.getType() == null ? " no" : " ") + " mime: " + uri);
					}
					return true;
				} catch (Throwable ex2) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "no intent for view. " + ex2.toString());
					}
				}
			}

			CustomToast.showText(R.string.intent_security_fail, Toast.LENGTH_LONG);
		} catch (android.content.ActivityNotFoundException ex) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "no intent for view. " + ex.toString());
			}

			if (mimetype != null) {
				try {
					Intent intent2 = new Intent(Intent.ACTION_VIEW, uri);
					intent2.putExtra("title", name);
					if (!tryLaunchWithMimeFirst) {
						intent2.setType(mimetype);
					}
					startActivity(intent2);
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "Started (no mime set) " + uri);
					}
					return true;
				} catch (android.content.ActivityNotFoundException ex2) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "no intent for view. " + ex2.toString());
					}
				}
			}

			CustomToast.showText(R.string.no_intent, Toast.LENGTH_SHORT);
		}
		return true;
	}

	@Thunk
	int getFocusedFileIndex() {
		Session session = getSession();
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
			Log.d(TAG, "FocusedFile #" + id);
		}
		Object object = listFiles.get((int) id);
		if (object instanceof Map<?, ?>) {
			return (int) id;
		}
		return -1;
	}

	private Map<?, ?> getFocusedFile() {
		FilesAdapterDisplayObject selectedItem = adapter.getSelectedItem();
		if (selectedItem == null) {
			return null;
		}
		if (selectedItem instanceof FilesAdapterDisplayFolder) {
			return null;
		}

		Session session = getSession();
		return selectedItem.getMap(session, torrentID);
	}

	@Thunk
	boolean showContextualActions() {
		if (AndroidUtils.isTV()) {
			// TV doesn't get action bar changes, because it's impossible to get to
			// with client control when you are on row 4000
			return false;
		}
		if (mActionMode != null) {
			if (AndroidUtils.DEBUG_MENU) {
				Log.d(TAG, "showContextualActions: invalidate existing");
			}
			Map<?, ?> selectedFile = getFocusedFile();
			String name = MapUtils.getMapString(selectedFile, "name", null);
			mActionMode.setSubtitle(name);

			mActionMode.invalidate();
			return false;
		}

		if (mCallback != null) {
			mCallback.setActionModeBeingReplaced(null, true);
		}
		AppCompatActivity activity = (AppCompatActivity) getActivity();
		if (activity == null) {
			return false;
		}
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG,
					"showContextualActions: startAB. mActionMode = " + mActionMode
							+ "; isShowing=" + (activity.getSupportActionBar() == null
									? "null" : activity.getSupportActionBar().isShowing()));
		}
		// Start the CAB using the ActionMode.Callback defined above
		mActionMode = activity.startSupportActionMode(mActionModeCallback);
		if (mActionMode == null) {
			Log.d(TAG,
					"showContextualActions: startSupportsActionMode returned null");
			return false;
		}

		mActionMode.setTitle(R.string.context_file_title);
		Map<?, ?> selectedFile = getFocusedFile();
		String name = MapUtils.getMapString(selectedFile, "name", null);
		mActionMode.setSubtitle(name);
		if (mCallback != null) {
			mCallback.setActionModeBeingReplaced(mActionMode, false);
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
			final List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
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
						Log.d(TAG, "TorrentListReceived, contains torrent #" + torrentID);
					}
					break;
				}
			}
		}
		if (!found) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG,
						"TorrentListReceived, does not contain torrent #" + torrentID);
			}
			return;
		}
		// Not accurate when we are triggered because of addListener
		lastUpdated = System.currentTimeMillis();

		AndroidUtilsUI.runOnUIThread(this, new AndroidUtils.RunnableWithActivity() {
			@Override
			public void run() {
				if (adapter != null) {
					adapter.setTorrentID(torrentID);
				}
				AndroidUtilsUI.invalidateOptionsMenuHC(activity, mActionMode);
			}
		});
	}

	@Override
	public void triggerRefresh() {
		Session session = getSession();
		if (torrentID < 0) {
			return;
		}
		synchronized (mLock) {
			if (refreshing) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Skipping Refresh");
				}
				return;
			}
			refreshing = true;
		}

		showProgressBar();
		session.torrent.getFileInfo(TAG, torrentID, null,
				new TorrentListReceivedListener() {
					@Override
					public void rpcTorrentListReceived(String callID,
							List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
						hideProgressBar();
						synchronized (mLock) {
							refreshing = false;
						}
					}
				});
	}

	@Override
	public void pageActivated() {
		listview.addOnScrollListener(onScrollListener);
		super.pageActivated();
	}

	@Override
	public void pageDeactivated() {
		listview.removeOnScrollListener(onScrollListener);
		finishActionMode();
		synchronized (mLock) {
			refreshing = false;
		}
		super.pageDeactivated();
	}

	public void launchOrStreamFile() {
		Map<?, ?> selectedFile = getFocusedFile();
		if (selectedFile == null) {
			return;
		}
		Session session = getSession();
		boolean isLocalHost = session.getRemoteProfile().isLocalHost();
		if (isLocalHost) {
			launchLocalFile(selectedFile);
		} else {
			streamFile(selectedFile);
		}
	}

	@Override
	String getTAG() {
		return TAG;
	}

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
		FilesAdapterDisplayObject item = adapter.getItem(selectedPosition);

		// TODO: Menu for folder actions
		if (!(item instanceof FilesAdapterDisplayFile)) {
			return false;
		}

		String s = getResources().getString(R.string.file_actions_for, item.name);
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

				TextView tvSwipeText = (TextView) view.findViewById(R.id.swipe_text);
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

}
