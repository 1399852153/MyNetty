package com.my.netty.bytebuffer.netty.allocator;


import com.my.netty.bytebuffer.netty.MyByteBuf;

public interface MyByteBufAllocator {

    /**
     * Allocate a heap {@link MyByteBuf} with the given initial capacity.
     */
    MyByteBuf heapBuffer();

    /**
     * Allocate a heap {@link MyByteBuf} with the given initial capacity.
     */
    MyByteBuf heapBuffer(int initialCapacity);

    /**
     * Allocate a heap {@link MyByteBuf} with the given initial capacity and the given
     * maximal capacity.
     */
    MyByteBuf heapBuffer(int initialCapacity, int maxCapacity);

    /**
     * Calculate the new capacity of a {@link MyByteBuf} that is used when a {@link MyByteBuf} needs to expand by the
     * {@code minNewCapacity} with {@code maxCapacity} as upper-bound.
     */
    int calculateNewCapacity(int minNewCapacity, int maxCapacity);
}
