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

package com.biglybt.android.client.adapter;

import com.biglybt.android.adapter.FlexibleRecyclerViewHolder;
import com.biglybt.android.client.R;

import android.view.View;
import android.widget.*;

public class FilesTreeViewHolder
	extends FlexibleRecyclerViewHolder<FilesTreeViewHolder>
{
	final TextView tvName;

	final TextView tvProgress;

	final ProgressBar pb;

	final TextView tvInfo;

	final TextView tvStatus;

	final ImageButton expando;

	final ImageButton btnWant;

	final View strip;

	final RelativeLayout layout;

	public int fileIndex = -1;

	public long torrentID = -1;

	FilesTreeViewHolder(RecyclerSelectorInternal<FilesTreeViewHolder> selector,
			View rowView) {
		super(selector, rowView);

		tvName = rowView.findViewById(R.id.filerow_name);

		tvProgress = rowView.findViewById(R.id.filerow_progress_pct);
		pb = rowView.findViewById(R.id.filerow_progress);
		tvInfo = rowView.findViewById(R.id.filerow_info);
		tvStatus = rowView.findViewById(R.id.filerow_state);
		expando = rowView.findViewById(R.id.filerow_expando);
		btnWant = rowView.findViewById(R.id.filerow_btn_dl);
		strip = rowView.findViewById(R.id.filerow_indent);
		layout = rowView.findViewById(R.id.filerow_layout);
	}
}
