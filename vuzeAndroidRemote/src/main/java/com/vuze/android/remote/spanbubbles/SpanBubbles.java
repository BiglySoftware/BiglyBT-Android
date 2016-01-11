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
 *
 */

package com.vuze.android.remote.spanbubbles;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.vuze.android.remote.SessionInfo;
import com.vuze.util.MapUtils;

import java.util.Map;

public class SpanBubbles {

	public static void setSpanBubbles(SessionInfo sessionInfo,
			final SpanBubbleClick spanBubbleClick, SpannableStringBuilder ss,
			String text, String token, final TextPaint p, final int textColor,
			final int fillColor, final Drawable rightIcon, final boolean selected) {
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

			String id = text.substring(start + tokenLen, end);

			Map mapTag = null;
			try {
				long tagUID = Long.parseLong(id);
				mapTag = sessionInfo.getTag(tagUID);
			} catch (Throwable ignore) {
			}

			final String word = MapUtils.getMapString(mapTag, "name", "" + id);

			Drawable imgDrawable = new DrawableTag(p, fillColor, textColor, word,
					rightIcon, mapTag, selected);

			float bottom = -p.ascent();
			float w = p.measureText(word + " ") + rightIconWidth + (bottom * 0.04f)
					+ 6 + (bottom / 2);
			int y = 0;

			imgDrawable.setBounds(0, y, (int) w, (int) (bottom * 1.1f));

			ImageSpan imageSpan = new ImageSpan(imgDrawable,
					DynamicDrawableSpan.ALIGN_BASELINE);

			ss.setSpan(imageSpan, start, end + tokenLen, 0);

			ClickableSpan clickSpan = new ClickableSpan() {
				@Override
				public void onClick(View widget) {
					Log.e("TUX", "Clicked on " + word);

					spanBubbleClick.spanBubbleClicked(word);
					//torrent.put(TransmissionVars.FIELD_TORRENT_TAGS, word);
				}
			};
			ss.setSpan(clickSpan, start, end + tokenLen, 0);
		}
	}

	/**
	 * Replaces TextView's text with span bubbles
	 */
	public void setSpanBubbles(TextView tv, String token,
			final int borderColor, final int textColor, final int fillColor) {
		if (tv == null) {
			return;
		}
		CharSequence text = tv.getText();

		SpannableStringBuilder ss = new SpannableStringBuilder(text);
		String string = text.toString();

		setSpanBubbles(ss, string, token, tv.getPaint(), borderColor, textColor,
				fillColor);
		tv.setText(ss);
	}

	/**
	 * Outputs span bubbles to ss based on text wrapped in token
	 */
	public void setSpanBubbles(SpannableStringBuilder ss, String text,
			String token, final TextPaint p, final int borderColor,
			final int textColor, final int fillColor) {
		if (ss.length() > 0) {
			// hack so ensure descent is always added by TextView
			ss.append("\u200B");
		}

		// Start and end refer to the points where the span will apply
		int tokenLen = token.length();
		int base = 0;

		while (true) {
			int start = text.indexOf(token, base);
			int end = text.indexOf(token, start + tokenLen);

			if (start < 0 || end < 0) {
				break;
			}

			base = end + tokenLen;

			final String word = text.substring(start + tokenLen, end);

			Drawable imgDrawable = new MyDrawable(word, p, fillColor, borderColor, textColor);

			float w = p.measureText(word + "__");
			float bottom = -p.ascent();
			int y = 0;

			imgDrawable.setBounds(0, y, (int) w, (int) bottom);

			ImageSpan imageSpan = new ImageSpan(imgDrawable,
					DynamicDrawableSpan.ALIGN_BASELINE);

			ss.setSpan(imageSpan, start, end + tokenLen, 0);
		}
	}

	private static class MyDrawable extends Drawable {

		private final String word;
		private final TextPaint p;
		private final int fillColor;
		private final int borderColor;
		private final int textColor;

		public MyDrawable(String word, TextPaint p, int fillColor, int borderColor, int textColor) {
			this.word = word;
			this.p = p;
			this.fillColor = fillColor;
			this.borderColor = borderColor;
			this.textColor = textColor;
		}

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
			float adjY = p.descent();

			RectF rectF = new RectF(bounds.left + wIndent,
					bounds.top + topIndent, bounds.right - (wIndent * 2),
					bounds.bottom + adjY);
			paintLine.setStyle(Paint.Style.FILL);
			paintLine.setColor(fillColor);
			canvas.drawRoundRect(rectF, bounds.height() / 3, bounds.height() / 3,
					paintLine);

			paintLine.setStrokeWidth(2);
			paintLine.setStyle(Paint.Style.STROKE);
			paintLine.setColor(borderColor);
			canvas.drawRoundRect(rectF, bounds.height() / 3, bounds.height() / 3,
					paintLine);

			paintLine.setStrokeWidth(strokeWidth);

			paintLine.setTextAlign(Paint.Align.CENTER);
			paintLine.setColor(textColor);
			paintLine.setSubpixelText(true);
			canvas.drawText(word, bounds.left + bounds.width() / 2, -p.ascent(),
					paintLine);
		}
	}
}
