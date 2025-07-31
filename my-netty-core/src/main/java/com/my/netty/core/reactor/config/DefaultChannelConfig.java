package com.my.netty.core.reactor.config;

import com.my.netty.bytebuffer.netty.allocator.MyByteBufAllocator;
import com.my.netty.bytebuffer.netty.allocator.MyUnpooledByteBufAllocator;
import com.my.netty.threadlocal.impl.netty.MyDefaultThreadFactory;

/**
 * channel相关的配置，待后续拓展
 * */
public class DefaultChannelConfig {

    private int initialReceiveBufferSize = -1;

    private MyDefaultThreadFactory defaultThreadFactory = new MyDefaultThreadFactory();

    private MyByteBufAllocator allocator = new MyUnpooledByteBufAllocator();

    public int getInitialReceiveBufferSize() {
        return initialReceiveBufferSize;
    }

    public void setInitialReceiveBufferSize(int initialReceiveBufferSize) {
        this.initialReceiveBufferSize = initialReceiveBufferSize;
    }

    public MyDefaultThreadFactory getDefaultThreadFactory() {
        return defaultThreadFactory;
    }

    public void setDefaultThreadFactory(MyDefaultThreadFactory defaultThreadFactory) {
        this.defaultThreadFactory = defaultThreadFactory;
    }

    public MyByteBufAllocator getAllocator() {
        return allocator;
    }

    public void setAllocator(MyByteBufAllocator myByteBufAllocator) {
        this.allocator = myByteBufAllocator;
    }
}
