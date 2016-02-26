/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * <p/>
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

package com.vuze.android.remote.fragment;

import java.util.*;

import android.content.Context;
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.vuze.android.FlexibleRecyclerAdapter;
import com.vuze.android.FlexibleRecyclerSelectionListener;
import com.vuze.android.remote.*;
import com.vuze.android.remote.TextViewFlipper.FlipValidator;
import com.vuze.util.MapUtils;

/**
 * Checked == Activated according to google.  In google docs for View
 * .setActivated:
 * (Um, yeah, we are deeply sorry about the terminology here.)
 * <p/>
 * </p>
 * Other terms:
 * Focused: One focus per screen
 * Selected: highlighted item(s).  May not be activated
 * Checked: activated item(s)
 */
public class TorrentListAdapter
	extends FlexibleRecyclerAdapter<TorrentListViewHolder, Long>
	implements Filterable
{
	public final static int FILTERBY_ALL = 8;

	public final static int FILTERBY_ACTIVE = 4;

	public final static int FILTERBY_COMPLETE = 9;

	public final static int FILTERBY_INCOMPLETE = 1;

	public final static int FILTERBY_STOPPED = 2;

	public static final boolean DEBUG = AndroidUtils.DEBUG;

	private static final String TAG = "TorrentListAdapter";

	public static class ViewHolderFlipValidator
		implements FlipValidator
	{
		private TorrentListViewHolder holder;

		private long torrentID;

		public ViewHolderFlipValidator(TorrentListViewHolder holder,
				long torrentID) {
			this.holder = holder;
			this.torrentID = torrentID;
		}

		@Override
		public boolean isStillValid() {
			return holder.torrentID == torrentID;
		}
	}

	private Context context;

	private TorrentFilter filter;

	public final Object mLock = new Object();

	private Comparator<? super Map<?, ?>> comparator;

	private String[] sortFieldIDs;

	private Boolean[] sortOrderAsc;

	private SessionInfo sessionInfo;

	private TorrentListRowFiller torrentListRowFiller;

	private boolean isRefreshing;

	public TorrentListAdapter(Context context,
			FlexibleRecyclerSelectionListener selector) {
		super(selector);
		this.context = context;

		torrentListRowFiller = new TorrentListRowFiller(context);
	}

	public void lettersUpdated(HashMap<String, Integer> setLetters) {

	}

	public void setSessionInfo(SessionInfo sessionInfo) {
		this.sessionInfo = sessionInfo;
	}

	@Override
	public TorrentFilter getFilter() {
		if (filter == null) {
			filter = new TorrentFilter();
		}
		return filter;
	}

	public class TorrentFilter
		extends Filter
	{
		private long filterMode;

		private String constraint;

		private boolean compactDigits = true;

		private boolean compactNonLetters = true;

		private boolean buildLetters = false;

		private boolean compactPunctuation = true;

		public void setFilterMode(long filterMode) {
			this.filterMode = filterMode;
			filter(constraint);
		}

		public void setBuildLetters(boolean buildLetters) {
			this.buildLetters = buildLetters;
		}

		public void refilter() {
			filter(constraint);
		}

		@Override
		protected FilterResults performFiltering(CharSequence _constraint) {
			synchronized (mLock) {
				isRefreshing = false;
			}

			this.constraint = _constraint == null ? null
					: _constraint.toString().toUpperCase(Locale.US);
			FilterResults results = new FilterResults();

			if (sessionInfo == null) {
				if (DEBUG) {
					Log.d(TAG, "performFiltering skipped: No sessionInfo");
				}
				return results;
			}

			boolean hasConstraint = buildLetters
					|| (constraint != null && constraint.length() > 0);

			LongSparseArray<Map<?, ?>> torrentList = sessionInfo.getTorrentListSparseArray();
			int size = torrentList.size();

			if (DEBUG) {
				Log.d(TAG, "performFiltering: size=" + size + "/hasConstraint? "
						+ hasConstraint + "/filter=" + filterMode);
			}

			if (size > 0 && (hasConstraint || filterMode > 0)) {
				if (DEBUG) {
					Log.d(TAG, "filtering " + torrentList.size());
				}

				if (filterMode >= 0 && filterMode != FILTERBY_ALL) {
					synchronized (mLock) {
						for (int i = size - 1; i >= 0; i--) {
							long key = torrentList.keyAt(i);

							if (!filterCheck(filterMode, key)) {
								torrentList.removeAt(i);
								size--;
							}
						}
					}
				}

				if (DEBUG) {
					Log.d(TAG, "type filtered to " + size);
				}

				if (hasConstraint) {
					if (constraint == null) {
						constraint = "";
					}
					HashSet<String> setLetters = null;
					HashMap<String, Integer> mapLetterCount = null;
					if (buildLetters) {
						setLetters = new HashSet<>();
						mapLetterCount = new HashMap<>();
					}
					synchronized (mLock) {
						for (int i = size - 1; i >= 0; i--) {
							long key = torrentList.keyAt(i);

							if (!constraintCheck(constraint, key, setLetters,
									constraint.toString(), compactDigits, compactNonLetters,
									compactPunctuation)) {
								torrentList.removeAt(i);
								size--;
							}

							if (buildLetters && setLetters.size() > 0) {
								for (String letter : setLetters) {
									Integer count = mapLetterCount.get(letter);
									if (count == null) {
										count = 1;
									} else {
										count++;
									}
									mapLetterCount.put(letter, count);
								}
								setLetters.clear();
							}
						}
					}

					if (buildLetters) {
						lettersUpdated(mapLetterCount);
					}

					if (DEBUG) {
						Log.d(TAG, "text filtered to " + size);
					}
				}
			}
			int num = torrentList.size();
			ArrayList<Long> keys = new ArrayList<>(num);
			for (int i = 0; i < num; i++) {
				keys.add(torrentList.keyAt(i));
			}

			results.values = keys;
			results.count = keys.size();

			return results;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint,
				FilterResults results) {
			// Now we have to inform the adapter about the new list filtered
			if (results.count == 0) {
				removeAllItems();
			} else {
				synchronized (mLock) {
					if (results.values instanceof List) {
						setItems((List<Long>) results.values);
						doSort();
					}
				}
			}
		}

		public boolean getCompactDigits() {
			return compactDigits;
		}

		public boolean setCompactDigits(boolean compactDigits) {
			if (this.compactDigits == compactDigits) {
				return false;
			}
			this.compactDigits = compactDigits;
			return true;
		}

		public boolean getCompactNonLetters() {
			return compactNonLetters;
		}

		public boolean setCompactOther(boolean compactNonLetters) {
			if (this.compactNonLetters == compactNonLetters) {
				return false;
			}
			this.compactNonLetters = compactNonLetters;
			return true;
		}

		public boolean setCompactPunctuation(boolean compactPunctuation) {
			if (this.compactPunctuation == compactPunctuation) {
				return false;
			}
			this.compactPunctuation = compactPunctuation;
			return true;
		}

		public boolean getCompactPunctuation() {
			return compactPunctuation;
		}
	}

	public void refreshDisplayList() {
		synchronized (mLock) {
			if (isRefreshing) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "skipped refreshDisplayList");
				}
				return;
			}
			isRefreshing = true;
		}
		getFilter().refilter();
	}

	public boolean constraintCheck(CharSequence constraint, long torrentID,
			HashSet<String> setLetters, String charAfter, boolean compactDigits,
			boolean compactNonLetters, boolean compactPunctuation) {
		if (setLetters == null
				&& (constraint == null || constraint.length() == 0)) {
			return true;
		}
		Map<?, ?> map = sessionInfo.getTorrent(torrentID);
		if (map == null) {
			return false;
		}

		String name = MapUtils.getMapString(map,
				TransmissionVars.FIELD_TORRENT_NAME, "").toUpperCase(Locale.US);
		if (setLetters != null) {
			int nameLength = name.length();
			if (charAfter.length() > 0) {
				int pos = name.indexOf(charAfter);
				while (pos >= 0) {
					int end = pos + charAfter.length();
					if (end < nameLength) {
						char c = name.charAt(end);
						boolean isDigit = Character.isDigit(c);
						if (compactDigits && isDigit) {
							setLetters.add(TorrentListFragment.LETTERS_NUMBERS);
						} else if (compactPunctuation && isStandardPuncuation(c)) {
							setLetters.add(TorrentListFragment.LETTERS_PUNCTUATION);
						} else if (compactNonLetters && !isDigit && !isAlphabetic(c)
								&& !isStandardPuncuation(c)) {
							setLetters.add(TorrentListFragment.LETTERS_NON);
						} else {
							setLetters.add(Character.toString(c));
						}
					}
					pos = name.indexOf(charAfter, pos + 1);
				}
			} else {
				for (int i = 0; i < nameLength; i++) {
					char c = name.charAt(i);
					boolean isDigit = Character.isDigit(c);
					if (compactDigits && isDigit) {
						setLetters.add(TorrentListFragment.LETTERS_NUMBERS);
					} else if (compactPunctuation && isStandardPuncuation(c)) {
						setLetters.add(TorrentListFragment.LETTERS_PUNCTUATION);
					} else if (compactNonLetters && !isDigit && !isAlphabetic(c)
							&& !isStandardPuncuation(c)) {
						setLetters.add(TorrentListFragment.LETTERS_NON);
					} else {
						setLetters.add(Character.toString(c));
					}
				}
			}
		}
		if (constraint == null || constraint.length() == 0) {
			return true;
		}
		return name.contains(constraint);
	}

	private static boolean isAlphabetic(int c) {
		// Seems to return symbolic languages
//		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//			return Character.isAlphabetic(c);
//		}
		if (!Character.isLetter(c)) {
			return false;
		}
		int type = Character.getType(c);
		return type == Character.UPPERCASE_LETTER
				|| type == Character.LOWERCASE_LETTER;
		// Simple, but doesn't include letters with hats on them ;)
		//return ('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z');
	}

	private static boolean isStandardPuncuation(int c) {
		int type = Character.getType(c);
		return type == Character.START_PUNCTUATION
				|| type == Character.END_PUNCTUATION
				|| type == Character.OTHER_PUNCTUATION;
	}

	private boolean filterCheck(long filterMode, long torrentID) {
		Map<?, ?> map = sessionInfo.getTorrent(torrentID);
		if (map == null) {
			return false;
		}

		if (filterMode > 10) {
			List<?> listTagUIDs = MapUtils.getMapList(map, "tag-uids", null);
			return listTagUIDs != null && listTagUIDs.contains(filterMode);
		}

		switch ((int) filterMode) {
			case FILTERBY_ACTIVE:
				long dlRate = MapUtils.getMapLong(map,
						TransmissionVars.FIELD_TORRENT_RATE_DOWNLOAD, -1);
				long ulRate = MapUtils.getMapLong(map,
						TransmissionVars.FIELD_TORRENT_RATE_UPLOAD, -1);
				if (ulRate <= 0 && dlRate <= 0) {
					return false;
				}
				break;

			case FILTERBY_COMPLETE: {
				float pctDone = MapUtils.getMapFloat(map,
						TransmissionVars.FIELD_TORRENT_PERCENT_DONE, 0);
				if (pctDone < 1.0f) {
					return false;
				}
				break;
			}
			case FILTERBY_INCOMPLETE: {
				float pctDone = MapUtils.getMapFloat(map,
						TransmissionVars.FIELD_TORRENT_PERCENT_DONE, 0);
				if (pctDone >= 1.0f) {
					return false;
				}
				break;
			}
			case FILTERBY_STOPPED: {
				int status = MapUtils.getMapInt(map,
						TransmissionVars.FIELD_TORRENT_STATUS,
						TransmissionVars.TR_STATUS_STOPPED);
				if (status != TransmissionVars.TR_STATUS_STOPPED) {
					return false;
				}
				break;
			}
		}
		return true;
	}

	public void setSort(String[] fieldIDs, Boolean[] sortOrderAsc) {
		synchronized (mLock) {
			sortFieldIDs = fieldIDs;
			Boolean[] order;
			if (sortOrderAsc == null) {
				order = new Boolean[sortFieldIDs.length];
				Arrays.fill(order, Boolean.FALSE);
			} else if (sortOrderAsc.length != sortFieldIDs.length) {
				order = new Boolean[sortFieldIDs.length];
				Arrays.fill(order, Boolean.FALSE);
				System.arraycopy(sortOrderAsc, 0, order, 0, sortOrderAsc.length);
			} else {
				order = sortOrderAsc;
			}
			this.sortOrderAsc = order;
			comparator = null;
		}
		doSort();
	}

	public void setSort(Comparator<? super Map<?, ?>> comparator) {
		synchronized (mLock) {
			this.comparator = comparator;
		}
		doSort();
	}

	private void doSort() {
		if (sessionInfo == null) {
			if (DEBUG) {
				Log.d(TAG, "doSort skipped: No sessionInfo");
			}
			return;
		}
		if (comparator == null && sortFieldIDs == null) {
			if (DEBUG) {
				Log.d(TAG, "doSort skipped: no comparator and no sort");
			}
			return;
		}
		if (DEBUG) {
			Log.d(TAG, "sort: " + Arrays.asList(sortFieldIDs) + "/"
					+ Arrays.asList(sortOrderAsc));
		}

		ComparatorMapFields sorter = new ComparatorMapFields(sortFieldIDs,
				sortOrderAsc, comparator) {

			@Override
			public int reportError(Comparable<?> oLHS, Comparable<?> oRHS,
					Throwable t) {
				VuzeEasyTracker.getInstance(context).logError(t);
				return 0;
			}

			@Override
			public Map<?, ?> mapGetter(Object o) {
				return sessionInfo.getTorrent((Long) o);
			}

			@SuppressWarnings("rawtypes")
			@Override
			public Comparable modifyField(String fieldID, Map map, Comparable o) {
				if (fieldID.equals(TransmissionVars.FIELD_TORRENT_POSITION)) {
					return (MapUtils.getMapLong(map,
							TransmissionVars.FIELD_TORRENT_LEFT_UNTIL_DONE, 1) == 0
									? 0x1000000000000000L : 0)
							+ ((Number) o).longValue();
				}
				if (fieldID.equals(TransmissionVars.FIELD_TORRENT_ETA)) {
					if (((Number) o).longValue() < 0) {
						o = Long.MAX_VALUE;
					}
				}
				return o;
			}
		};

		sortItems(sorter);
	}

	public Map<?, ?> getTorrentItem(int position) {
		if (sessionInfo == null) {
			return new HashMap<Object, Object>();
		}
		Long item = getItem(position);
		if (item == null) {
			return new HashMap<Object, Object>();
		}
		return sessionInfo.getTorrent(item);
	}

	public long getTorrentID(int position) {
		Long torrentID = getItem(position);
		return torrentID == null ? -1 : torrentID;
	}

	@SuppressWarnings("unchecked")
	@Override
	public TorrentListViewHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {
		boolean isSmall = sessionInfo.getRemoteProfile().useSmallLists();
		int resourceID = isSmall ? R.layout.row_torrent_list_small
				: R.layout.row_torrent_list;
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		View rowView = inflater.inflate(resourceID, parent, false);
		TorrentListViewHolder holder = new TorrentListViewHolder(this, rowView,
				isSmall);

		rowView.setTag(holder);
		return holder;
	}

	@Override
	public void onBindFlexibleViewHolder(TorrentListViewHolder holder,
			int position) {
		Map<?, ?> item = getTorrentItem(position);
		torrentListRowFiller.fillHolder(holder, item, sessionInfo);
	}

	@Override
	public long getItemId(int position) {
		return getTorrentID(position);
	}
}