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

package com.vuze.android.remote.activity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.Paint.Align;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.astuetz.PagerSlidingTabStrip;
import com.vuze.android.remote.*;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.dialog.DialogFragmentMoveData.DialogFragmentMoveDataListener;
import com.vuze.android.remote.fragment.OpenOptionsFilesFragment;
import com.vuze.android.remote.fragment.OpenOptionsGeneralFragment;
import com.vuze.android.remote.fragment.OpenOptionsPagerAdapter;
import com.vuze.android.remote.rpc.ReplyMapReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;
import com.vuze.util.MapUtils;

/**
 * Open Torrent: Options Dialog (Window)
 * <p>
 * Many Layouts!
 * <p>
 * 1) Wide and Long:  General Info on the left, File List on the right
 * 2) Just Long: General Info on top, File List on bottom
 * 3) Small: Tabs with General and Files
 * 
 * <P>
 * Related classes: 
 * {@link OpenOptionsPagerAdapter}
 * {@link OpenOptionsGeneralFragment} 
 * {@link OpenOptionsFilesFragment}
 */
public class TorrentOpenOptionsActivity
	extends ActionBarActivity
	implements DialogFragmentMoveDataListener
{
	private static final String TAG = "TorrentOpenOptions";

	private SessionInfo sessionInfo;

	private long torrentID;

	private OpenOptionsPagerAdapter pagerAdapter;

	protected boolean positionLast = true;

	protected boolean stateQueued = true;

	private Map<?, ?> torrent;

	/* (non-Javadoc)
	* @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
	*/
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
			finish();
			return;
		}

		String remoteProfileID = extras.getString(SessionInfoManager.BUNDLE_KEY);
		if (remoteProfileID != null) {
			sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID, this);
		}

		torrentID = extras.getLong("TorrentID");

		if (sessionInfo == null) {
			Log.e(TAG, "sessionInfo NULL!");
			finish();
			return;
		}

		torrent = sessionInfo.getTorrent(torrentID);
		if (torrent == null) {
			Log.e(TAG, "torrent NULL");
			finish();
			return;
		}

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		positionLast = remoteProfile.isAddPositionLast();
		stateQueued = remoteProfile.isAddStateQueued();

		setContentView(R.layout.activity_torrent_openoptions);

		setupActionBar();

		ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
		PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(R.id.pager_title_strip);
		if (viewPager != null && tabs != null) {
			pagerAdapter = new OpenOptionsPagerAdapter(getSupportFragmentManager(),
					viewPager, tabs);
		} else {
			pagerAdapter = null;
		}

		Button btnAdd = (Button) findViewById(R.id.openoptions_btn_add);
		Button btnCancel = (Button) findViewById(R.id.openoptions_btn_cancel);
		CompoundButton cbSilentAdd = (CompoundButton) findViewById(R.id.openoptions_cb_silentadd);

		if (btnAdd != null) {
			btnAdd.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finish(true);
				}
			});
		}
		if (btnCancel != null) {
			btnCancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					finish(false);
				}
			});
		}
		if (cbSilentAdd != null) {
			cbSilentAdd.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView,
						boolean isChecked) {
					sessionInfo.getRemoteProfile().setAddTorrentSilently(isChecked);
				}
			});
			cbSilentAdd.setChecked(sessionInfo.getRemoteProfile().isAddTorrentSilently());
		}

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
								if (TorrentOpenOptionsActivity.this.isFinishing()) {
									return;
								}

								Object tagSearchID = optionalMap.get("id");
								final Map mapResultsRequest = new HashMap();
								mapResultsRequest.put("id", tagSearchID);
								if (tagSearchID != null) {
									rpc.simpleRpcCall("tags-lookup-get-results",
											mapResultsRequest, new ReplyMapReceivedListener() {

												@Override
												public void rpcSuccess(String id, Map<?, ?> optionalMap) {
													boolean complete = MapUtils.getMapBoolean(
															optionalMap, "complete", true);
													if (!complete) {
														try {
															Thread.sleep(1000);
														} catch (InterruptedException e) {
														}

														rpc.simpleRpcCall("tags-lookup-get-results",
																mapResultsRequest, this);
													}

													updateTags(optionalMap);
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

	private void updateTags(Map<?, ?> optionalMap) {
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
				TorrentOpenOptionsActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (TorrentOpenOptionsActivity.this.isFinishing()) {
							return;
						}

						updateTags(tags);
					}
				});
				break;
			}
		}
	}

	private void updateTags(List<?> discoveredTags) {
		TextView tvTags = (TextView) findViewById(R.id.openoptions_tags);
		if (tvTags == null) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (Object tag : discoveredTags) {
			if (sb.length() > 0) {
				sb.append(" ");
			}
			sb.append('|');
			sb.append(tag.toString());
			sb.append("| ");
		}
		
		List<Map<?, ?>> allTags = sessionInfo.getTags();
		for (Map<?, ?> mapTag : allTags) {
			long uid = MapUtils.getMapLong(mapTag, "uid", 0);
			String name = MapUtils.getMapString(mapTag, "name", "??");
			int type = MapUtils.getMapInt(mapTag, "type", 0);
			if (type == 3) { // manual
				sb.append('|');
				sb.append(name);
				sb.append("| ");
			}
		}

		Log.d(TAG, "Setting tags to " + sb.toString());

		Resources resources = getResources();
		int colorBGTagCat = resources.getColor(R.color.bg_tag_type_cat);
		int colorFGTagCat = resources.getColor(R.color.fg_tag_type_cat);

		SpannableStringBuilder ss = new SpannableStringBuilder(sb);

		String string = sb.toString();

		Drawable drawable = resources.getDrawable(R.drawable.tag_q);
		tvTags.setMovementMethod(LinkMovementMethod.getInstance());

		setSpanBubbles(torrent, ss, string, "|", tvTags.getPaint(), colorFGTagCat,
				colorFGTagCat, colorBGTagCat, drawable);

		tvTags.setText(ss);
	}

	protected void finish(boolean addTorrent) {
		if (addTorrent) {
			// set position and state, the rest are already set
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.simpleRpcCall(positionLast ? "queue-move-bottom"
							: "queue-move-top", new long[] {
						torrentID
					}, null);
					if (stateQueued) {
						rpc.startTorrents("OpenOptions", new long[] {
							torrentID
						}, false, null);
					} else {
						// should be already stopped, but stop anyway
						rpc.stopTorrents("OpenOptions", new long[] {
							torrentID
						}, null);
					}
				}
			});
		} else {
			// remove the torrent
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.removeTorrent(new long[] {
						torrentID
					}, true, null);
				}
			});
		}
		if (!isFinishing()) {
			finish();
		}
	}

	private void setupActionBar() {
		Toolbar toolBar = (Toolbar) findViewById(R.id.actionbar);
		if (toolBar != null) {
			setSupportActionBar(toolBar);
		}

		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			if (AndroidUtils.DEBUG) {
				System.err.println("actionBar is null");
			}
			return;
		}
		actionBar.setDisplayHomeAsUpEnabled(false);

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		actionBar.setSubtitle(remoteProfile.getNick());
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.FragmentActivity#onPause()
	 */
	@Override
	protected void onPause() {
		if (pagerAdapter != null) {
			pagerAdapter.onPause();
		}

		super.onPause();
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.FragmentActivity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();

		if (pagerAdapter != null) {
			pagerAdapter.onResume();
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		VuzeEasyTracker.getInstance(this).activityStop(this);
	}

	/* (non-Javadoc)
	 * @see android.support.v7.app.ActionBarActivity#onBackPressed()
	 */
	@Override
	public void onBackPressed() {
		finish(false);
		super.onBackPressed();
	}

	public boolean isPositionLast() {
		return positionLast;
	}

	public boolean isStateQueued() {
		return stateQueued;
	}

	public void setPositionLast(boolean positionLast) {
		this.positionLast = positionLast;
		sessionInfo.getRemoteProfile().setAddPositionLast(positionLast);
	}

	public void setStateQueued(boolean stateQueud) {
		this.stateQueued = stateQueud;
		sessionInfo.getRemoteProfile().setAddStateQueued(stateQueud);
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.dialog.DialogFragmentMoveData.DialogFragmentMoveDataListener#locationChanged(java.lang.String)
	 */
	@Override
	public void locationChanged(String location) {
		List<Fragment> fragments = getSupportFragmentManager().getFragments();
		for (Fragment fragment : fragments) {
			if (fragment.isAdded()
					&& (fragment instanceof OpenOptionsGeneralFragment)) {
				((OpenOptionsGeneralFragment) fragment).locationChanged(location);
			}
		}
	}

	public static void setSpanBubbles(final Map torrent, SpannableStringBuilder ss, String text,
			String token, final TextPaint p, final int borderColor,
			final int textColor, final int fillColor, final Drawable rightIcon) {
		if (ss.length() > 0) {
			// hack to ensure descent is always added by TextView
			ss.append("\u200B");
		}

		// Start and end refer to the points where the span will apply
		int tokenLen = token.length();
		int base = 0;

		final int rightIconWidth = rightIcon.getIntrinsicWidth();
		final int rightIconHeight = rightIcon.getIntrinsicHeight();

		while (true) {
			int start = text.indexOf(token, base);
			int end = text.indexOf(token, start + tokenLen);

			if (start < 0 || end < 0) {
				break;
			}

			base = end + tokenLen;

			final String word = text.substring(start + tokenLen, end);

			Drawable imgDrawable = new Drawable() {

				@Override
				public void setColorFilter(ColorFilter cf) {
				}

				@Override
				public void setAlpha(int alpha) {
				}

				@Override
				public int getOpacity() {
					return 255;
				}

				@Override
				public void draw(Canvas canvas) {
					Rect bounds = getBounds();

					Paint paintLine = new Paint(p);
					paintLine.setAntiAlias(true);
					paintLine.setAlpha(255);

					float strokeWidth = paintLine.getStrokeWidth();

					float wIndent = bounds.height() * 0.02f;
					float topIndent = 1;
					float adjY = p.descent() - 1;

					float radius = bounds.height() / 2;
					RectF rectF = new RectF(bounds.left + wIndent,
							bounds.top + topIndent, bounds.right - (wIndent * 2),
							bounds.bottom + adjY);
					paintLine.setStyle(Paint.Style.FILL);
					paintLine.setColor(fillColor);
					canvas.drawRoundRect(rectF, radius, radius, paintLine);

					paintLine.setStrokeWidth(2);
					paintLine.setStyle(Paint.Style.STROKE);
					paintLine.setColor(borderColor);
					canvas.drawRoundRect(rectF, radius, radius, paintLine);

					paintLine.setStrokeWidth(strokeWidth);

					paintLine.setTextAlign(Align.LEFT);
					paintLine.setColor(textColor);
					paintLine.setSubpixelText(true);
					canvas.drawText(word, bounds.left + (bounds.height() / 2),
							-p.ascent(), paintLine);

					rightIcon.setBounds(bounds.left, bounds.top, bounds.right
							- (int) (wIndent * 2) - 6, (int) (bounds.bottom + adjY));
					if (rightIcon instanceof BitmapDrawable) {
						((BitmapDrawable) rightIcon).setGravity(Gravity.CENTER_VERTICAL
								| Gravity.RIGHT);
						((BitmapDrawable) rightIcon).setAntiAlias(true);
					}
					rightIcon.draw(canvas);
				}
			};

			float bottom = -p.ascent();
			float w = p.measureText(word + " ") + rightIconWidth + (bottom * 0.04f)
					+ 6 + (bottom / 2);
			int y = 0;

			imgDrawable.setBounds(0, y, (int) w, (int) bottom);

			ImageSpan imageSpan = new ImageSpan(imgDrawable,
					DynamicDrawableSpan.ALIGN_BASELINE);

			ss.setSpan(imageSpan, start, end + tokenLen, 0);

			ClickableSpan clickSpan = new ClickableSpan() {
				@Override
				public void onClick(View widget) {
					Log.e("TUX", "Clicked on " + word);
					//torrent.put(TransmissionVars.FIELD_TORRENT_TAGS, word);
				}
			};
			ss.setSpan(clickSpan, start, end + tokenLen, 0);
		}
	}

}
