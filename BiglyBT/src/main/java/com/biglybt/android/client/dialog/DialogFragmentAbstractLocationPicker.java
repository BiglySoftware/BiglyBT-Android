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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.activity.DirectoryChooserActivity;
import com.biglybt.android.client.session.*;
import com.biglybt.android.client.spanbubbles.SpanBubbles;
import com.biglybt.android.util.FileUtils;
import com.biglybt.android.util.FileUtils.PathInfo;
import com.biglybt.android.util.PaulBurkeFileUtils;
import com.biglybt.android.widget.PreCachingLayoutManager;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public abstract class DialogFragmentAbstractLocationPicker
	extends DialogFragmentResized
{
	protected static final String TAG = "MoveDataDialog";

	static final String KEY_HISTORY = "history";

	static final String KEY_DEFAULT_DIR = "default_dir";

	protected static final boolean DEBUG = false;

	private static final int REQUEST_PATHCHOOSER = 3;

	@Thunk
	private EditText etLocation;

	private CheckBox cbRememberLocation;

	private ArrayList<String> history;

	@Thunk
	AlertDialog dialog;

	public interface LocationPickerListener
	{
		void locationChanged(String location);
	}

	@Thunk
	String currentDir;

	@Thunk
	String newLocation;

	private Button btnOk;

	@Thunk
	PathArrayAdapter adapter;

	private ProgressBar pb;

	private List<PathInfo> listPathInfos;

	public DialogFragmentAbstractLocationPicker() {
		setDialogWidthRes(R.dimen.dlg_movedata_width);
		setDialogHeightRes(R.dimen.dlg_movedata_height);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		Bundle args = getArguments();
		assert args != null;
		currentDir = args.getString(KEY_DEFAULT_DIR);
		history = args.getStringArrayList(KEY_HISTORY);

		Session session = SessionManager.findOrCreateSession(this, null);
		if (session == null) {
			AnalyticsTracker.getInstance(this).logError("session null", TAG);
			return super.onCreateDialog(savedInstanceState);
		}
		boolean isLocalCore = session.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_CORE;

		@LayoutRes
		int layoutID = isLocalCore ? R.layout.dialog_move_localdata
				: R.layout.dialog_move_data;

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				requireActivity(), layoutID);

		AlertDialog.Builder builder = alertDialogBuilder.builder;

		onCreateBuilder(alertDialogBuilder);

		// Add action buttons

		View view = alertDialogBuilder.view;
		btnOk = view.findViewById(R.id.ok);
		if (btnOk != null) {
			btnOk.setOnClickListener(v -> okClickedP());
		}

		Button btnCancel = view.findViewById(R.id.cancel);
		if (btnCancel != null) {
			btnCancel.setOnClickListener(v -> dismissDialog());
		}

		if (btnOk == null) {
			builder.setPositiveButton(android.R.string.ok,
					(dialog, id) -> okClickedP());
			builder.setNegativeButton(android.R.string.cancel,
					(dialog, id) -> dialog.cancel());
		}

		dialog = builder.create();
		setupWidgets(alertDialogBuilder.view);

		return dialog;
	}

	private void okClickedP() {
		Session session = SessionManager.findOrCreateSession(this, null);
		if (session == null) {
			return;
		}

		String location = getLocation();
		if (cbRememberLocation != null && cbRememberLocation.isChecked()) {
			if (history != null && !history.contains(location)) {
				history.add(0, location);
				session.moveDataHistoryChanged(history);
			}
		}
		okClicked(session, location);
	}

	protected abstract void okClicked(Session session, String location);

	protected abstract void onCreateBuilder(AlertDialogBuilder builder);

	@Override
	public void onStart() {
		super.onStart();
		Session session = SessionManager.findOrCreateSession(this, null);
		if (session == null) {
			this.dismissAllowingStateLoss();
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		RecyclerView lvAvailPaths = dialog.findViewById(R.id.movedata_avail_paths);
		if (lvAvailPaths != null) {
			getPositiveButton().setEnabled(newLocation != null);
		}
	}

	@Thunk
	Button getPositiveButton() {
		if (btnOk != null) {
			return btnOk;
		}
		return dialog.getButton(DialogInterface.BUTTON_POSITIVE);
	}

	protected void setupWidgets(@NonNull View view) {
		Resources resources = getResources();
		Context context = getContext();

		ArrayList<String> newHistory = history == null ? new ArrayList<>(1)
				: new ArrayList<>(history);

		if (currentDir != null && !newHistory.contains(currentDir)) {
			if (newHistory.size() > 1) {
				newHistory.add(1, currentDir);
			} else {
				newHistory.add(currentDir);
			}
		}

		etLocation = view.findViewById(R.id.movedata_editview);
		if (currentDir != null && etLocation != null) {
			etLocation.setText(currentDir);
		}

		cbRememberLocation = view.findViewById(R.id.movedata_remember);

		TextView tv = view.findViewById(R.id.movedata_currentlocation);
		if (tv != null) {
			CharSequence s = FileUtils.buildPathInfo(context,
					new File(currentDir)).getFriendlyName(context);

			tv.setText(AndroidUtils.fromHTML(resources,
					R.string.movedata_currentlocation, s));
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
					if (pathInfo == null) {
						return;
					}
					if (pathInfo instanceof PathInfoBrowser || pathInfo.isReadOnly) {
						FileUtils.openFolderChooser(
								DialogFragmentAbstractLocationPicker.this, pathInfo.file == null
										? currentDir : pathInfo.file.getAbsolutePath(),
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
			adapter = new PathArrayAdapter(view.getContext(), selectionListener);
			adapter.setMultiCheckModeAllowed(false);
			lvAvailPaths.setAdapter(adapter);

			AndroidUtilsUI.runOffUIThread(() -> {
				List<PathInfo> list = buildFolderList(
						DialogFragmentAbstractLocationPicker.this);
				AndroidUtilsUI.runOnUIThread(() -> {
					if (isRemoving() || isDetached()) {
						return;
					}
					if (pb != null) {
						pb.setVisibility(View.GONE);
					}
					listPathInfos = list;
					adapter.setItems(list, null, null);
				});
			});

		}
	}

	@NonNull
	private List<PathInfo> buildFolderList(@NonNull Fragment fragment) {
		List<PathInfo> list = new ArrayList<>();

		Context context = fragment.requireContext();
		Session session = SessionManager.findOrCreateSession(fragment, null);
		if (session == null) {
			return list;
		}
		SessionSettings sessionSettings = session.getSessionSettingsClone();
		if (sessionSettings == null) {
			return list;
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
			if (externalFilesDirs != null) {
				for (File externalFilesDir : externalFilesDirs) {
					if (externalFilesDir != null && externalFilesDir.exists()) {
						addPath(list, FileUtils.buildPathInfo(context, externalFilesDir));
					}
				}
			}
		}

		//noinspection deprecation
		File externalStorageDirectory = Environment.getExternalStorageDirectory();
		if (externalStorageDirectory != null && externalStorageDirectory.exists()) {
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

		String[] DIR_IDS = new String[] {
			Environment.DIRECTORY_DOWNLOADS,
			"Documents", //NON-NLS API19:	Environment.DIRECTORY_DOCUMENTS,
			Environment.DIRECTORY_MOVIES,
			Environment.DIRECTORY_MUSIC,
			Environment.DIRECTORY_PICTURES,
			Environment.DIRECTORY_PODCASTS
		};
		for (String id : DIR_IDS) {
			//noinspection deprecation
			File directory = Environment.getExternalStoragePublicDirectory(id);
			if (directory != null && directory.exists()) {
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
						// getPath is hidden, but present since at API 15, and is still present in 29
						// We could use getPathFile, but it's API 17
						//noinspection JavaReflectionMemberAccess
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

		if (numStorageVolumes == 0 && currentDir != null) {
			PathInfo pathInfo = FileUtils.buildPathInfo(new PathInfoBrowser(),
					context, new File(currentDir));
			addPath(list, pathInfo);
		}

		// Move Private Storage to bottom
		int end = list.size();
		for (int i = 0; i < end; i++) {
			PathInfo pathInfo = list.get(i);
			if (pathInfo != null && pathInfo.isPrivateStorage) {
				end--;
				//noinspection SuspiciousListRemoveInLoop
				list.remove(i);
				list.add(pathInfo);
			}
		}

		return list;
	}

	private static void addPath(@NonNull List<PathInfo> list,
			@NonNull PathInfo pathInfo) {
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

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		String moveTo = null;
		if (requestCode == REQUEST_PATHCHOOSER
				&& resultCode == Activity.RESULT_OK) {

			Uri uri = data.getData();
			if (uri != null) {
				// Persist access permissions.
				final int takeFlags = data.getFlags()
						& (Intent.FLAG_GRANT_READ_URI_PERMISSION
								| Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
				if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
					ContentResolver contentResolver = requireActivity().getContentResolver();
					contentResolver.takePersistableUriPermission(uri, takeFlags);
				}

				moveTo = PaulBurkeFileUtils.getPath(getActivity(), uri);
				if (DEBUG) {
					DocumentFile pickedDir = DocumentFile.fromTreeUri(requireContext(),
							uri);
					Log.d(TAG,
							"can R/W? " + pickedDir.canRead() + "/" + pickedDir.canWrite());

					// List all existing files inside picked directory
					for (DocumentFile file : pickedDir.listFiles()) {
						Log.d(TAG,
								"Found file " + file.getName() + " with size " + file.length());
					}
				}
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
			if (DEBUG) {
				Log.d(TAG, "File.canRW? " + file.canRead() + "/" + file.canRead());
			}
			if (FileUtils.canWrite(file)) {
				newLocation = moveTo;

				if (etLocation != null) {
					etLocation.setText(moveTo);
				}

				if (history != null && !history.contains(moveTo)) {
					history.add(history.size() > 0 ? 1 : 0, moveTo);
					Session session = SessionManager.findOrCreateSession(this, null);
					if (session != null) {
						session.moveDataHistoryChanged(history);
					}
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
			} // else { // TODO WARN
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	public String getLocation() {
		return etLocation == null ? newLocation : etLocation.getText().toString();
	}

	//////////////////////////////////////////////////////////////////////////////

	static class PathHolder
		extends FlexibleRecyclerViewHolder
	{

		@NonNull
		@Thunk
		final TextView tvPath;

		@NonNull
		@Thunk
		final TextView tvWarning;

		@NonNull
		@Thunk
		final TextView tvFree;

		@NonNull
		@Thunk
		final ImageView ivPath;

		PathHolder(@Nullable RecyclerSelectorInternal selector,
				@NonNull View rowView) {
			super(selector, rowView);

			tvPath = ViewCompat.requireViewById(rowView, R.id.path_row_text);
			tvWarning = ViewCompat.requireViewById(rowView, R.id.path_row_warning);
			tvFree = ViewCompat.requireViewById(rowView, R.id.path_row_free);
			ivPath = ViewCompat.requireViewById(rowView, R.id.path_row_image);
		}
	}

	public class PathArrayAdapter
		extends FlexibleRecyclerAdapter<PathArrayAdapter, PathHolder, PathInfo>
	{
		private final Context context;

		PathArrayAdapter(Context context,
				FlexibleRecyclerSelectionListener<PathArrayAdapter, PathHolder, PathInfo> listener) {
			super(TAG, listener);
			this.context = context;
		}

		@NonNull
		@Override
		public PathHolder onCreateFlexibleViewHolder(@NonNull ViewGroup parent,
				@NonNull LayoutInflater inflater, int viewType) {
			View rowView = AndroidUtilsUI.requireInflate(inflater,
					R.layout.row_path_selection, parent, false);

			return new PathHolder(this, rowView);
		}

		@Override
		public void onBindFlexibleViewHolder(@NonNull PathHolder holder,
				int position) {
			final PathInfo item = getItem(position);
			if (item == null) {
				return;
			}

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
						AndroidUtilsUI.getStyleColor(context,
								R.attr.login_textbubble_color),
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
				holder.tvFree.setText(item.storagePath);

				AndroidUtilsUI.runOffUIThread(() -> {
					String freeSpaceString = DisplayFormatters.formatByteCountToKiBEtc(
							item.file.getFreeSpace());

					AndroidUtilsUI.runOnUIThread(() -> {
						FragmentActivity activity = getActivity();
						if (adapter == null || activity == null || activity.isFinishing()) {
							return;
						}
						PathInfo currentPathInfo = adapter.getItem(position);
						if (currentPathInfo != item) {
							return;
						}

						String s = getString(R.string.x_space_free, freeSpaceString);
						holder.tvFree.setText(s + " - " + item.storagePath);
					});
				});
			}

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

	@Thunk
	static class PathInfoBrowser
		extends PathInfo
	{
	}
}
