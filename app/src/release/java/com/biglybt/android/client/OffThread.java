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

package com.biglybt.android.client;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;

import com.biglybt.util.RunnableUIThread;
import com.biglybt.util.RunnableWorkerThread;

public class OffThread
{
	private static final String TAG = "OffThread";

	private static HandlerThread workerThread = new HandlerThread("worker");

	private static final Handler workerHandler;

	static {
		workerThread.start();
		workerHandler = new Handler(workerThread.getLooper());
	}

	public static Handler getWorkerHandler() {
		return workerHandler;
	}

	@SuppressLint("WrongThread")
	@AnyThread
	public static void runOnUIThread(
			@UiThread @NonNull RunnableUIThread runnable) {
		if (AndroidUtilsUI.isUIThread()) {
			try {
				runnable.run();
			} catch (Throwable t) {
				AnalyticsTracker.getInstance().logError(t);
			}
		} else {
			AndroidUtilsUI.postDelayed(runnable);
		}
	}

	/**
	 * @return
	 * true - New Thread started with Runnable.<br/>
	 * false - Already off UI Thread. Runnable has been executed.
	 */
	@AnyThread
	public static boolean runOffUIThread(
			@WorkerThread @NonNull RunnableWorkerThread workerThreadRunnable) {
		if (AndroidUtilsUI.isUIThread()) {
			StackTraceElement[] st = Thread.currentThread().getStackTrace();
			String name = "runOffUIThread";
			try {
				if (st.length > 2) {
					int end = st.length - 1;
					for (int i = 1; i < end; i++) {
						if (name.equals(st[i].getMethodName())) {
							int callingPos = i + 1;
							StackTraceElement caller = st[callingPos];
							String fileName = caller.getFileName();
							if (fileName.endsWith(".java")) {
								fileName = fileName.substring(0, fileName.length() - 5);
							}
							name = caller.getMethodName() + "@" + fileName + ":"
									+ caller.getLineNumber();
							break;
						}
					}
				}
			} catch (Throwable ignore) {
			}

			new Thread(() -> {
				try {
					workerThreadRunnable.run();
				} catch (Throwable t) {
					AnalyticsTracker.getInstance().logError(t, st);
				}
			}, name).start();
			return true;
		}
		try {
			workerThreadRunnable.run();
		} catch (Throwable t) {
			AnalyticsTracker.getInstance().logError(t);
		}
		return false;
	}

	/**
	 * Same as {@link Activity#runOnUiThread(Runnable)}, except ensures
	 * activity still exists while in UI Thread, before executing runnable
	 */
	@AnyThread
	public static void runOnUIThread(@NonNull final Fragment fragment,
			final boolean allowFinishing,
			final @NonNull RunnableWithActivity runnable) {
		Activity fragActivity = fragment.getActivity();
		if (fragActivity == null
				|| (!allowFinishing && fragActivity.isFinishing())) {
			return;
		}
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		fragActivity.runOnUiThread(() -> {
			Activity activity = fragment.getActivity();
			if (activity == null || (!allowFinishing && activity.isFinishing())) {
				if (AndroidUtils.DEBUG) {
					String stack = AndroidUtils.getCompressedStackTrace(stackTrace, null,
							0, 12);
					Log.w(TAG, "runOnUIThread: skipped runOnUIThread on finish activity "
							+ fragActivity + ", " + stack);
				}
				return;
			}

			long start = AndroidUtils.DEBUG ? SystemClock.uptimeMillis() : 0;
			try {
				runnable.run(activity);

				if (AndroidUtils.DEBUG) {
					long diff = SystemClock.uptimeMillis() - start;
					if (diff <= 500) {
						return;
					}
					String stack = AndroidUtils.getCompressedStackTrace(stackTrace, null,
							0, 12);
					Log.w(TAG, "runOnUIThread: " + diff + "ms for " + stack);
				}
			} catch (Throwable t) {
				AnalyticsTracker.getInstance().logError(t, stackTrace);
			}
		});
	}

	/**
	 * Same as {@link Activity#runOnUiThread(Runnable)}, except ensures
	 * activity still exists while in UI Thread, before executing runnable
	 */
	@AnyThread
	public static <T extends Activity> void runOnUIThread(final T activity,
			final boolean allowFinishing,
			final @NonNull @UiThread RunnableWithActivity<T> runnable) {
		if (activity == null || (!allowFinishing && activity.isFinishing())) {
			return;
		}
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		activity.runOnUiThread(() -> {
			if (!allowFinishing && activity.isFinishing()) {
				if (AndroidUtils.DEBUG) {
					Log.w(TAG, "runOnUIThread: skipped runOnUIThread on finish activity "
							+ activity + ", " + runnable);
				}
				return;
			}

			long start = AndroidUtils.DEBUG ? SystemClock.uptimeMillis() : 0;
			try {
				runnable.run(activity);

				if (AndroidUtils.DEBUG) {
					long diff = SystemClock.uptimeMillis() - start;
					if (diff <= 500) {
						return;
					}
					String stack = AndroidUtils.getCompressedStackTrace(stackTrace, null,
							0, 12);
					Log.w(TAG, "runOnUIThread: " + diff + "ms for " + stack);
				}
			} catch (Throwable t) {
				AnalyticsTracker.getInstance().logError(t, stackTrace);
			}
		});
	}
}
