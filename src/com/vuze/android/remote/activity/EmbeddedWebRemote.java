package com.vuze.android.remote.activity;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
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
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.TextView;

import com.aelitis.azureus.util.JSONUtils;
import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.*;
import com.vuze.android.remote.DialogFragmentFilterBy.FilterByDialogListener;
import com.vuze.android.remote.DialogFragmentOpenTorrent.OpenTorrentDialogListener;
import com.vuze.android.remote.DialogFragmentSessionSettings.SessionSettingsListener;
import com.vuze.android.remote.DialogFragmentSortBy.SortByDialogListener;
import com.vuze.android.remote.rpc.RPC;
import com.vuze.android.remote.rpc.RPCException;

public class EmbeddedWebRemote
	extends FragmentActivity
	implements OpenTorrentDialogListener, FilterByDialogListener,
	SortByDialogListener, SessionSettingsListener
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

	private EditText filterEditText;

	private JSInterface jsInterface;

	private String ac;

	private String rpcRoot;

	private Semaphore semGoBack;

	private boolean goBackCancelled;

	protected boolean uiReady = false;

	private TextView tvUpSpeed;

	private TextView tvDownSpeed;

	private TextView tvTorrentCount;

	private TextView tvFilteringBy;

	private TextView tvCenter;

	protected SessionSettings sessionSettings;

	private String nick;

	private int port;

	private String host;

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

		final Bundle extras = intent.getExtras();
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

		setContentView(R.layout.activity_embedded_web_remote);

		ac = extras.getString("com.vuze.android.remote.ac");
		final String user = extras.getString("com.vuze.android.remote.user");
		final boolean remember = extras.getBoolean("com.vuze.android.remote.remember");
		nick = extras.getString("com.vuze.android.remote.nick");
		if (nick == null || nick.length() == 0) {
			nick = ac;
		}
		setTitle(nick);

		jsInterface = new JSInterface(this, myWebView, new JSInterfaceListener() {

			public void uiReady() {
				uiReady = true;
				System.out.println("UI READY");
				String dataString = getIntent().getDataString();
				if (dataString != null) {
					if (dataString.startsWith("file://")) {
						openTorrent(getIntent().getData());
					} else {
						openTorrent(dataString);
					}
				}

				runOnUiThread(new Runnable() {
					public void run() {
						tvCenter.setText("");
					}
				});
			}

			public void selectionChanged(final long selectionCount,
					boolean haveActive, boolean havePaused, boolean haveActiveSel,
					boolean havePausedSel) {
				EmbeddedWebRemote.this.selectionCount = selectionCount;
				EmbeddedWebRemote.this.haveActive = haveActive;
				EmbeddedWebRemote.this.havePaused = havePaused;
				EmbeddedWebRemote.this.haveActiveSel = haveActiveSel;
				EmbeddedWebRemote.this.havePausedSel = havePausedSel;

				runOnUiThread(new Runnable() {
					public void run() {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
							selectionChangedHoneyComb(selectionCount);
						}
					}

					@TargetApi(Build.VERSION_CODES.HONEYCOMB)
					private void selectionChangedHoneyComb(long selectionCount) {
						if (selectionCount == 0) {
							if (mActionMode != null) {
								mActionMode.finish();
							} else {
								invalidateOptionsMenu();
							}
							return;
						}

						showContextualActions();
					}

				});
			}

			@Override
			public void cancelGoBack(boolean cancel) {
				goBackCancelled = cancel;
				System.out.println("cancel gobakc " + cancel);
				semGoBack.release();
			}

			@Override
			public void deleteTorrent(long torrentID) {
				runJavaScript("deleteTorrent",
						"transmission.remote.removeTorrentAndDataById(" + torrentID + ");");
			}

			@Override
			public void updateSpeed(final long downSpeed, final long upSpeed) {
				runOnUiThread(new Runnable() {
					public void run() {
						if (downSpeed <= 0) {
							tvDownSpeed.setVisibility(View.GONE);
						} else {
							tvDownSpeed.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(downSpeed));
							tvDownSpeed.setVisibility(View.VISIBLE);
						}
						if (upSpeed <= 0) {
							tvUpSpeed.setVisibility(View.GONE);
						} else {
							tvUpSpeed.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(upSpeed));
							tvUpSpeed.setVisibility(View.VISIBLE);
						}
					}
				});
			}

			@Override
			public void updateTorrentCount(final long total) {
				runOnUiThread(new Runnable() {
					public void run() {
						if (total == 0) {
							tvTorrentCount.setText("");
						} else {
							tvTorrentCount.setText(total + " torrents");
						}
					}
				});
			}

			@Override
			public void sessionPropertiesUpdated(Map map) {
				SessionSettings settings = new SessionSettings();
				settings.setDLIsAuto(MapUtils.getMapBoolean(map,
						"speed-limit-down-enabled", true));
				settings.setULIsAuto(MapUtils.getMapBoolean(map,
						"speed-limit-up-enabled", true));
				long refreshRateSecs = MapUtils.getMapLong(map, "refresh_rate", 0);
				settings.setRefreshIntervalIsAuto(refreshRateSecs > 0);
				settings.setRefreshInterval(refreshRateSecs);
				settings.setDlSpeed(MapUtils.getMapLong(map, "speed-limit-down", 0));
				settings.setUlSpeed(MapUtils.getMapLong(map, "speed-limit-up", 0));
				EmbeddedWebRemote.this.sessionSettings = settings;

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					invalidateOptionsMenuHC();
				}
			}
		});

		host = extras.getString("com.vuze.android.remote.host");

		tvUpSpeed = (TextView) findViewById(R.id.wvUpSpeed);
		tvDownSpeed = (TextView) findViewById(R.id.wvDnSpeed);
		tvFilteringBy = (TextView) findViewById(R.id.wvFilteringBy);
		tvTorrentCount = (TextView) findViewById(R.id.wvTorrentCount);
		tvCenter = (TextView) findViewById(R.id.wvCenter);

		filterEditText = (EditText) findViewById(R.id.filterText);
		filterEditText.addTextChangedListener(new TextWatcher() {

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
			// old style menu
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
				Log.d("console.log", cm.message() + " -- line " + cm.lineNumber()
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

		myWebView.addJavascriptInterface(jsInterface, "externalOSFunctions");

		setProgressBarIndeterminateVisibility(true);

		Thread thread = new Thread() {
			public void run() {
				try {
					if (host != null && host.length() > 0) {
						port = extras.getInt("com.vuze.android.remote.port");
						open(user, ac, "http", host, port, remember);
					} else {
						bindAndOpen(ac, user, remember);
					}
				} finally {
					runOnUiThread(new Runnable() {
						public void run() {
							setProgressBarIndeterminateVisibility(false);
						}
					});
				}
			}
		};
		thread.setDaemon(true);
		thread.start();
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected void invalidateOptionsMenuHC() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				invalidateOptionsMenu();
			}
		});
	}

	protected void bindAndOpen(final String ac, final String user,
			boolean remember) {

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
							System.out.println("can't display -- finishing");
							return;
						}
						new AlertDialog.Builder(EmbeddedWebRemote.this).setTitle(
								"Error Connecting").setMessage(errMsg).setCancelable(false).setPositiveButton(
								"Ok", new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										if (EmbeddedWebRemote.this.isTaskRoot()) {
											new RemoteUtils(EmbeddedWebRemote.this).openRemoteList(getIntent());
										}
										finish();
									}
								}).show();
					}
				});

				return;
			}

			host = (String) bindingInfo.get("ip");
			String protocol = (String) bindingInfo.get("protocol");
			port = Integer.valueOf((String) bindingInfo.get("port"));

			if (host == null) {
				//ip = "192.168.2.59";
				host = "192.168.1.2";
				protocol = "http";
				port = 9092;
			}

			if (host != null && protocol != null) {
				open("vuze", ac, protocol, host, port, remember);
			}
		} catch (final RPCException e) {
			runOnUiThread(new Runnable() {
				public void run() {
					if (EmbeddedWebRemote.this.isFinishing()) {
						System.out.println("can't display -- finishing");
						return;
					}
					System.out.println("Error from RPCException " + e.getMessage());

					new AlertDialog.Builder(EmbeddedWebRemote.this).setTitle(
							"Error Connecting").setMessage(e.getMessage()).setCancelable(
							false).setPositiveButton("Ok",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									if (EmbeddedWebRemote.this.isTaskRoot()) {
										new RemoteUtils(EmbeddedWebRemote.this).openRemoteList(getIntent());
									}
									finish();
								}
							}).show();
				}
			});
			e.printStackTrace();
		}
	}

	private void open(String user, final String ac, String protocol, String host,
			int port, boolean remember) {
		try {
			String up = user + ":" + ac;

			rpcRoot = protocol + "://" + host + ":" + port + "/";
			String rpcUrl = rpcRoot + "transmission/rpc";

			if (!isURLAlive(rpcUrl)) {
				runOnUiThread(new Runnable() {
					public void run() {
						if (EmbeddedWebRemote.this.isFinishing()) {
							System.out.println("can't display -- finishing");
							return;
						}
						new AlertDialog.Builder(EmbeddedWebRemote.this).setTitle(
								"Error Connecting").setMessage("Remote was not found").setCancelable(
								false).setPositiveButton("Ok",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										if (EmbeddedWebRemote.this.isTaskRoot()) {
											new RemoteUtils(EmbeddedWebRemote.this).openRemoteList(getIntent());
										}
										finish();
									}
								}).show();
					}
				});

				return;
			}

			AppPreferences appPreferences = new AppPreferences(this);
			RemoteProfile remoteProfile = appPreferences.getRemote(nick);
			if (remoteProfile == null && remember) {
				remoteProfile = new RemoteProfile(user, ac);
			}
			if (remoteProfile != null) {
				remoteProfile.setLastUsedOn(System.currentTimeMillis());
				appPreferences.addRemoteProfile(remoteProfile);
				if (remember) {
					appPreferences.setLastRemote(ac);
				}
			}

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

			System.out.println("rpc root = " + rpcRoot);
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
				}
			});
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

				fixupMenu(menu);

				return true;
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
			if (uiReady) {
				runJavaScript("pauseUI", "transmission.pauseUI();");
			}
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
			if (uiReady) {
				runJavaScript("resumeUI", "transmission.resumeUI();");
			}
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

	public void openTorrent(Uri uri) {
		try {
			File file = new File(uri.getPath());
			//System.out.println("uri = " + uri + ";" + file);
			byte[] bs = readFileAsByteArray(file);
			String metainfo = Base64.encodeToString(bs, Base64.DEFAULT).replaceAll(
					"[\\r\\n]", "");
			//System.out.println("metainfo=" + metainfo);
			runJavaScript("openTorrent", "transmission.remote.addTorrentByMetainfo('"
					+ metainfo + "')");
		} catch (IOException e) {
			// TODO Alert
			e.printStackTrace();
		}
	}

	private void runJavaScript(final String id, final String js) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (!isFinishing()) {
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
				boolean newVisibility = filterEditText.getVisibility() != View.VISIBLE;
				filterEditText.setVisibility(newVisibility ? View.VISIBLE : View.GONE);
				if (newVisibility) {
					filterEditText.requestFocus();
				} else {
					myWebView.requestFocus();
				}
				return true;
			case R.id.action_settings:
				showSessionSettings();
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
				openContextMenu(myWebView);
				return true;

			case R.id.action_logout:
				new RemoteUtils(EmbeddedWebRemote.this).openRemoteList(getIntent());
				finish();
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

	private void showSessionSettings() {
		if (sessionSettings == null) {
			return;
		}
		;
		DialogFragmentSessionSettings dlg = new DialogFragmentSessionSettings();
		Bundle bundle = new Bundle();
		bundle.putSerializable(SessionSettings.class.getName(), sessionSettings);
		dlg.setArguments(bundle);
		dlg.show(getSupportFragmentManager(), "SessionSettings");
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
		MenuItem menuSessionSettings = menu.findItem(R.id.action_settings);
		menuSessionSettings.setEnabled(sessionSettings != null);

		MenuItem menuContext = menu.findItem(R.id.action_context);
		menuContext.setVisible(selectionCount > 0);

		fixupMenu(menu);

		return super.onPrepareOptionsMenu(menu);
	}

	private void fixupMenu(Menu menu) {
		for (int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			Drawable icon = item.getIcon();
			if (icon != null) {
				icon.setAlpha(item.isEnabled() ? 255 : 64);
			}
		}
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
	public void filterBy(String filterMode, String name) {
		runJavaScript("filterText", "transmission.setFilterMode(" + filterMode
				+ ");");
		tvFilteringBy.setText(name);
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

	public static byte[] readFileAsByteArray(File file)

			throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream((int) file.length());

		byte[] buffer = new byte[32 * 1024];

		InputStream is = new FileInputStream(file);

		try {
			while (true) {

				int len = is.read(buffer);

				if (len <= 0) {

					break;
				}

				baos.write(buffer, 0, len);
			}

			return (baos.toByteArray());

		} finally {

			is.close();
		}
	}

	public static boolean isURLAlive(String URLName) {
		try {
			HttpURLConnection.setFollowRedirects(false);
			HttpURLConnection con = (HttpURLConnection) new URL(URLName).openConnection();
			con.setConnectTimeout(2000);
			con.setReadTimeout(2000);
			con.setRequestMethod("HEAD");
			//System.out.println("conn result=" + con.getResponseCode() + ";" + con.getResponseMessage());
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public void sessionSettingsChanged(SessionSettings newSettings) {
		if (sessionSettings == null) {
			// Should not have happened -- dialog can only show when sessionSettings is non-null
			return;
		}
		if (newSettings.isRefreshIntervalIsAuto() != sessionSettings.isRefreshIntervalIsAuto()
				|| newSettings.getRefreshInterval() != sessionSettings.getRefreshInterval()) {
			long interval = newSettings.getRefreshInterval();
			runJavaScript("setRefreshInterval",
					"transmission.setPref(Prefs._RefreshRate, " + interval + ");"
							+ (interval > 0 ? "transmission.refreshTorrents();" : ""));
		}
		Map<String, Object> changes = new HashMap<String, Object>();
		if (newSettings.isDLAuto() != sessionSettings.isDLAuto()) {
			changes.put("speed-limit-down-enabled", newSettings.isDLAuto());
		}
		if (newSettings.isULAuto() != sessionSettings.isULAuto()) {
			changes.put("speed-limit-up-enabled", newSettings.isULAuto());
		}
		if (newSettings.getUlSpeed() != sessionSettings.getUlSpeed()) {
			changes.put("speed-limit-up", newSettings.getUlSpeed());
		}
		if (newSettings.getDlSpeed() != sessionSettings.getDlSpeed()) {
			changes.put("speed-limit-down", newSettings.getDlSpeed());
		}
		if (changes.size() > 0) {
			String json = JSONUtils.encodeToJSON(changes);
			runJavaScript("setSpeeds", "transmission.remote.savePrefs(" + json + ");");
		}
		sessionSettings = newSettings;
	}
}
