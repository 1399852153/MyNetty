package com.my.netty.bytebuffer.netty;


import com.my.netty.bytebuffer.netty.objectpool.MyObjectPool;
import com.my.netty.bytebuffer.netty.util.BitsUtil;

import java.nio.ByteBuffer;

public class MyPooledHeapByteBuf extends MyPooledByteBuf<byte[]>{

    private static final MyObjectPool<MyPooledHeapByteBuf> RECYCLER = MyObjectPool.newPool(
        new MyObjectPool.ObjectCreator<MyPooledHeapByteBuf>() {
            @Override
            public MyPooledHeapByteBuf newObject(MyObjectPool.Handle<MyPooledHeapByteBuf> handle) {
                return new MyPooledHeapByteBuf(handle, 0);
            }
        });

    MyPooledHeapByteBuf(MyObjectPool.Handle<? extends MyPooledHeapByteBuf> recyclerHandle, int maxCapacity) {
        super(recyclerHandle, maxCapacity);
    }

    public static MyPooledHeapByteBuf newInstance(int maxCapacity) {
        MyPooledHeapByteBuf buf = RECYCLER.get();
        buf.reuse(maxCapacity);
        return buf;
    }

    @Override
    protected byte _getByte(int index) {
        return memory[idx(index)];
    }

    @Override
    protected int _getInt(int index) {
        return BitsUtil.getInt(memory, idx(index));
    }

    @Override
    protected int _getIntLE(int index) {
        return BitsUtil.getIntLE(memory, idx(index));
    }

    @Override
    protected void _setByte(int index, int value) {
        // 逻辑下标值为index，基于偏移量计算出最终的实际下标值
        int finallyIndex = idx(index);
        this.memory[finallyIndex] = (byte) value;
    }

    @Override
    public final MyByteBuf setBytes(int index, MyByteBuf src, int srcIndex, int length) {
        // 带上src的偏移量
        setBytes(index, src.array(), src.arrayOffset() + srcIndex, length);
        return this;
    }

    @Override
    public final MyByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        System.arraycopy(src, srcIndex, memory, idx(index), length);
        return this;
    }

    @Override
    public byte[] array() {
        return this.memory;
    }

    @Override
    public int arrayOffset() {
        return offset;
    }

    @Override
    public final MyByteBuf getBytes(int index, MyByteBuf dst, int dstIndex, int length) {
        // 带上dst的偏移量
        getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length);
        return this;
    }

    @Override
    public final MyByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        int srcPos = idx(index);
        System.arraycopy(memory, srcPos, dst, dstIndex, length);
        return this;
    }

    @Override
    protected final ByteBuffer newInternalNioBuffer(byte[] memory) {
        return ByteBuffer.wrap(memory);
    }

    @Override
    protected final ByteBuffer duplicateInternalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return ByteBuffer.wrap(memory, idx(index), length).slice();
    }
}
