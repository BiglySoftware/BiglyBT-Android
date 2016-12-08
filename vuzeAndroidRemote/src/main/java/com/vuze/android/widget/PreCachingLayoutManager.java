
package com.vuze.android.widget;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;

/**
 * From https://androiddevx.wordpress.com/2014/12/05/recycler-view-pre-cache-views/
 * or https://github.com/ovy9086/recyclerview-playground
 *
 * Created by ovidiu.latcu on 12/4/2014.
 */
public class PreCachingLayoutManager
	extends LinearLayoutManager
{
	private int DEFAULT_EXTRA_LAYOUT_SPACE = 600;

	private int extraLayoutSpace = -1;

	private final Context context;

	public PreCachingLayoutManager(Context context) {
		super(context);
		this.context = context;
		setDefaultExtraLayoutSpace(context, getOrientation());
	}

	public PreCachingLayoutManager(Context context, int extraLayoutSpace) {
		super(context);
		this.context = context;
		this.extraLayoutSpace = extraLayoutSpace;
		setDefaultExtraLayoutSpace(context, getOrientation());
	}

	public PreCachingLayoutManager(Context context, int orientation,
			boolean reverseLayout) {
		super(context, orientation, reverseLayout);
		this.context = context;
		setDefaultExtraLayoutSpace(context, orientation);
	}

	public void setExtraLayoutSpace(int extraLayoutSpace) {
		this.extraLayoutSpace = extraLayoutSpace;
	}

	private void setDefaultExtraLayoutSpace(Context context, int orientation) {
		if (context == null) {
			// initializing, probably being called by setOrientation.  Will be called again
			return;
		}
		DisplayMetrics dm = context.getResources().getDisplayMetrics();
		DEFAULT_EXTRA_LAYOUT_SPACE = orientation == LinearLayoutManager.HORIZONTAL
				? dm.widthPixels : dm.heightPixels;
	}

	@Override
	public void setOrientation(int orientation) {
		setDefaultExtraLayoutSpace(context, orientation);
		super.setOrientation(orientation);
	}

	@Override
	protected int getExtraLayoutSpace(RecyclerView.State state) {
		if (extraLayoutSpace > 0) {
			return extraLayoutSpace;
		}
		return DEFAULT_EXTRA_LAYOUT_SPACE;
	}

	// FROM http://stackoverflow.com/a/33985508
	/**
	 * Disable predictive animations. There is a bug in RecyclerView which causes views that
	 * are being reloaded to pull invalid ViewHolders from the internal recycler stack if the
	 * adapter size has decreased since the ViewHolder was recycled.
	 */
	@Override
	public boolean supportsPredictiveItemAnimations() {
		return false;
	}
}