package com.vuze.android.remote;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

public class JSInterface
{
	private WebView myWebView;
	private FragmentActivity activity;
	private JSInterfaceListener listener;

	public JSInterface(FragmentActivity activity, WebView myWebView, JSInterfaceListener listener) {
		this.activity = activity;
		this.myWebView = myWebView;
		this.listener = listener;
	}

	@JavascriptInterface
	public void showOpenTorrentDialog() {
		OpenTorrentDialogFragment dlg = new OpenTorrentDialogFragment();
		dlg.show(activity.getSupportFragmentManager(), "OpenTorrentDialog");
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
