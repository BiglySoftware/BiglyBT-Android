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

import java.util.*;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.activity.TorrentOpenOptionsActivity;
import com.vuze.android.remote.dialog.DialogFragmentMoveData;
import com.vuze.android.remote.rpc.ReplyMapReceivedListener;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.util.DisplayFormatters;
import com.vuze.util.MapUtils;

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

	private TorrentOpenOptionsActivity ourActivity;

	private TextView tvFreeSpace;

	private TextView tvTags;

	private boolean tagLookupCalled;

	private SpanTags spanTags;

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).fragmentStart(this, TAG);
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
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

		ImageButton btnEditDir = (ImageButton) topView.findViewById(
				R.id.openoptions_btn_editdir);
		ImageButton btnEditName = (ImageButton) topView.findViewById(
				R.id.openoptions_btn_editname);

		tvName = (TextView) topView.findViewById(R.id.openoptions_name);
		tvSaveLocation = (TextView) topView.findViewById(R.id.openoptions_saveloc);
		tvFreeSpace = (TextView) topView.findViewById(R.id.openoptions_freespace);
		tvTags = (TextView) topView.findViewById(R.id.openoptions_tags);

		btnPositionLast = (CompoundButton) topView.findViewById(
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

		final Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);

		if (torrent == null) {
			getActivity().finish();
			VuzeEasyTracker.getInstance(getActivity()).logError(
					"Torrent doesn't exist", TAG);
			return topView;
		}

		if (torrent.containsKey(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR)) {
			updateFields(torrent);
		} else {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.getTorrent(TAG, torrentID,
							Collections.singletonList(
									TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR),
							new TorrentListReceivedListener() {

						@Override
						public void rpcTorrentListReceived(String callID,
								List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
							AndroidUtils.runOnUIThread(OpenOptionsGeneralFragment.this,
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
					Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
					DialogFragmentMoveData.openMoveDataDialog(torrent, sessionInfo,
							getFragmentManager());
				}
			});
		}

		if (btnEditName != null) {
			if (sessionInfo.getSupportsTorrentRename()) {
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
			} else {
				btnEditName.setVisibility(View.GONE);
			}
		}

		if (!tagLookupCalled) {
			tagLookupCalled = true;
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(final TransmissionRPC rpc) {
					Map<String, Object> map = new HashMap<>();
					map.put("ids", new Object[] {
						torrent.get("hashString")
					});
					rpc.simpleRpcCall("tags-lookup-start", map,
							new ReplyMapReceivedListener() {

						@Override
						public void rpcSuccess(String id, Map<?, ?> optionalMap) {
							if (ourActivity.isFinishing()) {
								return;
							}

							if (OpenOptionsGeneralFragment.this.isRemoving()) {
								if (AndroidUtils.DEBUG) {
									Log.e(TAG, "isRemoving");
								}

								return;
							}

							Object tagSearchID = optionalMap.get("id");
							final Map<String, Object> mapResultsRequest = new HashMap<>();

							mapResultsRequest.put("id", tagSearchID);
							if (tagSearchID != null) {
								rpc.simpleRpcCall("tags-lookup-get-results", mapResultsRequest,
										new ReplyMapReceivedListener() {

									@Override
									public void rpcSuccess(String id, Map<?, ?> optionalMap) {
										if (ourActivity.isFinishing()) {
											return;
										}

										if (OpenOptionsGeneralFragment.this.isRemoving()) {
											if (AndroidUtils.DEBUG) {
												Log.e(TAG, "isRemoving");
											}

											return;
										}

										if (AndroidUtils.DEBUG) {
											Log.d(TAG, "tag results: " + optionalMap);
										}
										boolean complete = MapUtils.getMapBoolean(optionalMap,
												"complete", true);
										if (!complete) {
											try {
												Thread.sleep(1500);
											} catch (InterruptedException ignored) {
											}

											if (ourActivity.isFinishing()) {
												return;
											}
											rpc.simpleRpcCall("tags-lookup-get-results",
													mapResultsRequest, this);
										}

										updateSuggestedTags(optionalMap);
									}

									@Override
									public void rpcFailure(String id, String message) {
									}

									@Override
									public void rpcError(String id, Exception e) {
									}
								});
							}
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

		return topView;
	}

	@Override
	public void onResume() {
		super.onResume();

		updateTags();
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
							if (freeSpace <= 0) {
								return;
							}
							AndroidUtils.runOnUIThread(OpenOptionsGeneralFragment.this,
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
		Map torrent = sessionInfo.getTorrent(torrentID);
		torrent.put(TransmissionVars.FIELD_TORRENT_DOWNLOAD_DIR, location);
		updateFields(torrent);
	}

	private void updateSuggestedTags(Map<?, ?> optionalMap) {
		List listTorrents = MapUtils.getMapList(optionalMap, "torrents", null);
		if (listTorrents == null) {
			return;
		}
		for (Object oTorrent : listTorrents) {
			if (oTorrent instanceof Map) {
				Map mapTorrent = (Map) oTorrent;
				final List tags = MapUtils.getMapList(mapTorrent, "tags", null);
				if (tags == null) {
					continue;
				}
				ourActivity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (ourActivity.isFinishing()) {
							return;
						}

						if (spanTags != null) {
							spanTags.addTagNames(tags);
						}
						updateTags();
					}
				});
				break;
			}
		}
	}

	private void createTags() {
		if (tvTags == null) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "no tvTags");
			}
			return;
		}

		List<Map<?, ?>> manualTags = new ArrayList<>();

		List<Map<?, ?>> allTags = sessionInfo.getTags();
		for (Map<?, ?> mapTag : allTags) {
			int type = MapUtils.getMapInt(mapTag, "type", 0);
			if (type == 3) { // manual
				manualTags.add(mapTag);
			}
		}

		tvTags.setMovementMethod(LinkMovementMethod.getInstance());

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			// allows for shadow on DrawPath
			tvTags.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		}

		SpanTags.SpanTagsListener l = new SpanTags.SpanTagsListener() {
			@Override
			public void tagClicked(String name) {
				ourActivity.flipTagState(name);
			}

			@Override
			public boolean isTagSelected(String name) {
				Set<String> selectedTags = ourActivity.getSelectedTags();

				return selectedTags.contains(name);
			}
		};

		spanTags = new SpanTags(ourActivity, sessionInfo, tvTags, l);
		spanTags.setTagMaps(manualTags);
	}

	private void updateTags() {
		if (spanTags == null) {
			createTags();
		}
		spanTags.updateTags();
	}

}
