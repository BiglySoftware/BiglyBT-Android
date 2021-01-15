// IBiglyCoreCallback.aidl
package com.biglybt.android.client;

interface IBiglyCoreCallback {
	void onCoreEvent(in int event, in Map data);
}
