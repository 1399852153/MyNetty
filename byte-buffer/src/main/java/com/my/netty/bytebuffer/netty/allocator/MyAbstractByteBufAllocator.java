package com.my.netty.bytebuffer.netty.allocator;


import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.bytebuffer.netty.util.MathUtil;

public abstract class MyAbstractByteBufAllocator implements MyByteBufAllocator{

    static final int DEFAULT_INIT_CAPACITY = 256;
    static final int DEFAULT_MAX_CAPACITY = Integer.MAX_VALUE;

    static final int CALCULATE_THRESHOLD = 1048576 * 4; // 4 MiB page

    @Override
    public MyByteBuf heapBuffer() {
        // 以默认参数值创建一个heapBuffer
        return newHeapBuffer(DEFAULT_INIT_CAPACITY,DEFAULT_MAX_CAPACITY);
    }

    @Override
    public MyByteBuf heapBuffer(int initialCapacity) {
        return heapBuffer(initialCapacity,DEFAULT_MAX_CAPACITY);
    }

    @Override
    public MyByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        // 简单起见，不实现netty里空buf优化

        // capacity参数校验
        validate(initialCapacity, maxCapacity);

        return newHeapBuffer(initialCapacity,maxCapacity);
    }

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        // 不能超过buf的最大容量
        if (minNewCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                "minNewCapacity: %d (expected: not greater than maxCapacity(%d)",
                minNewCapacity, maxCapacity));
        }

        final int threshold = CALCULATE_THRESHOLD; // 4 MiB page

        if (minNewCapacity == threshold) {
            return threshold;
        }

        // If over threshold, do not double but just increase by threshold.
        if (minNewCapacity > threshold) {
            // 所申请的内存高于阈值，保守一点扩容，以提高空间利用率
            int newCapacity = minNewCapacity / threshold * threshold;
            if (newCapacity > maxCapacity - threshold) {
                // 预期扩容后的容量已经超过maxCapacity了，以maxCapacity为准
                newCapacity = maxCapacity;
            } else {
                // 以threshold为基准扩容
                newCapacity += threshold;
            }
            return newCapacity;
        }else{
            // 所申请的内存低于阈值，可以激进一点成倍的扩容，以提高性能
            // 64 <= newCapacity is a power of 2 <= threshold
            final int newCapacity = MathUtil.findNextPositivePowerOfTwo(Math.max(minNewCapacity, 64));
            return Math.min(newCapacity, maxCapacity);
        }
    }

    /**
     * Create a heap {@link MyByteBuf} with the given initialCapacity and maxCapacity.
     */
    protected abstract MyByteBuf newHeapBuffer(int initialCapacity, int maxCapacity);

    private static void validate(int initialCapacity, int maxCapacity) {
        if (initialCapacity < 0){
            throw new IllegalArgumentException("initialCapacity (expected: >= 0)");

        }

        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity: %d (expected: not greater than maxCapacity(%d)",
                    initialCapacity, maxCapacity));
        }
    }

}
