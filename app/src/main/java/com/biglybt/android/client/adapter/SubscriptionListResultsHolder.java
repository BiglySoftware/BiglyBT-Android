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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

/**
 * Created by TuxPaper on 9/15/18.
 */
class SubscriptionListResultsHolder
	extends FlexibleRecyclerViewHolder<SubscriptionListResultsHolder>
{
	@NonNull
	final TextView tvName;

	@NonNull
	final TextView tvQueryInfo;

	@NonNull
	final TextView tvCount;

	@NonNull
	final TextView tvNewCount;

	@NonNull
	final TextView tvError;

	@NonNull
	final TextView tvLastUpdated;

	@NonNull
	final ImageView iv;

	SubscriptionListResultsHolder(
			RecyclerSelectorInternal<SubscriptionListResultsHolder> selector,
			@NonNull View rowView) {
		super(selector, rowView);

		tvName = ViewCompat.requireViewById(rowView,R.id.sl_name);
		tvQueryInfo = ViewCompat.requireViewById(rowView,R.id.sl_queryInfo);
		tvCount = ViewCompat.requireViewById(rowView,R.id.sl_count);
		tvNewCount = ViewCompat.requireViewById(rowView,R.id.sl_new_count);
		tvError = ViewCompat.requireViewById(rowView,R.id.sl_error);
		tvLastUpdated = ViewCompat.requireViewById(rowView,R.id.sl_lastchecked);
		iv = ViewCompat.requireViewById(rowView,R.id.sl_image);
	}
}
