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
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

/**
 * Snippet class for exposing {@link WifiAwareManager} APIs.
 */
public class WifiAwareManagerSnippet implements Snippet {
    private final Context mContext;
    private final WifiAwareManager mWifiAwareManager;
    private final Handler mHandler;
    // WifiAwareSession will be initialized after attach.
    private WifiAwareSession mWifiAwareSession;
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
        mWifiAwareManager = mContext.getSystemService(WifiAwareManager.class);
        PermissionUtils.checkPermissions(mContext, Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE);
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
                                + "the "
                                + "current operation to complete, or call `wifiAwareDetach` to "
                                + "cancel and" + " re-attach .");
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
}

