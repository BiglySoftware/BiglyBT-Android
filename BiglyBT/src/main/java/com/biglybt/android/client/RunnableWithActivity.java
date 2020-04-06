package com.biglybt.android.client;

import android.app.Activity;

/**
 * Created by TuxPaper on 9/12/18.
 */
public interface RunnableWithActivity<T extends Activity> {
	void run(T activity);
}
