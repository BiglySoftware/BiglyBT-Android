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

import java.util.Map;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.TransmissionVars;
import com.biglybt.android.util.MapUtils;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.text.TextPaint;
import android.util.Log;

public abstract class DrawableTag
	extends Drawable
{
	private static final boolean DEBUG = false && AndroidUtils.DEBUG;

	private static final String TAG = "DrawableTag";

	private static final boolean SHOW_COUNT_ON_SPLIT = true;

	private static final float MIN_FONT_SIZE = AndroidUtilsUI.spToPx(12);

	public static final String KEY_FILL_COLOR = "fillColor";

	public static final String KEY_ROUNDED = "rounded";

	private float SEGMENT_PADDING_Y_PX;

	private float STROKE_WIDTH_PX;

	private float HALF_STROKE_WIDTH_PX = STROKE_WIDTH_PX / 2;

	private float SEGMENT_PADDING_X_PX;

	private float SEGMENT_PADDING_MIDX_PX;

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

	private int lineSpaceExtra = 0;

	public DrawableTag(Context context, TextPaint p, String word,
			Drawable rightIcon, Map tag, boolean drawCount) {
		this.context = context;
		this.p = p;
		if (drawCount) {
			count = MapUtils.getMapLong(tag, TransmissionVars.FIELD_TAG_COUNT, 0);
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
		SEGMENT_PADDING_X_PX = 0;
		SEGMENT_PADDING_MIDX_PX = fontHeight * 0.2f;
		SEGMENT_PADDING_Y_PX = Math.max(1, fontHeight * 0.1f);
		float height = fontHeight + (SEGMENT_PADDING_Y_PX * 4) - fm.bottom;

		return (int) height + getLineSpaceExtra();
	}

	@Override
	public int getIntrinsicWidth() {
		TextPaint paintCopy = new TextPaint(p);

		Paint.FontMetrics fm = paintCopy.getFontMetrics();
		float fontHeight = fm.bottom - fm.top;

		STROKE_WIDTH_PX = Math.max(1, fontHeight * 0.1f);
		HALF_STROKE_WIDTH_PX = STROKE_WIDTH_PX / 2;
		SEGMENT_PADDING_X_PX = 0;
		SEGMENT_PADDING_MIDX_PX = fontHeight * 0.2f;
		SEGMENT_PADDING_Y_PX = Math.max(1, fontHeight * 0.1f);
		float height = fontHeight + (SEGMENT_PADDING_Y_PX * 4) - fm.bottom;

		// right icon eats 1/2 into right arc
		float rightIconWidth = rightIcon == null ? 0
				: ((height - (SEGMENT_PADDING_Y_PX * 2)) / 2) + SEGMENT_PADDING_MIDX_PX;

		float radius = height / 2;

		float wordWidthOriginal = paintCopy.measureText(word);
		float w = SEGMENT_PADDING_X_PX + STROKE_WIDTH_PX + wordWidthOriginal
				+ rightIconWidth + STROKE_WIDTH_PX + SEGMENT_PADDING_X_PX;

		if (MapUtils.getMapBoolean(mapTag, KEY_ROUNDED, false)) {
			w += radius;
		} else {
			w += (radius * 0.75); // let text leak into right rounded corder
		}

		if (drawCount && count > 0) {
			paintCopy = new TextPaint(p);
			float textSize = Math.max(MIN_FONT_SIZE,
					p.getTextSize() * countFontRatio);
			paintCopy.setTextSize(textSize);
			fmCount = paintCopy.getFontMetrics();
			String s = String.valueOf(count);
			countWidth = paintCopy.measureText(s);

			w += countWidth;
			w += SEGMENT_PADDING_MIDX_PX * 2;

			if (rightIcon == null) {
				w -= (radius / 2);
			}
		}

		return (int) w;
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
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

		bounds.bottom -= getLineSpaceExtra();

		Paint paintLine = new Paint(p);
		paintLine.setAntiAlias(true);
		paintLine.setAlpha(255);

		Rect clipBounds = canvas.getClipBounds();
		Paint.FontMetrics fm = p.getFontMetrics();

		if (DEBUG) {
			Log.d(TAG,
					word + "] cb=" + clipBounds + ";cb.w=" + clipBounds.width() + "c.w="
							+ canvas.getWidth() + ";bounds=" + bounds + ";p.asc=" + p.ascent()
							+ ";.desc=" + p.descent() + ";" + countWidth);
		}

		boolean splitWord = false;
		boolean overBounds = clipBounds.right < bounds.right; // cw < bw;
		if (overBounds) {
			float widthTextFull = p.measureText(word);
			float lostWidth = bounds.right - clipBounds.right;
			float widthTextRemaining = widthTextFull - lostWidth;

			bounds.right = bounds.left + clipBounds.right;

			// Don't squish too much
			splitWord = widthTextRemaining < widthTextFull * 0.55;
			if (splitWord) {
				paintLine.setTextSize(paintLine.getTextSize() / 2);
				drawCountThisTime = SHOW_COUNT_ON_SPLIT;

				int wordMiddle = findNiceMiddle(word);
				float line1Width = paintLine.measureText(word, 0, wordMiddle + 1);
				float line2Width = paintLine.measureText(word, wordMiddle,
						word.length());
				float newTextWidth = Math.max(line1Width, line2Width);
				int ofs = (int) (widthTextRemaining - newTextWidth);

				if (ofs > 0) {
					bounds.right -= ofs;
				} // else we ellipsize it later
			} else {
				paintLine.setTextScaleX(widthTextRemaining / widthTextFull);
			}

			// I'm not sure why, but when we are drawing the image into an area
			// that doesn't fit the width, the top few rows are being clipped too
			bounds.top += fm.bottom / 2;
			bounds.bottom += fm.bottom / 2;
		}

		//int baseline = bounds.bottom;
//		bounds.top -= fm.ascent - fm.top - 1;
		bounds.bottom += fm.bottom;

		float strokeWidth = paintLine.getStrokeWidth();

		int tagState = getTagState();

		if (mapTag != null) {
			Object color = mapTag.get(TransmissionVars.FIELD_TAG_COLOR);
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

			color = mapTag.get(KEY_FILL_COLOR);
			//Log.d(TAG, "draw " + word + " fillColor: " + color);
			if (color instanceof String) {
				fillColor = Color.parseColor((String) color);
				skipColorize = true;
			} else if (color instanceof Number) {
				fillColor = ((Number) color).intValue();
				skipColorize = true;
			}

			if (drawCount) {
				count = MapUtils.getMapLong(mapTag, TransmissionVars.FIELD_TAG_COUNT,
						0);
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

		float wIndent = SEGMENT_PADDING_X_PX + HALF_STROKE_WIDTH_PX;
		float hIndent = SEGMENT_PADDING_Y_PX + HALF_STROKE_WIDTH_PX;

		float radius = bounds.height() / 2.0f;

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
		if (MapUtils.getMapBoolean(mapTag, KEY_ROUNDED, false)) {
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
		paintLine.setAlpha(255);

		/*
		Path path2 = new Path();
		path2.addPath(path);
		paintLine.setColor(shadowColor);
		paintLine.setStyle(Paint.Style.STROKE);
		canvas.drawPath(path2, paintLine);
		*/

		// Fill tag insides
		///////////////////
		paintLine.setStyle(Paint.Style.FILL);

		paintLine.setColor(fillColor);
		canvas.drawPath(path, paintLine);

		// Draw line
		///////////////////
		paintLine.setStyle(Paint.Style.STROKE);
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

		// Calculate right image size/location
		float imageSize = 0;
		float imageX1 = 0;
		if (rightIcon != null) {
			imageSize = y2 - y1 - (STROKE_WIDTH_PX * 2);
			imageX1 = bounds.right - hIndent - HALF_STROKE_WIDTH_PX - imageSize;
		}

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

			float textIndent = SEGMENT_PADDING_X_PX + HALF_STROKE_WIDTH_PX
					+ SEGMENT_PADDING_MIDX_PX + addedTextIndent;

			float middleBoundsY = y1 + ((y2 - y1) / 2.0f);
			float widthForText = (bounds.right - bounds.left) - (STROKE_WIDTH_PX * 2)
					- (wIndent * 2);
			if (imageSize > 0) {
				widthForText -= (imageSize + wIndent);
			}
			if (drawCountThisTime) {
				widthForText -= (countWidth + SEGMENT_PADDING_MIDX_PX);
			}
			float middleBoundsX = (widthForText / 2.0f);

			Paint.FontMetrics fmSmall = paintLine.getFontMetrics();

			int textY1 = (int) (middleBoundsY - (fmSmall.descent));
			drawText(canvas, paintLine, word1, middleBoundsX, textY1, textIndent,
					bounds);

			int textY2 = (int) (middleBoundsY + (fmSmall.ascent * -1) - 1);
			drawText(canvas, paintLine, word2, middleBoundsX, textY2, textIndent,
					bounds);

		} else {
			float textIndent = SEGMENT_PADDING_X_PX + HALF_STROKE_WIDTH_PX
					+ SEGMENT_PADDING_MIDX_PX + addedTextIndent;

			int y = (int) (y1 + (((y2 - y1) / 2) - (fontHeight / 2) + (-fm.top))) - 1;
			canvas.drawText(word, bounds.left + textIndent, y, paintLine);
		}
		paintLine.setShadowLayer(0f, 0f, 0f, shadowColor);

//		paintLine.setStyle(Paint.Style.FILL);
//		paintLine.setColor(0x44ff0000);
//		canvas.drawRect(x1, y1, x2, y2, paintLine);
//		paintLine.setStyle(Paint.Style.FILL_AND_STROKE);
//		paintLine.setColor(textColor);

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
				countX1 = bounds.right - wIndent - HALF_STROKE_WIDTH_PX - countWidth;
				countX1 -= overBounds ? SEGMENT_PADDING_MIDX_PX / 2
						: SEGMENT_PADDING_MIDX_PX;
			} else {
				countX1 = imageX1 - countWidth - SEGMENT_PADDING_MIDX_PX;
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

			itemToDraw.setBounds((int) imageX1, (int) (y1 + STROKE_WIDTH_PX),
					(int) (imageX1 + imageSize), (int) (y2 - STROKE_WIDTH_PX));
			//Log.d(TAG, "draw: " + itemToDraw.getBounds());
			if (itemToDraw instanceof BitmapDrawable) {
				((BitmapDrawable) itemToDraw).setAntiAlias(true);
			}
			itemToDraw.draw(canvas);
//			Log.e(TAG, "drawing " + itemToDraw + " for " + word
//					+ " with state " + Arrays.toString(getState()) + ";" + selected);

		}
	}

	// returns overBounds
	private static boolean drawText(Canvas canvas, Paint paintLine, String word1,
			float middleBoundsX, int textY1, float textIndent, Rect bounds) {

		float width1 = paintLine.measureText(word1);

		float centerOffset1 = middleBoundsX - (width1 / 2);
		boolean overBounds = centerOffset1 < -1;
		if (overBounds) {
			while (word1.length() > 1) {
				word1 = word1.substring(0, word1.length() - 1);
				width1 = paintLine.measureText(word1 + "\u2026");

				centerOffset1 = middleBoundsX - (width1 / 2);
				if (centerOffset1 >= 0) {
					break;
				}
			}
			word1 += "\u2026";
		}

		float x1 = bounds.left + textIndent; //+ centerOffset1;
		//canvas.drawRect(x1 - centerOffset1, textY1,
		//		x1 - centerOffset1 + (middleBoundsX * 2), textY1 + 10, paintLine);
		canvas.drawText(word1, x1, textY1, paintLine);
		return overBounds;
	}

	private static int findNiceMiddle(String word) {
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
		int distanceRight = posRight - middle;
		int distanceLeft = middle - posLeft;
		return distanceLeft < distanceRight ? posLeft : posRight;
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

	public void setLineSpaceExtra(int lineSpaceExtra) {
		this.lineSpaceExtra = lineSpaceExtra;
	}

	public int getLineSpaceExtra() {
		return lineSpaceExtra;
	}
}
