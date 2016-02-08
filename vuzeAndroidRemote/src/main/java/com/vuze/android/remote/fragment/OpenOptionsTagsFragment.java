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

package com.vuze.android.remote.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentOpenOptionsActivity;
import com.vuze.android.remote.rpc.ReplyMapReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.util.MapUtils;

import java.util.*;

public class OpenOptionsTagsFragment
	extends Fragment
{
	private static final String TAG = "OpenOptionsTag";

	private SessionInfo sessionInfo;

	private long torrentID;

	private TextView tvTags;

	private boolean tagLookupCalled;

	private SpanTags spanTags;

	private TorrentOpenOptionsActivity ourActivity;

	public OpenOptionsTagsFragment() {
		// Required empty public constructor
	}

	@Override
	public void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).fragmentStart(this, TAG);
	}

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

		final Map<?, ?> torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			Log.e(TAG, "No torrent!");
			// In theory TorrentOpenOptionsActivity handled this NPE already
			return null;
		}

		if (activity instanceof TorrentOpenOptionsActivity) {
			ourActivity = (TorrentOpenOptionsActivity) activity;
		}

		View topView = inflater.inflate(R.layout.frag_torrent_tags, container, false);

		tvTags = (TextView) topView.findViewById(R.id.openoptions_tags);

		if (!tagLookupCalled) {
			tagLookupCalled = true;
			sessionInfo.executeRpc(new SessionInfo.RpcExecuter() {
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

									if (OpenOptionsTagsFragment.this.isRemoving()) {
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

														if (OpenOptionsTagsFragment.this.isRemoving()) {
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
	public void onAttach(Context context) {
		super.onAttach(context);
	}

	@Override
	public void onDetach() {
		super.onDetach();
	}

	@Override
	public void onResume() {
		super.onResume();

		updateTags();
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
				ourActivity.runOnUiThread(new Runnable()
				{
					@Override
					public void run() {
						if (ourActivity.isFinishing()) {
							return;
						}

						if (spanTags != null) {
							//noinspection unchecked
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
		if (allTags == null) {
			return;
		}
		for (Map<?, ?> mapTag : allTags) {
			int type = MapUtils.getMapInt(mapTag, "type", 0);
			if (type == 3) { // manual
				manualTags.add(mapTag);
			}
		}

		tvTags.setMovementMethod(LinkMovementMethod.getInstance());

		SpanTags.SpanTagsListener l = new SpanTags.SpanTagsListener() {
			@Override
			public void tagClicked(Map mapTags, String name) {
				ourActivity.flipTagState(mapTags, name);
			}

			@Override
			public int getTagState(Map mapTag, String name) {
				List<Object> selectedTags = ourActivity.getSelectedTags();
				Object id = MapUtils.getMapObject(mapTag, "uid", name, Object.class);

				return selectedTags.contains(id) ? SpanTags.TAG_STATE_SELECTED
						: SpanTags.TAG_STATE_UNSELECTED;
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
