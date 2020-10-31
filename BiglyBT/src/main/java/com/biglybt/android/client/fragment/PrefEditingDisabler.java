/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.fragment;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;
import android.view.animation.*;
import android.view.animation.Animation.AnimationListener;
import android.widget.FrameLayout;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.util.Thunk;

public class PrefEditingDisabler {
	private static final String TAG = "PrefEditingDisabler";

	private static final boolean TEST_ADD_DELAY = false; //AndroidUtils.DEBUG;
	
	@Thunk
	ViewGroup topViewGroup;

	@Thunk
	FrameLayout frameLayout;

	@Thunk
	AlphaAnimation setAnim;

	private long disablingStart;

	private Fragment fragment;

	public PrefEditingDisabler(Fragment fragment) {
		this.fragment = fragment;
	}

	@Thunk
	public void enableEditing() {
		if (topViewGroup == null) {
			return;
		}
		if (TEST_ADD_DELAY) {
			try {
				long millis = (long) (Math.random() * 1100);
				Log.e(TAG, "ma=; sleep " + millis);
				Thread.sleep(millis);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		AndroidUtilsUI.runOnUIThread(fragment, false, (activity) -> {
			if (setAnim != null) {
				Transformation tf = new Transformation();
				setAnim.getTransformation(AnimationUtils.currentAnimationTimeMillis(),
					tf);
				float alpha = tf.getAlpha();
				topViewGroup.clearAnimation();
				setAnim.cancel();
				setAnim = null;

				int duration = (int) Math.min(
					(System.currentTimeMillis() - disablingStart) / 3, 500);
				if (alpha < 1.0f && duration > 10) {
					AlphaAnimation alphaAnimation = new AlphaAnimation(alpha, 1.0f);
					alphaAnimation.setInterpolator(new AccelerateInterpolator());
					alphaAnimation.setDuration(duration);
					alphaAnimation.setFillAfter(true);
					alphaAnimation.setAnimationListener(new AnimationListener() {
						@Override
						public void onAnimationStart(Animation animation) {

						}

						@Override
						public void onAnimationEnd(Animation animation) {
							topViewGroup.removeView(frameLayout);
						}

						@Override
						public void onAnimationRepeat(Animation animation) {

						}
					});
					topViewGroup.startAnimation(alphaAnimation);
					return;
				}
			} else {
				topViewGroup.setAlpha(1.0f);
			}
			topViewGroup.removeView(frameLayout);
		});
	}

	public void disableEditing(boolean fade) {
		if (setAnim != null) {
			return;
		}

		ViewGroup view = (ViewGroup) fragment.getView();
		if (view == null) {
			FragmentActivity activity = fragment.getActivity();
			if (activity == null) {
				return;
			}
			topViewGroup = activity.findViewById(android.R.id.list_container);
			if (topViewGroup == null) {
				topViewGroup = AndroidUtilsUI.getContentView(activity);
			}
		} else {
			topViewGroup = view.findViewById(android.R.id.list_container);
		}

		frameLayout = null;
		if (topViewGroup != null) {
			Context context = fragment.getContext();
			if (context == null) {
				return;
			}
			frameLayout = new FrameLayout(context);
			frameLayout.setClickable(true);
			frameLayout.setFocusable(true);
			topViewGroup.addView(frameLayout);
			frameLayout.bringToFront();
			frameLayout.requestFocus();

			if (fade) {
				setAnim = new AlphaAnimation(1.0f, 0.2f);
				setAnim.setInterpolator(new DecelerateInterpolator());
				setAnim.setDuration(1500);
				setAnim.setFillAfter(true);
				setAnim.setFillEnabled(true);
				topViewGroup.startAnimation(setAnim);
			} else {
				topViewGroup.setAlpha(0.2f);
			}
		}

		disablingStart = System.currentTimeMillis();
	}
}
