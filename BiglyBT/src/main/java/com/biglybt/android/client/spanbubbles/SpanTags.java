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

package com.biglybt.android.client.spanbubbles;

import java.util.*;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;
import com.biglybt.android.client.adapter.TorrentListAdapter;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.TextViewFlipper;
import com.biglybt.util.Thunk;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.*;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.StateSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class SpanTags
{
	private static final String TAG = "SpanTags";

	public static final int TAG_STATE_SELECTED = 1;

	public static final int TAG_STATE_UNSELECTED = 0;

	public static final int TAG_STATE_UPDATING = 2;

	private Context context;

	private Session session;

	// Tag Drawables can be static, since we change the state within the Canvas
	// drawing
	private static StateListDrawable tagDrawables = null;

	@Thunk
	TextView tvTags;

	@Thunk
	SpanTagsListener listener;

	private final HashMap<Long, Map<?, ?>> mapTagIdsToTagMap = new LinkedHashMap<>(
			4);

	private final List<String> listAdditionalNames = new ArrayList<>(1);

	private TextViewFlipper flipper;

	private TextViewFlipper.FlipValidator validator;

	private boolean showIcon = true;

	private boolean drawCount = true;

	private float countFontRatio = 0;

	private boolean linkTags = true;

	@Thunk
	int lineSpaceExtra = 0;

	public SpanTags() {
	}

	public SpanTags(Context context, @Nullable Session session, TextView tvTags,
			@Nullable SpanTagsListener listener) {
		init(context, session, tvTags, listener);
	}

	public void init(Context context, @Nullable Session session, TextView tvTags,
			@Nullable SpanTagsListener listener) {
		this.context = context;
		this.session = session;
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
		mapTagIdsToTagMap.clear();
		for (Map map : listTagMaps) {
			mapTagIdsToTagMap.put((Long) map.get("uid"), map);
		}
	}

	public Collection<Map<?, ?>> getTagMaps() {
		return mapTagIdsToTagMap.values();
	}

	public void addTagNames(List<String> names) {
		for (String name : names) {
			if (!listAdditionalNames.contains(name)) {
				listAdditionalNames.add(name);
			}
		}
	}

	private StringBuilder buildSpannableString(List<Map> outTags) {
		StringBuilder sb = new StringBuilder();

		String token = "~!~";

		for (Map map : mapTagIdsToTagMap.values()) {
			sb.append(token);
			sb.append(MapUtils.getMapString(map, "name", "??"));
			sb.append(token);
			sb.append(' ');
			outTags.add(map);
		}

		for (String name : listAdditionalNames) {
			sb.append(token);
			sb.append(name);
			sb.append(token);
			sb.append(' ');
			outTags.add(null);
		}

		return sb;
	}

	public void setLineSpaceExtra(int lineSpaceExtra) {
		this.lineSpaceExtra = lineSpaceExtra;
	}

	private void setTagBubbles(final SpannableStringBuilder ss, final String text,
			final String token, final List<Map> outTags) {
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

		int index = 0;
		float lineWidth = 0;
		int lineMaxWidth = calcWidgetWidth(tvTags);
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

			Map mapTag = outTags.get(index);

			final String word = MapUtils.getMapString(mapTag, "name", "" + id);
			final Map fMapTag = mapTag;

			final int finalIndex = index;
			final DrawableTag imgDrawable = new DrawableTag(context, p, word,
					showIcon ? tagDrawables : null, mapTag, drawCount) {

				@Override
				public boolean isTagPressed() {
					if (!AndroidUtils.usesNavigationControl()) {
						return false;
					}
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
					return listener.getTagState(finalIndex, fMapTag, word);
				}

				@Override
				public int getLineSpaceExtra() {
					return lineSpaceExtra;
				}
			};

			if (countFontRatio > 0) {
				imgDrawable.setCountFontRatio(countFontRatio);
			}

			imgDrawable.setBounds(0, 0, imgDrawable.getIntrinsicWidth(),
					imgDrawable.getIntrinsicHeight());
//			Log.d(TAG, "State=" + Arrays.toString(imgDrawable.getState()));

			if (listener != null && showIcon) {
				int tagState = listener.getTagState(finalIndex, mapTag, word);
				int[] state = makeState(tagState, mapTag == null, false);
				imgDrawable.setState(state);
			}

			ImageSpan imageSpan = new ImageSpan(imgDrawable,
					DynamicDrawableSpan.ALIGN_BASELINE) {

				@Override
				public int getSize(Paint paint, CharSequence text, int start, int end,
						Paint.FontMetricsInt fm) {
					int size = super.getSize(paint, text, start, end, fm);
					int width = calcWidgetWidth(tvTags);
					if (width <= 0) {
						return size;
					}
					//imgDrawable.setBounds(0, 0, size, imgDrawable.getIntrinsicHeight());
					return Math.min(size, width);
				}
			};

			if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
				float w = imgDrawable.getIntrinsicWidth()
						+ tvTags.getPaint().measureText(" ");
				lineWidth += w;
				if (lineMaxWidth > 0 && lineWidth > lineMaxWidth) {
					lineWidth = w;
					if (start > 0) {
						ss.replace(start - 1, start, "\n");
					}
				}
			}

			ss.setSpan(imageSpan, start, end + tokenLen, 0);

			if (listener != null) {
				ClickableSpan clickSpan = new ClickableSpan() {

					@Override
					public void onClick(View widget) {
						listener.tagClicked(finalIndex, fMapTag, word);

						if (AndroidUtils.hasTouchScreen()) {
							Selection.removeSelection((Spannable) tvTags.getText());
						}
						widget.invalidate();
					}

					@Override
					public void updateDrawState(TextPaint ds) {
						// skip super, which draws underline on older Android
					}
				};

				ss.setSpan(clickSpan, start, end + tokenLen, 0);
			}

			index++;
		}
	}

	private int calcWidgetWidth(TextView tvTags) {
		int width = tvTags.getWidth();
		if (width <= 0
				|| tvTags.getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT) {
			if (tvTags.getParent() instanceof ViewGroup) {
				ViewGroup parent = (ViewGroup) tvTags.getParent();
				width = parent.getWidth() - parent.getPaddingLeft()
						- parent.getPaddingRight();
			}
		}
		return width;
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
		updateTags(false);
	}

	public void updateTags(boolean forceSet) {
		int lineMaxWidth = calcWidgetWidth(tvTags);
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			if (lineMaxWidth <= 0) {
				tvTags.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
					@TargetApi(Build.VERSION_CODES.HONEYCOMB)
					@Override
					public void onLayoutChange(View v, int left, int top, int right,
							int bottom, int oldLeft, int oldTop, int oldRight,
							int oldBottom) {
						tvTags.removeOnLayoutChangeListener(this);
						tvTags.post(new Runnable() {
							@Override
							public void run() {
								updateTags(true);
							}
						});
					}
				});
				return;
			}
		}

		List<Map> outTags = new ArrayList<>();
		StringBuilder sb = buildSpannableString(outTags);
		SpannableStringBuilder ss = new SpannableStringBuilder(sb);

		String string = sb.toString();

		setTagBubbles(ss, string, "~!~", outTags);

		if (flipper != null) {
			flipper.changeText(tvTags, ss, false, validator);
			return;
		}

		if (!string.equals(tvTags.getText().toString()) || forceSet) {
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

	/**
	 * @return true: Icon will be shown
	 */
	public boolean isShowIcon() {
		return showIcon;
	}

	public void setShowIcon(boolean showIcon) {
		this.showIcon = showIcon;
	}

	public interface SpanTagsListener
	{
		void tagClicked(int index, @Nullable Map mapTag, String name);

		int getTagState(int index, @Nullable Map mapTag, String name);
	}

	/**
	 * Sets whether to draw the count indicator
	 */
	public void setDrawCount(boolean drawCount) {
		this.drawCount = drawCount;
	}

	public void setCountFontRatio(float countFontRatio) {
		this.countFontRatio = countFontRatio;
	}

	public void setLinkTags(boolean linkTags) {
		this.linkTags = linkTags;
	}
}
