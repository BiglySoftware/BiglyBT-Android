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
 * 
 */

package com.vuze.android.remote.fragment;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.support.v7.widget.*;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.*;

import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.R;
import com.vuze.android.remote.activity.ImageViewer;
import com.vuze.android.remote.activity.VideoViewer;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.util.MapUtils;

/**
 * Shows the list of files with a torrent
 * 
 * 
 * TODO: Move progressbar logic out so all {@link TorrentDetailPage} can use it
 */
public class FilesFragment
	extends TorrentDetailPage
	implements ActionModeBeingReplacedListener
{
	protected static final String TAG = "FilesFragment";

	/**
	 * Launching an Intent without a Mime will result in a different list
	 * of apps then one including the Mime type.  Sometimes one is better than
	 * the other, especially with URLs :(
	 * 
	 * Pros for setting MIME:
	 * - In theory should provide more apps
	 * 
	 * Cons for setting MIME:
	 * - the Web browser will not show as an app, but rather a
	 * html viewer app, if you are lucky
	 * - A lot of apps that accept MIME types can't handle URLs and fail
	 */
	protected static final boolean tryLaunchWithMimeFirst = false;

	private RecyclerView listview;

	private FilesTreeAdapter adapter;

	protected int selectedFileIndex = -1;

	private Callback mActionModeCallback;

	protected ActionMode mActionMode;

	private final Object mLock = new Object();

	private int numProgresses = 0;

	private ActionModeBeingReplacedListener mCallback;

	private ProgressBar progressBar;

	private boolean showProgressBarOnAttach = false;

	private long lastUpdated = 0;

	private boolean refreshing;

	private View viewAreaToggleEditMode;

	private TextView tvScrollTitle;

	private CompoundButton btnEditMode;

	private Toolbar tb;

	public FilesFragment() {
		super();
	}

	@Override
	public void onAttach(Context context) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onAttach " + this + " to " + context);
		}
		super.onAttach(context);

		if (showProgressBarOnAttach) {
			showProgressBar();
		}

		if (context instanceof ActionModeBeingReplacedListener) {
			mCallback = (ActionModeBeingReplacedListener) context;
		}
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onActivityCreated(android.os.Bundle)
	 */
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		tb = (Toolbar) getActivity().findViewById(R.id.toolbar_bottom);
	}

	private void showProgressBar() {
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

	private void hideProgressBar() {
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
		AndroidUtils.runOnUIThread(this, new Runnable() {
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

		FragmentActivity activity = getActivity();

		progressBar = (ProgressBar) activity.findViewById(
				R.id.details_progress_bar);

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

		final SwipeTextRefreshLayout swipeRefresh = (SwipeTextRefreshLayout) view.findViewById(
				R.id.swipe_container);
		if (swipeRefresh != null) {
			swipeRefresh.setOnRefreshListener(
					new SwipeRefreshLayout.OnRefreshListener() {
						@Override
						public void onRefresh() {
							if (sessionInfo == null) {
								return;
							}
							showProgressBar();
							sessionInfo.executeRpc(new RpcExecuter() {
								@Override
								public void executeRpc(TransmissionRPC rpc) {
									rpc.getTorrentFileInfo(TAG, torrentID, null,
											new TorrentListReceivedListener() {
										@Override
										public void rpcTorrentListReceived(String callID,
												List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
											AndroidUtils.runOnUIThread(FilesFragment.this,
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
											FragmentActivity activity = getActivity();
											if (activity == null) {
												return;
											}
											long sinceMS = System.currentTimeMillis() - lastUpdated;
											String since = DateUtils.getRelativeDateTimeString(
													activity, lastUpdated, DateUtils.SECOND_IN_MILLIS,
													DateUtils.WEEK_IN_MILLIS, 0).toString();
											String s = activity.getResources().getString(
													R.string.last_updated, since);
											swipeRefresh.getTextView().setText(s);

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

		listview = (RecyclerView) view.findViewById(R.id.files_list);
		listview.setLayoutManager(new LinearLayoutManager(getContext()));
		listview.setAdapter(adapter);
		((SimpleItemAnimator) listview.getItemAnimator()).setSupportsChangeAnimations(
				false);

		listview.setOnScrollListener(new RecyclerView.OnScrollListener() {
			int firstVisibleItem = 0;

			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);
				LinearLayoutManager lm = (LinearLayoutManager) listview.getLayoutManager();
				int firstVisibleItem = lm.findFirstCompletelyVisibleItemPosition();
				if (firstVisibleItem != this.firstVisibleItem) {
					this.firstVisibleItem = firstVisibleItem;
					FilesAdapterDisplayObject itemAtPosition = (FilesAdapterDisplayObject) adapter.getItem(
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
		});

		FlexibleRecyclerSelectionListener rs = new FlexibleRecyclerSelectionListener() {
			@Override
			public void onItemSelected(final int position, boolean isChecked) {
			}

			@Override
			public void onItemClick(int position) {
			}

			@Override
			public boolean onItemLongClick(int position) {
				selectedFileIndex = (int) adapter.getItemId(position);
				adapter.setItemChecked(position, true);
				return true;
			}

			@Override
			public void onItemCheckedChanged(int position, boolean isChecked) {
				selectedFileIndex = isChecked ? (int) adapter.getItemId(position) : -1;

				Object oItem = adapter.getItem(position);
				if (oItem instanceof FilesAdapterDisplayFolder) {
					FilesAdapterDisplayFolder oFolder = (FilesAdapterDisplayFolder) oItem;
					oFolder.expand = !oFolder.expand;
					adapter.getFilter().filter("");

					finishActionMode();
					adapter.setItemChecked(position, false);
					return;
				}

				if (mActionMode == null && isChecked) {
					showContextualActions();
				}

				if (adapter.getCheckedItemCount() == 0) {
					finishActionMode();
				}

				AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}
		};

		adapter = new FilesTreeAdapter(this.getActivity(), rs);
		adapter.setSessionInfo(sessionInfo);
		listview.setAdapter(adapter);

		return view;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.TorrentDetailPage#updateTorrentID(long, boolean, boolean, boolean)
	 */
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
			sessionInfo.addTorrentListReceivedListener(this, false);
		} else if (wasTorrent && !isTorrent) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "setTorrentID: remove listener");
			}
			sessionInfo.removeTorrentListReceivedListener(this);
		}

		//System.out.println("torrent is " + torrent);
		adapter.setSessionInfo(sessionInfo);
		if (isTorrent) {
			Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
			if (torrent == null) {
				Log.e(TAG, "setTorrentID: No torrent #" + torrentID);
			} else {

				if (torrent.containsKey("files")) {
					// already has files.. we are good to go, although might be a bit outdated
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

		outState.putLong("torrentID", torrentID);
	}

	private void setupActionModeCallback() {
		mActionModeCallback = new Callback() {

			// Called when the action mode is created; startActionMode() was called
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "onCreateActionMode");
				}

				if (mode != null) {
					mActionMode = new ActionModeWrapperV7(mode, tb, getActivity());
				}

				if (tb != null) {
					menu = tb.getMenu();
				}

				// Inflate a menu resource providing context menu items
				ActionBarToolbarSplitter.buildActionBar(getActivity(), this,
						R.menu.menu_context_torrent_files, menu, tb);

				return true;
			}

			// Called each time the action mode is shown. Always called after onCreateActionMode, but
			// may be called multiple times if the mode is invalidated.
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				if (AndroidUtils.DEBUG_MENU) {
					Log.d(TAG, "onPrepareActionMode");
				}

				if (tb != null) {
					menu = tb.getMenu();
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

	private void destroyActionMode() {
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

	protected boolean prepareContextMenu(Menu menu) {
		if (sessionInfo == null || torrentID < 0) {
			return false;
		}

		boolean isComplete = false;
		Map<?, ?> mapFile = getSelectedFile();
		if (mapFile != null) {
			long bytesCompleted = MapUtils.getMapLong(mapFile, "bytesCompleted", 0);
			long length = MapUtils.getMapLong(mapFile, "length", -1);
			//System.out.println("mapFIle=" + mapFile);
			isComplete = bytesCompleted == length;
		}

		boolean isLocalHost = sessionInfo != null
				&& sessionInfo.getRemoteProfile().isLocalHost();
		boolean isOnlineOrLocal = VuzeRemoteApp.getNetworkState().isOnline()
				|| isLocalHost;

		MenuItem menuLaunch = menu.findItem(R.id.action_sel_launch);
		if (menuLaunch != null) {
			if (sessionInfo.getRemoteProfile().isLocalHost()) {
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
			boolean canStream = isComplete
					&& mapFile.containsKey(TransmissionVars.FIELD_FILES_CONTENT_URL);
			canStream &= isOnlineOrLocal;
			menuLaunchStream.setEnabled(canStream);
		}

		MenuItem menuSave = menu.findItem(R.id.action_sel_save);
		if (menuSave != null) {
			boolean visible = !isLocalHost;
			menuSave.setVisible(visible);
			if (visible) {
				boolean canSave = isOnlineOrLocal && isComplete
						&& mapFile.containsKey(TransmissionVars.FIELD_FILES_CONTENT_URL);
				menuSave.setEnabled(canSave);
			}
		}

		int priority = MapUtils.getMapInt(mapFile,
				TransmissionVars.FIELD_TORRENT_FILES_PRIORITY,
				TransmissionVars.TR_PRI_NORMAL);
		MenuItem menuPriorityUp = menu.findItem(R.id.action_sel_priority_up);
		if (menuPriorityUp != null) {
			menuPriorityUp.setEnabled(isOnlineOrLocal && !isComplete
					&& priority < TransmissionVars.TR_PRI_HIGH);
		}
		MenuItem menuPriorityDown = menu.findItem(R.id.action_sel_priority_down);
		if (menuPriorityDown != null) {
			menuPriorityDown.setEnabled(isOnlineOrLocal && !isComplete
					&& priority > TransmissionVars.TR_PRI_LOW);
		}

		boolean wanted = MapUtils.getMapBoolean(mapFile, "wanted", true);
		MenuItem menuUnwant = menu.findItem(R.id.action_sel_unwanted);
		if (menuUnwant != null) {
			menuUnwant.setVisible(wanted);
			menuUnwant.setEnabled(isOnlineOrLocal);
		}
		MenuItem menuWant = menu.findItem(R.id.action_sel_wanted);
		if (menuWant != null) {
			menuWant.setVisible(!wanted);
			menuWant.setEnabled(isOnlineOrLocal);
		}

		AndroidUtils.fixupMenuAlpha(menu);
		return true;
	}

	protected boolean handleMenu(int itemId) {
		if (sessionInfo == null || torrentID < 0) {
			return false;
		}
		if (itemId == R.id.action_sel_launch) {
			Map<?, ?> selectedFile = getSelectedFile();
			return selectedFile != null && launchLocalFile(selectedFile);
		} else if (itemId == R.id.action_sel_launch_stream) {
			Map<?, ?> selectedFile = getSelectedFile();
			return selectedFile != null && streamFile(selectedFile);
		} else if (itemId == R.id.action_sel_save) {
			Map<?, ?> selectedFile = getSelectedFile();
			return saveFile(selectedFile);
		} else if (itemId == R.id.action_sel_wanted) {
			showProgressBar();
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.setWantState(TAG, torrentID, new int[] {
						selectedFileIndex
					}, true, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_unwanted) {
			// TODO: Delete Prompt
			showProgressBar();
			sessionInfo.executeRpc(new RpcExecuter() {

				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.setWantState(TAG, torrentID, new int[] {
						selectedFileIndex
					}, false, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_priority_up) {

			Map<?, ?> selectedFile = getSelectedFile();

			int priority = MapUtils.getMapInt(selectedFile,
					TransmissionVars.FIELD_TORRENT_FILES_PRIORITY,
					TransmissionVars.TR_PRI_NORMAL);
			if (priority >= TransmissionVars.TR_PRI_HIGH)

			{
				return true;
			} else

			{
				priority += 1;
			}

			showProgressBar();
			final int fpriority = priority;
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.setFilePriority(TAG, torrentID, new int[] {
						selectedFileIndex
					}, fpriority, null);
				}
			});
			return true;
		} else if (itemId == R.id.action_sel_priority_down) {
			Map<?, ?> selectedFile = getSelectedFile();
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
			sessionInfo.executeRpc(new RpcExecuter() {

				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.setFilePriority(TAG, torrentID, new int[] {
						selectedFileIndex
					}, fpriority, null);
				}

			});
			return true;
		}
		return false;
	}

	protected boolean saveFile(Map<?, ?> selectedFile) {
		if (selectedFile == null) {
			return false;
		}
		if (sessionInfo == null) {
			return false;
		}
		if (sessionInfo.getRemoteProfile().isLocalHost()) {
			return false;
		}
		final String contentURL = getContentURL(selectedFile);
		if (contentURL == null || contentURL.length() == 0) {
			return false;
		}

		final File directory = AndroidUtils.getDownloadDir();
		final File outFile = new File(directory,
				MapUtils.getMapString(selectedFile, "name", "foo.txt"));

		if (VuzeRemoteApp.getNetworkState().isOnlineMobile()) {
			Resources resources = getActivity().getResources();
			String message = resources.getString(R.string.on_mobile,
					resources.getString(R.string.save_content,
							TextUtils.htmlEncode(outFile.getName())));
			Builder builder = new AlertDialog.Builder(getActivity()).setMessage(
					Html.fromHtml(message)).setPositiveButton(R.string.yes,
							new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									reallySaveFile(contentURL, outFile);
								}
							}).setNegativeButton(R.string.no, null);
			builder.show();
			return true;
		}

		reallySaveFile(contentURL, outFile);

		return true;
	}

	private String getContentURL(Map<?, ?> selectedFile) {
		String contentURL = MapUtils.getMapString(selectedFile, "contentURL", null);
		if (contentURL == null || contentURL.length() == 0) {
			return contentURL;
		}
		if (contentURL.charAt(0) == ':' || contentURL.charAt(0) == '/') {
			contentURL = sessionInfo.getBaseURL() + contentURL;
		}
		if (contentURL.contains("/localhost:")) {
			return contentURL.replaceAll("/localhost:",
					"/" + VuzeRemoteApp.getNetworkState().getActiveIpAddress() + ":");
		}

		return contentURL;
	}

	protected void reallySaveFile(final String contentURL, final File outFile) {
		showProgressBar();
		new Thread(new Runnable() {
			String failText = null;

			@Override
			public void run() {
				try {
					AndroidUtils.copyUrlToFile(contentURL, outFile);
				} catch (Exception e) {
					VuzeEasyTracker.getInstance().logError(e);
					failText = e.getMessage();
				}
				FragmentActivity activity = getActivity();
				if (activity == null) {
					return;
				}
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						hideProgressBar();
						Activity activity = getActivity();
						Context context = activity == null ? VuzeRemoteApp.getContext()
								: activity;
						String s;
						if (failText == null) {
							s = context.getResources().getString(R.string.content_saved,
									TextUtils.htmlEncode(outFile.getName()),
									TextUtils.htmlEncode(outFile.getParent()));
						} else {
							s = context.getResources().getString(
									R.string.content_saved_failed,
									TextUtils.htmlEncode(outFile.getName()),
									TextUtils.htmlEncode(outFile.getParent()),
									TextUtils.htmlEncode(failText));
						}
						Toast.makeText(context, Html.fromHtml(s),
								Toast.LENGTH_SHORT).show();
					}
				});
			}
		}).start();
	}

	protected boolean streamFile(final Map<?, ?> selectedFile) {

		if (VuzeRemoteApp.getNetworkState().isOnlineMobile()) {
			String name = MapUtils.getMapString(selectedFile, "name", null);

			Resources resources = getActivity().getResources();
			String message = resources.getString(R.string.on_mobile,
					resources.getString(R.string.stream_content,
							TextUtils.htmlEncode(name)));
			Builder builder = new AlertDialog.Builder(getActivity()).setMessage(
					Html.fromHtml(message)).setPositiveButton(R.string.yes,
							new OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									reallyStreamFile(selectedFile);
								}
							}).setNegativeButton(R.string.no, null);
			builder.show();
		} else {
			reallyStreamFile(selectedFile);
		}
		return true;
	}

	protected boolean launchLocalFile(Map<?, ?> selectedFile) {

		String fullPath = MapUtils.getMapString(selectedFile, "fullPath", null);
		if (fullPath != null && fullPath.length() > 0) {
			File file = new File(fullPath);
			if (file.exists()) {
				Uri uri = Uri.fromFile(file);
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				try {
					startActivity(intent);
				} catch (android.content.ActivityNotFoundException ignore) {

				}
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Started " + uri);
				}
				return true;
			} else {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Launch: File Not Found: " + fullPath);
				}
			}
		}

		return false;
	}

	@SuppressWarnings("unused")
	protected boolean reallyStreamFile(Map<?, ?> selectedFile) {
		final String contentURL = getContentURL(selectedFile);
		if (contentURL != null && contentURL.length() > 0) {
			Uri uri = Uri.parse(contentURL);

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
			if (list.size() == 0) {
				// Intent will launch, but show message to the user:
				// "Opening web browser links is not supported"
				intent.setClass(getActivity(), fallBackIntentClass);
			}
			if (list.size() == 1) {
				ResolveInfo info = list.get(0);
				ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
				if (componentInfo != null && componentInfo.name != null) {
					if ("com.amazon.unifiedshare.actionchooser.BuellerShareActivity".equals(
							componentInfo.name)
							|| componentInfo.name.startsWith(
									"com.google.android.tv.frameworkpackagestubs.Stubs")) {
						intent.setClass(getActivity(), fallBackIntentClass);
					}
				}
			}

			try {
				startActivity(intent);
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Started " + uri + " MIME: " + intent.getType());
				}
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

				Toast.makeText(getActivity().getApplicationContext(),
						getActivity().getResources().getString(R.string.no_intent),
						Toast.LENGTH_SHORT).show();
			}
			return true;
		}

		return true;
	}

	protected Map<?, ?> getSelectedFile() {
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			return null;
		}
		List<?> listFiles = MapUtils.getMapList(torrent, "files", null);
		if (listFiles == null || selectedFileIndex < 0
				|| selectedFileIndex >= listFiles.size()) {
			return null;
		}
		Object object = listFiles.get(selectedFileIndex);
		if (object instanceof Map<?, ?>) {
			return (Map<?, ?>) object;
		}
		return null;
	}

	protected Map<?, ?> getFocusedFile() {
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			return null;
		}
		List<?> listFiles = MapUtils.getMapList(torrent, "files", null);
		int selectedPosition = adapter.getSelectedPosition();
		long id = adapter.getItemId(selectedPosition);
		if (listFiles == null || id < 0 || id >= listFiles.size()) {
			return null;
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "FocusedFile #" + id);
		}
		Object object = listFiles.get((int) id);
		if (object instanceof Map<?, ?>) {
			return (Map<?, ?>) object;
		}
		return null;
	}

	protected boolean showContextualActions() {
		if (mActionMode != null) {
			if (AndroidUtils.DEBUG_MENU) {
				Log.d(TAG, "showContextualActions: invalidate existing");
			}
			Map<?, ?> selectedFile = getSelectedFile();
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
		ActionMode am = activity.startSupportActionMode(mActionModeCallback);
		mActionMode = new ActionModeWrapperV7(am, tb, getActivity());

		mActionMode.setTitle(R.string.context_file_title);
		Map<?, ?> selectedFile = getSelectedFile();
		String name = MapUtils.getMapString(selectedFile, "name", null);
		mActionMode.setSubtitle(name);
		if (mCallback != null) {
			mCallback.setActionModeBeingReplaced(mActionMode, false);
		}
		return true;
	}

	public void finishActionMode() {
		if (mActionMode != null) {
			mActionMode.finish();
			mActionMode = null;
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.rpc.TorrentListReceivedListener#rpcTorrentListReceived(java.util.List)
	 */
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

		AndroidUtils.runOnUIThread(this, new AndroidUtils.RunnableWithActivity() {
			@Override
			public void run() {
				hideProgressBar();
				if (adapter != null) {
					adapter.setTorrentID(torrentID);
				}
				AndroidUtils.invalidateOptionsMenuHC(activity, mActionMode);
			}
		});
	}

	@Override
	public void triggerRefresh() {
		if (sessionInfo == null || torrentID < 0) {
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
		sessionInfo.executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.getTorrentFileInfo(TAG, torrentID, null,
						new TorrentListReceivedListener() {
					@Override
					public void rpcTorrentListReceived(String callID,
							List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
						synchronized (mLock) {
							refreshing = false;
						}
					}
				});
			}
		});
	}

	@Override
	public void pageDeactivated() {
		finishActionMode();
		synchronized (mLock) {
			refreshing = false;
		}
		super.pageDeactivated();
	}

	public void launchOrStreamFile() {
		Map<?, ?> selectedFile = getFocusedFile();
		if (selectedFile == null) {
			selectedFile = getSelectedFile();
			if (selectedFile == null) {
				return;
			}
		}
		boolean isLocalHost = sessionInfo != null
				&& sessionInfo.getRemoteProfile().isLocalHost();
		if (isLocalHost) {
			launchLocalFile(selectedFile);
		} else {
			streamFile(selectedFile);
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.TorrentDetailPage#getTAG()
	 */
	@Override
	String getTAG() {
		return TAG;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#setActionModeBeingReplaced(android.support.v7.view.ActionMode, boolean)
	 */
	@Override
	public void setActionModeBeingReplaced(ActionMode actionMode,
			boolean actionModeBeingReplaced) {
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#actionModeBeingReplacedDone()
	 */
	@Override
	public void actionModeBeingReplacedDone() {
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#rebuildActionMode()
	 */
	@Override
	public void rebuildActionMode() {
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.ActionModeBeingReplacedListener#getActionMode()
	 */
	@Override
	public ActionMode getActionMode() {
		return mActionMode;
	}

	@Override
	public Callback getActionModeCallback() {
		return mActionModeCallback;
	}
}
