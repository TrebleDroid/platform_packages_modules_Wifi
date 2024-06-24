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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * Unit tests for {@link StructGenNlMsgHdr};
 */
public class StructGenNlMsgHdrTest {
    public static final short TEST_COMMAND = 123;

    /**
     * Test that an instance of StructGenNlMsgHdr can be packed into and parsed from a ByteBuffer.
     */
    @Test
    public void testPackAndParse() {
        StructGenNlMsgHdr genericHeader = new StructGenNlMsgHdr(TEST_COMMAND);
        ByteBuffer buffer = Nl80211TestUtils.createByteBuffer(StructGenNlMsgHdr.STRUCT_SIZE);
        genericHeader.pack(buffer);

        buffer.position(0); // reset to the beginning of the buffer
        StructGenNlMsgHdr parsedHeader = StructGenNlMsgHdr.parse(buffer);
        assertTrue(genericHeader.equals(parsedHeader));
    }

    /**
     * Test that the parse method can handle an invalid ByteBuffer.
     */
    @Test
    public void testParseFromInvalidByteBuffer() {
        // Buffer is not large enough to contain a valid instance
        int invalidBufferSize = StructGenNlMsgHdr.STRUCT_SIZE / 2;
        ByteBuffer invalidBuffer = Nl80211TestUtils.createByteBuffer(invalidBufferSize);
        assertNull(StructGenNlMsgHdr.parse(invalidBuffer));

        // Null buffer
        assertNull(StructGenNlMsgHdr.parse(null));
    }
}
