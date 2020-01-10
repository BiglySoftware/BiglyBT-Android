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
import com.google.android.material.button.MaterialButtonToggleGroup;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Spanned;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

public class DialogFragmentRemoteAccessQR
	extends DialogFragmentBase
{

	private static final String TAG = "RemAccessQR";

	private static final String KEY_QR_URL_BIGLYBT = "qrURLb";

	private static final String KEY_QR_URL_WEBUI = "qrURLw";

	int viewMode = R.id.remote_access_qr_toggle_biglybt;

	View view;

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.dialog_remote_access_qr);

		view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		builder.setTitle(R.string.dialog_title_remote_access_qr);

		// Add action buttons
		builder.setPositiveButton(android.R.string.ok, (dialog, id) -> {
		});

		AlertDialog dialog = builder.create();

		MaterialButtonToggleGroup toggleGroup = view.findViewById(
				R.id.remote_access_qr_toggle);
		if (toggleGroup != null) {
			viewMode = toggleGroup.getCheckedButtonId();
			toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
				if (checkedId == viewMode && !isChecked) {
					// Note app:selectionRequired not available until 1.2.0. 
					// This is the workaround assumes event with isChecked is fired
					// before the !isChecked event
					group.check(R.id.remote_access_qr_toggle_biglybt);
					return;
				}
				if (!isChecked) {
					return;
				}
				viewMode = checkedId;
				setupVars(view);
			});
		}

		setupVars(view);

		return dialog;

	}

	void setupVars(View view) {
		Bundle args = getArguments();
		assert args != null;
		String url = args.getString(KEY_QR_URL_BIGLYBT);
		String urlWebUI = args.getString(KEY_QR_URL_WEBUI);

		TextView tv = view.findViewById(R.id.tv_info);
		Spanned s = AndroidUtils.fromHTML(getResources(),
				viewMode == R.id.remote_access_qr_toggle_biglybt
						? R.string.dialog_remote_access_biglybt_qr_info
						: R.string.dialog_remote_access_webui_qr_info);
		tv.setText(s);

		final ImageView iv = view.findViewById(R.id.iv_qr);
		if (iv != null) {
			new AwesomeQRCode.Renderer().contents(
					viewMode == R.id.remote_access_qr_toggle_biglybt ? url
							: urlWebUI).dotScale(1).size(AndroidUtilsUI.dpToPx(600)).margin(
									AndroidUtilsUI.dpToPx(4)).renderAsync(
											new AwesomeQRCode.Callback() {
												@Override
												public void onRendered(AwesomeQRCode.Renderer renderer,
														final Bitmap bitmap) {
													FragmentActivity activity = getActivity();
													if (activity == null || activity.isFinishing()) {
														return;
													}
													activity.runOnUiThread(() -> {
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

	}

	public static void open(FragmentManager fragmentManager, String url,
			String urlWebUI) {
		DialogFragmentRemoteAccessQR dlg = new DialogFragmentRemoteAccessQR();
		Bundle bundle = new Bundle();
		bundle.putString(KEY_QR_URL_BIGLYBT, url);
		bundle.putString(KEY_QR_URL_WEBUI, urlWebUI);

		dlg.setArguments(bundle);
		AndroidUtilsUI.showDialog(dlg, fragmentManager, TAG);
	}

}
