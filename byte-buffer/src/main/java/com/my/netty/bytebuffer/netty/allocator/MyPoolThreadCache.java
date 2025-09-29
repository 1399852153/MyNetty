package com.my.netty.bytebuffer.netty.allocator;

import com.my.netty.bytebuffer.netty.MyPooledByteBuf;
import com.my.netty.bytebuffer.netty.allocator.enums.SizeClassEnum;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 池化内存分配线程缓存
 * */
public class MyPoolThreadCache {

    final MyPoolArena<byte[]> heapArena;

    private final int freeSweepAllocationThreshold;

    // Hold the caches for the different size classes, which are small and normal.
    private final MyMemoryRegionCache<byte[]>[] smallSubPageHeapCaches;
    private final MyMemoryRegionCache<byte[]>[] normalHeapCaches;

    private int allocations;

    private final AtomicBoolean freed = new AtomicBoolean();


    MyPoolThreadCache(MyPoolArena<byte[]> heapArena,
                    int smallCacheSize, int normalCacheSize, int maxCachedBufferCapacity,
                    int freeSweepAllocationThreshold) {
        this.freeSweepAllocationThreshold = freeSweepAllocationThreshold;
        this.heapArena = heapArena;

        if (heapArena != null) {
            // 为每一种Small类型的size，都创建一个SubPageMemoryRegionCache来做缓存
            smallSubPageHeapCaches = createSubPageCaches(
                smallCacheSize, heapArena.numSmallSubpagePools);

            // 为每一种Normal类型的size，都创建一个NormalMemoryRegionCache来做缓存
            normalHeapCaches = createNormalCaches(
                normalCacheSize, maxCachedBufferCapacity, heapArena);

            // 当前Arena所绑定的ThreadCache数量加1
            heapArena.numThreadCaches.getAndIncrement();
        } else {
            // No heapArea is configured so just null out all caches
            smallSubPageHeapCaches = null;
            normalHeapCaches = null;
        }

        // Only check if there are caches in use.
        if ((smallSubPageHeapCaches != null || normalHeapCaches != null)
            && freeSweepAllocationThreshold < 1) {
            throw new IllegalArgumentException("freeSweepAllocationThreshold: " + freeSweepAllocationThreshold + " (expected: > 0)");
        }
    }

