package com.biglybt.android.client;

import android.app.Activity;

import androidx.annotation.UiThread;

/**
 * Created by TuxPaper on 9/12/18.
 */
public interface RunnableWithActivity<T extends Activity> {
	@UiThread
	void run(T activity);
}
