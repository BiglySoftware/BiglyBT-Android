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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.support.v4.content.ContextCompat;
import android.text.*;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.StateSet;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.vuze.android.remote.*;
import com.vuze.android.remote.fragment.TorrentListAdapter;
import com.vuze.util.MapUtils;

public class SpanTags
{
	private static final String TAG = "SpanTags";

	public static final int TAG_STATE_SELECTED = 1;

	public static final int TAG_STATE_UNSELECTED = 0;

	public static final int TAG_STATE_UPDATING = 2;

	private Context context;

	private SessionInfo sessionInfo;

	// Tag Drawables can be static, since we change the state within the Canvas
	// drawing
	private static StateListDrawable tagDrawables;

	private TextView tvTags;

	private SpanTagsListener listener;

	private List<Map<?, ?>> listTags = new ArrayList<>();

	private List<String> listAdditionalNames = new ArrayList<>();

	private TextViewFlipper flipper;

	private TextViewFlipper.FlipValidator validator;

	private boolean showIcon = true;

	public SpanTags() {
	}

	public SpanTags(Context context, SessionInfo sessionInfo, TextView tvTags,
			SpanTagsListener listener) {
		init(context, sessionInfo, tvTags, listener);
	}

	public void init(Context context, SessionInfo sessionInfo, TextView tvTags,
			SpanTagsListener listener) {
		this.context = context;
		this.sessionInfo = sessionInfo;
		this.tvTags = tvTags;
		this.listener = listener;
	}

	private void createDrawTagables() {
		if (tagDrawables != null) {
			return;
		}
		if (context == null) {
			return;
		}

		Drawable drawableTag = ContextCompat.getDrawable(context, R.drawable.tag_q);
		Drawable drawableTagIdea = ContextCompat.getDrawable(context,
				R.drawable.tag_idea);
		Drawable drawableTagPending = ContextCompat.getDrawable(context,
				R.drawable.tag_pending);
		Drawable drawableTagSelected = ContextCompat.getDrawable(context,
				R.drawable.tag_check);

		tagDrawables = new StateListDrawable();
		tagDrawables.addState(new int[] {
			android.R.attr.state_middle
		}, drawableTagPending);
		tagDrawables.addState(new int[] {
			android.R.attr.state_middle,
			android.R.attr.state_checked
		}, drawableTagPending);
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

	public List<Map<?, ?>> getTagMaps() {
		return listTags;
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
			sb.append(token);
			sb.append(uid);
			sb.append(token);
			sb.append(' ');
		}

		for (String name : listAdditionalNames) {
			sb.append(token);
			sb.append(name);
			sb.append(token);
			sb.append(' ');
		}

		return sb;
	}

