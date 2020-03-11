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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.biglybt.android.adapter.FlexibleRecyclerViewHolder;

/**
 * Base class for all TorrentList holders ({@link TorrentListHolderHeader}, {@link TorrentListHolderItem})
 * </p>
 * Created by TuxPaper on 8/27/18.
 */
public class TorrentListHolder
	extends FlexibleRecyclerViewHolder<TorrentListHolder>
{
	TorrentListHolder(
			@Nullable RecyclerSelectorInternal<TorrentListHolder> selector,
			@NonNull View rowView) {
		super(selector, rowView);
	}
}
