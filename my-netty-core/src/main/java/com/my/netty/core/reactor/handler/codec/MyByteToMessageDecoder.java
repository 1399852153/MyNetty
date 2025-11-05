package com.my.netty.core.reactor.handler.codec;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.bytebuffer.netty.allocator.MyByteBufAllocator;
import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;

import java.util.List;

import static java.lang.Integer.MAX_VALUE;

/**
 * 基本copy自Netty的ByteToMessageDecoder类，但做了一些简化
 * */
public abstract class MyByteToMessageDecoder extends MyChannelEventHandlerAdapter {

    /**
     * Byte累积容器，用于积攒无法解码的半包Byte数据
     */
    private MyByteBuf cumulation;

    /**
     * 累积器
     */
    private Cumulator cumulator = MERGE_CUMULATOR;

    private boolean first;

    private int numReads;

    private int discardAfterReads = 16;

    /**
     * 将新接受到的ByteBuf in合并到cumulation中
     * 除此之外，netty中还有另一种累积器COMPOSITE_CUMULATOR，基于更复杂的ByteBuf容器CompositeByteBuf，所以MyNetty中没有实现
     * MERGE_CUMULATOR很好理解，就是把后面来的ByteBuf中的数据写入之前已有的ByteBuf中，这里需要进行内存数据的复制。
     * 而COMPOSITE_CUMULATOR中使用CompositeByteBuf可以做到几乎没有内存数据的复制。因为CompositeByteBuf通过一系列巧妙的映射计算，将实际上内存空间不连续的N个ByteBuf转换为了逻辑上连续的一个ByteBuf。
     * 因此MERGE_CUMULATOR合并时性能较差，但实际解码读取数据时性能更好。而COMPOSITE_CUMULATOR在合并时性能较好，而实际解码时性能较差。Netty中默认使用MERGE_CUMULATOR作为累加器。
     */
    public static final Cumulator MERGE_CUMULATOR = new Cumulator() {
        @Override
        public MyByteBuf cumulate(MyByteBufAllocator alloc, MyByteBuf cumulation, MyByteBuf in) {
            if (!cumulation.isReadable()
                //&& in.isContiguous()
            ) {
                // 已有的cumulation已经被读取完毕了，清理掉，直接用新来的in代替之前的cumulation

                // If cumulation is empty and input buffer is contiguous, use it directly
                // 目前已实现的MyByteBuf都是contiguous=true的
                cumulation.release();
                return in;
            }

            try {
                // 新来的ByteBuf一共有多少字节可供读取
                final int required = in.readableBytes();

                // 需要合并的字节数，超过了cumulation的当前的最大可写阈值，需要令cumulation扩容
                if (required > cumulation.maxWritableBytes() ||
                    required > cumulation.maxFastWritableBytes() && cumulation.refCnt() > 1) {
                    // Expand cumulation (by replacing it) under the following conditions:
                    // - cumulation cannot be resized to accommodate the additional data
                    // - cumulation can be expanded with a reallocation operation to accommodate but the buffer is
                    //   assumed to be shared (e.g. refCnt() > 1) and the reallocation may not be safe.
                    return expandCumulation(alloc, cumulation, in);
                } else {
                    // cumulation的空间足够，能够存放in中的数据，直接将in中的内容写入cumulation的尾部即可

                    // 因为目前只支持基于数组的heapByteBuf，所以直接in.array()
                    cumulation.writeBytes(in, in.readerIndex(), required);

                    // 将in设置为已读完(读指针等于写指针)
                    in.readerIndex(in.writerIndex());
                    return cumulation;
                }
            } finally {
                // We must release in all cases as otherwise it may produce a leak if writeBytes(...) throw
                // for whatever release (for example because of OutOfMemoryError)

                // in中的内容被写入到cumulation后，in需要被release回收掉，避免内存泄露
                in.release();
            }
        }
    };

