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

import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Deserializes JSONObject into data objects defined in Wi-Fi Aware API.
 */
public class WifiAwareJsonDeserializer {

    private static final String SERVICE_NAME = "service_name";
    private static final String SERVICE_SPECIFIC_INFO = "service_specific_info";
    private static final String MATCH_FILTER = "match_filter";
    private static final String SUBSCRIBE_TYPE = "subscribe_type";
    private static final String TERMINATE_NOTIFICATION_ENABLED = "terminate_notification_enabled";
    private static final String MAX_DISTANCE_MM = "max_distance_mm";
    private static final String PAIRING_CONFIG = "pairing_config";
    // PublishConfig special
    private static final String PUBLISH_TYPE = "publish_type";
    private static final String RANGING_ENABLED = "ranging_enabled";
    // AwarePairingConfig specific
    private static final String PAIRING_CACHE_ENABLED = "pairing_cache_enabled";
    private static final String PAIRING_SETUP_ENABLED = "pairing_setup_enabled";
    private static final String PAIRING_VERIFICATION_ENABLED = "pairing_verification_enabled";
    private static final String BOOTSTRAPPING_METHODS = "bootstrapping_methods";

    private WifiAwareJsonDeserializer() {
    }

    /**
     * Converts Python dict to {@link SubscribeConfig}.
     *
     * @param jsonObject corresponding to SubscribeConfig in
     *                   tests/hostsidetests/multidevices/test/aware/constants.py
     */
    public static SubscribeConfig jsonToSubscribeConfig(JSONObject jsonObject)
            throws JSONException {
        SubscribeConfig.Builder builder = new SubscribeConfig.Builder();
        if (jsonObject.has(SERVICE_NAME)) {
            String serviceName = jsonObject.getString(SERVICE_NAME);
            builder.setServiceName(serviceName);
        }
        if (jsonObject.has(SERVICE_SPECIFIC_INFO)) {
            byte[] serviceSpecificInfo =
                    jsonObject.getString(SERVICE_SPECIFIC_INFO).getBytes(StandardCharsets.UTF_8);
            builder.setServiceSpecificInfo(serviceSpecificInfo);
        }
        if (jsonObject.has(MATCH_FILTER)) {
            List<byte[]> matchFilter = new ArrayList<>();
            for (int i = 0; i < jsonObject.getJSONArray(MATCH_FILTER).length(); i++) {
                matchFilter.add(jsonObject.getJSONArray(MATCH_FILTER).getString(i)
                        .getBytes(StandardCharsets.UTF_8));
            }
            builder.setMatchFilter(matchFilter);
        }
        if (jsonObject.has(SUBSCRIBE_TYPE)) {
            int subscribeType = jsonObject.getInt(SUBSCRIBE_TYPE);
            builder.setSubscribeType(subscribeType);
        }
        if (jsonObject.has(TERMINATE_NOTIFICATION_ENABLED)) {
            boolean terminateNotificationEnabled =
                    jsonObject.getBoolean(TERMINATE_NOTIFICATION_ENABLED);
            builder.setTerminateNotificationEnabled(terminateNotificationEnabled);
        }
        if (jsonObject.has(MAX_DISTANCE_MM)) {
            int maxDistanceMm = jsonObject.getInt(MAX_DISTANCE_MM);
            if (maxDistanceMm > 0) {
                builder.setMaxDistanceMm(maxDistanceMm);
            }
        }
        if (jsonObject.has(PAIRING_CONFIG)) {
            JSONObject pairingConfigObject = jsonObject.getJSONObject(PAIRING_CONFIG);
            AwarePairingConfig pairingConfig = jsonToAwarePairingConfig(pairingConfigObject);
            builder.setPairingConfig(pairingConfig);
        }
        return builder.build();
    }

    /**
     * Converts JSONObject to {@link AwarePairingConfig}.
     *
     * @param jsonObject corresponding to SubscribeConfig in
     *                   tests/hostsidetests/multidevices/test/aware/constants.py
     */
    private static AwarePairingConfig jsonToAwarePairingConfig(JSONObject jsonObject)
            throws JSONException {
        AwarePairingConfig.Builder builder = new AwarePairingConfig.Builder();
        if (jsonObject.has(PAIRING_CACHE_ENABLED)) {
            boolean pairingCacheEnabled = jsonObject.getBoolean(PAIRING_CACHE_ENABLED);
            builder.setPairingCacheEnabled(pairingCacheEnabled);
        }
        if (jsonObject.has(PAIRING_SETUP_ENABLED)) {
            boolean pairingSetupEnabled = jsonObject.getBoolean(PAIRING_SETUP_ENABLED);
            builder.setPairingSetupEnabled(pairingSetupEnabled);
        }
        if (jsonObject.has(PAIRING_VERIFICATION_ENABLED)) {
            boolean pairingVerificationEnabled =
                    jsonObject.getBoolean(PAIRING_VERIFICATION_ENABLED);
            builder.setPairingVerificationEnabled(pairingVerificationEnabled);
        }
        if (jsonObject.has(BOOTSTRAPPING_METHODS)) {
            int bootstrappingMethods = jsonObject.getInt(BOOTSTRAPPING_METHODS);
            builder.setBootstrappingMethods(bootstrappingMethods);
        }
        return builder.build();
    }

    /**
     * Converts Python dict to {@link PublishConfig}.
     *
     * @param jsonObject corresponding to PublishConfig in
     *                   tests/hostsidetests/multidevices/test/aware/constants.py
     */
    public static PublishConfig jsonToPublishConfig(JSONObject jsonObject) throws JSONException {
        PublishConfig.Builder builder = new PublishConfig.Builder();
        if (jsonObject.has(SERVICE_NAME)) {
            String serviceName = jsonObject.getString(SERVICE_NAME);
            builder.setServiceName(serviceName);
        }
        if (jsonObject.has(SERVICE_SPECIFIC_INFO)) {
            byte[] serviceSpecificInfo =
                    jsonObject.getString(SERVICE_SPECIFIC_INFO).getBytes(StandardCharsets.UTF_8);
            builder.setServiceSpecificInfo(serviceSpecificInfo);
        }
        if (jsonObject.has(MATCH_FILTER)) {
            List<byte[]> matchFilter = new ArrayList<>();
            for (int i = 0; i < jsonObject.getJSONArray(MATCH_FILTER).length(); i++) {
                matchFilter.add(jsonObject.getJSONArray(MATCH_FILTER).getString(i)
                        .getBytes(StandardCharsets.UTF_8));
            }
            builder.setMatchFilter(matchFilter);
        }
        if (jsonObject.has(PUBLISH_TYPE)) {
            int publishType = jsonObject.getInt(PUBLISH_TYPE);
            builder.setPublishType(publishType);
        }
        if (jsonObject.has(TERMINATE_NOTIFICATION_ENABLED)) {
            boolean terminateNotificationEnabled =
                    jsonObject.getBoolean(TERMINATE_NOTIFICATION_ENABLED);
            builder.setTerminateNotificationEnabled(terminateNotificationEnabled);
        }
        if (jsonObject.has(RANGING_ENABLED)) {
            boolean rangingEnabled = jsonObject.getBoolean(RANGING_ENABLED);
            builder.setRangingEnabled(rangingEnabled);
        }
        if (jsonObject.has(PAIRING_CONFIG)) {
            JSONObject pairingConfigObject = jsonObject.getJSONObject(PAIRING_CONFIG);
            AwarePairingConfig pairingConfig = jsonToAwarePairingConfig(pairingConfigObject);
            builder.setPairingConfig(pairingConfig);
        }
        return builder.build();
    }

}
