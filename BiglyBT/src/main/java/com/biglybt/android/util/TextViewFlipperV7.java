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

package com.biglybt.android.util;

import com.biglybt.android.client.R;
import com.biglybt.util.Thunk;

import android.text.SpannableString;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

public class TextViewFlipperV7
	extends TextViewFlipper
{
	private final int animId;

	public TextViewFlipperV7() {
		this.animId = R.anim.anim_field_change;
	}

	@Thunk
	static String meh(View v) {
		int id = v.getId();
		return id == View.NO_ID ? ""
				: v.getContext().getResources().getResourceEntryName(id);
	}

	/**
	 * Change the text on repeat of Animation.
	 *  @param tv Widget to update
	 * @param newText New Text to set
	 * @param animate false to set right away, true to wait
	 * @param validator when animated, validator will be called to determine
	 */
	@Override
	public boolean changeText(final TextView tv, final CharSequence newText,
			boolean animate, final FlipValidator validator) {
		if (DEBUG_FLIPPER) {
			Log.d("flipper", meh(tv) + "] changeText: '" + newText + "';"
					+ (animate ? "animate" : "now"));
		}
		if (!animate) {
			tv.setText(newText);
			tv.setVisibility(newText.length() == 0 ? View.GONE : View.VISIBLE);
			return true;
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
			return true;
		} else {
			if (DEBUG_FLIPPER) {
				Log.d("flipper", meh(tv) + "] changeText: ALREADY " + newText);
			}
			return false;
		}
	}

	private void flipIt(View view, AnimationListener l) {
		// Should do this, to prevent destorying of list row while animating
		// would have to set to false in listener
		//view.setHasTransientState(true);
		Animation animation = AnimationUtils.loadAnimation(view.getContext(),
				animId);
		// Some Android versions won't animate when view is GONE
		if (view.getVisibility() == View.GONE) {
			if (DEBUG_FLIPPER) {
				Log.d("flipper",
						meh(view) + "] changeText: view gone.. need to make visible");
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

	@Override
	public void changeText(final TextView tv, final SpannableString newText,
			boolean animate, final FlipValidator validator) {
		String newTextString = newText.toString();
		if (!animate || tv.getAnimation() != null) {
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
					tv.setVisibility(
							newText.toString().length() == 0 ? View.GONE : View.VISIBLE);
				}
			});
		}
	}

}
