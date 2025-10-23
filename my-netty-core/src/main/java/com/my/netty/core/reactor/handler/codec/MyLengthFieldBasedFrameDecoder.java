package com.my.netty.core.reactor.handler.codec;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.bytebuffer.netty.util.ObjectUtil;
import com.my.netty.core.reactor.exception.MyNettyException;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import java.nio.ByteOrder;
import java.util.List;

/**
 * 基本copy自Netty的LengthFieldBasedFrameDecoder，但做了一些简化
 * */
public class MyLengthFieldBasedFrameDecoder extends MyByteToMessageDecoder {

    private final ByteOrder byteOrder;
    private final int maxFrameLength;
    private final int lengthFieldOffset;
    private final int lengthFieldLength;
    private final int lengthFieldEndOffset;
    private final int lengthAdjustment;
    private final int initialBytesToStrip;
    private final boolean failFast;
    private boolean discardingTooLongFrame;
    private long tooLongFrameLength;
    private long bytesToDiscard;
    private int frameLengthInt = -1;

    public MyLengthFieldBasedFrameDecoder(
            int maxFrameLength,
            int lengthFieldOffset, int lengthFieldLength) {
        this(maxFrameLength, lengthFieldOffset, lengthFieldLength, 0, 0);
    }

    public MyLengthFieldBasedFrameDecoder(
            int maxFrameLength,
            int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip) {
        this(
                maxFrameLength,
                lengthFieldOffset, lengthFieldLength, lengthAdjustment,
                initialBytesToStrip, true);
    }

    public MyLengthFieldBasedFrameDecoder(
            int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
        this(
                ByteOrder.BIG_ENDIAN, maxFrameLength, lengthFieldOffset, lengthFieldLength,
                lengthAdjustment, initialBytesToStrip, failFast);
    }

    public MyLengthFieldBasedFrameDecoder(
            ByteOrder byteOrder, int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip, boolean failFast) {

        this.byteOrder = ObjectUtil.checkNotNull(byteOrder, "byteOrder");

        ObjectUtil.checkPositive(maxFrameLength, "maxFrameLength");

        ObjectUtil.checkPositiveOrZero(lengthFieldOffset, "lengthFieldOffset");

        ObjectUtil.checkPositiveOrZero(initialBytesToStrip, "initialBytesToStrip");

        if (lengthFieldOffset > maxFrameLength - lengthFieldLength) {
            throw new IllegalArgumentException(
                    "maxFrameLength (" + maxFrameLength + ") " +
                    "must be equal to or greater than " +
                    "lengthFieldOffset (" + lengthFieldOffset + ") + " +
                    "lengthFieldLength (" + lengthFieldLength + ").");
        }

        this.maxFrameLength = maxFrameLength;
        this.lengthFieldOffset = lengthFieldOffset;
        this.lengthFieldLength = lengthFieldLength;
        this.lengthAdjustment = lengthAdjustment;
        this.lengthFieldEndOffset = lengthFieldOffset + lengthFieldLength;
        this.initialBytesToStrip = initialBytesToStrip;
        this.failFast = failFast;
    }

