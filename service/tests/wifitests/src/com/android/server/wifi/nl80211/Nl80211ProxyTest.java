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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.net.util.SocketUtils;
import android.system.Os;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.net.module.util.netlink.NetlinkUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;

/**
 * Unit tests for {@link Nl80211Proxy}.
 */
public class Nl80211ProxyTest {
    private Nl80211Proxy mDut;
    private MockitoSession mSession;

    @Mock FileDescriptor mFileDescriptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(NetlinkUtils.class, withSettings().lenient())
                .mockStatic(Os.class)
                .mockStatic(SocketUtils.class)
                .startMocking();
        when(NetlinkUtils.netlinkSocketForProto(anyInt())).thenReturn(mFileDescriptor);
        mDut = new Nl80211Proxy();
        assertTrue(mDut.initialize());
    }

    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    /**
     * Set the response returned by {@link NetlinkUtils#recvMessage(FileDescriptor, int, long)}
     */
    private void setResponseMessage(GenericNetlinkMsg responseMessage) throws Exception {
        ByteBuffer responseMsgBuffer =
                Nl80211TestUtils.createByteBuffer(responseMessage.nlHeader.nlmsg_len);
        responseMessage.pack(responseMsgBuffer);
        responseMsgBuffer.position(0); // reset to the beginning of the buffer
        when(NetlinkUtils.recvMessage(any(), anyInt(), anyLong())).thenReturn(responseMsgBuffer);
    }

    /**
     * Test that we can successfully send an Nl80211 message and receive a response.
     */
    @Test
    public void testSendAndReceiveMessage() throws Exception {
        // Use a non-default command id to identify this as the response message
        GenericNetlinkMsg expectedResponse = new GenericNetlinkMsg(
                (short) (Nl80211TestUtils.TEST_COMMAND + 15),
                Nl80211TestUtils.TEST_FLAGS,
                Nl80211TestUtils.TEST_TYPE,
                Nl80211TestUtils.TEST_SEQUENCE);
        setResponseMessage(expectedResponse);
        GenericNetlinkMsg requestMsg = Nl80211TestUtils.createTestMessage();
        GenericNetlinkMsg receivedResponse = mDut.sendMessageAndReceiveResponse(requestMsg);
        assertTrue(expectedResponse.equals(receivedResponse));
    }
}
