package com.vuze.android.remote.activity;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import android.widget.SearchView.OnQueryTextListener;

import com.aelitis.azureus.util.JSONUtils;
import com.aelitis.azureus.util.MapUtils;
import com.google.analytics.tracking.android.EasyTracker;
import com.google.analytics.tracking.android.MapBuilder;
import com.vuze.android.remote.*;
import com.vuze.android.remote.dialog.*;
import com.vuze.android.remote.dialog.DialogFragmentFilterBy.FilterByDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentMoveData.MoveDataDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentOpenTorrent.OpenTorrentDialogListener;
import com.vuze.android.remote.dialog.DialogFragmentSessionSettings.SessionSettingsListener;
import com.vuze.android.remote.dialog.DialogFragmentSortBy.SortByDialogListener;
import com.vuze.android.remote.rpc.RPC;
import com.vuze.android.remote.rpc.RPCException;

public class EmbeddedWebRemote
	extends FragmentActivity
	implements OpenTorrentDialogListener, FilterByDialogListener,
	SortByDialogListener, SessionSettingsListener, MoveDataDialogListener
{
	private WebView myWebView;

	private ValueCallback<Uri> mUploadMessage;

	private String rpcHost;

	private SearchView mSearchView;

	protected ActionMode mActionMode;

	public final static int FILECHOOSER_RESULTCODE = 1;

	private static final boolean DEBUG = AndroidUtils.DEBUG;

	private boolean haveActive;

	private boolean havePaused;

	private ActionMode.Callback mActionModeCallback;

	protected boolean haveActiveSel;

	protected boolean havePausedSel;

	private EditText filterEditText;

	private JSInterface jsInterface;

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

	protected boolean searchIsIconified = true;

	private RemoteProfile remoteProfile;

	private boolean wifiConnected;

	private boolean isOnline;

	private BroadcastReceiver mConnectivityReceiver;

	private boolean remember;

	@SuppressWarnings("rawtypes")
	protected List<Map> selectedTorrents = new ArrayList<Map>(0);

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (DEBUG) {
			System.out.println("ActivityResult!! " + requestCode + "/" + resultCode
					+ ";" + intent);
		}

		requestCode &= 0xFFFF;

		if (requestCode == FILECHOOSER_RESULTCODE) {
			Uri result = intent == null || resultCode != RESULT_OK ? null
					: intent.getData();
			if (DEBUG) {
				System.out.println("result = " + result);
			}
			if (result == null) {
				return;
			}
			if (mUploadMessage != null) {
				// came from the browser
				mUploadMessage.onReceiveValue(result);
				mUploadMessage = null;
			} else {
				openTorrent(result);
			}
			return;
		}

		super.onActivityResult(requestCode, resultCode, intent);
	}

	@SuppressWarnings("deprecation")
	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		Intent intent = getIntent();
		if (DEBUG) {
			System.out.println("embeddedWebRemote intent = " + intent);
			System.out.println("Type:" + intent.getType() + ";"
					+ intent.getDataString());
		}

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
		// setup view ids now because listeners below may trigger as soon as we get them
		tvUpSpeed = (TextView) findViewById(R.id.wvUpSpeed);
		tvDownSpeed = (TextView) findViewById(R.id.wvDnSpeed);
		tvFilteringBy = (TextView) findViewById(R.id.wvFilteringBy);
		tvTorrentCount = (TextView) findViewById(R.id.wvTorrentCount);
		tvCenter = (TextView) findViewById(R.id.wvCenter);

		filterEditText = (EditText) findViewById(R.id.filterText);
		myWebView = (WebView) findViewById(R.id.webview);

		// register BroadcastReceiver on network state changes
		mConnectivityReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
					return;
				}
				setWifiConnected(AndroidUtils.isWifiConnected(context));
				setOnline(AndroidUtils.isOnline(context));
			}
		};
		setOnline(AndroidUtils.isOnline(getApplicationContext()));
		final IntentFilter mIFNetwork = new IntentFilter();
		mIFNetwork.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(mConnectivityReceiver, mIFNetwork);

		remember = extras.getBoolean("com.vuze.android.remote.remember");
		String remoteAsJSON = extras.getString("remote.json");
		if (remoteAsJSON != null) {
			remoteProfile = new RemoteProfile(JSONUtils.decodeJSON(remoteAsJSON));
		} else {

			String ac = extras.getString("com.vuze.android.remote.ac");
			String user = extras.getString("com.vuze.android.remote.user");

			AppPreferences appPreferences = new AppPreferences(this);
			remoteProfile = appPreferences.getRemote(ac);
			if (remoteProfile == null) {
				remoteProfile = new RemoteProfile(user, ac);
			}
		}
		setTitle(remoteProfile.getNick());

		jsInterface = new JSInterface(this, myWebView, new JSInterfaceListener() {

			public void uiReady() {
				new Thread(new Runnable() {
					public void run() {
						setUIReady();
					}
				}).start();
			}

			@SuppressWarnings("rawtypes")
			@Override
			public void selectionChanged(final List<Map> selectedTorrentFields,
					boolean haveActiveSel, boolean havePausedSel) {
				EmbeddedWebRemote.this.selectedTorrents = selectedTorrentFields;
				EmbeddedWebRemote.this.haveActiveSel = haveActiveSel;
				EmbeddedWebRemote.this.havePausedSel = havePausedSel;

				runOnUiThread(new Runnable() {
					public void run() {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
							selectionChangedHoneyComb(selectedTorrents.size());
						}
					}

					@TargetApi(Build.VERSION_CODES.HONEYCOMB)
					private void selectionChangedHoneyComb(long selectionCount) {
						if (selectionCount == 0) {
							if (mActionMode != null) {
								mActionMode.finish();
							} else {
								supportInvalidateOptionsMenu();
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
			public void updateTorrentStates(boolean haveActive, boolean havePaused,
					boolean haveActiveSel, boolean havePausedSel) {
				EmbeddedWebRemote.this.haveActive = haveActive;
				EmbeddedWebRemote.this.havePaused = havePaused;
				EmbeddedWebRemote.this.haveActiveSel = haveActiveSel;
				EmbeddedWebRemote.this.havePausedSel = havePausedSel;

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					invalidateOptionsMenuHC();
				}
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

			@SuppressWarnings("rawtypes")
			@Override
			public void sessionPropertiesUpdated(Map map) {
				SessionSettings settings = new SessionSettings();
				settings.setDLIsAuto(MapUtils.getMapBoolean(map,
						"speed-limit-down-enabled", true));
				settings.setULIsAuto(MapUtils.getMapBoolean(map,
						"speed-limit-up-enabled", true));
				settings.setDownloadDir(MapUtils.getMapString(map, "download-dir", null));
				long refreshRateSecs = MapUtils.getMapLong(map, "refresh_rate", 0);
				settings.setRefreshIntervalEnabled(refreshRateSecs > 0);
				long profileRefeshInterval = remoteProfile.getUpdateInterval();
				settings.setRefreshInterval(refreshRateSecs == 0
						&& profileRefeshInterval > 0 ? profileRefeshInterval
						: refreshRateSecs);
				settings.setDlSpeed(MapUtils.getMapLong(map, "speed-limit-down", 0));
				settings.setUlSpeed(MapUtils.getMapLong(map, "speed-limit-up", 0));
				EmbeddedWebRemote.this.sessionSettings = settings;

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					invalidateOptionsMenuHC();
				}
			}
		});

		filterEditText.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				String newText = s.toString();
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
			public void onConsoleMessage(String message, int lineNumber,
					String sourceID) {
				// Just in case FROYO and above call this for backwards compat reasons
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
					AndroidUtils.handleConsoleMessageFroyo(EmbeddedWebRemote.this,
							message, sourceID, lineNumber);
				}
			}

			@TargetApi(Build.VERSION_CODES.FROYO)
			public boolean onConsoleMessage(ConsoleMessage cm) {
				AndroidUtils.handleConsoleMessageFroyo(EmbeddedWebRemote.this,
						cm.message(), cm.sourceId(), cm.lineNumber());
				return true;
			}

			// For Android 3.0+
			@SuppressWarnings("unused")
			public void openFileChooser(ValueCallback<Uri> uploadMsg) {
				mUploadMessage = uploadMsg;
				if (DEBUG) {
					System.out.println("3.0+ Upload From Browser");
				}
				AndroidUtils.openFileChooser(EmbeddedWebRemote.this,
						"application/x-bittorrent", FILECHOOSER_RESULTCODE);
			}

			// For Android 3.0+
			@SuppressWarnings("unused")
			public void openFileChooser(ValueCallback<Uri> uploadMsg,
					String acceptType) {
				mUploadMessage = uploadMsg;
				if (DEBUG) {
					System.out.println("3.0+ Upload From Browser: " + acceptType);
				}
				AndroidUtils.openFileChooser(EmbeddedWebRemote.this, acceptType,
						FILECHOOSER_RESULTCODE);
			}

			//For Android 4.1
			@SuppressWarnings("unused")
			public void openFileChooser(ValueCallback<Uri> uploadMsg,
					String acceptType, String capture) {
				mUploadMessage = uploadMsg;
				if (DEBUG) {
					System.out.println("4.1+ Upload From Browser: " + acceptType);
				}
				AndroidUtils.openFileChooser(EmbeddedWebRemote.this, acceptType,
						FILECHOOSER_RESULTCODE);
			}

			@Override
			public boolean onJsAlert(WebView view, String url, String message,
					final JsResult result) {
				AlertDialog show = new AlertDialog.Builder(EmbeddedWebRemote.this).setMessage(
						message).setPositiveButton(android.R.string.ok,
						new OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
							}
						}).show();
				show.setOnDismissListener(new OnDismissListener() {
					public void onDismiss(DialogInterface dialog) {
						result.confirm();
					}
				});
				return true;
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

		if (!isOnline) {
			AndroidUtils.showError(this, R.string.no_network_connection, false);
			return;
		}

		Thread thread = new Thread() {
			public void run() {
				try {
					String host = remoteProfile.getHost();
					if (host != null && host.length() > 0
							&& remoteProfile.getRemoteType() == RemoteProfile.TYPE_NORMAL) {
						open(remoteProfile.getUser(), remoteProfile.getAC(), "http", host,
								remoteProfile.getPort(), remember);
					} else {
						bindAndOpen(remoteProfile.getAC(), remoteProfile.getUser(),
								remember);
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

	private void setUIReady() {
		uiReady = true;
		if (DEBUG) {
			System.out.println("UI READY");
		}
		if (!isOnline) {
			pauseUI();
		}
		String dataString = getIntent().getDataString();
		if (dataString != null) {
			openTorrent(getIntent().getData());
		}

		String sortBy = remoteProfile.getSortBy();
		if (sortBy != null) {
			sortBy(sortBy, false);
		}

		String filterBy = remoteProfile.getFilterBy();
		if (filterBy != null) {
			String[] valuesArray = getResources().getStringArray(
					R.array.filterby_list_values);
			String[] stringArray = getResources().getStringArray(
					R.array.filterby_list);
			for (int i = 0; i < valuesArray.length; i++) {
				String value = valuesArray[i];
				if (value.equals(filterBy)) {
					filterBy(filterBy, stringArray[i], false);
					break;
				}
			}
		}
		boolean isUpdateIntervalEnabled = remoteProfile.isUpdateIntervalEnabled();
		long interval = remoteProfile.getUpdateInterval();
		if (sessionSettings != null) {
			sessionSettings.setRefreshIntervalEnabled(isUpdateIntervalEnabled);
			if (interval >= 0) {
				sessionSettings.setRefreshInterval(interval);
			}
		}
		if (!isUpdateIntervalEnabled) {
			interval = 0;
		}
		if (interval >= 0) {
			runJavaScript("setRefreshInterval",
					"transmission.setPref(Prefs._RefreshRate, " + interval + ");"
							+ (interval > 0 ? "transmission.refreshTorrents();" : ""));
		}

		runOnUiThread(new Runnable() {
			public void run() {
				tvCenter.setText("");
			}
		});
	}

	@Override
	protected void onStart() {
		super.onStart();
		VuzeEasyTracker.getInstance(this).activityStart(this);
	}

	protected void setWifiConnected(boolean wifiConnected) {
		if (this.wifiConnected == wifiConnected) {
			return;
		}
		this.wifiConnected = wifiConnected;
	}

	protected void setOnline(boolean isOnline) {
		if (DEBUG) {
			System.out.println("set Online to " + isOnline);
		}
		if (this.isOnline == isOnline) {
			return;
		}
		this.isOnline = isOnline;
		runOnUiThread(new Runnable() {
			@SuppressLint("NewApi")
			public void run() {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					invalidateOptionsMenu();
				}
				if (EmbeddedWebRemote.this.isOnline) {
					tvCenter.setText("");
					resumeUI();
				} else {
					tvCenter.setText(R.string.no_network_connection);
					pauseUI();
				}
			}
		});
	}

	@Override
	protected void onNewIntent(Intent intent) {
		// Called via MetaSearch
		openTorrent(intent.getData());
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected void invalidateOptionsMenuHC() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				supportInvalidateOptionsMenu();
			}
		});
	}

	@SuppressLint("NewApi")
	@Override
	public void invalidateOptionsMenu() {
		if (mSearchView != null) {
			searchIsIconified = mSearchView.isIconified();
		}
		super.invalidateOptionsMenu();
	}

	@SuppressWarnings("null")
	protected void bindAndOpen(final String ac, final String user,
			boolean remember) {

		RPC rpc = new RPC();
		try {
			Map<?, ?> bindingInfo = rpc.getBindingInfo(ac);

			Map<?, ?> error = MapUtils.getMapMap(bindingInfo, "error", null);
			if (error != null) {
				String errMsg = MapUtils.getMapString(error, "msg", "Unknown Error");
				if (DEBUG) {
					System.out.println("Error from getBindingInfo " + errMsg);
				}

				AndroidUtils.showError(this, errMsg, false);
				return;
			}

			String host = MapUtils.getMapString(bindingInfo, "ip", null);
			String protocol = MapUtils.getMapString(bindingInfo, "protocol", null);
			int port = Integer.valueOf(MapUtils.getMapString(bindingInfo, "port", "0"));

			if (DEBUG) {
				if (host == null) {
					//ip = "192.168.2.59";
					host = "192.168.1.2";
					protocol = "http";
					port = 9092;
				}
			}

			if (host != null && protocol != null) {
				remoteProfile.setHost(host);
				remoteProfile.setPort(port);
				open("vuze", ac, protocol, host, port, remember);
			}
		} catch (final RPCException e) {
			VuzeEasyTracker.getInstance(this).logError(this, e);
			AndroidUtils.showError(EmbeddedWebRemote.this, e.getMessage(), false);
			if (DEBUG) {
				e.printStackTrace();
			}
		}
	}

	@SuppressLint("NewApi")
	private void open(String user, final String ac, String protocol, String host,
			int port, boolean remember) {
		try {
			String up = user + ":" + ac;

			rpcRoot = protocol + "://" + host + ":" + port + "/";
			String rpcUrl = rpcRoot + "transmission/rpc";

			if (!isURLAlive(rpcUrl)) {
				AndroidUtils.showError(this, R.string.error_remote_not_found, false);
				return;
			}

			AppPreferences appPreferences = new AppPreferences(this);
			remoteProfile.setLastUsedOn(System.currentTimeMillis());
			if (remember) {
				appPreferences.setLastRemote(ac);
				appPreferences.addRemoteProfile(remoteProfile);
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
				VuzeEasyTracker.getInstance(this).logError(this, e);
			}

			if (DEBUG) {
				System.out.println("rpc root = " + rpcRoot);
			}
			jsInterface.setRemoteProfile(remoteProfile);
			jsInterface.setRpcRoot(rpcRoot);

			runOnUiThread(new Runnable() {
				public void run() {
					// Android API 11-15 doesn't support url parameters on local files.  We
					// hack it into userAgent :)
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
							&& Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
						WebSettings webSettings = myWebView.getSettings();
						webSettings.setUserAgentString(remoteParams.replaceAll("[\r\n]", ""));
						myWebView.loadUrl(remoteUrl);
					} else {
						myWebView.loadUrl(remoteUrl + remoteParams);
					}
				}
			});
		} catch (UnsupportedEncodingException e) {
			VuzeEasyTracker.getInstance(this).logError(this, e);
			if (DEBUG) {
				e.printStackTrace();
			}
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
		}
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
			if (DEBUG) {
				System.err.println("actionBar is null");
			}
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
				menuMove.setEnabled(selectedTorrents.size() == 1);

				MenuItem menuStart = menu.findItem(R.id.action_sel_start);
				menuStart.setVisible(havePausedSel);

				MenuItem menuStop = menu.findItem(R.id.action_sel_stop);
				menuStop.setVisible(haveActiveSel);

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
			if (DEBUG) {
				System.out.println("CANCELLED");
			}
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
				if (DEBUG) {
					System.out.println("tryAquire timeout");
				}
			}
		} catch (InterruptedException e) {
			if (DEBUG) {
				e.printStackTrace();
			}
			VuzeEasyTracker.getInstance(this).logError(this, e);
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
		if (!AndroidUtils.areWebViewsPaused() && myWebView != null) {
			if (DEBUG) {
				System.out.println("EWR Pause");
			}
			//myWebView.pauseTimers();
			//AndroidUtils.setWebViewsPaused(true);
		}
		if (uiReady) {
			runJavaScript("pauseUI", "transmission.pauseUI();");
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		resumeUI();
	}

	private void resumeUI() {
		if (AndroidUtils.areWebViewsPaused() && myWebView != null && isOnline) {
			if (DEBUG) {
				System.out.println("EWR resume");
			}
			//myWebView.resumeTimers();
			//AndroidUtils.setWebViewsPaused(false);
		}
		if (uiReady) {
			runJavaScript("resumeUI", "transmission.resumeUI();");
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (DEBUG) {
			System.out.println("EWR STOP");
		}
		VuzeEasyTracker.getInstance(this).activityStop(this);
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

		if (mConnectivityReceiver != null) {
			unregisterReceiver(mConnectivityReceiver);
			mConnectivityReceiver = null;
		}

		super.onDestroy();
		if (DEBUG) {
			System.out.println("EWR onDestroy");
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.DialogFragmentOpenTorrent.OpenTorrentDialogListener#openTorrent(java.lang.String)
	 */
	public void openTorrent(String s) {
		if (s == null) {
			return;
		}
		runJavaScript("openTorrent", "transmission.remote.addTorrentByUrl('"
				+ quoteIt(s) + "', false)");
		EasyTracker.getInstance(this).send(
				MapBuilder.createEvent("RemoteAction", "AddTorrent", "AddTorrentByUrl",
						null).build());
	}

	public String quoteIt(String s) {
		return s.replaceAll("'", "\\'").replaceAll("\\\\", "\\\\\\\\");
	}

	@SuppressLint("NewApi")
	public void openTorrent(InputStream is) {
		try {
			byte[] bs = readInputStreamAsByteArray(is);
			String metainfo = Base64.encodeToString(bs, Base64.DEFAULT).replaceAll(
					"[\\r\\n]", "");
			runJavaScript("openTorrent", "transmission.remote.addTorrentByMetainfo('"
					+ metainfo + "')");
		} catch (IOException e) {
			if (DEBUG) {
				e.printStackTrace();
			}
			VuzeEasyTracker.getInstance(this).logError(this, e);
		}
		EasyTracker.getInstance(this).send(
				MapBuilder.createEvent("remoteAction", "AddTorrent",
						"AddTorrentByMeta", null).build());
	}

	@Override
	public void openTorrent(Uri uri) {
		if (DEBUG) {
			System.out.println("openTorernt " + uri);
		}
		if (uri == null) {
			return;
		}
		String scheme = uri.getScheme();
		if (DEBUG) {
			System.out.println("openTorernt " + scheme);
		}
		if ("file".equals(scheme) || "content".equals(scheme)) {
			try {
				InputStream stream = getContentResolver().openInputStream(uri);
				openTorrent(stream);
			} catch (FileNotFoundException e) {
				if (DEBUG) {
					e.printStackTrace();
				}
				VuzeEasyTracker.getInstance(this).logError(this, e);
			}
		} else {
			openTorrent(uri.toString());
		}
	}

	private void runJavaScript(final String id, final String js) {
		if (id == null || js == null) {
			return;
		}
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
					InputMethodManager mgr = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
					mgr.showSoftInput(filterEditText, InputMethodManager.SHOW_IMPLICIT);
					EasyTracker.getInstance(EmbeddedWebRemote.this).send(
							MapBuilder.createEvent("uiAction", "ViewShown", "FilterBox", null).build());
				} else {
					InputMethodManager mgr = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
					mgr.hideSoftInputFromWindow(filterEditText.getWindowToken(), 0);
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

			case R.id.action_start_all:
				runJavaScript("startAll", "transmission.startAllTorrents(false);");
				return true;

			case R.id.action_stop_all:
				runJavaScript("stopAll", "transmission.stopAllTorrents();");
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
				openMoveDataDialog();
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

	@SuppressWarnings("rawtypes")
	private void openMoveDataDialog() {
		if (selectedTorrents.size() == 0) {
			return;
		}
		DialogFragmentMoveData dlg = new DialogFragmentMoveData();
		Bundle bundle = new Bundle();
		Map mapTorrent = selectedTorrents.get(0);
		bundle.putString("id", "" + mapTorrent.get("id"));
		bundle.putString("name", "" + mapTorrent.get("name"));

		String defaultDownloadDir = sessionSettings.getDownloadDir();
		String downloadDir = MapUtils.getMapString(mapTorrent, "downloadDir",
				defaultDownloadDir);
		bundle.putString("downloadDir", downloadDir);
		ArrayList<String> history = new ArrayList<String>();
		if (defaultDownloadDir != null) {
			history.add(defaultDownloadDir);
		}

		List<String> saveHistory = remoteProfile.getSavePathHistory();
		for (String s : saveHistory) {
			if (!history.contains(s)) {
				history.add(s);
			}
		}
		bundle.putStringArrayList("history", history);
		dlg.setArguments(bundle);
		dlg.show(getSupportFragmentManager(), "MoveDataDialog");
	}

	private void showSessionSettings() {
		if (sessionSettings == null) {
			return;
		}
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

	private void fixupMenu(Menu menu) {
		for (int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			Drawable icon = item.getIcon();
			if (icon != null) {
				icon.setAlpha(item.isEnabled() ? 255 : 64);
			}
		}
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
		menuContext.setVisible(selectedTorrents.size() > 0);

		MenuItem menuSearch = menu.findItem(R.id.action_search);
		menuSearch.setEnabled(isOnline);

		fixupMenu(menu);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void filterBy(String filterMode, final String name, boolean save) {
		runJavaScript("filterText", "transmission.setFilterMode(" + filterMode
				+ ");");
		runOnUiThread(new Runnable() {
			public void run() {
				tvFilteringBy.setText(name);
			}
		});
		if (save) {
			remoteProfile.setFilterBy(filterMode);
			saveProfileIfRemember();
		}
	}

	private void saveProfileIfRemember() {
		if (remember) {
			AppPreferences appPreferences = new AppPreferences(this);
			appPreferences.addRemoteProfile(remoteProfile);
		}
	}

	@Override
	public void sortBy(String sortType, boolean save) {
		runJavaScript("sortBy", "transmission.setSortMethod(" + sortType + ");");
		if (save) {
			remoteProfile.setSortBy(sortType);
			saveProfileIfRemember();
		}
	}

	@Override
	public void flipSortOrder() {
		runJavaScript(
				"flipSort",
				"if (transmission[Prefs._SortDirection] === Prefs._SortDescending) transmission.setSortDirection(Prefs._SortAscending); else transmission.setSortDirection(Prefs._SortDescending);");
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupSearchView(MenuItem searchItem) {
		mSearchView = (SearchView) searchItem.getActionView();
		if (mSearchView == null) {
			return;
		}

		SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		if (searchManager != null) {
			mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
		}
		mSearchView.setIconifiedByDefault(true);
		mSearchView.setIconified(searchIsIconified);
		mSearchView.setQueryHint(getResources().getString(R.string.search_box_hint));
		mSearchView.setOnQueryTextListener(new OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				jsInterface.executeSearch(query);
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				return false;
			}
		});
	}

	@Override
	public boolean onSearchRequested() {
		Bundle appData = new Bundle();
		appData.putString("com.vuze.android.remote.searchsource", rpcRoot);
		appData.putString("com.vuze.android.remote.ac", remoteProfile.getAC());
		startSearch(null, false, appData, false);
		return true;
	}

	private static byte[] readInputStreamAsByteArray(InputStream is)
			throws IOException {
		int available = is.available();
		if (available <= 0) {
			available = 32 * 1024;
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream(available);

		byte[] buffer = new byte[32 * 1024];

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
		if (newSettings.isRefreshIntervalIsEnabled() != sessionSettings.isRefreshIntervalIsEnabled()
				|| newSettings.getRefreshInterval() != sessionSettings.getRefreshInterval()) {
			long interval = newSettings.getRefreshInterval();

			if (!newSettings.isRefreshIntervalIsEnabled()) {
				interval = 0;
			}
			runJavaScript("setRefreshInterval",
					"transmission.setPref(Prefs._RefreshRate, " + interval + ");"
							+ (interval > 0 ? "transmission.refreshTorrents();" : ""));

			remoteProfile.setUpdateInterval(newSettings.getRefreshInterval());
			remoteProfile.setUpdateIntervalEnabled(newSettings.isRefreshIntervalIsEnabled());
			saveProfileIfRemember();
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

	@Override
	public void moveDataTo(String id, String s) {
		runJavaScript("moveData", "transmission.remote.moveTorrents([" + id
				+ "], '" + quoteIt(s)
				+ "', transmission.refreshTorrents, transmission);");
		EasyTracker.getInstance(this).send(
				MapBuilder.createEvent("RemoteAction", "MoveData", null, null).build());
	}

	@Override
	public void moveDataHistoryChanged(ArrayList<String> history) {
		if (remoteProfile == null) {
			return;
		}
		remoteProfile.setSavePathHistory(history);
		saveProfileIfRemember();
	}
}
