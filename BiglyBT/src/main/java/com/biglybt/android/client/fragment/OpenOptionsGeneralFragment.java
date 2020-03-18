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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.TorrentOpenOptionsActivity;
import com.biglybt.android.client.dialog.DialogFragmentMoveData;
import com.biglybt.android.client.rpc.RPCSupports;
import com.biglybt.android.client.rpc.SuccessReplyMapRecievedListener;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.util.FileUtils;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class OpenOptionsGeneralFragment
	extends SessionFragment
{
	private static final String TAG = "OpenOptionsGeneral";

	@Thunk
	long torrentID;

	@Thunk
	TextView tvName;

	@Thunk
	TextView tvSaveLocation;

	@Thunk
	TorrentOpenOptionsActivity ourActivity;

	@Thunk
	TextView tvFreeSpace;

	@Nullable
	@Override
	public View onCreateViewWithSession(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

		FragmentActivity activity = requireActivity();
		torrentID = TorrentUtils.getTorrentID(activity);
		if (torrentID < 0) {
			return null;
		}

		if (activity instanceof TorrentOpenOptionsActivity) {
			ourActivity = (TorrentOpenOptionsActivity) activity;
		}

		View topView = inflater.inflate(R.layout.frag_openoptions_general,
				container, false);

		Button btnEditDir = topView.findViewById(R.id.openoptions_btn_editdir);
		Button btnEditName = topView.findViewById(R.id.openoptions_btn_editname);

		tvName = topView.findViewById(R.id.openoptions_name);
		tvSaveLocation = topView.findViewById(R.id.openoptions_saveloc);
		tvFreeSpace = topView.findViewById(R.id.openoptions_freespace);

		CompoundButton btnPositionLast = topView.findViewById(
				R.id.openoptions_sw_position);

		CompoundButton btnStateQueued = topView.findViewById(
				R.id.openoptions_sw_state);

		if (ourActivity != null) {
			if (btnPositionLast != null) {
				btnPositionLast.setChecked(ourActivity.isPositionLast());
				btnPositionLast.setOnCheckedChangeListener(
						(buttonView, isChecked) -> ourActivity.setPositionLast(isChecked));
			}
			if (btnStateQueued != null) {
				btnStateQueued.setChecked(ourActivity.isStateQueued());
				btnStateQueued.setOnCheckedChangeListener(
						(buttonView, isChecked) -> ourActivity.setStateQueued(isChecked));
			}
		}

		final Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);

		if (torrent == null) {
			activity.finish();
			AnalyticsTracker.getInstance(activity).logError("Torrent doesn't exist",
					TAG);
			return topView;
		}

		if (torrent.containsKey(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR)) {
			updateFields(torrent);
		} else {
			session.executeRpc(rpc -> rpc.getTorrent(TAG, torrentID,
					Collections.singletonList(
							TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR),
					(callID, addedTorrentMaps, fields, fileIndexes,
							removedTorrentIDs) -> AndroidUtilsUI.runOnUIThread(
									OpenOptionsGeneralFragment.this, false,
									validActivity -> updateFields(torrent))));
		}

		if (btnEditDir != null) {
			btnEditDir.setOnClickListener(
					v -> DialogFragmentMoveData.openMoveDataDialog(torrentID, session,
							getFragmentManager()));
		}

		if (btnEditName != null) {
			if (session.getSupports(RPCSupports.SUPPORTS_TORRENT_RENAAME)) {
				btnEditName.setOnClickListener(
						v -> AndroidUtilsUI.createTextBoxDialog(requireContext(),
								R.string.change_name_title, 0, R.string.change_name_message,
								tvName.getText().toString(), EditorInfo.IME_ACTION_DONE,
								(dialog, which, editText) -> {
									final String newName = editText.getText().toString();
									tvName.setText(newName);
									session.torrent.setDisplayName(TAG, torrentID, newName);
								}).show());
			} else {
				btnEditName.setVisibility(View.GONE);
			}
		}

		return topView;
	}

	@Thunk
	void updateFields(Map<?, ?> torrent) {
		if (tvName != null) {
			tvName.setText(MapUtils.getMapString(torrent, "name", "dunno"));
		}
		final String saveLocation = TorrentUtils.getSaveLocation(session, torrent);
		if (tvSaveLocation != null) {
			CharSequence s = session.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_CORE
					? FileUtils.buildPathInfo(getContext(),
							new File(saveLocation)).getFriendlyName(requireContext())
					: saveLocation;

			tvSaveLocation.setText(s);
		}
		if (tvFreeSpace != null) {
			tvFreeSpace.setText("");
			session.executeRpc(rpc -> rpc.getFreeSpace(saveLocation,
					(SuccessReplyMapRecievedListener) (id, optionalMap) -> {
						if (getActivity() == null || getActivity().isFinishing()) {
							return;
						}

						final long freeSpace = MapUtils.getMapLong(optionalMap,
								TransmissionVars.FIELD_FREESPACE_SIZE_BYTES, -1);
						if (freeSpace <= 0) {
							return;
						}
						AndroidUtilsUI.runOnUIThread(OpenOptionsGeneralFragment.this, false,
								activity -> {
									String freeSpaceString = DisplayFormatters.formatByteCountToKiBEtc(
											freeSpace);
									String s = getResources().getString(R.string.x_space_free,
											freeSpaceString);
									tvFreeSpace.setText(s);
								});
					}));
		}

	}

	public void locationChanged(String location) {
		Map<String, Object> torrent = session.torrent.getCachedTorrent(torrentID);
		if (torrent == null) {
			return;
		}
		torrent.put(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR, location);
		updateFields(torrent);
	}

}
