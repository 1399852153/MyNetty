package com.my.netty.core.reactor.channel.buffer;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.core.reactor.channel.MyNioChannel;

import java.util.concurrent.CompletableFuture;

class MyChannelOutBoundBufferEntry {

    // Assuming a 64-bit JVM:
    //  - 16 bytes object header
    //  - 6 reference fields
    //  - 2 long fields
    //  - 2 int fields
    //  - 1 boolean field
    //  - padding
    //  netty中ChannelOutboundBuffer的Entry对象属性较多，16 + 6*8 + 2*8 + 2*4 + 1 = 89
    //  往大了计算，未开启指针压缩时，64位机器按照8的倍数向上取整，算出填充默认需要96字节(netty中可通过系统参数(io.netty.transport.outboundBufferEntrySizeOverhead)动态配置)
    //  MyNetty做了简化，暂时没那么属性，但这里就不改了，只是多浪费了一些空间
    //  详细的计算方式可参考大佬的博客：https://www.cnblogs.com/binlovetech/p/16453634.html
    private static final int DEFAULT_CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD = 96;

    MyChannelOutBoundBufferEntry next;
    MyByteBuf msg;
    CompletableFuture<MyNioChannel> completableFuture;
    int msgSize;
    int pendingSize;
    boolean cancelled;

    static MyChannelOutBoundBufferEntry newInstance(MyByteBuf msg, int msgSize, CompletableFuture<MyNioChannel> completableFuture) {
        // 简单起见，暂时不使用对象池，直接new
        MyChannelOutBoundBufferEntry entry = new MyChannelOutBoundBufferEntry();
        entry.msg = msg;
        entry.msgSize = msgSize;
        // entry实际的大小 = 消息体的大小 + 对象头以及各个属性值占用的大小
        entry.pendingSize = msgSize + DEFAULT_CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD;
        entry.completableFuture = completableFuture;
        return entry;
    }

    void cancel() {
        if (!cancelled) {
            cancelled = true;
        }
    }

    @Override
    public String toString() {
        return "MyChannelOutBoundBufferEntry{" +
            "msg=" + msg +
            ", pendingSize=" + pendingSize +
            ", cancelled=" + cancelled +
            '}';
    }
}
