package com.my.netty.bytebuffer.netty.allocator;


import com.my.netty.bytebuffer.netty.MyPooledByteBuf;
import com.my.netty.bytebuffer.netty.MyPooledHeapByteBuf;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

public abstract class MyPoolArena<T> {

    final MyPooledByteBufAllocator parent;
    final MySizeClasses mySizeClasses;

    private final MyPoolChunkList<T> q050;
    private final MyPoolChunkList<T> q025;
    private final MyPoolChunkList<T> q000;
    private final MyPoolChunkList<T> qInit;
    private final MyPoolChunkList<T> q075;
    private final MyPoolChunkList<T> q100;

    private final ReentrantLock lock = new ReentrantLock();

    public MyPoolArena(MyPooledByteBufAllocator parent) {
        this.parent = parent;
        this.mySizeClasses = new MySizeClasses();

        int chunkSize = this.mySizeClasses.getChunkSize();

        // 从小到大，使用率区间相近的PoolChunkList进行关联，组成双向链表(使用率区间有重合，论文中提到是设置磁滞效应，避免使用率小幅波动时反复移动位置，以提高性能)
        // qInit <=> q000(1%-50%) <=> q025(25%-75%) <=> q050(50%-100%) <=> q075(75%-100%) <=> q100(100%)
        this.q100 = new MyPoolChunkList<T>(this, null, 100, Integer.MAX_VALUE, chunkSize);
        this.q075 = new MyPoolChunkList<T>(this, q100, 75, 100, chunkSize);
        this.q050 = new MyPoolChunkList<T>(this, q075, 50, 100, chunkSize);
        this.q025 = new MyPoolChunkList<T>(this, q050, 25, 75, chunkSize);
        this.q000 = new MyPoolChunkList<T>(this, q025, 1, 50, chunkSize);
        this.qInit = new MyPoolChunkList<T>(this, q000, Integer.MIN_VALUE, 25, chunkSize);

        this.q100.setPrevList(q075);
        this.q075.setPrevList(q050);
        this.q050.setPrevList(q025);
        this.q025.setPrevList(q000);
        this.q000.setPrevList(null);
        this.qInit.setPrevList(qInit);

        // 简单起见，PoolChunkListMetric暂不实现
    }

    /**
     * 从当前PoolArena中申请分配内存，并将其包装成一个PooledByteBuf返回
     * */
    MyPooledByteBuf<T> allocate(int reqCapacity, int maxCapacity) {
        // 从对象池中获取缓存的PooledByteBuf对象
        MyPooledByteBuf<T> buf = newByteBuf(maxCapacity);
        // 为其分配底层数组对应的内存
        allocate(buf, reqCapacity);
        return buf;
    }

    public void reallocate(MyPooledByteBuf<T> buf, int newCapacity) {
        final int oldCapacity;
        final MyPoolChunk<T> oldChunk;
        final ByteBuffer oldNioBuffer;
        final long oldHandle;
        final T oldMemory;
        final int oldOffset;
        final int oldMaxLength;
//        final PoolThreadCache oldCache;

        // We synchronize on the ByteBuf itself to ensure there is no "concurrent" reallocations for the same buffer.
        // We do this to ensure the ByteBuf internal fields that are used to allocate / free are not accessed
        // concurrently. This is important as otherwise we might end up corrupting our internal state of our data
        // structures.
        //
        // Also note we don't use a Lock here but just synchronized even tho this might seem like a bad choice for Loom.
        // This is done to minimize the overhead per ByteBuf. The time this would block another thread should be
        // relative small and so not be a problem for Loom.
        // See https://github.com/netty/netty/issues/13467
        synchronized (buf) {
            oldCapacity = buf.getLength();
            if (oldCapacity == newCapacity) {
                return;
            }

            oldChunk = buf.getChunk();
//            oldNioBuffer = buf.tmpNioBuf;
            oldHandle = buf.getHandle();
            oldMemory = buf.getMemory();
            oldOffset = buf.getOffset();
            oldMaxLength = buf.getMaxLength();
//            oldCache = buf.cache;

            // This does not touch buf's reader/writer indices
            allocate(buf, newCapacity);
        }
        int bytesToCopy;
        if (newCapacity > oldCapacity) {
            bytesToCopy = oldCapacity;
        } else {
            buf.trimIndicesToCapacity(newCapacity);
            bytesToCopy = newCapacity;
        }
        memoryCopy(oldMemory, oldOffset, buf, bytesToCopy);
        free(oldChunk, oldHandle, oldMaxLength);
    }

    /**
     * 释放回收属于当前PoolArena特定Chunk中的某个内存段
     * */
    public void free(MyPoolChunk<T> chunk, long handle, int normCapacity) {
        freeChunk(chunk, handle, normCapacity);
    }

    void freeChunk(MyPoolChunk<T> chunk, long handle, int normCapacity) {
        final boolean destroyChunk;
        lock();
        try {
            // 将handle对应的内存段释放掉。如果释放后内存使用率发生变化超过了最小使用率的临界值，MyPoolChunkList#free方法内部会将其移动到合适的PoolChunkList中
            // 特别的，当chunk释放掉当前handle内存段后已经完全空闲，
            destroyChunk = !chunk.parent.free(chunk, handle, normCapacity);
        } finally {
            unlock();
        }

        if (destroyChunk) {
            // destroyChunk not need to be called while holding the synchronized lock.
            destroyChunk(chunk);
        }
    }

