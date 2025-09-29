package com.my.netty.bytebuffer.netty.allocator;

import com.my.netty.bytebuffer.netty.MyPooledByteBuf;
import com.my.netty.bytebuffer.netty.allocator.enums.SizeClassEnum;
import com.my.netty.bytebuffer.netty.objectpool.MyObjectPool;
import com.my.netty.bytebuffer.netty.util.MathUtil;
import org.jctools.queues.unpadded.MpscUnpaddedArrayQueue;

import java.nio.ByteBuffer;
import java.util.Queue;

public abstract class MyMemoryRegionCache<T> {

    private final int size;
    private final Queue<Entry<T>> queue;
    private final SizeClassEnum sizeClassEnum;
    private int allocations;

    @SuppressWarnings("rawtypes")
    private static final MyObjectPool<Entry> RECYCLER = MyObjectPool.newPool(handle -> new Entry(handle));

    MyMemoryRegionCache(int size, SizeClassEnum sizeClassEnum) {
        this.size = MathUtil.safeFindNextPositivePowerOfTwo(size);
        queue = new MpscUnpaddedArrayQueue<>(this.size);
        this.sizeClassEnum = sizeClassEnum;
    }

    /**
     * Add to cache if not already full.
     * @return true 当前线程释放内存时，成功加入到本地线程缓存，不需要实际的回收
     *         false 当前线程释放内存时，加入本地线程缓存失败，需要进行实际的回收
     */
    @SuppressWarnings("unchecked")
    public final boolean add(MyPoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, int normCapacity) {
        Entry<T> entry = newEntry(chunk, nioBuffer, handle, normCapacity);
        // 尝试加入到当前Cache的队列里
        boolean queued = queue.offer(entry);
        if (!queued) {
            // If it was not possible to cache the chunk, immediately recycle the entry
            entry.recycle();
        }

        return queued;
    }

    /**
     * Allocate something out of the cache if possible and remove the entry from the cache.
     */
    public final boolean allocate(MyPooledByteBuf<T> buf, int reqCapacity, MyPoolThreadCache threadCache) {
        Entry<T> entry = queue.poll();
        if (entry == null) {
            return false;
        }

        // 之前已经缓存过了，直接把对应的内存段拿出来复用，给本次同样规格的内存分配
        initBuf(entry.chunk, entry.nioBuffer, entry.handle, buf, reqCapacity, threadCache);
        entry.recycle();

        // allocations is not thread-safe which is fine as this is only called from the same thread all time.
        ++ allocations;
        return true;
    }

    /**
     * Clear out this cache and free up all previous cached {@link MyPoolChunk}s and {@code handle}s.
     */
    public final int free(boolean finalizer) {
        return free(Integer.MAX_VALUE, finalizer);
    }

    private int free(int max, boolean finalizer) {
        int numFreed = 0;
        for (; numFreed < max; numFreed++) {
            // 遍历所有已缓存的entry，一个接着一个进行实际的内存释放
            Entry<T> entry = queue.poll();
            if (entry != null) {
                freeEntry(entry, finalizer);
            } else {
                // all cleared
                return numFreed;
            }
        }
        return numFreed;
    }

    /**
     * Free up cached {@link MyPoolChunk}s if not allocated frequently enough.
     */
    public final void trim() {
        int free = size - allocations;
        allocations = 0;

        // We not even allocated all the number that are
        if (free > 0) {
            free(free, false);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private  void freeEntry(Entry entry, boolean finalizer) {
        // Capture entry state before we recycle the entry object.
        MyPoolChunk chunk = entry.chunk;
        long handle = entry.handle;
//        ByteBuffer nioBuffer = entry.nioBuffer;
        int normCapacity = entry.normCapacity;

        if (!finalizer) {
            // recycle now so PoolChunk can be GC'ed. This will only be done if this is not freed because of
            // a finalizer.
            entry.recycle();
        }

        // 将当前entry中缓存的handle内存段进行实际的回收，放回到所属的PoolChunk中
        chunk.arena.freeChunk(chunk, handle, normCapacity);
    }



    protected abstract void initBuf(MyPoolChunk<T> chunk, ByteBuffer nioBuffer, long handle,
                                    MyPooledByteBuf<T> buf, int reqCapacity, MyPoolThreadCache threadCache);

    @SuppressWarnings("rawtypes")
    private static Entry newEntry(MyPoolChunk<?> chunk, ByteBuffer nioBuffer, long handle, int normCapacity) {
        Entry entry = RECYCLER.get();
        entry.chunk = chunk;
        entry.nioBuffer = nioBuffer;
        entry.handle = handle;
        entry.normCapacity = normCapacity;
        return entry;
    }

    static final class Entry<T> {
        final MyObjectPool.Handle<Entry<?>> recyclerHandle;
        MyPoolChunk<T> chunk;
        ByteBuffer nioBuffer;
        long handle = -1;
        int normCapacity;

        Entry(MyObjectPool.Handle<Entry<?>> recyclerHandle) {
            this.recyclerHandle = recyclerHandle;
        }

        void recycle() {
            chunk = null;
            nioBuffer = null;
            handle = -1;
            recyclerHandle.recycle(this);
        }
    }

}
