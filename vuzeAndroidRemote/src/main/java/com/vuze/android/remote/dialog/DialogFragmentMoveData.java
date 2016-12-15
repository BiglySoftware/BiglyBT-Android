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

package com.vuze.android.remote.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.vuze.android.remote.*;
import com.vuze.android.remote.AndroidUtilsUI.AlertDialogBuilder;
import com.vuze.android.remote.session.Session;
import com.vuze.android.remote.session.SessionManager;
import com.vuze.android.remote.session.SessionSettings;
import com.vuze.util.MapUtils;
import com.vuze.util.Thunk;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

public class DialogFragmentMoveData
	extends DialogFragmentResized
{

	private static final String KEY_HISTORY = "history";

	@Thunk
	EditText etLocation;

	private CheckBox cbRememberLocation;

	private long torrentId;

	private ArrayList<String> history;

	private AlertDialog dialog;

	private AlertDialogBuilder alertDialogBuilder;

	public interface DialogFragmentMoveDataListener
	{
		void locationChanged(String location);
	}

	public DialogFragmentMoveData() {
		setMinWidthPX(
				(int) (AndroidUtilsUI.getScreenWidthPx(VuzeRemoteApp.getContext())
						* 0.9));
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		boolean checked = cbRememberLocation.isChecked();
		String location = etLocation.getText().toString();

		// This mess is an attempt to rebuild the layout within the dialog
		// when the orientation changes.  Seems to work, but diesn't make sense
		ViewGroup viewGroup = (ViewGroup) alertDialogBuilder.view;
		//ViewGroup parent = (ViewGroup) viewGroup.getParent();
		viewGroup.removeAllViews();
		View view = View.inflate(dialog.getContext(), R.layout.dialog_move_data,
				viewGroup);
		dialog.setView(view);
		alertDialogBuilder.view = view;
		setupVars(view);

		cbRememberLocation.setChecked(checked);
		etLocation.setText(location);

		resize();
	}

	private void resize() {
		// fill full width because we need all the room
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		Window window = getDialog().getWindow();
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

		alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(getActivity(),
				R.layout.dialog_move_data);

		Builder builder = alertDialogBuilder.builder;

		builder.setTitle(R.string.action_sel_relocate);

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						moveData();
					}
				});
		builder.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						DialogFragmentMoveData.this.getDialog().cancel();
					}
				});

		final View view = alertDialogBuilder.view;

		dialog = builder.create();
		setupVars(view);

		return dialog;
	}

	@Thunk
	void moveData() {
		Session session = SessionManager.findSession(this, null);
		if (session == null) {
			return;
		}

		String moveTo = etLocation.getText().toString();
		if (cbRememberLocation.isChecked()) {
			if (!history.contains(moveTo)) {
				history.add(0, moveTo);
				session.moveDataHistoryChanged(history);
			}
		}
		session.torrent.moveDataTo(torrentId, moveTo);
		FragmentActivity activity = getActivity();
		if (activity instanceof DialogFragmentMoveDataListener) {
			((DialogFragmentMoveDataListener) activity).locationChanged(moveTo);
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		resize();
	}

	@Override
	public void onResume() {
		super.onResume();
		resize();
	}

	private void setupVars(View view) {
		Bundle args = getArguments();
		String name = args.getString(TransmissionVars.FIELD_TORRENT_NAME);
		torrentId = args.getLong(TransmissionVars.FIELD_TORRENT_ID);
		String downloadDir = args.getString(
				TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR);
		history = args.getStringArrayList(KEY_HISTORY);

		ArrayList<String> newHistory = new ArrayList<>();
		if (history != null) {
			newHistory.addAll(history);
		}

		if (downloadDir != null && !newHistory.contains(downloadDir)) {
			if (newHistory.size() > 1) {
				newHistory.add(1, downloadDir);
			} else {
				newHistory.add(downloadDir);
			}
		}

		etLocation = (EditText) view.findViewById(R.id.movedata_editview);
		if (downloadDir != null) {
			etLocation.setText(downloadDir);
		}
		ListView lvHistory = (ListView) view.findViewById(
				R.id.movedata_historylist);
		cbRememberLocation = (CheckBox) view.findViewById(R.id.movedata_remember);
		TextView tv = (TextView) view.findViewById(R.id.movedata_label);

		tv.setText(getResources().getString(R.string.movedata_label, name));

		ArrayAdapter<String> adapter = new ArrayAdapter<>(view.getContext(),
				R.layout.list_view_small_font, newHistory);
		lvHistory.setAdapter(adapter);

		lvHistory.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, final View view,
					int position, long id) {
				Object item = parent.getItemAtPosition(position);

				if (item instanceof String) {
					etLocation.setText((String) item);
				}
			}
		});

	}

	@Override
	public String getLogTag() {
		return "MoveData";
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
		bundle.putString(SessionManager.BUNDLE_KEY,
				session.getRemoteProfile().getID());

		SessionSettings sessionSettings = session.getSessionSettings();

		String defaultDownloadDir = sessionSettings == null ? null
				: sessionSettings.getDownloadDir();
		String downloadDir = TorrentUtils.getSaveLocation(session, mapTorrent);
		bundle.putString(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR, downloadDir);
		ArrayList<String> history = new ArrayList<>();
		if (defaultDownloadDir != null) {
			history.add(defaultDownloadDir);
		}

		List<String> saveHistory = session.getRemoteProfile().getSavePathHistory();
		for (String s : saveHistory) {
			if (!history.contains(s)) {
				history.add(s);
			}
		}
		bundle.putStringArrayList(KEY_HISTORY, history);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fm, "MoveDataDialog");
	}
}
