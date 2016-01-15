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

import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.support.v4.content.ContextCompat;
import android.text.TextPaint;
import android.util.Log;
import android.util.StateSet;
import android.view.Gravity;

import com.vuze.android.remote.R;
import com.vuze.android.remote.VuzeRemoteApp;
import com.vuze.util.MapUtils;

import java.util.*;

public abstract class DrawableTag
	extends Drawable
{

	private final TextPaint p;

	private final String word;

	private final Drawable rightIcon;

	private final Map mapTag;

	public DrawableTag(TextPaint p, String word, Drawable rightIcon, Map tag) {
		this.p = p;
		this.word = word;
		this.rightIcon = rightIcon;
		this.mapTag = tag;
	}

	public abstract boolean isTagSelected();

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
		int fillColor;
		String color = MapUtils.getMapString(mapTag, "color", null);
		if (color != null) {
			fillColor = Color.parseColor(color);
		} else {
			fillColor = ContextCompat.getColor(VuzeRemoteApp.getContext(),
					R.color.bg_tag_type_0);
		}

		/* Shadow is ugly */
		int shadowColor = fillColor;
		{ //if (!selected) {
			float[] hsv = new float[3];
			Color.colorToHSV(fillColor, hsv);
			hsv[2] = 1.0f - hsv[2];
			shadowColor = Color.HSVToColor(0x60, hsv);
		}

		float wIndent = Math.max(1, bounds.height() * 0.1f);
		float hIndent = Math.max(1, bounds.height() * 0.1f);

		float radius = bounds.height() / 2;

		boolean selected = isTagSelected();
		boolean isIdea = mapTag == null;

		int[] state = SpanTags.makeState(selected, isIdea);

		boolean changed = rightIcon.setState(state);

		float x1 = bounds.left + wIndent;
		float x2 = bounds.right - wIndent - radius;
		float y1 = bounds.top + hIndent;
		float y2 = bounds.bottom - hIndent;

		Path path = new Path();
		path.moveTo(x1, y1);
		path.lineTo(x2, y1);
		path.arcTo(new RectF(x2 - radius, y1, x2 + radius, y2), 270, 180);
		path.lineTo(x1, y2);
		path.lineTo(x1, y1);
		path.close();

		paintLine.setStyle(Paint.Style.FILL);
		paintLine.setColor(fillColor);
		paintLine.setAlpha(selected ? 0x80 : 0x20);
		canvas.drawPath(path, paintLine);

		paintLine.setStyle(Paint.Style.FILL_AND_STROKE);
		paintLine.setTextAlign(Paint.Align.LEFT);
		paintLine.setColor(fillColor);
		paintLine.setSubpixelText(true);
		paintLine.setShadowLayer(3f, 0f, 0f, shadowColor);
		float fontHeight = (-fm.top) + fm.bottom;
		int y = (int) (((bounds.bottom - bounds.top) / 2) - (fontHeight / 2)
				+ (-fm.ascent));
		canvas.drawText(word, bounds.left + (wIndent * 3), y, paintLine);

		paintLine.setShadowLayer(0f, 0f, 0f, shadowColor);
		paintLine.setStrokeWidth(2);
		paintLine.setStyle(Paint.Style.STROKE);
		paintLine.setAlpha(255);

		Path path2 = new Path();
		path2.offset(2, 2);
		path2.addPath(path);
		paintLine.setColor(shadowColor);
		canvas.drawPath(path2, paintLine);

		paintLine.setColor(fillColor);
		//paintLine.setShadowLayer(1f, 1f, 1f, shadowColor);
		canvas.drawPath(path, paintLine);

		paintLine.setStrokeWidth(strokeWidth);

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
