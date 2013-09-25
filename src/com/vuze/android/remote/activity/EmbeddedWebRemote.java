package com.vuze.android.remote.activity;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnLongClickListener;
import android.webkit.*;
import android.widget.SearchView;
import android.widget.TextView;

import com.vuze.android.remote.*;
import com.vuze.android.remote.DialogFragmentFilterBy.FilterByDialogListner;
import com.vuze.android.remote.DialogFragmentOpenTorrent.OpenTorrentDialogListener;
import com.vuze.android.remote.DialogFragmentSortBy.SortByDialogListner;
import com.vuze.android.remote.rpc.RPC;
import com.vuze.android.remote.rpc.RPCException;

public class EmbeddedWebRemote
	extends FragmentActivity
	implements OpenTorrentDialogListener, FilterByDialogListner,
	SortByDialogListner
{
	private WebView myWebView;

	private ValueCallback<Uri> mUploadMessage;

	private String rpcHost;

	private SearchView mSearchView;

	protected ActionMode mActionMode;

	// needs to be global? YES IT DOES
	private static boolean mIsPaused = false;

	private final static int FILECHOOSER_RESULTCODE = 1;

	private boolean haveActive;

	private boolean havePaused;

	private ActionMode.Callback mActionModeCallback;

	protected long selectionCount;

	protected boolean haveActiveSel;

	protected boolean havePausedSel;

	private TextView filterTextView;

	private JSInterface jsInterface;

	private String ac;

	private String rpcRoot;

	private Semaphore semGoBack;

	private boolean goBackCancelled;

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

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

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

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			setupHoneyComb();
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			setupIceCream();
		}

		//configActionBar();
		//		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.activity_embedded_web_remote);

		ac = extras.getString("com.vuze.android.remote.ac");
		final String user = extras.getString("com.vuze.android.remote.user");
		final boolean remember = extras.getBoolean("com.vuze.android.remote.remember");
		Thread thread = new Thread() {
			public void run() {
				bindAndOpen(ac, user, remember);
			}
		};
		thread.setDaemon(true);
		thread.start();

		filterTextView = (TextView) findViewById(R.id.filterText);
		filterTextView.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				String newText = s.toString();
				System.out.println("newSearch: " + newText);
				runJavaScript("filterText",
						"transmission.setFilterText('" + newText.replaceAll("'", "\\'")
								+ "');");
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		myWebView = (WebView) findViewById(R.id.webview);

		myWebView.clearCache(true);

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			// contextual action mode available in Honeycomb (11) and higher
			// Use transmission floating contextual menu for anything prior
			// runJavaScript("longClick", "if (typeof az != 'undefined' && typeof az.longClick != 'undefined') vz.longClick()");
			// TODO: use
			registerForContextMenu(myWebView);
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

		jsInterface = new JSInterface(this, myWebView, new JSInterfaceListener() {

			public void uiReady() {
				System.out.println("UI READY");
				String dataString = getIntent().getDataString();
				if (dataString != null) {
					openTorrent(dataString);
				}

			}

			public void selectionChanged(final long selectionCount,
					boolean haveActive, boolean havePaused, boolean haveActiveSel,
					boolean havePausedSel) {
				EmbeddedWebRemote.this.selectionCount = selectionCount;
				EmbeddedWebRemote.this.haveActive = haveActive;
				EmbeddedWebRemote.this.havePaused = havePaused;
				EmbeddedWebRemote.this.haveActiveSel = haveActiveSel;
				EmbeddedWebRemote.this.havePausedSel = havePausedSel;

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					runOnUiThread(new Runnable() {
						@TargetApi(Build.VERSION_CODES.HONEYCOMB)
						public void run() {

							System.out.println("selectionCount " + selectionCount + ";"
									+ mActionMode);
							if (selectionCount == 0) {
								if (mActionMode != null) {
									System.out.println("finish actionmode");
									mActionMode.finish();
								} else {
									if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
										invalidateOptionsMenu();
									} // else TODO
								}
								return;
							}

							showContextualActions();
						}

					});
				} // TODO: Handle API < 11
			}

			@Override
			public void cancelGoBack(boolean cancel) {
				goBackCancelled = cancel;
				System.out.println("cancel gobakc " + cancel);
				semGoBack.release();
			}
		});
		myWebView.addJavascriptInterface(jsInterface, "externalOSFunctions");

	}

	protected void bindAndOpen(final String ac, final String user,
			boolean remember) {
		runOnUiThread(new Runnable() {
			public void run() {
				setProgressBarIndeterminateVisibility(true);
			}
		});

		RPC rpc = new RPC();
		try {
			Map bindingInfo = rpc.getBindingInfo(ac);
			Map error = (Map) bindingInfo.get("error");
			if (error != null) {
				final String errMsg = (String) error.get("msg");
				System.out.println("Error from getBindingInfo " + errMsg);

				runOnUiThread(new Runnable() {
					public void run() {
						if (EmbeddedWebRemote.this.isFinishing()) {
							return;
						}
						new AlertDialog.Builder(EmbeddedWebRemote.this).setTitle(
								"Error Connecting").setMessage(errMsg).setCancelable(false).setPositiveButton(
								"Ok", new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										Intent myIntent = new Intent(getIntent());
										myIntent.setClassName("com.vuze.android.remote",
												LoginActivity.class.getName());

										startActivity(myIntent);
										finish();
									}
								}).show();
					}
				});

				return;
			}

			String ip = (String) bindingInfo.get("ip");
			String protocol = (String) bindingInfo.get("protocol");
			String port = (String) bindingInfo.get("port");

			if (ip == null) {
				//ip = "192.168.2.59";
				ip = "192.168.1.2";
				protocol = "http";
				port = "9092";
			}

			if (ip != null && protocol != null && port != null) {

				if (remember) {
					RemotePreferences remotePreferences = new RemotePreferences(user, ac);
					remotePreferences.setLastUsedOn(System.currentTimeMillis());

					AppPreferences appPreferences = new AppPreferences(this);
					appPreferences.addRemotePref(remotePreferences);
					appPreferences.setLastRemote(ac);
				}

				String up = "vuze:" + ac;

				rpcRoot = protocol + "://" + ip + ":" + port + "/";
				String rpcUrl = rpcRoot + "transmission/rpc";

				String urlEncoded = URLEncoder.encode(rpcUrl, "utf-8");
				String acEnc = URLEncoder.encode(ac, "utf-8");

				String basicAuth = Base64.encodeToString(up.getBytes("utf-8"), 0);

				final String remoteUrl = "file:///android_asset/transmission/web/index.html";
				final String remoteParams = "?vuze_pairing_ac=" + acEnc + "&_Root="
						+ urlEncoded + "&_BasicAuth=" + basicAuth;
				//remoteUrl = protocol + "://" + ip + ":" + port;

				rpcHost = "";
				try {
					URI uri = new URI(rpcUrl);
					rpcHost = uri.getHost();
				} catch (URISyntaxException e) {
				}

				jsInterface.setAc(ac);
				jsInterface.setRpcRoot(rpcRoot);

				runOnUiThread(new Runnable() {
					public void run() {
						// Android API 11-15 doesn't support url parameters on local files.  We
						// hack it into userAgent :)
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
								&& Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
							WebSettings webSettings = myWebView.getSettings();
							webSettings.setUserAgentString(remoteParams);
							myWebView.loadUrl(remoteUrl);
						} else {
							myWebView.loadUrl(remoteUrl + remoteParams);
						}
						setTitle(ac);
					}
				});
			}
		} catch (final RPCException e) {
			runOnUiThread(new Runnable() {
				public void run() {
					if (EmbeddedWebRemote.this.isFinishing()) {
						return;
					}
					System.out.println("Error from RPCException " + e.getMessage());

					new AlertDialog.Builder(EmbeddedWebRemote.this).setTitle(
							"Error Connecting").setMessage(e.toString()).setCancelable(false).setPositiveButton(
							"Ok", new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									Intent myIntent = new Intent(getIntent());
									myIntent.setClassName("com.vuze.android.remote",
											LoginActivity.class.getName());

									startActivity(myIntent);
									finish();
								}
							}).show();
				}
			});
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			runOnUiThread(new Runnable() {
				public void run() {
					setProgressBarIndeterminateVisibility(false);
				}
			});
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected boolean showContextualActions() {
		if (mActionMode != null) {
			mActionMode.invalidate();
			return false;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			// Start the CAB using the ActionMode.Callback defined above
			mActionMode = startActionMode(mActionModeCallback);
		} // else TODO
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
		// needed because one of our test machines won't listen to <item name="android:windowActionBar">true</item>
		requestWindowFeature(Window.FEATURE_ACTION_BAR);

		// enable ActionBar app icon to behave as action to toggle nav drawer
		ActionBar actionBar = getActionBar();
		if (actionBar == null) {
			System.err.println("actionBar is null");
			return;
		}
		actionBar.setDisplayHomeAsUpEnabled(true);

		mActionModeCallback = new ActionMode.Callback() {

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
				MenuItem menuMove = menu.findItem(R.id.action_sel_move);
				menuMove.setEnabled(selectionCount == 1);
				MenuItem menuStart = menu.findItem(R.id.action_sel_start);
				menuStart.setEnabled(havePausedSel);
				MenuItem menuStop = menu.findItem(R.id.action_sel_stop);
				menuStop.setEnabled(haveActiveSel);

				return true; // Return false if nothing is done
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
				runJavaScript("closeContext", "transmission.deselectAll();");
			}
		};

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
		if (sendBackPressToWeb()) {
			System.out.println("CANCELLED");
			return;
		}
		super.onBackPressed();
	}

	private boolean sendBackPressToWeb() {
		goBackCancelled = false;

		semGoBack = new Semaphore(0);

		runJavaScript("goBack", "vz.goBack();");

		try {
			boolean tryAcquire = semGoBack.tryAcquire(200, TimeUnit.MILLISECONDS);
			if (tryAcquire) {
				if (goBackCancelled) {
					return true;
				}
			} else {
				System.out.println("tryAquire teimout");
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (mActionMode != null) {
			if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
					&& event.getAction() == KeyEvent.ACTION_UP) {
				if (sendBackPressToWeb()) {
					return true;
				}
			}
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	protected void onPause() {
		super.onPause();
		pauseUI();
	}

	private void pauseUI() {
		if (!mIsPaused && myWebView != null) {
			System.out.println("EWR Pause");
			//			myWebView.pauseTimers();
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
			System.out.println("EWR resume");
			//			myWebView.resumeTimers();
			runJavaScript("resumeUI", "transmission.resumeUI();");
			mIsPaused = false;
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		System.out.println("EWR STOP");
	}

	@Override
	protected void onDestroy() {
		if (myWebView != null) {
			// Ensure webview gets destroyed (reports are it doesn't!)
			// http://www.anddev.org/other-coding-problems-f5/webviewcorethread-problem-t10234.html
			myWebView.stopLoading();
			//		try {
			//			((ViewGroup) myWebView.getParent()).removeView(myWebView);
			//		} catch (Exception e) {
			//		}
			//		myWebView.destroy();

			myWebView.loadUrl("about:blank");
		}

		super.onDestroy();
		System.out.println("EWR onDestroy");
	}

	public void openTorrent(String s) {
		runJavaScript("openTorrent",
				"transmission.remote.addTorrentByUrl('" + s.replaceAll("'", "\\'")
						+ "', false)");
	}

	private void runJavaScript(final String id, final String js) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (isFinishing()) {
					myWebView.loadUrl("javascript:try {" + js
							+ "} catch (e) { console.log('Error in " + id
							+ "');  console.log(e); }");
					return;
				}
			}
		});
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

				Intent upIntent = NavUtils.getParentActivityIntent(this);
				if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
					upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

					// This activity is NOT part of this app's task, so create a new task
					// when navigating up, with a synthesized back stack.
					TaskStackBuilder.create(this)
					// Add all of this activity's parents to the back stack
					.addNextIntentWithParentStack(upIntent)
					// Navigate up to the closest parent
					.startActivities();
					finish();
				} else {
					upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(upIntent);
					finish();
					// Opens parent with FLAG_ACTIVITY_CLEAR_TOP
					// Note: navigateUpFromSameTask and navigateUpTo doesn't set FLAG_ACTIVITY_CLEAR_TOP on JellyBean
					//NavUtils.navigateUpFromSameTask(this);
					//NavUtils.navigateUpTo(this, upIntent);
				}
				return true;
			case R.id.action_filterby:
				openFilterByDialog();
				return true;
			case R.id.action_filter:
				boolean newVisibility = filterTextView.getVisibility() != View.VISIBLE;
				filterTextView.setVisibility(newVisibility ? View.VISIBLE : View.GONE);
				if (newVisibility) {
					filterTextView.requestFocus();
				} else {
					myWebView.requestFocus();
				}
				return true;
			case R.id.action_settings:
				runJavaScript("toggleSettings", "transmission.showPrefsDialog();");
				return true;
			case R.id.action_sortby:
				openSortByDialog();
				return true;
			case R.id.action_add_torrent:
				DialogFragmentOpenTorrent dlg = new DialogFragmentOpenTorrent();
				dlg.show(getSupportFragmentManager(), "OpenTorrentDialog");
				break;
			case R.id.action_search:
				onSearchRequested();
				return true;

			case R.id.action_context:
				runJavaScript("showContext", "transmission.showContextMenu();");
				return true;

			case R.id.action_logout:
				Intent myIntent = new Intent(getIntent());
				myIntent.setAction(Intent.ACTION_VIEW);
				myIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION
						| Intent.FLAG_ACTIVITY_CLEAR_TOP);
				myIntent.setClassName("com.vuze.android.remote",
						IntentHandler.class.getName());
				startActivity(myIntent);
				return true;

				// Start of Context Menu Items
			case R.id.action_sel_remove:
				runJavaScript("removeSelected",
						"transmission.removeSelectedTorrentsAndData();");
				return true;
			case R.id.action_sel_start:
				runJavaScript("startSelected",
						"transmission.startSelectedTorrents(false);");
				return true;
			case R.id.action_sel_forcestart:
				runJavaScript("fstartSelected",
						"transmission.startSelectedTorrents(true);");
				return true;
			case R.id.action_sel_stop:
				runJavaScript("remoteSelected", "transmission.stopSelectedTorrents();");
				return true;
			case R.id.action_sel_relocate:
				runJavaScript("relocate", "transmission.moveSelectedTorrents(false);");
				return true;
			case R.id.action_sel_move_top:
				runJavaScript("relocate", "transmission.moveTop();");
				return true;
			case R.id.action_sel_move_up:
				runJavaScript("relocate", "transmission.moveUp();");
				return true;
			case R.id.action_sel_move_down:
				runJavaScript("relocate", "transmission.moveDown();");
				return true;
			case R.id.action_sel_move_bottom:
				runJavaScript("relocate", "transmission.moveBottom();");
				return true;
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
	// For Android 2.x
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_context, menu);
	}

	@Override
	// For Android 2.x
	public boolean onContextItemSelected(MenuItem item) {
		if (handleMenu(item.getItemId())) {
			return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_web, menu);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			MenuItem searchItem = menu.findItem(R.id.action_search);
			setupSearchView(searchItem);
		}

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {

		MenuItem menuStartAll = menu.findItem(R.id.action_start_all);
		menuStartAll.setEnabled(havePaused);
		MenuItem menuStopAll = menu.findItem(R.id.action_stop_all);
		menuStopAll.setEnabled(haveActive);

		MenuItem menuContext = menu.findItem(R.id.action_context);
		menuContext.setVisible(selectionCount > 0);

		return super.onPrepareOptionsMenu(menu);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupSearchView(MenuItem searchItem) {
		mSearchView = (SearchView) searchItem.getActionView();
		if (mSearchView == null) {
			return;
		}

		SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
		mSearchView.setIconifiedByDefault(true);

		//		searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		/*
				mSearchView.setOnQueryTextListener(new OnQueryTextListener() {

					@Override
					public boolean onQueryTextChange(String newText) {
						System.out.println("Query = " + newText);
						return false;
					}

					@Override
					public boolean onQueryTextSubmit(String query) {

						System.out.println("Query = " + query + " : submitted");
						return true;
					}

				});
				*/
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

	@Override
	public boolean onSearchRequested() {
		System.out.println("ON SEARCH Re");
		Bundle appData = new Bundle();
		appData.putString("com.vuze.android.remote.searchsource", rpcRoot);
		appData.putString("com.vuze.android.remote.ac", ac);
		startSearch(null, false, appData, false);
		return true;
	}
}
