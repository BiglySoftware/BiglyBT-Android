/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vuze.android.remote;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.RelativeLayout;

/**
 * FROM https://github.com/android/platform_packages_apps_music/blob/master/src/com/android/music/CheckableRelativeLayout.java
 * 
 * A special variation of RelativeLayout that can be used as a checkable object.
 * This allows it to be used as the top-level view of a list view item, which
 * also supports checking.  Otherwise, it works identically to a RelativeLayout.
 *
 * Checkable stuff not needed for API >= 11 / HONEYCOMB
 * Hack fix for left/right keeping focus would need to be moved if dropping
 * support for API < 11
 */
public class CheckableRelativeLayout extends RelativeLayout implements Checkable {
    private boolean mChecked;

    private static final int[] CHECKED_STATE_SET = {
        android.R.attr.state_checked
    };

    public CheckableRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }

    public void toggle() {
        setChecked(!mChecked);
    }
    
    public boolean isChecked() {
        return mChecked;
    }

    public void setChecked(boolean checked) {
        if (mChecked != checked) {
            mChecked = checked;
            refreshDrawableState();
        }
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        // When this layout is in a vertical list, gaining focus from the
        // left or the right is usually wrong.  ie. Android tries to guess
        // the row to the left/right of the currently focused view, and doesn't
        // send a requestFocus to the parent first (not even with
        // FOCUS_BEFORE_DESCENDANTS or parent)
        // Hack fix this by forcing parent to get focus
        if (AndroidUtils.DEBUG) {
            Log.d("FSREC", "reqestFocus from " + direction);
        }
        if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
            return ((View) getParent()).requestFocus();
        }
        return super.requestFocus(direction, previouslyFocusedRect);
    }
}