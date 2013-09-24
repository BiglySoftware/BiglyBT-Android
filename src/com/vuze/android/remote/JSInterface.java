package com.vuze.android.remote;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.FragmentActivity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.vuze.android.remote.activity.LoginActivity;

public class JSInterface
{
	private FragmentActivity activity;

	private JSInterfaceListener listener;

	private String rpcRoot;

	private String ac;

	public JSInterface(FragmentActivity activity, WebView myWebView,
			JSInterfaceListener listener) {
		this.activity = activity;
		this.listener = listener;
		this.setRpcRoot(rpcRoot);
	}

	@JavascriptInterface
	public void showOpenTorrentDialog() {
		DialogFragmentOpenTorrent dlg = new DialogFragmentOpenTorrent();
		dlg.show(activity.getSupportFragmentManager(), "OpenTorrentDialog");
	}

	@JavascriptInterface
	public boolean executeSearch(String search) {
		try {
			String strURL = "http://search.vuze.com/xsearch/?q="
					+ URLEncoder.encode(search, "utf-8")
					+ "&xdmv=no&source=android&search_source="
					+ URLEncoder.encode(rpcRoot, "utf-8") + "&ac="
					+ URLEncoder.encode(getAc(), "utf-8");

			System.out.println(strURL);
			Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(strURL));
			activity.startActivity(myIntent);
			return true;
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return false;
	}

	@JavascriptInterface
	public void selectionChanged(long selectionCount, boolean haveActive,
			boolean havePaused, boolean haveActiveSel, boolean havePausedSel) {
		listener.selectionChanged(selectionCount, haveActive, havePaused,
				haveActiveSel, havePausedSel);
	}

	@JavascriptInterface
	public void logout() {
		if (activity.isFinishing()) {
			System.err.println("activity finishing.. can't log out");
			return;
		}

		System.out.println("logging out " + activity.toString());

		Intent myIntent = new Intent(activity.getIntent());
		myIntent.setClassName("com.vuze.android.remote",
				LoginActivity.class.getName());

		activity.startActivity(myIntent);
		activity.finish();
	}

	@JavascriptInterface
	public void uiReady() {
		listener.uiReady();
	}

	@JavascriptInterface
	public void cancelGoBack(boolean cancel) {
		listener.cancelGoBack(cancel);
	}

	@JavascriptInterface
	public boolean handleConnectionError(final long errNo, final String errMsg) {
		System.out.println("hCE: " + errNo + ";" + errMsg);
		activity.runOnUiThread(new Runnable() {
			public void run() {
				new AlertDialog.Builder(activity).setTitle("Error Connecting").setMessage(
						errMsg).setCancelable(false).setPositiveButton("Ok",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								logout();
							}
						}).show();
			}
		});

		return true;
	}

	@JavascriptInterface
	public boolean handleTapHold() {
		return true;
	}

	public String getRpcRoot() {
		return rpcRoot;
	}

	public void setRpcRoot(String rpcRoot) {
		this.rpcRoot = rpcRoot;
	}

	public String getAc() {
		return ac;
	}

	public void setAc(String ac) {
		this.ac = ac;
	}
}
