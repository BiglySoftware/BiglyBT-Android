package com.biglybt.android.client.sidelist;

import com.biglybt.android.adapter.SortableRecyclerAdapter;

import android.view.View;

/**
 * Created by TuxPaper on 8/20/18.
 */
public interface SideListHelperListener
{
	SortableRecyclerAdapter getMainAdapter();

	SideActionSelectionListener getSideActionSelectionListener();

	void sideListExpandListChanged(boolean expanded);

	void sideListExpandListChanging(boolean expanded);

	/**
	 * Sidelist is initialized and visible.  Do any setup here, like
	 * registering new sections, updating text fields, etc
	 */
	void onSideListHelperVisibleSetup(View view);

	void onSideListHelperPostSetup(SideListHelper sideListHelper);

	/** 
	 * <p>Triggered when SideListHelper is created, typically only once per Activity</p>
	 * 
	 * <p>If you need to {@link SideListHelper#addEntry(String, View, int, int)},
	 * do it here</p>
	 */
	void onSideListHelperCreated(SideListHelper sideListHelper);

	boolean showFilterEntry();
}
