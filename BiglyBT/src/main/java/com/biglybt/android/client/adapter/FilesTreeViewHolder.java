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

import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import com.biglybt.android.adapter.FlexibleRecyclerViewHolder;
import com.biglybt.android.client.R;

public class FilesTreeViewHolder
	extends FlexibleRecyclerViewHolder<FilesTreeViewHolder>
{
	final TextView tvName;

	final TextView tvProgress;

	final ProgressBar pb;

	@NonNull
	final TextView tvInfo;

	final TextView tvStatus;

	final ImageButton expando;

	@NonNull
	final ImageButton btnWant;

	@NonNull
	final View strip;

	@NonNull
	final ViewGroup layout;

	int fileIndex = -1;

	public long torrentID = -1;

	FilesTreeViewHolder(RecyclerSelectorInternal<FilesTreeViewHolder> selector,
			@NonNull View rowView) {
		super(selector, rowView);

		tvName = rowView.findViewById(R.id.filerow_name);

		tvProgress = rowView.findViewById(R.id.filerow_progress_pct);
		pb = rowView.findViewById(R.id.filerow_progress);
		tvInfo = ViewCompat.requireViewById(rowView, R.id.filerow_info);
		tvStatus = rowView.findViewById(R.id.filerow_state);
		expando = rowView.findViewById(R.id.filerow_expando);
		btnWant = ViewCompat.requireViewById(rowView, R.id.filerow_btn_dl);
		strip = ViewCompat.requireViewById(rowView, R.id.filerow_indent);
		layout = ViewCompat.requireViewById(rowView, R.id.filerow_meh);
	}
}
