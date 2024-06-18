/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.net.wifi.util;

import android.content.Context;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * A class to cache the Wifi resource value and provide override mechanism from shell
 * @hide
 */
public class WifiResourceCache {
    private final Context mContext;

    private final Map<String, Boolean> mBooleanResourceMap;
    private final Map<String, Integer> mIntegerResourceMap;

    public WifiResourceCache(Context context) {
        mContext = context;
        mBooleanResourceMap = new HashMap<>();
        mIntegerResourceMap = new HashMap<>();
    }

    /**
     * Get and cache the boolean value as {@link android.content.res.Resources#getBoolean(int)}
     */
    public boolean getBoolean(int resourceId, String resourceName) {
        return mBooleanResourceMap.computeIfAbsent(resourceName,
                v -> mContext.getResources().getBoolean(resourceId));
    }

    /**
     * Get and cache the integer value as {@link android.content.res.Resources#getInteger(int)}
     */
    public int getInteger(int resourceId, String resourceName) {
        return mIntegerResourceMap.computeIfAbsent(resourceName,
                v -> mContext.getResources().getInteger(resourceId));
    }

    /**
     * Override the target boolean value
     *
     * @param resourceName the resource overlay name
     * @param value        override to this value
     */
    public void overrideBooleanValue(String resourceName, boolean value) {
        mBooleanResourceMap.put(resourceName, value);
    }

    /**
     * Override the target boolean value
     */
    public void restoreBooleanValue(String resourceName) {
        mBooleanResourceMap.remove(resourceName);
    }

    /**
     * Override the target integer value
     * @param resourceName the resource overlay name
     * @param value override to this value
     */
    public void overrideIntegerValue(String resourceName, int value) {
        mIntegerResourceMap.put(resourceName, value);
    }

    /**
     * Override the target integer value
     */
    public void restoreIntegerValue(String resourceName) {
        mIntegerResourceMap.remove(resourceName);
    }

    /**
     * Dump of current resource value
     */
    public void dump(PrintWriter pw) {
        pw.println("Dump of WifiResourceCache");
        pw.println("WifiResourceCache - resource value Begin ----");

        for (Map.Entry<String, Integer> resourceEntry : mIntegerResourceMap.entrySet()) {
            pw.println("Resource Name: " + resourceEntry.getKey()
                    + ", value: " + resourceEntry.getValue());
        }
        for (Map.Entry<String, Boolean> resourceEntry : mBooleanResourceMap.entrySet()) {
            pw.println("Resource Name: " + resourceEntry.getKey()
                    + ", value: " + resourceEntry.getValue());
        }
        pw.println("WifiResourceCache - resource value End ----");
    }

    /**
     * Remove all override value and set to default
     */
    public void reset() {
        mBooleanResourceMap.clear();
        mIntegerResourceMap.clear();
    }
}
