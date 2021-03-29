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

package com.biglybt.android.client.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.view.View;

import androidx.annotation.NonNull;

import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.TorrentDetailsActivity;
import com.biglybt.android.client.adapter.TorrentListAdapter.ViewHolderFlipValidator;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.spanbubbles.SpanBubbles;
import com.biglybt.android.client.spanbubbles.SpanTags;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.TextViewFlipper;
import com.biglybt.util.DisplayFormatters;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fills one Torrent info row.
 * <p/>
 * Split out from {@link TorrentListAdapter} so that
 * {@link TorrentDetailsActivity} can use it for its top area
 */
public class TorrentListRowFiller
{
	@SuppressWarnings("unused")
	private static final String TAG = "TL_RowFiller";

	private final int colorBGTagState;

	private final int colorFGTagState;

	@NonNull
	private final TextViewFlipper flipper;

	private TorrentListHolderItem viewHolder;

	private final boolean showTags;

	private static final NumberFormat nfPct1 = NumberFormat.getPercentInstance();

	static {
		nfPct1.setMaximumFractionDigits(1);
	}

	public TorrentListRowFiller(@NonNull Context context,
			@NonNull View parentView, boolean showTags) {
		this(context, showTags);
		this.viewHolder = new TorrentListHolderItem(null, parentView, false);
	}

	TorrentListRowFiller(Context context, boolean showTags) {
		colorBGTagState = AndroidUtilsUI.getStyleColor(context,
				R.attr.bg_tag_type_2);
		colorFGTagState = AndroidUtilsUI.getStyleColor(context,
				R.attr.fg_tag_type_2);

		flipper = TextViewFlipper.create();
		this.showTags = showTags;
	}

	public void fillHolder(Map<?, ?> item, @NonNull Session session) {
		if (viewHolder == null) {
			return;
		}
		fillHolder(viewHolder, item, session);
	}

