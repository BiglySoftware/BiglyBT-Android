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

package com.vuze.android.remote.spanbubbles;

import java.util.Map;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.AndroidUtilsUI;
import com.vuze.util.MapUtils;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.Log;

public abstract class DrawableTag
	extends Drawable
{
	private static final boolean DEBUG = false && AndroidUtils.DEBUG;

	private static final String TAG = "DrawableTag";

	private static final boolean SHOW_COUNT_ON_SPLIT = false;

	private static final float MIN_FONT_SIZE = AndroidUtilsUI.spToPx(12);

	private float SEGMENT_PADDING_Y_PX;

	private float STROKE_WIDTH_PX;

	private float HALF_STROKE_WIDTH_PX = STROKE_WIDTH_PX / 2;

	private float SEGMENT_PADDING_X_PX;

	private final Context context;

	private final TextPaint p;

	private final String word;

	private final Drawable rightIcon;

	private final Map mapTag;

	private final boolean drawCount;

	private long count;

	private float countWidth;

	private Paint.FontMetrics fmCount;

	private float countFontRatio = 0.6f;

	public DrawableTag(Context context, TextPaint p, String word,
			Drawable rightIcon, Map tag, boolean drawCount) {
		this.context = context;
		this.p = p;
		if (drawCount) {
			count = MapUtils.getMapLong(tag, "count", 0);
		}
		this.word = word;
		this.rightIcon = rightIcon;
		this.mapTag = tag;
		this.drawCount = drawCount;

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
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public int getIntrinsicHeight() {
		Paint.FontMetrics fm = p.getFontMetrics();
		float fontHeight = fm.bottom - fm.top;

		STROKE_WIDTH_PX = Math.max(1, fontHeight * 0.1f);
		HALF_STROKE_WIDTH_PX = STROKE_WIDTH_PX / 2;
		SEGMENT_PADDING_X_PX = Math.max(1, fontHeight * 0.2f);
		SEGMENT_PADDING_Y_PX = Math.max(1, SEGMENT_PADDING_X_PX / 2);
		float height = fontHeight + (SEGMENT_PADDING_Y_PX * 4) - fm.bottom;

		return (int) height;
	}

	@Override
	public int getIntrinsicWidth() {
		TextPaint paintCopy = new TextPaint(p);

		Paint.FontMetrics fm = paintCopy.getFontMetrics();
		float fontHeight = fm.bottom - fm.top;

		STROKE_WIDTH_PX = Math.max(1, fontHeight * 0.1f);
		HALF_STROKE_WIDTH_PX = STROKE_WIDTH_PX / 2;
		SEGMENT_PADDING_X_PX = Math.max(1, fontHeight * 0.2f);
		SEGMENT_PADDING_Y_PX = Math.max(1, SEGMENT_PADDING_X_PX / 2);
		float height = fontHeight + (SEGMENT_PADDING_Y_PX * 4) - fm.bottom;

		float rightIconWidth = rightIcon == null ? 0
				: height - (SEGMENT_PADDING_X_PX * 3); // iconWidth = (height - padding*4), then add padding..

		float radius = height / 2;

		float wordWidthOriginal = paintCopy.measureText(word);
		float w = SEGMENT_PADDING_X_PX + STROKE_WIDTH_PX + SEGMENT_PADDING_X_PX
				+ wordWidthOriginal + SEGMENT_PADDING_X_PX + rightIconWidth + radius;

		if (drawCount && count > 0) {
			paintCopy = new TextPaint(p);
			float textSize = Math.max(MIN_FONT_SIZE,
					p.getTextSize() * countFontRatio);
			paintCopy.setTextSize(textSize);
			fmCount = paintCopy.getFontMetrics();
			String s = String.valueOf(count);
			countWidth = paintCopy.measureText(s);

			w += countWidth;
			w += SEGMENT_PADDING_X_PX;

			if (rightIcon == null) {
				w -= (radius / 2);
			}
		}

		return (int) w;
	}

	@Override
	public void draw(Canvas canvas) {
		int tagColor;
		int lineColor;
		int fillColor = 0;
		int textColor = 0;
		boolean skipColorize = false;
		boolean drawCountThisTime = drawCount;

		// when ImageSpan is ALIGN_BASELINE:
		// bounds.top = 0, starting at ascent
		// bounds.bottom = baseline
		Rect bounds = new Rect(getBounds());

		Paint paintLine = new Paint(p);
		paintLine.setAntiAlias(true);
		paintLine.setAlpha(255);

		Rect clipBounds = canvas.getClipBounds();

		if (DEBUG) {
			Log.d(TAG,
					word + "] cb=" + clipBounds + ";cb.w=" + clipBounds.width() + "c.w="
							+ canvas.getWidth() + ";bounds=" + bounds + ";p.asc=" + p.ascent()
							+ ";.desc=" + p.descent() + ";" + countWidth);
		}

		boolean splitWord = false;
		float cw = clipBounds.width();
		float bw = bounds.width();
		boolean overBounds = clipBounds.right < bounds.right; // cw < bw;
		if (overBounds) {
			float ofs = rightIcon == null
					? SEGMENT_PADDING_X_PX * 2 + (bounds.height() / 7.0f)
					: (SEGMENT_PADDING_X_PX * 2) + (bounds.height() / 2.0f);
			cw = cw - countWidth - ofs;
			bw = bw - countWidth - ofs;

			if (cw * 2 < bw) {
				if ((cw + countWidth) * 2 < (bw + countWidth)) {
					splitWord = true;
					paintLine.setTextSize(paintLine.getTextSize() / 2);
					drawCountThisTime = SHOW_COUNT_ON_SPLIT;
				} else {
					drawCountThisTime = false;
					cw += countWidth;
					bw += countWidth;
				}
			}

			if (!splitWord) {
				paintLine.setTextScaleX(cw / bw);
			}

			bounds.right = bounds.left + clipBounds.width();
		}

		//int baseline = bounds.bottom;
		Paint.FontMetrics fm = p.getFontMetrics();
//		bounds.top -= fm.ascent - fm.top - 1;
		bounds.bottom += fm.bottom;

		float strokeWidth = paintLine.getStrokeWidth();

		int tagState = getTagState();

		if (mapTag != null) {
			Object color = mapTag.get("color");
			//		Log.d(TAG, "draw " + word + " tagColor: " + color);
			if (color instanceof String) {
				tagColor = Color.parseColor((String) color);
			} else if (color instanceof Number) {
				tagColor = ((Number) color).intValue();
			} else {
				tagColor = AndroidUtilsUI.getStyleColor(context,
						android.R.attr.textColorSecondary); // AndroidUtilsUI.getStyleColor
				// (context, R.attr.bg_tag_type_0);
			}

			color = mapTag.get("fillColor");
			//Log.d(TAG, "draw " + word + " fillColor: " + color);
			if (color instanceof String) {
				fillColor = Color.parseColor((String) color);
				skipColorize = true;
			} else if (color instanceof Number) {
				fillColor = ((Number) color).intValue();
				skipColorize = true;
			}
		} else {
			tagColor = AndroidUtilsUI.getStyleColor(context,
					android.R.attr.textColorSecondary); // AndroidUtilsUI.getStyleColor
		}

		boolean selected = (tagState & SpanTags.TAG_STATE_SELECTED) > 0;
		boolean pressed = isTagPressed();
		boolean isIdea = mapTag == null;

		lineColor = tagColor;
		/* Shadow is ugly */

		float[] hsv = new float[3];
		Color.colorToHSV(tagColor, hsv);

		if (skipColorize) {
			textColor = tagColor;
		} else {
			//		Log.d(TAG, "draw " + word + " tagColor: " + Integer
			// .toHexString(tagColor) + "; pressed=" + pressed + ";" + selected);
			if (!selected && !pressed) {
				//			textColor = paintLine.getColor();
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
				int yiq = ((red * 299) + (green * 587) + (blue * 114)) / 1000;

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
		}

		Color.colorToHSV(tagColor, hsv);
		hsv[2] = 1.0f - hsv[2];
		int shadowColor = Color.HSVToColor(0x60, hsv);

		float wIndent = SEGMENT_PADDING_X_PX;
		float hIndent = SEGMENT_PADDING_Y_PX;

		float radius = bounds.height() / 2;

		if (rightIcon != null) {
			int[] state = SpanTags.makeState(tagState, isIdea, pressed);

			rightIcon.setState(state);
		}

		final float x1 = bounds.left + wIndent;
		final float x2 = bounds.right - wIndent - radius;
		final float y1 = bounds.top + hIndent;
		final float y2 = bounds.bottom - hIndent;
		if (DEBUG) {
			Log.d(TAG, "draw: " + x1 + "," + y1 + " to " + x2 + "," + y2);
		}

		float addedTextIndent = 0;

		// Setup tag path
		///////////////////
		Path path = new Path();
		if (MapUtils.getMapBoolean(mapTag, "rounded", false)) {
			path.addRoundRect(new RectF(x1, y1, x2 + radius, y2), radius, radius,
					Path.Direction.CW);
			addedTextIndent = radius / 4;
		} else {
			path.moveTo(x1, y1);
			path.lineTo(x2, y1);
			path.arcTo(new RectF(x2 - radius, y1, x2 + radius, y2), 270, 180);
			path.lineTo(x1, y2);
			path.lineTo(x1, y1);
		}
		path.close();

		// Draw Solid Shadow for line
		///////////////////
		paintLine.setStrokeWidth(STROKE_WIDTH_PX);
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

		// Fill tag insides
		///////////////////
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
		float fontHeight = fm.bottom - fm.top;
		if (splitWord) {
			int wordMiddle = findNiceMiddle(word);
			String word1 = word.substring(0, wordMiddle);
			String word2 = word.substring(wordMiddle);
			if (word2.startsWith(" ") && word2.length() > 1) {
				word2 = word2.substring(1);
			} else {
				word1 += "-";
			}

			float textIndent = STROKE_WIDTH_PX + HALF_STROKE_WIDTH_PX;

			float middleBoundsY = y1 + ((y2 - y1) / 2);
			float middleBoundsX;
			if (drawCountThisTime) {
				middleBoundsX = (bounds.right - bounds.left - countWidth - wIndent
						- wIndent - STROKE_WIDTH_PX) / 2.0f;
			} else {
				middleBoundsX = (bounds.right - bounds.left - wIndent - wIndent
						- STROKE_WIDTH_PX) / 2.0f;
			}

			int textY1 = (int) (middleBoundsY - fm.descent + 1);
			drawText(canvas, paintLine, word1, middleBoundsX, textY1, textIndent,
					bounds);

			int textY2 = (int) (middleBoundsY + (fm.ascent / -2) - 1);
			drawText(canvas, paintLine, word2, middleBoundsX, textY2, textIndent,
					bounds);

		} else {
			float textIndent = SEGMENT_PADDING_X_PX * 2 + HALF_STROKE_WIDTH_PX
					+ addedTextIndent;

			int y = (int) (y1 + ((y2 - y1) / 2) - (fontHeight / 2) + (-fm.top));
			canvas.drawText(word, bounds.left + textIndent, y, paintLine);
		}
		paintLine.setShadowLayer(0f, 0f, 0f, shadowColor);

//		paintLine.setStyle(Paint.Style.FILL);
//		paintLine.setColor(0x44ff0000);
//		canvas.drawRect(x1, y1, x2, y2, paintLine);
//		paintLine.setStyle(Paint.Style.FILL_AND_STROKE);
//		paintLine.setColor(textColor);

		float imageSize = 0;
		float imageX1 = 0;
		if (rightIcon != null) {
			imageSize = y2 - y1 - (hIndent * 2);
			imageX1 = bounds.right - hIndent - HALF_STROKE_WIDTH_PX - imageSize
					- SEGMENT_PADDING_X_PX;
		}

		// Draw Count

		if (drawCount && count > 0 && drawCountThisTime && fmCount != null) {
			float textSize = p.getTextSize();
			float textSizeCount = Math.max(MIN_FONT_SIZE, textSize * countFontRatio);
			paintLine.setTextSize(textSizeCount);
			paintLine.setTextScaleX(1.0f);
			String s = String.valueOf(count);
			float fontHeightCount = (-fmCount.top) + fmCount.bottom;
//			int countYofs = (int) (((bounds.bottom - bounds.top) / 2)
//					- (fontHeightCount / 2) + (-fmCount.top) + 0.5);
			paintLine.setAlpha(0xA0);

			float countX1;
			if (rightIcon == null) {
				countX1 = bounds.right - wIndent - STROKE_WIDTH_PX - countWidth;
				if (!splitWord) {
					countX1 -= (radius / 4);
				}
			} else {
				countX1 = imageX1 - countWidth - SEGMENT_PADDING_X_PX;
			}

			int y = (int) (y1 + ((y2 - y1) / 2) - (fontHeightCount / 2)
					+ (-fmCount.top));
			canvas.drawText(s, countX1, y, paintLine);
			paintLine.setAlpha(0xff);
			paintLine.setTextSize(textSize);
		}

		// Draw Icon
		///////////////////
		if (rightIcon != null) {
			Drawable itemToDraw;
			itemToDraw = rightIcon.getCurrent();

			itemToDraw.setBounds((int) imageX1, (int) (y1 + hIndent),
					(int) (imageX1 + imageSize), (int) (y2 - hIndent));
			//Log.d(TAG, "draw: " + itemToDraw.getBounds());
			if (itemToDraw instanceof BitmapDrawable) {
				((BitmapDrawable) itemToDraw).setAntiAlias(true);
			}
			itemToDraw.draw(canvas);
//			Log.e(TAG, "drawing " + itemToDraw + " for " + word
//					+ " with state " + Arrays.toString(getState()) + ";" + selected);

		}
	}

	private void drawText(Canvas canvas, Paint paintLine, String word1,
			float middleBoundsX, int textY1, float textIndent, Rect bounds) {

		float width1 = paintLine.measureText(word1);

		int radiusHalf = bounds.height() / 2;
		int maxWidth = bounds.width() - radiusHalf;
		boolean overBounds = width1 > maxWidth;
		if (overBounds) {
			while (word1.length() > 1) {
				word1 = word1.substring(0, word1.length() - 1);
				width1 = paintLine.measureText(word1 + "\u2026");

				if (width1 <= maxWidth) {
					break;
				}
			}
			word1 += "\u2026";
		}

		float centerOffset1 = middleBoundsX - (width1 / 2);
		canvas.drawText(word1, bounds.left + centerOffset1 + textIndent, textY1,
				paintLine);
	}

	private int findNiceMiddle(String word) {
		int middle = word.length() / 2;
		if (word.charAt(middle) == ' ') {
			return middle;
		}
		int posRight = word.indexOf(' ', middle);
		int posLeft = word.lastIndexOf(' ', middle);
		if (posLeft < 0 && posRight < 0) {
			return middle;
		}
		if (posLeft < 0) {
			return posRight;
		}
		if (posRight < 0) {
			return posLeft;
		}
		return Math.min(posLeft, posRight);
	}

	public void setCountFontRatio(float countFontRatio) {
		this.countFontRatio = countFontRatio;
	}

	public Map getTagMap() {
		return mapTag;
	}

	public String getWord() {
		return word;
	}
}
