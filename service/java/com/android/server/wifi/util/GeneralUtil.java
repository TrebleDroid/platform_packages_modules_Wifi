/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.wifi.util;

import java.util.BitSet;

/**
 * Class for general helper methods and objects for Wifi Framework code.
 * @hide
 */
public class GeneralUtil {

    /**
     * Class which can be used to fetch an object out of a lambda. Fetching an object
     * out of a local scope with HIDL is a common operation (although usually it can
     * and should be avoided).
     *
     * @param <E> Inner object type.
     */
    public static final class Mutable<E> {
        public E value;

        public Mutable() {
            value = null;
        }

        public Mutable(E value) {
            this.value = value;
        }
    }

    /**
     * Convert a capability bitmask to its index in a BitSet.
     *
     * TODO: Remove this method once the WifiManager capabilities are
     *       represented as indexes rather than bitmasks.
     */
    public static int getCapabilityIndex(long capability) {
        // Index of the first enabled bit is the number of trailing zeroes
        // in the binary representation
        return Long.numberOfTrailingZeros(capability);
    }

    /**
     * Convert a long to a BitSet.
     *
     * See TODO in {@link #getCapabilityIndex(long)}.
     */
    public static BitSet longToBitset(long longValue) {
        return BitSet.valueOf(new long[]{longValue});
    }

    /**
     * Convert a BitSet to a long.
     *
     * See TODO in {@link #getCapabilityIndex(long)}.
     */
    public static long bitsetToLong(BitSet bitset) {
        if (bitset == null || bitset.cardinality() == 0) {
            return 0L;
        }
        return bitset.toLongArray()[0];
    }
}
