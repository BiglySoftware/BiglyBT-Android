package com.vuze.android.remote.spanbubbles;

import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;

import com.vuze.util.MapUtils;

import java.util.Map;

/**
 * Created by Vuze on 12/28/15.
 */
public class DrawableTag
		extends Drawable
{

	private final TextPaint p;

	private final int fillColor;

	private final int textColor;

	private final String word;

	private final Drawable rightIcon;

	private final Map mapTag;

	private final boolean selected;

	public DrawableTag(TextPaint p, int fillColor, int textColor, String word,
			Drawable rightIcon, Map tag, boolean selected) {
		this.p = p;
		this.fillColor = fillColor;
		this.textColor = textColor;
		this.word = word;
		this.rightIcon = rightIcon;
		this.mapTag = tag;
		this.selected = selected;
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
		int fillColor = this.fillColor;
		String color = MapUtils.getMapString(mapTag, "color", null);
		if (color != null) {
			fillColor = Color.parseColor(color);
		}

		float wIndent = bounds.height() * 0.02f;
		float topIndent = 1;
		float adjY = p.descent() - 1;

		float radius = bounds.height() / 2;
		RectF rectF = new RectF(bounds.left + wIndent, bounds.top + topIndent,
				bounds.right - (wIndent * 2), bounds.bottom + adjY);
		paintLine.setStyle(Paint.Style.FILL);
		paintLine.setColor(fillColor);
		paintLine.setAlpha(selected ? 0x80 : 0x20);

		float x1 = bounds.left + wIndent;
		float x2 = bounds.right - (wIndent * 2) - radius;
		float y1 = bounds.top + topIndent;
		float y2 = bounds.bottom + adjY;

		Path path = new Path();
		path.moveTo(x1, y1);
		path.lineTo(x2, y1);
		path.arcTo(new RectF(x2 - radius, y1, x2 + radius, y2), 270, 180);
		path.lineTo(x1, y2);
		path.lineTo(x1, y1);

		canvas.drawPath(path, paintLine);
		//canvas.drawRoundRect(rectF, radius, radius, paintLine);

		paintLine.setStrokeWidth(2);
		paintLine.setStyle(Paint.Style.STROKE);
		paintLine.setColor(fillColor);
		paintLine.setAlpha(255);
		canvas.drawPath(path, paintLine);
		//canvas.drawRoundRect(rectF, radius, radius, paintLine);

		paintLine.setStrokeWidth(strokeWidth);

		paintLine.setTextAlign(Paint.Align.LEFT);
		paintLine.setColor(textColor);
		paintLine.setColor(fillColor);
		paintLine.setSubpixelText(true);
		/* Shadow is ugly */
		int shadowColor = fillColor;
		{ //if (!selected) {
			float[] hsv = new float[3];
			Color.colorToHSV(fillColor, hsv);
			hsv[2] = 1.0f - hsv[2];
			shadowColor = Color.HSVToColor(0x60, hsv);
		}
		paintLine.setShadowLayer(3f, 0f, 0f, shadowColor);
		/**/
		canvas.drawText(word, bounds.left + (bounds.height() / 2), -p.ascent(),
				paintLine);

		rightIcon.setBounds(bounds.left, bounds.top,
				bounds.right - (int) (wIndent * 2) - 6, (int) (bounds.bottom + adjY));
		if (rightIcon instanceof BitmapDrawable) {
			((BitmapDrawable) rightIcon).setGravity(
					Gravity.CENTER_VERTICAL | Gravity.RIGHT);
			((BitmapDrawable) rightIcon).setAntiAlias(true);
		}
		rightIcon.draw(canvas);
	}
}
