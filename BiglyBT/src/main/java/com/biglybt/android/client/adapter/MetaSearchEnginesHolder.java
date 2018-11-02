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
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Created by TuxPaper on 9/15/18.
 */
public class MetaSearchEnginesHolder
	extends FlexibleRecyclerViewHolder<MetaSearchEnginesHolder>
{
	final TextView tvName;

	final TextView tvCount;

	final ProgressBar pb;

	final ImageView iv;

	final ImageView ivChecked;

	MetaSearchEnginesHolder(
			RecyclerSelectorInternal<MetaSearchEnginesHolder> selector,
			View rowView) {
		super(selector, rowView);

		tvName = rowView.findViewById(R.id.ms_engine_name);
		tvCount = rowView.findViewById(R.id.ms_engine_count);
		pb = rowView.findViewById(R.id.ms_engine_pb);
		iv = rowView.findViewById(R.id.ms_engine_icon);
		ivChecked = rowView.findViewById(R.id.ms_engine_checked);
	}
}
