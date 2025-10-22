package com.my.netty.core.reactor.handler.codec;


import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.bytebuffer.netty.allocator.MyByteBufAllocator;

public interface Cumulator {

    /**
     * Cumulate the given {@link MyByteBuf}s and return the {@link MyByteBuf} that holds the cumulated bytes.
     * The implementation is responsible to correctly handle the life-cycle of the given {@link MyByteBuf}s and so
     * call {@link MyByteBuf#release()} if a {@link MyByteBuf} is fully consumed.
     */
    MyByteBuf cumulate(MyByteBufAllocator alloc, MyByteBuf cumulation, MyByteBuf in);
}
