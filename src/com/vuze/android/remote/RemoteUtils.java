package com.vuze.android.remote;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

import android.app.Activity;
import android.content.Intent;
import android.util.Base64;

public class RemoteUtils
{
	private Activity activity;
	private AppPreferences appPreferences;

	public RemoteUtils(Activity activity) {
		this.activity = activity;
		this.appPreferences = new AppPreferences(activity);
	}
	
	public RemoteUtils(Activity activity, AppPreferences appPreferences) {
		this.activity = activity;
		this.appPreferences = appPreferences;
	}



	public void openRemote(final String user, final String ac,
			final boolean remember) {
		System.out.println("openRemote " + ac);
		Thread thread = new Thread() {
			public void run() {

				RPC rpc = new RPC();
				try {
					Map bindingInfo = rpc.getBindingInfo(ac);
					String ip = (String) bindingInfo.get("ip");
					String protocol = (String) bindingInfo.get("protocol");
					String port = (String) bindingInfo.get("port");

					if (ip == null) {
						ip = "192.168.2.59";
						ip = "192.168.1.2";
						protocol = "http";
						port = "9092";
					}

					if (ip != null && protocol != null && port != null) {

						if (remember) {
							RemotePreferences remotePreferences = new RemotePreferences(user, ac);
							remotePreferences.setLastUsedOn(System.currentTimeMillis());
							
							appPreferences.addRemotePref(remotePreferences);
							appPreferences.setLastRemote(ac);
						}

						String up = "vuze:" + ac;

						String rpcRoot = protocol + "://" + ip + ":" + port + "/";
						String rpcUrl = rpcRoot + "transmission/rpc";

						String urlEncoded = URLEncoder.encode(rpcUrl, "utf-8");
						String acEnc = URLEncoder.encode(ac, "utf-8");
						
						String basicAuth = Base64.encodeToString(up.getBytes("utf-8"), 0);

						String remoteUrl = "file:///android_asset/transmission/web/index.html?vuze_pairing_ac="
								+ acEnc
								+ "&_Root="
								+ urlEncoded
								+ "&_BasicAuth=" + basicAuth;
						//remoteUrl = protocol + "://" + ip + ":" + port;

						Intent myIntent = new Intent(activity.getIntent());
						myIntent.setAction(Intent.ACTION_VIEW);
						myIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
						//myIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
						myIntent.setClassName("com.vuze.android.remote",
								"com.vuze.android.remote.EmbeddedWebRemote");
						myIntent.putExtra("com.vuze.android.rpc.basicAuth", basicAuth);
						myIntent.putExtra("com.vuze.android.rpc.root", rpcRoot);
						myIntent.putExtra("com.vuze.android.rpc.url", rpcUrl);
						myIntent.putExtra("com.vuze.android.remote.url", remoteUrl); // key/value pair, where key needs current package prefix.
						myIntent.putExtra("com.vuze.android.remote.ac", ac);
						myIntent.putExtra("com.vuze.android.remote.name", ac);
						activity.startActivity(myIntent);
					}
				} catch (RPCException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		thread.setDaemon(true);
		thread.start();
	}

}
