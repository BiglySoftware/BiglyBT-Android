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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.TorrentOpenOptionsActivity;
import com.biglybt.android.client.dialog.DialogFragmentMoveData;
import com.biglybt.android.client.rpc.*;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.util.FileUtils;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class OpenOptionsGeneralFragment
	extends Fragment
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

	@Thunk
	String remoteProfileID;

	@Override
	public void onStart() {
		super.onStart();
		AnalyticsTracker.getInstance(this).fragmentResume(this, TAG);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		FragmentActivity activity = getActivity();
		Intent intent = activity.getIntent();

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, activity + "] onCreateview " + this);
		}

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
			return null;
		}

		remoteProfileID = SessionManager.findRemoteProfileID(this);

		torrentID = extras.getLong("TorrentID");

		if (activity instanceof TorrentOpenOptionsActivity) {
			ourActivity = (TorrentOpenOptionsActivity) activity;
		}

		View topView = inflater.inflate(R.layout.frag_openoptions_general,
				container, false);

		ImageButton btnEditDir = (ImageButton) topView.findViewById(
				R.id.openoptions_btn_editdir);
		ImageButton btnEditName = (ImageButton) topView.findViewById(
				R.id.openoptions_btn_editname);

		tvName = (TextView) topView.findViewById(R.id.openoptions_name);
		tvSaveLocation = (TextView) topView.findViewById(R.id.openoptions_saveloc);
		tvFreeSpace = (TextView) topView.findViewById(R.id.openoptions_freespace);

		CompoundButton btnPositionLast = (CompoundButton) topView.findViewById(
				R.id.openoptions_sw_position);

		CompoundButton btnStateQueued = (CompoundButton) topView.findViewById(
				R.id.openoptions_sw_state);

		if (ourActivity != null) {
			if (btnPositionLast != null) {
				btnPositionLast.setChecked(ourActivity.isPositionLast());
				btnPositionLast.setOnCheckedChangeListener(
						new OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView,
									boolean isChecked) {
								ourActivity.setPositionLast(isChecked);
							}
						});
			}
			if (btnStateQueued != null) {
				btnStateQueued.setChecked(ourActivity.isStateQueued());
				btnStateQueued.setOnCheckedChangeListener(
						new OnCheckedChangeListener() {
							@Override
							public void onCheckedChanged(CompoundButton buttonView,
									boolean isChecked) {
								ourActivity.setStateQueued(isChecked);
							}
						});
			}
		}

		Session session = SessionManager.getSession(remoteProfileID, null, null);
		final Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);

		if (torrent == null) {
			getActivity().finish();
			AnalyticsTracker.getInstance(getActivity()).logError(
					"Torrent doesn't exist", TAG);
			return topView;
		}

		if (torrent.containsKey(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR)) {
			updateFields(torrent);
		} else {
			session.executeRpc(new Session.RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.getTorrent(TAG, torrentID,
							Collections.singletonList(
									TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR),
							new TorrentListReceivedListener() {

								@Override
								public void rpcTorrentListReceived(String callID,
										List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
									AndroidUtilsUI.runOnUIThread(OpenOptionsGeneralFragment.this,
											new Runnable() {
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
					Session session = SessionManager.getSession(remoteProfileID, null,
							null);
					Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
					DialogFragmentMoveData.openMoveDataDialog(torrent, session,
							getFragmentManager());
				}
			});
		}

		if (btnEditName != null) {
			if (session.getSupports(RPCSupports.SUPPORTS_TORRENT_RENAAME)) {
				btnEditName.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						Builder builder = new AlertDialog.Builder(getActivity());
						final TextView textView = new EditText(getActivity());
						textView.setText(tvName.getText());
						textView.setSingleLine();

						if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
							builder.setInverseBackgroundForced(true);
						}

						builder.setView(textView);
						builder.setTitle(R.string.change_name_title);
						builder.setMessage(R.string.change_name_message);
						builder.setPositiveButton(android.R.string.ok,
								new DialogInterface.OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog, int which) {
										final String newName = textView.getText().toString();
										tvName.setText(newName);
										Session session = SessionManager.getSession(remoteProfileID,
												null, null);
										session.torrent.setDisplayName(TAG, torrentID, newName);
									}
								});
						builder.setNegativeButton(android.R.string.cancel,
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
									}
								});
						builder.show();
					}
				});
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
		Session session = SessionManager.getSession(remoteProfileID, null, null);
		final String saveLocation = TorrentUtils.getSaveLocation(session, torrent);
		if (tvSaveLocation != null) {
			String s = session.getRemoteProfile().getRemoteType() == RemoteProfile.TYPE_CORE
					? FileUtils.buildPathInfo(getContext(),
							new File(saveLocation)).getFriendlyName()
					: saveLocation;

			tvSaveLocation.setText(s);
		}
		if (tvFreeSpace != null) {
			tvFreeSpace.setText("");
			session.executeRpc(new Session.RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.getFreeSpace(saveLocation, new ReplyMapReceivedListener() {

						@Override
						public void rpcSuccess(String id, Map<?, ?> optionalMap) {
							final long freeSpace = MapUtils.getMapLong(optionalMap,
									"size-bytes", -1);
							if (freeSpace <= 0) {
								return;
							}
							AndroidUtilsUI.runOnUIThread(OpenOptionsGeneralFragment.this,
									new Runnable() {
										@Override
										public void run() {
											String freeSpaceString = DisplayFormatters.formatByteCountToKiBEtc(
													freeSpace);
											String s = getResources().getString(R.string.x_space_free,
													freeSpaceString);
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
		Session session = SessionManager.getSession(remoteProfileID, null, null);
		Map torrent = session.torrent.getCachedTorrent(torrentID);
		torrent.put(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR, location);
		updateFields(torrent);
	}

}
