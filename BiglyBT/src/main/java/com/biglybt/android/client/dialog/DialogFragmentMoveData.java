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

package com.biglybt.android.client.dialog;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.session.*;
import com.biglybt.android.client.spanbubbles.SpanBubbles;
import com.biglybt.android.util.FileUtils;
import com.biglybt.android.util.FileUtils.PathInfo;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.PaulBurkeFileUtils;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import android.app.Activity;
import android.app.Dialog;
import androidx.lifecycle.Lifecycle;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.*;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;

import net.rdrei.android.dirchooser.DirectoryChooserActivity;

public class DialogFragmentMoveData
	extends DialogFragmentResized
{
	private static final String TAG = "MoveDataDialog";

	private static final String KEY_HISTORY = "history";

	private static final String BUNDLEKEY_DEF_APPEND_NAME = "DefAppendName";

	private static final boolean DEBUG = false;

	public final static int REQUEST_PATHCHOOSER = 3;

	@Thunk
	EditText etLocation;

	private CheckBox cbRememberLocation;

	private long torrentId;

	private ArrayList<String> history;

	@Thunk
	AlertDialog dialog;

	private AlertDialogBuilder alertDialogBuilder;

	public interface DialogFragmentMoveDataListener
	{
		void locationChanged(String location);
	}

	private CheckBox cbAppendSubDir;

	private String torrentName;

	@Thunk
	String currentDownloadDir;

	private boolean appendName;

	private boolean isLocalCore;

	@LayoutRes
	private int layoutID;

	@Thunk
	String newLocation;

	private Button btnOk;

	private PathArrayAdapter adapter;

	private ProgressBar pb;

	private List<PathInfo> listPathInfos;

	public DialogFragmentMoveData() {
		setMinWidthPX(
				(int) (AndroidUtilsUI.getScreenWidthPx(BiglyBTApp.getContext()) * 0.9));
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (isLocalCore) {
			return;
		}

		boolean checked = cbRememberLocation != null
				&& cbRememberLocation.isChecked();
		boolean checkedSub = cbAppendSubDir != null && cbAppendSubDir.isChecked();
		String location = etLocation.getText().toString();

		// This mess is an attempt to rebuild the layout within the dialog
		// when the orientation changes.  Seems to work, but doesn't make sense
		ViewGroup viewGroup = (ViewGroup) alertDialogBuilder.view;
		//ViewGroup parent = (ViewGroup) viewGroup.getParent();
		viewGroup.removeAllViews();
		View view = View.inflate(dialog.getContext(), layoutID, viewGroup);
		dialog.setView(view);
		alertDialogBuilder = new AlertDialogBuilder(view,
				alertDialogBuilder.builder);
		setupWidgets(view);

		if (cbRememberLocation != null) {
			cbRememberLocation.setChecked(checked);
		}
		if (etLocation != null) {
			etLocation.setText(location);
		}
		if (cbAppendSubDir != null) {
			cbAppendSubDir.setChecked(checkedSub);
		}

		resize();
	}

	private void resize() {
		if (isLocalCore) {
			return;
		}
		// fill full width because we need all the room
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		if (metrics == null) {
			return;
		}
		Dialog dialog = getDialog();
		if (dialog == null) {
			return;
		}
		Window window = dialog.getWindow();
		if (window == null) {
			return;
		}
		try {
			window.setLayout(metrics.widthPixels, LayoutParams.WRAP_CONTENT);
		} catch (NullPointerException ignore) {
		}

		WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
		lp.copyFrom(window.getAttributes());
		lp.width = metrics.widthPixels; // WindowManager.LayoutParams.MATCH_PARENT;
		lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
		window.setAttributes(lp);

	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		Bundle args = getArguments();
		assert args != null;
		torrentName = args.getString(TransmissionVars.FIELD_TORRENT_NAME);
		torrentId = args.getLong(TransmissionVars.FIELD_TORRENT_ID);
		currentDownloadDir = args.getString(
				TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR);
		appendName = args.getBoolean(BUNDLEKEY_DEF_APPEND_NAME, true);
		history = args.getStringArrayList(KEY_HISTORY);

		Session session = SessionManager.findOrCreateSession(this, null);
		if (session == null) {
			AnalyticsTracker.getInstance(this).logError("session null", TAG);
			return super.onCreateDialog(savedInstanceState);
		}
		isLocalCore = session.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_CORE;

		layoutID = isLocalCore
				? AndroidUtils.isTV(requireContext())
						? R.layout.dialog_move_localdata_tv : R.layout.dialog_move_localdata
				: R.layout.dialog_move_data;

		alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(getActivity(),
				layoutID);

		AlertDialog.Builder builder = alertDialogBuilder.builder;

		builder.setTitle(R.string.action_sel_relocate);

		// Add action buttons

		View view = alertDialogBuilder.view;
		btnOk = view.findViewById(R.id.ok);
		if (btnOk != null) {
			btnOk.setOnClickListener(v -> {
				moveData();
				DialogFragmentMoveData.this.getDialog().dismiss();
			});
		}

		Button btnCancel = view.findViewById(R.id.cancel);
		if (btnCancel != null) {
			btnCancel.setOnClickListener(
					v -> DialogFragmentMoveData.this.getDialog().dismiss());
		}

		if (btnOk == null) {
			builder.setPositiveButton(android.R.string.ok,
					(dialog, id) -> moveData());
			builder.setNegativeButton(android.R.string.cancel,
					(dialog, id) -> DialogFragmentMoveData.this.getDialog().cancel());
		}

		dialog = builder.create();
		setupWidgets(alertDialogBuilder.view);

		return dialog;
	}

	@Override
	public void onStart() {
		super.onStart();
		Session session = SessionManager.findOrCreateSession(this, null);
		if (session == null) {
			this.dismissAllowingStateLoss();
		}
	}

	@Thunk
	void moveData() {
		Session session = SessionManager.findOrCreateSession(this, null);
		if (session == null) {
			return;
		}

		String moveTo = etLocation == null ? newLocation
				: etLocation.getText().toString();
		if (cbRememberLocation != null && cbRememberLocation.isChecked()) {
			if (history != null && !history.contains(moveTo)) {
				history.add(0, moveTo);
				session.moveDataHistoryChanged(history);
			}
		}
		if (cbAppendSubDir != null && cbAppendSubDir.isChecked()) {
			char sep = moveTo.length() > 2 && moveTo.charAt(2) == '\\' ? '\\' : '/';
			moveTo += sep + torrentName;
		}
		session.torrent.moveDataTo(torrentId, moveTo);
		FragmentActivity activity = getActivity();
		if (activity instanceof DialogFragmentMoveDataListener) {
			((DialogFragmentMoveDataListener) activity).locationChanged(moveTo);
		}
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		resize();
	}

	@Override
	public void onResume() {
		super.onResume();

		RecyclerView lvAvailPaths = dialog.findViewById(R.id.movedata_avail_paths);
		if (lvAvailPaths != null) {
			getPositiveButton().setEnabled(newLocation != null);
		}

		resize();
	}

	private Button getPositiveButton() {
		if (btnOk != null) {
			return btnOk;
		}
		return dialog.getButton(DialogInterface.BUTTON_POSITIVE);
	}

	private void setupWidgets(View view) {
		Resources resources = getResources();
		Context context = getContext();
		if (currentDownloadDir != null
				&& currentDownloadDir.endsWith(torrentName)) {
			currentDownloadDir = currentDownloadDir.substring(0,
					currentDownloadDir.length() - torrentName.length() - 1);
			appendName = true;
		}

		ArrayList<String> newHistory = history == null ? new ArrayList<>(1)
				: new ArrayList<>(history);

		if (currentDownloadDir != null
				&& !newHistory.contains(currentDownloadDir)) {
			if (newHistory.size() > 1) {
				newHistory.add(1, currentDownloadDir);
			} else {
				newHistory.add(currentDownloadDir);
			}
		}

		etLocation = view.findViewById(R.id.movedata_editview);
		if (currentDownloadDir != null && etLocation != null) {
			etLocation.setText(currentDownloadDir);
		}

		ImageButton btnBrowser = view.findViewById(R.id.movedata_btn_editdir);
		if (btnBrowser != null) {
			if (isLocalCore) {
				btnBrowser.setOnClickListener(
						v -> FileUtils.openFolderChooser(DialogFragmentMoveData.this,
								currentDownloadDir, REQUEST_PATHCHOOSER));
			} else {
				btnBrowser.setVisibility(View.GONE);
			}
		}

		cbRememberLocation = view.findViewById(R.id.movedata_remember);

		TextView tv = view.findViewById(R.id.movedata_label);
		if (tv != null) {
			tv.setText(AndroidUtils.fromHTML(resources, R.string.movedata_label,
					torrentName));
		}

		tv = view.findViewById(R.id.movedata_currentlocation);
		if (tv != null) {
			CharSequence s = FileUtils.buildPathInfo(context,
					new File(currentDownloadDir)).getFriendlyName(context);

			tv.setText(AndroidUtils.fromHTML(resources,
					R.string.movedata_currentlocation, s));
		}

		cbAppendSubDir = view.findViewById(R.id.movedata_appendname);
		if (cbAppendSubDir != null) {
			cbAppendSubDir.setChecked(appendName);
			cbAppendSubDir.setText(AndroidUtils.fromHTML(resources,
					R.string.movedata_place_in_subfolder, torrentName));
		}

		pb = view.findViewById(R.id.movedata_pb);

		ListView lvHistory = view.findViewById(R.id.movedata_historylist);
		if (lvHistory != null) {
			ArrayAdapter<String> adapter = new ArrayAdapter<>(view.getContext(),
					R.layout.list_view_small_font, newHistory);
			lvHistory.setAdapter(adapter);

			lvHistory.setOnItemClickListener((parent, view1, position, id) -> {
				Object item = parent.getItemAtPosition(position);

				if (item instanceof String) {
					etLocation.setText((String) item);
				}
			});
		}

		final FlexibleRecyclerView lvAvailPaths = view.findViewById(
				R.id.movedata_avail_paths);
		if (lvAvailPaths != null) {
			lvAvailPaths.setLayoutManager(new PreCachingLayoutManager(context));

			FlexibleRecyclerSelectionListener<PathArrayAdapter, PathHolder, PathInfo> selectionListener = new FlexibleRecyclerSelectionListener<PathArrayAdapter, PathHolder, PathInfo>() {
				@Override
				public void onItemClick(PathArrayAdapter adapter, int position) {
					PathInfo pathInfo = adapter.getItem(position);
					if (pathInfo instanceof PathInfoBrowser || pathInfo.isReadOnly) {
						FileUtils.openFolderChooser(
								DialogFragmentMoveData.this, pathInfo.file == null
										? currentDownloadDir : pathInfo.file.getAbsolutePath(),
								REQUEST_PATHCHOOSER);
					} else {
						getPositiveButton().setEnabled(true);
						newLocation = pathInfo.file.getAbsolutePath();
						getPositiveButton().requestFocus();
					}
				}

				@Override
				public boolean onItemLongClick(PathArrayAdapter adapter, int position) {
					return false;
				}

				@Override
				public void onItemSelected(PathArrayAdapter adapter, int position,
						boolean isChecked) {

				}

				@Override
				public void onItemCheckedChanged(PathArrayAdapter adapter,
						PathInfo item, boolean isChecked) {

				}
			};
			adapter = new PathArrayAdapter(view.getContext(), getLifecycle(),
					selectionListener);
			lvAvailPaths.setAdapter(adapter);

			new AsyncTask<View, Object, List<PathInfo>>() {
				View view;

				@Override
				protected List<PathInfo> doInBackground(View... views) {
					this.view = views[0];
					return buildFolderList(view);
				}

				@Override
				protected void onPostExecute(List<PathInfo> list) {
					if (pb != null) {
						pb.setVisibility(View.GONE);
					}
					listPathInfos = list;
					adapter.setItems(list, null, null);
				}
			}.execute(view);

		}
	}

	private List<PathInfo> buildFolderList(View view) {
		List<PathInfo> list = new ArrayList<>();

		Context context = view.getContext();
		Session session = SessionManager.findOrCreateSession(
				DialogFragmentMoveData.this, null);
		if (session == null) {
			return null;
		}
		SessionSettings sessionSettings = session.getSessionSettingsClone();
		if (sessionSettings == null) {
			return null;
		}
		String downloadDir = sessionSettings.getDownloadDir();
		if (downloadDir != null) {
			File file = new File(downloadDir);
			addPath(list, FileUtils.buildPathInfo(context, file));
		}

		if (history != null && history.size() > 0) {
			for (String loc : history) {
				File file = new File(loc);
				addPath(list, FileUtils.buildPathInfo(context, file));
			}
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			File[] externalFilesDirs = context.getExternalFilesDirs(null);
			for (File externalFilesDir : externalFilesDirs) {
				if (externalFilesDir != null && externalFilesDir.exists()) {
					addPath(list, FileUtils.buildPathInfo(context, externalFilesDir));
				}
			}
		}

		File externalStorageDirectory = Environment.getExternalStorageDirectory();
		if (externalStorageDirectory.exists()) {
			addPath(list, FileUtils.buildPathInfo(context, externalStorageDirectory));
		}

		String secondaryStorage = System.getenv("SECONDARY_STORAGE"); //NON-NLS
		if (secondaryStorage != null) {
			String[] split = secondaryStorage.split(File.pathSeparator);
			for (String dir : split) {
				File f = new File(dir);
				if (f.exists()) {
					addPath(list, FileUtils.buildPathInfo(context, f));
				}
			}
		}

		int posManual = list.size() - 1;

		String[] DIR_IDS = new String[] {
			Environment.DIRECTORY_DOWNLOADS,
			"Documents", //NON-NLS API19:	Environment.DIRECTORY_DOCUMENTS,
			Environment.DIRECTORY_MOVIES,
			Environment.DIRECTORY_MUSIC,
			Environment.DIRECTORY_PICTURES,
			Environment.DIRECTORY_PODCASTS
		};
		for (String id : DIR_IDS) {
			File directory = Environment.getExternalStoragePublicDirectory(id);
			if (directory.exists()) {
				addPath(list, FileUtils.buildPathInfo(context, directory));
			}
		}

		int numStorageVolumes = 0;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			StorageManager sm = (StorageManager) context.getSystemService(
					Context.STORAGE_SERVICE);
			if (sm != null) {
				List<StorageVolume> storageVolumes = sm.getStorageVolumes();
				for (StorageVolume volume : storageVolumes) {
					if (DEBUG) {
						Log.d(TAG, "buildFolderList: volume = " + volume.toString()
								+ "; state=" + volume.getState());
					}
					try {
						Method mGetPath = volume.getClass().getMethod("getPath");
						Object oPath = mGetPath.invoke(volume);
						if (oPath instanceof String) {
							String path = (String) oPath;
							PathInfo pathInfo = FileUtils.buildPathInfo(new PathInfoBrowser(),
									context, new File(path));
							addPath(list, pathInfo);
							numStorageVolumes++;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}

		if (numStorageVolumes == 0) {
			PathInfo pathInfo = FileUtils.buildPathInfo(new PathInfoBrowser(),
					context, new File(currentDownloadDir));
			addPath(list, pathInfo);
		}

		// Move Private Storage to bottom
		int end = list.size();
		for (int i = 0; i < end; i++) {
			PathInfo pathInfo = list.get(i);
			if (pathInfo.isPrivateStorage) {
				end--;
				list.remove(i);
				list.add(pathInfo);
			}
		}

		return list;
	}

	private static void addPath(List<PathInfo> list, PathInfo pathInfo) {
		if (DEBUG) {
			Log.d(TAG, "addPath: " + pathInfo.file);
		}
		if (!(pathInfo instanceof PathInfoBrowser) && pathInfo.file != null) {
			for (PathInfo info : list) {
				if (info.file == null) {
					return;
				}
				if (info.file.equals(pathInfo.file)) {
					return;
				}
			}
		}
		list.add(pathInfo);
	}

	@SuppressWarnings("rawtypes")
	public static void openMoveDataDialog(Map mapTorrent, Session session,
			FragmentManager fm) {
		DialogFragmentMoveData dlg = new DialogFragmentMoveData();
		Bundle bundle = new Bundle();
		if (mapTorrent == null) {
			return;
		}

		bundle.putLong(TransmissionVars.FIELD_TORRENT_ID,
				MapUtils.getMapLong(mapTorrent, TransmissionVars.FIELD_TORRENT_ID, -1));
		bundle.putString(TransmissionVars.FIELD_TORRENT_NAME,
				"" + mapTorrent.get(TransmissionVars.FIELD_TORRENT_NAME));
		int numFiles = MapUtils.getMapInt(mapTorrent,
				TransmissionVars.FIELD_TORRENT_FILE_COUNT, 0);
		bundle.putBoolean(BUNDLEKEY_DEF_APPEND_NAME, numFiles > 1);
		bundle.putString(SessionManager.BUNDLE_KEY,
				session.getRemoteProfile().getID());

		SessionSettings sessionSettings = session.getSessionSettingsClone();

		String defaultDownloadDir = sessionSettings == null ? null
				: sessionSettings.getDownloadDir();
		String downloadDir = TorrentUtils.getSaveLocation(session, mapTorrent);
		bundle.putString(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR, downloadDir);

		List<String> saveHistory = session.getRemoteProfile().getSavePathHistory();

		ArrayList<String> history = new ArrayList<>(saveHistory.size() + 1);
		if (defaultDownloadDir != null) {
			history.add(defaultDownloadDir);
		}

		for (String s : saveHistory) {
			if (!history.contains(s)) {
				history.add(s);
			}
		}
		bundle.putStringArrayList(KEY_HISTORY, history);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		String moveTo = null;
		if (requestCode == REQUEST_PATHCHOOSER
				&& resultCode == Activity.RESULT_OK) {

			Uri uri = data.getData();
			if (uri != null) {
				moveTo = PaulBurkeFileUtils.getPath(getActivity(), uri);
			}
		}
		if (requestCode == REQUEST_PATHCHOOSER
				&& resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED) {
			moveTo = data.getStringExtra(
					DirectoryChooserActivity.RESULT_SELECTED_DIR);
		}

		if (DEBUG) {
			Log.d(TAG, "onActivityResult: " + moveTo);
		}
		if (moveTo != null) {
			File file = new File(moveTo);
			if (FileUtils.canWrite(file)) {
				newLocation = moveTo;

				if (etLocation != null) {
					etLocation.setText(moveTo);
				}

				if (history != null && !history.contains(moveTo)) {
					history.add(history.size() > 0 ? 1 : 0, moveTo);
					Session session = SessionManager.findOrCreateSession(this, null);
					session.moveDataHistoryChanged(history);
				}

				if (adapter != null && listPathInfos != null) {
					boolean exists = false;
					int[] checkedItemPositions = adapter.getCheckedItemPositions();
					int checkedPos = checkedItemPositions.length == 0 ? -1
							: checkedItemPositions[0];
					Button btnOk = getPositiveButton();
					for (int i = 0; i < listPathInfos.size(); i++) {
						PathInfo pathInfo = listPathInfos.get(i);
						if (pathInfo.file.getAbsolutePath().equals(moveTo)) {
							if (checkedPos != i) {
								adapter.setItemChecked(checkedPos, false);
								adapter.setItemChecked(i, true);
								adapter.getRecyclerView().scrollToPosition(i);
								adapter.setItemSelected(i);
								btnOk.setEnabled(true);
								btnOk.requestFocus();
							}
							exists = true;
						}
					}
					if (!exists) {
						listPathInfos.add(0,
								FileUtils.buildPathInfo(requireContext(), file));
						adapter.getRecyclerView().scrollToPosition(0);
						adapter.setItems(listPathInfos, null, null);
						adapter.setItemChecked(0, true);
						adapter.setItemSelected(0);
						btnOk.setEnabled(true);
						btnOk.requestFocus();
					}
				}
			} else {
				// TODO warn
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	public class PathHolder
		extends FlexibleRecyclerViewHolder
	{

		private final TextView tvPath;

		private final TextView tvWarning;

		private final TextView tvFree;

		private final ImageView ivPath;

		public PathHolder(@Nullable RecyclerSelectorInternal selector,
				View rowView) {
			super(selector, rowView);

			tvPath = rowView.findViewById(R.id.path_row_text);
			tvWarning = rowView.findViewById(R.id.path_row_warning);
			tvFree = rowView.findViewById(R.id.path_row_free);
			ivPath = rowView.findViewById(R.id.path_row_image);
		}
	}

	public class PathArrayAdapter
		extends FlexibleRecyclerAdapter<PathArrayAdapter, PathHolder, PathInfo>
	{
		private final Context context;

		public PathArrayAdapter(Context context, Lifecycle lifecycle,
				FlexibleRecyclerSelectionListener<PathArrayAdapter, PathHolder, PathInfo> listener) {
			super(TAG, lifecycle, listener);
			this.context = context;
		}

		@Override
		public PathHolder onCreateFlexibleViewHolder(ViewGroup parent,
				int viewType) {
			final Context context = parent.getContext();
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);

			View rowView = inflater.inflate(R.layout.row_path_selection, parent,
					false);

			return new PathHolder(this, rowView);
		}

		@Override
		public void onBindFlexibleViewHolder(PathHolder holder, int position) {
			final PathInfo item = getItem(position);

			if (item.isReadOnly) {
				holder.itemView.setAlpha(0.75f);
			} else {
				holder.itemView.setAlpha(1);
			}

			CharSequence friendlyName = item.getFriendlyName(context);
			if (item instanceof PathInfoBrowser || item.isReadOnly) {
				String text = getString(R.string.browse_dir, friendlyName);

				Context context = requireContext();
				SpanBubbles.setSpanBubbles(holder.tvPath, text, "|",
						AndroidUtilsUI.getStyleColor(context, R.attr.login_text_color),
						AndroidUtilsUI.getStyleColor(context,
								R.attr.login_textbubble_color),
						AndroidUtilsUI.getStyleColor(context, R.attr.login_text_color),
						null);
			} else {
				holder.tvPath.setText(friendlyName);
			}

			holder.ivPath.setImageResource(
					item.isRemovable ? R.drawable.ic_sd_storage_gray_24dp
							: R.drawable.ic_folder_gray_24dp);
			if (item.file == null) {
				holder.tvFree.setText("");
			} else {
				String freeSpaceString = DisplayFormatters.formatByteCountToKiBEtc(
						item.file.getFreeSpace());
				String s = getString(R.string.x_space_free, freeSpaceString);
				holder.tvFree.setText(s + " - " + item.storagePath);
			}

			if (holder.tvWarning != null) {
				String s = "";
				if (item.isPrivateStorage) {
					s = getString(R.string.private_internal_storage_warning);
				}
				if (item.isReadOnly) {
					s = getString(R.string.read_only);
				}
				holder.tvWarning.setText(s);
				holder.tvWarning.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
			}
		}
	}

	public class PathInfoBrowser
		extends PathInfo
	{

	}
}
