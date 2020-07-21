package com.biglybt.android.client.dialog;

import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentManager;

import com.biglybt.android.client.*;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.client.session.SessionSettings;
import com.biglybt.android.core.az.AndroidFile;
import com.biglybt.android.core.az.AndroidFileHandler;
import com.biglybt.android.util.FileUtils.PathInfo;
import com.biglybt.util.Thunk;

import java.io.File;
import java.util.*;

public class DialogFragmentMoveData
	extends DialogFragmentAbstractLocationPicker
{
	private static final String BUNDLEKEY_ENABLE_APPEND_NAME = "EnableAppendName";

	private long torrentId;

	private String torrentName;

	private CheckBox cbAppendSubDir;

	private boolean allowAppendName;

	private boolean appendName;

	@SuppressWarnings("rawtypes")
	public static void openMoveDataDialog(long torrentID, Session session,
			FragmentManager fm) {
		if (torrentID == -1) {
			return;
		}

		Map<?, ?> mapTorrent = session.torrent.getCachedTorrent(torrentID);
		if (mapTorrent == null) {
			return;
		}
		if (!mapTorrent.containsKey(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR)) {
			if (DEBUG) {
				Log.d(TAG, "Missing downloadDir, fetching..");
			}
			session.executeRpc(rpc -> rpc.getTorrent(TAG, torrentID,
					Collections.singletonList(
							TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR),
					(callID, addedTorrentMaps, fields, fileIndexes,
							removedTorrentIDs) -> {
						Map<?, ?> newMap = session.torrent.getCachedTorrent(torrentID);
						if (newMap == null || !newMap.containsKey(
								TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR)) {
							// TODO: Warn
							return;
						}
						openMoveDataDialog(torrentID, session, fm);
					}));
			return;
		}

		DialogFragmentAbstractLocationPicker dlg = new DialogFragmentMoveData();
		Bundle bundle = new Bundle();
		//bundle.putString(KEY_CALLBACK_ID, null);
		bundle.putLong(TransmissionVars.FIELD_TORRENT_ID, torrentID);
		bundle.putString(TransmissionVars.FIELD_TORRENT_NAME,
				"" + mapTorrent.get(TransmissionVars.FIELD_TORRENT_NAME));
		boolean isSimpleTorrent = TorrentUtils.isSimpleTorrent(mapTorrent);
		bundle.putBoolean(BUNDLEKEY_ENABLE_APPEND_NAME, !isSimpleTorrent);
		bundle.putString(SessionManager.BUNDLE_KEY,
				session.getRemoteProfile().getID());

		SessionSettings sessionSettings = session.getSessionSettingsClone();

		String defaultDownloadDir = sessionSettings == null ? null
				: sessionSettings.getDownloadDir();
		String downloadDir = TorrentUtils.getSaveLocation(session, mapTorrent);
		bundle.putString(KEY_DEFAULT_DIR, downloadDir);

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
	protected void onCreateBuilder(AlertDialogBuilder alertDialogBuilder) {
		Bundle args = getArguments();
		assert args != null;

		torrentId = args.getLong(TransmissionVars.FIELD_TORRENT_ID);
		torrentName = args.getString(TransmissionVars.FIELD_TORRENT_NAME);

		allowAppendName = args.getBoolean(BUNDLEKEY_ENABLE_APPEND_NAME, false);
		if (allowAppendName) {
			if (currentDir != null && currentDir.endsWith(torrentName)) {
				currentDir = currentDir.substring(0,
						currentDir.length() - torrentName.length() - 1);
				appendName = true;
			}
		}

		alertDialogBuilder.builder.setTitle(torrentName);
	}

	@Override
	protected void okClicked(Session session, PathInfo pathInfo) {
		moveData(session, pathInfo);
		dismissDialog();
	}

	@Override
	protected void setupWidgets(@NonNull View view) {
		super.setupWidgets(view);

		Resources resources = getResources();

		cbAppendSubDir = view.findViewById(R.id.movedata_appendname);
		if (cbAppendSubDir != null) {
			if (allowAppendName) {
				cbAppendSubDir.setVisibility(View.VISIBLE);
				cbAppendSubDir.setChecked(appendName);
				cbAppendSubDir.setText(AndroidUtils.fromHTML(resources,
						R.string.movedata_place_in_subfolder, torrentName));
			} else {
				cbAppendSubDir.setVisibility(View.GONE);
			}
		}
	}

	@Thunk
	void moveData(@NonNull Session session, @NonNull PathInfo pathInfo) {
		// Move on Remote will have null uri
		if (pathInfo.uri == null) {
			String moveTo = pathInfo.file.getAbsolutePath();
			if (allowAppendName && cbAppendSubDir != null
					&& cbAppendSubDir.isChecked()) {
				char sep = moveTo.length() > 2 && moveTo.charAt(2) == '\\' ? '\\' : '/';
				moveTo += sep + torrentName;
			}
			session.torrent.moveDataTo(torrentId, moveTo);
		} else {
			AndroidFileHandler fileHandler = new AndroidFileHandler();
			File file = fileHandler.newFile(pathInfo.fullPath);
			if (allowAppendName && cbAppendSubDir != null
					&& cbAppendSubDir.isChecked()) {
				file = fileHandler.newFile(file, torrentName);
			}
			session.torrent.moveDataTo(torrentId, file.toString());
		}
		triggerLocationChanged(pathInfo);
	}
}
