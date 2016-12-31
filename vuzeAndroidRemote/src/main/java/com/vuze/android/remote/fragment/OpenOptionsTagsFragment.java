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

import java.util.*;

import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentOpenOptionsActivity;
import com.vuze.android.remote.rpc.ReplyMapReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.android.remote.session.Session;
import com.vuze.android.remote.session.SessionManager;
import com.vuze.android.remote.spanbubbles.SpanTags;
import com.vuze.util.MapUtils;
import com.vuze.util.Thunk;

import android.app.AlertDialog;
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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class OpenOptionsTagsFragment
	extends Fragment
	implements FragmentPagerListener
{
	private static final String TAG = "OpenOptionsTag";

	@Thunk
	long torrentID;

	private TextView tvTags;

	private boolean tagLookupCalled;

	@Thunk
	SpanTags spanTags;

	@Thunk
	TorrentOpenOptionsActivity ourActivity;

	@Thunk
	String remoteProfileID;

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
			return null;
		}

		remoteProfileID = SessionManager.findRemoteProfileID(this);
		if (remoteProfileID == null) {
			Log.e(TAG, "No remoteProfileID!");
			return null;
		}
		Session session = SessionManager.getSession(remoteProfileID,
				null, null);

		torrentID = extras.getLong("TorrentID");

		final Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
		if (torrent == null) {
			Log.e(TAG, "No torrent!");
			// In theory TorrentOpenOptionsActivity handled this NPE already
			return null;
		}

		if (activity instanceof TorrentOpenOptionsActivity) {
			ourActivity = (TorrentOpenOptionsActivity) activity;
		}

		View topView = inflater.inflate(R.layout.frag_torrent_tags, container,
				false);

		tvTags = (TextView) topView.findViewById(R.id.openoptions_tags);

		Button btnNew = (Button) topView.findViewById(R.id.torrent_tags_new);
		if (btnNew != null) {
			btnNew.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					handleNewButtonClick();
				}
			});
		}

		if (!tagLookupCalled) {
			tagLookupCalled = true;
			session.executeRpc(new Session.RpcExecuter() {
				@Override
				public void executeRpc(final TransmissionRPC rpc) {
					Map<String, Object> map = new HashMap<>();
					map.put("ids", new Object[] {
						torrent.get(TransmissionVars.FIELD_TORRENT_HASH_STRING)
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
										rpc.simpleRpcCall("tags-lookup-get-results",
												mapResultsRequest, new ReplyMapReceivedListener() {

													@Override
													public void rpcSuccess(String id,
															Map<?, ?> optionalMap) {
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
														boolean complete = MapUtils.getMapBoolean(
																optionalMap, "complete", true);
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

	@Thunk
	void handleNewButtonClick() {
		AlertDialog alertDialog = AndroidUtilsUI.createTextBoxDialog(getContext(),
				R.string.create_new_tag, R.string.newtag_name,
				new AndroidUtilsUI.OnTextBoxDialogClick() {

					@Override
					public void onClick(DialogInterface dialog, int which,
							EditText editText) {
						final String newName = editText.getText().toString();
						spanTags.addTagNames(Collections.singletonList(newName));
						ourActivity.flipTagState(null, newName);
						updateTags();
						Session session = SessionManager.getSession(
								remoteProfileID, null, null);
						session.tag.addTagToTorrents(TAG, new long[] {
							torrentID
						}, new Object[] {
							newName
						});
					}
				});

		alertDialog.show();
	}

	@Override
	public void onResume() {
		super.onResume();

		updateTags();
	}

	@Override
	public void pageDeactivated() {

	}

	@Override
	public void pageActivated() {
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
			// Issue in older APIs where ScrollView doesn't calculate scrolling
			// (scrollbars don't appear) when updateTorrentID(..) is trriggered
			// (via onResume).  pageActivated it fired later.
			// Issue is present on API 7 and 10, but not on 18
			tvTags.post(new Runnable() {
				@Override
				public void run() {
					updateTags();
				}
			});
		}
	}

	@Thunk
	void updateSuggestedTags(Map<?, ?> optionalMap) {
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

		Session session = SessionManager.getSession(remoteProfileID,
				null, null);
		List<Map<?, ?>> allTags = session.tag.getTags();
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
			public void tagClicked(int index, Map mapTags, String name) {
				ourActivity.flipTagState(mapTags, name);
			}

			@Override
			public int getTagState(int index, Map mapTag, String name) {
				List<Object> selectedTags = ourActivity.getSelectedTags();
				Object id = MapUtils.getMapObject(mapTag, "uid", name, Object.class);

				return selectedTags.contains(id) ? SpanTags.TAG_STATE_SELECTED
						: SpanTags.TAG_STATE_UNSELECTED;
			}
		};

		spanTags = new SpanTags(ourActivity, session, tvTags, l);
		spanTags.setLineSpaceExtra(AndroidUtilsUI.dpToPx(8));
		spanTags.setTagMaps(manualTags);
	}

	@Thunk
	void updateTags() {
		if (spanTags == null) {
			createTags();
		}
		if (spanTags != null) {
			spanTags.updateTags();
		}
	}

}
