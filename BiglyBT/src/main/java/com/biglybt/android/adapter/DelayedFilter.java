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

package com.biglybt.android.adapter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Timer;
import java.util.TimerTask;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.util.Thunk;

import android.annotation.SuppressLint;
import android.support.annotation.IntDef;
import android.util.Log;
import android.widget.Filter;

/**
 * Created by TuxPaper on 8/4/18.
 */
public abstract class DelayedFilter
	extends Filter
{
	private static final String TAG = "DelayedFilter";

	public static final int FILTERSTATE_IDLE = 0;

	public static final int FILTERSTATE_FILTERING = 1;

	public static final int FILTERSTATE_SORTING = 2;

	public static final int FILTERSTATE_PUBLISHING = 3;

	public static final String[] FILTERSTATE_DEBUGSTRINGS = {
		"Idle",
		"Filtering",
		"Sorting",
		"Publishing"
	};

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({
		FILTERSTATE_IDLE,
		FILTERSTATE_FILTERING,
		FILTERSTATE_SORTING,
		FILTERSTATE_PUBLISHING
	})
	public @interface FilterState {
	}

	private final PerformingFilteringListener performingFilteringListener;

	@Thunk
	protected CharSequence constraint;

	@Thunk
	boolean refilteringSoon;

	private @FilterState int filterState;

	private long debugLastFilterStateSetOn;

	private String classSimpleName;

	private boolean isDestroyed;

	public CharSequence getConstraint() {
		return constraint;
	}

	public DelayedFilter(PerformingFilteringListener l) {
		performingFilteringListener = l;
	}

	public void destroy() {
		isDestroyed = true;
	}

	/**
	 * Runs filter with a delay.  
	 * If {@link Filter#setDelayer(Delayer)} wasn't hidden,
	 * we could just use that.
	 */
	@SuppressWarnings("JavadocReference")
	public void refilter() {
		refilter(200);
	}

	public void refilter(int delay) {
		synchronized (TAG) {
			if (refilteringSoon) {
				if (AndroidUtils.DEBUG_ADAPTER) {
					log(TAG, "refilter: skip refilter, refiltering soon. "
							+ AndroidUtils.getCompressedStackTrace(4));
				}
				return;
			}
			refilteringSoon = true;
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			log(TAG, "refilter() via " + AndroidUtils.getCompressedStackTrace());
		}
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				synchronized (TAG) {
					refilteringSoon = false;
				}
				if (getFilterState() != FILTERSTATE_IDLE) {
					if (AndroidUtils.DEBUG_ADAPTER) {
						log(TAG, "refilter() skipped because filterstate was "
								+ FILTERSTATE_DEBUGSTRINGS[getFilterState()]);
					}
					// Could schedule a new one
					return;
				}
				if (!isDestroyed) {
					filter(constraint);
				}
			}
		}, delay);
	}

	@Override
	protected final FilterResults performFiltering(CharSequence constraint) {
		synchronized (TAG) {
			if (AndroidUtils.DEBUG_ADAPTER && (filterState == FILTERSTATE_FILTERING
					|| filterState == FILTERSTATE_SORTING)) {
				log(Log.ERROR, TAG, "performFiltering: ALREADY PERFORMING FILTERING "
						+ DelayedFilter.FILTERSTATE_DEBUGSTRINGS[filterState]);
			}

			setFilterState(FILTERSTATE_FILTERING);
		}
		try {
			return performFiltering2(constraint);
		} catch (Throwable t) {
			synchronized (TAG) {
				setFilterState(FILTERSTATE_IDLE);
			}
			throw t;
		}
	}

	/**
	 * <p>Invoked in a worker thread to filter the data according to the
	 * constraint. Subclasses must implement this method to perform the
	 * filtering operation. Results computed by the filtering operation
	 * must be returned as a {@link FilterResults} that
	 * will then be published in the UI thread through
	 * {@link #publishResults2(CharSequence,
	 * FilterResults)}.</p>
	 *
	 * <p><strong>Contract:</strong> When the constraint is null, the original
	 * data must be restored.</p>
	 *
	 * @param constraint the constraint used to filter the data
	 * @return the results of the filtering operation
	 *
	 * @see #filter(CharSequence, FilterListener)
	 * @see #publishResults2(CharSequence, FilterResults)
	 * @see FilterResults
	 */
	protected abstract FilterResults performFiltering2(CharSequence constraint);

	@Override
	protected final void publishResults(CharSequence constraint,
			FilterResults results) {
		if (results == null || results.values == null) {
			if (AndroidUtils.DEBUG_ADAPTER) {
				log(TAG, "publishResults: no result values.  Skipping publish.");
			}
			synchronized (TAG) {
				setFilterState(FILTERSTATE_IDLE);
			}
			return;
		}
		synchronized (TAG) {
			if (filterState != FILTERSTATE_FILTERING
					&& filterState != FILTERSTATE_SORTING) {
				if (AndroidUtils.DEBUG_ADAPTER) {
					log(Log.ERROR, TAG,
							"publishResults: skipped; not marked as performing filter. "
									+ filterState);
				}
				return;
			}
			synchronized (TAG) {
				setFilterState(FILTERSTATE_PUBLISHING);
			}
		}
		try {
			boolean complete = publishResults2(constraint, results);
			if (complete) {
				synchronized (TAG) {
					setFilterState(FILTERSTATE_IDLE);
				}
			}
		} catch (Throwable t) {
			log(TAG, "publishResults2", t);
			synchronized (TAG) {
				setFilterState(FILTERSTATE_IDLE);
			}
		}
	}

	/**
	 * <p>Invoked in the UI thread to publish the filtering results in the
	 * user interface. Subclasses must implement this method to display the
	 * results computed in {@link #performFiltering2}.</p>
	 * 
	 * <p>Protected against multiple simultaneous calls.  While 
	 * {@link #filter(CharSequence)} cancels previous calls, it doesn't
	 * cancel currently running calls (afaict).</p>
	 *
	 * @param constraint the constraint used to filter the data
	 * @param results the results of the filtering operation
	 *                
	 * @return If publish is complete, and state can go to idle.  When false,
	 * you will need to setFilterState(FILTERSTATE_IDLE) once publishing is complete. 
	 *
	 * @see #filter(CharSequence, FilterListener)
	 * @see #performFiltering2(CharSequence)
	 * @see FilterResults
	 */
	protected abstract boolean publishResults2(CharSequence constraint,
			FilterResults results);

	public void setConstraint(CharSequence s) {
		constraint = s;
	}

	@SuppressLint("LogConditional")
	@Thunk
	public void log(String TAG, String s) {
		if (classSimpleName == null) {
			classSimpleName = AndroidUtils.getSimpleName(getClass()) + "@"
					+ Integer.toHexString(hashCode());
		}
		Log.d(classSimpleName, TAG + ": " + s);
	}

	@SuppressLint("LogConditional")
	public void log(int prority, String TAG, String s) {
		if (classSimpleName == null) {
			classSimpleName = AndroidUtils.getSimpleName(getClass()) + "@"
					+ Integer.toHexString(hashCode());
		}
		Log.println(prority, classSimpleName, TAG + ": " + s);
	}

	@SuppressLint("LogConditional")
	public void log(String TAG, String s, Throwable t) {
		if (classSimpleName == null) {
			classSimpleName = AndroidUtils.getSimpleName(getClass()) + "@"
					+ Integer.toHexString(hashCode());
		}
		Log.e(classSimpleName, TAG + ": " + s, t);
	}

	@SuppressWarnings("WeakerAccess")
	public @DelayedFilter.FilterState int getFilterState() {
		return filterState;
	}

	void setFilterState(@FilterState int filterState) {
		if (filterState == this.filterState) {
			if (filterState != FILTERSTATE_IDLE) {
				log(Log.ERROR, TAG,
						"setFilterState to same state "
								+ DelayedFilter.FILTERSTATE_DEBUGSTRINGS[filterState] + " via "
								+ AndroidUtils.getCompressedStackTrace());
			}
			return;
		}
		if (AndroidUtils.DEBUG_ADAPTER) {
			String s = "setFilterState "
					+ DelayedFilter.FILTERSTATE_DEBUGSTRINGS[filterState];
			if (debugLastFilterStateSetOn > 0) {
				s += ". Previous "
						+ DelayedFilter.FILTERSTATE_DEBUGSTRINGS[this.filterState]
						+ " took "
						+ (System.currentTimeMillis() - debugLastFilterStateSetOn) + "ms";
			}
			log(TAG, s + ". " + AndroidUtils.getCompressedStackTrace());
		}
		@FilterState
		int oldState = this.filterState;
		this.filterState = filterState;
		if (AndroidUtils.DEBUG_ADAPTER) {
			debugLastFilterStateSetOn = System.currentTimeMillis();
		}
		if (performingFilteringListener != null) {
			performingFilteringListener.performingFilteringChanged(filterState,
					oldState);
		}
	}

	public interface PerformingFilteringListener
	{
		void performingFilteringChanged(@FilterState int filterState,
				@FilterState int oldState);
	}

}
