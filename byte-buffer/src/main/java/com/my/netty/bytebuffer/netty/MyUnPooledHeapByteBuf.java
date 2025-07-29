package com.my.netty.bytebuffer.netty;


import com.my.netty.bytebuffer.netty.allocator.MyByteBufAllocator;
import com.my.netty.bytebuffer.netty.util.BitsUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * 参考自netty的UnpooledHeapByteBuf，在其基础上做了简化(只实现了最基础的一些功能以作参考)
 * */
public class MyUnPooledHeapByteBuf extends MyAbstractReferenceCountedByteBuf{

    private final MyByteBufAllocator alloc;

    private ByteBuffer tmpNioBuf;

    public static final byte[] EMPTY_BYTES = {};
    private byte[] array;

    public MyUnPooledHeapByteBuf(MyByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(maxCapacity);

        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity(%d) > maxCapacity(%d)", initialCapacity, maxCapacity));
        }

        this.alloc = alloc;
        this.array = new byte[initialCapacity];
        setIndex(0, 0);
    }

    @Override
    public int capacity() {
        // heapByteBuf的capacity就是内部数组的长度
        return array.length;
    }

    @Override
    public MyByteBuf capacity(int newCapacity) {
        checkNewCapacity(newCapacity);

        byte[] oldArray = array;
        int oldCapacity = oldArray.length;
        if (newCapacity == oldCapacity) {
            // 特殊情况，如果与之前的容量一样则无事发生
            return this;
        }

        int bytesToCopy;
        if (newCapacity > oldCapacity) {
            // 如果新的capacity比之前的大，那么就将原来内部数组中的内容整个copy到新数组中
            bytesToCopy = oldCapacity;
        } else {
            // 如果新的capacity比之前的小，那么可能需要截断之前的数组内容
            if (writerIndex() > newCapacity) {
                // 写指针大于newCapacity，确定需要截断
                this.readerIndex = Math.min(readerIndex(), newCapacity);
                this.writerIndex = newCapacity;
            }
            bytesToCopy = newCapacity;
        }

        // 将原始内部数组中的内容copy到新数组中
        byte[] newArray = new byte[newCapacity];
        System.arraycopy(oldArray, 0, newArray, 0, bytesToCopy);
        this.array = newArray;
        return this;
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
//        checkReadableBytes(length);
        int readBytes = getBytes(readerIndex, out, length, true);
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return getBytes(index, out, length, false);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        try {
            return in.read((ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length));
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }

    @Override
    public byte[] array() {
        return this.array;
    }

    @Override
    public int arrayOffset() {
        // 非Pool的，没有偏移量
        return 0;
    }

    @Override
    public MyByteBuf getBytes(int index, MyByteBuf dst, int dstIndex, int length) {
        // 带上dst的偏移量
        getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length);
        return this;
    }

    @Override
    public MyByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        System.arraycopy(array, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public MyByteBuf setBytes(int index, MyByteBuf src, int srcIndex, int length) {
        // 带上src的偏移量
        setBytes(index, src.array(), src.arrayOffset() + srcIndex, length);
        return this;
    }


    @Override
    public MyByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        System.arraycopy(src, srcIndex, array, index, length);
        return this;
    }

    @Override
    protected byte _getByte(int index) {
        return array[index];
    }

    @Override
    protected void _setByte(int index, int value) {
        this.array[index] = (byte) value;
    }

    @Override
    public MyByteBufAllocator alloc() {
        return alloc;
    }

    @Override
    protected int _getInt(int index) {
        return BitsUtil.getInt(this.array,index);
    }

    @Override
    protected int _getIntLE(int index) {
        return BitsUtil.getIntLE(this.array,index);
    }

    @Override
    protected void deallocate() {
        // heapByteBuf的回收很简单，就是清空内部数组，等待gc回收掉原来的数组对象即可
        array = EMPTY_BYTES;
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return (ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length);
    }

    private ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = ByteBuffer.wrap(array);
        }
        return tmpNioBuf;
    }

    private int getBytes(int index, GatheringByteChannel out, int length, boolean internal) throws IOException {
        ByteBuffer tmpBuf;
        if (internal) {
            tmpBuf = internalNioBuffer();
        } else {
            tmpBuf = ByteBuffer.wrap(array);
        }
        return out.write((ByteBuffer) tmpBuf.clear().position(index).limit(index + length));
    }
}
