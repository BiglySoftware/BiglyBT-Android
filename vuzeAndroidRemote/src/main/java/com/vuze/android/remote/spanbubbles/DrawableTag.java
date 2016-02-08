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

import java.util.Arrays;
import java.util.Map;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextPaint;
import android.util.Log;
import android.view.Gravity;

import com.vuze.android.remote.AndroidUtilsUI;
import com.vuze.android.remote.R;
import com.vuze.android.remote.VuzeRemoteApp;
import com.vuze.util.MapUtils;

public abstract class DrawableTag
	extends Drawable
{

	private final Context context;

	private final TextPaint p;

	private final String word;

	private final Drawable rightIcon;

	private final Map mapTag;

	public DrawableTag(Context context, TextPaint p, String word,
			Drawable rightIcon, Map tag) {
		this.context = context;
		this.p = p;
		this.word = word;
		this.rightIcon = rightIcon;
		this.mapTag = tag;
	}

	public abstract boolean isTagPressed();

	public abstract int getTagState();

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
		int tagColor;
		int lineColor;
		int fillColor;
		int textColor;

		// when ImageSpan is ALIGN_BASELINE:
		// bounds.top = 0, starting at ascent
		// bounds.bottom = baseline
		Rect bounds = new Rect(getBounds());

		Paint paintLine = new Paint(p);
		paintLine.setAntiAlias(true);
		paintLine.setAlpha(255);

//		Log.e("DrawableTag", "cb=" + canvas.getClipBounds() + ";bounds=" + bounds
//				+ ";p.asc=" + p.ascent() + ";.desc=" + p.descent());

		int baseline = bounds.bottom;
		Paint.FontMetrics fm = p.getFontMetrics();
		bounds.top -= fm.ascent - fm.top - 1;
		bounds.bottom += fm.descent;

		float strokeWidth = paintLine.getStrokeWidth();
		String color = MapUtils.getMapString(mapTag, "color", null);
		if (color != null) {
			tagColor = Color.parseColor(color);
		} else {
			tagColor = AndroidUtilsUI.getStyleColor(context, R.attr.bg_tag_type_0);
		}

		int tagState = getTagState();
		boolean selected = (tagState & SpanTags.TAG_STATE_SELECTED) > 0;
		boolean pressed = isTagPressed();
		boolean isIdea = mapTag == null;

		lineColor = tagColor;
		/* Shadow is ugly */

		float[] hsv = new float[3];
		Color.colorToHSV(tagColor, hsv);
		if (!selected && !pressed) {
			textColor = AndroidUtilsUI.getStyleColor(context,
					android.R.attr.textColorPrimary);
			fillColor = Color.HSVToColor(0x18, hsv);
		} else {
			lineColor = tagColor;

			if (pressed) {
				fillColor = Color.HSVToColor(0xc0, hsv);
			} else {
				fillColor = tagColor;
			}

			int red = Color.red(tagColor);
			int green = Color.green(tagColor);
			int blue = Color.blue(tagColor);
			int yiq = ((red*299)+(green*587)+(blue*114))/1000;

			if (yiq >= 128) {
				// black
				if (hsv[1] < 0.7f) {
					hsv[1] = 0.7f;
				}
				hsv[2] = 0.2f;
			} else {
				// white
				if (hsv[1] > 0.3f) {
					hsv[1] = 0.3f;
				}
				hsv[2] = hsv[2] < 0.5f ? 0.95f : 0.96f;
			}

			textColor = Color.HSVToColor(hsv);
		}

		Color.colorToHSV(tagColor, hsv);
		hsv[2] = 1.0f - hsv[2];
		int shadowColor = Color.HSVToColor(0x60, hsv);

		float wIndent = Math.max(1, bounds.height() * 0.1f);
		float hIndent = Math.max(1, bounds.height() * 0.1f);

		float radius = bounds.height() / 2;

		if (rightIcon != null) {
			int[] state = SpanTags.makeState(tagState, isIdea, pressed);

			rightIcon.setState(state);
		}

		float x1 = bounds.left + wIndent;
		float x2 = bounds.right - wIndent - radius;
		float y1 = bounds.top + hIndent;
		float y2 = bounds.bottom - hIndent;

		// Fill tag insides
		///////////////////
		Path path = new Path();
		path.moveTo(x1, y1);
		path.lineTo(x2, y1);
		path.arcTo(new RectF(x2 - radius, y1, x2 + radius, y2), 270, 180);
		path.lineTo(x1, y2);
		path.lineTo(x1, y1);
		path.close();

		paintLine.setStyle(Paint.Style.FILL);

		paintLine.setColor(fillColor);
		canvas.drawPath(path, paintLine);

		// Draw Tag Name
		///////////////////
		paintLine.setStyle(Paint.Style.FILL_AND_STROKE);
		paintLine.setTextAlign(Paint.Align.LEFT);
		paintLine.setAlpha(0xFF);
		paintLine.setColor(textColor);
		paintLine.setSubpixelText(true);
		paintLine.setShadowLayer(3f, 0f, 0f, shadowColor);
		float fontHeight = (-fm.top) + fm.bottom;
		int y = (int) (((bounds.bottom - bounds.top) / 2) - (fontHeight / 2)
				+ (-fm.ascent) + 0.5);
		canvas.drawText(word, (float) (bounds.left + (wIndent * 2.5)), y,
				paintLine);
		paintLine.setShadowLayer(0f, 0f, 0f, shadowColor);

		// Draw Solid Shadow for line
		///////////////////
		paintLine.setStrokeWidth(3);
		paintLine.setStyle(Paint.Style.STROKE);
		paintLine.setAlpha(255);

		Path path2 = new Path();
		path2.offset(2, 2);
		path2.addPath(path);
		paintLine.setColor(shadowColor);
		canvas.drawPath(path2, paintLine);

		// Draw line
		///////////////////
		paintLine.setColor(lineColor);
		//paintLine.setShadowLayer(1f, 1f, 1f, shadowColor);
//		if (!selected) {
//			paintLine.setPathEffect(new DashPathEffect(new float[] {
//				3,
//				3
//			}, 0));
//		}
		canvas.drawPath(path, paintLine);
//		paintLine.setPathEffect(null);
		paintLine.setStrokeWidth(strokeWidth);

		// Draw Icon
		///////////////////
		if (rightIcon != null) {
			Drawable itemToDraw;
			if (rightIcon instanceof StateListDrawable) {
				itemToDraw = ((StateListDrawable) rightIcon).getCurrent();
			} else {
				itemToDraw = rightIcon;
			}

			itemToDraw.setBounds(bounds.left, bounds.top,
					bounds.right - (int) (wIndent * 2) - 6, bounds.bottom);
			if (itemToDraw instanceof BitmapDrawable) {
				((BitmapDrawable) itemToDraw).setGravity(
						Gravity.CENTER_VERTICAL | Gravity.RIGHT);
				((BitmapDrawable) itemToDraw).setAntiAlias(true);
			}
			itemToDraw.draw(canvas);
			//		Log.e("DrawableTag", "drawing " + itemToDraw + " for " + word
			//				+ " with state " + Arrays.toString(getState()) + ";" + selected);

		}
	}
}
