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

package com.biglybt.android.client.adapter;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.biglybt.android.adapter.*;
import com.biglybt.android.client.*;
import com.biglybt.android.client.rpc.ReplyMapReceivedListener;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.util.MapUtils;
import com.biglybt.android.util.TextViewFlipper;
import com.biglybt.util.DisplayFormatters;
import com.biglybt.util.Thunk;

import android.arch.lifecycle.Lifecycle;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

public class FilesTreeAdapter
	extends
	SortableRecyclerAdapter<FilesTreeAdapter, FilesTreeViewHolder, FilesAdapterItem>
	implements FlexibleRecyclerAdapter.SetItemsCallBack<FilesAdapterItem>,
	SessionAdapterFilterTalkback<FilesAdapterItem>
{

	@Thunk
	static final String TAG = "FilesTreeAdapter2";

	public static final int TYPE_FOLDER = 0;

	@SuppressWarnings("WeakerAccess")
	public static final int TYPE_FILE = 1;

	@Thunk
	static final Pattern patternFolderSplit = Pattern.compile("[\\\\/]");

	@NonNull
	private final SessionGetter sessionGetter;

	@Thunk
	long torrentID;

	private final TextViewFlipper flipper;

	private final int levelPaddingPx;

	private boolean inEditMode = false;

	private final int levelPadding2Px;

	public boolean useTree;

	private boolean requiresPostSetItemsInvalidate = false;

	private ImageSpan trashImageSpan;

	public FilesTreeAdapter(Lifecycle lifecycle,
			@NonNull SessionGetter sessionGetter,
			FlexibleRecyclerSelectionListener<FilesTreeAdapter, FilesTreeViewHolder, FilesAdapterItem> selector) {
		super(TAG, lifecycle, selector);
		this.sessionGetter = sessionGetter;
		flipper = TextViewFlipper.create();

		final Context context = BiglyBTApp.getContext();
		final int screenWidthDp = AndroidUtilsUI.getScreenWidthDp(context);
		levelPaddingPx = AndroidUtilsUI.dpToPx(screenWidthDp >= 600 ? 32 : 20);
		levelPadding2Px = AndroidUtilsUI.dpToPx(screenWidthDp >= 600 ? 10 : 5);
	}

	@Override
	public FilesTreeViewHolder onCreateFlexibleViewHolder(ViewGroup parent,
			int viewType) {

		boolean isFolder = viewType == TYPE_FOLDER;
		LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);
		assert inflater != null;
		View rowView = inflater.inflate(
				isFolder ? R.layout.row_folder_selection : R.layout.row_file_selection,
				parent, false);
		FilesTreeViewHolder viewHolder = new FilesTreeViewHolder(this, rowView);

		rowView.setTag(viewHolder);

		return viewHolder;
	}

	@Override
	public void setSortDefinition(SortDefinition sortDefinition, boolean isAsc) {
		boolean useTree = sortDefinition != null && sortDefinition.id == 0L;
		requiresPostSetItemsInvalidate = useTree != this.useTree;
		this.useTree = useTree;

		super.setSortDefinition(sortDefinition, isAsc);
	}

	@Override
	public void onBindFlexibleViewHolder(FilesTreeViewHolder holder,
			int position) {
		Object oItem = getItem(position);
		boolean isFolder = (oItem instanceof FilesAdapterItemFolder);

		int level = useTree ? ((FilesAdapterItem) oItem).level : 0;
		int paddingX = levelPaddingPx * level;
		int parentWidth = holder.itemView.getWidth();
		// if first 6 take up 1/3rd of the width, make levels over 6 use smaller width
		if (level > 6 && (levelPaddingPx * 6) > parentWidth / 4) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG, "Using smaller Padding.. from " + paddingX + " to "
						+ ((levelPaddingPx * 6) + (levelPadding2Px * (level - 6))));
			}
			paddingX = (levelPaddingPx * 6) + (levelPadding2Px * (level - 6));
		}

		if (holder.strip != null) {
			android.view.ViewGroup.LayoutParams lp = holder.strip.getLayoutParams();
			if (lp instanceof LinearLayout.LayoutParams) {
				holder.strip.setLayoutParams(new LinearLayout.LayoutParams(
						levelPaddingPx * level, LayoutParams.MATCH_PARENT));
			} else if (lp instanceof RelativeLayout.LayoutParams) {
				holder.strip.setLayoutParams(new RelativeLayout.LayoutParams(
						levelPaddingPx * level, LayoutParams.MATCH_PARENT));
			}
		} else if (holder.layout != null) {
			holder.layout.setPadding(paddingX, holder.layout.getPaddingTop(),
					holder.layout.getPaddingRight(), holder.layout.getPaddingBottom());
		}

		if (holder.btnWant != null) {
			holder.btnWant.setVisibility(inEditMode ? View.VISIBLE : View.GONE);
		}

		// There's common code in both buildViews that can be moved up here
		if (isFolder) {
			buildView((FilesAdapterItemFolder) oItem, holder);
		} else {
			buildView((FilesAdapterItemFile) oItem, holder);
		}
	}

	@Override
	public boolean areContentsTheSame(FilesAdapterItem oldItem,
			FilesAdapterItem newItem) {
		// Assumed that items represent same file
		if (oldItem instanceof FilesAdapterItemFile) {
			FilesAdapterItemFile oldFile = (FilesAdapterItemFile) oldItem;
			FilesAdapterItemFile newFile = (FilesAdapterItemFile) newItem;
			return oldFile.want == newFile.want
					&& oldFile.priority == newFile.priority
					&& oldFile.bytesComplete == newFile.bytesComplete
					&& oldFile.name.equals(newFile.name);
		} else {
			FilesAdapterItemFolder oldF = (FilesAdapterItemFolder) oldItem;
			FilesAdapterItemFolder newF = (FilesAdapterItemFolder) newItem;
			return oldF.expand == newF.expand
					&& oldF.numFilesWanted == newF.numFilesWanted
					&& oldF.numFilesFilteredWanted == newF.numFilesFilteredWanted
					&& oldF.getNumFilteredFiles() == newF.getNumFilteredFiles()
					&& oldF.sizeWanted == newF.sizeWanted
					&& oldF.folder.equals(newF.folder);
		}
	}

	private void buildView(final FilesAdapterItemFolder oFolder,
			FilesTreeViewHolder holder) {

		FilesTreeViewHolderFlipValidator validator = new FilesTreeViewHolderFlipValidator(
				holder, torrentID, -3);
		boolean animateFlip = validator.isStillValid();
		holder.fileIndex = -3;
		holder.torrentID = torrentID;

		if (holder.tvName != null) {
			int breakAt = AndroidUtils.lastindexOfAny(oFolder.folder,
					TorrentUtils.ANYSLASH, oFolder.folder.length() - 2);
			String s = (breakAt > 0) ? oFolder.folder.substring(breakAt + 1)
					: oFolder.folder;
			flipper.changeText(holder.tvName, AndroidUtils.lineBreaker(s),
					animateFlip, validator);
		}
		if (holder.expando != null) {
			holder.expando.setImageResource(
					oFolder.expand ? R.drawable.ic_folder_open_black_24dp
							: R.drawable.ic_folder_black_24dp);
			holder.expando.setOnClickListener(
					v -> setExpandState(oFolder, !oFolder.expand));
		}
		int numFiles = oFolder.getNumFiles();
		if (holder.tvInfo != null) {
			final Resources resources = holder.tvInfo.getResources();

			String s;

			int numFilteredFiles = oFolder.getNumFilteredFiles();
			if (oFolder.numFilesWanted == numFiles
					&& oFolder.numFilesFilteredWanted == numFilteredFiles
					&& numFiles == numFilteredFiles) {
				// simple summary
				s = resources.getQuantityString(R.plurals.folder_summary_simple,
						numFiles, DisplayFormatters.formatNumber(numFiles),
						DisplayFormatters.formatByteCountToKiBEtc(oFolder.size));
			} else {

				String summaryWanted = resources.getString(R.string.folder_summary,
						DisplayFormatters.formatNumber(oFolder.numFilesWanted),
						DisplayFormatters.formatNumber(numFiles),
						DisplayFormatters.formatByteCountToKiBEtc(oFolder.sizeWanted),
						DisplayFormatters.formatByteCountToKiBEtc(oFolder.size));

				if ((oFolder.numFilesFilteredWanted != oFolder.numFilesWanted
						|| numFilteredFiles != numFiles
						|| oFolder.sizeWantedFiltered != oFolder.sizeWanted)
						&& (oFolder.numFilesFilteredWanted != oFolder.numFilesWanted
								|| numFilteredFiles != numFiles)) {
					String summaryFiltered = numFilteredFiles == oFolder.numFilesFilteredWanted
							? resources.getString(R.string.folder_summary_filtered_all_wanted,
									DisplayFormatters.formatNumber(
											oFolder.numFilesFilteredWanted),
									DisplayFormatters.formatByteCountToKiBEtc(
											oFolder.sizeWantedFiltered))
							: resources.getString(R.string.folder_summary_filtered,
									DisplayFormatters.formatNumber(
											oFolder.numFilesFilteredWanted),
									DisplayFormatters.formatByteCountToKiBEtc(
											oFolder.sizeWantedFiltered),
									DisplayFormatters.formatNumber(numFilteredFiles));
					s = summaryWanted + "\n" + summaryFiltered;
				} else {
					s = summaryWanted;
				}
			}

			flipper.changeText(holder.tvInfo, s, animateFlip, validator);
		}
		if (holder.btnWant != null) {
			holder.btnWant.setImageResource(numFiles == oFolder.numFilesWanted
					? R.drawable.btn_want : oFolder.numFilesWanted == 0
							? R.drawable.btn_unwant : R.drawable.ic_menu_want);
			holder.btnWant.setOnClickListener(
					v -> setWantState(null, true, null, oFolder));
		}
	}

	public void setExpandState(FilesAdapterItemFolder folder, boolean expand) {
		folder.expand = expand;
		int adapterPosition = getPositionForItem(folder);
		notifyItemChanged(adapterPosition);
		if (expand) {
			getFilter().refilter();
		} else {
			int count = 0;
			FilesAdapterItem nextItem = getItem(adapterPosition + count + 1);
			while (nextItem != null && nextItem.parent != null) {
				boolean isOurs = false;
				while (nextItem.parent != null) {
					if (nextItem.parent == folder) {
						isOurs = true;
						break;
					}
					nextItem = nextItem.parent;
				}
				if (isOurs) {
					count++;
					nextItem = getItem(adapterPosition + count + 1);
				} // not ours, nextItem will be null, will exit while
			}
			if (adapterPosition + count > getItemCount()) {
				count = getItemCount() - adapterPosition;
			}
			removeItems(adapterPosition + 1, count);
		}
	}
	
	public void setWantState(Boolean toWantStat, boolean filtered,
			ReplyMapReceivedListener replyMapReceivedListener,
			FilesAdapterItemFolder folderItem) {
		Session session = sessionGetter.getSession();
		Map<?, ?> torrent = session.torrent.getCachedTorrent(torrentID);
		if (torrent == null) {
			return;
		}
		//noinspection unchecked
		final List<Map<String, Object>> listFiles = MapUtils.getMapList(torrent,
				TransmissionVars.FIELD_TORRENT_FILES, null);
		if (listFiles == null) {
			return;
		}

		int[] fileIndexes = filtered ? folderItem.getFilteredFileIndexes()
				: folderItem.getFileIndexes();
		for (int index : fileIndexes) {
			Map<String, Object> map = listFiles.get(index);
			if (toWantStat == null) {
				toWantStat = !MapUtils.getMapBoolean(map,
						TransmissionVars.FIELD_FILESTATS_WANTED, true);
			}
			map.put(TransmissionVars.FIELD_FILESTATS_WANTED, toWantStat);
		}

		if (fileIndexes.length == 0) {
			// something went terribly wrong!
			if (replyMapReceivedListener != null) {
				// wrong == success, sure!
				replyMapReceivedListener.rpcSuccess("FolderWant", null);
			}
			return;
		}

		notifyDataSetInvalidated();
		session.torrent.setFileWantState("FilteredFolderWant", torrentID,
				fileIndexes, toWantStat, replyMapReceivedListener);
	}

	private void buildView(final FilesAdapterItemFile oFile,
			FilesTreeViewHolder holder) {
		FilesTreeViewHolderFlipValidator validator = new FilesTreeViewHolderFlipValidator(
				holder, torrentID, oFile.fileIndex);
		boolean animateFlip = validator.isStillValid();
		holder.fileIndex = oFile.fileIndex;
		holder.torrentID = torrentID;

		if (holder.tvName != null) {
			flipper.changeText(holder.tvName, AndroidUtils.lineBreaker(oFile.name),
					animateFlip, validator);
		}
		float alpha = 1f;

		if (oFile.length > 0) {
			float pctDone = (float) oFile.bytesComplete / oFile.length;
			if (holder.tvProgress != null) {
				holder.tvProgress.setVisibility(inEditMode ? View.GONE : View.VISIBLE);
				if (!inEditMode) {
					if (oFile.want) {
						holder.tvProgress.setBackgroundResource(0);
						NumberFormat format = NumberFormat.getPercentInstance();
						format.setMaximumFractionDigits(1);
						String s = format.format(pctDone);
						flipper.changeText(holder.tvProgress, s, animateFlip, validator);
					} else {
						SpannableString span = new SpannableString(" ");
						span.setSpan(getTrashImageSpan(holder.tvProgress.getContext()), 0,
								1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
						holder.tvProgress.setText(span);
						alpha = 0.8f;
					}
				}
			}
			if (holder.pb != null) {
				holder.pb.setVisibility(
						inEditMode ? View.GONE : oFile.want ? View.VISIBLE : View.GONE);
				if (oFile.want && !inEditMode) {
					holder.pb.setProgress((int) (pctDone * 10000));
				}
			}
		} else {
			if (holder.tvProgress != null) {
				holder.tvProgress.setVisibility(
						inEditMode ? View.GONE : View.INVISIBLE);
			}
			if (holder.pb != null) {
				holder.pb.setVisibility(inEditMode ? View.GONE : View.INVISIBLE);
			}
		}
		if (holder.tvInfo != null) {
			String s = inEditMode || oFile.bytesComplete == oFile.length
					? DisplayFormatters.formatByteCountToKiBEtc(oFile.length)
					: holder.tvInfo.getResources().getString(R.string.generic_x_of_y,
							DisplayFormatters.formatByteCountToKiBEtc(oFile.bytesComplete),
							DisplayFormatters.formatByteCountToKiBEtc(oFile.length));
			flipper.changeText(holder.tvInfo, s, animateFlip, validator);
		}
		if (holder.tvStatus != null) {
			int id;
			switch (oFile.priority) {
				case TransmissionVars.TR_PRI_HIGH:
					id = R.string.torrent_file_priority_high;
					break;
				case TransmissionVars.TR_PRI_LOW:
					id = R.string.torrent_file_priority_low;
					break;
				default:
					id = R.string.torrent_file_priority_normal;
					break;
			}

			String s = holder.tvStatus.getResources().getString(id);
			flipper.changeText(holder.tvStatus, s, animateFlip, validator);
		}
		if (holder.btnWant != null) {
			holder.btnWant.setImageResource(
					oFile.want ? R.drawable.btn_want : R.drawable.btn_unwant);
			holder.btnWant.setOnClickListener(v -> setWantState(null, null, oFile));
		}

		if (holder.layout.getAlpha() != alpha) {
			// setAlpha doesn't redraw TextView. Invalidate on TextView doesn't work either
			//holder.layout.setAlpha(alpha);

			AlphaAnimation a = new AlphaAnimation(alpha, alpha);
			a.setDuration(0); // Make animation instants
			a.setFillAfter(true); // Tell it to persist after the animation ends
			holder.layout.startAnimation(a);
		}
	}

	private ImageSpan getTrashImageSpan(Context context) {
		if (trashImageSpan == null) {
			Drawable d = context.getResources().getDrawable(
					R.drawable.ic_trash_24dp).mutate();
			d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
			d.setAlpha(0x40);
			int styleColor = AndroidUtilsUI.getStyleColor(context,
					android.R.attr.textColorPrimary);
			DrawableCompat.setTint(d, styleColor);
			trashImageSpan = new ImageSpan(d);
		}
		return trashImageSpan;
	}

	@Thunk
	public void setWantState(Boolean toWantState,
			ReplyMapReceivedListener replyMapReceivedListener,
			FilesAdapterItemFile... fileItems) {

		boolean needRefilter = false;

		int[] fileIndexes = new int[fileItems.length];
		int i = 0;

		for (FilesAdapterItemFile oFile : fileItems) {
			int fileIndex = oFile.fileIndex;
			if (fileIndex < 0) {
				continue;
			}
			Map<String, Object> map = getFileMap(oFile);
			if (map == null) {
				continue;
			}

			fileIndexes[i++] = fileIndex;

			if (toWantState == null) {
				toWantState = !oFile.want;
			}
			//noinspection unchecked
			map.put(TransmissionVars.FIELD_FILESTATS_WANTED, toWantState);
			oFile.want = toWantState;
			notifyItemChanged(getPositionForItem(oFile));

			if (oFile.path == null || oFile.path.length() == 0) {
				FilesTreeFilter filter = getFilter();
				long length = MapUtils.getMapLong(map,
						TransmissionVars.FIELD_FILES_LENGTH, 0);
				if (toWantState) {
					filter.totalFilteredNumFilesWanted++;
					filter.totalNumFilesWanted++;

					filter.totalFilteredSizeWanted += length;
					filter.totalSizeWanted += length;
				} else {
					filter.totalFilteredNumFilesWanted--;
					filter.totalNumFilesWanted--;

					filter.totalFilteredSizeWanted -= length;
					filter.totalSizeWanted -= length;
				}
				if (useTree) {
					needRefilter = true;
				}
			} else {
				needRefilter = true;
			}

		}

		if (needRefilter) {
			getFilter().refilter();
		}

		if (i < fileIndexes.length) {
			int[] old = fileIndexes;
			fileIndexes = new int[i];
			System.arraycopy(old, 0, fileIndexes, 0, i);
		}

		Session session = sessionGetter.getSession();
		session.torrent.setFileWantState("FileWant" + i, torrentID, fileIndexes,
				toWantState, replyMapReceivedListener);
	}

	@SuppressWarnings("rawtypes")
	@Thunk
	static Map<?, ?> getFileMap(FilesAdapterItem o, @NonNull List<?> mapList) {
		if (o instanceof FilesAdapterItemFile) {
			FilesAdapterItemFile file = (FilesAdapterItemFile) o;
			return (Map<?, ?>) mapList.get(file.fileIndex);
		}
		if (o instanceof FilesAdapterItemFolder) {
			return ((FilesAdapterItemFolder) o).map;
		}
		return Collections.EMPTY_MAP;
	}

	@SuppressWarnings("rawtypes")
	private Map<String, Object> getFileMap(
			FilesAdapterItem filesAdapterDisplayObject) {

		Session session = sessionGetter.getSession();
		//noinspection unchecked
		return (Map<String, Object>) filesAdapterDisplayObject.getMap(session,
				torrentID);
	}

	public void setTorrentID(long torrentID, boolean alwaysRefilter) {
		if (torrentID != this.torrentID) {
			this.torrentID = torrentID;
			resetFilter();
			getFilter().refilter();
		} else if (alwaysRefilter) {
			getFilter().refilter();
		}
	}

	@Override
	public long getItemId(int position) {
		FilesAdapterItem filesAdapterDisplayObject = getItem(position);
		if (filesAdapterDisplayObject instanceof FilesAdapterItemFile) {
			FilesAdapterItemFile dof = (FilesAdapterItemFile) filesAdapterDisplayObject;
			return dof.fileIndex;
		}
		return -position;
	}

	@Override
	public int getItemViewType(int position) {
		return (getItem(position) instanceof FilesAdapterItemFolder) ? TYPE_FOLDER
				: TYPE_FILE;
	}

	public boolean isInEditMode() {
		return inEditMode;
	}

	public void setInEditMode(boolean inEditMode) {
		this.inEditMode = inEditMode;
		if (getItemCount() > 0) {
			notifyDataSetInvalidated();
		}
	}

	public long getTotalSizeWanted() {
		FilesTreeFilter filter = getFilter();
		return filter.totalFilteredSizeWanted;
	}

	@Override
	public boolean setItems(List<FilesAdapterItem> values,
			SparseIntArray countsByViewType) {
		return setItems(values, countsByViewType, this);
	}

	@Override
	public LetterFilter<FilesAdapterItem> createFilter() {
		return new FilesTreeFilter(torrentID, this);
	}

	@Override
	public @NonNull FilesTreeFilter getFilter() {
		return (FilesTreeFilter) super.getFilter();
	}

	@Override
	public Session getSession() {
		return sessionGetter.getSession();
	}

	@Override
	protected void triggerOnSetItemsCompleteListeners() {

		super.triggerOnSetItemsCompleteListeners();
		if (requiresPostSetItemsInvalidate) {
			requiresPostSetItemsInvalidate = false;
			notifyDataSetInvalidated();
		}
	}

	public FilesAdapterItemFile[] getFilteredFileItems() {
		FilesTreeFilter filter = getFilter();
		int filteredCount = filter.getFilteredFileCount();
		FilesAdapterItemFile[] array = new FilesAdapterItemFile[filteredCount];

		List<FilesAdapterItem> allItems = getAllItems();
		int i = 0;
		for (FilesAdapterItem item : allItems) {
			if (item instanceof FilesAdapterItemFile) {
				array[i++] = (FilesAdapterItemFile) item;
			}
		}
		return array;
	}

	public int[] getFilteredFileIndexes() {
		FilesTreeFilter filter = getFilter();
		int filteredCount = filter.getFilteredFileCount();
		int[] array = new int[filteredCount];

		if (filteredCount == filter.getUnfilteredFileCount()) {
			for (int i = 0; i < filteredCount; i++) {
				array[i] = i;
			}
		} else {
			List<FilesAdapterItem> allItems = getAllItems();
			int i = 0;
			for (FilesAdapterItem item : allItems) {
				if (item instanceof FilesAdapterItemFile) {
					array[i++] = ((FilesAdapterItemFile) item).fileIndex;
				}
			}
		}
		return array;
	}
}
