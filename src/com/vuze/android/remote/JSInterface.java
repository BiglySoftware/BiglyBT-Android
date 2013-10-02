package com.vuze.android.remote;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.FragmentActivity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.aelitis.azureus.util.JSONUtils;
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
	public void updateSessionProperties(String json) {
		Map map = JSONUtils.decodeJSON(json);
		listener.sessionPropertiesUpdated(map);
	}

	@JavascriptInterface
	public void showOpenTorrentDialog() {
		DialogFragmentOpenTorrent dlg = new DialogFragmentOpenTorrent();
		dlg.show(activity.getSupportFragmentManager(), "OpenTorrentDialog");
	}

	@JavascriptInterface
	public boolean showConfirmDeleteDialog(String name, final long torrentID) {
		// TODO: Strings.xml
		new AlertDialog.Builder(activity).setTitle("Remove and Delete Data?").setMessage(
				"All data downloaded for '" + name + "' will be deleted.").setPositiveButton(
				"Remove", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						listener.deleteTorrent(torrentID);
					}
				}).setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					}
				}).setIcon(android.R.drawable.ic_dialog_alert).show();
		return true;
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
	public void updateSpeed(long downSpeed, long upSpeed) {
		System.out.println("update speed " + downSpeed + ";" + upSpeed);
		listener.updateSpeed(downSpeed, upSpeed);
	}

	@JavascriptInterface
	public void updateTorrentCount(long total) {
		listener.updateTorrentCount(total);
	}

	@JavascriptInterface
	public void logout() {
		if (activity.isFinishing()) {
			System.err.println("activity finishing.. can't log out");
			return;
		}

		System.out.println("logging out " + activity.toString());

		Intent myIntent = new Intent(activity.getIntent());
		myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
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
	public boolean handleConnectionError(final long errNo, final String errMsg, final String status) {
		System.out.println(ac + "/hCE: " + errNo + ";" + errMsg);
		
		if (status.equals("timeout")) {
			// ignore timeout for now :(
			// TODO: Don't ignore
			return true;
		}
		activity.runOnUiThread(new Runnable() {
			public void run() {
				if (activity.isFinishing()) {
					System.out.println("can't display -- finishing");
					return;
				}
				// XXX LEAK IF WE GET MULTIPLE ERRORS!!!
				new AlertDialog.Builder(activity).setTitle("Error Connecting").setMessage(
						errMsg).setCancelable(false).setPositiveButton("Ok",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								if (activity.isTaskRoot()) {
									new RemoteUtils(activity).openRemoteList(activity.getIntent());
								}
								activity.finish();
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
