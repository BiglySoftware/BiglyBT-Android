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

package com.biglybt.android.core.az;

import android.content.ContentResolver;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.core.diskmanager.file.impl.FMFileAccess.FileAccessor;
import com.biglybt.core.diskmanager.file.impl.FMFileAccessController.FileAccessorRAF;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;

public class AndroidFileAccessor
	implements FileAccessor
{

	private static final String TAG = "AndroidFileAccessor";

	@NonNull
	private final File file;

	private FileAccessorRAF fallback;

	private AndroidFileChannel fc;

	private ParcelFileDescriptor pfd;

	public AndroidFileAccessor(@NonNull File file, @NonNull String mode)
			throws FileNotFoundException {
		this.file = file;
		if (!openAndroidFile(file, mode)) {
			fallback = new FileAccessorRAF(file, mode);
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "AndroidFileAccessor: Using fallback "
						+ AndroidUtils.getCompressedStackTrace());
			}
		}
	}

	private boolean openAndroidFile(File file, @NonNull String mode)
			throws FileNotFoundException {
		if (!(file instanceof AndroidFile)) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "AndroidFileAccessor: Not AndroidFile " + file);
			}
			return false;
		}
		AndroidFile androidFile = (AndroidFile) file;
		ContentResolver contentResolver = BiglyBTApp.getContext().getContentResolver();
		if (contentResolver == null) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "Could not get ContentResolver");
			}
			return false;
		}

		// IllegalArgumentException – if the mode argument is not equal to one of "r", "rw", "rws", or "rwd"
		// java.io.FileNotFoundException – 
		//   if the mode is "r" but the given file object does not denote an existing regular file, 
		//   or if the mode begins with "rw" but the given file object does not denote an existing,
		//         writable regular file and a new regular file of that name cannot be created, 
		//   or if some other error occurs while opening or creating the file
		if (mode.contains("w") && !file.exists()) {
			try {
				if (!file.createNewFile()) {
					throw new FileNotFoundException("Unable to create file " + file);
				}
			} catch (IOException e) {
				throw (FileNotFoundException) new FileNotFoundException(
						"Error creating file").initCause(e);
			}
		}

		try {
			pfd = contentResolver.openFileDescriptor(androidFile.getUri(), mode);
			FileDescriptor fileDescriptor = pfd.getFileDescriptor();
			fc = new AndroidFileChannel(fileDescriptor, mode);
			return true;
		} catch (FileNotFoundException e) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "AndroidFileAccessor", e);
			}
			throw e;
		}
	}

	@Override
	public FileChannel getChannel() {
		if (fallback != null) {
			return fallback.getChannel();
		}
		return fc;
	}

	@Override
	public long getLength()
			throws IOException {
		if (fallback != null) {
			return fallback.getLength();
		}
		return file.length();
	}

	@Override
	public void setLength(long len)
			throws IOException {
		if (fallback != null) {
			fallback.setLength(len);
			return;
		}
		if (fc == null) {
			return;
		}
		Log.e(TAG, "setLength: len " + len + " on " + this);
		fc.truncate(len);
	}

	@Override
	public long getPosition()
			throws IOException {
		if (fallback != null) {
			return fallback.getPosition();
		}
		return fc == null ? -1 : fc.position();
	}

	@Override
	public void setPosition(long pos)
			throws IOException {
		if (fallback != null) {
			fallback.setPosition(pos);
			return;
		}
		if (fc == null) {
			return;
		}
		fc.position(pos);
	}

	@Override
	public void write(int b)
			throws IOException {
		if (fallback != null) {
			fallback.write(b);
			return;
		}
		if (fc == null) {
			return;
		}

		ByteBuffer byteBuffer = ByteBuffer.allocate(4);
		byteBuffer.putInt(b);
		fc.write(byteBuffer);
	}

	@Override
	public void close()
			throws IOException {
		if (fallback != null) {
			fallback.close();
			return;
		}

		if (fc != null) {
			fc.close();
		}
		if (pfd != null) {
			pfd.close();
		}
	}

	public static class AndroidFileChannel
		extends FileChannel
	{

		private final FileChannel in;

		private final FileChannel out;

		@NonNull
		private final FileDescriptor fileDescriptor;

		private long position;

		AndroidFileChannel(@NonNull FileDescriptor fd, @NonNull String mode) {
			fileDescriptor = fd;
			in = mode.contains("r") ? new FileInputStream(fd).getChannel() : null;
			out = mode.contains("w") ? new FileOutputStream(fd).getChannel() : null;
		}

		@Override
		public int read(ByteBuffer dst)
				throws IOException {
			if (in == null) {
				throw new IOException("Not in Read mode");
			}
			in.position(position);
			int read = in.read(dst);
			position = in.position();
			return read;
		}

		@Override
		public long read(ByteBuffer[] dsts, int offset, int length)
				throws IOException {
			if (in == null) {
				throw new IOException("Not in Read mode");
			}
			in.position(position);
			long read = in.read(dsts, offset, length);
			position = in.position();
			return read;
		}

		@Override
		public int write(ByteBuffer src)
				throws IOException {
			if (out == null) {
				throw new IOException("Not in Write mode");
			}
			out.position(position);
			int write = out.write(src);
			position = out.position();
			return write;
		}

		@Override
		public long write(ByteBuffer[] srcs, int offset, int length)
				throws IOException {
			if (out == null) {
				throw new IOException("Not in Write mode");
			}
			out.position(position);
			long write = out.write(srcs, offset, length);
			position = out.position();
			return write;
		}

		@Override
		public long position()
				throws IOException {
			return position;
		}

		@Override
		public FileChannel position(long newPosition)
				throws IOException {
			position = newPosition;
			return this;
		}

		@Override
		public long size()
				throws IOException {
			return in != null ? in.size() : out != null ? out.size() : -1;
		}

		@Override
		public FileChannel truncate(long size)
				throws IOException {
			if (out == null) {
				throw new IOException("Not in Write mode");
			}
			FileChannel truncate = out.truncate(size);
			position = out.position();
			return truncate;
		}

		@Override
		public void force(boolean metaData)
				throws IOException {
			if (out == null) {
				throw new IOException("Not in Write mode");
			}
			out.force(metaData);
			fileDescriptor.sync();
		}

		@Override
		public long transferTo(long position, long count,
				WritableByteChannel target)
				throws IOException {
			if (in == null) {
				throw new IOException("Not in Read mode");
			}
			return in.transferTo(position, count, target);
		}

		@Override
		public long transferFrom(ReadableByteChannel src, long position, long count)
				throws IOException {
			if (out == null) {
				throw new IOException("Not in Write mode");
			}
			return out.transferFrom(src, position, count);
		}

		@Override
		public int read(ByteBuffer dst, long position)
				throws IOException {
			if (in == null) {
				throw new IOException("Not in Read mode");
			}
			in.position(this.position);
			int read = in.read(dst, position);
			this.position = in.position();
			return read;
		}

		@Override
		public int write(ByteBuffer src, long position)
				throws IOException {
			if (out == null) {
				throw new IOException("Not in Write mode");
			}
			out.position(this.position);
			int write = out.write(src, position);
			this.position = out.position();
			return write;
		}

		@Override
		public MappedByteBuffer map(MapMode mode, long position, long size)
				throws IOException {
			return mode == MapMode.READ_ONLY ? in.map(mode, position, size)
					: out.map(mode, position, size);
		}

		@Override
		public FileLock lock(long position, long size, boolean shared)
				throws IOException {
			// Should we be locking both in and out?
			return in != null ? in.lock(position, size, shared)
					: out.lock(position, size, shared);
		}

		@Override
		public FileLock tryLock(long position, long size, boolean shared)
				throws IOException {
			return in != null ? in.tryLock(position, size, shared)
					: out.tryLock(position, size, shared);
		}

		@Override
		protected void implCloseChannel()
				throws IOException {
			if (in != null) {
				in.close();
			}

			if (out != null) {
				out.close();
			}
		}
	}
}
