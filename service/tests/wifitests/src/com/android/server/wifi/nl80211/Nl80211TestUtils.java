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

import com.android.net.module.util.netlink.StructNlAttr;
import com.android.net.module.util.netlink.StructNlMsgHdr;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utilities for the Nl80211 unit tests.
 */
public class Nl80211TestUtils {
    public static final short TEST_COMMAND = 123;
    public static final short TEST_TYPE = 12;
    public static final short TEST_FLAGS = StructNlMsgHdr.NLM_F_REQUEST;
    public static final int TEST_SEQUENCE = 9999;
    public static final short TEST_ATTRIBUTE_ID = 41;
    public static final int TEST_ATTRIBUTE_VALUE = 789;
    public static final int NUM_TEST_ATTRIBUTES = 5;

    /**
     * Create a byte buffer in native order.
     */
    public static ByteBuffer createByteBuffer(int size) {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    /**
     * Create a test GenericNetlinkMsg with no attributes.
     */
    public static GenericNetlinkMsg createTestMessage() {
        return new GenericNetlinkMsg(TEST_COMMAND, TEST_TYPE, TEST_FLAGS, TEST_SEQUENCE);
    }

    /**
     * Create a test GenericNetlinkMsg with attributes.
     */
    public static GenericNetlinkMsg createTestMessageWithAttributes() {
        GenericNetlinkMsg msg = createTestMessage();
        for (int i = 0; i < NUM_TEST_ATTRIBUTES; i++) {
            StructNlAttr attribute =
                    new StructNlAttr((short) (TEST_ATTRIBUTE_ID + i), TEST_ATTRIBUTE_VALUE);
            msg.addAttribute(attribute);
        }
        return msg;
    }
}
