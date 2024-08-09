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
package com.google.snippet.wifi.aware;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Utility class for checking permissions.
 */
public final class PermissionUtils {
    /**
     * Check if the app has the required permissions.
     *
     * @param context     The context of the app.
     * @param permissions The permissions to check.
     */
    public static void checkPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException(
                        "Permission denied (missing " + permission + " permission)");
            }
        }
    }
}
