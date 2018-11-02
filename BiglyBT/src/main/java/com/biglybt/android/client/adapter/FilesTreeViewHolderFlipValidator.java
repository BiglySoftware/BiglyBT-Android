package com.biglybt.android.client.adapter;

import com.biglybt.android.util.TextViewFlipper;

public class FilesTreeViewHolderFlipValidator
	implements TextViewFlipper.FlipValidator
{
	private final FilesTreeViewHolder holder;

	private int fileIndex;

	private final long torrentID;

	FilesTreeViewHolderFlipValidator(FilesTreeViewHolder holder, long torrentID,
			int fileIndex) {
		this.holder = holder;
		this.torrentID = torrentID;
		this.fileIndex = fileIndex;
	}

	@Override
	public boolean isStillValid() {
		return fileIndex >= 0 && holder.fileIndex == fileIndex
				&& holder.torrentID == torrentID;
	}
}
