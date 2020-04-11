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

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;
import com.google.android.material.button.MaterialButton;

/**
 * Preference row with a button.  Row can be set non-clickable while having
 * button clickable.
 */
public class ButtonPreference
	extends Preference
	implements OnClickListener
{
	private CharSequence buttonText;

	@DrawableRes
	private int buttonIconResourceId = View.NO_ID;

	private boolean rowClickable = true;

	public ButtonPreference(@NonNull Context context) {
		super(context);
		setWidgetLayoutResource(R.layout.preference_widget_button);
	}

	@Override
	public void onBindViewHolder(PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);
		MaterialButton button = (MaterialButton) holder.findViewById(R.id.button);
		if (button != null) {
			holder.itemView.setClickable(rowClickable);
			((ViewGroup) holder.itemView).setDescendantFocusability(
					rowClickable ? ViewGroup.FOCUS_BLOCK_DESCENDANTS
							: ViewGroup.FOCUS_AFTER_DESCENDANTS);

			// must be before setClickable because it sets it true even if we pass null
			button.setOnClickListener(rowClickable ? null : this);
			button.setClickable(!rowClickable);
			button.setFocusable(!rowClickable);
			if (buttonText != null) {
				button.setText(buttonText);
				button.setIconPadding(AndroidUtilsUI.dpToPx(4));
			} else {
				button.setText("");
				button.setIconPadding(0);
			}
			if (buttonIconResourceId != View.NO_ID) {
				button.setIconResource(buttonIconResourceId);
			} else {
				button.setIcon(null);
			}
		}
	}

	public void setButtonText(@StringRes int res) {
		buttonText = getContext().getString(res);
	}

	public void setButtonText(CharSequence text) {
		buttonText = text;
	}

	public void setButtonIcon(@DrawableRes int res) {
		buttonIconResourceId = res;
	}

	public void setRowClickable(boolean rowClickable) {
		if (AndroidUtils.isTV(getContext())) {
			return;
		}
		this.rowClickable = rowClickable;
	}

	@Override
	public void onClick(View v) {
		OnPreferenceClickListener clickListener = getOnPreferenceClickListener();
		if (clickListener != null) {
			clickListener.onPreferenceClick(this);
		}
	}
}