    private static <T> MyMemoryRegionCache<T>[] createSubPageCaches(int cacheSize, int numCaches) {
        if (cacheSize > 0 && numCaches > 0) {
            // 为每一种Small类型的size，都创建一个SubPageMemoryRegionCache来做缓存
            MyMemoryRegionCache<T>[] cache = new MyMemoryRegionCache[numCaches];
            for (int i = 0; i < cache.length; i++) {
                cache[i] = new SubPageMemoryRegionCache<T>(cacheSize);
            }
            return cache;
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> MyMemoryRegionCache<T>[] createNormalCaches(int cacheSize, int maxCachedBufferCapacity, MyPoolArena<T> area) {
        if (cacheSize > 0 && maxCachedBufferCapacity > 0) {
            // 所能缓存的buf规格的最大值，由chunk和用户参数指定的最小值决定的
            int max = Math.min(area.mySizeClasses.getChunkSize(), maxCachedBufferCapacity);

            List<MyMemoryRegionCache<T>> cache = new ArrayList<>() ;

            // 为每一种Normal类型的size，都创建一个NormalMemoryRegionCache来做缓存
            int nSizes = area.getMySizeClasses().getSmallAndNormalTotalSize();
            for (int idx = area.numSmallSubpagePools;
                 idx < nSizes && area.getMySizeClasses().sizeIdx2size(idx).getSize() <= max ; idx++) {
                cache.add(new NormalMemoryRegionCache<>(cacheSize));
            }
            return cache.toArray(new MyMemoryRegionCache[0]);
        } else {
            return null;
        }
    }

    /**
     * Try to allocate a small buffer out of the cache. Returns {@code true} if successful {@code false} otherwise
     */
    boolean allocateSmall(MyPoolArena<?> area, MyPooledByteBuf<?> buf, int reqCapacity, MySizeClassesMetadataItem sizeClassesMetadataItem) {
        return allocate(cacheForSmall(area, sizeClassesMetadataItem), buf, reqCapacity);
    }

    /**
     * Try to allocate a normal buffer out of the cache. Returns {@code true} if successful {@code false} otherwise
     */
    boolean allocateNormal(MyPoolArena<?> area, MyPooledByteBuf<?> buf, int reqCapacity, MySizeClassesMetadataItem sizeClassesMetadataItem) {
        return allocate(cacheForNormal(area, sizeClassesMetadataItem), buf, reqCapacity);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private boolean allocate(MyMemoryRegionCache<?> cache, MyPooledByteBuf buf, int reqCapacity) {
        if (cache == null) {
            // no cache found so just return false here
            return false;
        }
        boolean allocated = cache.allocate(buf, reqCapacity, this);
        if (++ allocations >= freeSweepAllocationThreshold) {
            allocations = 0;
            trim();
        }
        return allocated;
    }

    boolean add(MyPoolArena<?> area, MyPoolChunk chunk,  ByteBuffer nioBuffer, long handle, int normCapacity) {
        MySizeClassesMetadataItem mySizeClassesMetadataItem = area.getMySizeClasses().size2SizeIdx(normCapacity);
        MyMemoryRegionCache<?> cache = cache(area, mySizeClassesMetadataItem);
        if (cache == null) {
            // 当前无法缓存
            return false;
        }
        if (freed.get()) {
            return false;
        }
        return cache.add(chunk, nioBuffer, handle, normCapacity);
    }

    /// TODO: In the future when we move to Java9+ we should use java.lang.ref.Cleaner.
    @Override
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            free(true);
        }
    }

    /**
     *  Should be called if the Thread that uses this cache is about to exist to release resources out of the cache
     */
    void free(boolean finalizer) {
        // As free() may be called either by the finalizer or by FastThreadLocal.onRemoval(...) we need to ensure
        // we only call this one time.
        if (freed.compareAndSet(false, true)) {
            // 当前ThreadLocal被销毁时，会调用free方法，在free方法中需要将当前已缓存的、未实际释放掉的内存都放回到PoolArena中
            // 只有这样，才能避免内存泄露
            free(smallSubPageHeapCaches, finalizer);
            free(normalHeapCaches, finalizer);

            if (heapArena != null) {
                heapArena.numThreadCaches.getAndDecrement();
            }
        }
    }

    private static int free(MyMemoryRegionCache<?>[] caches, boolean finalizer) {
        if (caches == null) {
            return 0;
        }

        int numFreed = 0;
        for (MyMemoryRegionCache<?> c: caches) {
            numFreed += free(c, finalizer);
        }
        return numFreed;
    }

    private static int free(MyMemoryRegionCache<?> cache, boolean finalizer) {
        if (cache == null) {
            return 0;
        }
        return cache.free(finalizer);
    }

    private MyMemoryRegionCache<?> cacheForSmall(MyPoolArena<?> area, MySizeClassesMetadataItem sizeClassesMetadataItem) {

        return cache(smallSubPageHeapCaches, sizeClassesMetadataItem.getSize());
    }

    private MyMemoryRegionCache<?> cacheForNormal(MyPoolArena<?> area, MySizeClassesMetadataItem sizeClassesMetadataItem) {
        int sizeIdx = sizeClassesMetadataItem.getTableIndex();
        // We need to substract area.numSmallSubpagePools as sizeIdx is the overall index for all sizes.
        int idx = sizeIdx - area.numSmallSubpagePools;

        return cache(normalHeapCaches, idx);
    }

    private MyMemoryRegionCache<?> cache(MyPoolArena<?> area, MySizeClassesMetadataItem sizeClassesMetadataItem) {
        switch (sizeClassesMetadataItem.getSizeClassEnum()) {
            case NORMAL:
                return cacheForNormal(area, sizeClassesMetadataItem);
            case SMALL:
                return cacheForSmall(area, sizeClassesMetadataItem);
            default:
                throw new Error();
        }
    }

    private static <T> MyMemoryRegionCache<T> cache(MyMemoryRegionCache<T>[] cache, int sizeIdx) {
        if (cache == null || sizeIdx > cache.length - 1) {
            // 当前规格无法缓存
            return null;
        }
        return cache[sizeIdx];
    }

    void trim() {
        trim(smallSubPageHeapCaches);
        trim(normalHeapCaches);
    }

    private static void trim(MyMemoryRegionCache<?>[] caches) {
        if (caches == null) {
            return;
        }
        for (MyMemoryRegionCache<?> c: caches) {
            trim(c);
        }
    }

    private static void trim(MyMemoryRegionCache<?> cache) {
        if (cache == null) {
            return;
        }
        cache.trim();
    }


    /**
     * Cache used for buffers which are backed by TINY or SMALL size.
     */
    public static final class SubPageMemoryRegionCache<T> extends MyMemoryRegionCache<T> {
        SubPageMemoryRegionCache(int size) {
            super(size, SizeClassEnum.SMALL);
        }

        @Override
        protected void initBuf(
            MyPoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, MyPooledByteBuf<T> buf, int reqCapacity,
            MyPoolThreadCache threadCache) {
            chunk.initBufWithSubpage(buf, nioBuffer, handle, reqCapacity, threadCache);
        }
    }

    /**
     * Cache used for buffers which are backed by NORMAL size.
     */
    public static final class NormalMemoryRegionCache<T> extends MyMemoryRegionCache<T> {
        NormalMemoryRegionCache(int size) {
            super(size, SizeClassEnum.NORMAL);
        }

        @Override
        protected void initBuf(
            MyPoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, MyPooledByteBuf<T> buf, int reqCapacity,
            MyPoolThreadCache threadCache) {
            chunk.initBuf(buf, nioBuffer, handle, reqCapacity, threadCache);
        }
    }
}
