/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
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

import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.res.Resources;
import android.support.v4.content.*;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.view.View;

import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentDetailsActivity;
import com.vuze.android.remote.fragment.TorrentListAdapter.ViewHolder;
import com.vuze.android.remote.fragment.TorrentListAdapter.ViewHolderFlipValidator;
import com.vuze.android.remote.spanbubbles.SpanBubbles;
import com.vuze.util.DisplayFormatters;
import com.vuze.util.MapUtils;

/**
 * Fills one Torrent info row.
 * <p/>
 * Split out from {@link TorrentListAdapter} so that
 * {@link TorrentDetailsActivity} can use it for its top area
 */
public class TorrentListRowFiller
{
	private int colorBGTagState;

	private int colorFGTagState;

	private int colorBGTagType0;

	private int colorFGTagType0;

	private int colorBGTagCat;

	private int colorFGTagCat;

	private int colorBGTagManual;

	private int colorFGTagManual;

	private TextViewFlipper flipper;

	private Context context;

	private ViewHolder viewHolder;

	public TorrentListRowFiller(Context context, View parentView) {
		this(context);
		this.viewHolder = new ViewHolder(parentView, false);
	}

	protected TorrentListRowFiller(Context context) {
		this.context = context;
		Resources resources = context.getResources();
		colorBGTagState = ContextCompat.getColor(context, R.color.bg_tag_type_2);
		colorFGTagState = ContextCompat.getColor(context, R.color.fg_tag_type_2);
		colorBGTagType0 = ContextCompat.getColor(context, R.color.bg_tag_type_0);
		colorFGTagType0 = ContextCompat.getColor(context, R.color.fg_tag_type_0);
		colorBGTagCat = ContextCompat.getColor(context, R.color.bg_tag_type_cat);
		colorFGTagCat = ContextCompat.getColor(context, R.color.fg_tag_type_cat);
		colorBGTagManual = ContextCompat.getColor(context,
				R.color.bg_tag_type_manualtag);
		colorFGTagManual = ContextCompat.getColor(context,
				R.color.fg_tag_type_manualtag);

		flipper = new TextViewFlipper(R.anim.anim_field_change);
	}

	public void fillHolder(Map<?, ?> item, SessionInfo sessionInfo) {
		fillHolder(viewHolder, item, sessionInfo);
	}

	protected void fillHolder(ViewHolder holder, Map<?, ?> item,
			SessionInfo sessionInfo) {
		long torrentID = MapUtils.getMapLong(item, "id", -1);

		Resources resources = holder.tvName.getResources();

		holder.animateFlip = holder.torrentID == torrentID;
		holder.torrentID = torrentID;
		ViewHolderFlipValidator validator = new ViewHolderFlipValidator(holder,
				torrentID);

		if (holder.tvName != null) {
			flipper.changeText(holder.tvName,
					AndroidUtils.lineBreaker(MapUtils.getMapString(item, "name", " ")),
					holder.animateFlip, validator);
		}

		float pctDone = MapUtils.getMapFloat(item, "percentDone", -1f);
		if (holder.tvProgress != null) {
			NumberFormat format = NumberFormat.getPercentInstance();
			format.setMaximumFractionDigits(1);
			String s = pctDone < 0 || pctDone >= 1 ? "" : format.format(pctDone);
			flipper.changeText(holder.tvProgress, s, holder.animateFlip, validator);
		}
		if (holder.pb != null) {
			holder.pb.setIndeterminate(pctDone < 0);
			if (pctDone >= 0) {
				holder.pb.setProgress((int) (pctDone * 10000));
			}
		}
		if (holder.tvInfo != null) {
			int fileCount = MapUtils.getMapInt(item, "fileCount", 0);
			long size = MapUtils.getMapLong(item, "sizeWhenDone", 0);

			String s = resources.getQuantityString(R.plurals.torrent_row_info,
					fileCount, fileCount)
					+ resources.getString(R.string.torrent_row_info2,
							DisplayFormatters.formatByteCountToKiBEtc(size));
			long error = MapUtils.getMapLong(item, "error",
					TransmissionVars.TR_STAT_OK);
			if (error != TransmissionVars.TR_STAT_OK) {
				// error
				// TODO: parse error and add error type to message
				String errorString = MapUtils.getMapString(item, "errorString", "");
				if (holder.tvTrackerError != null) {
					flipper.changeText(holder.tvTrackerError, errorString,
							holder.animateFlip, validator);
				} else {
					if (s.length() > 0) {
						s += holder.isSmall
								? resources.getString(R.string.torrent_row_line_split) : "\n";
					}
					s += errorString;
				}
			} else if (holder.tvTrackerError != null) {
				flipper.changeText(holder.tvTrackerError, "", holder.animateFlip,
						validator);
			}

			flipper.changeText(holder.tvInfo, s, holder.animateFlip, validator);
		}
		if (holder.tvETA != null) {
			long etaSecs = MapUtils.getMapLong(item, "eta", -1);
			String s = "";
			if (etaSecs > 0 && etaSecs * 1000l < DateUtils.WEEK_IN_MILLIS) {
				s = DisplayFormatters.prettyFormat(etaSecs);
			} else if (pctDone >= 1) {
				float shareRatio = MapUtils.getMapFloat(item,
						TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO, -1);
				s = shareRatio < 0 ? ""
						: resources.getString(
								holder.isSmall ? R.string.torrent_row_share_ratio
										: R.string.torrent_row_share_ratio_circle,
								shareRatio);
			}
			flipper.changeText(holder.tvETA, s, holder.animateFlip, validator);
		}
		if (holder.tvUlRate != null) {
			long rateUpload = MapUtils.getMapLong(item, "rateUpload", 0);

			String rateString = rateUpload <= 0 ? "" : "\u25B2 "
					+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateUpload);
			flipper.changeText(holder.tvUlRate, rateString, holder.animateFlip,
					validator);
		}
		if (holder.tvDlRate != null) {
			long rateDownload = MapUtils.getMapLong(item, "rateDownload", 0);
			String rateString = rateDownload <= 0 ? "" : "\u25BC "
					+ DisplayFormatters.formatByteCountToKiBEtcPerSec(rateDownload);
			flipper.changeText(holder.tvDlRate, rateString, holder.animateFlip,
					validator);
		}

