package com.vuze.android.widget;

import android.content.Context;
import android.support.v4.widget.TextViewCompat;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * Text view that auto adjusts text size to fit within the view.
 * <p/>
 * Modified from code by  Chase Colburn, distributed on Apr 4, 2011
 */
public class AutoResizeTextView
	extends TextView
{
	public static final boolean DEBUG_AUTORESIZE = false;

	// Minimum text size for this text view
	public static final float MIN_TEXT_SIZE = 14;

	private static final String TAG = "ARTV";

	// Flag for text and/or size changes to force a resize
	private boolean mNeedsResize = false;

	// Text size that is set from code. This acts as a starting point for
	// resizing
	private float mTextSize;

	// Temporary upper bounds on the starting text size
	private float mMaxTextSize = 0;

	// Lower bounds for text size
	private float mMinTextSize = MIN_TEXT_SIZE;

	// Text view line spacing multiplier
	private float mSpacingMult = 1.0f;

	// Text view additional line spacing
	private float mSpacingAdd = 0.0f;

	private int mPreferedLineCount = 1;

	// Default constructor override
	public AutoResizeTextView(Context context) {
		this(context, null);
		mMaxTextSize = mTextSize = getTextSize();
	}

	// Default constructor when inflating from XML file
	public AutoResizeTextView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
		mMaxTextSize = mTextSize = getTextSize();
	}

	// Default constructor override
	public AutoResizeTextView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mMaxTextSize = mTextSize = getTextSize();
	}

	/**
	 * When text changes, set the force resize flag to true and reset the text
	 * size.
	 */
	@Override
	protected void onTextChanged(final CharSequence text, final int start,
			final int before, final int after) {
		mNeedsResize = true;
	}

	/**
	 * If the text view size changed, set the force resize flag to true
	 */
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		if (w != oldw || h != oldh) {
			mNeedsResize = true;
		}
	}

	/**
	 * Override the set text size to update our internal reference values
	 */
	@Override
	public void setTextSize(float size) {
		super.setTextSize(size);
		mTextSize = getTextSize();
	}

	/**
	 * Override the set text size to update our internal reference values
	 */
	@Override
	public void setTextSize(int unit, float size) {
		super.setTextSize(unit, size);
		mTextSize = getTextSize();
	}

	/**
	 * Override the set line spacing to update our internal reference values
	 */
	@Override
	public void setLineSpacing(float add, float mult) {
		super.setLineSpacing(add, mult);
		mSpacingMult = mult;
		mSpacingAdd = add;
	}

	/**
	 * Set the upper text size limit and invalidate the view
	 *
	 * @param maxTextSize
	 */
	public void setMaxTextSize(float maxTextSize) {
		mMaxTextSize = maxTextSize;
		requestLayout();
		invalidate();
	}

	/**
	 * Return upper text size limit
	 *
	 * @return
	 */
	public float getMaxTextSize() {
		return mMaxTextSize;
	}

	/**
	 * Set the lower text size limit and invalidate the view
	 *
	 * @param minTextSize size in px
	 */
	public void setMinTextSize(float minTextSize) {
		mMinTextSize = minTextSize;
		requestLayout();
		invalidate();
	}

	/**
	 * Return lower text size limit
	 *
	 * @return size in px
	 */
	public float getMinTextSize() {
		return mMinTextSize;
	}

	/**
	 * Resize text after measuring
	 */
	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		if (changed || mNeedsResize) {
			int widthLimit = (right - left) - getCompoundPaddingLeft()
					- getCompoundPaddingRight();
			int heightLimit = (bottom - top) - getCompoundPaddingBottom()
					- getCompoundPaddingTop();
			resizeText(widthLimit, heightLimit);
		}
		super.onLayout(changed, left, top, right, bottom);
	}

	/**
	 * Resize the text size with default width and height
	 */
	public void resizeText() {

		int heightLimit = getHeight() - getPaddingBottom() - getPaddingTop();
		int widthLimit = getWidth() - getPaddingLeft() - getPaddingRight();
		resizeText(widthLimit, heightLimit);
	}

	/**
	 * Resize the text size with specified width and height
	 *
	 * @param width
	 * @param height
	 */
	public void resizeText(int width, int height) {
		CharSequence text = getText();
		// Do not resize if the view does not have dimensions or there is no text
		if (text == null || text.length() == 0 || height <= 0 || width <= 0
				|| mTextSize == 0) {
			return;
		}

		if (getTransformationMethod() != null) {
			text = getTransformationMethod().getTransformation(text, this);
		}

		// Get the text view's paint object
		TextPaint textPaint = getPaint();

		// Store the current text size
		float oldTextSize = textPaint.getTextSize();

		int adjustX = getCompoundPaddingLeft() + getCompoundPaddingRight();
		int adjustedWidth = width - adjustX;

		float targetSize = findPerfectSize(text, textPaint, adjustedWidth,
				oldTextSize, mPreferedLineCount, false);

		// Some devices try to auto adjust line spacing, so force default line
		// spacing and invalidate the layout as a side effect
		if (targetSize != oldTextSize) {
			if (DEBUG_AUTORESIZE) {
				Log.d(TAG, "resizeText: " + text + ";newSize=" + targetSize);
			}
			setTextSize(TypedValue.COMPLEX_UNIT_PX, targetSize);
			setLineSpacing(mSpacingAdd, mSpacingMult);
		}

		// Reset force resize flag
		mNeedsResize = false;
	}

	private float findPerfectSize(CharSequence text, TextPaint textPaint,
			int adjustedWidth, float targetSize, int mPreferedLineCount,
			boolean lastTry) {
		boolean wantsOverSize = true;
		StaticLayout textLayout = getTextLayout(text, textPaint, adjustedWidth,
				targetSize);

		if (DEBUG_AUTORESIZE) {
			Log.d(TAG, text + ";lc=" + textLayout.getLineCount() + ";p="
					+ mPreferedLineCount);
		}
		if (textLayout.getLineCount() <= mPreferedLineCount) {
			// scale up
			if (DEBUG_AUTORESIZE) {
				Log.d(TAG,
						"resizeTextUp: " + text + ";" + targetSize + ";" + mMaxTextSize);
			}

			while (targetSize < mMaxTextSize) {
				float nextSize = Math.min(targetSize + 1, mMaxTextSize);
				textLayout = getTextLayout(text, textPaint, adjustedWidth, nextSize);
				if (textLayout.getLineCount() <= mPreferedLineCount) {
					targetSize = nextSize;
				} else {
					// too big
					wantsOverSize = false;
					break;
				}
			}

		} else {
			// scale down
			if (DEBUG_AUTORESIZE) {
				Log.d(TAG,
						"resizeTextDown: " + text + ";" + targetSize + ";" + mMinTextSize);
			}

			while (targetSize > mMinTextSize) {
				float nextSize = Math.max(targetSize - 1, mMinTextSize);
				textLayout = getTextLayout(text, textPaint, adjustedWidth, nextSize);
				if (textLayout.getLineCount() > mPreferedLineCount) {
					targetSize = nextSize;
				} else {
					wantsOverSize = false;
					break;
				}
			}
		}

		if (wantsOverSize && targetSize == mMinTextSize
				&& TextViewCompat.getMaxLines(this) != 1) {
			// set back to default size if it's multiline and we tried to shrink it to fit into one line, in hopes it will wrap
			// we could loop again, with a higher preferredlinecount..
			if (!lastTry) {
				int idxSpace = text.toString().indexOf(' ');
				if (idxSpace > 0) {
					return findPerfectSize(text.subSequence(0, idxSpace), textPaint,
							adjustedWidth, targetSize, mPreferedLineCount, true);
				}
			}
			return mMaxTextSize;
		}

		return targetSize;
	}

	// Set the text size of the text paint object and use a static layout to
	// render text off screen before measuring
	private StaticLayout getTextLayout(CharSequence source, TextPaint paint,
			int width, float textSize) {
		// modified: make a copy of the original TextPaint object for measuring
		// (apparently the object gets modified while measuring, see also the
		// docs for TextView.getPaint() (which states to access it read-only)
		TextPaint paintCopy = new TextPaint(paint);
		// Update the text paint object
		paintCopy.setTextSize(textSize);
		// Measure using a static layout

		return new StaticLayout(source, 0, source.length(), paintCopy, width,
				Alignment.ALIGN_NORMAL, mSpacingMult, mSpacingAdd, true);
	}

}