    @Override
    protected final void decode(MyChannelHandlerContext ctx, MyByteBuf in, List<Object> out) throws Exception {
        Object decoded = decode(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }

    private void discardingTooLongFrame(MyByteBuf in) {
        long bytesToDiscard = this.bytesToDiscard;
        int localBytesToDiscard = (int) Math.min(bytesToDiscard, in.readableBytes());
        in.skipBytes(localBytesToDiscard);
        bytesToDiscard -= localBytesToDiscard;
        this.bytesToDiscard = bytesToDiscard;

        failIfNecessary(false);
    }

    private static void failOnNegativeLengthField(MyByteBuf in, long frameLength, int lengthFieldEndOffset) {
        in.skipBytes(lengthFieldEndOffset);
        throw new MyNettyException(
           "negative pre-adjustment length field: " + frameLength);
    }

    private static void failOnFrameLengthLessThanLengthFieldEndOffset(MyByteBuf in,
                                                                      long frameLength,
                                                                      int lengthFieldEndOffset) {
        in.skipBytes(lengthFieldEndOffset);
        throw new MyNettyException(
           "Adjusted frame length (" + frameLength + ") is less " +
              "than lengthFieldEndOffset: " + lengthFieldEndOffset);
    }

    private void exceededFrameLength(MyByteBuf in, long frameLength) {
        long discard = frameLength - in.readableBytes();
        tooLongFrameLength = frameLength;

        if (discard < 0) {
            // buffer contains more bytes then the frameLength so we can discard all now
            in.skipBytes((int) frameLength);
        } else {
            // Enter the discard mode and discard everything received so far.
            discardingTooLongFrame = true;
            bytesToDiscard = discard;
            in.skipBytes(in.readableBytes());
        }
        failIfNecessary(true);
    }

    private static void failOnFrameLengthLessThanInitialBytesToStrip(MyByteBuf in,
                                                                     long frameLength,
                                                                     int initialBytesToStrip) {
        in.skipBytes((int) frameLength);
        throw new MyNettyException(
           "Adjusted frame length (" + frameLength + ") is less " +
              "than initialBytesToStrip: " + initialBytesToStrip);
    }

    protected Object decode(MyChannelHandlerContext ctx, MyByteBuf in) {
        long frameLength = 0;
        if (frameLengthInt == -1) { // new frame

            if (discardingTooLongFrame) {
                discardingTooLongFrame(in);
            }

            if (in.readableBytes() < lengthFieldEndOffset) {
                return null;
            }

            int actualLengthFieldOffset = in.readerIndex() + lengthFieldOffset;
            frameLength = getUnadjustedFrameLength(in, actualLengthFieldOffset, lengthFieldLength, byteOrder);

            if (frameLength < 0) {
                failOnNegativeLengthField(in, frameLength, lengthFieldEndOffset);
            }

            frameLength += lengthAdjustment + lengthFieldEndOffset;

            if (frameLength < lengthFieldEndOffset) {
                failOnFrameLengthLessThanLengthFieldEndOffset(in, frameLength, lengthFieldEndOffset);
            }

            if (frameLength > maxFrameLength) {
                exceededFrameLength(in, frameLength);
                return null;
            }
            // never overflows because it's less than maxFrameLength
            frameLengthInt = (int) frameLength;
        }
        if (in.readableBytes() < frameLengthInt) { // frameLengthInt exist , just check buf
            return null;
        }
        if (initialBytesToStrip > frameLengthInt) {
            failOnFrameLengthLessThanInitialBytesToStrip(in, frameLength, initialBytesToStrip);
        }
        in.skipBytes(initialBytesToStrip);

        // extract frame
        int readerIndex = in.readerIndex();
        int actualFrameLength = frameLengthInt - initialBytesToStrip;
        MyByteBuf frame = extractFrame(ctx, in, readerIndex, actualFrameLength);
        in.readerIndex(readerIndex + actualFrameLength);
        frameLengthInt = -1; // start processing the next frame
        return frame;
    }

    protected long getUnadjustedFrameLength(MyByteBuf buf, int offset, int length, ByteOrder order) {
        // 改为使用getUnsignedIntLE方法获取
//        buf = buf.order(order);

        switch (length) {
            // MyNetty没有支持ByteBuf更多的数据类型，简化成只支持最常见的int类型，4字节的长度字段协议
            case 4:
                if(order == ByteOrder.BIG_ENDIAN) {
                    return buf.getUnsignedInt(offset);
                }else{
                    return buf.getUnsignedIntLE(offset);
                }
            default:
//                throw new MyNettyException("unsupported lengthFieldLength: " + lengthFieldLength + " (expected: 1, 2, 3, 4, or 8)");
                throw new MyNettyException("unsupported lengthFieldLength: " + lengthFieldLength + " (expected: 4)");
        }
    }

    private void failIfNecessary(boolean firstDetectionOfTooLongFrame) {
        if (bytesToDiscard == 0) {
            // Reset to the initial state and tell the handlers that
            // the frame was too large.
            long tooLongFrameLength = this.tooLongFrameLength;
            this.tooLongFrameLength = 0;
            discardingTooLongFrame = false;
            if (!failFast || firstDetectionOfTooLongFrame) {
                fail(tooLongFrameLength);
            }
        } else {
            // Keep discarding and notify handlers if necessary.
            if (failFast && firstDetectionOfTooLongFrame) {
                fail(tooLongFrameLength);
            }
        }
    }

    /**
     * Extract the sub-region of the specified buffer.
     */
    protected MyByteBuf extractFrame(MyChannelHandlerContext ctx, MyByteBuf buffer, int index, int length) {
        // Netty使用buffer.retainedSlice(index, length);
        // 零拷贝，性能更好，节约内存，但MyNetty简单起见没有实现slice切片能力，所以只能重新创建一个ByteBuf,把数据复制过去

        MyByteBuf frameByteBuf = ctx.alloc().heapBuffer(length - index);
        buffer.getBytes(index, buffer, 0, length);

        return frameByteBuf;
    }

    private void fail(long frameLength) {
        if (frameLength > 0) {
            throw new MyNettyException(
                            "Adjusted frame length exceeds " + maxFrameLength +
                            ": " + frameLength + " - discarded");
        } else {
            throw new MyNettyException(
                            "Adjusted frame length exceeds " + maxFrameLength +
                            " - discarding");
        }
    }
}