		if (holder.tvStatus != null) {
			List<?> mapTagUIDs = MapUtils.getMapList(item, "tag-uids", null);
			StringBuilder text = new StringBuilder();
			int color = -1;

			if (mapTagUIDs == null || mapTagUIDs.size() == 0) {

				int status = MapUtils.getMapInt(item,
						TransmissionVars.FIELD_TORRENT_STATUS,
						TransmissionVars.TR_STATUS_STOPPED);
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
					text.append(context.getString(id));
				}
			} else {
				for (Object o : mapTagUIDs) {
					String name = null;
					int type = 0;
					if (o instanceof Number) {
						Map<?, ?> mapTag = sessionInfo.getTag(((Number) o).longValue());
						if (mapTag != null) {
							String htmlColor = MapUtils.getMapString(mapTag, "color", null);
							if (htmlColor != null && htmlColor.startsWith("#")) {
								color = Integer.decode("0x" + htmlColor.substring(1));
							}
							name = MapUtils.getMapString(mapTag, "name", null);
							type = MapUtils.getMapInt(mapTag, "type", 0);
						}
					}
					if (type != 2) {
						continue;
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

			SpannableStringBuilder ss = new SpannableStringBuilder(text);
			String string = text.toString();
			new SpanBubbles().setSpanBubbles(ss, string, "|",
					holder.tvStatus.getPaint(), color < 0 ? colorBGTagState : color,
					colorFGTagState, colorBGTagState);
			flipper.changeText(holder.tvStatus, ss, holder.animateFlip, validator);
		}

		if (holder.tvTags != null) {
			List<?> mapTagUIDs = MapUtils.getMapList(item, "tag-uids", null);
			StringBuilder sb = new StringBuilder();
			if (mapTagUIDs != null) {
				for (Object o : mapTagUIDs) {
					String name = null;
					int type = 0;
					// TODO: Use Color
					//long color = -1;
					if (o instanceof Number) {
						Map<?, ?> mapTag = sessionInfo.getTag(((Number) o).longValue());
						if (mapTag != null) {
							type = MapUtils.getMapInt(mapTag, "type", 0);
							if (type == 2) {
								continue;
							}
							if (type == 1) {
								boolean canBePublic = MapUtils.getMapBoolean(mapTag,
										"canBePublic", false);
								if (!canBePublic) {
									continue;
								}
							}
							name = MapUtils.getMapString(mapTag, "name", null);
//							String htmlColor = MapUtils.getMapString(mapTag, "color", null);
//							if (htmlColor != null && htmlColor.startsWith("#")) {
//								color = Long.decode("0x" + htmlColor.substring(1));
//							}
						}
					}
					if (name == null) {
						continue;
					}
					if (sb.length() > 0) {
						sb.append(" ");
					}
					if (type > 3) {
						type = 3;
					}
					String token = "~" + type + "~";
					sb.append(token);
					sb.append(name);
					sb.append(token);
				}
			}
			if (sb.length() == 0) {
				flipper.changeText(holder.tvTags, "", holder.animateFlip, validator);
			} else {
				SpannableStringBuilder ss = new SpannableStringBuilder(sb);
				String string = sb.toString();
				int color = -1;
				new SpanBubbles().setSpanBubbles(ss, string, "~0~",
						holder.tvTags.getPaint(), color < 0 ? colorBGTagType0 : color,
						colorFGTagType0, colorBGTagType0);
				new SpanBubbles().setSpanBubbles(ss, string, "~1~",
						holder.tvTags.getPaint(), color < 0 ? colorBGTagCat : color,
						colorFGTagCat, colorBGTagCat);
				new SpanBubbles().setSpanBubbles(ss, string, "~3~",
						holder.tvTags.getPaint(), color < 0 ? colorBGTagManual : color,
						colorFGTagManual, colorBGTagManual);
				flipper.changeText(holder.tvTags, ss, holder.animateFlip, validator);
			}
		}
	}
}