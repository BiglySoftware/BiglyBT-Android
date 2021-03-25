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

package com.biglybt.android.client.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.R;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.client.spanbubbles.SpanBubbleListener;
import com.biglybt.android.client.spanbubbles.SpanBubbles;

import java.util.List;

/**
 * This is the dialog box that asks the user for a search term for MetaSearch
 */
public class DialogFragmentMetaSearch
	extends DialogFragmentBase
{

	private static final String TAG = "MetaSearchDialog";

	private static final String BUNDLEKEY_DEFAULT_TERM = "DefaultTerm";

	private static class CachedTermsListener
		implements SpanBubbleListener
	{
		private final AlertDialog[] alertDialog;

		private final Context context;

		private final Session session;

		CachedTermsListener(AlertDialog[] alertDialog, Context context,
				Session session) {
			this.alertDialog = alertDialog;
			this.context = context;
			this.session = session;
		}

		@Override
		public void spanBubbleClicked(int index, String word) {
			if (alertDialog[0] != null) {
				alertDialog[0].cancel();
			}
			AndroidUtils.executeSearch(word, context, session);
		}

		@Override
		public int[] getColors(int index, String word, boolean isPressed) {
			return null;
		}
	}

	public static void openOpenTorrentDialog(FragmentManager fm, String profileID,
			String defaultTerm) {
		DialogFragmentMetaSearch dlg = new DialogFragmentMetaSearch();
		Bundle bundle = new Bundle();
		bundle.putString(SessionManager.BUNDLE_KEY, profileID);
		bundle.putString(BUNDLEKEY_DEFAULT_TERM, defaultTerm);
		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fm, TAG);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		Session session = SessionManager.findOrCreateSession(this, null);
		if (session == null) {
			return super.onCreateDialog(savedInstanceState);
		}

		Context context = requireContext();
		AlertDialogBuilder adb = AndroidUtilsUI.createAlertDialogBuilder(context,
				R.layout.dialog_metasearch);

		AlertDialog[] alertDialog = {
			null
		};
		View cachedArea = adb.view.findViewById(R.id.searchbox_cached_group);

		List<String> cachedTerms = session.metasearch.getCachedSearchTerms();
		if (cachedTerms.isEmpty()) {
			cachedArea.setVisibility(View.GONE);
		} else {
			cachedArea.setVisibility(View.VISIBLE);

			TextView tvTerms = adb.view.findViewById(R.id.searchbox_cached_textview);
			tvTerms.setMovementMethod(LinkMovementMethod.getInstance());

			int colorBGTagState = AndroidUtilsUI.getStyleColor(context,
					R.attr.bg_tag_type_2);
			int colorFGTagState = AndroidUtilsUI.getStyleColor(context,
					R.attr.fg_tag_type_2);

			StringBuilder text = new StringBuilder();

			for (String cachedTerm : cachedTerms) {
				// always append space, even to the end, otherwise last clickable area
				// will be too short (bug, probably in my code)
				text.append('|').append(cachedTerm).append("| ");
			}

			SpannableStringBuilder ss = new SpannableStringBuilder(text);
			String string = text.toString();
			SpanBubbles.setSpanBubbles(ss, string, "|", tvTerms.getPaint(),
					colorBGTagState, colorFGTagState, colorBGTagState,
					new CachedTermsListener(alertDialog, context, session));
			tvTerms.setText(ss);
		}

		Bundle args = getArguments();
		String presetText = args == null ? null
				: args.getString(BUNDLEKEY_DEFAULT_TERM);

		alertDialog[0] = AndroidUtilsUI.createTextBoxDialog(context, adb,
				getString(R.string.search), getString(R.string.search_box_hint), null,
				presetText, EditorInfo.IME_ACTION_SEARCH, InputType.TYPE_CLASS_TEXT,
				(dialog, which, editText) -> {
					final String newName = editText.getText().toString();
					if (newName.isEmpty()) {
						return;
					}
					Session currentSession = SessionManager.findOrCreateSession(
							DialogFragmentMetaSearch.this, null);
					if (currentSession == null) {
						return;
					}
					currentSession.metasearch.removeCachedTerm(newName);
					AndroidUtils.executeSearch(newName, context, currentSession);
				});

		return alertDialog[0];
	}

}
