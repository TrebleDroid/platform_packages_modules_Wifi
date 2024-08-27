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

import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;

import com.google.android.mobly.snippet.SnippetObjectConverter;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;

/**
 * The converter class that allows users to use custom type as snippet RPC arguments and return
 * values.
 */
public class WifiAwareSnippetConverter implements SnippetObjectConverter {

    @Override
    public JSONObject serialize(Object object) throws JSONException {
        // If the RPC method requires a custom return type, e.g. SubscribeConfig, PublishConfig, we
        // need to define it here.
        // If the object type is not recognized, you can throw an exception or return null
        // depending on your application's needs.
        return null;
    }

    @Override
    public Object deserialize(JSONObject jsonObject, Type type) throws JSONException {
        // The parameters of Mobly RPC directly reference the Object type.
        // Here, we need to convert JSONObjects back into specific types.
        if (type == SubscribeConfig.class) {
            return WifiAwareJsonDeserializer.jsonToSubscribeConfig(jsonObject);
        } else if (type == PublishConfig.class) {
            return WifiAwareJsonDeserializer.jsonToPublishConfig(jsonObject);
        }
        // If the type is not recognized, you can throw an exception or return null
        // depending on your application's needs.
        return null;
    }
}
