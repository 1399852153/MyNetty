package com.my.netty.bytebuffer.netty;

import com.my.netty.bytebuffer.netty.allocator.MyByteBufAllocator;
import com.my.netty.bytebuffer.netty.util.ByteBufUtil;
import com.my.netty.bytebuffer.netty.util.MathUtil;
import com.my.netty.bytebuffer.netty.util.SystemPropertyUtil;

import java.io.IOException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

public abstract class MyAbstractByteBuf extends MyByteBuf {

    int readerIndex;
    int writerIndex;
    private int markedReaderIndex;
    private int markedWriterIndex;
    private int maxCapacity;

    /**
     * 是否在编辑读/写指针的时候进行边界校验
     * 默认为true，设置为false可以不进行校验从而略微的提高性能，但可能出现内存越界的问题
     */
    private static final boolean checkBounds = SystemPropertyUtil.getBoolean("my.netty.check.bounds",true);

    protected MyAbstractByteBuf(int maxCapacity) {
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("maxCapacity must > 0");
        }
        this.maxCapacity = maxCapacity;
    }

    @Override
    public int maxCapacity() {
        return maxCapacity;
    }

    protected final void maxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    @Override
    public int readerIndex() {
        return readerIndex;
    }

    @Override
    public MyByteBuf readerIndex(int readerIndex) {
        if (checkBounds) {
            checkIndexBounds(readerIndex, writerIndex, capacity());
        }
        this.readerIndex = readerIndex;
        return this;
    }

    @Override
    public int writerIndex() {
        return writerIndex;
    }

    @Override
    public MyByteBuf writerIndex(int writerIndex) {
        if (checkBounds) {
            checkIndexBounds(readerIndex, writerIndex, capacity());
        }
        this.writerIndex = writerIndex;
        return this;
    }

    @Override
    public MyByteBuf setIndex(int readerIndex, int writerIndex) {
        if (checkBounds) {
            checkIndexBounds(readerIndex, writerIndex, capacity());
        }

        this.readerIndex = readerIndex;
        this.writerIndex = writerIndex;
        return this;
    }

    private static void checkIndexBounds(final int readerIndex, final int writerIndex, final int capacity) {
        // 要求满足 0 <= readIndex <= writeIndex <= capacity
        if (readerIndex < 0 || readerIndex > writerIndex || writerIndex > capacity) {
            throw new IndexOutOfBoundsException(String.format(
                    "readerIndex: %d, writerIndex: %d (expected: 0 <= readerIndex <= writerIndex <= capacity(%d))",
                    readerIndex, writerIndex, capacity));
        }
    }

    protected final void checkNewCapacity(int newCapacity) {
        if (checkBounds && (newCapacity < 0 || newCapacity > maxCapacity())) {
            throw new IllegalArgumentException("newCapacity: " + newCapacity +
                    " (expected: 0-" + maxCapacity() + ')');
        }
    }

    @Override
    public int readableBytes() {
        // readIndex与writeIndex之间，写了但还没有读的区间
        return writerIndex - readerIndex;
    }

    @Override
    public int writableBytes() {
        // writerIndex与capacity之间,整个byteBuf中还剩余的可写空间
        return capacity() - writerIndex;
    }

    @Override
    public int maxWritableBytes() {
        // writerIndex与maxCapacity之间,整个byteBuf中还剩余的可写空间
        return maxCapacity() - writerIndex;
    }

    @Override
    public MyByteBuf markReaderIndex() {
        markedReaderIndex = readerIndex;
        return this;
    }

    @Override
    public MyByteBuf resetReaderIndex() {
        readerIndex(markedReaderIndex);
        return this;
    }

    @Override
    public MyByteBuf markWriterIndex() {
        markedWriterIndex = writerIndex;
        return this;
    }

    @Override
    public MyByteBuf resetWriterIndex() {
        writerIndex(markedWriterIndex);
        return this;
    }

    @Override
    public byte getByte(int index) {
        checkIndex(index,1);
        return _getByte(index);
    }

    @Override
    public byte readByte() {
        // 检查是否可以读1个字节
        checkReadableBytes0(1);
        int i = readerIndex;
        byte b = _getByte(i);
        // 和jdk的实现一样，在getByte的基础上，推进读指针
        readerIndex = i + 1;
        return b;
    }


    protected abstract byte _getByte(int index);

    @Override
    public boolean getBoolean(int index) {
        return getByte(index) != 0;
    }

    @Override
    public int getInt(int index) {
        checkIndex(index, 4);
        return _getInt(index);
    }

    @Override
    public int getIntLE(int index) {
        checkIndex(index, 4);
        return _getIntLE(index);
    }

    @Override
    public int readInt() {
        // 检查是否可以读4个字节(int是32位，4个字节)
        checkReadableBytes0(4);
        int v = _getInt(readerIndex);

        // 和jdk的实现一样，在getByte的基础上，推进读指针
        readerIndex += 4;
        return v;
    }

    @Override
    public int readIntLE() {
        // 检查是否可以读4个字节(int是32位，4个字节)
        checkReadableBytes0(4);
        int v = _getIntLE(readerIndex);

        // 和jdk的实现一样，在getByte的基础上，推进读指针
        readerIndex += 4;
        return v;
    }

    protected abstract int _getInt(int index);

    protected abstract int _getIntLE(int index);

    @Override
    public long getUnsignedInt(int index) {
        return getInt(index) & 0xFFFFFFFFL;
    }

    @Override
    public long getUnsignedIntLE(int index) {
        return getIntLE(index) & 0xFFFFFFFFL;
    }

    @Override
    public MyByteBuf setByte(int index, int value) {
        checkIndex(index,1);
        _setByte(index, value);
        return this;
    }

    protected abstract void _setByte(int index, int value);

    public abstract MyByteBufAllocator alloc();

    @Override
    public MyByteBuf setBoolean(int index, boolean value) {
        setByte(index, value? 1 : 0);
        return this;
    }

    @Override
    public boolean readBoolean() {
        return readByte() != 0;
    }

    @Override
    public MyByteBuf writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
        return this;
    }

    @Override
    public MyByteBuf writeByte(int value) {
        ensureWritable0(1);
        _setByte(writerIndex++, value);
        return this;
    }

    @Override
    public MyByteBuf writeBytes(byte[] src) {
        return writeBytes(src, 0, src.length);
    }

    @Override
    public MyByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        ensureWritable0(length);
        setBytes(writerIndex, src, srcIndex, length);
        writerIndex += length;
        return this;
    }

    @Override
    public MyByteBuf writeBytes(MyByteBuf src) {
        writeBytes(src, src.readableBytes());
        return this;
    }

    private MyByteBuf writeBytes(MyByteBuf src, int length) {
        writeBytes(src, src.readerIndex(), length);
        src.readerIndex(src.readerIndex() + length);
        return this;
    }

    private MyByteBuf writeBytes(MyByteBuf src, int srcIndex, int length) {
        // 只支持heapByteBuf，所以直接写入array
        setBytes(writerIndex, src, srcIndex, length);
        writerIndex += length;
        return this;
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        // checkReadableBytes(length);

        int readBytes = getBytes(readerIndex, out, length);
        readerIndex += readBytes;
        return readBytes;
    }

    @Override
    public MyByteBuf readBytes(MyByteBuf dst) {
        readBytes(dst, dst.writableBytes());
        return this;
    }

    private MyByteBuf readBytes(MyByteBuf dst, int length) {
        readBytes(dst, dst.writerIndex(), length);
        dst.writerIndex(dst.writerIndex() + length);
        return this;
    }

    private MyByteBuf readBytes(MyByteBuf dst, int dstIndex, int length) {
        // 只支持heapByteBuf，所以直接写入array
        getBytes(readerIndex, dst, dstIndex, length);
        readerIndex += length;
        return this;
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        ensureWritable0(length);
        int writtenBytes = setBytes(writerIndex, in, length);
        if (writtenBytes > 0) {
            writerIndex += writtenBytes;
        }
        return writtenBytes;
    }

    @Override
    public boolean isReadable() {
        return writerIndex > readerIndex;
    }

    @Override
    public String toString(Charset charset) {
        return toString(readerIndex, readableBytes(), charset);
    }

    @Override
    public MyByteBuf discardSomeReadBytes() {
        if (readerIndex > 0) {
            // 所有已写的内容，都全部被读取完成了，则将读写指针都重置为0
            if (readerIndex == writerIndex) {
                adjustMarkers(readerIndex);
                writerIndex = readerIndex = 0;
                return this;
            }

            // 读指针已经超过了当前容量的50%，将当前已读的内容删除，未读的内容整体平移到前面去
            // 读指针设置为0，写指针减去读指针
            if (readerIndex >= capacity() >>> 1) {
                // 因为目前只支持基于数组的heapByteBuf，所以直接this.array()
                setBytes(0, this.array(), readerIndex, writerIndex - readerIndex);
                writerIndex -= readerIndex;
                adjustMarkers(readerIndex);
                readerIndex = 0;
                return this;
            }
        }

        return this;
    }

    @Override
    public MyByteBuf skipBytes(int length) {
        readerIndex += length;
        return this;
    }

    private void adjustMarkers(int decrement) {
        if (markedReaderIndex <= decrement) {
            markedReaderIndex = 0;
            if (markedWriterIndex <= decrement) {
                markedWriterIndex = 0;
            } else {
                markedWriterIndex -= decrement;
            }
        } else {
            markedReaderIndex -= decrement;
            markedWriterIndex -= decrement;
        }
    }

    private String toString(int index, int length, Charset charset) {
        return ByteBufUtil.decodeString(this, index, length, charset);
    }

    final void setIndex0(int readerIndex, int writerIndex) {
        this.readerIndex = readerIndex;
        this.writerIndex = writerIndex;
    }

    final void discardMarks() {
        // 将markedReaderIndex和markedWriterIndex两个标记清零
        markedReaderIndex = markedWriterIndex = 0;
    }

    protected final void checkIndex(int index, int fieldLength) {
        checkIndex0(index, fieldLength);
    }

    final void checkIndex0(int index, int fieldLength) {
        if (checkBounds) {
            checkRangeBounds("index", index, fieldLength, capacity());
        }
    }

    private static void checkRangeBounds(final String indexName, final int index,
                                         final int fieldLength, final int capacity) {
        if (MathUtil.isOutOfBounds(index, fieldLength, capacity)) {
            throw new IndexOutOfBoundsException(String.format(
                    "%s: %d, length: %d (expected: range(0, %d))", indexName, index, fieldLength, capacity));
        }
    }

    private void checkReadableBytes0(int minimumReadableBytes) {
        if (checkBounds && readerIndex > writerIndex - minimumReadableBytes) {
            throw new IndexOutOfBoundsException(String.format(
                    "readerIndex(%d) + length(%d) exceeds writerIndex(%d): %s",
                    readerIndex, minimumReadableBytes, writerIndex, this));
        }
    }

    final void ensureWritable0(int minWritableBytes) {
        // 当前写指针
        final int writerIndex = writerIndex();
        // 预期写入后的容量
        final int targetCapacity = writerIndex + minWritableBytes;
        if (targetCapacity >= 0 & targetCapacity <= capacity()) {
            // 当前capacity容量能够满足要求，直接返回
            return;
        }

        if (checkBounds && (targetCapacity < 0 || targetCapacity > maxCapacity)) {
            throw new IndexOutOfBoundsException(String.format(
                "writerIndex(%d) + minWritableBytes(%d) exceeds maxCapacity(%d): %s",
                writerIndex, minWritableBytes, maxCapacity, this));
        }

        // 当前capacity不满足需求，需要扩容

        // 获得当前buf逻辑上可以直接扩容的字节数(无需重新复制当前数据到另一块更大的内存块中，重新分配)
        final int fastWritable = maxFastWritableBytes();
        int newCapacity;
        if(fastWritable >= minWritableBytes){
            // 能直接满足，增大当前capacity即可
            newCapacity = writerIndex + fastWritable;
        }else{
            // 可能需要扩容
            newCapacity = alloc().calculateNewCapacity(targetCapacity, maxCapacity);
        }

        // Adjust to the new capacity.
        // 内部实现比较复杂
        capacity(newCapacity);
    }

}
