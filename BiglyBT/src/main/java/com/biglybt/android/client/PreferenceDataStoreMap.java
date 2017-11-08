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

package com.biglybt.android.client;

import android.support.annotation.Nullable;
import android.support.v7.preference.PreferenceDataStore;

import com.biglybt.android.util.MapUtils;

import java.util.*;

/**
 * Created by TuxPaper on 10/11/17.
 */

public class PreferenceDataStoreMap
	extends PreferenceDataStore
{
	final Map<String, Object> map = new HashMap<>();
	
	public PreferenceDataStoreMap() {
		super();
	}

	@Override
	public void putString(String key, @Nullable String value) {
		map.put(key, value);
	}

	@Override
	public void putStringSet(String key, @Nullable Set<String> values) {
		map.put(key, values);
	}

	@Override
	public void putInt(String key, int value) {
		map.put(key, value);
	}

	@Override
	public void putLong(String key, long value) {
		map.put(key, value);
	}

	@Override
	public void putFloat(String key, float value) {
		map.put(key, value);
	}

	@Override
	public void putBoolean(String key, boolean value) {
		map.put(key, value);
	}

	@Nullable
	@Override
	public String getString(String key, @Nullable String defValue) {
		return MapUtils.getMapString(map, key, defValue);
	}

	@Nullable
	@Override
	public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
		List list = MapUtils.getMapList(map, key, null);
		if (list == null) {
			return defValues;
		}
		return new HashSet<>(list);
	}

	@Override
	public int getInt(String key, int defValue) {
		return MapUtils.getMapInt(map, key, defValue);
	}

	@Override
	public long getLong(String key, long defValue) {
		return MapUtils.getMapLong(map, key, defValue);
	}

	@Override
	public float getFloat(String key, float defValue) {
		return MapUtils.getMapFloat(map, key, defValue);
	}

	@Override
	public boolean getBoolean(String key, boolean defValue) {
		return MapUtils.getMapBoolean(map, key, defValue);
	}
}
