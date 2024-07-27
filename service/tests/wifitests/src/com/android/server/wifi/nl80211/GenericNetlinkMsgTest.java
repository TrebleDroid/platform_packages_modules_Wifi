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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.net.module.util.netlink.StructNlAttr;

import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * Unit tests for {@link GenericNetlinkMsg}.
 */
public class GenericNetlinkMsgTest {
    /**
     * Test that an instance of GenericNetlinkMsg can be packed into and parsed from a ByteBuffer.
     */
    @Test
    public void testPackAndParse() {
        GenericNetlinkMsg msg = Nl80211TestUtils.createTestMessageWithAttributes();
        ByteBuffer buffer = Nl80211TestUtils.createByteBuffer(msg.nlHeader.nlmsg_len);
        msg.pack(buffer);
        buffer.position(0); // reset to the beginning of the buffer

        GenericNetlinkMsg parsedMsg = GenericNetlinkMsg.parse(buffer);
        assertTrue(msg.equals(parsedMsg));
    }

    /**
     * Test that attributes can be properly set and retrieved.
     */
    @Test
    public void testSetAndGetAttributes() {
        // No attributes by default
        GenericNetlinkMsg msg = Nl80211TestUtils.createTestMessage();
        assertNull(msg.getAttribute(Nl80211TestUtils.TEST_ATTRIBUTE_ID));

        // Attribute can be retrieved once it has been added
        StructNlAttr attribute = new StructNlAttr(
                Nl80211TestUtils.TEST_ATTRIBUTE_ID, Nl80211TestUtils.TEST_ATTRIBUTE_VALUE);
        msg.addAttribute(attribute);
        assertNotNull(msg.getAttribute(Nl80211TestUtils.TEST_ATTRIBUTE_ID));
    }

    /**
     * Test that the total message size is updated if an existing attribute is replaced.
     */
    @Test
    public void testReplaceAttribute() {
        // Create two attributes with the same attribute id, but different sizes
        StructNlAttr intAttribute = new StructNlAttr(
                Nl80211TestUtils.TEST_ATTRIBUTE_ID, Nl80211TestUtils.TEST_ATTRIBUTE_VALUE);
        StructNlAttr longAttribute = new StructNlAttr(
                Nl80211TestUtils.TEST_ATTRIBUTE_ID, (long) Nl80211TestUtils.TEST_ATTRIBUTE_VALUE);
        int expectedSizeIncrease =
                longAttribute.getAlignedLength() - intAttribute.getAlignedLength();
        assertNotEquals(0, expectedSizeIncrease);

        GenericNetlinkMsg msg = Nl80211TestUtils.createTestMessage();
        msg.addAttribute(intAttribute);
        int intAttributeMsgSize = msg.nlHeader.nlmsg_len;

        // Replacing the int attribute with the long attribute should increase the message size
        msg.addAttribute(longAttribute);
        int longAttributeMsgSize = msg.nlHeader.nlmsg_len;
        assertEquals(expectedSizeIncrease, longAttributeMsgSize - intAttributeMsgSize);
    }

    /**
     * Pack attribute into a ByteBuffer and override the size to the provided value.
     *
     * Allows us to create malformed attribute headers with an invalid size.
     */
    private static ByteBuffer packAttributeAndOverrideSize(StructNlAttr attribute, int newSize) {
        int actualSize = attribute.getAlignedLength();
        ByteBuffer buffer = Nl80211TestUtils.createByteBuffer(actualSize);
        attribute.pack(buffer);

        // Overwrite the size field with the new value
        buffer.position(0);
        buffer.putShort((short) newSize);

        // Reset to the beginning of the buffer to allow parsing
        buffer.position(0);
        return buffer;
    }

    /**
     * Test that {@link GenericNetlinkMsg#parseAttributesToMap(ByteBuffer, int)} properly handles
     * an attribute whose header indicates that it is smaller than the minimum NlAttribute size.
     */
    @Test
    public void testMalformedAttribute_lessThanMinAttributeSize() {
        StructNlAttr attribute = new StructNlAttr(Nl80211TestUtils.TEST_ATTRIBUTE_ID);
        ByteBuffer buffer =
                packAttributeAndOverrideSize(attribute, StructNlAttr.NLA_HEADERLEN - 1);
        assertNull(GenericNetlinkMsg.parseAttributesToMap(buffer, buffer.remaining()));
    }

    /**
     * Test that {@link GenericNetlinkMsg#parseAttributesToMap(ByteBuffer, int)} properly handles
     * an attribute whose header indicates that it is larger than the space remaining
     * in the message.
     */
    @Test
    public void testMalformedAttribute_greaterThanRemainingBufferSize() {
        StructNlAttr attribute = new StructNlAttr(Nl80211TestUtils.TEST_ATTRIBUTE_ID);
        final int invalidSize = 100;
        ByteBuffer buffer = packAttributeAndOverrideSize(attribute, invalidSize);
        // Size indicated in the header is larger than the size of the entire buffer
        assertTrue(invalidSize > buffer.remaining());
        assertNull(GenericNetlinkMsg.parseAttributesToMap(buffer, buffer.remaining()));
    }

    /**
     * Test that {@link GenericNetlinkMsg#parseAttributesToMap(ByteBuffer, int)} properly handles
     * an attribute whose header indicates that it is smaller than its actual size.
     */
    @Test
    public void testMalformedAttribute_smallerThanRemainingBufferSize() {
        StructNlAttr attribute = new StructNlAttr(
                Nl80211TestUtils.TEST_ATTRIBUTE_ID, (long) Nl80211TestUtils.TEST_ATTRIBUTE_VALUE);
        final int invalidSize = 5;
        ByteBuffer buffer = packAttributeAndOverrideSize(attribute, invalidSize);
        // Size indicated in the header is smaller than the remaining size in the buffer
        assertTrue(invalidSize < buffer.remaining());
        assertNull(GenericNetlinkMsg.parseAttributesToMap(buffer, buffer.remaining()));
    }
}
