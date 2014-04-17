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

package com.vuze.android.remote;

import android.text.SpannableString;
import android.util.Log;
import android.view.View;
import android.view.animation.*;
import android.view.animation.Animation.AnimationListener;
import android.widget.TextView;

public class TextViewFlipper
{
	private static final boolean DEBUG_FLIPPER = false;

	private int animId;

	public static interface FlipValidator
	{
		public boolean isStillValid();
	}

	public TextViewFlipper(int animId) {
		this.animId = animId;
	}

	public String meh(View v) {
		int id = v.getId();
		String idText = id == View.NO_ID ? ""
				: v.getContext().getResources().getResourceEntryName(id);
		return idText;
	}

	/**
	 * Change the text on repeat of Animation.
	 * 
	 * @param tv Widget to update
	 * @param newText New Text to set
	 * @param animate false to set right away, true to wait
	 * @param validator when animated, validator will be called to determine
	 * if text setting is still required
	 */
	public void changeText(final TextView tv, final CharSequence newText,
			boolean animate, final FlipValidator validator) {
		if (DEBUG_FLIPPER) {
			Log.d("flipper", meh(tv) + "] changeText: '" + newText + "';"
					+ (animate ? "animate" : "now"));
		}
		if (!animate) {
			tv.setText(newText);
			tv.setVisibility(newText.length() == 0 ? View.GONE : View.VISIBLE);
			return;
		}
		if (!newText.toString().equals(tv.getText().toString())) {
			flipIt(tv, new AnimationAdapter() {
				@Override
				public void onAnimationRepeat(Animation animation) {
					if (validator != null && !validator.isStillValid()) {
						if (DEBUG_FLIPPER) {
							Log.d("flipper", meh(tv) + "] changeText: no longer valid");
						}
						return;
					}
					if (DEBUG_FLIPPER) {
						Log.d("flipper", meh(tv) + "] changeText: setting to " + newText);
					}
					tv.setText(newText);
					tv.setVisibility(newText.length() == 0 ? View.GONE : View.VISIBLE);
				}
			});
		} else {
			if (DEBUG_FLIPPER) {
				Log.d("flipper", meh(tv) + "] changeText: ALREADY " + newText);
			}
		}
	}

	private void flipIt(View view, AnimationListener l) {
		Animation animation = AnimationUtils.loadAnimation(view.getContext(),
				animId);
		// Some Android versions won't animate when view is GONE
		if (view.getVisibility() == View.GONE) {
			if (DEBUG_FLIPPER) {
				Log.d("flipper", meh(view)
						+ "] changeText: view gone.. need to make visible");
			}
			if (view instanceof TextView) {
				// Some Android versions won't animate when text is ""
				((TextView) view).setText(" ");
			}
			view.setVisibility(View.VISIBLE);
		}
		if (l != null) {
			if (animation instanceof AnimationSet) {
				AnimationSet as = (AnimationSet) animation;
				as.getAnimations().get(0).setAnimationListener(l);
			} else {
				animation.setAnimationListener(l);
			}
		}
		view.startAnimation(animation);
	}

	public void changeText(final TextView tv, final SpannableString newText,
			boolean animate, final FlipValidator validator) {
		String newTextString = newText.toString();
		if (!animate) {
			tv.setText(newText);
			tv.setVisibility(newTextString.length() == 0 ? View.GONE : View.VISIBLE);
			return;
		}
		if (!newTextString.equals(tv.getText().toString())) {
			flipIt(tv, new AnimationAdapter() {
				@Override
				public void onAnimationRepeat(Animation animation) {
					if (validator != null && !validator.isStillValid()) {
						return;
					}
					tv.setText(newText);
					tv.setVisibility(newText.toString().length() == 0 ? View.GONE
							: View.VISIBLE);
				}
			});
		}
	}

}