	void fillHolder(@NonNull TorrentListHolderItem holder, Map<?, ?> item,
			@NonNull Session session) {
		long torrentID = MapUtils.getMapLong(item,
				TransmissionVars.FIELD_TORRENT_ID, -1);

		if (holder.tvName == null) {
			return;
		}
		Resources resources = AndroidUtils.requireResources(holder.itemView);

		holder.animateFlip = holder.torrentID == torrentID;
		holder.torrentID = torrentID;
		ViewHolderFlipValidator validator = new ViewHolderFlipValidator(holder,
				torrentID);

		if (holder.ivChecked != null) {
			holder.ivChecked.setVisibility(
					AndroidUtils.hasTouchScreen() ? View.GONE : View.VISIBLE);
		}

		String torrentName = MapUtils.getMapString(item,
				TransmissionVars.FIELD_TORRENT_NAME, " ");
		flipper.changeText(holder.tvName, AndroidUtils.lineBreaker(torrentName),
				holder.animateFlip, validator);

		int fileCount = MapUtils.getMapInt(item,
				TransmissionVars.FIELD_TORRENT_FILE_COUNT, 0);
		long size = MapUtils.getMapLong(item,
				TransmissionVars.FIELD_TORRENT_SIZE_WHEN_DONE, 0);

		float pctDone = TorrentUtils.getPercentDone(item);
		if (holder.tvProgress != null) {
			String s = pctDone < 0 || (!holder.isSmall && pctDone >= 1) ? ""
					: nfPct1.format(pctDone);
			flipper.changeText(holder.tvProgress, s, holder.animateFlip, validator);
		}
		if (holder.pb != null) {
			if (!(holder.pb instanceof LinearProgressIndicator)) {
				holder.pb.setVisibility(pctDone < 0 ? View.INVISIBLE : View.VISIBLE);
			}
			int pctDoneInt = (int) (pctDone * 10000);
			if (holder.pb.getProgress() != pctDoneInt) {
				AndroidUtilsUI.setProgress(holder.pb, pctDoneInt, true);
			}
			float shareRatio = MapUtils.getMapFloat(item,
					TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO, -1);
			int ratioPct = (int) (pctDoneInt == 10000 ? shareRatio * 10000
					: (shareRatio * (10000 - 2400)) + 1200);
			holder.pb.setSecondaryProgress(ratioPct);
		}

		long error = MapUtils.getMapLong(item, TransmissionVars.FIELD_TORRENT_ERROR,
				TransmissionVars.TR_STAT_OK);
		boolean hasScrapeError = error == TransmissionVars.TR_STAT_TRACKER_ERROR
				|| error == TransmissionVars.TR_STAT_TRACKER_WARNING;

		if (holder.tvInfo != null) {

			StringBuilder sb = new StringBuilder();

			if (size >= 0) {
				if (fileCount <= 1) {
					sb.append(DisplayFormatters.formatByteCountToKiBEtc(size));
				} else {
					sb.append(resources.getQuantityString(R.plurals.torrent_row_info,
							fileCount, fileCount));
					sb.append(resources.getString(R.string.torrent_row_info2,
							DisplayFormatters.formatByteCountToKiBEtc(size)));
				}
			}

			long numPeersDLFrom = MapUtils.getMapLong(item,
					TransmissionVars.FIELD_TORRENT_PEERS_SENDING_TO_US, -1);
			long numPeersULTo = MapUtils.getMapLong(item,
					TransmissionVars.FIELD_TORRENT_PEERS_GETTING_FROM_US, -1);
			long numPeersConnected = MapUtils.getMapLong(item,
					TransmissionVars.FIELD_TORRENT_PEERS_CONNECTED, -1);
			if (numPeersConnected > 0 && numPeersDLFrom >= 0 && numPeersULTo >= 0) {
				if (sb.length() > 0) {
					sb.append(resources.getString(R.string.torrent_row_line_split));
				}
				sb.append(resources.getString(R.string.torrent_row_peers,
						Long.toString(pctDone < 1.0 ? numPeersDLFrom : numPeersULTo),
						Long.toString(numPeersConnected)));
			}
			if (!hasScrapeError && error != TransmissionVars.TR_STAT_OK) {
				// error
				// TODO: parse error and add error type to message
				String errorString = MapUtils.getMapString(item,
						TransmissionVars.FIELD_TORRENT_ERROR_STRING, "");
				if (holder.tvTrackerError != null) {
					flipper.changeText(holder.tvTrackerError,
							AndroidUtils.lineBreaker(errorString), holder.animateFlip,
							validator);
				} else {
					if (sb.length() > 0) {
						sb.append(holder.isSmall
								? resources.getString(R.string.torrent_row_line_split)
								: "<br>");
					}
					sb.append("<font color=\"#880000\">").append(errorString).append(
							"</font>");
				}
			} else if (holder.tvTrackerError != null) {
				flipper.changeText(holder.tvTrackerError, "", holder.animateFlip,
						validator);
			}

			flipper.changeText(holder.tvInfo, AndroidUtils.fromHTML(sb.toString()),
					holder.animateFlip, validator);
		}
		if (holder.tvETA != null) {
			long etaSecs = MapUtils.getMapLong(item,
					TransmissionVars.FIELD_TORRENT_ETA, -1);
			CharSequence s = "";
			if (etaSecs > 0 && etaSecs * 1000L < DateUtils.WEEK_IN_MILLIS) {
				s = DisplayFormatters.prettyFormatTimeDiffShort(resources, etaSecs);
			} else if (pctDone >= 1) {
				float shareRatio = MapUtils.getMapFloat(item,
						TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO, -1);
				s = shareRatio < 0 ? ""
						: AndroidUtils.fromHTML(resources,
								holder.isSmall ? R.string.torrent_row_share_ratio
										: R.string.torrent_row_share_ratio_circle,
								shareRatio);
			}
			flipper.changeText(holder.tvETA, s, holder.animateFlip, validator);
		}
		if (holder.tvUlRate != null) {
			long rateUpload = MapUtils.getMapLong(item,
					TransmissionVars.FIELD_TORRENT_RATE_UPLOAD, 0);

			if (rateUpload > 0) {
				String text = "|\u25B2 "
						+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateUpload) + '|';
				SpannableStringBuilder ss = new SpannableStringBuilder(text);
				SpanBubbles.setSpanBubbles(ss, text, "|", holder.tvUlRate.getPaint(),
						0xFF40A080, colorFGTagState, 0x3040A080, null);
				flipper.changeText(holder.tvUlRate, ss, holder.animateFlip, validator);
			} else {
				flipper.changeText(holder.tvUlRate, "", holder.animateFlip, validator);
			}
		}
		if (holder.tvDlRate != null) {
			long rateDownload = MapUtils.getMapLong(item,
					TransmissionVars.FIELD_TORRENT_RATE_DOWNLOAD, 0);

			if (rateDownload > 0) {
				String text = "|\u25BC "
						+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateDownload)
						+ '|';
				SpannableStringBuilder ss = new SpannableStringBuilder(text);
				SpanBubbles.setSpanBubbles(ss, text, "|", holder.tvDlRate.getPaint(),
						0xFF2a8bcb, colorFGTagState, 0x302a8bcb, null);
				flipper.changeText(holder.tvDlRate, ss, holder.animateFlip, validator);
			} else {
				flipper.changeText(holder.tvDlRate, "", holder.animateFlip, validator);
			}
		}

		List<?> mapTagUIDs = MapUtils.getMapList(item,
				TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);

		if (holder.tvStatus != null) {
			StringBuilder text = new StringBuilder();
			int color = -1;

			int status = MapUtils.getMapInt(item,
					TransmissionVars.FIELD_TORRENT_STATUS,
					TransmissionVars.TR_STATUS_STOPPED);

			if (mapTagUIDs == null || mapTagUIDs.size() == 0) {

				int id;
				switch (status) {
					case TransmissionVars.TR_STATUS_CHECK_WAIT:
					case TransmissionVars.TR_STATUS_CHECK:
						id = R.string.torrent_status_checking;
						break;

					case TransmissionVars.TR_STATUS_DOWNLOAD:
						id = R.string.torrent_status_download;
						break;

					case TransmissionVars.TR_STATUS_DOWNLOAD_WAIT:
						id = R.string.torrent_status_queued_dl;
						break;

					case TransmissionVars.TR_STATUS_SEED:
						id = R.string.torrent_status_seed;
						break;

					case TransmissionVars.TR_STATUS_SEED_WAIT:
						id = R.string.torrent_status_queued_ul;
						break;

					case TransmissionVars.TR_STATUS_STOPPED:
						id = R.string.torrent_status_stopped;
						break;

					default:
						id = -1;
						break;
				}
				if (id >= 0) {
					text.append(resources.getString(id));
				}
			} else {
				if (status == TransmissionVars.TR_STATUS_CHECK_WAIT
						|| status == TransmissionVars.TR_STATUS_CHECK) {

					int id;
					int statusBigly = MapUtils.getMapInt(item,
							TransmissionVars.FIELD_TORRENT_STATUS + ".biglybt", -1);
					switch (statusBigly) {
						case 0: // STATE_WAITING       = 0;
							id = R.string.torrent_status_waiting;
							break;
						case 5: // STATE_INITIALIZING  = 5;
						case 10: // STATE_INITIALIZED   = 10;
							id = R.string.torrent_status_initializing; // possible duplicate -- there might be a tag, sometimes..
							break;
						case 20: // STATE_ALLOCATING = 20;
							id = R.string.torrent_status_alloc;
							break;
						case 65: // STOPPING
							id = R.string.torrent_status_stopping;
							break;
						case 30: // STATE_CHECKING = 30;
						default:
							id = R.string.torrent_status_checking;
					}

					text.append("|");
					text.append(resources.getString(id));
					text.append("|");
				}

				for (Object o : mapTagUIDs) {
					if (!(o instanceof Number)) {
						continue;
					}
					String name = null;
					int type;
					Map<?, ?> mapTag = session.tag.getTag(((Number) o).longValue());
					if (mapTag != null) {
						type = MapUtils.getMapInt(mapTag, TransmissionVars.FIELD_TAG_TYPE,
								0);
						if (type != 2) {
							continue;
						}
						String htmlColor = MapUtils.getMapString(mapTag,
								TransmissionVars.FIELD_TAG_COLOR, null);
						if (htmlColor != null && htmlColor.startsWith("#")) {
							color = Integer.decode("0x" + htmlColor.substring(1));
						}
						name = MapUtils.getMapString(mapTag,
								TransmissionVars.FIELD_TAG_NAME, null);
						// English hack.  If we had the tag-id, we could use 3 or 4
						if (name != null && name.startsWith("Queued for")) {
							name = resources.getString(R.string.statetag_queued);
						}
					}
					if (name == null) {
						continue;
					}
					if (text.length() > 0) {
						text.append(" ");
					}
					text.append("|");
					text.append(name);
					text.append("|");
				}
			}

			if (hasScrapeError) {
				if (text.length() > 0) {
					text.append(" ");
				}
				text.append("|");
				text.append(resources.getString(R.string.statetag_tracker_error));
				text.append("|");
			}

			if (MapUtils.getMapBoolean(item, TransmissionVars.FIELD_TORRENT_IS_FORCED,
					false)) {
				if (text.length() > 0) {
					text.append(" ");
				}
				text.append("|");
				text.append(resources.getString(R.string.statetag_force_started));
				text.append("|");
			}

			if (MapUtils.getMapBoolean(item,
					TransmissionVars.FIELD_TORRENT_SEQUENTIAL, false)) {
				if (text.length() > 0) {
					text.append(" ");
				}
				text.append("|");
				text.append(resources.getString(R.string.sequential_download));
				text.append("|");
			}

			SpannableStringBuilder ss = new SpannableStringBuilder(text);
			String string = text.toString();
			SpanBubbles.setSpanBubbles(ss, string, "|", holder.tvStatus.getPaint(),
					color < 0 ? colorBGTagState : color, colorFGTagState, colorBGTagState,
					null);
			flipper.changeText(holder.tvStatus, ss, holder.animateFlip, validator);
		}

		if (holder.tvTags != null && showTags) {
			ArrayList<Map<?, ?>> listTags = new ArrayList<>();
			if (mapTagUIDs != null) {
				for (Object o : mapTagUIDs) {
					int type;
					if (o instanceof Number) {
						Map<?, ?> mapTag = session.tag.getTag(((Number) o).longValue());
						if (mapTag != null) {
							type = MapUtils.getMapInt(mapTag, TransmissionVars.FIELD_TAG_TYPE,
									0);
							if (type == 2) {
								continue;
							}
							if (type == 1) {
								boolean canBePublic = MapUtils.getMapBoolean(mapTag,
										TransmissionVars.FIELD_TAG_CANBEPUBLIC, false);
								if (!canBePublic) {
									continue;
								}
							}
							listTags.add(mapTag);
						}
					}
				}
			}
			if (listTags.size() > 0) {
				try {
					// TODO: mebbe cache spanTags in holder?
					SpanTags spanTags = new SpanTags(holder.tvTags, null);

					//spanTags.setFlipper(flipper, validator);
					spanTags.setShowIcon(false);
					spanTags.setDrawCount(false);

					spanTags.setTagMaps(listTags);
					spanTags.updateTags();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			} else {
				//flipper.changeText(holder.tvTags, "", false, validator);
				holder.tvTags.setText("");
			}
		}
	}
}