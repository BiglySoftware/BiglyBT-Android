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
import android.content.*;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.biglybt.android.TargetFragmentFinder;
import com.biglybt.android.adapter.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.activity.DirectoryChooserActivity;
import com.biglybt.android.client.session.*;
import com.biglybt.android.client.spanbubbles.SpanBubbles;
import com.biglybt.android.util.FileUtils;
import com.biglybt.android.util.PathInfo;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public abstract class DialogFragmentAbstractLocationPicker
	extends DialogFragmentResized
{
	static final String TAG = "MoveDataDialog";

	static final String KEY_HISTORY = "history";

	static final String KEY_DEFAULT_DIR = "default_dir";

	static final String KEY_CALLBACK_ID = "cb";

	static final boolean DEBUG = false;

	private static final int REQUEST_PATHCHOOSER = 3;

	@Thunk
	private EditText etLocation;

	private CheckBox cbRememberLocation;

	private ArrayList<String> history;

	@Thunk
	AlertDialog dialog;

	public interface LocationPickerListener
	{
		void locationChanged(String callbackID, @NonNull PathInfo location);
	}

	@Thunk
	String currentDir;

	@Thunk
	PathInfo newLocation;

	private Button btnOk;

	@Thunk
	PathArrayAdapter adapter;

	private ProgressBar pb;

	private List<PathInfo> listPathInfos;

	private String callbackID;

	private Button btnBrowse;

	private View dialogView;

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
		callbackID = args.getString(KEY_CALLBACK_ID);
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

		dialogView = alertDialogBuilder.view;
		btnOk = dialogView.findViewById(R.id.ok);
		if (btnOk != null) {
			btnOk.setOnClickListener(v -> okClickedP());
		}

		Button btnCancel = dialogView.findViewById(R.id.cancel);
		if (btnCancel != null) {
			btnCancel.setOnClickListener(v -> dismissDialog());
		}

		View.OnClickListener onBrowseClicked = v -> {
			String initialPath = null;
			if (adapter != null) {
				PathInfo item = adapter.getSelectedItem();
				if (item != null) {
					initialPath = item.fullPath;
				}
			}
			FileUtils.openFolderChooser(DialogFragmentAbstractLocationPicker.this,
					initialPath, REQUEST_PATHCHOOSER);
		};

		btnBrowse = dialogView.findViewById(R.id.browse);
		if (btnBrowse != null) {
			btnBrowse.setOnClickListener(onBrowseClicked);
		}

		if (btnOk == null) {
			builder.setPositiveButton(android.R.string.ok,
					(dialog, id) -> okClickedP());
			builder.setNegativeButton(android.R.string.cancel,
					(dialog, id) -> dialog.cancel());
			builder.setNeutralButton(R.string.button_browse, null);
		}

		dialog = builder.create();
		if (btnOk == null) {
			// Prevent Neutral button from closing dialog
			dialog.setOnShowListener(di -> {
				final Button btnNeutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
				if (btnNeutral != null) {
					btnNeutral.setOnClickListener(
							v -> onBrowseClicked.onClick(btnNeutral));
				}
			});
		}
		setupWidgets(alertDialogBuilder.view);

		return dialog;
	}

	private void okClickedP() {
		Session session = SessionManager.findOrCreateSession(this, null);
		if (session == null) {
			return;
		}

		PathInfo location = getLocation();
		if (cbRememberLocation != null && cbRememberLocation.isChecked()) {
			if (history != null && location != null
					&& !history.contains(location.fullPath)) {
				synchronized (history) {
					history.add(0, location.fullPath);
				}
				session.moveDataHistoryChanged(history);
			}
		}
		okClicked(session, location);
	}

	protected abstract void okClicked(Session session, PathInfo pathInfo);

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
		if (dialog == null) {
			return;
		}

		RecyclerView lvAvailPaths = dialog.findViewById(R.id.movedata_avail_paths);
		if (lvAvailPaths != null) {
			getPositiveButton().setEnabled(newLocation != null);
		}
	}

	@Thunk
	@NonNull
	Button getPositiveButton() {
		if (btnOk != null) {
			return btnOk;
		}
		if (dialog == null) {
			throw new IllegalStateException();
		}
		Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
		if (button == null) {
			throw new IllegalStateException();
		}
		return button;
	}

	@Thunk
	@NonNull
	Button getBrowseButton() {
		if (btnBrowse != null) {
			return btnBrowse;
		}
		if (dialog == null) {
			throw new IllegalStateException();
		}
		Button button = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
		if (button == null) {
			throw new IllegalStateException();
		}
		return button;
	}

	@UiThread
	void setupWidgets(@NonNull View view) {
		Resources resources = getResources();
		Context context = requireContext();

		ArrayList<String> newHistory = history == null ? new ArrayList<>(1)
				: new ArrayList<>(history);

		if (currentDir != null && currentDir.length() > 0
				&& !newHistory.contains(currentDir)) {
			if (newHistory.size() > 1) {
				newHistory.add(1, currentDir);
			} else {
				newHistory.add(currentDir);
			}
		}

		etLocation = view.findViewById(R.id.movedata_editview);
		if (currentDir != null && currentDir.length() > 0 && etLocation != null) {
			etLocation.setText(currentDir);
		}

		cbRememberLocation = view.findViewById(R.id.movedata_remember);

		TextView tv = view.findViewById(R.id.movedata_currentlocation);
		// Only exists in view for local core 
		if (tv != null) {
			if (currentDir == null || currentDir.isEmpty()) {
				tv.setText("");
			} else {
				OffThread.runOffUIThread(() -> {
					PathInfo pathInfo = PathInfo.buildPathInfo(currentDir);
					CharSequence s = pathInfo.getFriendlyName();

					OffThread.runOnUIThread(this, false,
							a -> tv.setText(AndroidUtils.fromHTML(resources,
									R.string.movedata_currentlocation, s)));
				});
			}
		}

		pb = view.findViewById(R.id.movedata_pb);

		ListView lvHistory = view.findViewById(R.id.movedata_historylist);
		if (lvHistory != null) {
			ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
					R.layout.list_view_small_font, newHistory);
			lvHistory.setAdapter(adapter);

			lvHistory.setOnItemClickListener((parent, view1, position, id) -> {
				Object item = parent.getItemAtPosition(position);

				if ((item instanceof String) && etLocation != null) {
					etLocation.setText((String) item);
				}
			});
		}

		final FlexibleRecyclerView lvAvailPaths = view.findViewById(
				R.id.movedata_avail_paths);
		// Only exists in view for local core 
		if (lvAvailPaths != null) {
			lvAvailPaths.setLayoutManager(new LinearLayoutManager(requireContext()));
			lvAvailPaths.setFastScrollEnabled(false);

			FlexibleRecyclerSelectionListener<PathArrayAdapter, PathHolder, PathInfo> selectionListener = new FlexibleRecyclerSelectionListener<PathArrayAdapter, PathHolder, PathInfo>() {
				@Override
				public void onItemClick(PathArrayAdapter adapter, int position) {
					PathInfo pathInfo = adapter.getItem(position);
					if (pathInfo == null) {
						return;
					}
					updateItemSelected(pathInfo, true);
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
			adapter = new PathArrayAdapter(selectionListener);
			adapter.setMultiCheckModeAllowed(false);
			adapter.setHasStableIds(true);
			lvAvailPaths.setAdapter(adapter);

			OffThread.runOffUIThread(() -> {
				List<PathInfo> list = buildFolderList(this);
				Context offThreadContext = getContext();
				if (offThreadContext == null) {
					// detached
					return;
				}
				int numOSFolderChoosers = FileUtils.numOSFolderChoosers(
						offThreadContext);

				OffThread.runOnUIThread(() -> {
					if (isRemoving() || isDetached()) {
						return;
					}
					if (pb != null) {
						pb.setVisibility(View.GONE);
					}
					if (numOSFolderChoosers == 0) {
						TextView tvWarning = dialogView.findViewById(R.id.no_saf_warning);
						if (tvWarning != null) {
							tvWarning.setVisibility(View.VISIBLE);
						}
					}
					int selectPos = -1;
					if (list != null && newLocation != null) {
						for (int i = 0; i < list.size(); i++) {
							PathInfo iterPathInfo = list.get(i);
							// Unknown if File.equals takes time, but we can't calculate
							// selectPos until we are about to build adapter, otherwise
							// changes from onActivityResult may not apply if it's run
							// before us
							if (newLocation.equals(iterPathInfo)) {
								selectPos = i;
								break;
							}
						}
					}
					if (selectPos < 0 && newLocation != null) {
						list.add(0, newLocation);
						selectPos = 0;
					}

					listPathInfos = list;
					if (selectPos >= 0) {
						int finalSelectPos = selectPos;
						adapter.addOnSetItemsCompleteListener(
								new FlexibleRecyclerAdapter.OnSetItemsCompleteListener<PathArrayAdapter>() {
									@Override
									public void onSetItemsComplete(PathArrayAdapter a) {
										a.setItemChecked(finalSelectPos, true);
										a.getRecyclerView().scrollToPosition(finalSelectPos);
										a.setItemSelected(finalSelectPos);
										updateItemSelected(newLocation, true);
										adapter.removeOnSetItemsCompleteListener(this);
									}
								});
					} else {
						getPositiveButton().setEnabled(false);
					}
					adapter.setItems(list, null, null);
				});
			});

		}
	}

	@UiThread
	@Thunk
	void updateItemSelected(PathInfo pathInfo, boolean setFocus) {
		OffThread.runOffUIThread(() -> {
			boolean isReadOnly = pathInfo.isReadOnly();
			OffThread.runOnUIThread(this, false, a -> {
				if (isReadOnly) {
					getPositiveButton().setEnabled(false);
					if (setFocus) {
						getBrowseButton().requestFocus();
					}
				} else {
					getPositiveButton().setEnabled(true);
					newLocation = pathInfo;
					if (setFocus) {
						getPositiveButton().requestFocus();
					}
				}
				itemSelected(pathInfo);
			});
		});
	}

	void itemSelected(PathInfo pathInfo) {
	}

	@NonNull
	@WorkerThread
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
			addPath(list, PathInfo.buildPathInfo(downloadDir));
		}

		if (history != null && history.size() > 0) {
			synchronized (history) {
				for (String loc : history) {
					addPath(list, PathInfo.buildPathInfo(loc));
				}
			}
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			File[] externalFilesDirs = context.getExternalFilesDirs(null);
			if (externalFilesDirs != null) {
				for (File externalFilesDir : externalFilesDirs) {
					if (externalFilesDir != null && externalFilesDir.exists()) {
						addPath(list, PathInfo.buildPathInfo(externalFilesDir));
					}
				}
			}
		}

		File externalStorageDirectory = Environment.getExternalStorageDirectory();
		if (externalStorageDirectory != null && externalStorageDirectory.exists()) {
			addPath(list, PathInfo.buildPathInfo(externalStorageDirectory));
		}

		String secondaryStorage = System.getenv("SECONDARY_STORAGE"); //NON-NLS
		if (secondaryStorage != null) {
			String[] split = secondaryStorage.split(File.pathSeparator);
			for (String dir : split) {
				if (new File(dir).exists()) {
					addPath(list, PathInfo.buildPathInfo(dir));
				}
			}
		}

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
			ContentResolver contentResolver = BiglyBTApp.getContext().getContentResolver();
			List<UriPermission> persistedUriPermissions = contentResolver.getPersistedUriPermissions();
			for (UriPermission uriPermission : persistedUriPermissions) {
				if (uriPermission.isWritePermission()
						&& uriPermission.isReadPermission()) {
					addPath(list,
							PathInfo.buildPathInfo(uriPermission.getUri().toString()));
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
			File directory = Environment.getExternalStoragePublicDirectory(id);
			if (directory != null && directory.exists()) {
				addPath(list, PathInfo.buildPathInfo(directory));
			}
		}

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
							PathInfo pathInfo = PathInfo.buildPathInfo(path);
							addPath(list, pathInfo);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
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

	@WorkerThread
	private void addPath(@NonNull List<PathInfo> list,
			@NonNull PathInfo pathInfo) {
		if (getContext() == null) {
			// dialog detached, don't add or do disk IO
			return;
		}
		boolean contains = list.contains(pathInfo);
		if (DEBUG) {
			Log.d(TAG, (contains ? "(skipped) " : "") + "addPath: " + pathInfo.file);
		}
		if (contains || !pathInfo.exists()) {
			return;
		}
		// Force isReadOnly check on worker thread. Subsequent calls will be cached
		pathInfo.isReadOnly();
		list.add(pathInfo);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		String chosenPathString = null;
		if (requestCode == REQUEST_PATHCHOOSER && resultCode == Activity.RESULT_OK
				&& data != null) {
			// Result from Android OS File Picker, which is only used on local core

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
				chosenPathString = uri.toString();
			}
		}

		// RESULT_CODE_DIR_SELECTED is from DirectoryChooser which only returns
		// file paths (no "content://" strings)
		if (requestCode == REQUEST_PATHCHOOSER
				&& resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED
				&& data != null) {
			chosenPathString = data.getStringExtra(
					DirectoryChooserActivity.RESULT_SELECTED_DIR);
		}

		if (DEBUG) {
			Log.d(TAG, "onActivityResult: " + chosenPathString);
		}

		if (chosenPathString == null) {
			super.onActivityResult(requestCode, resultCode, data);
			return;
		}

		String s = chosenPathString;
		OffThread.runOffUIThread(() -> setChosenPathExternal(s));
	}

	@WorkerThread
	private void setChosenPathExternal(String path) {

		newLocation = PathInfo.buildPathInfo(path);

		if (newLocation.isReadOnly() && !FileUtils.canUseSAF(requireContext())) {
			// TODO: Warn
			return;
		}

		if (history != null && !history.contains(newLocation.fullPath)) {
			synchronized (history) {
				history.add(history.size() > 0 ? 1 : 0, newLocation.fullPath);
			}
			Session session = SessionManager.findOrCreateSession(this, null);
			if (session != null) {
				session.moveDataHistoryChanged(history);
			}
		}

		OffThread.runOnUIThread(this, false, activity -> {
			if (etLocation != null) {
				etLocation.setText(newLocation.fullPath);
			}

			if (adapter == null || listPathInfos == null) {
				return;
			}

			boolean exists = false;
			int[] checkedItemPositions = adapter.getCheckedItemPositions();
			int checkedPos = checkedItemPositions.length == 0 ? -1
					: checkedItemPositions[0];
			for (int i = 0; i < listPathInfos.size(); i++) {
				PathInfo iterPathInfo = listPathInfos.get(i);
				if (newLocation.equals(iterPathInfo)) {
					if (checkedPos != i) {
						adapter.setItemChecked(checkedPos, false);
						adapter.setItemChecked(i, true);
						adapter.getRecyclerView().scrollToPosition(i);
						adapter.setItemSelected(i);
						updateItemSelected(newLocation, true);
					}
					exists = true;
				}
			}
			if (!exists) {
				listPathInfos.add(0, newLocation);
				adapter.getRecyclerView().scrollToPosition(0);
				adapter.setItems(listPathInfos, null, null);
				adapter.clearChecked();
				adapter.setItemChecked(0, true);
				adapter.setItemSelected(0);
				updateItemSelected(newLocation, true);
			}
		});
	}

	private PathInfo getLocation() {
		if (etLocation != null) {
			return new PathInfo(etLocation.getText().toString());
		}
		return newLocation;
	}

	void triggerLocationChanged(@NonNull PathInfo newLocation) {
		LocationPickerListener listener = new TargetFragmentFinder<LocationPickerListener>(
				LocationPickerListener.class).findTarget(this, getContext());
		if (listener != null) {
			listener.locationChanged(callbackID, newLocation);
		}
	}

	//////////////////////////////////////////////////////////////////////////////

	static class PathHolder
		extends FlexibleRecyclerViewHolder<PathHolder>
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

		PathHolder(@Nullable RecyclerSelectorInternal<PathHolder> selector,
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
		PathArrayAdapter(
				FlexibleRecyclerSelectionListener<PathArrayAdapter, PathHolder, PathInfo> listener) {
			super(TAG, listener);
		}

		@Override
		public long getItemId(int position) {
			PathInfo item = getItem(position);
			if (item == null) {
				return -1;
			}
			return item.fullPath.hashCode();
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

			if (item.isReadOnly()) {
				holder.itemView.setAlpha(0.75f);
			} else {
				holder.itemView.setAlpha(1);
			}

			CharSequence friendlyName = item.getFriendlyName();
			holder.tvPath.setText(friendlyName);
			holder.ivPath.setImageResource(
					item.isRemovable ? R.drawable.ic_sd_storage_gray_24dp
							: R.drawable.ic_folder_gray_24dp);
			StringBuilder sbLine2 = new StringBuilder();
			String s = getString(
					item.isSAF ? R.string.fileaccess_saf : R.string.fileaccess_direct);
			sbLine2.append("|").append(s).append("| ");
			if (item.freeBytes == 0) {
				if (item.storagePath != null) {
					sbLine2.append(item.storagePath);
				}
			} else {
				String freeSpaceString = DisplayFormatters.formatByteCountToKiBEtc(
						item.freeBytes);
				sbLine2.append(getString(R.string.x_space_free, freeSpaceString));
				if (item.storagePath != null) {
					sbLine2.append(" - ");
					sbLine2.append(item.storagePath);
				}
			}
			if (AndroidUtils.DEBUG) {
				sbLine2.append("\n").append(item.fullPath);
			}
			String text = sbLine2.toString();
			SpannableStringBuilder ss = new SpannableStringBuilder(text);
			TextPaint paint = new TextPaint();
			paint.set(holder.tvFree.getPaint());
			paint.setTextSize(paint.getTextSize() * 0.8f);
			// Note: I tried to set fillColor to
			// AndroidUtilsUI.getStyleColor(context, android.R.attr.colorBackground)
			// but since context is DialogFragment it returns a different color
			// from the dialogs.  Calling
			// AndroidUtilsUI.getStyleColor(dialog.getContext(), android.R.attr.colorBackground)
			// fails to find a color
			// So I gave up and set it to "0" which is invisible
			SpanBubbles.setSpanBubbles(ss, text, "|", paint,
					holder.tvFree.getCurrentTextColor(),
					holder.tvFree.getCurrentTextColor(), 0, null);
			holder.tvFree.setText(ss);

			String warning = "";
			if (item.isPrivateStorage) {
				warning = getString(R.string.private_internal_storage_warning);
			}
			if (item.isReadOnly()) {
				warning = getString(R.string.read_only);
			}
			holder.tvWarning.setText(warning);
			holder.tvWarning.setVisibility(
					warning.isEmpty() ? View.GONE : View.VISIBLE);
		}
	}
}
