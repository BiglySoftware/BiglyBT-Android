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

import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.content.Context;
import android.content.res.Resources;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.view.View;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.*;
import com.vuze.android.remote.activity.TorrentDetailsActivity;
import com.vuze.android.remote.fragment.TorrentListAdapter.ViewHolder;
import com.vuze.android.remote.fragment.TorrentListAdapter.ViewHolderFlipValidator;

/**
 * Fills one Torrent info row.
 * <p>
 * Split out from {@link TorrentListAdapter} so that 
 * {@link TorrentDetailsActivity} can use it for its top area
 */
public class TorrentListRowFiller
{
	private Resources resources;

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
		this.viewHolder = new ViewHolder(parentView);
	}

	protected TorrentListRowFiller(Context context) {
		this.context = context;
		resources = context.getResources();
		colorBGTagState = resources.getColor(R.color.bg_tag_type_2);
		colorFGTagState = resources.getColor(R.color.fg_tag_type_2);
		colorBGTagType0 = resources.getColor(R.color.bg_tag_type_0);
		colorFGTagType0 = resources.getColor(R.color.fg_tag_type_0);
		colorBGTagCat = resources.getColor(R.color.bg_tag_type_cat);
		colorFGTagCat = resources.getColor(R.color.fg_tag_type_cat);
		colorBGTagManual = resources.getColor(R.color.bg_tag_type_manualtag);
		colorFGTagManual = resources.getColor(R.color.fg_tag_type_manualtag);

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

		//		boolean isChecked = false;
		//		if (parent instanceof ListView) {
		//			isChecked = ((ListView) parent).isItemChecked(position);
		//			System.out.println(position + " checked? " + isChecked);
		//		}

		//		rowView.setBackgroundColor(isChecked
		//				? resources.getColor(R.color.list_bg_f) : 0);

		if (holder.tvName != null) {
			flipper.changeText(holder.tvName,
					AndroidUtils.lineBreaker(MapUtils.getMapString(item, "name", " ")),
					holder.animateFlip, validator);
		}

		float pctDone = MapUtils.getMapFloat(item, "percentDone", -1f);
		if (holder.tvProgress != null) {
			NumberFormat format = NumberFormat.getPercentInstance();
			format.setMaximumFractionDigits(1);
			String s = pctDone < 0 ? "" : format.format(pctDone);
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
				if (s.length() > 0) {
					s += "\n";
				}
				s += errorString;
			}

			flipper.changeText(holder.tvInfo, s, holder.animateFlip, validator);
		}
		if (holder.tvETA != null) {
			long etaSecs = MapUtils.getMapLong(item, "eta", -1);
			String eta = etaSecs > 0 && etaSecs * 1000l < DateUtils.WEEK_IN_MILLIS
					? DisplayFormatters.prettyFormat(etaSecs) : "";
			flipper.changeText(holder.tvETA, eta, holder.animateFlip, validator);
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
					text.append("| ");
					text.append(name);
					text.append(" |");
				}
			}

			SpannableString ss = new SpannableString(text);
			String string = text.toString();
			AndroidUtils.setSpanBubbles(ss, string, "|", holder.tvStatus.getPaint(),
					color < 0 ? colorBGTagState : color, colorFGTagState, colorBGTagState);
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
							//String htmlColor = MapUtils.getMapString(mapTag, "color", null);
							//if (htmlColor != null && htmlColor.startsWith("#")) {
							//	color = Long.decode("0x" + htmlColor.substring(1));
							//}
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
					sb.append(" ");
					sb.append(name);
					sb.append(" ");
					sb.append(token);
				}
			}
			if (sb.length() == 0) {
				flipper.changeText(holder.tvTags, "", holder.animateFlip, validator);
			} else {
				SpannableString ss = new SpannableString(sb);
				String string = sb.toString();
				int color = -1;
				AndroidUtils.setSpanBubbles(ss, string, "~0~",
						holder.tvTags.getPaint(), color < 0 ? colorBGTagType0 : color,
						colorFGTagType0, colorBGTagType0);
				AndroidUtils.setSpanBubbles(ss, string, "~1~",
						holder.tvTags.getPaint(), color < 0 ? colorBGTagCat : color,
						colorFGTagCat, colorBGTagCat);
				AndroidUtils.setSpanBubbles(ss, string, "~3~",
						holder.tvTags.getPaint(), color < 0 ? colorBGTagManual : color,
						colorFGTagManual, colorBGTagManual);
				flipper.changeText(holder.tvTags, ss, holder.animateFlip, validator);
			}
		}
	}
}