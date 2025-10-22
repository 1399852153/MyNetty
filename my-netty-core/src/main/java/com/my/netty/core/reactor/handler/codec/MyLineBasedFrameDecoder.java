/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.my.netty.core.reactor.handler.codec;


import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.bytebuffer.netty.util.ByteProcessor;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;

import java.util.List;

/**
 * A decoder that splits the received {@link MyByteBuf}s on line endings.
 * <p>
 * Both {@code "\n"} and {@code "\r\n"} are handled.
 * <p>
 * The byte stream is expected to be in UTF-8 character encoding or ASCII. The current implementation
 * uses direct {@code byte} to {@code char} cast and then compares that {@code char} to a few low range
 * ASCII characters like {@code '\n'} or {@code '\r'}. UTF-8 is not using low range [0..0x7F]
 * byte values for multibyte codepoint representations therefore fully supported by this implementation.
 * <p>
 * For a more general delimiter-based decoder, see {DelimiterBasedFrameDecoder}.
 */
public class MyLineBasedFrameDecoder extends MyByteToMessageDecoder {

    /** Maximum length of a frame we're willing to decode.  */
    private final int maxLength;
    /** Whether or not to throw an exception as soon as we exceed maxLength. */
    private final boolean failFast;
    private final boolean stripDelimiter;

    /** True if we're discarding input because we're already over maxLength.  */
    private boolean discarding;
    private int discardedBytes;

    /** Last scan position. */
    private int offset;

    public MyLineBasedFrameDecoder(final int maxLength) {
        this(maxLength, true, false);
    }

    public MyLineBasedFrameDecoder(final int maxLength, final boolean stripDelimiter, final boolean failFast) {
        this.maxLength = maxLength;
        this.failFast = failFast;
        this.stripDelimiter = stripDelimiter;
    }

    @Override
    protected final void decode(MyChannelHandlerContext ctx, MyByteBuf in, List<Object> out) throws Exception {
        Object decoded = decode(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }

    protected Object decode(MyChannelHandlerContext ctx, MyByteBuf buffer) throws Exception {
        // 尝试找到一个换行符(\n)
        final int eol = findEndOfLine(buffer);
        if (!discarding) {
            if (eol >= 0) {
                // 找到了换行符
                final MyByteBuf frame;

                final int length = eol - buffer.readerIndex();
                // 如果\r\n连在一起，就把\r也一并当做分割符
                final int delimLength = buffer.getByte(eol) == '\r'? 2 : 1;

                if (length > maxLength) {
                    // 找到了换行符，但是发现包含的消息体超过了限制，fail逻辑抛异常
                    buffer.readerIndex(eol + delimLength);
                    fail(ctx, length);
                    return null;
                }

                if (stripDelimiter) {
                    // netty中使用slice机制(readRetainedSlice)，切分出来的ByteBuf与原ByteBuf共享底层内存，性能好很多，但是较为复杂
                    // MyNetty简单起见，直接new一个新的ByteBuf来实现decode解码的功能

                    // 解码后的消息需要忽略掉换行分隔符
                    frame = ctx.alloc().heapBuffer(length);
                    buffer.readBytes(frame);

                    buffer.skipBytes(delimLength);
                } else {
                    // 解码后的消息不忽略换行分隔符，消息中一并带上
                    frame = ctx.alloc().heapBuffer(length + delimLength);
                    buffer.readBytes(frame);
                }

                // 成功解码出一个消息
                return frame;
            } else {
                // 没有找到换行符
                final int length = buffer.readableBytes();
                if (length > maxLength) {
                    // 在maxLength的范围内都没有换行符，直接跳过
                    discardedBytes = length;
                    buffer.readerIndex(buffer.writerIndex());
                    discarding = true;
                    offset = 0;
                    if (failFast) {
                        fail(ctx, "over " + discardedBytes);
                    }
                }
                return null;
            }
        } else {
            // 超过maxLength后没找到换行符，进入了丢弃模式
            if (eol >= 0) {
                final int length = discardedBytes + eol - buffer.readerIndex();
                final int delimLength = buffer.getByte(eol) == '\r'? 2 : 1;
                buffer.readerIndex(eol + delimLength);
                discardedBytes = 0;
                discarding = false;
                if (!failFast) {
                    fail(ctx, length);
                }
            } else {
                // 把一些字节都丢弃掉
                discardedBytes += buffer.readableBytes();
                buffer.readerIndex(buffer.writerIndex());
                // We skip everything in the buffer, we need to set the offset to 0 again.
                offset = 0;
            }
            return null;
        }
    }

    private void fail(final MyChannelHandlerContext ctx, int length) {
        fail(ctx, String.valueOf(length));
    }

    private void fail(final MyChannelHandlerContext ctx, String length) {
        ctx.fireExceptionCaught(
            new RuntimeException(
                "frame length (" + length + ") exceeds the allowed maximum (" + maxLength + ')'));
    }

    /**
     * Returns the index in the buffer of the end of line found.
     * Returns -1 if no end of line was found in the buffer.
     */
    private int findEndOfLine(final MyByteBuf buffer) {
        int totalLength = buffer.readableBytes();
        int i = buffer.forEachByte(buffer.readerIndex() + offset, totalLength - offset, ByteProcessor.FIND_LF);
        if (i >= 0) {
            offset = 0;
            if (i > 0 && buffer.getByte(i - 1) == '\r') {
                i--;
            }
        } else {
            offset = totalLength;
        }
        return i;
    }

}
