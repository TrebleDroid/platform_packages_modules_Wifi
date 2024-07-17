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

import android.Manifest;
import android.content.Context;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.ServiceDiscoveryInfo;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;

import org.json.JSONException;

import java.nio.charset.StandardCharsets;
import java.util.List;


/**
 * Snippet class for exposing {@link WifiAwareManager} APIs.
 */
public class WifiAwareManagerSnippet implements Snippet {
    private final Context mContext;
    private final WifiAwareManager mWifiAwareManager;
    private final Handler mHandler;
    // WifiAwareSession will be initialized after attach.
    private WifiAwareSession mWifiAwareSession;
    // DiscoverySession will be initialized after publish or subscribe
    private DiscoverySession mDiscoverySession;
    private PeerHandle mPeerHandle;
    private final Object mLock = new Object();

    private enum AttachState {
        IDLE, ATTACHING, ATTACHED
    }

    private AttachState mAttachState = AttachState.IDLE;


    private static class WifiAwareManagerSnippetException extends Exception {
        WifiAwareManagerSnippetException(String msg) {
            super(msg);
        }
    }

    public WifiAwareManagerSnippet() throws WifiAwareManagerSnippetException {
        mContext = ApplicationProvider.getApplicationContext();
        PermissionUtils.checkPermissions(mContext,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
        );
        mWifiAwareManager = mContext.getSystemService(WifiAwareManager.class);
        checkWifiAwareManager();
        HandlerThread handlerThread = new HandlerThread("Snippet-Aware");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    /**
     * Use {@link WifiAwareManager#attach(AttachCallback, Handler)} to attach to the Wi-Fi Aware.
     */
    @AsyncRpc(description = "Attach to the Wi-Fi Aware service - enabling the application to "
            + "create discovery sessions or publish or subscribe to services.")
    public void wifiAwareAttach(String callbackId) throws WifiAwareManagerSnippetException {
        synchronized (mLock) {
            if (mAttachState != AttachState.IDLE) {
                throw new WifiAwareManagerSnippetException(
                        "Attaching multiple Wi-Fi Aware session is not supported now. Wi-Fi Aware"
                                + " is currently attaching or already attached. Please wait for "
                                + "the current operation to complete, or call `wifiAwareDetach` to "
                                + "cancel and re-attach .");
            }
            mAttachState = AttachState.ATTACHING;
        }
        AttachCallback attachCallback = new AttachCallback() {
            @Override
            public void onAttachFailed() {
                super.onAttachFailed();
                synchronized (mLock) {
                    mAttachState = AttachState.IDLE;
                }
                sendEvent(callbackId, "onAttachFailed");
            }

            @Override
            public void onAttached(WifiAwareSession session) {
                super.onAttached(session);
                synchronized (mLock) {
                    mWifiAwareSession = session;
                    mAttachState = AttachState.ATTACHED;
                }
                sendEvent(callbackId, "onAttached");
            }

            @Override
            public void onAwareSessionTerminated() {
                super.onAwareSessionTerminated();
                wifiAwareDetach();
                sendEvent(callbackId, "onAwareSessionTerminated");
            }
        };
        mWifiAwareManager.attach(attachCallback, mHandler);
    }

    /**
     * Use {@link WifiAwareSession#close()} to detach from the Wi-Fi Aware.
     */
    @Rpc(description = "Detach from the Wi-Fi Aware service.")
    public void wifiAwareDetach() {
        synchronized (mLock) {
            if (mWifiAwareSession != null) {
                mWifiAwareSession.close();
                mWifiAwareSession = null;
            }
            mAttachState = AttachState.IDLE;
        }

    }

    /**
     * Check if Wi-Fi Aware is attached.
     */
    @Rpc(description = "Check if Wi-Fi aware is attached")
    public boolean wifiAwareIsSessionAttached() {
        synchronized (mLock) {
            return mAttachState == AttachState.ATTACHED && mWifiAwareSession != null;
        }
    }

    /**
     * Check if Wi-Fi Aware is  pairing supported.
     */
    @Rpc(description = "Check if Wi-Fi aware pairing is available")
    public Boolean wifiAwareIsAwarePairingSupported() throws WifiAwareManagerSnippetException {
        checkWifiAwareManager();
        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        if (characteristics == null) {
            throw new WifiAwareManagerSnippetException(
                    "Can not get Wi-Fi Aware characteristics. Possible reasons include: 1. The "
                            + "Wi-Fi Aware service is not initialized. Please call "
                            + "attachWifiAware first. 2. The device does not support Wi-Fi Aware."
                            + " Check the device's hardware and driver Wi-Fi Aware support.");

        }
        return characteristics.isAwarePairingSupported();
    }


    /**
     * Check if Wi-Fi Aware services is available.
     */
    private void checkWifiAwareManager() throws WifiAwareManagerSnippetException {
        if (mWifiAwareManager == null) {
            throw new WifiAwareManagerSnippetException("Device does not support Wi-Fi Aware.");
        }
    }

    /**
     * Check if Wi-Fi Aware is available.
     */
    @Rpc(description = "Check if Wi-Fi Aware is available")
    public Boolean wifiAwareIsAvailable() {
        return mWifiAwareManager.isAvailable();
    }

    /**
     * Send callback event of current method
     */
    private void sendEvent(String callbackId, String methodName) {
        SnippetEvent event = new SnippetEvent(callbackId, methodName);
        EventCache.getInstance().postEvent(event);
    }

    class WifiAwareDiscoverySessionCallback extends DiscoverySessionCallback {

        String mCallBackId = "";

        WifiAwareDiscoverySessionCallback(String callBackId) {
            this.mCallBackId = callBackId;
        }

        private void putMatchFilterData(List<byte[]> matchFilter, SnippetEvent event) {
            Bundle[] matchFilterBundle = new Bundle[matchFilter.size()];
            int index = 0;
            for (byte[] filter : matchFilter) {
                Bundle bundle = new Bundle();
                bundle.putByteArray("value", filter);
                matchFilterBundle[index] = bundle;
                index++;
            }
            event.getData().putParcelableArray("matchFilter", matchFilterBundle);
        }

        @Override
        public void onPublishStarted(PublishDiscoverySession session) {
            mDiscoverySession = session;
            SnippetEvent snippetEvent = new SnippetEvent(mCallBackId, "discoveryResult");
            snippetEvent.getData().putString("callbackName", "onPublishStarted");
            snippetEvent.getData().putBoolean("isSessionInitialized", session != null);
            EventCache.getInstance().postEvent(snippetEvent);
        }

        @Override
        public void onSubscribeStarted(SubscribeDiscoverySession session) {
            mDiscoverySession = session;
            SnippetEvent snippetEvent = new SnippetEvent(mCallBackId, "discoveryResult");
            snippetEvent.getData().putString("callbackName", "onSubscribeStarted");
            snippetEvent.getData().putBoolean("isSessionInitialized", session != null);
            EventCache.getInstance().postEvent(snippetEvent);
        }

        @Override
        public void onSessionConfigUpdated() {
            sendEvent(mCallBackId, "onSessionConfigUpdated");
        }

        @Override
        public void onSessionConfigFailed() {
            SnippetEvent snippetEvent = new SnippetEvent(mCallBackId, "discoveryResult");
            snippetEvent.getData().putString("callbackName", "onSessionConfigFailed");
            EventCache.getInstance().postEvent(snippetEvent);
        }

        @Override
        public void onSessionTerminated() {
            sendEvent(mCallBackId, "onSessionTerminated");
        }


        @Override
        public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo,
                                        List<byte[]> matchFilter) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onServiceDiscovered");
            event.getData().putByteArray("serviceSpecificInfo", serviceSpecificInfo);
            putMatchFilterData(matchFilter, event);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onServiceDiscovered(ServiceDiscoveryInfo info) {
            mPeerHandle = info.getPeerHandle();
            List<byte[]> matchFilter = info.getMatchFilters();
            SnippetEvent event = new SnippetEvent(mCallBackId, "onServiceDiscovered");
            event.getData().putByteArray("serviceSpecificInfo", info.getServiceSpecificInfo());
            event.getData().putString("pairedAlias", info.getPairedAlias());
            putMatchFilterData(matchFilter, event);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onServiceDiscoveredWithinRange(PeerHandle peerHandle,
                                                   byte[] serviceSpecificInfo,
                                                   List<byte[]> matchFilter, int distanceMm) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onServiceDiscoveredWithinRange");
            event.getData().putByteArray("serviceSpecificInfo", serviceSpecificInfo);
            event.getData().putInt("distanceMm", distanceMm);
            putMatchFilterData(matchFilter, event);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onMessageSendSucceeded(int messageId) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onMessageSendSucceeded");
            event.getData().putInt("lastMessageId", messageId);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onMessageSendFailed(int messageId) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onMessageSendFailed");
            event.getData().putInt("lastMessageId", messageId);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onMessageReceived");
            event.getData().putByteArray("receivedMessage", message);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingSetupRequestReceived(PeerHandle peerHandle, int requestId) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingSetupRequestReceived");
            event.getData().putInt("pairingRequestId", requestId);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingSetupSucceeded(PeerHandle peerHandle, String alias) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingSetupSucceeded");
            event.getData().putString("pairedAlias", alias);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingSetupFailed(PeerHandle peerHandle) {
            mPeerHandle = peerHandle;
            sendEvent(mCallBackId, "onPairingSetupFailed");

        }

        @Override
        public void onPairingVerificationSucceed(@NonNull PeerHandle peerHandle,
                                                 @NonNull String alias) {
            super.onPairingVerificationSucceed(mPeerHandle, alias);
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingVerificationSucceed");
            event.getData().putString("pairedAlias", alias);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingVerificationFailed(PeerHandle peerHandle) {
            mPeerHandle = peerHandle;
            sendEvent(mCallBackId, "onPairingVerificationFailed");
        }

        @Override
        public void onBootstrappingSucceeded(PeerHandle peerHandle, int method) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onBootstrappingSucceeded");
            event.getData().putInt("bootstrappingMethod", method);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onBootstrappingFailed(PeerHandle peerHandle) {
            mPeerHandle = peerHandle;
            sendEvent(mCallBackId, "onBootstrappingFailed");
        }
    }

    private void checkWifiAwareSession() throws WifiAwareManagerSnippetException {
        if (mWifiAwareSession == null) {
            throw new WifiAwareManagerSnippetException(
                    "Wi-Fi Aware session is not attached. Please call wifiAwareAttach first.");
        }
    }


    /**
     * Creates a new Aware subscribe discovery session.
     * For Android T and later, this method requires NEARBY_WIFI_DEVICES permission and user
     * permission flag "neverForLocation". For earlier versions, this method requires
     * NEARBY_WIFI_DEVICES and ACCESS_FINE_LOCATION permissions.
     *
     * @param callbackId      Assigned automatically by mobly.
     * @param subscribeConfig Defines the subscription configuration via
     *                               WifiAwareJsonDeserializer.
     */
    @AsyncRpc(
            description = "Create a Wi-Fi Aware subscribe discovery session and handle callbacks.")
    public void wifiAwareSubscribe(String callbackId, SubscribeConfig subscribeConfig)
            throws JSONException, WifiAwareManagerSnippetException {
        checkWifiAwareSession();
        Log.v("subscribeConfig: " + subscribeConfig.toString());
        WifiAwareDiscoverySessionCallback myDiscoverySessionCallback =
                new WifiAwareDiscoverySessionCallback(callbackId);
        mWifiAwareSession.subscribe(subscribeConfig, myDiscoverySessionCallback, mHandler);
    }

    /**
     * Creates a new Aware publish discovery session.
     * Requires NEARBY_WIFI_DEVICES (with neverForLocation) or ACCESS_FINE_LOCATION for Android
     * TIRAMISU+.
     * ACCESS_FINE_LOCATION is required for earlier versions.
     *
     * @param callbackId    Assigned automatically by mobly.
     * @param publishConfig Defines the publish configuration via WifiAwareJsonDeserializer.
     */
    @AsyncRpc(description = "Create a Wi-Fi Aware publish discovery session and handle callbacks.")
    public void wifiAwarePublish(String callbackId, PublishConfig publishConfig)
            throws JSONException, WifiAwareManagerSnippetException {
        checkWifiAwareSession();
        Log.v("publishConfig: " + publishConfig.toString());
        WifiAwareDiscoverySessionCallback myDiscoverySessionCallback =
                new WifiAwareDiscoverySessionCallback(callbackId);
        mWifiAwareSession.publish(publishConfig, myDiscoverySessionCallback, mHandler);
    }

    private void checkPeerHandler() throws WifiAwareManagerSnippetException {
        if (mPeerHandle == null) {
            throw new WifiAwareManagerSnippetException("Please call publish or subscribe method");
        }
    }

    private void checkDiscoverySession() throws WifiAwareManagerSnippetException {
        if (mDiscoverySession == null) {
            throw new WifiAwareManagerSnippetException("Please call publish or subscribe method");
        }
    }

    /**
     * Sends a message to a peer using Wi-Fi Aware.
     *
     * <p>This method sends a specified message to a peer device identified by a peer handle
     * in an ongoing Wi-Fi Aware discovery session. The message is sent asynchronously,
     * and the method waits for the send status to confirm whether the message was
     * successfully sent or if any errors occurred.</p>
     *
     * <p>Before sending the message, this method checks if there is an active discovery
     * session. If there is no active session, it throws a
     * {@link WifiAwareManagerSnippetException}.</p>
     *
     * @param messageId an integer representing the message ID, which is used to track the message.
     * @param message   a {@link String} containing the message to be sent.
     * @throws WifiAwareManagerSnippetException if there is no active discovery session or
     *                                          if sending the message fails.
     * @see android.net.wifi.aware.DiscoverySession#sendMessage
     * @see android.net.wifi.aware.PeerHandle
     * @see java.nio.charset.StandardCharsets#UTF_8
     */
    @Rpc(description = "Send a message to a peer using Wi-Fi Aware.")
    public void wifiAwareSendMessage(int messageId, String message)
            throws WifiAwareManagerSnippetException {
        // 4. send message & wait for send status
        checkDiscoverySession();
        mDiscoverySession.sendMessage(mPeerHandle, messageId,
                message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Closes the current Wi-Fi Aware discovery session if it is active.
     *
     * <p>This method checks if there is an active discovery session. If so,
     * it closes the session and sets the session object to null. This ensures
     * that resources are properly released and the session is cleanly terminated.</p>
     */
    @Rpc(description = "Close the current Wi-Fi Aware discovery session.")
    public void wifiAwareCloseDiscoverSession() {
        if (mDiscoverySession != null) {
            mDiscoverySession.close();
            mDiscoverySession = null;
        }
    }

    /**
     * Closes the current Wi-Fi Aware session if it is active.
     *
     * <p>This method checks if there is an active Wi-Fi Aware session. If so,
     * it closes the session and sets the session object to null. This ensures
     * that resources are properly released and the session is cleanly terminated.</p>
     */
    @Rpc(description = "Close the current Wi-Fi Aware session.")
    public void wifiAwareCloseWifiAwareSession() {
        if (mWifiAwareSession != null) {
            mWifiAwareSession.close();
            mWifiAwareSession = null;
        }
    }

}

