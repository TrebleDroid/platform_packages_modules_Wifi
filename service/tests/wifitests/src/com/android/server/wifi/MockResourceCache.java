/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import android.content.Context;
import android.net.wifi.util.WifiResourceCache;

import java.util.HashMap;

public class MockResourceCache extends WifiResourceCache {

    private final HashMap<Integer, Boolean> mBooleanValues;
    private final HashMap<Integer, Integer> mIntegerValues;
    private final HashMap<Integer, String>  mStringValues;
    private final HashMap<Integer, int[]> mIntArrayValues;
    private final HashMap<Integer, String[]> mStringArrayValues;

    public MockResourceCache(Context context) {
        super(context);
        mBooleanValues = new HashMap<Integer, Boolean>();
        mIntegerValues = new HashMap<Integer, Integer>();
        mStringValues  = new HashMap<Integer, String>();
        mIntArrayValues = new HashMap<Integer, int[]>();
        mStringArrayValues = new HashMap<Integer, String[]>();
    }

    @Override
    public boolean getBoolean(int id) {
        return mBooleanValues.getOrDefault(id, false);
    }

    @Override
    public int getInteger(int id) {
        return mIntegerValues.getOrDefault(id, 0);
    }

    @Override
    public String getString(int id) {
        return mStringValues.getOrDefault(id, null);
    }

    @Override
    public int[] getIntArray(int id) {
        return mIntArrayValues.getOrDefault(id, null);
    }

    @Override
    public String[] getStringArray(int id) {
        return mStringArrayValues.getOrDefault(id, null);
    }

    public void setBoolean(int id, boolean value) {
        mBooleanValues.put(id, value);
    }

    public void setInteger(int id, int value) {
        mIntegerValues.put(id, value);
    }

    public void setString(int id, String value) {
        mStringValues.put(id, value);
    }

    public void setIntArray(int id, int[] value) {
        mIntArrayValues.put(id, value);
    }

    public void setStringArray(int id, String[] value) {
        mStringArrayValues.put(id, value);
    }
}
