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

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Representation of a generic Netlink message header.
 *
 * See kernel/uapi/linux/genetlink.h
 */
public class StructGenNlMsgHdr {
    public static final int STRUCT_SIZE = 4;

    public final short command; // byte
    public final byte version;
    public final short reserved;

    public StructGenNlMsgHdr(short command) {
        this.command = command;
        this.version = 1;   // only V1 is used
        this.reserved = 0;  // unused
    }

    private StructGenNlMsgHdr(short command, byte version, short reserved) {
        this.command = command;
        this.version = version;
        this.reserved = reserved;
    }

    /**
     * Read a StructGenNlMsgHdr object from a ByteBuffer.
     */
    @Nullable
    public static StructGenNlMsgHdr parse(@NonNull final ByteBuffer byteBuffer) {
        if (byteBuffer == null || byteBuffer.remaining() < STRUCT_SIZE) {
            return null;
        }
        // Assume that the byte buffer has already been set to native order
        short command = (short) (byteBuffer.get() & 0xFF);
        byte version = byteBuffer.get();
        short reserved = byteBuffer.getShort();
        return new StructGenNlMsgHdr(command, version, reserved);
    }

    /**
     * Write a StructGenNlMsgHdr object to a ByteBuffer.
     */
    public void pack(@NonNull final ByteBuffer byteBuffer) {
        if (byteBuffer == null || byteBuffer.remaining() < STRUCT_SIZE) {
            return;
        }
        // Assume that the byte buffer has already been set to native order
        byteBuffer.put((byte) (command & 0xFF));
        byteBuffer.put(version);
        byteBuffer.putShort(reserved);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null || !(o instanceof StructGenNlMsgHdr)) return false;
        StructGenNlMsgHdr other = (StructGenNlMsgHdr) o;
        return this.command == other.command
                && this.version == other.version
                && this.reserved == other.reserved;
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, version, reserved);
    }

    @Override
    public String toString() {
        return "StructGenNlMsgHdr{ "
                + "command{" + command + "}, "
                + "version{" + version + "}, "
                + "reserved{" + reserved + "} "
                + "}";
    }
}
