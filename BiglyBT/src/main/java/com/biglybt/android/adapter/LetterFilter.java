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

package com.biglybt.android.adapter;

import java.util.*;

import com.biglybt.android.client.AndroidUtils;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

/**
 * Created by TuxPaper on 6/30/16.
 */
public abstract class LetterFilter<T>
	extends FilterWithMapSorter<T>
{
	private static final boolean DEBUG = AndroidUtils.DEBUG_ADAPTER;

	private static final String TAG = "LetterFilter";

	private SparseArray<SortDefinition> sortDefinitions;

	private boolean compactDigits = true;

	private boolean compactNonLetters = true;

	private boolean buildLetters = false;

	private boolean compactPunctuation = true;

	private LettersUpdatedListener lettersUpdatedListener;

	public LetterFilter(PerformingFilteringListener l) {
		super(l);
	}

	public void setBuildLetters(boolean buildLetters) {
		this.buildLetters = buildLetters;
	}

	public boolean isBuildLetters() {
		return buildLetters;
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

	protected boolean constraintCheck(@NonNull String constraint,
			String stringToConstrain, @Nullable HashSet<String> setLetters,
			HashMap<String, Integer> mapLetterCount) {
		if (stringToConstrain == null) {
			return false;
		}

		boolean hasConstraint = !constraint.isEmpty();
		boolean buildLetters = setLetters != null;

		int nameLength = stringToConstrain.length();
		boolean matches;
		if (!hasConstraint || nameLength == 0) {
			if (buildLetters) {
				stringToConstrain = stringToConstrain.toUpperCase(Locale.US);
			}
			matches = true;
		} else {
			stringToConstrain = stringToConstrain.toUpperCase(Locale.US);
			matches = stringToConstrain.contains(constraint);
		}
		if (buildLetters && matches) {
			if (hasConstraint) {
				int pos = stringToConstrain.indexOf(constraint);
				while (pos >= 0) {
					int end = pos + constraint.length();
					if (end < nameLength) {
						char c = stringToConstrain.charAt(end);
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
					pos = stringToConstrain.indexOf(constraint, pos + 1);
				}
			} else {
				for (int i = 0; i < nameLength; i++) {
					char c = stringToConstrain.charAt(i);
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

		return matches;
	}

	/**
	 * The returned String will be used when filtering by letters
	 */
	protected abstract String getStringToConstrain(T key);

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
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

	/**
	 * @return List of items removed
	 */
	protected HashSet<T> performLetterFiltering(CharSequence _constraint,
			List<T> searchResultList) {

		String constraint = _constraint == null ? ""
				: _constraint.toString().toUpperCase(Locale.US);

		boolean hasConstraint = !constraint.isEmpty();

		int size = searchResultList.size();

		if (DEBUG) {
			log(TAG,
					"performFiltering: size=" + size + (hasConstraint ? "; has" : "; no")
							+ " Constraint; buildLetters? " + buildLetters);
		}

		// HashSet is faster than ArrayList for .contains(), which removeAll uses
		HashSet<T> toRemove = new HashSet<>();
		if (size <= 0 || (!buildLetters && !hasConstraint)) {
			return toRemove;
		}

		if (DEBUG && hasConstraint) {
			Log.d(TAG, "filtering " + searchResultList.size());
		}

		HashSet<String> setLetters = null;
		HashMap<String, Integer> mapLetterCount = null;
		if (buildLetters) {
			setLetters = new HashSet<>();
			mapLetterCount = new HashMap<>();
		}
		if (buildLetters || hasConstraint) {
			for (T key : searchResultList) {
				if (!constraintCheck(constraint, getStringToConstrain(key), setLetters,
						mapLetterCount)) {
					toRemove.add(key);
				}
			}
		}
		if (DEBUG && hasConstraint) {
			Log.d(TAG, "filter removing " + toRemove.size());
		}
		if (toRemove.size() > 0) {
			searchResultList.removeAll(toRemove);
		}

		if (buildLetters && lettersUpdatedListener != null) {
			lettersUpdatedListener.lettersUpdated(mapLetterCount);
		}

		if (DEBUG && hasConstraint) {
			Log.d(TAG, "text filtered to " + (size - toRemove.size()));
		}
		return toRemove;
	}

	abstract public @NonNull String getSectionName(int position);

	abstract public boolean showLetterUI();

	public void setLettersUpdatedListener(LettersUpdatedListener l) {
		this.lettersUpdatedListener = l;
	}

	public LettersUpdatedListener getLettersUpdatedListener() {
		return lettersUpdatedListener;
	}

	public void saveToBundle(Bundle outState) {
	}

	public void restoreFromBundle(Bundle savedInstanceState) {
	}

	@NonNull
	public abstract SparseArray<SortDefinition> createSortDefinitions();

	@NonNull
	public SparseArray<SortDefinition> getSortDefinitions() {
		if (sortDefinitions == null) {
			sortDefinitions = createSortDefinitions();
		}
		return sortDefinitions;
	}
}
