// IBiglyCoreInterface.aidl
package com.biglybt.android.client;

import com.biglybt.android.client.IBiglyCoreCallback;

interface IBiglyCoreInterface {

    boolean addListener(in IBiglyCoreCallback callback);

    boolean removeListener(in IBiglyCoreCallback callback);

    void startCore();
    
    int getParamInt(in String key);    
    boolean setParamInt(in String key, in int val);

    long getParamLong(in String key);
    boolean setParamLong(in String key, in long val);

    float getParamFloat(in String key);
    boolean setParamFloat(in String key, in float val);

    String getParamString(in String key);
    boolean setParamString(in String key, in String val);

    boolean getParamBool(in String key);
    boolean setParamBool(in String key, in boolean val);
}
