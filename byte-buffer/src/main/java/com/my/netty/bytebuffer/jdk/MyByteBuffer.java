package com.my.netty.bytebuffer.jdk;

import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;

public abstract class MyByteBuffer extends MyBuffer {

    // These fields are declared here rather than in Heap-X-Buffer in order to
    // reduce the number of virtual method invocations needed to access these
    // values, which is especially costly when coding small buffers.
    //
    final byte[] hb;                  // Non-null only for heap buffers
    final int offset;
    boolean isReadOnly;                 // Valid only for heap buffers

    MyByteBuffer(int mark, int pos, int lim, int cap, byte[] hb, int offset) {
        super(mark, pos, lim, cap);

        this.hb = hb;
        this.offset = offset;
    }

    /**
     * 创建一个指定了capacity的堆内ByteBuffer
     * */
    public static MyByteBuffer allocate(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException();
        }
        return new MyHeapByteBuffer(capacity, capacity);
    }

    /**
     * 相对读操作
     * */
    public abstract byte get();

    /**
     * 绝对读操作
     * */
    public abstract byte get(int index);

    /**
     * 相对写操作
     * */
    public abstract MyByteBuffer put(byte b);

    /**
     * 绝对写操作
     * */
    public abstract MyByteBuffer put(int index, byte b);

    /**
     * 相对的批量读操作
     * 将当前buffer的length个字节，写入指定dst数组。写入的起始下标位置是offset
     * */
    public MyByteBuffer get(byte[] dst, int offset, int length) {
        checkBounds(offset, length, dst.length);
        if (length > remaining()) {
            // 所要读取的字节数不能超过当前buffer总的剩余可读取数量
            throw new BufferUnderflowException();
        }
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            dst[i] = get();
        }
        return this;
    }

    /**
     * 相对的批量读操作
     * 将当前buffer中的数据写入指定dst数组。写入的起始下标是0，length为dst的总长度
     * */
    public MyByteBuffer get(byte[] dst) {
        return get(dst, 0, dst.length);
    }

    /**
     * 压缩操作(也算写操作)
     * */
    public abstract MyByteBuffer compact();

    /**
     * 默认是大端
     * */
    boolean bigEndian = true;

    public final ByteOrder order() {
        return bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
    }

    // char、int、double、float、char、long、short

    public abstract int getInt();

    public abstract int getInt(int index);

    public abstract MyByteBuffer putInt(int value);

    public abstract MyByteBuffer putInt(int index, int value);

    // Unchecked accessors, for use by ByteBufferAs-X-Buffer classes
    abstract byte _get(int i);                          // package-private
    abstract void _put(int i, byte b);                  // package-private

}