    public MyPooledByteBufAllocator getParent() {
        return parent;
    }

    private void allocate(MyPooledByteBuf<T> buf, final int reqCapacity) {
        final MySizeClassesMetadataItem sizeClassesMetadataItem = mySizeClasses.size2SizeIdx(reqCapacity);

        switch (sizeClassesMetadataItem.getSizeClassEnum()){
            case SMALL:
                // 暂不支持
                throw new RuntimeException("not support small size allocate! reqCapacity=" + reqCapacity);
                // tcacheAllocateSmall(buf, reqCapacity, sizeIdx);
            case NORMAL:
                tcacheAllocateNormal(buf, reqCapacity, sizeClassesMetadataItem);
                return;
            case HUGE:
                // 超过了PoolChunk大小的内存分配就是Huge级别的申请，每次分配使用单独的非池化的新PoolChunk来承载
                allocateHuge(buf, reqCapacity);
        }
    }

    private void tcacheAllocateNormal(MyPooledByteBuf<T> buf, final int reqCapacity, final MySizeClassesMetadataItem sizeClassesMetadataItem) {
        // todo 基于线程本地缓存获取，暂不实现

        lock();
        try {
            // 加锁处理，防止并发修改元数据
            allocateNormal(buf, reqCapacity, sizeClassesMetadataItem);
        } finally {
            unlock();
        }
    }

    private void allocateHuge(MyPooledByteBuf<T> buf, int reqCapacity) {
        MyPoolChunk<T> chunk = newUnpooledChunk(reqCapacity);
        buf.initUnPooled(chunk, reqCapacity);
    }


    private void allocateNormal(MyPooledByteBuf<T> buf, int reqCapacity, MySizeClassesMetadataItem sizeIdx) {
        // 优先从050的PoolChunkList开始尝试分配，尽可能的复用已经使用较充分的PoolChunk。如果分配失败，就尝试另一个区间内的PoolChunk
        // 分配成功则直接return快速返回
        if (q050.allocate(buf, reqCapacity, sizeIdx)){
            return;
        }
        if (q025.allocate(buf, reqCapacity, sizeIdx)){
            return;
        }
        if (q000.allocate(buf, reqCapacity, sizeIdx)){
            return;
        }
        if (qInit.allocate(buf, reqCapacity, sizeIdx)){
            return;
        }
        if (q075.allocate(buf, reqCapacity, sizeIdx)){
            return;
        }

        // 所有的PoolChunkList都尝试过了一遍，都没能分配成功，说明已经被创建出来的，所有有剩余空间的PoolChunk空间都不够了

        // MyNetty对sizeClass做了简化，里面的规格都是写死的，所以直接从sizeClass里取
        int pageSize = this.mySizeClasses.getPageSize();
        int nPSizes = this.mySizeClasses.getNPageSizes();
        int pageShifts = this.mySizeClasses.getPageShifts();
        int chunkSize = this.mySizeClasses.getChunkSize();

        // 创建一个新的PoolChunk，用来进行本次内存分配
        MyPoolChunk<T> c = newChunk(pageSize, nPSizes, pageShifts, chunkSize);
        c.allocate(buf, reqCapacity, sizeIdx);
        // 新创建的PoolChunk首先加入qInit(可能使用率较高，add方法里会去移动到合适的PoolChunkList中(nextList.add))
        qInit.add(c);
    }


    public MySizeClasses getMySizeClasses() {
        return mySizeClasses;
    }

    void lock() {
        lock.lock();
    }

    void unlock() {
        lock.unlock();
    }

    protected abstract MyPooledByteBuf<T> newByteBuf(int maxCapacity);
    protected abstract MyPoolChunk<T> newChunk(int pageSize, int maxPageIdx, int pageShifts, int chunkSize);
    protected abstract MyPoolChunk<T> newUnpooledChunk(int capacity);
    protected abstract void destroyChunk(MyPoolChunk<T> chunk);

    protected abstract void memoryCopy(T src, int srcOffset, MyPooledByteBuf<T> dst, int length);

    public static final class HeapArena extends MyPoolArena<byte[]>{

        public HeapArena(MyPooledByteBufAllocator parent) {
            super(parent);
        }

        @Override
        protected MyPooledByteBuf<byte[]> newByteBuf(int maxCapacity) {
            return MyPooledHeapByteBuf.newInstance(maxCapacity);
        }

        @Override
        protected MyPoolChunk<byte[]> newChunk(int pageSize, int maxPageIdx, int pageShifts, int chunkSize) {
            // HeapArena以byte数组承载HeapByteBuf
            byte[] memory = new byte[chunkSize];
            return new MyPoolChunk<>(
                this, null, memory, pageSize, pageShifts, chunkSize, maxPageIdx);
        }

        @Override
        protected MyPoolChunk<byte[]> newUnpooledChunk(int capacity) {
            byte[] memory = new byte[capacity];
            return new MyPoolChunk<>(this, null, memory, capacity);
        }

        @Override
        protected void destroyChunk(MyPoolChunk<byte[]> chunk) {
            // 基于堆内存的PoolChunk，不需要做额外的回收操作，让GC自己将其销毁
            // doNothing
        }

        @Override
        protected void memoryCopy(byte[] src, int srcOffset, MyPooledByteBuf<byte[]> dst, int length) {
            if (length == 0) {
                return;
            }

            // 基于堆内存的buffer，复制数组
            System.arraycopy(src, srcOffset, dst.getMemory(), dst.getOffset(), length);
        }
    }
}
