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

import android.animation.Animator;
import android.annotation.TargetApi;
import android.os.Build;

/**
 * Created by TuxPaper on 6/13/16.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class AnimatorEndListener
	implements Animator.AnimatorListener
{
	@Override
	public final void onAnimationStart(Animator animation) {

	}

	@Override
	public void onAnimationEnd(Animator animation) {

	}

	@Override
	public final void onAnimationCancel(Animator animation) {

	}

	@Override
	public final void onAnimationRepeat(Animator animation) {

	}
}
