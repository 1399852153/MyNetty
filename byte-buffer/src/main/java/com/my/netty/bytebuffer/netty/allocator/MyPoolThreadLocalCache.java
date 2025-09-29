package com.my.netty.bytebuffer.netty.allocator;


import com.my.netty.threadlocal.impl.netty.MyFastThreadLocal;
import com.my.netty.threadlocal.impl.netty.MyFastThreadLocalThread;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class MyPoolThreadLocalCache extends MyFastThreadLocal<MyPoolThreadCache> {

    private final boolean useCacheForAllThreads;

    private static final int DEFAULT_SMALL_CACHE_SIZE = 256;
    private static final int DEFAULT_NORMAL_CACHE_SIZE = 32;

    private static final int DEFAULT_MAX_CACHED_BUFFER_CAPACITY = 32 * 1024;
    private static final int DEFAULT_CACHE_TRIM_INTERVAL = 8192;

    private MyPooledByteBufAllocator myPooledByteBufAllocator;

    private ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1);

    private final Runnable trimTask = new Runnable() {
        @Override
        public void run() {
            myPooledByteBufAllocator.trimCurrentThreadCache();
        }
    };

    MyPoolThreadLocalCache(boolean useCacheForAllThreads,
                           MyPooledByteBufAllocator myPooledByteBufAllocator) {
        this.useCacheForAllThreads = useCacheForAllThreads;
        this.myPooledByteBufAllocator = myPooledByteBufAllocator;
    }

    @Override
    protected synchronized MyPoolThreadCache initialValue() {
        // 从allocator所包含的HeapArena中挑选出一个最合适的HeapArena与当前线程绑定
        // 什么是最合适的？就是被其它线程绑定次数最少的(最少被使用 leastUsed)，也就是相对最空闲的PoolArena
        final MyPoolArena<byte[]> heapArena = leastUsedArena(myPooledByteBufAllocator.getHeapArenas());

        final Thread current = Thread.currentThread();

        // 如果没有配置useCacheForAllThreads=true，则只有FastThreadLocalThread等特殊场景才启用PoolThreadCache缓存功能
        if (useCacheForAllThreads ||
            // If the current thread is a FastThreadLocalThread we will always use the cache
            current instanceof MyFastThreadLocalThread) {
            final MyPoolThreadCache cache = new MyPoolThreadCache(
                heapArena, DEFAULT_SMALL_CACHE_SIZE, DEFAULT_NORMAL_CACHE_SIZE,
                DEFAULT_MAX_CACHED_BUFFER_CAPACITY, DEFAULT_CACHE_TRIM_INTERVAL);


            return cache;
        }else{
            // No caching so just use 0 as sizes.
            // 不启用缓存，则返回一个smallCacheSize/normalCacheSize都为0的特殊PoolThreadCache
            // 但是挑选一个heapArena和当前线程绑定的逻辑依然是存在的，只是没有small/normal具体分配时的线程本地缓存
            return new MyPoolThreadCache(heapArena, 0, 0, 0, 0);
        }
    }

    @Override
    protected void onRemoval(MyPoolThreadCache threadCache) {
        threadCache.free(false);
    }

    private <T> MyPoolArena<T> leastUsedArena(MyPoolArena<T>[] arenas) {
        if (arenas == null || arenas.length == 0) {
            return null;
        }

        MyPoolArena<T> minArena = arenas[0];

        // optimized
        // If it is the first execution, directly return minarena and reduce the number of for loop comparisons below
        if (minArena.numThreadCaches.get() == 0) {
            // 当前Allocator第一次分配PoolArena，快速返回第一个即可
            return minArena;
        }

        // 否则从所有的PoolArena中找到相对来说被最少得线程绑定的那个PoolArena
        for (int i = 1; i < arenas.length; i++) {
            MyPoolArena<T> arena = arenas[i];
            if (arena.numThreadCaches.get() < minArena.numThreadCaches.get()) {
                minArena = arena;
            }
        }

        return minArena;
    }
}
