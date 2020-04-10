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
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;

/**
 * Simple {@link Preference} with first itemView focusable, and only 
 * clickable if {@link #isSelectable()}.  Also linkifies <tt>&lt;a href=</tt> 
 * <p/>
 * We can't use {@link Preference#setSelectable(boolean)} because it sets both
 * focusable and clickable to false, which means on Leanback the user may
 * not be able to see all the text (ex. Multiline title as 1st preference, 
 * 2nd preference gets focused, potentially pushing the 1st preference partially
 * off the top of the fragment)
 */
public class LabelPreference
	extends Preference
{
	public LabelPreference(@NonNull Context context, AttributeSet attrs,
			int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		if (!AndroidUtils.isTV(context)) {
			setLayoutResource(R.layout.preference_label);
		}
		setSelectable(false);
	}

	public LabelPreference(@NonNull Context context, AttributeSet attrs,
			int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public LabelPreference(@NonNull Context context, AttributeSet attrs) {
		this(context, attrs, AndroidUtilsUI.getAttr(context, R.attr.preferenceStyle,
				android.R.attr.preferenceStyle));
	}

	public LabelPreference(@NonNull Context context) {
		this(context, null);
	}

	@Override
	public void onBindViewHolder(PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);
		View itemView = holder.itemView;
		if (holder.getAdapterPosition() == 0) {
			itemView.setFocusable(true);
		}
		Context context = getContext();
		if (context instanceof FragmentActivity) {
			TextView summaryView = (TextView) holder.findViewById(
					android.R.id.summary);
			if (summaryView != null && summaryView.getVisibility() == View.VISIBLE) {
				linkifyIfHREF((FragmentActivity) context, summaryView);
			}
			TextView title = (TextView) holder.findViewById(android.R.id.title);
			if (title != null && title.getVisibility() == View.VISIBLE) {
				linkifyIfHREF((FragmentActivity) context, title);
			}
		}
	}

	private void linkifyIfHREF(@NonNull FragmentActivity activity, @NonNull TextView tv) {
		String msg = tv.getText().toString();
		if (msg.contains("<A HREF=") || msg.contains("<a href=")) {
			AndroidUtilsUI.linkify(activity, tv, null, msg);
		}
	}
}
