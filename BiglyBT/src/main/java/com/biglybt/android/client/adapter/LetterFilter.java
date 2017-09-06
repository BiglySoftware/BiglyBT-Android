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

import java.util.*;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.FilterConstants;

import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Filter;

/**
 * Created by TuxPaper on 6/30/16.
 */
public abstract class LetterFilter<T>
	extends Filter
{
	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private static final String TAG = "LetterFilter";

	private String constraint;

	private boolean compactDigits = true;

	private boolean compactNonLetters = true;

	private boolean buildLetters = false;

	private boolean compactPunctuation = true;

	private boolean refilteringSoon;

	public void setBuildLetters(boolean buildLetters) {
		this.buildLetters = buildLetters;
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

	public String getConstraint() {
		return constraint;
	}

	private boolean constraintCheck(CharSequence constraint, T key,
			@Nullable HashSet<String> setLetters, String charAfter,
			boolean compactDigits, boolean compactNonLetters,
			boolean compactPunctuation) {
		if (setLetters == null
				&& (constraint == null || constraint.length() == 0)) {
			return true;
		}
		String name = getStringToConstrain(key);
		if (name == null) {
			return false;
		}

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
							setLetters.add(FilterConstants.LETTERS_NUMBERS);
						} else if (compactPunctuation && isStandardPuncuation(c)) {
							setLetters.add(FilterConstants.LETTERS_PUNCTUATION);
						} else if (compactNonLetters && !isDigit && !isAlphabetic(c)
								&& !isStandardPuncuation(c)) {
							setLetters.add(FilterConstants.LETTERS_NON);
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
						setLetters.add(FilterConstants.LETTERS_NUMBERS);
					} else if (compactPunctuation && isStandardPuncuation(c)) {
						setLetters.add(FilterConstants.LETTERS_PUNCTUATION);
					} else if (compactNonLetters && !isDigit && !isAlphabetic(c)
							&& !isStandardPuncuation(c)) {
						setLetters.add(FilterConstants.LETTERS_NON);
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

	protected abstract String getStringToConstrain(T key);

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

	public void refilter() {
		synchronized (TAG) {
			if (refilteringSoon) {
				if (AndroidUtils.DEBUG_ADAPTER) {
					Log.d(TAG, "refilter: skip refilter, refiltering soon");
				}
				return;
			}
			refilteringSoon = true;
		}
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				synchronized (TAG) {
					refilteringSoon = false;
				}
				filter(constraint);
			}
		}, 200);
	}

	protected void performLetterFiltering(CharSequence _constraint,
			List<T> searchResultList) {

		this.constraint = _constraint == null ? null
				: _constraint.toString().toUpperCase(Locale.US);

		boolean hasConstraint = constraint != null && constraint.length() > 0;

		int size = searchResultList.size();

		if (DEBUG) {
			Log.d(TAG,
					"performFiltering: size=" + size + (hasConstraint ? "; has" : "; no")
							+ " Constraint; buildLetters? " + buildLetters);
		}

		if (size > 0 && (buildLetters || hasConstraint)) {
			if (DEBUG && hasConstraint) {
				Log.d(TAG, "filtering " + searchResultList.size());
			}

			if (constraint == null) {
				constraint = "";
			}
			HashSet<String> setLetters = null;
			HashMap<String, Integer> mapLetterCount = null;
			if (buildLetters) {
				setLetters = new HashSet<>();
				mapLetterCount = new HashMap<>();
			}
			for (int i = size - 1; i >= 0; i--) {
				T key = searchResultList.get(i);

				if (!constraintCheck(constraint, key, setLetters, constraint,
						compactDigits, compactNonLetters, compactPunctuation)) {
					searchResultList.remove(i);
					size--;
				}

				//noinspection ConstantConditions
				if (buildLetters && setLetters.size() > 0) {
					for (String letter : setLetters) {
						@SuppressWarnings("ConstantConditions")
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

			if (buildLetters) {
				lettersUpdated(mapLetterCount);
			}

			if (DEBUG && hasConstraint) {
				Log.d(TAG, "text filtered to " + size);
			}
		}
	}

	protected abstract void lettersUpdated(
			@Nullable HashMap<String, Integer> mapLetterCount);

}
