package com.my.netty.bytebuffer.netty.allocator;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.bytebuffer.netty.MyUnPooledHeapByteBuf;

public class MyUnpooledByteBufAllocator extends MyAbstractByteBufAllocator{
    @Override
    protected MyByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        // 当前版本的allocator只是个架子，主要还是方便后续PooledByteBuf实现
        return new MyUnPooledHeapByteBuf(this, initialCapacity, maxCapacity);
    }
}
