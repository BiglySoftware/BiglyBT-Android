package com.biglybt.android.client.sidelist;

import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;
import com.biglybt.android.client.SessionGetter;
import com.biglybt.android.client.fragment.SessionFragment;

import java.util.List;

/**
 * <p>Simple extension of FragmentM to handle expand state when hamburger pressed.
 * Also adds {@link SideListHelperListener}, {@link SessionGetter}, and
 * provides access to {@link SideListActivity}
 * </p>
 * 
 * Created by TuxPaper on 8/15/18.
 */
public abstract class SideListFragment
	extends SessionFragment
	implements SideListHelperListener
{
	private static final String TAG = "SideListFragment";

	public SideListFragment() {
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			// Respond to the action bar's Up/Home button
			SideListActivity sideListActivity = getSideListActivity();
			if (sideListActivity != null
					&& sideListActivity.findViewById(R.id.drawer_layout) == null) {
				if (sideListActivity.flipSideListExpandState()) {
					return true;
				}
			}
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void sideListExpandListChanged(boolean expanded) {
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getChildFragmentManager());
		for (Fragment f : fragments) {
			if (f instanceof SideListHelperListener) {
				((SideListHelperListener) f).sideListExpandListChanged(expanded);
			}
		}
	}

	@Override
	public void sideListExpandListChanging(boolean expanded) {
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getChildFragmentManager());
		for (Fragment f : fragments) {
			if (f instanceof SideListHelperListener) {
				((SideListHelperListener) f).sideListExpandListChanging(expanded);
			}
		}
	}

	@Override
	public void onSideListHelperVisibleSetup(View view) {
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getChildFragmentManager());
		for (Fragment f : fragments) {
			if (f instanceof SideListHelperListener) {
				((SideListHelperListener) f).onSideListHelperVisibleSetup(view);
			}
		}
	}

	@Override
	public void onSideListHelperPostSetup(SideListHelper sideListHelper) {
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getChildFragmentManager());
		for (Fragment f : fragments) {
			if (f instanceof SideListHelperListener) {
				((SideListHelperListener) f).onSideListHelperPostSetup(sideListHelper);
			}
		}
	}

	@Override
	public void onSideListHelperCreated(SideListHelper sideListHelper) {
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getChildFragmentManager());
		for (Fragment f : fragments) {
			if (f instanceof SideListHelperListener) {
				((SideListHelperListener) f).onSideListHelperCreated(sideListHelper);
			}
		}
	}

	@Nullable
	public SideListActivity getSideListActivity() {
		FragmentActivity activity = getActivity();
		if (activity == null || activity.isFinishing()) {
			return null;
		}
		if (!(activity instanceof SideListActivity)) {
			Log.e(TAG, "getSideListActivity: Activity not SideListActivity, but "
					+ activity);
			return null;
		}
		return (SideListActivity) activity;
	}

}