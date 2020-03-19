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

import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.Session_Tag;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import java.util.*;

import static com.biglybt.android.client.adapter.TorrentListSorter.SORTDEFINITION_ACTIVESORT;

public class TorrentListFilter
	extends LetterFilter<TorrentListAdapterItem>
{

	private static final String TAG = "TorrentFilter";

	public final static int FILTERBY_ALL = 8;

	public final static int FILTERBY_ACTIVE = 4;

	public final static int FILTERBY_COMPLETE = 9;

	public final static int FILTERBY_INCOMPLETE = 1;

	public final static int FILTERBY_STOPPED = 2;

	private static final String RESULTFIELD_LIST = "list";

	private static final String RESULTFIELD_SECTIONS = "sections";

	private static final String RESULTFIELD_SECTION_STARTS = "sectionStarts";

	private static final String RESULTFIELD_COUNTS_VIEWTYPE = "countsByViewType";

	public static final String KEY_SUFFIX_FILTER_MODE = ".filterMode";

	@Thunk
	final Object lockSections = new Object();

	@Thunk
	String[] sections;

	@Thunk
	List<Integer> sectionStarts;

	@Thunk
	final SessionAdapterFilterTalkback<TorrentListAdapterItem> talkback;

	private long filterMode;

	private int defaultSortID;

	TorrentListFilter(
			@NonNull SessionAdapterFilterTalkback<TorrentListAdapterItem> talkback) {
		super(talkback);
		this.talkback = talkback;

		StoredSortByInfo sortByInfo = talkback.getSession().getRemoteProfile().getSortByInfo(
				"");
		SortDefinition sortDefinition = SortDefinition.findSortDefinition(
				sortByInfo, getSortDefinitions(), defaultSortID);
		boolean isAsc = sortByInfo == null ? sortDefinition.isSortAsc()
				: sortByInfo.isAsc;

		setSorter(new TorrentListSorter(talkback, sortDefinition, isAsc));
	}

	public void setFilterMode(long filterMode) {
		this.filterMode = filterMode;
		Session session = talkback.getSession();
		if (session.torrent.getLastListReceivedOn() > 0) {
			refilter(false);
		}
	}

	@Override
	protected FilterResults performFiltering2(CharSequence _constraint) {
		FilterResults results = new FilterResults();

		Session session = talkback.getSession();
		if (session == null || session.isDestroyed()) {
			if (TorrentListAdapter.DEBUG) {
				log(TAG, "performFiltering skipped: No session? " + (session == null));
			}
			return results;
		}

		LongSparseArray<Map<?, ?>> torrentList = session.torrent.getListAsSparseArray();
		int size = torrentList.size();

		if (TorrentListAdapter.DEBUG) {
			log(TAG, "performFiltering: size=" + size + "/filterMode=" + filterMode);
		}

		if (size > 0 && filterMode > 0) {
			if (TorrentListAdapter.DEBUG) {
				log(TAG, "filtering " + torrentList.size());
			}

			if (filterMode >= 0 && filterMode != FILTERBY_ALL) {
				for (int i = size - 1; i >= 0; i--) {
					long key = torrentList.keyAt(i);

					if (!filterCheck(filterMode, key)) {
						torrentList.removeAt(i);
						size--;
					}
				}
			}

			if (TorrentListAdapter.DEBUG) {
				log(TAG, "type filtered to " + size);
			}
		}
		int num = torrentList.size();
		ArrayList<TorrentListAdapterItem> keys = new ArrayList<>(num);
		for (int i = 0; i < num; i++) {
			keys.add(new TorrentListAdapterTorrentItem(torrentList.keyAt(i)));
		}

		performLetterFiltering(_constraint, keys);

		doSort(keys);

		Map<String, Object> map = new HashMap<>();
		map.put(RESULTFIELD_LIST, keys);
		refreshSections(keys, map);

		results.values = map;
		results.count = keys.size();

		return results;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected boolean publishResults2(CharSequence constraint,
			FilterResults results) {
		// Now we have to inform the adapter about the new list filtered
		if (results.count == 0 || !(results.values instanceof Map)) {
			Session session = talkback.getSession();
			if (session != null && session.torrent.getCount() > 0) {
				talkback.removeAllItems();
			}
			return true;
		}

		Map map = (Map) results.values;
		List<TorrentListAdapterItem> displayList = (List<TorrentListAdapterItem>) map.get(
				RESULTFIELD_LIST);
		synchronized (lockSections) {
			sections = (String[]) map.get(RESULTFIELD_SECTIONS);
			sectionStarts = (List<Integer>) map.get(RESULTFIELD_SECTION_STARTS);
		}

		SparseIntArray countsByViewType = (SparseIntArray) map.get(
				RESULTFIELD_COUNTS_VIEWTYPE);

		if (displayList == null) {
			displayList = new ArrayList<>();
		}

		return talkback.setItems(displayList, countsByViewType);
	}

	@Nullable
	@Override
	protected String getStringToConstrain(TorrentListAdapterItem item) {
		if (item instanceof TorrentListAdapterTorrentItem) {
			Session session = talkback.getSession();
			Map<?, ?> map = ((TorrentListAdapterTorrentItem) item).getTorrentMap(
					session);
			if (map == null) {
				return null;
			}

			return MapUtils.getMapString(map, TransmissionVars.FIELD_TORRENT_NAME,
					"");
		} else {
			return null;
		}
	}

	@Thunk
	boolean filterCheck(long filterMode, long torrentID) {
		Session session = talkback.getSession();
		Map<?, ?> map = session.torrent.getCachedTorrent(torrentID);
		if (map == null) {
			return false;
		}

		if (filterMode > 10) {
			List<?> listTagUIDs = MapUtils.getMapList(map,
					TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
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

	private void refreshSections(List<TorrentListAdapterItem> items,
			Map<String, Object> map) {
		SparseIntArray countsByViewType = new SparseIntArray();
		TorrentListSorter sorter = (TorrentListSorter) getSorter();
		if (sorter == null) {
			return;
		}

		GroupedSortDefinition<TorrentListAdapterItem, Integer> sortDefinition = sorter.getGroupedSortDefinition();
		if (sortDefinition == null) {
			// doesn't support grouping
			return;
		}
		if (items.size() < sortDefinition.getMinCountBeforeGrouping()) {
			return;
		}

		List<Comparable> groupIDs = new ArrayList<>();
		List<String> groupNames = new ArrayList<>();
		List<Integer> groupStartPositions = new ArrayList<>();
		List<Integer> groupCounts = new ArrayList<>();

		ListIterator<TorrentListAdapterItem> iterator = items.listIterator();

		boolean isAsc = sorter.isAsc();
		Comparable lastID = null;
		int pos = -1;
		int numInGroup = 0;
		boolean collapsed = false;
		int countHeaders = 0;
		int countItems = items.size();
		while (iterator.hasNext()) {
			TorrentListAdapterItem item = iterator.next();
			if (!(item instanceof TorrentListAdapterTorrentItem)) {
				countItems--;
				iterator.remove();
				continue;
			}
			if (!collapsed) {
				pos++;
			}
			Integer id = sortDefinition.getGroupID(item, isAsc, items);
			if (id == null) {
				continue;
			}
			if (id.equals(lastID)) {
				numInGroup++;
			} else {
				// First item or New Group
				groupIDs.add(id);
				collapsed = isGroupCollapsed(id);
				groupNames.add(sortDefinition.getGroupName(id, isAsc));
				groupStartPositions.add(pos);
				pos++;
				if (lastID != null) {
					// New Group, add count of previous group
					groupCounts.add(numInGroup);
				}
				numInGroup = 1;
			}
			lastID = id;
		}
		groupCounts.add(numInGroup);

		// Add Headers and collapse entries if needed
		for (int i = 0, size = groupNames.size(); i < size; i++) {
			//noinspection ConstantConditions
			int position = groupStartPositions.get(i);
			//noinspection ConstantConditions
			numInGroup = groupCounts.get(i);
			Comparable groupID = groupIDs.get(i);
			items.add(position, new TorrentListAdapterHeaderItem(groupID,
					groupNames.get(i), numInGroup));
			countHeaders++;
			if (isGroupCollapsed(groupID)) {
				items.subList(position + 1, position + numInGroup + 1).clear();
				countItems -= numInGroup;
			}
		}

		countsByViewType.put(TorrentListAdapter.VIEWTYPE_HEADER, countHeaders);
		countsByViewType.put(TorrentListAdapter.VIEWTYPE_TORRENT, countItems);

		map.put(RESULTFIELD_SECTIONS, groupNames.toArray(new String[0]));
		map.put(RESULTFIELD_SECTION_STARTS, groupStartPositions);
		map.put(RESULTFIELD_COUNTS_VIEWTYPE, countsByViewType);
	}

//	@Override
//	public Object[] getSections() {
//		if (AndroidUtils.DEBUG) {
//			log(TAG,
//					"GetSections " + (sections == null ? "NULL" : sections.length));
//		}
//		return sections;
//	}

//	@Override
//	public int getPositionForSection(int sectionIndex) {
//		synchronized (lockSections) {
//			if (sectionIndex < 0 || sectionStarts == null
//					|| sectionIndex >= sectionStarts.size()) {
//				return 0;
//			}
//			return sectionStarts.get(sectionIndex);
//		}
//	}

	//	@Override
	private int getSectionForPosition(int position) {
		synchronized (lockSections) {
			if (sectionStarts == null || sections == null) {
				return 0;
			}
			int i = Collections.binarySearch(sectionStarts, position);
			if (i < 0) {
				i = (-1 * i) - 2;
			}
			if (i >= sections.length) {
				i = sections.length - 1;
			} else if (i < 0) {
				i = 0;
			}
			return i;
		}
	}

	@NonNull
	@Override
	public String getSectionName(int position) {
		synchronized (lockSections) {
			if (sections == null) {
				return "";
			}
			int sectionForPosition = getSectionForPosition(position);
			if (sectionForPosition != 0 || sections.length > 0) {
				return sections[sectionForPosition];
			}
			return "";
		}
	}

	@Override
	public boolean showLetterUI() {
		return talkback.getSession().torrent.getCount() > 3;
	}

	@NonNull
	@Override
	public SparseArray<SortDefinition> createSortDefinitions() {
		Resources resources = BiglyBTApp.getContext().getResources();
		String[] sortNames = resources.getStringArray(R.array.sortby_list);

		SparseArray<SortDefinition> sortDefinitions = new SparseArray<>(
				sortNames.length);
		int i = 0;

		// <item>Queue</item>
		GroupedSortDefinition<TorrentListAdapterItem, Integer> sdQueue = new GroupedSortDefinition<TorrentListAdapterItem, Integer>(
				i, sortNames[i], new String[] {
					TransmissionVars.FIELD_TORRENT_IS_COMPLETE,
					TransmissionVars.FIELD_TORRENT_POSITION
				}, new Boolean[] {
					SortDefinition.SORT_NATURAL,
					SortDefinition.SORT_NATURAL
				}, SortDefinition.SORT_ASC) {

			String completeText;

			String incompleteText;

			@Override
			public void sortEventTriggered(int sortEventID) {
				switch (sortEventID) {
					case SORTEVENT_ACTIVATING: {
						AndroidUtils.ValueStringArray filterByList = AndroidUtils.getValueStringArray(
								resources, R.array.filterby_list);
						for (int i = 0; i < filterByList.size; i++) {
							if (filterByList.values[i] == TorrentListFilter.FILTERBY_COMPLETE) {
								completeText = filterByList.strings[i];
							} else if (filterByList.values[i] == TorrentListFilter.FILTERBY_INCOMPLETE) {
								incompleteText = filterByList.strings[i];
							}
						}
						break;
					}
					case SORTEVENT_DEACTIVATING: {
						break;
					}
				}
			}

			@Override
			public Integer getGroupID(TorrentListAdapterItem o, boolean isAsc,
					List<TorrentListAdapterItem> items) {
				if (!(o instanceof TorrentListAdapterTorrentItem)) {
					return 0;
				}
				Session session = talkback.getSession();
				Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
						session);
				boolean complete = MapUtils.getMapBoolean(map,
						TransmissionVars.FIELD_TORRENT_IS_COMPLETE, false);
				if (items.size() < 10) {
					return complete ? -1 : -2;
				}
				long position = MapUtils.getMapLong(map,
						TransmissionVars.FIELD_TORRENT_POSITION, 1) - 1;
				return (int) ((position / 10) << 1) + (complete ? 1 : 0);
			}

			@Override
			public String getGroupName(Integer sectionID, boolean isAsc) {
				if (sectionID < 0) {
					boolean complete = sectionID == -1;
					return complete ? completeText : incompleteText;
				}

				boolean complete = (sectionID & 0x1) == 1;

				int start = (sectionID >> 1) * 10 + 1;

				return resources.getString(
						complete ? R.string.TorrentListSectionName_Queue_complete
								: R.string.TorrentListSectionName_Queue_incomplete,
						DisplayFormatters.formatNumber(isAsc ? start : start + 9),
						DisplayFormatters.formatNumber(!isAsc ? start : start + 9));
			}
		};
		sdQueue.setMinCountBeforeGrouping(1);
		sdQueue.setShowGroupCount(false);
		sortDefinitions.put(i, sdQueue);
		defaultSortID = i;

		i++; // <item>Activity</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							SORTDEFINITION_ACTIVESORT,
							TransmissionVars.FIELD_TORRENT_DATE_ACTIVITY,
						}, new Boolean[] {
							SortDefinition.SORT_REVERSED,
							SortDefinition.SORT_REVERSED,
						}, SortDefinition.SORT_ASC) {
					@Override
					public Integer getGroupID(TorrentListAdapterItem o, boolean isAsc,
							List<TorrentListAdapterItem> items) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return 0;
						}
						Session session = talkback.getSession();
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								session);
						boolean active;
						List<?> listTagUIDs = MapUtils.getMapList(map,
								TransmissionVars.FIELD_TORRENT_TAG_UIDS, null);
						Long tagUID_Active = session.tag.getDownloadStateUID(
								Session_Tag.STATEID_ACTIVE);
						if (listTagUIDs != null && tagUID_Active != null) {
							active = listTagUIDs.contains(tagUID_Active);
						} else {
							long rateDL = MapUtils.getMapLong(map,
									TransmissionVars.FIELD_TORRENT_RATE_DOWNLOAD, 0);
							long rateUL = MapUtils.getMapLong(map,
									TransmissionVars.FIELD_TORRENT_RATE_UPLOAD, 0);
							active = rateDL > 0 && rateUL > 0;
						}
						if (!active) {
							long lastActiveOn = MapUtils.getMapLong(map,
									TransmissionVars.FIELD_TORRENT_DATE_ACTIVITY, 0);
							if (lastActiveOn > 0) {
								GregorianCalendar today = new GregorianCalendar();
								GregorianCalendar calendar = new GregorianCalendar();
								calendar.setTimeInMillis(lastActiveOn * 1000);
								if (today.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)) {
									int todayDOY = today.get(Calendar.DAY_OF_YEAR);
									int thisDOY = calendar.get(Calendar.DAY_OF_YEAR);
									if (todayDOY == thisDOY) {
										return -3;
									}
									if (todayDOY - 1 == thisDOY) {
										// Note: this will miss Dec 31st
										return -4;
									}
								}
								return (calendar.get(Calendar.YEAR) << 4)
										| calendar.get(Calendar.MONTH);
							}
						}
						return active ? -1 : -2;
					}

					@Override
					public String getGroupName(Integer sectionID, boolean isAsc) {
						switch (sectionID) {
							case -1:
								return resources.getString(
										R.string.TorrentListSectionName_Activity_active);
							case -2:
								return resources.getString(
										R.string.TorrentListSectionName_Activity_inactive);
							case -3:
								return resources.getString(
										R.string.TorrentListSectionName_Activity_today);
							case -4:
								return resources.getString(
										R.string.TorrentListSectionName_Activity_yesterday);
							default:
								GregorianCalendar calendar = new GregorianCalendar();
								calendar.set(Calendar.YEAR, sectionID >> 4);
								calendar.set(Calendar.MONTH, sectionID & 0xF);
								return resources.getString(
										R.string.TorrentListSectionName_Activity_lastactive,
										DateFormat.format("MMMM, yyyy", calendar).toString());
						}
					}
				});

		i++; // <item>Age</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_DATE_ADDED
						}, SortDefinition.SORT_DESC) {
					@Override
					public Integer getGroupID(TorrentListAdapterItem o, boolean isAsc,
							List<TorrentListAdapterItem> items) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return 0;
						}
						Session session = talkback.getSession();
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								session);
						long addedOn = MapUtils.getMapLong(map,
								TransmissionVars.FIELD_TORRENT_DATE_ADDED, 0);
						GregorianCalendar calendar = new GregorianCalendar();
						calendar.setTimeInMillis(addedOn * 1000);
						return (calendar.get(Calendar.YEAR) << 4)
								+ calendar.get(Calendar.MONTH);
					}

					@Override
					public String getGroupName(Integer sectionID, boolean isAsc) {
						GregorianCalendar calendar = new GregorianCalendar();
						calendar.set(Calendar.YEAR, sectionID >> 4);
						calendar.set(Calendar.MONTH, sectionID & 0xF);
						return DateFormat.format("MMMM, yyyy", calendar).toString();
					}
				});

		i++; // <item>Progress</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_PERCENT_DONE
						}, SortDefinition.SORT_ASC) {
					@Override
					public Integer getGroupID(TorrentListAdapterItem o, boolean isAsc,
							List<TorrentListAdapterItem> items) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return 0;
						}
						Session session = talkback.getSession();
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								session);
						float pctDone = MapUtils.getMapFloat(map,
								TransmissionVars.FIELD_TORRENT_PERCENT_DONE, 0);

						return ((int) (pctDone * 10)) * 10;
					}

					@Override
					public String getGroupName(Integer sectionID, boolean isAsc) {
						if (sectionID < 10) {
							return resources.getString(
									R.string.TorrentListSectionName_Progress_under10);
						}
						if (sectionID >= 100) {
							return resources.getString(
									R.string.TorrentListSectionName_Progress_100);
						}
						long endPct = sectionID + 10;

						return resources.getString(R.string.TorrentListSectionName_Progress,
								(isAsc ? sectionID : endPct), (!isAsc ? sectionID : endPct));
					}
				});

		i++; // <item>Ratio</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO
						}, SortDefinition.SORT_ASC) {
					@Override
					public Integer getGroupID(TorrentListAdapterItem o, boolean isAsc,
							List<TorrentListAdapterItem> items) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return 0;
						}
						Session session = talkback.getSession();
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								session);
						float ratio = MapUtils.getMapFloat(map,
								TransmissionVars.FIELD_TORRENT_UPLOAD_RATIO, 0);
						return (int) ratio;
					}

					@Override
					public String getGroupName(Integer sectionID, boolean isAsc) {
						if (isAsc) {
							return "< " + (sectionID + 1) + ":1";
						}
						return "> " + sectionID + ":1";
					}
				});

		i++; // <item>Size</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_SIZE_WHEN_DONE
						}, SortDefinition.SORT_DESC) {

					static final int MB_BREAK = 500;

					@Override
					public Integer getGroupID(TorrentListAdapterItem o, boolean isAsc,
							List<TorrentListAdapterItem> items) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return 0;
						}
						Session session = talkback.getSession();
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								session);
						long bytes = MapUtils.getMapLong(map,
								TransmissionVars.FIELD_TORRENT_SIZE_WHEN_DONE, 0);

						if (bytes < 1024L * 1024L * MB_BREAK) {
							return 0;
						} else if (bytes < 1024L * 1024L * 1024L) {
							return -1;
						} else {
							return (int) (bytes / 1024 / 1024 / 1024);
						}
					}

					@Override
					public String getGroupName(Integer sectionID, boolean isAsc) {

						String start;
						String end;
						switch (sectionID) {
							case 0:
								start = "0";
								end = MB_BREAK
										+ DisplayFormatters.getUnit(DisplayFormatters.UNIT_MB);
								break;
							case -1:
								start = MB_BREAK
										+ DisplayFormatters.getUnit(DisplayFormatters.UNIT_MB);
								end = "1"
										+ DisplayFormatters.getUnit(DisplayFormatters.UNIT_GB);
								break;
							default:
								long gigsLower = sectionID;
								start = gigsLower
										+ DisplayFormatters.getUnit(DisplayFormatters.UNIT_GB);
								end = (gigsLower + 1)
										+ DisplayFormatters.getUnit(DisplayFormatters.UNIT_GB);
								break;
						}
						if (isAsc) {
							return resources.getString(R.string.filter_size, start, end);
						} else {
							return resources.getString(R.string.filter_size, end, start);
						}
					}
				});

		i++; // <item>State</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_STATUS
						}, SortDefinition.SORT_ASC) {
					@Override
					public Integer getGroupID(TorrentListAdapterItem o, boolean isAsc,
							List<TorrentListAdapterItem> items) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return -1;
						}
						Session session = talkback.getSession();
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								session);
						return MapUtils.getMapInt(map,
								TransmissionVars.FIELD_TORRENT_STATUS, 0);
					}

					@Override
					public String getGroupName(Integer sectionID, boolean isAsc) {
						int id;
						switch (sectionID) {
							case TransmissionVars.TR_STATUS_CHECK_WAIT:
							case TransmissionVars.TR_STATUS_CHECK:
								id = R.string.torrent_status_checking;
								break;

							case TransmissionVars.TR_STATUS_DOWNLOAD:
								id = R.string.torrent_status_download;
								break;

							case TransmissionVars.TR_STATUS_DOWNLOAD_WAIT:
								id = R.string.torrent_status_queued_dl;
								break;

							case TransmissionVars.TR_STATUS_SEED:
								id = R.string.torrent_status_seed;
								break;

							case TransmissionVars.TR_STATUS_SEED_WAIT:
								id = R.string.torrent_status_queued_ul;
								break;

							case TransmissionVars.TR_STATUS_STOPPED:
								id = R.string.torrent_status_stopped;
								break;

							default:
								id = -1;
								break;
						}
						if (id >= 0) {
							return resources.getString(id);
						}
						return "" + id;
					}
				});

		i++; // <item>ETA</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_ETA,
							TransmissionVars.FIELD_TORRENT_PERCENT_DONE
						}, new Boolean[] {
							SortDefinition.SORT_NATURAL,
							SortDefinition.SORT_REVERSED
						}, SortDefinition.SORT_ASC) {
					@Override
					public Integer getGroupID(TorrentListAdapterItem o, boolean isAsc,
							List<TorrentListAdapterItem> items) {
						if (!(o instanceof TorrentListAdapterTorrentItem)) {
							return -1;
						}
						Session session = talkback.getSession();
						Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
								session);
						long etaSecs = MapUtils.getMapLong(map,
								TransmissionVars.FIELD_TORRENT_ETA, -1);
						if (etaSecs < 0) {
							float pctDone = MapUtils.getMapFloat(map,
									TransmissionVars.FIELD_TORRENT_PERCENT_DONE, 0);
							if (pctDone >= 1) {
								return 0;
							}
							return 1;
						}
						return 2;
					}

					@Override
					public String getGroupName(Integer sectionID, boolean isAsc) {
						switch (sectionID) {
							case 0:
								return resources.getString(
										R.string.TorrentListSectionName_ETA_complete);
							case 1:
								return resources.getString(
										R.string.TorrentListSectionName_ETA_none);
							case 2:
								return resources.getString(
										R.string.TorrentListSectionName_ETA_available);
						}
						return "";
					}
				});

		i++; // <item>Count</item>
		sortDefinitions.put(i,
				new GroupedSortDefinition<TorrentListAdapterItem, Integer>(i,
						sortNames[i], new String[] {
							TransmissionVars.FIELD_TORRENT_FILE_COUNT,
							TransmissionVars.FIELD_TORRENT_SIZE_WHEN_DONE
						}, new Boolean[] {
							SortDefinition.SORT_NATURAL,
							SortDefinition.SORT_REVERSED
						}, SortDefinition.SORT_ASC) {
					@Override
					public Integer getGroupID(TorrentListAdapterItem o, boolean isAsc,
							List<TorrentListAdapterItem> items) {
						{
							if (!(o instanceof TorrentListAdapterTorrentItem)) {
								return -1;
							}
							Session session = talkback.getSession();
							Map<?, ?> map = ((TorrentListAdapterTorrentItem) o).getTorrentMap(
									session);
							int numFiles = MapUtils.getMapInt(map,
									TransmissionVars.FIELD_TORRENT_FILE_COUNT, 0);
							if (numFiles < 0) {
								return -1;
							} else if (numFiles == 1) {
								return 1;
							} else if (numFiles < 4) {
								return 2;
							} else if (numFiles < 100) {
								return 3;
							} else if (numFiles < 2000) {
								return 4;
							} else {
								return 5;
							}
						}
					}

					@Override
					public String getGroupName(Integer sectionID, boolean isAsc) {
						switch (sectionID) {
							case 1:
								return resources.getString(
										R.string.TorrentListSectionName_File_1);
							case 2:
								return resources.getString(
										R.string.TorrentListSectionName_File_few);
							case 3:
								return resources.getString(
										R.string.TorrentListSectionName_File_many);
							case 4:
								return resources.getString(
										R.string.TorrentListSectionName_File_hundreds);
							case 5:
								return resources.getString(
										R.string.TorrentListSectionName_File_thousands);
						}
						return "";
					}

				});
		return sortDefinitions;
	}

	@Override
	protected void saveSortDefinition(SortDefinition sortDefinition,
			boolean isAsc) {
		Session session = talkback.getSession();
		if (session.getRemoteProfile().setSortBy("", sortDefinition, isAsc)) {
			session.saveProfile();
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putLong(TAG + KEY_SUFFIX_FILTER_MODE, filterMode);
	}

	@Override
	public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		filterMode = savedInstanceState.getLong(TAG + KEY_SUFFIX_FILTER_MODE,
				filterMode);
	}
}
