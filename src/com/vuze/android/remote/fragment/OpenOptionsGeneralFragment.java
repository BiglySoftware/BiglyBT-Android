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
 */

package com.vuze.android.remote.fragment;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.activity.TorrentOpenOptionsActivity;
import com.vuze.android.remote.dialog.DialogFragmentMoveData;
import com.vuze.android.remote.rpc.ReplyMapReceivedListener;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;

public class OpenOptionsGeneralFragment
	extends Fragment
{

	private static final String TAG = "OpenOptionsGeneral";

	private View topView;

	private SessionInfo sessionInfo;

	private long torrentID;

	private TextView tvName;

	private TextView tvSaveLocation;

	private CompoundButton btnPositionLast;

	private CompoundButton btnStateQueued;

	private TorrentOpenOptionsActivity ourActivity;

	private TextView tvFreeSpace;

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onCreateview " + this);
		}

		FragmentActivity activity = getActivity();
		Intent intent = activity.getIntent();

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
		} else {

			String remoteProfileID = extras.getString(SessionInfoManager.BUNDLE_KEY);
			if (remoteProfileID != null) {
				sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID,
						activity);
			}
			
			torrentID = extras.getLong("TorrentID");
		}

		if (activity instanceof TorrentOpenOptionsActivity) {
			ourActivity = (TorrentOpenOptionsActivity) activity;
		}

		topView = inflater.inflate(R.layout.frag_openoptions_general, container,
				false);

		ImageButton btnEditDir = (ImageButton) topView.findViewById(R.id.openoptions_btn_editdir);
		ImageButton btnEditName = (ImageButton) topView.findViewById(R.id.openoptions_btn_editname);

		tvName = (TextView) topView.findViewById(R.id.openoptions_name);
		tvSaveLocation = (TextView) topView.findViewById(R.id.openoptions_saveloc);
		tvFreeSpace = (TextView) topView.findViewById(R.id.openoptions_freespace);

		btnPositionLast = (CompoundButton) topView.findViewById(R.id.openoptions_sw_position);
		btnStateQueued = (CompoundButton) topView.findViewById(R.id.openoptions_sw_state);

		if (ourActivity != null) {
			if (btnPositionLast != null) {
				btnPositionLast.setChecked(ourActivity.isPositionLast());
				btnPositionLast.setOnCheckedChangeListener(new OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						ourActivity.setPositionLast(isChecked);
					}
				});
			}
			if (btnStateQueued != null) {
				btnStateQueued.setChecked(ourActivity.isStateQueud());
				btnStateQueued.setOnCheckedChangeListener(new OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						ourActivity.setStateQueud(isChecked);
					}
				});
			}
		}

		final Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);

		if (torrent.containsKey(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR)) {
			updateFields(torrent);
		} else {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.getTorrent(TAG, torrentID,
							Arrays.asList(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR),
							new TorrentListReceivedListener() {

								@Override
								public void rpcTorrentListReceived(String callID,
										List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
									getActivity().runOnUiThread(new Runnable() {
										@Override
										public void run() {
											updateFields(torrent);
										}
									});
								}
							});
				}
			});
		}

		if (btnEditDir != null) {
			btnEditDir.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
					DialogFragmentMoveData.openMoveDataDialog(torrent, sessionInfo,
							getFragmentManager());
				}
			});
		}

		if (btnEditName != null) {
			btnEditName.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Builder builder = new AlertDialog.Builder(getActivity());
					final TextView textView = new EditText(getActivity());
					textView.setText(tvName.getText());
					textView.setSingleLine();

					builder.setView(textView);
					builder.setTitle(R.string.change_name_title);
					builder.setMessage(R.string.change_name_message);
					builder.setPositiveButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog, int which) {
									final String newName = textView.getText().toString();
									tvName.setText(newName);
									sessionInfo.executeRpc(new RpcExecuter() {

										@Override
										public void executeRpc(TransmissionRPC rpc) {
											rpc.setDisplayName(TAG, torrentID, newName);
										}
									});
								}
							});
					builder.setNegativeButton(android.R.string.cancel,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
								}
							});
					builder.create().show();
				}
			});
		}

		return topView;
	}

	private void updateFields(Map<?, ?> torrent) {
		if (tvName != null) {
			tvName.setText(MapUtils.getMapString(torrent, "name", "dunno"));
		}
		final String saveLocation = TorrentUtils.getSaveLocation(torrent);
		if (tvSaveLocation != null) {
			tvSaveLocation.setText(saveLocation);
		}
		if (tvFreeSpace != null) {
			tvFreeSpace.setText("");
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.getFreeSpace(saveLocation, new ReplyMapReceivedListener() {

						@Override
						public void rpcSuccess(String id, Map<?, ?> optionalMap) {
							final long freeSpace = MapUtils.getMapLong(optionalMap,
									"size-bytes", -1);
							if (freeSpace < 0) {
								return;
							}
							AndroidUtils.runOnUIThread(OpenOptionsGeneralFragment.this,
									new Runnable() {
										@Override
										public void run() {
											String freeSpaceString = DisplayFormatters.formatByteCountToKiBEtc(freeSpace);
											String s = getResources().getString(
													R.string.x_space_free, freeSpaceString);
											tvFreeSpace.setText(s);
										}
									});
						}

						@Override
						public void rpcFailure(String id, String message) {
						}

						@Override
						public void rpcError(String id, Exception e) {
						}
					});
				}
			});
		}

	}

	public void locationChanged(String location) {
		Map torrent = sessionInfo.getTorrent(torrentID);
		torrent.put(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR, location);
		updateFields(torrent);
	}

}
