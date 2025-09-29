package com.my.netty.bytebuffer.netty.allocator;


import com.my.netty.bytebuffer.netty.MyByteBuf;

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

    public MyPoolArena<byte[]>[] getHeapArenas() {
        return heapArenas;
    }

    public boolean trimCurrentThreadCache() {
        MyPoolThreadCache cache = threadLocalCache.getIfExists();
        if (cache != null) {
            cache.trim();
            return true;
        }

        return false;
    }

    public MyPoolThreadLocalCache getThreadLocalCache() {
        return threadLocalCache;
    }
}
