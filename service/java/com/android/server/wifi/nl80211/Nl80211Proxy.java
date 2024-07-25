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

package com.android.server.wifi.nl80211;

import static com.android.server.wifi.nl80211.NetlinkConstants.NETLINK_GENERIC;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.system.ErrnoException;
import android.util.Log;

import com.android.net.module.util.netlink.NetlinkUtils;

import java.io.FileDescriptor;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper around Nl80211 functionality. Allows sending and receiving Nl80211 messages.
 */
public class Nl80211Proxy {
    private static final String TAG = "Nl80211Proxy";

    private boolean mIsInitialized;
    private FileDescriptor mNetlinkFd;

    public Nl80211Proxy() {}

    protected static FileDescriptor createNetlinkFileDescriptor() {
        try {
            FileDescriptor fd = NetlinkUtils.netlinkSocketForProto(NETLINK_GENERIC);
            NetlinkUtils.connectToKernel(fd);
            return fd;
        } catch (ErrnoException | SocketException e) {
            Log.e(TAG, "Unable to create Netlink file descriptor. " + e);
            return null;
        }
    }

    private boolean sendNl80211Message(@NonNull GenericNetlinkMsg message) {
        if (message == null) return false;
        try {
            byte[] msgBytes = message.toByteArray();
            NetlinkUtils.sendMessage(
                    mNetlinkFd, msgBytes, 0, msgBytes.length, NetlinkUtils.IO_TIMEOUT_MS);
            return true;
        } catch (ErrnoException | IllegalArgumentException | InterruptedIOException e) {
            Log.i(TAG, "Unable to send Nl80211 message. " + e);
            return false;
        }
    }

    private static @Nullable List<GenericNetlinkMsg> parseNl80211MessagesFromBuffer(
            @NonNull ByteBuffer buffer) {
        if (buffer == null) return null;
        List<GenericNetlinkMsg> parsedMessages = new ArrayList<>();

        // Expect buffer to be the exact size of all the contained messages
        while (buffer.remaining() > 0) {
            GenericNetlinkMsg message = GenericNetlinkMsg.parse(buffer);
            if (message == null) {
                Log.e(TAG, "Unable to parse a received message. numParsed=" + parsedMessages.size()
                        + ", bufRemaining=" + buffer.remaining());
                return null;
            }
            parsedMessages.add(message);
        }
        return parsedMessages;
    }

    private @Nullable List<GenericNetlinkMsg> receiveNl80211Messages() {
        try {
            ByteBuffer recvBuffer = NetlinkUtils.recvMessage(
                    mNetlinkFd, NetlinkUtils.DEFAULT_RECV_BUFSIZE, NetlinkUtils.IO_TIMEOUT_MS);
            return parseNl80211MessagesFromBuffer(recvBuffer);
        } catch (ErrnoException | IllegalArgumentException | InterruptedIOException e) {
            Log.i(TAG, "Unable to receive Nl80211 messages. " + e);
            return null;
        }
    }

    /**
     * Initialize this instance of Nl80211Proxy.
     *
     * @return true if initialization was successful, false otherwise
     */
    public boolean initialize() {
        mNetlinkFd = createNetlinkFileDescriptor();
        if (mNetlinkFd != null) {
            mIsInitialized = true;
        }
        return mIsInitialized;
    }

    /**
     * Send a GenericNetlinkMsg and receive several responses.
     *
     * @param message Netlink message to be sent.
     * @return List of response messages, or null if an error occurred.
     */
    public @Nullable List<GenericNetlinkMsg> sendMessageAndReceiveResponses(
            @NonNull GenericNetlinkMsg message) {
        if (mNetlinkFd == null) {
            Log.e(TAG, "Netlink file descriptor is not available");
            return null;
        }
        if (message == null) {
            Log.e(TAG, "Unable to send a null message");
            return null;
        }
        if (!sendNl80211Message(message)) {
            return null;
        }
        return receiveNl80211Messages();
    }

    /**
     * Send a GenericNetlinkMsg and receive a single response.
     *
     * @param message Netlink message to be sent.
     * @return Response message, or null if an error occurred.
     */
    public @Nullable GenericNetlinkMsg sendMessageAndReceiveResponse(
            @NonNull GenericNetlinkMsg message) {
        List<GenericNetlinkMsg> responses = sendMessageAndReceiveResponses(message);
        if (responses == null) {
            return null;
        }
        if (responses.size() != 1) {
            Log.e(TAG, "Received " + responses.size() + " responses, but was expecting one");
            return null;
        }
        return responses.get(0);
    }
}
