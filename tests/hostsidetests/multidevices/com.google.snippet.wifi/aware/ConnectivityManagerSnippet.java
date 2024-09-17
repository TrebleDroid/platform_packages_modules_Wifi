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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TransportInfo;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;

import java.util.HashMap;
import java.util.Map;

public class ConnectivityManagerSnippet implements Snippet {
    private static final String EVENT_KEY_CB_NAME = "callbackName";
    private static final String EVENT_KEY_NETWORK = "network";
    private static final String EVENT_KEY_NETWORK_CAP = "networkCapabilities";
    private static final String EVENT_KEY_TRANSPORT_INFO_CLASS = "transportInfoClassName";

    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;
    private final Map<String, NetworkCallback> mNetworkCallBacks = new HashMap<>();

    class ConnectivityManagerSnippetSnippetException extends Exception {
        ConnectivityManagerSnippetSnippetException(String msg) {
            super(msg);
        }
    }

    public ConnectivityManagerSnippet() throws ConnectivityManagerSnippetSnippetException {
        mContext = ApplicationProvider.getApplicationContext();
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        if (mConnectivityManager == null) {
            throw new ConnectivityManagerSnippetSnippetException("ConnectivityManager not "
                + "available.");
        }
    }

    public class NetworkCallback extends ConnectivityManager.NetworkCallback {

        String mCallBackId;

        NetworkCallback(String callBackId) {
            mCallBackId = callBackId;
        }

        @Override
        public void onUnavailable() {
            SnippetEvent event = new SnippetEvent(mCallBackId, "NetworkCallback");
            event.getData().putString(EVENT_KEY_CB_NAME, "onUnavailable");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onCapabilitiesChanged(
                @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "NetworkCallback");
            event.getData().putString(EVENT_KEY_CB_NAME, "onCapabilitiesChanged");
            event.getData().putParcelable(EVENT_KEY_NETWORK, network);
            event.getData().putParcelable(EVENT_KEY_NETWORK_CAP, networkCapabilities);
            TransportInfo transportInfo = networkCapabilities.getTransportInfo();
            String transportInfoClassName = "";
            if (transportInfo != null) {
                transportInfoClassName = transportInfo.getClass().getName();
            }
            event.getData().putString(EVENT_KEY_TRANSPORT_INFO_CLASS, transportInfoClassName);
            EventCache.getInstance().postEvent(event);
        }
    }

    /**
     * Requests a network with given network request.
     *
     * @param callBackId              Assigned automatically by mobly. Will be used as request Id
     *                                for further operations
     * @param request                 The request object.
     * @param requestNetworkTimeoutMs The timeout in milliseconds.
     */
    @AsyncRpc(description = "Request a network.")
    public void connectivityRequestNetwork(String callBackId, NetworkRequest request,
                                           int requestNetworkTimeoutMs) {
        Log.v("Requesting network with request: " + request.toString());
        NetworkCallback callback = new NetworkCallback(callBackId);
        mNetworkCallBacks.put(callBackId, callback);
        mConnectivityManager.requestNetwork(request, callback, requestNetworkTimeoutMs);
    }

    /**
     * Unregisters the registered network callback and possibly releases requested networks.
     *
     * @param requestId Id of the network request.
     */
    @Rpc(description = "Unregister a network request")
    public void connectivityUnregisterNetwork(String requestId) {
        NetworkCallback callback = mNetworkCallBacks.get(requestId);
        if (callback == null) {
            return;
        }
        mConnectivityManager.unregisterNetworkCallback(callback);
    }
}
