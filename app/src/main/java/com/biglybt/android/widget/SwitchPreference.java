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
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import com.biglybt.android.client.R;

import java.lang.reflect.Field;

public class SwitchPreference
	extends SwitchPreferenceCompat
	implements PreferenceLongClickable
{
	private OnPreferenceClickListener onLongClickListener = null;

	private PreferenceIndenter indenter;

	public SwitchPreference(Context context, AttributeSet attrs, int defStyleAttr,
			int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	public SwitchPreference(Context context, AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public SwitchPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public SwitchPreference(Context context) {
		super(context);
	}

	@Override
	public void onBindViewHolder(PreferenceViewHolder holder) {
		// SwitchCompat is designed to only allow setting On/Off text once
		// This isn't compatible with recyclerview which will reuse widgets, and
		// thus breaks when any one item uses different on/off text
		//
		// I've tried holder.setIsRecyclable(false), but that leaves random unattached items on the display
		//
		// One solution would be to tell the recycler view that SwitchPreference always
		// needs a new holder, but I don't know how to do that.
		//
		// The following solution is to clear on/off layout object when on/off text doesn't match
		View view = holder.findViewById(R.id.switchWidget);
		if (view instanceof SwitchCompat) {
			final SwitchCompat switchView = (SwitchCompat) view;
			try {
				CharSequence textOn = switchView.getTextOn();
				if (!getSwitchTextOn().equals(textOn)) {
					Field mOnLayout = SwitchCompat.class.getDeclaredField("mOnLayout");
					mOnLayout.setAccessible(true);
					mOnLayout.set(switchView, null);
				}
				CharSequence textOff = switchView.getTextOff();
				if (!getSwitchTextOff().equals(textOff)) {
					Field mOffLayout = SwitchCompat.class.getDeclaredField("mOffLayout");
					mOffLayout.setAccessible(true);
					mOffLayout.set(switchView, null);
				}
			} catch (Exception e) {
				Log.e("SO", "onBindViewHolder: ", e);
			}
		}

		super.onBindViewHolder(holder);

		View iv = holder.itemView;
		iv.setOnLongClickListener(v -> {
			if (onLongClickListener == null || !isEnabled() || !isSelectable()) {
				return false;
			}
			return onLongClickListener.onPreferenceClick(this);
		});

		PreferenceIndenter.setIndent(indenter, iv);
	}

	@Override
	public void setOnLongClickListener(
			OnPreferenceClickListener onLongClickListener) {
		this.onLongClickListener = onLongClickListener;
	}

	@Override
	public void setIndent(int indent) {
		if (indenter == null) {
			indenter = new PreferenceIndenter(indent);
		}
	}

	@Override
	public int getIndent() {
		return indenter == null ? 0 : indenter.indent;
	}
}
