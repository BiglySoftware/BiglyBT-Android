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

package com.biglybt.android.client;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.StrictMode;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.biglybt.android.util.JSONUtils;
import com.biglybt.android.util.MapUtils;
import com.biglybt.util.Thunk;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

/**
 * Bare Analytics Tracker that only tracks crashes and startup event.
 * Events sent to biglybt's server (no Google Analytics)
 */
public class AnalyticsTrackerBare
	implements IAnalyticsTracker
{
	private static final String TAG = "ATbare";

	private static final String RPC_URL = "https://rpc.biglybt.com/rpc.php";

	private static final String LOG_MSG_ERROR = "logError";

	private static final String KEY_LOCALE = "locale";

	private static final String KEY_APP_VERSION = "app-version";

	private static final String KEY_DEVICE_TYPE = "device-type";

	private static final String KEY_SCREEN_DENSITY = "screen-density";

	private static final String KEY_SCREEN_INCHES = "screen-inches";

	private static final String KEY_DEVICE_NAME = "device-name";

	private static final String KEY_ANDROID_VERSION = "android-version";

	private static final String KEY_EXCEPTION_NAME = "exception-name";

	private static final String KEY_IS_CRASH = "is-crash";

	private static final String KEY_STACK = "stack";

	private static final String KEY_THREAD = "thread";

	private static final String KEY_RPC_VERSION = "rpc-version";

	private static final String KEY_CLIENT_VERSION = "client-version";

	private static final String KEY_CLIENT_TYPE = "client-type";

	private static final String KEY_LISTENERID_ANDROID = "android";

	private static final String KEY_OPID_CRASH = "crash";

	private static final String KEY_EVENT = "event";

	private static final String KEY_OPID_LOG = "log";

	private static final String KEY_LISTENER_ID = "listener-id";

	private static final String KEY_OP_ID = "op-id";

	private static final String KEY_SEQ_ID = "seq-id";

	private static final String KEY_VALUES = "values";

	private static final String KEY_COMMANDS = "commands";

	private static final String PARAM_PAYLOAD = "payload";

	private static final String KEY_VIEW_NAME = "v";

	private int densityDpi;

	private String deviceType;

	private String deviceName;

	private String clientVersion;

	private String rpcVersion;

	private String remoteTypeName;

	private double screenInches;

	private String lastViewName;

	protected AnalyticsTrackerBare() {
	}

	@Override
	public void setLastViewName(String lastViewName) {
		this.lastViewName = lastViewName;
	}

	@Override
	public void activityResume(Activity activity) {
		lastViewName = activity == null ? "" : activity.getClass().getSimpleName();
	}

	@Override
	public void fragmentResume(@NonNull Fragment fragment) {
		lastViewName = fragment.getClass().getSimpleName();
		if (fragment instanceof DialogFragment) {
			FragmentActivity activity = fragment.getActivity();

			if (activity != null) {
				lastViewName += "/" + activity.getClass().getSimpleName();
			}
		}
	}

	@Override
	public void activityPause(Activity activity) {
	}

	@Override
	public void fragmentPause(Fragment fragment) {
	}

	@Override
	public void logError(String s, String page) {
		try {
			logCrash(false, "Screen: " + page, s, Thread.currentThread().getName());
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, LOG_MSG_ERROR, t);
			}
		}
	}

	@Override
	public void logError(Throwable e) {
		try {
			logCrash(false, toString(e),
					e == null ? "" : AndroidUtils.getCompressedStackTrace(e, 12),
					Thread.currentThread().getName());
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, LOG_MSG_ERROR, t);
			}
		}
	}

	private static String getCompressedStackTrace(Throwable t) {
		return AndroidUtils.getCompressedStackTrace(t, 12).replace(".java", "");
	}

	private static String toString(Throwable t) {
		if (t == null) {
			return "";
		}
		try {
			String s = t.getClass().getSimpleName();
			String msg = t.getMessage();
			if ((t instanceof RuntimeException) && msg != null) {
				// Despite what "Remove Redundant Escape" says, \\} is needed
				// otherwise PatternSyntaxException
				msg = msg.replaceAll("ProcessRecord\\{[^}]+\\}", "<PR>");
				msg = msg.replaceAll("pid=[0-9]+", "");
				msg = msg.replaceAll("uid[= ][0-9]+", "");
				msg = msg.replaceAll("content://[^ ]+", "<content uri>");
				s += ": " + msg;
			}
			return s;
		} catch (Throwable uhoh) {
			if (AndroidUtils.DEBUG) {
				uhoh.printStackTrace();
			}
		}
		return "" + t;
	}

	@Override
	public void logError(Throwable e, String extra) {
		try {
			String s = toString(e);
			if (extra != null) {
				s += "[" + extra + "]";
			}
			logCrash(false, s, e == null ? "" : getCompressedStackTrace(e),
					Thread.currentThread().getName());

		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, LOG_MSG_ERROR, t);
			}
		}
	}

	@Override
	public void logError(@NonNull Throwable t,
			@NonNull StackTraceElement[] stackTrace) {
		try {
			String s = toString(t);

			String threadStack = AndroidUtils.getCompressedStackTrace(stackTrace,
					null, 12).replace(".java", "");

			String stack = getCompressedStackTrace(t) + "\n|via " + threadStack;

			logCrash(false, s, stack, Thread.currentThread().getName());

		} catch (Throwable t2) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, LOG_MSG_ERROR, t2);
			}
		}
	}

	@Override
	public void logErrorNoLines(Throwable e) {
		logCrash(false, toString(e), AndroidUtils.getCauses(e),
				Thread.currentThread().getName());
	}

	@Override
	public void registerExceptionReporter(Context applicationContext) {
		try {
			Thread.UncaughtExceptionHandler uncaughtExceptionHandler = (t,
					e) -> logCrash(true, toString(e), getCompressedStackTrace(e),
							"*" + t.getName());
			Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
		} catch (Throwable t) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "registerExceptionReporter", t);
			}
		}
	}

	@Override
	public void setClientVersion(String clientVersion) {
		this.clientVersion = clientVersion;
	}

	@Override
	public void setDensity(int densityDpi) {
		this.densityDpi = densityDpi;
	}

	@Override
	public void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
	}

	@Override
	public void setDeviceType(String deviceType) {
		this.deviceType = deviceType;
	}

	@Override
	public void setRPCVersion(String rpcVersion) {
		this.rpcVersion = rpcVersion;
	}

	@Override
	public void setRemoteTypeName(String remoteTypeName) {
		this.remoteTypeName = remoteTypeName;
	}

	@Override
	public void setScreenInches(double screenInches) {
		this.screenInches = screenInches;
	}

	@Override
	public void stop() {

	}

	@Override
	public void sendEvent(String category, String action, String label,
			Long value) {
	}

	private Map createBasicMap() {
		Map mapPayLoad = new HashMap();

		mapPayLoad.put(KEY_LOCALE, Locale.getDefault().toString());
		mapPayLoad.put(KEY_APP_VERSION, BuildConfig.VERSION_CODE);
		mapPayLoad.put(KEY_DEVICE_TYPE, deviceType);
		mapPayLoad.put(KEY_SCREEN_DENSITY, densityDpi);
		mapPayLoad.put(KEY_SCREEN_INCHES, screenInches);
		mapPayLoad.put(KEY_DEVICE_NAME, deviceName);
		mapPayLoad.put(KEY_ANDROID_VERSION, Build.VERSION.SDK_INT);
		if (lastViewName != null) {
			mapPayLoad.put(KEY_VIEW_NAME, lastViewName);
		}

		return mapPayLoad;
	}

	@Thunk
	void logCrash(boolean isCrash, String exceptionName, String stack,
			String threadName) {
		if (AndroidUtils.DEBUG) {
			Log.println(isCrash ? Log.ERROR : Log.WARN, "CRASH",
					"[" + threadName + "] " + exceptionName + ": " + stack);
		}
		Map map = createBasicMap();

		map.put(KEY_EXCEPTION_NAME, exceptionName);
		map.put(KEY_IS_CRASH, isCrash ? 1 : 0);
		map.put(KEY_STACK, stack);
		map.put(KEY_THREAD, threadName);

		map.put(KEY_RPC_VERSION, rpcVersion);
		map.put(KEY_CLIENT_VERSION, clientVersion);
		map.put(KEY_CLIENT_TYPE, remoteTypeName);

		if ("OutOfMemory".equals(exceptionName)) {
			map.put(KEY_VIEW_NAME, MapUtils.getMapString(map, KEY_VIEW_NAME, "") + ";"
					+ BiglyBTApp.lastMemoryLevel);
		}

		log(KEY_LISTENERID_ANDROID, KEY_OPID_CRASH, map, !isCrash);
	}

	@Override
	public void logEvent(String event) {
		Map map = createBasicMap();
		map.put(KEY_EVENT, event);

		log(KEY_LISTENERID_ANDROID, KEY_OPID_LOG, map, true);
	}

	private static void log(String listenerID, String opID, Map mapValues,
			boolean async) {
		Map mapCommand = new HashMap();
		mapCommand.put(KEY_LISTENER_ID, listenerID);
		mapCommand.put(KEY_OP_ID, opID);
		mapCommand.put(KEY_SEQ_ID, 1);
		mapCommand.put(KEY_VALUES, mapValues);

		List listCommands = new ArrayList();
		listCommands.add(mapCommand);

		Map mapPayLoad = new HashMap();
		mapPayLoad.put(KEY_COMMANDS, listCommands);

		try {

			OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
			clientBuilder.retryOnConnectionFailure(true).connectTimeout(15,
					TimeUnit.SECONDS).readTimeout(120L, TimeUnit.SECONDS).writeTimeout(
							15L, TimeUnit.SECONDS);
			OkHttpClient client = clientBuilder.build();

			Request.Builder builder = new Request.Builder().url(
					RPC_URL + "?rnd=" + Math.random()) //NON-NLS
					.header("User-Agent", AndroidUtils.BIGLYBT_USERAGENT); //NON-NLS

			String payloadString = JSONUtils.encodeToJSON(mapPayLoad);
			RequestBody requestBody = new MultipartBody.Builder().setType(
					MultipartBody.FORM).addFormDataPart(PARAM_PAYLOAD,
							payloadString).build();

			if (AndroidUtils.DEBUG_RPC) {
				Log.d(TAG, payloadString);
			}

			builder.post(requestBody);

			Request request = builder.build();
			Call call = client.newCall(request);
			if (async) {
				call.enqueue(new Callback() {
					@Override
					public void onFailure(Call call, IOException e) {
						if (AndroidUtils.DEBUG_RPC) {
							Log.e(TAG, "Async Fail", e);
						}
					}

					@Override
					public void onResponse(Call call, Response response) {
						int statusCode = response.code();

						try {
							ResponseBody body = response.body();
							// fix     java.lang.Throwable: Explicit termination method 'response.body().close()' not called
							if (body != null) {
								body.close();
							}

							if (AndroidUtils.DEBUG_RPC) {
								if (statusCode != 200) {
									Log.d(TAG, "Async StatusCode: " + statusCode);
								} else {
									Log.d(TAG, "Async Response: "
											+ (body == null ? "null" : body.string()));
								}
							}
						} catch (Throwable ignore) {

						}

					}
				});
			} else {
				StrictMode.ThreadPolicy old = enableNasty();
				Response response = call.execute();
				revertNasty(old);
				int statusCode = response == null ? 0 : response.code();

				if (AndroidUtils.DEBUG_RPC) {
					if (statusCode != 200) {
						Log.d(TAG, "Sync StatusCode: " + statusCode);
					} else {
						ResponseBody body = response.body();
						Log.d(TAG,
								"Sync Response: " + (body == null ? "null" : body.string()));
					}
				}
			}

		} catch (Throwable e) {
			if (AndroidUtils.DEBUG) {
				Log.e(TAG, "log", e);
			}
		}
	}

	private static StrictMode.ThreadPolicy enableNasty() {
		StrictMode.ThreadPolicy oldThreadPolicy = StrictMode.getThreadPolicy();
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
		StrictMode.setThreadPolicy(policy);
		return oldThreadPolicy;
	}

	private static void revertNasty(@Nullable StrictMode.ThreadPolicy oldPolicy) {
		if (oldPolicy == null) {
			return;
		}
		StrictMode.setThreadPolicy(oldPolicy);
	}
}
