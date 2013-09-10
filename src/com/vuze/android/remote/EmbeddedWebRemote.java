package com.vuze.android.remote;

import java.net.URI;
import java.net.URISyntaxException;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.MenuItem;
import android.webkit.*;

import com.vuze.android.remote.OpenTorrentDialogFragment.OpenTorrentDialogListener;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
@SuppressLint("SetJavaScriptEnabled")
public class EmbeddedWebRemote
	extends FragmentActivity
	implements OpenTorrentDialogListener
{
	private WebView myWebView;

	private ValueCallback<Uri> mUploadMessage;

	private String rpcHost;

	// needs to be global?
	private static boolean mIsPaused = false;

	private final static int FILECHOOSER_RESULTCODE = 1;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (requestCode == FILECHOOSER_RESULTCODE) {
			if (null == mUploadMessage)
				return;
			Uri result = intent == null || resultCode != RESULT_OK ? null
					: intent.getData();
			mUploadMessage.onReceiveValue(result);
			mUploadMessage = null;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		System.out.println("embeddedWebRemote intent = " + intent);
		System.out.println("Type:" + intent.getType() + ";"
				+ intent.getDataString());

		Bundle extras = intent.getExtras();
		if (extras == null) {
			System.err.println("No extras!");
			finish();
			return;
		}
		String rpcUrl = extras.getString("com.vuze.android.rpc.url");
		String remoteUrl = extras.getString("com.vuze.android.remote.url");
		//String ac = extras.getString("com.vuze.android.remote.ac");
		System.out.println("RPC URL IS " + rpcUrl);
		System.out.println("remote URL is " + remoteUrl);

		rpcHost = "";
		try {
			URI uri = new URI(remoteUrl);
			rpcHost = uri.getHost();
		} catch (URISyntaxException e) {
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			setupIceCream();
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			setupHoneyComb();
		}

		//configActionBar();
		//		requestWindowFeature(Window.FEATURE_NO_TITLE);

		String name = extras.getString("com.vuze.android.remote.name");
		setTitle(name);

		setContentView(R.layout.activity_embedded_web_remote);

		myWebView = (WebView) findViewById(R.id.webview);

		myWebView.clearCache(true);

		//		myWebView.setOnLongClickListener(new OnLongClickListener() {
		//			@Override
		//			public boolean onLongClick(View v) {
		//				myWebView.loadUrl("javascript:if (typeof az != 'undefined' && typeof az.longClick != 'undefined') vz.longClick()");
		//				System.out.println("Long Clicked!");
		//				return false;
		//			}
		//		});

		myWebView.setWebChromeClient(new WebChromeClient() {
			public boolean onConsoleMessage(ConsoleMessage cm) {
				Log.d("console.log", cm.message() + " -- From line " + cm.lineNumber()
						+ " of " + cm.sourceId());
				return true;
			}

			// For Android 3.0+
			@SuppressWarnings("unused")
			public void openFileChooser(ValueCallback<Uri> uploadMsg) {

				mUploadMessage = uploadMsg;
				Intent i = new Intent(Intent.ACTION_GET_CONTENT);
				i.addCategory(Intent.CATEGORY_OPENABLE);
				i.setType("*/*");
				Log.d("console.log", "Hi1");

				EmbeddedWebRemote.this.startActivityForResult(
						Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);
			}

			// For Android 3.0+
			@SuppressWarnings("unused")
			public void openFileChooser(ValueCallback uploadMsg, String acceptType) {
				mUploadMessage = uploadMsg;
				Intent i = new Intent(Intent.ACTION_GET_CONTENT);
				i.addCategory(Intent.CATEGORY_OPENABLE);
				i.setType("*/*");
				Log.d("console.log", "Hi2" + acceptType);
				EmbeddedWebRemote.this.startActivityForResult(
						Intent.createChooser(i, "File Browser"), FILECHOOSER_RESULTCODE);
			}

			//For Android 4.1
			@SuppressWarnings("unused")
			public void openFileChooser(ValueCallback<Uri> uploadMsg,
					String acceptType, String capture) {
				mUploadMessage = uploadMsg;
				Intent i = new Intent(Intent.ACTION_GET_CONTENT);
				i.addCategory(Intent.CATEGORY_OPENABLE);
				i.setType("*/*");
				Log.d("console.log", "Hi3" + acceptType);
				EmbeddedWebRemote.this.startActivityForResult(
						Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);
			}
		});

		myWebView.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				String newUrlHost = Uri.parse(url).getHost();
				if (newUrlHost == null) {
					newUrlHost = "";
				}
				if (!newUrlHost.equals(rpcHost)) {
					return false;
				}

				// Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				startActivity(intent);
				return true;
			}
		});

		WebSettings webSettings = myWebView.getSettings();
		webSettings.setJavaScriptEnabled(true);
		webSettings.setLightTouchEnabled(true);
		webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
		webSettings.setDomStorageEnabled(true);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			setupJellyBean(webSettings);
		}

		myWebView.addJavascriptInterface(new JSInterface(this, myWebView,
				new JSInterfaceListener() {
					public void uiReady() {
						System.out.println("UI READY");
						String dataString = getIntent().getDataString();
						if (dataString != null) {
							openTorrent(dataString);
						}
					}
				}), "externalOSFunctions");

		myWebView.loadUrl(remoteUrl);

	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void setupJellyBean(WebSettings webSettings) {
		webSettings.setAllowUniversalAccessFromFileURLs(true);
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void setupIceCream() {
		getActionBar().setHomeButtonEnabled(true);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupHoneyComb() {
		// enable ActionBar app icon to behave as action to toggle nav drawer
		getActionBar().setDisplayHomeAsUpEnabled(true);

	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void configActionBar() {
		ActionBar actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(false);
		//actionBar.setHomeButtonEnabled(false);
		actionBar.setDisplayUseLogoEnabled(false);
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setDisplayShowHomeEnabled(false);
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
	}

	@Override
	protected void onPause() {
		super.onPause();
		pauseUI();
	}

	private void pauseUI() {
		if (!mIsPaused && myWebView != null) {
			System.out.println("Pause");
			myWebView.pauseTimers();
			runJavaScript("pauseUI", "transmission.pauseUI();");
			mIsPaused = true;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		resumeUI();
	}

	private void resumeUI() {
		if (mIsPaused && myWebView != null) {
			System.out.println("resume");
			myWebView.resumeTimers();
			runJavaScript("resumeUI", "transmission.resumeUI();");
			mIsPaused = false;
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		System.out.println("STOP");
	}

	@Override
	protected void onDestroy() {
		// Ensure webview gets destroyed (reports are it doesn't!)
		// http://www.anddev.org/other-coding-problems-f5/webviewcorethread-problem-t10234.html
				myWebView.stopLoading();
		//		try {
		//			((ViewGroup) myWebView.getParent()).removeView(myWebView);
		//		} catch (Exception e) {
		//		}
		//		myWebView.destroy();

		myWebView.loadUrl("about:blank");

		super.onDestroy();
		System.out.println("onDestroy");
	}

	public void openTorrent(String s) {
		runJavaScript("openTorrent", "transmission.remote.addTorrentByUrl('"
				+ s.replaceAll("'", "\\'") + "', false)");
	}

	private void runJavaScript(String id, String js) {
		myWebView.loadUrl("javascript:try {" + js + "} catch (e) { console.log('Error in " + id + "');  console.log(e); }");
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				// This ID represents the Home or Up button. In the case of this
				// activity, the Up button is shown. Use NavUtils to allow users
				// to navigate up one level in the application structure. For
				// more details, see the Navigation pattern on Android Design:
				//
				// http://developer.android.com/design/patterns/navigation.html#up-vs-back
				//
				NavUtils.navigateUpFromSameTask(this);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

}
