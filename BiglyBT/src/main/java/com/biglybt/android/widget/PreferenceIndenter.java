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

import android.view.View;

import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;

public class PreferenceIndenter
{
	public int indent;

	public PreferenceIndenter(int indent) {
		this.indent = indent;
	}

	public void setIndent(View iv) {
		setIndent(iv, indent);
	}

	public static void setIndent(PreferenceIndenter indenter, View iv) {
		setIndent(iv, indenter == null ? 0 : indenter.indent);
	}

	public static void setIndent(View iv, int indent) {
		Object tag = iv.getTag(R.id.tag_orig_padding);
		if (!(tag instanceof Number)) {
			tag = iv.getPaddingLeft();
			iv.setTag(R.id.tag_orig_padding, tag);
		}
		iv.setPadding(((Number) tag).intValue() + AndroidUtilsUI.dpToPx(indent),
				iv.getPaddingTop(), iv.getPaddingRight(), iv.getPaddingBottom());
	}
}
