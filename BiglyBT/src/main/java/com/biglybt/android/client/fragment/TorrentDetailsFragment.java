/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ActionMode;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.biglybt.android.adapter.SortableRecyclerAdapter;
import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.SessionActivity;
import com.biglybt.android.client.activity.TorrentDetailsActivity;
import com.biglybt.android.client.activity.TorrentViewActivity;
import com.biglybt.android.client.adapter.PagerAdapter2UsingClasses;
import com.biglybt.android.client.rpc.RPCSupports;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.sidelist.*;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Torrent Details Fragment<br>
 * - Contains {@link PeersFragment}, {@link FilesFragment}, {@link TorrentInfoFragment}, {@link TorrentTagsFragment}<br>
 * - Contained in {@link TorrentViewActivity} for wide screens<br>
 * - Contained in {@link TorrentDetailsActivity} for narrow screens<br>
 */
public class TorrentDetailsFragment
	extends SideListFragment
	implements ActionModeBeingReplacedListener, View.OnKeyListener
{
	@Thunk
	long torrentID;

	private PagerAdapter2UsingClasses pagerAdapter;

	private ViewPager2 viewPager;

	@Override
	public View onCreateViewWithSession(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		Context context = requireContext();

		View view = inflater.inflate(
				AndroidUtils.isTV(context) ? R.layout.frag_torrent_details_tv
						: R.layout.frag_torrent_details_coord,
				container, false);
		assert view != null;

		CollapsingToolbarLayout collapsingToolbarLayout = view.findViewById(
				R.id.collapsing_toolbar);
		if (collapsingToolbarLayout != null) {
			AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) collapsingToolbarLayout.getLayoutParams();
			if (AndroidUtilsUI.getScreenHeightDp(context) >= 1000) {
				params.setScrollFlags(0);
			} else {
				params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
						| AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED);

				final AppBarLayout appbar = view.findViewById(R.id.appbar);
				appbar.addOnOffsetChangedListener(
						new AppBarLayout.OnOffsetChangedListener() {
							boolean isInFullView = true;

							@Override
							public void onOffsetChanged(AppBarLayout appBarLayout,
									int verticalOffset) {
								boolean isNowInFullView = verticalOffset == 0;
								if (isInFullView != isNowInFullView) {
									isInFullView = isNowInFullView;
									SessionActivity activity = (SessionActivity) requireActivity();
									if (activity.isFinishing()) {
										return;
									}
									ActionBar actionBar = activity.getSupportActionBar();
									if (actionBar == null) {
										return;
									}

									if (isInFullView) {
										RemoteProfile remoteProfile = session.getRemoteProfile();
										actionBar.setSubtitle(remoteProfile.getNick());
									} else {
										Map<?, ?> torrent = session.torrent.getCachedTorrent(
												torrentID);
										actionBar.setSubtitle(MapUtils.getMapString(torrent,
												TransmissionVars.FIELD_TORRENT_NAME, ""));

									}
								}
							}
						});
			}
		}

		setHasOptionsMenu(true);

		TabLayout tabLayout = view.findViewById(R.id.tab_layout);
		if (tabLayout != null) {
			/* When TabLayout is in "auto" mode and "center" gravity:
			   - Each tab will have it's own width
			   - If all tabs don't fit, view will scroll
			   - (FIX) When all tabs can be set to the same width, and still fit in
			     the view, tabs will be expanded to fill full view (instead of center)
			
			   TabLayout needs to be:
			     app:tabMode="auto"
			     app:tabGravity="center"
			 */
			if (tabLayout.getTabMode() == TabLayout.MODE_AUTO) {
				tabLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
					@Override
					public void onLayoutChange(View v, int left, int top, int right,
							int bottom, int oldLeft, int oldTop, int oldRight,
							int oldBottom) {
						int tabCount = tabLayout.getTabCount();
						int width = 0;
						int largestTabWidth = 0;
						int[] tabWidths = new int[tabCount];
						for (int i = 0; i < tabCount; i++) {
							TabLayout.Tab tabAt = tabLayout.getTabAt(i);
							if (tabAt == null) {
								continue;
							}
							tabWidths[i] = tabAt.view.getMeasuredWidth();
							width += tabWidths[i];
							if (tabWidths[i] > largestTabWidth) {
								largestTabWidth = tabWidths[i];
							}
						}
						if (largestTabWidth == 0) {
							return;
						}
						int maxWidth = largestTabWidth * tabCount;
						int layoutWidth = tabLayout.getWidth();
						//log("TDF", "l.w=" + layoutWidth + "; w=" + width + "; mW=" + maxWidth);

						// Switch only if all tabs are same width and total width is less
						// than tablayout's width
						if (width == maxWidth && maxWidth < layoutWidth) {
							//log("TDF", "Switch to fixed");
							tabLayout.setTabMode(TabLayout.MODE_FIXED);
							tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
							tabLayout.removeOnLayoutChangeListener(this);
						} else if (width < maxWidth && width < layoutWidth && tabCount > 2) {
							// Can't set each tab's minimum width, tablayout overwrites it,
							// so use smallest width as TabMinWidth
							int remaining = layoutWidth - width;
							Arrays.sort(tabWidths);
							while (remaining > 0) {
								if (tabWidths[0] == tabWidths[tabCount - 1]) {
									tabWidths[0] += remaining / tabCount;
									break;
								}

								int smallest = tabWidths[0];
								int i = 1;
								while (tabWidths[0] == tabWidths[i]) {
									i++;
									if (i == tabCount) {
										break;
									}
								}
								if (i < tabCount) {
									int lastSmallest = tabWidths[i];
									int adj = Math.min(remaining, lastSmallest - smallest);
									tabWidths[0] += adj;
									remaining -= adj;
								}
								Arrays.sort(tabWidths);
							}
							int smallest = tabWidths[0];

							try {
								//log("TDF", "spread'm " + smallest + ", lazy="	+ (layoutWidth / tabCount));
								Field requestedTabMinWidth = TabLayout.class.getDeclaredField(
										"requestedTabMinWidth");
								requestedTabMinWidth.setAccessible(true);
								requestedTabMinWidth.set(tabLayout, smallest);
								Method updateTabViews = TabLayout.class.getDeclaredMethod(
										"updateTabViews", boolean.class);
								updateTabViews.invoke(tabLayout, true);
							} catch (Throwable e) {
								if (AndroidUtils.DEBUG) {
									e.printStackTrace();
								}
							}
							tabLayout.removeOnLayoutChangeListener(this);
						}
					}
				});
			}

			Map<Class<? extends Fragment>, String> pageClassTitles = new LinkedHashMap<>();

			pageClassTitles.put(FilesFragment.class,
					getString(R.string.details_tab_files));

			pageClassTitles.put(TorrentInfoFragment.class,
					getString(R.string.details_tab_info));

			pageClassTitles.put(PeersFragment.class,
					getString(R.string.details_tab_peers));

			if (session.getSupports(RPCSupports.SUPPORTS_TAGS)) {
				pageClassTitles.put(TorrentTagsFragment.class,
						getString(R.string.details_tab_tags));
			}

			viewPager = ViewCompat.requireViewById(view, R.id.view_pager);
			//noinspection unchecked
			pagerAdapter = new PagerAdapter2UsingClasses(this,
					pageClassTitles.keySet().toArray(new Class[0]),
					pageClassTitles.values().toArray(new String[0]), viewPager);
			pagerAdapter.setFragmentAdapterCallback(fragment -> {
				Bundle args = fragment.getArguments();
				if (args == null) {
					args = new Bundle();
				}
				args.putLong("torrentID", torrentID);
				fragment.setArguments(args);
			});
			viewPager.setAdapter(pagerAdapter);
			new TabLayoutMediator(tabLayout, viewPager, (tab,
					position) -> tab.setText(pagerAdapter.getTitle(position))).attach();

			viewPager.setOffscreenPageLimit(1);

			viewPager.setOnKeyListener(this);
		}

		view.setOnKeyListener(this);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		ViewGroup topBGArea = requireActivity().findViewById(R.id.top_bg_area);
		View viewTorrentRow = requireActivity().findViewById(
				R.id.activity_torrent_detail_row);
		if (topBGArea != null && viewTorrentRow != null) {
			viewTorrentRow.addOnLayoutChangeListener((v, left, top, right, bottom,
					oldLeft, oldTop, oldRight, oldBottom) -> {
				if (bottom == oldBottom) {
					return;
				}
				ViewGroup.LayoutParams layoutParams = topBGArea.getLayoutParams();

				if (layoutParams != null) {
					int[] loc = new int[2];
					v.getLocationInWindow(loc);
					layoutParams.height = bottom + loc[1];
					topBGArea.setLayoutParams(layoutParams);
					topBGArea.requestLayout();
				}
			});
		}
	}

	// Called from Activity
	public void setTorrentIDs(@Nullable long[] newIDs) {
		this.torrentID = newIDs != null && newIDs.length == 1 ? newIDs[0] : -1;
		if (pagerAdapter != null) {
			Fragment frag = pagerAdapter.getCurrentFragment();
			if (frag instanceof SetTorrentIdListener) {
				((SetTorrentIdListener) frag).setTorrentID(torrentID);
			}
		}
		AndroidUtilsUI.runOnUIThread(this, false, activity -> {
			List<Fragment> fragments = AndroidUtilsUI.getFragments(
					getFragmentManager());
			for (Fragment item : fragments) {
				if (item instanceof SetTorrentIdListener) {
					((SetTorrentIdListener) item).setTorrentID(torrentID);
				}
			}
		});
	}

	public void onCreateActionMode(@Nullable ActionMode mode, Menu menu) {
		MenuInflater inflater;
		if (mode == null) {
			FragmentActivity activity = getActivity();
			if (activity == null) {
				return;
			}
			inflater = activity.getMenuInflater();
		} else {
			inflater = mode.getMenuInflater();
		}
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getFragmentManager());
		for (Fragment frag : fragments) {
			if (frag instanceof FragmentPagerListener) {
				frag.onCreateOptionsMenu(menu, inflater);
			}
		}
	}

	public void onPrepareActionMode(@NonNull Menu menu) {
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getFragmentManager());
		for (Fragment frag : fragments) {
			if (frag instanceof FragmentPagerListener) {
				frag.onPrepareOptionsMenu(menu);
			}
		}
	}

	public boolean onActionItemClicked(@NonNull MenuItem item) {
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getFragmentManager());
		for (Fragment frag : fragments) {
			if (frag instanceof FragmentPagerListener) {
				if (frag.onOptionsItemSelected(item)) {
					return true;
				}
			}
		}
		return false;
	}

	public void playVideo() {
		List<Fragment> fragments = AndroidUtilsUI.getFragments(
				getFragmentManager());
		for (Fragment frag : fragments) {
			if (frag instanceof FilesFragment) {
				((FilesFragment) frag).launchOrStreamFile();
			}
		}
	}

	@Override
	public void setActionModeBeingReplaced(ActionMode actionMode,
			boolean actionModeBeingReplaced) {
	}

	@Override
	public void actionModeBeingReplacedDone() {
	}

	@Override
	public void rebuildActionMode() {
	}

	@Override
	public ActionMode getActionMode() {
		if (pagerAdapter == null) {
			return null;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof ActionModeBeingReplacedListener) {
			return ((ActionModeBeingReplacedListener) frag).getActionMode();
		}
		return null;
	}

	@Override
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (pagerAdapter == null) {
			return false;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof View.OnKeyListener) {
			return ((View.OnKeyListener) frag).onKey(v, keyCode, event);
		}
		return false;
	}

	@Override
	public SortableRecyclerAdapter getMainAdapter() {
		if (pagerAdapter == null) {
			return null;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			return ((SideListHelperListener) frag).getMainAdapter();
		}
		return null;
	}

	@Override
	public SideActionSelectionListener getSideActionSelectionListener() {
		if (pagerAdapter == null) {
			return null;
		}

		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			return ((SideListHelperListener) frag).getSideActionSelectionListener();
		}
		return null;
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void sideListExpandListChanged(boolean expanded) {
		if (pagerAdapter == null) {
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).sideListExpandListChanged(expanded);
		}
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void sideListExpandListChanging(boolean expanded) {
		if (pagerAdapter == null) {
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).sideListExpandListChanging(expanded);
		}
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void onSideListHelperVisibleSetup(View view) {
		if (pagerAdapter == null) {
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).onSideListHelperVisibleSetup(view);
		}
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void onSideListHelperPostSetup(SideListHelper sideListHelper) {
		if (pagerAdapter == null) {
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).onSideListHelperPostSetup(sideListHelper);
		}
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public void onSideListHelperCreated(SideListHelper sideListHelper) {
		if (pagerAdapter == null) {
			return;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			((SideListHelperListener) frag).onSideListHelperCreated(sideListHelper);
		}
	}

	@Override
	public boolean showFilterEntry() {
		if (pagerAdapter == null) {
			return false;
		}
		Fragment frag = pagerAdapter.getCurrentFragment();
		if (frag instanceof SideListHelperListener) {
			return ((SideListHelperListener) frag).showFilterEntry();
		}
		return false;
	}
}
