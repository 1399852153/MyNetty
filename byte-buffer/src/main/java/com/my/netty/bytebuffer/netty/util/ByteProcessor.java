/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.my.netty.bytebuffer.netty.util;


/**
 * copy自netty，做了删减
 */
public interface ByteProcessor {

    static final byte LINE_FEED = (byte) '\n';

    /**
     * A {@link ByteProcessor} which finds the first appearance of a specific byte.
     */
    class IndexOfProcessor implements ByteProcessor {
        private final byte byteToFind;

        public IndexOfProcessor(byte byteToFind) {
            this.byteToFind = byteToFind;
        }

        @Override
        public boolean process(byte value) {
            return value != byteToFind;
        }
    }

    /**
     * Aborts on a {@code LF ('\n')}.
     */
    ByteProcessor FIND_LF = new IndexOfProcessor(LINE_FEED);


    boolean process(byte value);
}
