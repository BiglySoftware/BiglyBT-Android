package com.biglybt.android.adapter;

import java.util.List;

/**
 * Created by TuxPaper on 9/15/18.
 */
public class StoredSortByInfo {
	public final int id;

	public final boolean isAsc;

	public final List oldSortByFields;

	public StoredSortByInfo(int id, boolean isAsc, List oldSortByFields) {
		this.id = id;
		this.isAsc = isAsc;
		this.oldSortByFields = oldSortByFields;
	}
}
