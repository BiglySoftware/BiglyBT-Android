package com.vuze.android.remote;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

public class JSInterface
{
	private WebView myWebView;

	private FragmentActivity activity;

	private JSInterfaceListener listener;

	public JSInterface(FragmentActivity activity, WebView myWebView,
			JSInterfaceListener listener) {
		this.activity = activity;
		this.myWebView = myWebView;
		this.listener = listener;
	}

	@JavascriptInterface
	public void showOpenTorrentDialog() {
		DialogFragmentOpenTorrent dlg = new DialogFragmentOpenTorrent();
		dlg.show(activity.getSupportFragmentManager(), "OpenTorrentDialog");
	}

	@JavascriptInterface
	public boolean executeSearch(String search) {
		Intent intent = activity.getIntent();
		if (intent == null) {
			return false;
		}
		Bundle extras = intent.getExtras();
		if (extras == null) {
			return false;
		}
		String rpcRoot = extras.getString("com.vuze.android.rpc.root");
		String ac = extras.getString("com.vuze.android.remote.ac");

		try {
			String strURL = "http://search.vuze.com/xsearch/?q="
					+ URLEncoder.encode(search, "utf-8")
					+ "&xdmv=no&source=android&search_source="
					+ URLEncoder.encode(rpcRoot, "utf-8") + "&ac="
					+ URLEncoder.encode(ac, "utf-8");

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
	public boolean showStatusBar() {
		return false;
	}

	@JavascriptInterface
	public void logout() {
		Context context = myWebView.getContext();
		if (context instanceof Activity) {
			Activity activity = (Activity) context;

			if (activity.isFinishing()) {
				System.err.println("activity finishing.. can't log out");
				return;
			}

			System.out.println("logging out " + activity.toString());

			Intent myIntent = new Intent();
			myIntent.setClassName("com.vuze.android.remote",
					"com.vuze.android.remote.LoginActivity");

			activity.startActivity(myIntent);
			activity.finish();
		}
	}

	@JavascriptInterface
	public void uiReady() {
		listener.uiReady();
	}

	@JavascriptInterface
	public boolean handleConnectionError() {
		logout();
		return true;
	}
}
