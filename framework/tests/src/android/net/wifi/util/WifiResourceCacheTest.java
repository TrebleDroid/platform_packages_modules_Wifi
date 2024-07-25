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

package android.net.wifi.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.Resources;
import android.net.wifi.WifiContext;
import android.text.TextUtils;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;

@SmallTest
public class WifiResourceCacheTest {
    private static final int TEST_ID = 12345;
    private static final String TEST_NAME = "test_name";
    private static final int VALUE_1 = 10;
    private static final int VALUE_2 = 20;
    private static final String STRING_1 = "string_1";
    private static final String STRING_2 = "string_2";
    private static final int[] INT_ARRAY_1 = {1, 2, 3, 4, 5};
    private static final int[] INT_ARRAY_2 = {5, 4, 3, 2, 1};
    private static final String[] STRINGS_1 = {"1", "2", "3", "4", "5"};
    private static final String[] STRINGS_2 = {"5", "4", "3", "2", "1"};
    @Mock WifiContext mWifiContext;
    @Mock Resources mResources;
    private WifiResourceCache mWifiResourceCache;
    @Captor
    ArgumentCaptor<Integer> mIntegerArgumentCaptor;
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mWifiContext.getResources()).thenReturn(mResources);
        mWifiResourceCache = new WifiResourceCache(mWifiContext);
        doAnswer(v -> String.valueOf(v.getArguments()[0])).when(mResources)
                .getResourceEntryName(anyInt());
    }

    @After
    public void teardown() {
        StringWriter sw = new StringWriter();
        mWifiResourceCache.dump(new PrintWriter(sw));
        assertFalse(TextUtils.isEmpty(sw.toString()));
        Mockito.framework().clearInlineMocks();
    }

    @Test
    public void testGetBooleanResource() {
        when(mResources.getBoolean(TEST_ID)).thenReturn(true);
        assertTrue(mWifiResourceCache.getBoolean(TEST_ID));
        assertTrue(mWifiResourceCache.getBoolean(TEST_ID));
        verify(mResources).getBoolean(TEST_ID);
    }

    @Test
    public void testGetIntegerResource() {
        when(mResources.getInteger(TEST_ID)).thenReturn(VALUE_1);
        assertEquals(VALUE_1, mWifiResourceCache.getInteger(TEST_ID));
        assertEquals(VALUE_1, mWifiResourceCache.getInteger(TEST_ID));
        verify(mResources).getInteger(TEST_ID);
    }

    @Test
    public void testGetStringResource() {
        when(mResources.getString(TEST_ID)).thenReturn(STRING_1);
        assertEquals(STRING_1, mWifiResourceCache.getString(TEST_ID));
        assertEquals(STRING_1, mWifiResourceCache.getString(TEST_ID));
        verify(mResources).getString(TEST_ID);
    }

    @Test
    public void testOverrideBooleanResource() {
        mWifiResourceCache.restoreBooleanValue(String.valueOf(TEST_ID));
        when(mResources.getBoolean(TEST_ID)).thenReturn(true);
        assertTrue(mWifiResourceCache.getBoolean(TEST_ID));
        mWifiResourceCache.overrideBooleanValue(String.valueOf(TEST_ID), false);
        assertFalse(mWifiResourceCache.getBoolean(TEST_ID));
        mWifiResourceCache.restoreBooleanValue(String.valueOf(TEST_ID));
        assertTrue(mWifiResourceCache.getBoolean(TEST_ID));
        verify(mResources, times(2)).getBoolean(TEST_ID);
    }

    @Test
    public void testOverrideIntegerResource() {
        mWifiResourceCache.restoreIntegerValue(String.valueOf(TEST_ID));
        when(mResources.getInteger(TEST_ID)).thenReturn(VALUE_1);
        assertEquals(VALUE_1, mWifiResourceCache.getInteger(TEST_ID));
        mWifiResourceCache.overrideIntegerValue(String.valueOf(TEST_ID), VALUE_2);
        assertEquals(VALUE_2, mWifiResourceCache.getInteger(TEST_ID));
        mWifiResourceCache.restoreIntegerValue(String.valueOf(TEST_ID));
        assertEquals(VALUE_1, mWifiResourceCache.getInteger(TEST_ID));
        verify(mResources, times(2)).getInteger(TEST_ID);
    }

    @Test
    public void testOverrideStringResource() {
        mWifiResourceCache.restoreStringValue(String.valueOf(TEST_ID));
        when(mResources.getString(TEST_ID)).thenReturn(STRING_1);
        assertEquals(STRING_1, mWifiResourceCache.getString(TEST_ID));
        mWifiResourceCache.overrideStringValue(String.valueOf(TEST_ID), STRING_2);
        assertEquals(STRING_2, mWifiResourceCache.getString(TEST_ID));
        mWifiResourceCache.restoreStringValue(String.valueOf(TEST_ID));
        assertEquals(STRING_1, mWifiResourceCache.getString(TEST_ID));
        verify(mResources, times(2)).getString(TEST_ID);
    }

    @Test
    public void testOverrideIntArrayResource() {
        mWifiResourceCache.restoreIntArrayValue(String.valueOf(TEST_ID));
        when(mResources.getIntArray(TEST_ID)).thenReturn(INT_ARRAY_1);
        assertEquals(INT_ARRAY_1, mWifiResourceCache.getIntArray(TEST_ID));
        mWifiResourceCache.overrideIntArrayValue(String.valueOf(TEST_ID), INT_ARRAY_2);
        assertArrayEquals(INT_ARRAY_2, mWifiResourceCache.getIntArray(TEST_ID));
        mWifiResourceCache.restoreIntArrayValue(String.valueOf(TEST_ID));
        mWifiResourceCache.restoreIntArrayValue(String.valueOf(TEST_ID));
        assertEquals(INT_ARRAY_1, mWifiResourceCache.getIntArray(TEST_ID));
        verify(mResources, times(2)).getIntArray(TEST_ID);
    }

    @Test
    public void testOverrideStringArrayResource() {
        mWifiResourceCache.restoreStringArrayValue(String.valueOf(TEST_ID));
        when(mResources.getStringArray(TEST_ID)).thenReturn(STRINGS_1);
        assertEquals(STRINGS_1, mWifiResourceCache.getStringArray(TEST_ID));
        mWifiResourceCache.overrideStringArrayValue(String.valueOf(TEST_ID), STRINGS_2);
        assertEquals(STRINGS_2, mWifiResourceCache.getStringArray(TEST_ID));
        mWifiResourceCache.restoreStringArrayValue(String.valueOf(TEST_ID));
        assertEquals(STRINGS_1, mWifiResourceCache.getStringArray(TEST_ID));
        verify(mResources, times(2)).getStringArray(TEST_ID);
    }


    @Test
    public void testReset() {
        when(mResources.getBoolean(TEST_ID)).thenReturn(true);
        when(mResources.getInteger(TEST_ID + 1)).thenReturn(VALUE_1);
        assertTrue(mWifiResourceCache.getBoolean(TEST_ID));
        assertEquals(VALUE_1, mWifiResourceCache.getInteger(TEST_ID + 1));
        mWifiResourceCache.overrideBooleanValue(String.valueOf(TEST_ID), false);
        mWifiResourceCache.overrideIntegerValue(String.valueOf(TEST_ID + 1), VALUE_2);
        assertEquals(VALUE_2, mWifiResourceCache.getInteger(TEST_ID + 1));
        assertFalse(mWifiResourceCache.getBoolean(TEST_ID));
        mWifiResourceCache.reset();
        assertTrue(mWifiResourceCache.getBoolean(TEST_ID));
        assertEquals(VALUE_1, mWifiResourceCache.getInteger(TEST_ID + 1));
        verify(mResources, times(2)).getBoolean(TEST_ID);
        verify(mResources, times(2)).getInteger(TEST_ID + 1);
    }
}
