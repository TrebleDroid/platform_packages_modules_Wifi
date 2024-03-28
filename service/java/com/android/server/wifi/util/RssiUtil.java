/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.server.wifi.util;

import android.annotation.NonNull;
import android.content.Context;
import android.net.wifi.WifiInfo;

import com.android.wifi.resources.R;

/** Utilities for computations involving RSSI. */
public class RssiUtil {
    private RssiUtil() {}

    /**
     * Apply known fixes to a RSSi from signal poll and return INVALID_RSSI to indicate the data
     * is invalid.
     */
    public static int calculateAdjustedRssi(int rssi) {
        if (rssi > WifiInfo.INVALID_RSSI && rssi < WifiInfo.MAX_RSSI) {
            /*
             * Positive RSSI is possible when devices are close(~0m apart) to each other.
             * And there are some driver/firmware implementation, where they avoid
             * reporting large negative rssi values by adding 256.
             * so adjust the valid rssi reports for such implementations.
             */
            if (rssi > (WifiInfo.INVALID_RSSI + 256)) {
                return rssi - 256;
            }
            return rssi;
        }
        return WifiInfo.INVALID_RSSI;
    }

    /** Calculate RSSI level from RSSI using overlaid RSSI level thresholds. */
    public static int calculateSignalLevel(Context context, int rssi) {
        int[] thresholds = getRssiLevelThresholds(context);

        for (int level = 0; level < thresholds.length; level++) {
            if (rssi < thresholds[level]) {
                return level;
            }
        }
        return thresholds.length;
    }

    @NonNull
    private static int[] getRssiLevelThresholds(Context context) {
        // getIntArray() will never return null, it will throw instead
        return context.getResources().getIntArray(R.array.config_wifiRssiLevelThresholds);
    }
}