    public MyByteToMessageDecoder() {
        // 解码器必须是channel级别的，不能channel间共享, 因为内部暂存的流数据是channel级别的，共享的话就全乱套了，这里做个校验
        ensureNotSharable();
    }

    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) throws Exception {
        // 顾名思义，ByteToMessageDecoder只处理Byte类型的数据
        if (msg instanceof MyByteBuf) {
            // 从Byte累积器中解码成功后的消息集合
            MyCodecOutputList out = MyCodecOutputList.newInstance();

            // 是否是第一次执行解码
            this.first = (this.cumulation == null);

            MyByteBufAllocator alloc = ctx.alloc();
            MyByteBuf targetCumulation;
            if (first) {
                // netty里使用Unpooled.EMPTY_BUFFER，性能更好
                targetCumulation = alloc.heapBuffer(0, 0);
            } else {
                targetCumulation = this.cumulation;
            }

            // 通过累积器，将之前已经收到的Byte消息与新收到的ByteBuf进行合并，得到一个合并后的新ByteBuf(合并的逻辑中涉及到诸如ByteBuf扩容、msg回收等操作)
            this.cumulation = this.cumulator.cumulate(alloc, targetCumulation, (MyByteBuf) msg);

            try {
                // 读取cumulation中的数据进行解码
                callDecode(ctx, cumulation, out);
            } finally {
                try {
                    if (cumulation != null && !cumulation.isReadable()) {
                        // 及时清理掉已经读取完成的累积器buf
                        numReads = 0;
                        cumulation.release();
                        cumulation = null;
                    } else if (++numReads >= discardAfterReads) {
                        // We did enough reads already try to discard some bytes, so we not risk to see a OOME.
                        // See https://github.com/netty/netty/issues/4275

                        // 当前已读取的次数超过了阈值，尝试对cumulation进行缩容(已读的内容清除掉，)
                        numReads = 0;
                        discardSomeReadBytes();
                    }

                    int size = out.size();
                    fireChannelRead(ctx, out, size);
                } finally {
                    // 后续的handler在处理完成消息后，将out列表回收掉
                    out.recycle();
                }
            }
        } else {
            // 非ByteBuf类型直接跳过该handler
            ctx.fireChannelRead(msg);
        }

    }

    @Override
    public void channelReadComplete(MyChannelHandlerContext ctx) {
        numReads = 0;

        // 完成了一次read操作，对cumulation进行缩容
        discardSomeReadBytes();

        // 省略了一些autoRead相关的逻辑

        ctx.fireChannelReadComplete();
    }

    protected final void discardSomeReadBytes() {
        if (cumulation != null && !first && cumulation.refCnt() == 1) {
            // discard some bytes if possible to make more room in the
            // buffer but only if the refCnt == 1  as otherwise the user may have
            // used slice().retain() or duplicate().retain().
            //
            // See:
            // - https://github.com/netty/netty/issues/2327
            // - https://github.com/netty/netty/issues/1764
            cumulation.discardSomeReadBytes();
        }
    }

    /**
     * 对oldCumulation进行扩容，并且将in中的数据写入到扩容后的ByteBuf中
     *
     * @return 返回扩容后，并且合并完成后的ByteBuf
     */
    static MyByteBuf expandCumulation(MyByteBufAllocator alloc, MyByteBuf oldCumulation, MyByteBuf in) {
        int oldBytes = oldCumulation.readableBytes();
        int newBytes = in.readableBytes();

        // 老的和新的可读字节总数
        int totalBytes = oldBytes + newBytes;
        // 基于totalBytes，分配出新的cumulation
        MyByteBuf newCumulation = alloc.heapBuffer(alloc.calculateNewCapacity(totalBytes, MAX_VALUE));
        MyByteBuf toRelease = newCumulation;
        try {
            // This avoids redundant checks and stack depth compared to calling writeBytes(...)

            // 用setBytes代替writeBytes，性能好一点，但是需要自己设置正确的写指针(因为setBytes不会自动推进写指针)
            newCumulation
                // 先写入oldCumulation的内容
                .setBytes(0, oldCumulation.array(), oldCumulation.readerIndex(), oldBytes)
                // 再写入in中的内容
                .setBytes(oldBytes, in.array(), in.readerIndex(), newBytes)
                // 再推进写指针
                .writerIndex(totalBytes);
            in.readerIndex(in.writerIndex());
            toRelease = oldCumulation;
            return newCumulation;
        } finally {
            toRelease.release();
        }
    }

    /**
     * 将ByteBuf in中的数据按照既定的规则进行decode解码操作，解码成功后的消息加入out列表
     * <p>
     * MyNetty暂不支持handler被remove，省略了判断当前handler是否已经被remove的逻辑(ctx.isRemoved()、decodeRemovalReentryProtection等)
     */
    protected void callDecode(MyChannelHandlerContext ctx, MyByteBuf in, List<Object> out) {
        try {
            while (in.isReadable()) {
                final int outSize = out.size();

                if (outSize > 0) {
                    // 当decode逻辑中成功解码了至少一个完整消息，触发fireChannelRead，将消息向后面的handler传递
                    fireChannelRead(ctx, out, outSize);
                    // 处理完成后，将out列表及时清理掉
                    out.clear();
                }

                int oldInputLength = in.readableBytes();
                // 调用子类实现的自定义解码逻辑
                decode(ctx, in, out);

                if (out.isEmpty()) {
                    if (oldInputLength == in.readableBytes()) {
                        break;
                    } else {
                        continue;
                    }
                }

                if (oldInputLength == in.readableBytes()) {
                    throw new RuntimeException(getClass() + ".decode() did not read anything but decoded a message.");
                }

//                if (isSingleDecode()) {
//                    break;
//                }
            }
        } catch (Exception cause) {
            throw new RuntimeException(cause);
        }
    }

    protected abstract void decode(MyChannelHandlerContext ctx, MyByteBuf in, List<Object> out) throws Exception;

    static void fireChannelRead(MyChannelHandlerContext ctx, List<Object> msgs, int numElements) {
        if (msgs instanceof MyCodecOutputList) {
            fireChannelRead(ctx, (MyCodecOutputList) msgs, numElements);
        } else {
            for (int i = 0; i < numElements; i++) {
                ctx.fireChannelRead(msgs.get(i));
            }
        }
    }

    static void fireChannelRead(MyChannelHandlerContext ctx, MyCodecOutputList msgs, int numElements) {
        for (int i = 0; i < numElements; i++) {
            ctx.fireChannelRead(msgs.getUnsafe(i));
        }
    }
}
