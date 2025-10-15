# 从零开始实现简易版Netty(九) MyNetty 实现池化内存的线程本地缓存
## 1. Netty 池化内存线程本地缓存介绍
在上一篇博客中，截止lab8版本MyNetty已经实现了Normal和Small规格的池化内存分配。按照计划，在lab9中MyNetty将实现池化内存的线程本地缓存功能，以完成池化内存功能的最后一块拼图。
由于本文属于系列博客，读者需要对之前的博客内容有所了解才能更好地理解本文内容。
* lab1版本博客：[从零开始实现简易版Netty(一) MyNetty Reactor模式](https://www.cnblogs.com/xiaoxiongcanguan/p/18939320)
* lab2版本博客：[从零开始实现简易版Netty(二) MyNetty pipeline流水线](https://www.cnblogs.com/xiaoxiongcanguan/p/18964326)
* lab3版本博客：[从零开始实现简易版Netty(三) MyNetty 高效的数据读取实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18979699)
* lab4版本博客：[从零开始实现简易版Netty(四) MyNetty 高效的数据写出实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18992091)
* lab5版本博客：[从零开始实现简易版Netty(五) MyNetty FastThreadLocal实现](https://www.cnblogs.com/xiaoxiongcanguan/p/19005381)
* lab6版本博客：[从零开始实现简易版Netty(六) MyNetty ByteBuf实现](https://www.cnblogs.com/xiaoxiongcanguan/p/19029215)
* lab7版本博客：[从零开始实现简易版Netty(七) MyNetty 实现Normal规格的池化内存分配](https://www.cnblogs.com/xiaoxiongcanguan/p/19084677)
* lab8版本博客：[从零开始实现简易版Netty(八) MyNetty 实现Small规格的池化内存分配](https://www.cnblogs.com/xiaoxiongcanguan/p/19109991)
#####
在lab7、lab8的实现中可以发现，出于空间利用率的考虑，一个PoolArena会同时被多个线程并发访问。因此无论是Normal还是Small规格的池化内存分配，Netty在进行实际的池化内存分配时都或多或少的需要使用互斥锁来确保用于追踪池化内存状态的元数据(PoolArena或是PoolSubPage)不会被并发的更新而出现问题。  
jemalloc的论文中提到，内存分配作为一个高频的操作需要尽可能的减少线程的同步竞争以提高效率，大量线程都阻塞在同步锁上会大大降低内存分配的整体吞吐率。  
因此jemalloc中提到，可以引入线程本地缓存来减少同步事件。  
_"The main goal of thread caches is to reduce the volume of synchronization events."_    
_"Minimize lock contention. jemalloc's independent arenas were inspired by lkmalloc, but as time went on, tcmalloc made it abundantly clear that it's even better to avoid synchronization altogether, so jemalloc also implements thread-specific caching."_  
#####
引入线程本地缓存后，当前线程在释放池化内存时，不会直接将空闲的池化内存对象还给公共的PoolArena中，而是优先尝试放入独属于本线程的本地缓存中。同时，在尝试申请池化内存分配时，也会优先查询线程本地缓存中是否存在对应规格的可用池化内存对象，如果有则直接使用，而无需访问公共的PoolArena。   
有了线程本地缓存，线程在绝大多数情况下都只和独属于自己的本地缓存进行交互，因此能够大幅减少与其它线程争抢公共PoolArena元数据互斥锁的场景、提高所访问内存空间的缓存局部性，从而大幅提升内存分配的吞吐量。  
当然，线程本地缓存也不是没有缺点的，线程本地缓存毫无疑问增加了内存的开销，规格繁多的本地池化内存对象多数时候都只会静静地在缓存中等待被使用(视为内部碎片)，因此线程本地所能缓存的池化对象数量是被严格限制的，使用者需要在池化内存分配效率与空间利用率的取舍上达成平衡。   
具体的实现细节，我们在下文中结合源码再展开介绍。

## 2. MyNetty 池化内存线程本地缓存源码实现
在jemalloc的论文中提到，为了减少线程之间对Arena的争抢，jemalloc设置了多个Arena区域，并使用特别的算法使得每个Arena尽可能的被线程均匀的使用。Arena与线程是一对多的关系，而一个线程在进行池化内存分配前选择并永久绑定一个Arena。  

### 2.1 PoolThreadLocalCache实现解析
在Netty中，参考jemalloc也同样是设置多个PoolArena，并令一个线程在进行最初的池化内存分配之前绑定一个PoolArena。  
具体的逻辑在PooledByteBufAllocator中，PooledByteBufAllocator中为基于堆内存的HeapByteBuffer和基于堆外直接内存的DirectByteBuffer以数组的形式分别维护了N个PoolArena(heapArenas、directArenas)。  
具体N为多少可以在allocator分配器的构造方法中通过参数设置，默认情况下其值取决与处理器的数量和内存大小。  
而具体的当前线程与其中某一个PoolArena进行绑定的逻辑则位于PoolThreadLocalCache这一核心数据结构之中。

##### MyNetty PooledByteBufAllocator实现源码
```java
public class MyPooledByteBufAllocator extends MyAbstractByteBufAllocator{

    private final MyPoolArena<byte[]>[] heapArenas;

    private final MyPoolThreadLocalCache threadLocalCache;

    public MyPooledByteBufAllocator() {
        this(false);
    }

    public MyPooledByteBufAllocator(boolean useCacheForAllThreads) {
        // 简单起见，arena的数量与处理器核数挂钩(netty中有更复杂的方式去配置，既可以构造参数传参设置，也可以配置系统参数来控制默认值)
        int arenasNum = Runtime.getRuntime().availableProcessors() * 2;

        // 初始化好heapArena数组
        heapArenas = new MyPoolArena.HeapArena[arenasNum];
        for (int i = 0; i < heapArenas.length; i ++) {
            MyPoolArena.HeapArena arena = new MyPoolArena.HeapArena(this);
            heapArenas[i] = arena;
        }

        // 创建threadLocalCache，让线程绑定到唯一的PoolArena中，并且在small/normal分配时，启用相关的内存块缓存
        this.threadLocalCache = new MyPoolThreadLocalCache(useCacheForAllThreads,this);
    }

    @Override
    protected MyByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        // 从ThreadLocal中获得当前线程所绑定的PoolArena(如果是线程第一次分配，则ThreadLocal初始值获取时会进行绑定)
        MyPoolThreadCache cache = threadLocalCache.get();
        MyPoolArena<byte[]> targetArena = cache.heapArena;
        return targetArena.allocate(cache, initialCapacity, maxCapacity);
    }
}
```
##### MyNetty PoolThreadLocalCache实现源码
```java
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
```
#####
* MyNetty由于只支持了堆内存的池化内存分配，因此只有heapArenas数组。同时也简化了设置Arenas个数的逻辑。
* PoolThreadLocalCache是一个FastThreadLocal<PoolThreadCache>类型的数据结构。每一个线程都有一个自己所独有的PoolThreadCache，这个结构就是用于存放PooledByteBuf池化内存对象的线程本地缓存。
  initialValue方法中可以看到，Netty并不会无脑的为所有线程都开启线程本地缓存，默认情况下useCacheForAllThreads为false，则只会为FastThreadLocalThread类型的线程设置线程本地缓存。  
  对于普通线程，则返回了一个参数均为0的，本质上是无缓存作用的PoolThreadCache对象(PoolThreadCache内部的工作原理我们在下一小节展开)。
* 在初始化PoolThreadCache时，通过leastUsedArena方法找到当前绑定线程最少的PoolArena与线程专属的PoolThreadCache缓存进行关联。  
  通过这一方式，实现了上述jemalloc中提到的将线程与PoolArena进行绑定，并尽可能的使得PoolArena负载平衡。

### 2.2 PoolThreadCache实现解析
* 在PoolThreadCache中，类似Small规格的分配，为每一种特定的规格都维护了一个对象池。  
  对象池是MyMemoryRegionCache结构，small规格的对象池是smallSubPageHeapCaches数组，normal规格的对象池是normalHeapCaches数组。 
* 默认情况下，所有的small规格都会进行缓存；而normal规格中只有32kb的这一最小规格才会被缓存，更大的规格将不会进行线程本地缓存。  
  这样设计处于两个考虑，首先是每个线程都要维护线程本地缓存，缓存的池化内存对象会占用大量的内存空间，所要缓存的规格越多，则内存碎片越多，空间利用率越低。   
  其次，绝大多数情况下越大规格的内存申请的频率越低，进行线程本地缓存所带来的吞吐量的提升越小。基于这两点，netty将最大的本地缓存规格设置为了32kb。  
  当然，如果应用的开发者的实际场景中就是有大量的大规格池化内存的分配需求，netty也允许使用对应的参数来控制实际需要进行线程本地缓存的最大规格。
* MyMemoryRegionCache中都维护了一个队列存放所缓存的PooledByteBuf池化对象(挂载在Entry节点上)；与lab6中的对象池设计一样，队列也是专门针对多写单读的并发场景优化的。  
  因为从线程本地缓存中获取池化对象的只会是持有者线程，而归还时则可能在经过多次传递后，由其它线程进行归还而写回队列中。

##### MyNetty PoolThreadCache实现源码
```java
/**
 * 池化内存分配线程缓存，完全参考Netty的PoolThreadCache
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
```
#####
```java
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
```
### 2.3 引入线程本地缓存后的池化内存分配

### 2.4 引入线程本地缓存后的池化内存释放

### 3. Netty池化内存实现整体分析

## 总结
