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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.Resources;
import android.net.wifi.WifiContext;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
public class WifiResourceCacheTest {
    private static final int TEST_ID = 12345;
    private static final String TEST_NAME = "test_name";
    private static final int VALUE_1 = 10;
    private static final int VALUE_2 = 20;
    @Mock WifiContext mWifiContext;
    @Mock Resources mResources;
    private WifiResourceCache mWifiResourceCache;
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mWifiContext.getResources()).thenReturn(mResources);
        mWifiResourceCache = new WifiResourceCache(mWifiContext);
    }

    @Test
    public void testGetBooleanResource() {
        when(mResources.getBoolean(TEST_ID)).thenReturn(true);
        assertTrue(mWifiResourceCache.getBoolean(TEST_ID, TEST_NAME));
        assertTrue(mWifiResourceCache.getBoolean(TEST_ID, TEST_NAME));
        verify(mResources).getBoolean(TEST_ID);
    }

    @Test
    public void testGetIntegerResource() {
        when(mResources.getInteger(TEST_ID)).thenReturn(VALUE_1);
        assertEquals(VALUE_1, mWifiResourceCache.getInteger(TEST_ID, TEST_NAME));
        assertEquals(VALUE_1, mWifiResourceCache.getInteger(TEST_ID, TEST_NAME));
        verify(mResources).getInteger(TEST_ID);
    }

    @Test
    public void testOverrideBooleanResource() {
        when(mResources.getBoolean(TEST_ID)).thenReturn(true);
        assertTrue(mWifiResourceCache.getBoolean(TEST_ID, TEST_NAME));
        mWifiResourceCache.overrideBooleanValue(TEST_NAME, false);
        assertFalse(mWifiResourceCache.getBoolean(TEST_ID, TEST_NAME));
        mWifiResourceCache.restoreBooleanValue(TEST_NAME);
        assertTrue(mWifiResourceCache.getBoolean(TEST_ID, TEST_NAME));
        verify(mResources, times(2)).getBoolean(TEST_ID);
    }

    @Test
    public void testOverrideIntegerResource() {
        when(mResources.getInteger(TEST_ID)).thenReturn(VALUE_1);
        assertEquals(VALUE_1, mWifiResourceCache.getInteger(TEST_ID, TEST_NAME));
        mWifiResourceCache.overrideIntegerValue(TEST_NAME, VALUE_2);
        assertEquals(VALUE_2, mWifiResourceCache.getInteger(TEST_ID, TEST_NAME));
        mWifiResourceCache.restoreIntegerValue(TEST_NAME);
        assertEquals(VALUE_1, mWifiResourceCache.getInteger(TEST_ID, TEST_NAME));
        verify(mResources, times(2)).getInteger(TEST_ID);
    }

    @Test
    public void testReset() {
        when(mResources.getBoolean(TEST_ID)).thenReturn(true);
        when(mResources.getInteger(TEST_ID)).thenReturn(VALUE_1);
        assertTrue(mWifiResourceCache.getBoolean(TEST_ID, TEST_NAME));
        assertEquals(VALUE_1, mWifiResourceCache.getInteger(TEST_ID, TEST_NAME));
        mWifiResourceCache.overrideBooleanValue(TEST_NAME, false);
        mWifiResourceCache.overrideIntegerValue(TEST_NAME, VALUE_2);
        assertEquals(VALUE_2, mWifiResourceCache.getInteger(TEST_ID, TEST_NAME));
        assertFalse(mWifiResourceCache.getBoolean(TEST_ID, TEST_NAME));
        mWifiResourceCache.reset();
        assertTrue(mWifiResourceCache.getBoolean(TEST_ID, TEST_NAME));
        assertEquals(VALUE_1, mWifiResourceCache.getInteger(TEST_ID, TEST_NAME));
        verify(mResources, times(2)).getBoolean(TEST_ID);
        verify(mResources, times(2)).getInteger(TEST_ID);
    }
}
