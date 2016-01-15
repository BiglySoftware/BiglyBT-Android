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

package com.vuze.android.remote.spanbubbles;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.support.v4.content.ContextCompat;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.StateSet;
import android.view.View;
import android.widget.TextView;

import com.vuze.android.remote.R;
import com.vuze.android.remote.SessionInfo;
import com.vuze.util.MapUtils;

import java.util.*;

public class SpanTags
{

	private final Context context;

	private final SessionInfo sessionInfo;

	private final StateListDrawable tagDrawables;

	private final TextView tvTags;

	private final int colorBGTagCat;

	private final int colorFGTagCat;

	private final int colorBGTagCatSel;

	private final int colorFGTagCatSel;

	private SpanTagsListener listener;

	private List<Map<?, ?>> listTags = new ArrayList<>();

	private List<String> listAdditionalNames = new ArrayList<>();

	public SpanTags(Context context, SessionInfo sessionInfo, TextView tvTags,
			SpanTagsListener listener) {
		this.context = context;
		this.sessionInfo = sessionInfo;
		this.tvTags = tvTags;
		this.listener = listener;

		colorBGTagCat = ContextCompat.getColor(context, R.color.bg_tag_type_0);
		colorFGTagCat = ContextCompat.getColor(context, R.color.fg_tag_type_0);
		colorBGTagCatSel = ContextCompat.getColor(context,
				R.color.bg_tag_type_manualtag);
		colorFGTagCatSel = ContextCompat.getColor(context,
				R.color.fg_tag_type_manualtag);

		Drawable drawableTag = ContextCompat.getDrawable(context, R.drawable.tag_q);
		Drawable drawableTagIdea = ContextCompat.getDrawable(context,
				R.drawable.tag_idea);
		Drawable drawableTagSelected = ContextCompat.getDrawable(context,
				R.drawable.tag_check);

		tagDrawables = new StateListDrawable();
		tagDrawables.addState(new int[] {
			android.R.attr.state_checked
		}, drawableTagSelected);
		tagDrawables.addState(new int[] {
			android.R.attr.state_single
		}, drawableTagIdea);
		tagDrawables.addState(StateSet.WILD_CARD, drawableTag);

	}

	public void setTagMaps(List<Map<?, ?>> listTagMaps) {
		listTags = listTagMaps;
	}

	public void addTagNames(List<String> names) {
		for (String name : names) {
			if (!listAdditionalNames.contains(name)) {
				listAdditionalNames.add(name);
			}
		}
	}

	private StringBuilder buildSpannableString() {
		StringBuilder sb = new StringBuilder();

		String token = "~!~";

		for (Map<?, ?> mapTag : listTags) {
			long uid = MapUtils.getMapLong(mapTag, "uid", 0);
			String name = MapUtils.getMapString(mapTag, "name", "??");
			//boolean selected = selectedTags.contains(name);
			//String token = selected ? "~s~" : "~|~";
			sb.append(token);
			sb.append(uid);
			sb.append(token);
			sb.append(' ');
		}

		for (String name : listAdditionalNames) {
			//boolean selected = selectedTags.contains(name);
			//String token = selected ? "~s~" : "~i~";
			sb.append(token);
			sb.append(name);
			sb.append(token);
			sb.append(' ');
		}

		return sb;
	}

	public void setTagBubbles(SpannableStringBuilder ss, String text,
			String token) {
		if (ss.length() > 0) {
			// hack to ensure descent is always added by TextView
			ss.append("\u200B");
		}

		TextPaint p = tvTags.getPaint();

		// Start and end refer to the points where the span will apply
		int tokenLen = token.length();
		int base = 0;

		final int rightIconWidth = tagDrawables.getIntrinsicWidth();
		final int rightIconHeight = tagDrawables.getIntrinsicHeight();

		while (true) {
			int start = text.indexOf(token, base);
			int end = text.indexOf(token, start + tokenLen);

			if (start < 0 || end < 0) {
				break;
			}

			base = end + tokenLen;

			String id = text.substring(start + tokenLen, end);

			Map mapTag = null;
			try {
				long tagUID = Long.parseLong(id);
				mapTag = sessionInfo.getTag(tagUID);
			} catch (Throwable ignore) {
			}

			final String word = MapUtils.getMapString(mapTag, "name", "" + id);

			final DrawableTag imgDrawable = new DrawableTag(p, word, tagDrawables,
					mapTag) {

				@Override
				public boolean isTagSelected() {
					return listener.isTagSelected(word);
				}
			};

			boolean selected = listener.isTagSelected(word);
			int[] state = makeState(selected, mapTag == null);
			imgDrawable.setState(state);

			Paint.FontMetrics fm = p.getFontMetrics();
			float bottom = (-p.ascent()) + p.descent();
			bottom = fm.bottom - fm.top;
			float w = p.measureText(word + " ") + rightIconWidth + (bottom * 0.04f)
					+ 6 + (bottom / 2);

			imgDrawable.setBounds(0, 0, (int) w, (int) (bottom * 1.1f));

			ImageSpan imageSpan = new ImageSpan(imgDrawable,
					DynamicDrawableSpan.ALIGN_BASELINE);

			ss.setSpan(imageSpan, start, end + tokenLen, 0);

			ClickableSpan clickSpan = new ClickableSpan() {

				@Override
				public void onClick(View widget) {
					listener.tagClicked(word);

					widget.invalidate();
				}
			};

			ss.setSpan(clickSpan, start, end + tokenLen, 0);
		}
	}

	public static int[] makeState(boolean selected, boolean isSuggestion) {
		int[] state = new int[0];
		if (selected) {
			int[] newState = new int[state.length + 1];
			System.arraycopy(state, 0, newState, 1, state.length);
			newState[0] = android.R.attr.state_checked;
			state = newState;
		}
		if (isSuggestion) {
			int[] newState = new int[state.length + 1];
			System.arraycopy(state, 0, newState, 1, state.length);
			newState[0] = android.R.attr.state_single;
			state = newState;
		}

		return state;
	}

	public void updateTags() {
		StringBuilder sb = buildSpannableString();
		SpannableStringBuilder ss = new SpannableStringBuilder(sb);

		String string = sb.toString();

		setTagBubbles(ss, string, "~!~");

		if (!ss.equals(tvTags.getText())) {
//			int start = tvTags.getSelectionStart();
//			int end = tvTags.getSelectionEnd();
//			Log.e(TAG, "Selection " + tvTags.getSelectionStart() + " to " +
//					tvTags.getSelectionEnd());
			tvTags.setTextKeepState(ss);
			//CharSequence so = tvTags.getText();
			//Selection.getSelectionStart(so);
//			if (start >= 0 && end >= 0) {
//				Selection.setSelection(ss, start, end);
//			}
//			tvTags.setText(ss);
//			if (start >= 0 && end >= 0) {
//				Selection.setSelection((Spannable) tvTags.getText(), start, end);
//			}
		}

	}

	public interface SpanTagsListener
	{
		void tagClicked(String name);

		boolean isTagSelected(String name);
	}
}
