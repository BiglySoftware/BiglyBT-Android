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

import android.animation.*;
import android.text.SpannableString;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;
import com.biglybt.util.Thunk;

public class TextViewFlipper
{
	private final int animId;

	public TextViewFlipper() {
		this.animId = R.animator.anim_field_change;
	}

	@Thunk
	static final boolean DEBUG_FLIPPER = false;

	public interface FlipValidator
	{
		boolean isStillValid();
	}

	@NonNull
	public static TextViewFlipper create() {
		return new TextViewFlipper();
	}

	@Thunk
	static String meh(View v) {
		return AndroidUtils.getResourceName(v.getContext().getResources(),
				v.getId());
	}

	public boolean changeText(final TextView tv, final CharSequence newText,
			boolean animate, final FlipValidator validator) {
		return changeText(tv, newText, animate, validator, View.GONE);
	}

	/**
	 * Change the text on repeat of Animation.
	 *  @param tv Widget to update
	 * @param newText New Text to set
	 * @param animate false to set right away, true to wait
	 * @param validator when animated, validator will be called to determine
	 */
	public boolean changeText(final TextView tv, final CharSequence newText,
			boolean animate, final FlipValidator validator, int visibilityWhenEmpty) {
		if (DEBUG_FLIPPER) {
			Log.d("flipper", meh(tv) + "] changeText: '" + newText + "';"
					+ (animate ? "animate" : "now"));
		}
		if (!newText.toString().equals(tv.getText().toString())) {
			if (!animate) {
				tv.setText(newText);
				int visibility = newText.length() == 0 ? visibilityWhenEmpty
						: View.VISIBLE;
				// setVisibility does some Accessibility stuff even when nothing changes
				// getVisibility is a direct flag return
				if (visibility != tv.getVisibility()) {
					tv.setVisibility(visibility);
				}
				tv.setRotationX(0);
				return true;
			}

			if (DEBUG_FLIPPER) {
				Log.d("flipper", meh(tv) + "] doesn't match previous: " + tv.getText());
			}

			flipIt(tv, new MyAnimatorListenerAdapter(validator, tv, newText,
					visibilityWhenEmpty));
			return true;
		}

		if (DEBUG_FLIPPER) {
			Log.d("flipper", meh(tv) + "] changeText: ALREADY " + newText);
		}
		int newVisiblity = newText.length() == 0 ? visibilityWhenEmpty
				: View.VISIBLE;
		if (tv.getVisibility() != newVisiblity) {
			tv.setVisibility(newVisiblity);
		}
		return false;
	}

	private void flipIt(View view, Animator.AnimatorListener l) {
		AnimatorSet animation = (AnimatorSet) AnimatorInflater.loadAnimator(
				view.getContext(), animId);

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
			animation.getChildAnimations().get(0).addListener(l);
		}
		animation.setTarget(view);
		animation.start();
	}

	public void changeText(final TextView tv, final SpannableString newText,
			boolean animate, final FlipValidator validator) {
		String newTextString = newText.toString();
		if (!animate || tv.getAnimation() != null) {
			tv.setText(newText);
			tv.setVisibility(newTextString.length() == 0 ? View.GONE : View.VISIBLE);
			return;
		}
		if (!newTextString.equals(tv.getText().toString())) {
			flipIt(tv, new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
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

	private static class MyAnimatorListenerAdapter
		extends AnimatorListenerAdapter
	{

		private final FlipValidator validator;

		private final TextView tv;

		private final CharSequence newText;

		private final int visibilityWhenEmpty;

		MyAnimatorListenerAdapter(FlipValidator validator, TextView tv,
				CharSequence newText, int visibilityWhenEmpty) {
			this.validator = validator;
			this.tv = tv;
			this.newText = newText;
			this.visibilityWhenEmpty = visibilityWhenEmpty;
		}

		@Override
		public void onAnimationEnd(Animator animation) {
			if (validator != null && !validator.isStillValid()) {
				if (DEBUG_FLIPPER) {
					Log.d("flipper", meh(tv) + "] changeText: no longer valid");
				}
				return;
			}
			if (DEBUG_FLIPPER) {
				Log.d("flipper", meh(tv) + "] changeText: setting to '" + newText
						+ "'. Currently '" + tv.getText() + "'");
			}
			tv.setText(newText);
			if (newText.length() == 0) {
				tv.setVisibility(visibilityWhenEmpty);
			}
		}

	}
}
