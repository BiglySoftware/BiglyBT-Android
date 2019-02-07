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

package com.biglybt.android.client.dialog;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.AndroidUtilsUI.AlertDialogBuilder;
import com.biglybt.android.client.R;
import com.github.sumimakito.awesomeqr.AwesomeQRCode;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class DialogFragmentNoBrowser
	extends DialogFragmentBase
{

	private static final String TAG = "DialogFragmentNoBrowser";

	private static final String KEY_URL = "url";

	private static final String KEY_NAME = "name";

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		Bundle args = getArguments();
		assert args != null;
		final String url = args.getString(KEY_URL);
		final String name = args.getString(KEY_NAME);

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_nobrowser);

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		builder.setTitle(R.string.dialog_nobrowser_title);

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok, (dialog, id) -> {
		});
		final ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(
				Context.CLIPBOARD_SERVICE);
		if (clipboard != null) {
			builder.setNeutralButton(R.string.button_clipboard_url, (dialog, id) -> {
				ClipData clip = ClipData.newPlainText(name, url);
				clipboard.setPrimaryClip(clip);
			});
		}

		AlertDialog dialog = builder.create();

		TextView tvMessage = view.findViewById(R.id.dialog_nobrowser_message);
		if (tvMessage != null) {
			tvMessage.setText(AndroidUtils.fromHTML(getResources(),
					R.string.dialog_nobrowser_message, name));
		}
		TextView tvURL = view.findViewById(R.id.dialog_nobrowser_url);
		if (tvURL != null) {
			tvURL.setText(url);
		}

		final ImageView iv = view.findViewById(R.id.dialog_nobrowser_qr);
		if (iv != null && AndroidUtils.isTV(getContext())) {
			new AwesomeQRCode.Renderer().contents(url).dotScale(1).size(
					AndroidUtilsUI.dpToPx(400)).margin(
							AndroidUtilsUI.dpToPx(0)).renderAsync(
									new AwesomeQRCode.Callback() {
										@Override
										public void onRendered(AwesomeQRCode.Renderer renderer,
												final Bitmap bitmap) {
											requireActivity().runOnUiThread(() -> {
												iv.setImageBitmap(bitmap);
												iv.setVisibility(View.VISIBLE);
											});
										}

										@Override
										public void onError(AwesomeQRCode.Renderer renderer,
												Exception e) {
											e.printStackTrace();
										}
									});
		}

		return dialog;

	}

	public static void open(FragmentManager fragmentManager, String url,
			String name) {
		DialogFragmentNoBrowser dlg = new DialogFragmentNoBrowser();
		Bundle bundle = new Bundle();
		bundle.putString(KEY_NAME, name);
		bundle.putString(KEY_URL, url);

		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fragmentManager, TAG);
	}

}
