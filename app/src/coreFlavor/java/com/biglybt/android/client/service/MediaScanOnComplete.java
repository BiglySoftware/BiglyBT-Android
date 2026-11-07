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

package com.biglybt.android.client.service;

import android.content.Context;
import android.media.MediaScannerConnection;

import androidx.annotation.NonNull;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks Android to index the files of a finished download, so media apps
 * that rely on MediaStore (e.g. the TV's own media player) list them.
 * <p/>
 * Files written by the core are not indexed by themselves, and a
 * move-on-completion leaves MediaStore pointing at the old location.
 */
class MediaScanOnComplete
	implements DownloadManagerListener
{
	@NonNull
	private final Context context;

	MediaScanOnComplete(@NonNull Context context) {
		this.context = context.getApplicationContext();
	}

	@Override
	public void downloadComplete(DownloadManager manager) {
		scan(manager);
	}

	@Override
	public void fileLocationChanged(DownloadManager download,
			DiskManagerFileInfo file) {
		// null file: whole download moved, e.g. move on completion
		if (file == null && download.isDownloadComplete(false)) {
			scan(download);
		}
	}

	private void scan(@NonNull DownloadManager dm) {
		List<String> paths = new ArrayList<>();
		for (DiskManagerFileInfo info : dm.getDiskManagerFileInfoSet().getFiles()) {
			if (info.isSkipped()) {
				continue;
			}
			File f = info.getFile(true);
			if (f != null && f.isFile()) {
				paths.add(f.getAbsolutePath());
			}
		}
		if (!paths.isEmpty()) {
			MediaScannerConnection.scanFile(context, paths.toArray(new String[0]),
					null, null);
		}
	}

	@Override
	public void stateChanged(DownloadManager manager, int state) {
	}

	@Override
	public void completionChanged(DownloadManager manager, boolean bCompleted) {
	}

	@Override
	public void positionChanged(DownloadManager download, int oldPosition,
			int newPosition) {
	}

	@Override
	public void filePriorityChanged(DownloadManager download,
			DiskManagerFileInfo file) {
	}
}
