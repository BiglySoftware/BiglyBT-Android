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

import java.io.Serializable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Created by TuxPaper on 9/15/18.
 */
public class MetaSearchEnginesInfo
	implements Comparable<MetaSearchEnginesInfo>, Serializable
{
	public final String uid;

	public String name;

	public boolean completed;

	public int count;

	public String iconURL;

	MetaSearchEnginesInfo(String uid) {
		this.uid = uid;
	}

	public MetaSearchEnginesInfo(String uid, String name,
			@Nullable String iconURL, boolean completed) {
		this.name = name;
		this.iconURL = iconURL;
		this.completed = completed;
		this.uid = uid;
	}

	@Override
	public int compareTo(@NonNull MetaSearchEnginesInfo another) {
		return uid.compareTo(another.uid);
	}
}
