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

package com.biglybt.android.widget;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.R;

/**
 * SwitchPreferenceCompat row with different actions for clicking on switch
 * vs clicking on row.
 *
 * @implNote Tailored to specific needs -- not really useful as a generic preference option
 */
public class SwitchClickPreference
	extends SwitchPreferenceCompat
	implements OnClickListener
{
	private OnPreferenceClickListener mOnSwitchClickListener;

	private OnPreferenceClickListener mOnClickListener;

	public SwitchClickPreference(@NonNull Context context) {
		super(context);
		setWidgetLayoutResource(R.layout.preference_widget_switch_compat);
		// prevent row click from flipping switch
		super.setOnPreferenceClickListener(preference -> {
			if (mOnClickListener != null) {
				boolean checked = isChecked();
				mOnClickListener.onPreferenceClick(preference);
				setChecked(!checked);
			} else if (mOnSwitchClickListener != null) {
				mOnSwitchClickListener.onPreferenceClick(preference);
			}
			return false;
		});
	}

	@Override
	public void onBindViewHolder(PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);

		CompoundButton button = (CompoundButton) holder.findViewById(
				R.id.switchWidget);
		if (button != null) {
			holder.itemView.setClickable(true);
			holder.itemView.setFocusable(true);
			boolean hasSwitchClick = mOnSwitchClickListener != null;
			((ViewGroup) holder.itemView).setDescendantFocusability(
					ViewGroup.FOCUS_BEFORE_DESCENDANTS);

			// must be before setClickable because it sets it true even if we pass null
			button.setOnClickListener(hasSwitchClick ? this : null);
			button.setClickable(hasSwitchClick);
			button.setFocusable(hasSwitchClick);
			button.setDuplicateParentStateEnabled(false);
			holder.itemView.setNextFocusDownId(R.id.switchWidget);
			holder.itemView.setNextFocusForwardId(R.id.switchWidget);

			if (!AndroidUtils.isTV(getContext())) {
				ViewCompat.setBackground(button, null);
			}
		}
	}

	/**
	 * Sets the callback to be invoked when this preference is clicked.
	 *
	 * @param onPreferenceClickListener The callback to be invoked
	 */
	public void setOnSwitchClickListener(
			OnPreferenceClickListener onPreferenceClickListener) {
		mOnSwitchClickListener = onPreferenceClickListener;
	}

	/**
	 * Returns the callback to be invoked when this preference is clicked.
	 *
	 * @return The callback to be invoked
	 */
	public OnPreferenceClickListener getOnSwitchClickListener() {
		return mOnSwitchClickListener;
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void setOnPreferenceClickListener(
			OnPreferenceClickListener onPreferenceClickListener) {
		mOnClickListener = onPreferenceClickListener;
	}

	@Override
	public OnPreferenceClickListener getOnPreferenceClickListener() {
		return mOnClickListener;
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void setOnPreferenceChangeListener(
			OnPreferenceChangeListener onPreferenceChangeListener) {
		// Nope
	}

	@Override
	public void onClick(View v) {
		if (mOnSwitchClickListener != null) {
			mOnSwitchClickListener.onPreferenceClick(this);
		}
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void setLayoutResource(int layoutResId) {
		// Nope
	}
}
