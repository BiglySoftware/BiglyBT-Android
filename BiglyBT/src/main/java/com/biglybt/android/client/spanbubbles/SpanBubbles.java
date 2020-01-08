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

import com.biglybt.android.client.AndroidUtils;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.TextView;

public class SpanBubbles
{

	/**
	 * Replaces TextView's text with span bubbles
	 *
	public void setSpanBubbles(TextView tv, String token, final int borderColor,
		final int textColor, final int fillColor,
		@Nullable final SpanBubbleListener listener) {
		if (tv == null) {
			return;
		}
		CharSequence text = tv.getText();
	
		SpannableStringBuilder ss = new SpannableStringBuilder(text);
		String string = text.toString();
	
		setSpanBubbles(ss, string, token, tv.getPaint(), borderColor, textColor,
			fillColor, listener);
		tv.setText(ss);
	}
		*/

	/**
	 * Replaces TextView's text with span bubbles
	 */
	public static void setSpanBubbles(TextView tv, String text, String token,
			final int borderColor, final int textColor, final int fillColor,
			@Nullable final SpanBubbleListener listener) {
		if (tv == null) {
			return;
		}
		SpannableStringBuilder ss = new SpannableStringBuilder(text);

		setSpanBubbles(ss, text, token, tv.getPaint(), borderColor, textColor,
				fillColor, listener);
		tv.setText(ss);
	}

	/**
	 * Outputs span bubbles to ss based on text wrapped in token
	 */
	public static void setSpanBubbles(final SpannableStringBuilder ss,
			String text, String token, final TextPaint p, final int borderColor,
			final int textColor, final int fillColor,
			@Nullable final SpanBubbleListener listener) {
		if (ss.length() > 0) {
			// hack so ensure descent is always added by TextView
			ss.append("\u200B");
		}

		// Start and end refer to the points where the span will apply
		int tokenLen = token.length();
		int base = 0;

		int index = 0;
		while (true) {
			int start = text.indexOf(token, base);
			int end = text.indexOf(token, start + tokenLen);

			if (start < 0 || end < 0) {
				break;
			}

			base = end + tokenLen;

			final String word = text.substring(start + tokenLen, end);

			final int finalIndex = index;
			Drawable imgDrawable = new MyDrawable(finalIndex, word, p, fillColor,
					borderColor, textColor, listener);

			float w = p.measureText(word + "__");
			float bottom = -p.ascent() + p.descent();
			int y = 0;

			imgDrawable.setBounds(0, y, (int) w, (int) (bottom + p.descent()));

			ImageSpan imageSpan = new ImageSpan(imgDrawable,
					DynamicDrawableSpan.ALIGN_BASELINE);

			ss.setSpan(imageSpan, start, end + tokenLen, 0);

			if (listener != null) {
				ClickableSpan clickSpan = new ClickableSpan() {

					@Override
					public void onClick(View widget) {
						listener.spanBubbleClicked(finalIndex, word);

						if (AndroidUtils.hasTouchScreen()) {
							Selection.removeSelection(ss);
						}
						widget.invalidate();
					}
				};
				ss.setSpan(clickSpan, start, end + tokenLen, 0);
			}

			index++;
		}
	}

	private static class MyDrawable
		extends Drawable
	{

		private final int index;

		private final String word;

		private final TextPaint p;

		private int fillColor;

		private int borderColor;

		private int textColor;

		private final SpanBubbleListener listener;

		public MyDrawable(int index, String word, TextPaint p, int fillColor,
				int borderColor, int textColor, @Nullable SpanBubbleListener listener) {
			this.index = index;
			this.word = word;
			this.p = p;
			this.fillColor = fillColor;
			this.borderColor = borderColor;
			this.textColor = textColor;
			this.listener = listener;
		}

		@Override
		public void setColorFilter(ColorFilter cf) {
		}

		@Override
		public void setAlpha(int alpha) {
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			Rect bounds = getBounds();

			if (listener != null) {
				int[] colors = listener.getColors(index, word, false);// TODO: Pressed
				if (colors != null && colors.length == 3) {
					fillColor = colors[2];
					borderColor = colors[0];
					textColor = colors[1];
				}
			}

			Paint paintLine = new Paint(p);
			paintLine.setAntiAlias(true);
			paintLine.setAlpha(255);

			float strokeWidth = paintLine.getStrokeWidth();

			float wIndent = bounds.height() * 0.02f;
			float topIndent = 1 + (p.descent());
			float adjY = p.descent();

			RectF rectF = new RectF(bounds.left + wIndent, bounds.top + topIndent,
					bounds.right - (wIndent * 2), bounds.bottom + adjY);
			paintLine.setStyle(Paint.Style.FILL);
			paintLine.setColor(fillColor);
			canvas.drawRoundRect(rectF, bounds.height() / 3.0f,
					bounds.height() / 3.0f, paintLine);

			paintLine.setStrokeWidth(2);
			paintLine.setStyle(Paint.Style.STROKE);
			paintLine.setColor(borderColor);
			canvas.drawRoundRect(rectF, bounds.height() / 3.0f,
					bounds.height() / 3.0f, paintLine);

			paintLine.setStrokeWidth(strokeWidth);

			paintLine.setTextAlign(Paint.Align.CENTER);
			paintLine.setColor(textColor);
			paintLine.setSubpixelText(true);
			paintLine.setStyle(Paint.Style.FILL_AND_STROKE);
			canvas.drawText(word, bounds.left + bounds.width() / 2.0f,
					-p.ascent() + (p.descent() / 2.0f) + topIndent, paintLine);
		}
	}
}
