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
import android.util.Log;
import android.view.*;
import android.view.View.OnLongClickListener;
import android.webkit.*;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;

import com.vuze.android.remote.DialogFragmentFilterBy.FilterByDialogListner;
import com.vuze.android.remote.DialogFragmentOpenTorrent.OpenTorrentDialogListener;
import com.vuze.android.remote.DialogFragmentSortBy.SortByDialogListner;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
@SuppressLint("SetJavaScriptEnabled")
public class EmbeddedWebRemote
	extends FragmentActivity
	implements OpenTorrentDialogListener, FilterByDialogListner,
	SortByDialogListner
{
	private WebView myWebView;

	private ValueCallback<Uri> mUploadMessage;

	private String rpcHost;

	private SearchView mSearchView;

	protected Object mActionMode;

	// needs to be global?
	private static boolean mIsPaused = false;

	private final static int FILECHOOSER_RESULTCODE = 1;

	private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

		// Called when the action mode is created; startActionMode() was called
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			// Inflate a menu resource providing context menu items
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.menu_context, menu);
			return true;
		}

		// Called each time the action mode is shown. Always called after onCreateActionMode, but
		// may be called multiple times if the mode is invalidated.
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false; // Return false if nothing is done
		}

		// Called when the user selects a contextual menu item
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			if (EmbeddedWebRemote.this.handleMenu(item.getItemId())) {
				return true;
			}
			return false;
		}

		// Called when the user exits the action mode
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mActionMode = null;
		}
	};

	private SearchView mFilterView;

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

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			// contextual action mode available in Honeycomb (11) and higher
			// Use transmission floating contextual menu for anything prior
			// runJavaScript("longClick", "if (typeof az != 'undefined' && typeof az.longClick != 'undefined') vz.longClick()");
			// TODO: use
			//registerForContextMenu(myWebView);
		} else {
			myWebView.setOnLongClickListener(new OnLongClickListener() {
				public boolean onLongClick(View view) {
					return showContextualActions();
				}
			});
		}

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

	protected boolean showContextualActions() {
		if (mActionMode != null) {
			return false;
		}

		// Start the CAB using the ActionMode.Callback defined above
		mActionMode = startActionMode(mActionModeCallback);
		return true;
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
		runJavaScript("openTorrent",
				"transmission.remote.addTorrentByUrl('" + s.replaceAll("'", "\\'")
						+ "', false)");
	}

	private void runJavaScript(String id, String js) {
		myWebView.loadUrl("javascript:try {" + js
				+ "} catch (e) { console.log('Error in " + id
				+ "');  console.log(e); }");
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (handleMenu(item.getItemId())) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected boolean handleMenu(int itemId) {
		switch (itemId) {
			case android.R.id.home:
				// This ID represents the Home or Up button. In the case of this
				// activity, the Up button is shown. Use NavUtils to allow users
				// to navigate up one level in the application structure. For
				// more details, see the Navigation pattern on Android Design:
				//
				// http://developer.android.com/design/patterns/navigation.html#up-vs-back
				//
				//NavUtils.navigateUpFromSameTask(this);
				return true;
			case R.id.action_filterby:
				openFilterByDialog();
				return true;
			case R.id.action_compact:
				runJavaScript("toggleCompact", "transmission.toggleCompactClicked();");
				return true;
			case R.id.action_settings:
				runJavaScript("toggleSettings", "transmission.showPrefsDialog();");
				return true;
			case R.id.action_sortby:
				openSortByDialog();
				return true;
			case R.id.action_context:
				return showContextualActions();
		}
		return false;
	}

	private void openSortByDialog() {
		DialogFragmentSortBy dlg = new DialogFragmentSortBy();
		dlg.show(getSupportFragmentManager(), "OpenSortDialog");
	}

	private void openFilterByDialog() {
		DialogFragmentFilterBy dlg = new DialogFragmentFilterBy();
		dlg.show(getSupportFragmentManager(), "OpenFilterDialog");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_web, menu);

		MenuItem filterItem = menu.findItem(R.id.action_filter);
		mFilterView = (SearchView) filterItem.getActionView();
		setupFilterView(filterItem);

		MenuItem searchItem = menu.findItem(R.id.action_search);
		mSearchView = (SearchView) searchItem.getActionView();
		setupSearchView(searchItem);

		return true;
	}

	private void setupFilterView(MenuItem filterItem) {
//		filterItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM
//				| MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		mFilterView.setOnQueryTextListener(new OnQueryTextListener() {

			@Override
			public boolean onQueryTextChange(String newText) {
				System.out.println("Query = " + newText);
				runJavaScript("filterText",
						"transmission.setFilterText('" + newText.replaceAll("'", "\\'")
								+ "');");
				return false;
			}

			@Override
			public boolean onQueryTextSubmit(String query) {
				System.out.println("Query = " + query + " : submitted");
				return false;
			}

		});
	}

	private void setupSearchView(MenuItem searchItem) {

//		searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

		mSearchView.setOnQueryTextListener(new OnQueryTextListener() {

			@Override
			public boolean onQueryTextChange(String newText) {
				System.out.println("Query = " + newText);
				return false;
			}

			@Override
			public boolean onQueryTextSubmit(String query) {
				System.out.println("Query = " + query + " : submitted");
				runJavaScript("searchText",
						"vz.executeSearch('" + query.replaceAll("'", "\\'") + "');");
				return false;
			}

		});
	}

	@Override
	public void filterBy(String filterMode) {
		runJavaScript("filterText", "transmission.setFilterMode(" + filterMode
				+ ");");
	}

	@Override
	public void sortBy(String sortType) {
		runJavaScript("sortBy", "transmission.setSortMethod(" + sortType + ");");
	}

	@Override
	public void flipSortOrder() {
		runJavaScript(
				"flipSort",
				"if (transmission[Prefs._SortDirection] === Prefs._SortDescending) transmission.setSortDirection(Prefs._SortAscending); else transmission.setSortDirection(Prefs._SortDescending);");
	}

}
