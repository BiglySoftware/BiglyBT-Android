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

import androidx.annotation.NonNull;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

public class CategoryPreferenceNoDivider
	extends PreferenceCategory
	implements PreferenceIndentable
{
	private PreferenceIndenter indenter;

	public CategoryPreferenceNoDivider(Context context, AttributeSet attrs,
			int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	public CategoryPreferenceNoDivider(Context context, AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public CategoryPreferenceNoDivider(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CategoryPreferenceNoDivider(Context context) {
		super(context);
	}

	@Override
	public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);
		holder.setDividerAllowedAbove(false);
		holder.setDividerAllowedBelow(false);

		PreferenceIndenter.setIndent(indenter, holder.itemView);
	}

	@Override
	public void setIndent(int indent) {
		if (indenter == null) {
			indenter = new PreferenceIndenter(indent);
		}
	}

	@Override
	public void setLayoutResource(int layoutResId) {
		super.setLayoutResource(layoutResId);
		indenter = null;
	}

	@Override
	public int getIndent() {
		return indenter == null ? 0 : indenter.indent;
	}
}
