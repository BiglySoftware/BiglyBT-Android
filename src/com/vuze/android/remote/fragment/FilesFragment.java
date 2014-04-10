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
import android.os.*;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.*;
import android.webkit.MimeTypeMap;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

import com.aelitis.azureus.util.MapUtils;
import com.handmark.pulltorefresh.library.*;
import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnPullEventListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshBase.State;
import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.activity.VideoViewer;
import com.vuze.android.remote.R;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;

/**
 * TODO: Move progressbar logic out so all {@link TorrentDetailPage} can use it
 */
public class FilesFragment
	extends TorrentDetailPage
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

	private ListView listview;

	private FilesAdapter adapter;

	protected int selectedFileIndex = -1;

	private Callback mActionModeCallback;

	protected ActionModeWrapper mActionMode;

	private Object mLock = new Object();

	private int numProgresses = 0;

	private ActionModeBeingReplacedListener mCallback;

	private ProgressBar progressBar;

	private boolean showProgressBarOnAttach = false;

	private PullToRefreshListView pullListView;

	private long lastUpdated;

	private boolean refreshing;

	public FilesFragment() {
		super();
	}

	@Override
	public void onAttach(Activity activity) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onAttach " + this + " to " + activity);
		}
		super.onAttach(activity);

		if (showProgressBarOnAttach) {
			System.out.println("show Progress!");
			showProgressBar();
		}

		if (activity instanceof ActionModeBeingReplacedListener) {
			mCallback = (ActionModeBeingReplacedListener) activity;
		}
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
			System.out.println("show Progress Later");
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

		progressBar = (ProgressBar) getActivity().findViewById(
				R.id.details_progress_bar);

		View oListView = view.findViewById(R.id.files_list);
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
								if (pullListView.getState() != State.REFRESHING) {
									pullListView.getLoadingLayoutProxy().setLastUpdatedLabel(s);
								}

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
															pullListView.onRefreshComplete();
														}
													});
										}
									});
						}
					});

				}

			});
		}

		listview.setItemsCanFocus(false);
		listview.setClickable(true);
		listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		listview.setOnItemClickListener(new OnItemClickListener() {

			private long lastIdClicked = -1;

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				boolean isChecked = listview.isItemChecked(position);
				// DON'T USE adapter.getItemId, it doesn't account for headers!
				selectedFileIndex = isChecked
						? (int) parent.getItemIdAtPosition(position) : -1;

				if (mActionMode == null) {
					showContextualActions();
					lastIdClicked = id;
				} else if (lastIdClicked == id) {
					finishActionMode();
					//listview.setItemChecked(position, false);
					lastIdClicked = -1;
				} else {
					showContextualActions();

					lastIdClicked = id;
				}

				AndroidUtils.invalidateOptionsMenuHC(getActivity(), mActionMode);
			}
		});

		listview.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				selectedFileIndex = (int) parent.getItemIdAtPosition(position);
				listview.setItemChecked(position, true);
				return false;
			}
		});

		adapter = new FilesAdapter(this.getActivity());
		adapter.setSessionInfo(sessionInfo);
		listview.setItemsCanFocus(true);
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
			adapter.clearList();
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
			AndroidUtils.clearChecked(listview);
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
				// Inflate a menu resource providing context menu items
				MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.menu_context_torrent_files, menu);

				return true;
			}

			// Called each time the action mode is shown. Always called after onCreateActionMode, but
			// may be called multiple times if the mode is invalidated.
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
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
		mActionMode = null;

		listview.clearChoices();
		// Not sure why ListView doesn't invalidate by default
		adapter.notifyDataSetInvalidated();

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
		Map<?, ?> mapFileStats = getSelectedFileStats();
		if (mapFile != null) {
			long bytesCompleted = MapUtils.getMapLong(mapFile, "bytesCompleted", 0);
			long length = MapUtils.getMapLong(mapFile, "length", -1);
			//System.out.println("mapFIle=" + mapFile);
			isComplete = bytesCompleted == length;
		}

		boolean isLocalHost = sessionInfo != null
				&& !sessionInfo.getRemoteProfile().isLocalHost();

		MenuItem menuLaunch = menu.findItem(R.id.action_sel_launch);
		if (menuLaunch != null) {
			boolean canLaunch = isComplete && mapFile != null
					&& mapFile.containsKey(TransmissionVars.FIELD_FILES_CONTENT_URL);
			canLaunch &= (isLocalHost || VuzeRemoteApp.getNetworkState().isOnline());
			menuLaunch.setEnabled(canLaunch);
		}

		MenuItem menuSave = menu.findItem(R.id.action_sel_save);
		if (menuSave != null) {
			boolean visible = isLocalHost;
			menuSave.setVisible(visible);
			if (visible) {
				boolean canSave = isComplete && mapFile != null
						&& mapFile.containsKey(TransmissionVars.FIELD_FILES_CONTENT_URL);
				menuSave.setEnabled(canSave);
			}
		}

		int priority = MapUtils.getMapInt(mapFileStats,
				TransmissionVars.FIELD_TORRENT_FILES_PRIORITY,
				TransmissionVars.TR_PRI_NORMAL);
		MenuItem menuPriorityUp = menu.findItem(R.id.action_sel_priority_up);
		if (menuPriorityUp != null) {
			menuPriorityUp.setEnabled(!isComplete
					&& priority < TransmissionVars.TR_PRI_HIGH);
		}
		MenuItem menuPriorityDown = menu.findItem(R.id.action_sel_priority_down);
		if (menuPriorityDown != null) {
			menuPriorityDown.setEnabled(!isComplete
					&& priority > TransmissionVars.TR_PRI_LOW);
		}

		boolean wanted = MapUtils.getMapBoolean(mapFileStats, "wanted", true);
		MenuItem menuUnwant = menu.findItem(R.id.action_sel_unwanted);
		if (menuUnwant != null) {
			menuUnwant.setVisible(wanted);
		}
		MenuItem menuWant = menu.findItem(R.id.action_sel_wanted);
		if (menuWant != null) {
			menuWant.setVisible(!wanted);
		}

		AndroidUtils.fixupMenuAlpha(menu);
		return true;
	}

	protected boolean handleMenu(int itemId) {
		if (sessionInfo == null || torrentID < 0) {
			return false;
		}
		switch (itemId) {
			case R.id.action_sel_launch: {
				Map<?, ?> selectedFile = getSelectedFile();
				if (selectedFile == null) {
					return false;
				}
				return launchFile(selectedFile);
			}
			case R.id.action_sel_save: {
				Map<?, ?> selectedFile = getSelectedFile();
				return saveFile(selectedFile);
			}
			case R.id.action_sel_wanted: {
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
			}
			case R.id.action_sel_unwanted: {
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
			}
			case R.id.action_sel_priority_up: {
				Map<?, ?> selectedFile = getSelectedFileStats();
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
			case R.id.action_sel_priority_down: {
				Map<?, ?> selectedFile = getSelectedFileStats();
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
		final File outFile = new File(directory, MapUtils.getMapString(
				selectedFile, "name", "foo.txt"));

		if (!AndroidUtils.isOnlineMobile(getActivity())) {
			Resources resources = getActivity().getResources();
			String message = resources.getString(
					R.string.on_mobile,
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

		return contentURL;
	}

	protected void reallySaveFile(final String contentURL, final File outFile) {
		showProgressBar();
		new Thread(new Runnable() {

			@Override
			public void run() {
				AndroidUtils.copyUrlToFile(contentURL, outFile);
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
						String s = context.getResources().getString(R.string.content_saved,
								TextUtils.htmlEncode(outFile.getName()),
								TextUtils.htmlEncode(outFile.getParent()));
						Toast.makeText(context, Html.fromHtml(s), Toast.LENGTH_SHORT).show();
					}
				});
			}
		}).start();
	}

	protected boolean launchFile(final Map<?, ?> selectedFile) {

		if (AndroidUtils.isOnlineMobile(getActivity())) {
			String name = MapUtils.getMapString(selectedFile, "name", null);

			Resources resources = getActivity().getResources();
			String message = resources.getString(
					R.string.on_mobile,
					resources.getString(R.string.stream_content,
							TextUtils.htmlEncode(name)));
			Builder builder = new AlertDialog.Builder(getActivity()).setMessage(
					Html.fromHtml(message)).setPositiveButton(R.string.yes,
					new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							reallyLaunchFile(selectedFile);
						}
					}).setNegativeButton(R.string.no, null);
			builder.show();
		} else {
			reallyLaunchFile(selectedFile);
		}
		return true;
	}

	@SuppressWarnings("unused")
	protected boolean reallyLaunchFile(Map<?, ?> selectedFile) {

		String fullPath = MapUtils.getMapString(selectedFile, "fullPath", null);
		if (fullPath != null && fullPath.length() > 0) {
			File file = new File(fullPath);
			if (file.exists()) {
				Uri uri = Uri.fromFile(file);
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				try {
					startActivity(intent);
				} catch (android.content.ActivityNotFoundException ex) {

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

		final String contentURL = getContentURL(selectedFile);
		if (contentURL != null && contentURL.length() > 0) {
			Uri uri = Uri.parse(contentURL);

			Intent intent = new Intent(Intent.ACTION_VIEW, uri);

			String extension = MimeTypeMap.getFileExtensionFromUrl(contentURL).toLowerCase(
					Locale.US);
			String mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
					extension);
			if (mimetype != null && tryLaunchWithMimeFirst) {
				intent.setType(mimetype);
			}

			final PackageManager packageManager = getActivity().getPackageManager();
			List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
					PackageManager.MATCH_DEFAULT_ONLY);
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "num intents " + list.size());
				for (ResolveInfo info : list) {
					Log.d(TAG,
							info.toString() + "/" + AndroidUtils.getComponentInfo(info));
				}
			}
			if (list.size() == 0) {
				// Intent will launch, but show message to the user:
				// "Opening web browser links is not supported"
				intent.setClass(getActivity(), VideoViewer.class);
			}
			if (list.size() == 1) {
				ResolveInfo info = list.get(0);
				ComponentInfo componentInfo = AndroidUtils.getComponentInfo(info);
				if (componentInfo != null
						&& "com.amazon.unifiedshare.actionchooser.BuellerShareActivity".equals(componentInfo.name)) {
					intent.setClass(getActivity(), VideoViewer.class);
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
						if (!tryLaunchWithMimeFirst) {
							intent.setType(mimetype);
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
			Map<?, ?> map = (Map<?, ?>) object;
			return map;
		}
		return null;
	}

	protected Map<?, ?> getFocusedFile() {
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			return null;
		}
		List<?> listFiles = MapUtils.getMapList(torrent, "files", null);
		long id = listview.getSelectedItemId();
		if (listFiles == null || id < 0 || id >= listFiles.size()) {
			return null;
		}
		Object object = listFiles.get((int) id);
		if (object instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) object;
			return map;
		}
		return null;
	}

	protected Map<?, ?> getSelectedFileStats() {
		Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			return null;
		}
		List<?> listFiles = MapUtils.getMapList(torrent, "fileStats", null);
		if (listFiles == null || selectedFileIndex < 0
				|| selectedFileIndex >= listFiles.size()) {
			return getSelectedFile();
		}
		Object object = listFiles.get(selectedFileIndex);
		if (object instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) object;
			return map;
		}
		return null;
	}

	protected boolean showContextualActions() {
		if (mActionMode != null) {
			Map<?, ?> selectedFile = getSelectedFile();
			String name = MapUtils.getMapString(selectedFile, "name", null);
			mActionMode.setSubtitle(name);

			mActionMode.invalidate();
			return false;
		}

		if (mCallback != null) {
			mCallback.setActionModeBeingReplaced(true);
		}
		ActionBarActivity activity = (ActionBarActivity) getActivity();
		if (activity == null) {
			return false;
		}
		// Start the CAB using the ActionMode.Callback defined above
		ActionMode am = activity.startSupportActionMode(mActionModeCallback);
		mActionMode = new ActionModeWrapper(am);

		mActionMode.setTitle(R.string.context_file_title);
		Map<?, ?> selectedFile = getSelectedFile();
		String name = MapUtils.getMapString(selectedFile, "name", null);
		mActionMode.setSubtitle(name);
		if (mCallback != null) {
			mCallback.setActionModeBeingReplaced(false);
		}
		return true;
	}

	public void finishActionMode() {
		if (mActionMode != null) {
			mActionMode.finish();
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
				Log.d(TAG, "TorrentListReceived, does not contain torrent #"
						+ torrentID);
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

	public void launchFile() {
		Map<?, ?> selectedFile = getSelectedFile();
		if (selectedFile == null) {
			selectedFile = getFocusedFile();
			if (selectedFile == null) {
				return;
			}
		}
		launchFile(selectedFile);
	}
}
