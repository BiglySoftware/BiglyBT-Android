package com.biglybt.android.client.adapter;

import androidx.annotation.NonNull;

import com.biglybt.android.util.TextViewFlipper;

public class FilesTreeViewHolderFlipValidator
	implements TextViewFlipper.FlipValidator
{
	@NonNull
	private final FilesTreeViewHolder holder;

	private int fileIndex;

	private final long torrentID;

	FilesTreeViewHolderFlipValidator(@NonNull FilesTreeViewHolder holder,
			long torrentID, int fileIndex) {
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
