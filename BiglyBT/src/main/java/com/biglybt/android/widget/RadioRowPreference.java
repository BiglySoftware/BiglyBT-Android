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
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.core.view.ViewCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;
import com.google.android.material.radiobutton.MaterialRadioButton;

/**
 * Preference with radio buttons directly on the row, removing the need
 * for a popup dialog for selection.  Works on no-touch and touch devices.
 * Wraps items if needed.
 */
public class RadioRowPreference
	extends Preference
	implements OnCheckedChangeListener
{
	public interface OnPreferenceRadioSelectedListener
	{
		/**
		 * Called when a preference button has been clicked.
		 *
		 * @param position position that was selected, or -1 if selection cleared
		 * @param preference The preference that was clicked
		 */
		void onPreferenceRadioSelected(int position, @NonNull Preference preference);
	}

	private CharSequence[] texts;

	/** Not a RadioGroup because we want to wrap and RadioGroup is LinearLayout */
	private ViewGroup radiogroup;

	private Integer[] ids;

	private OnPreferenceRadioSelectedListener radioClickListener;

	private boolean skipListener = false;

	private int position = -1;

	@UiThread
	public RadioRowPreference(@NonNull Context context) {
		super(context);
		setLayoutResource(R.layout.preference_radio);
	}

	@UiThread
	public void setEntries(CharSequence... texts) {
		this.texts = texts;
		fillContainer();
	}

	public void setOnPreferenceRadioClickListener(
			OnPreferenceRadioSelectedListener l) {
		radioClickListener = l;
	}

	@UiThread
	public void setValueIndex(int position) {
		this.position = position;
		if (radiogroup != null) {
			skipListener = true;
			if (position == -1) {
				AndroidUtilsUI.walkTree(radiogroup, view -> {
					if (view instanceof MaterialRadioButton) {
						((MaterialRadioButton) view).setChecked(false);
					}
				});
			} else if (ids != null && position < ids.length) {
				View view = radiogroup.findViewById(ids[position]);
				if (view instanceof MaterialRadioButton) {
					((MaterialRadioButton) view).setChecked(true);
				}
			}
			skipListener = false;
		}
	}

	@Override
	public void onBindViewHolder(PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);
		holder.itemView.setClickable(false);
		((ViewGroup) holder.itemView).setDescendantFocusability(
				ViewGroup.FOCUS_AFTER_DESCENDANTS);

		radiogroup = (ViewGroup) holder.findViewById(R.id.radio_placement);

		fillContainer();
	}

	@UiThread
	private void fillContainer() {
		if (radiogroup == null) {
			return;
		}
		radiogroup.removeAllViews();
		if (texts == null) {
			return;
		}
		int textsLength = texts.length;
		ids = new Integer[texts.length];
		for (int i = 0; i < textsLength; i++) {
			CharSequence text = texts[i];

			MaterialRadioButton radioButton = new MaterialRadioButton(getContext());
			radioButton.setOnCheckedChangeListener(this);
			radioButton.setFocusable(true);
			radioButton.setClickable(true);
			radioButton.setText(text);
			ids[i] = ViewCompat.generateViewId();
			radioButton.setId(ids[i]);
			radiogroup.addView(radioButton);
		}
		if (position >= textsLength) {
			position = -1;
		}
		if (textsLength > 0) {
			skipListener = true;
			setValueIndex(position);
			skipListener = false;
		}
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if (!isChecked) {
			return;
		}

		int checkedId = buttonView.getId();
		// Clear other radio buttons
		AndroidUtilsUI.walkTree(radiogroup, view -> {
			if ((view instanceof MaterialRadioButton) && view.getId() != checkedId) {
				((MaterialRadioButton) view).setChecked(false);
			}
		});

		if (skipListener || ids == null || radioClickListener == null) {
			return;
		}
		int newPosition = -1;
		if (checkedId != -1) {
			for (int i = 0, idsLength = ids.length; i < idsLength; i++) {
				Integer id = ids[i];
				if (id == checkedId) {
					newPosition = i;
					break;
				}
			}
			if (newPosition == -1) {
				return;
			}
		}
		if (position != newPosition) {
			position = newPosition;
			radioClickListener.onPreferenceRadioSelected(newPosition, this);
		}
	}

}