	public void setTagBubbles(final SpannableStringBuilder ss, String text,
			String token) {
		if (ss.length() > 0) {
			// hack to ensure descent is always added by TextView
			ss.append("\u200B");
		}

		if (tvTags == null) {
			Log.e(TAG, "no tvTags");
			return;
		}

		TextPaint p = tvTags.getPaint();

		// Start and end refer to the points where the span will apply
		int tokenLen = token.length();
		int base = 0;

		if (showIcon && tagDrawables == null) {
			createDrawTagables();
		}

		final int rightIconWidth = !showIcon || tagDrawables == null ? 0
				: tagDrawables.getIntrinsicWidth();
		//final int rightIconHeight = tagDrawables.getIntrinsicHeight();

		while (true) {
			int start = text.indexOf(token, base);
			int end = text.indexOf(token, start + tokenLen);

			if (start < 0 || end < 0) {
				break;
			}

			base = end + tokenLen;

			final int fSpanStart = start;
			final int fSpanEnd = end + tokenLen;

			String id = text.substring(start + tokenLen, end);

			Map mapTag = null;
			try {
				long tagUID = Long.parseLong(id);
				mapTag = sessionInfo.getTag(tagUID);
			} catch (Throwable ignore) {
			}

			final String word = MapUtils.getMapString(mapTag, "name", "" + id);
			final Map fMapTag = mapTag;

			final DrawableTag imgDrawable = new DrawableTag(context, p, word,
					showIcon ? tagDrawables : null, mapTag) {

				@Override
				public boolean isTagPressed() {
					int selectionEnd = tvTags.getSelectionEnd();
					if (selectionEnd < 0) {
						return false;
					}
					int selectionStart = tvTags.getSelectionStart();
					return selectionStart == fSpanStart && selectionEnd == fSpanEnd;
				}

				@Override
				public int getTagState() {
					if (listener == null) {
						return TAG_STATE_SELECTED;
					}
					return listener.getTagState(fMapTag, word);
				}
			};

//			Log.d(TAG, "State=" + Arrays.toString(imgDrawable.getState()));

			if (listener != null && showIcon) {
				int tagState = listener.getTagState(mapTag, word);
				int[] state = makeState(tagState, mapTag == null, false);
				imgDrawable.setState(state);
			}

			Paint.FontMetrics fm = p.getFontMetrics();
			float bottom = (-p.ascent()) + p.descent();
			bottom = fm.bottom - fm.top;
			float w = p.measureText(word + " ") + rightIconWidth + (bottom * 0.04f)
					+ 6 + (bottom / 2);

			imgDrawable.setBounds(0, 0, (int) w, (int) (bottom * 1.1f));

			ImageSpan imageSpan = new ImageSpan(imgDrawable,
					DynamicDrawableSpan.ALIGN_BASELINE);

			ss.setSpan(imageSpan, start, end + tokenLen, 0);

			if (listener != null) {
				ClickableSpan clickSpan = new ClickableSpan() {

					@Override
					public void onClick(View widget) {
						listener.tagClicked(fMapTag, word);

						if (AndroidUtils.hasTouchScreen()) {
							Selection.removeSelection((Spannable) tvTags.getText());
						}
						widget.invalidate();
					}
				};

				ss.setSpan(clickSpan, start, end + tokenLen, 0);
			}

		}
	}

	public static int[] makeState(int tagState, boolean isSuggestion,
			boolean isTouched) {
		int[] state = new int[0];
		if ((tagState & SpanTags.TAG_STATE_SELECTED) > 0) {
			int[] newState = new int[state.length + 1];
			System.arraycopy(state, 0, newState, 1, state.length);
			newState[0] = android.R.attr.state_checked;
			state = newState;
		}
		if ((tagState & SpanTags.TAG_STATE_UPDATING) > 0) {
			int[] newState = new int[state.length + 1];
			System.arraycopy(state, 0, newState, 1, state.length);
			newState[0] = android.R.attr.state_middle;
			state = newState;
		}
		if (isSuggestion) {
			int[] newState = new int[state.length + 1];
			System.arraycopy(state, 0, newState, 1, state.length);
			newState[0] = android.R.attr.state_single;
			state = newState;
		}
		if (isTouched) {
			int[] newState = new int[state.length + 1];
			System.arraycopy(state, 0, newState, 1, state.length);
			newState[0] = android.R.attr.state_pressed;
			state = newState;
		}

		return state;
	}

	public void updateTags() {
		StringBuilder sb = buildSpannableString();
		SpannableStringBuilder ss = new SpannableStringBuilder(sb);

		String string = sb.toString();

		setTagBubbles(ss, string, "~!~");

		if (flipper != null) {
			flipper.changeText(tvTags, ss, false, validator);
			return;
		}

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

	public void setFlipper(TextViewFlipper flipper,
			TorrentListAdapter.ViewHolderFlipValidator validator) {
		this.flipper = flipper;
		this.validator = validator;
	}

	public boolean isShowIcon() {
		return showIcon;
	}

	public void setShowIcon(boolean showIcon) {
		this.showIcon = showIcon;
	}

	public interface SpanTagsListener
	{
		void tagClicked(Map mapTag, String name);

		int getTagState(Map mapTag, String name);
	}
}
