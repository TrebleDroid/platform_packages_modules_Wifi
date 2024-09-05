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

import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.os.Parcel;
import android.util.Base64;

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
    // WifiAwareNetworkSpecifier specific
    private static final String IS_ACCEPT_ANY = "is_accept_any";
    private static final String PMK = "pmk";
    private static final String CHANNEL_IN_MHZ = "channel_in_mhz";
    private static final String CHANNEL_REQUIRE = "channel_require";
    private static final String PSK_PASSPHRASE = "psk_passphrase";
    private static final String PORT = "port";
    private static final String TRANSPORT_PROTOCOL = "transport_protocol";
    //NetworkRequest specific
    private static final String TRANSPORT_TYPE = "transport_type";
    private static final String CAPABILITY = "capability";
    private static final String NETWORK_SPECIFIER = "network_specifier";


    private WifiAwareJsonDeserializer() {
    }

    /**
     * Converts Python dict to {@link SubscribeConfig}.
     *
     * @param jsonObject corresponding to SubscribeConfig in
     *                   tests/hostsidetests/multidevices/test/aware/constants.py
     */
    public static SubscribeConfig jsonToSubscribeConfig(JSONObject jsonObject) throws
            JSONException {
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
    private static AwarePairingConfig jsonToAwarePairingConfig(JSONObject jsonObject) throws
            JSONException {
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

    /**
     * Converts request from JSON object to {@link NetworkRequest}.
     *
     * @param jsonObject corresponding to WifiAwareNetworkSpecifier in
     *                   tests/hostsidetests/multidevices/test/aware/constants.py
     */
    public static NetworkRequest jsonToNetworkRequest(JSONObject jsonObject) throws JSONException {
        NetworkRequest.Builder requestBuilder = new NetworkRequest.Builder();
        int transportType;
        if (jsonObject.has(TRANSPORT_TYPE)) {
            transportType = jsonObject.getInt(TRANSPORT_TYPE);
        } else {
            // Returns null for request of unknown type.
            return null;
        }
        if (transportType == NetworkCapabilities.TRANSPORT_WIFI_AWARE) {
            requestBuilder.addTransportType(transportType);
            if (jsonObject.has(NETWORK_SPECIFIER)) {
                String specifierParcelableStr = jsonObject.getString(NETWORK_SPECIFIER);
                // Convert the Base64 string to a byte array
                byte[] bytes = Base64.decode(specifierParcelableStr, Base64.DEFAULT);
                // Use Parcel to read the byte array
                Parcel parcel = Parcel.obtain();
                parcel.unmarshall(bytes, 0, bytes.length);
                parcel.setDataPosition(0);
                // Use the CREATOR to create WifiAwareNetworkSpecifier from the parcel
                WifiAwareNetworkSpecifier specifier =
                        WifiAwareNetworkSpecifier.CREATOR.createFromParcel(parcel);
                // Release the Parcel object
                parcel.recycle();
                // Set the network specifier in the request builder
                requestBuilder.setNetworkSpecifier(specifier);
            }
            if (jsonObject.has(CAPABILITY)) {
                int capability = jsonObject.getInt(CAPABILITY);
                requestBuilder.addCapability(capability);
            }
            return requestBuilder.build();
        }
        return null;
    }


}
