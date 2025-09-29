package com.my.netty.bytebuffer.netty;


import com.my.netty.bytebuffer.netty.allocator.MyByteBufAllocator;
import com.my.netty.bytebuffer.netty.allocator.MyPoolChunk;
import com.my.netty.bytebuffer.netty.allocator.MyPoolThreadCache;
import com.my.netty.bytebuffer.netty.objectpool.MyObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

public abstract class MyPooledByteBuf<T> extends MyAbstractReferenceCountedByteBuf{

    private static final Logger logger = LoggerFactory.getLogger(MyPooledByteBuf.class);

    protected long handle;
    protected T memory;
    protected int length;

    protected MyPoolChunk<T> chunk;
    private MyByteBufAllocator allocator;
    protected int offset;
    int maxLength;

    private final MyObjectPool.Handle<MyPooledByteBuf<T>> recyclerHandle;
    ByteBuffer tmpNioBuf;

    private MyPoolThreadCache threadCache;


    @SuppressWarnings("unchecked")
    protected MyPooledByteBuf(MyObjectPool.Handle<? extends MyPooledByteBuf<T>> recyclerHandle, int maxCapacity) {
        super(maxCapacity);
        this.recyclerHandle = (MyObjectPool.Handle<MyPooledByteBuf<T>>) recyclerHandle;
    }

    public void init(MyPoolChunk<T> chunk, ByteBuffer nioBuffer,
                     long handle, int offset, int length, int maxLength, MyPoolThreadCache threadCache) {
        logger.info("init MyPooledByteBuf, use chunk={},offset={}",chunk,offset);

        init0(chunk, nioBuffer, handle, offset, length, maxLength, threadCache);
    }

    public void initUnPooled(MyPoolChunk<T> chunk, int length) {
        // UnPooled，没有对应的handle，maxlength等于length
        init0(chunk, null, 0, 0, length, length, null);
    }

    private void init0(MyPoolChunk<T> chunk, ByteBuffer nioBuffer,
                       long handle, int offset, int length, int maxLength, MyPoolThreadCache threadCache) {

        chunk.incrementPinnedMemory(maxLength);
        this.chunk = chunk;
        memory = chunk.getMemory();
        tmpNioBuf = nioBuffer;
        allocator = chunk.getArena().getParent();
        this.threadCache = threadCache;
        this.handle = handle;
        this.offset = offset;
        this.length = length;
        this.maxLength = maxLength;
    }

    /**
     * 一个PooledByteBuf在回归对象池后，再重新被拿出来作为一个新的ByteBuf使用。需要进行一系列的重置操作
     * */
    final void reuse(int maxCapacity) {
        // 设置新的maxCapacity
        maxCapacity(maxCapacity);
        // 释放后，再重新被复用，refCnt重置为1
        setRefCnt(1);

        // 读指针，写指针，以及对应的marks指针都设置为0
        this.readerIndex(0);
        this.writerIndex(0);
        discardMarks();
    }

    @Override
    protected final void deallocate() {
        if (handle >= 0) {
            logger.info("PooledByteBuf do deallocate handle={},chunk={}",handle,chunk);

            final long handle = this.handle;
            this.handle = -1;
            memory = null;
            chunk.decrementPinnedMemory(maxLength);
            chunk.getArena().free(chunk, handle, maxLength, threadCache);
            tmpNioBuf = null;
            chunk = null;
            recycle();
        }
    }

    protected final int idx(int index) {
        return offset + index;
    }

    private void recycle() {
        recyclerHandle.recycle(this);
    }

    @Override
    public int capacity() {
        return length;
    }

    @Override
    public MyByteBuf capacity(int newCapacity) {
        if (newCapacity == length) {
            return this;
        }

        checkNewCapacity(newCapacity);

        if (!chunk.isUnpooled()) {
            // 是池化的Chunk(small和normal级别的PooledBuf)
            // 有一种场景，比如用户申请的是4000字节，而实际上池化分配是按照固定规格级别来处理的，4000对应的规格其实是4096(maxLength=4096)
            // 那么当用户申请的buf在使用了4000字节后，想要再扩容100字节，其实不需要重新分配，而仅仅调整length即可，因为底层承载的内存块是大于4100的
            // 这样能略微的提高一些性能
            if (newCapacity > length) {
                if (newCapacity <= maxLength) {
                    length = newCapacity;
                    return this;
                }
            } else if (newCapacity > maxLength >>> 1 &&
                (maxLength > 512 || newCapacity > maxLength - 16)) {
                // 缩容时的考虑，如果缩容后与原容量差别不大，就不用真正的去缩容来节约内存占用。
                // 因为重新分配一个更小的内存块也是有开销的
                length = newCapacity;
                // 缩容后，重新标定读写指针，避免越界
                trimIndicesToCapacity(newCapacity);
                return this;
            }
        }

        // Reallocation required.
        chunk.decrementPinnedMemory(maxLength);
        // 从arena中重新分配，得到一个新容量的
        chunk.getArena().reallocate(this, newCapacity);
        return this;
    }

    // Called after a capacity reduction
    public final void trimIndicesToCapacity(int newCapacity) {
        if (writerIndex() > newCapacity) {
            setIndex0(Math.min(readerIndex(), newCapacity), newCapacity);
        }
    }

    public MyPoolChunk<T> getChunk() {
        return chunk;
    }

    public int getLength() {
        return length;
    }

    public long getHandle() {
        return handle;
    }

    public T getMemory() {
        return memory;
    }

    public int getOffset() {
        return offset;
    }

    public MyPoolThreadCache getThreadCache() {
        return threadCache;
    }

    public int getMaxLength() {
        return maxLength;
    }

    @Override
    public MyByteBufAllocator alloc() {
        return allocator;
    }

    @Override
    public final int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return out.write(duplicateInternalNioBuffer(index, length));
    }

    @Override
    public final int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        try {
            return in.read(internalNioBuffer(index, length));
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }

    @Override
    public int maxFastWritableBytes() {
        return Math.min(maxLength, maxCapacity()) - writerIndex;
    }

    protected abstract ByteBuffer newInternalNioBuffer(T memory);

    protected abstract ByteBuffer duplicateInternalNioBuffer(int index, int length);

    @Override
    public final ByteBuffer internalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return _internalNioBuffer(index, length, false);
    }

    final ByteBuffer _internalNioBuffer(int index, int length, boolean duplicate) {
        index = idx(index);
        ByteBuffer buffer = duplicate ? newInternalNioBuffer(memory) : internalNioBuffer();
        buffer.limit(index + length).position(index);
        return buffer;
    }

    protected final ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = newInternalNioBuffer(memory);
        } else {
            tmpNioBuf.clear();
        }
        return tmpNioBuf;
    }
}