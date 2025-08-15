package com.my.netty.bytebuffer.netty.allocator;


import com.my.netty.bytebuffer.netty.MyPooledByteBuf;
import com.my.netty.bytebuffer.netty.allocator.enums.SizeClassEnum;
import com.my.netty.bytebuffer.netty.util.queue.LongPriorityQueue;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 内存分配chunk
 * */
public class MyPoolChunk<T> {

    private static final int SIZE_BIT_LENGTH = 15;
    private static final int INUSED_BIT_LENGTH = 1;
    private static final int SUBPAGE_BIT_LENGTH = 1;
    private static final int BITMAP_IDX_BIT_LENGTH = 32;

    static final int IS_SUBPAGE_SHIFT = BITMAP_IDX_BIT_LENGTH;
    static final int IS_USED_SHIFT = SUBPAGE_BIT_LENGTH + IS_SUBPAGE_SHIFT;
    static final int SIZE_SHIFT = INUSED_BIT_LENGTH + IS_USED_SHIFT;
    static final int RUN_OFFSET_SHIFT = SIZE_BIT_LENGTH + SIZE_SHIFT;

    /**
     * 当前chunk属于哪个arena
     */
    final MyPoolArena<T> arena;

    /**
     * directByteBuffer的os内存基地址
     */
    final Object base;

    /**
     * 所管理的内存对象
     */
    final T memory;

    /**
     * chunk中page页的大小，page是chunk中最小的分配单元
     */
    final int pageSize;

    /**
     * pageSize的二次对数值
     * log2(pageSize) = pageShifts
     */
    final int pageShifts;

    /**
     * chunk的大小，也就是本PoolChunk可以分配的总内存大小
     */
    final int chunkSize;

    /**
     * 最多有多少种PageSize的规格
     */
    int maxPageIdx;

    /**
     * 当前chunk还未分配的空闲内存大小
     */
    int freeBytes;

    /**
     * 当前chunk所属的chunkList
     */
    MyPoolChunkList<T> parent;

    /**
     * 当前chunk在chunkList双向链表中的前驱节点
     */
    MyPoolChunk<T> prev;

    /**
     * 当前chunk在chunkList双向链表中的后继节点
     */
    MyPoolChunk<T> next;

    final boolean unpooled;

    /**
     * manage all avail runs
     * 每一种page规格都通过一个优先级队列(IntPriorityQueue)进行维护
     */
    private final LongPriorityQueue[] runsAvail;

    /**
     * store the first page and last page of each avail run
     */
    private final LongLongHashMap runsAvailMap;

    /**
     * 分配与释放内存时，防止并发操作runsAvail的互斥锁
     */
    private final ReentrantLock runsAvailLock;

    private final AtomicLong pinnedBytes = new AtomicLong();


    /**
     * 用于small和normal类型分配的构造函数 unpooled为false，需要池化
     * */
    public MyPoolChunk(MyPoolArena<T> arena, Object base, T memory, int pageSize, int pageShifts, int chunkSize, int maxPageIdx) {
        this.unpooled = false;
        this.arena = arena;
        this.base = base;
        this.memory = memory;

        this.pageSize = pageSize;
        this.pageShifts = pageShifts;
        this.chunkSize = chunkSize;
        this.maxPageIdx = maxPageIdx;

        // 初始化时，所有的内存都还没有分配过，所以freeBytes就等于chunkSize
        this.freeBytes = chunkSize;

        // 每一种page规格都通过一个优先级队列(IntPriorityQueue)进行维护
        // 优先级队列中维护的int类型数据被称为handle，本质上是一段连续内存段(被称为run)的元数据。
        this.runsAvail = newRunsAvailQueueArray(maxPageIdx);

        // 分配与释放内存时，防止并发操作runsAvail的互斥锁
        runsAvailLock = new ReentrantLock();

        this.runsAvailMap = new LongLongHashMap(-1);

        // todo PoolSubpage用于small级别的分配，等后面再实现
//        subpages = new PoolSubpage[chunkSize >> pageShifts];

        // 一个chunk一共有多少个Page单元
        int pages = chunkSize >> pageShifts;

        // a handle is a long number, the bit layout of a run looks like：(一个handle是一个long类型的数字，其bit位布局如下所述)
        // oooooooo ooooooos ssssssss ssssssue bbbbbbbb bbbbbbbb bbbbbbbb bbbbbbbb
        // o: runOffset (page offset in the chunk), 15bit(前15位offset)
        // s: size (number of pages) of this run, 15bit(接着15位是run的大小(该run内存段一共多少个page))
        // u: isUsed?, 1bit(接着1位标识着是否已被使用)
        // e: isSubpage?, 1bit(接着1位标识着该run内存段是否为用于small类型分配的subPage)
        // b: bitmapIdx of subpage, zero if it's not subpage, 32bit(最后32位标识着subPage是否使用的位图，如果isSubpage=0，则则全为0)

        // initHandle = 000000000000000  000001000000000 0 0 0000 0000 0000 0000 0000 0000 0000 0000
        // 偏移量为0，标识一整个run的pages总数为512，u=0未被使用，e=0不是用于subpage
        long initHandle = (long) pages << SIZE_SHIFT;
        // 将当前初始化好的handle插入到runsAvail中(空的PoolChunk里面就只有一个run，起始的offset=0，尾部的offset=511，总长度正好是PoolChunk的总页数512)
        insertAvailRun(0, pages, initHandle);

        // cachedNioBuffers相关逻辑暂不实现
//        cachedNioBuffers = new ArrayDeque<ByteBuffer>(8);
    }

    /**
     *  用于huge类型分配的构造函数 unpooled为true，不需要池化
     *  */
    MyPoolChunk(MyPoolArena<T> arena, Object base, T memory, int size) {
        unpooled = true;
        this.arena = arena;
        this.base = base;
        this.memory = memory;
        pageSize = 0;
        pageShifts = 0;
        runsAvailMap = null;
        runsAvail = null;
        runsAvailLock = null;
//        subpages = null;
        chunkSize = size;
    }

    private void insertAvailRun(int runOffset, int pages, long handle) {
        // 获得当前申请page个数的最终分配规格
        int pageIdxFloor = arena.getMySizeClasses().pages2pageIdxFloor(pages);
        // 找到对应规格的handle优先级队列，将对应的handle放进去
        LongPriorityQueue queue = runsAvail[pageIdxFloor];
        queue.offer(handle);

        // 写入handle的起始runOffset的索引
        insertAvailRun0(runOffset, handle);
        if (pages > 1) {
            // 计算出当前handle的最后一个page的offset(offset + pages - 1)
            int lastPage = lastPage(runOffset, pages);
            // 写入handle的末尾runOffset的索引
            insertAvailRun0(lastPage, handle);
        }
    }

    private void insertAvailRun0(int runOffset, long handle) {
        runsAvailMap.put(runOffset, handle);
    }

    private static int lastPage(int runOffset, int pages) {
        return runOffset + pages - 1;
    }

    private static LongPriorityQueue[] newRunsAvailQueueArray(int size) {
        LongPriorityQueue[] queueArray = new LongPriorityQueue[size];
        for (int i = 0; i < queueArray.length; i++) {
            queueArray[i] = new LongPriorityQueue();
        }
        return queueArray;
    }

    boolean allocate(MyPooledByteBuf<T> buf, int reqCapacity, MySizeClassesMetadataItem mySizeClassesMetadataItem) {
        long handle;
        if (mySizeClassesMetadataItem.getSizeClassEnum() == SizeClassEnum.SMALL) {
            // todo small分配待实现
            handle = -1;
        } else {
            // 除了Small就只可能是Normal，huge的不去池化，进不来
            // runSize must be multiple of pageSize(normal类型分配的连续内存段的大小必须是pageSize的整数倍)
            int runSize = mySizeClassesMetadataItem.getSize();
            handle = allocateRun(runSize);
            if (handle < 0) {
                return false;
            }
        }

        // 分配成功，将这个空的buf对象进行初始化
        initBuf(buf,null,handle,reqCapacity);
        return true;
    }

    void initBuf(MyPooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity) {
        // if (isSubpage(handle)) {
            // initBufWithSubpage(buf, nioBuffer, handle, reqCapacity, threadCache);
        // } else {
            int maxLength = runSize(pageShifts, handle);
            buf.init(this, nioBuffer, handle, runOffset(handle) << pageShifts,
                reqCapacity, maxLength);
        // }
    }

    public void incrementPinnedMemory(int delta) {
        pinnedBytes.addAndGet(delta);
    }

    public void decrementPinnedMemory(int delta) {
        pinnedBytes.addAndGet(-delta);
    }

    public T getMemory() {
        return memory;
    }

    public MyPoolArena<T> getArena() {
        return arena;
    }

    public boolean isUnpooled() {
        return unpooled;
    }

    /**
     * 分配一个大小等于runSize的连续内存段
     * @return >=0 分配成功，返回对应的handle描述符
     *         <0  分配失败
     * */
    private long allocateRun(int runSize) {
        // 一共要申请多少页
        int pages = runSize >> pageShifts;
        // 页数对应的规格
        int pageIdx = arena.getMySizeClasses().pages2pageIdx(pages);

        // 加锁防并发
        runsAvailLock.lock();
        try {
            // 伙伴系统，从小到大查找，找到最合适的可分配内存段规格(在满足大小的情况下，尽可能的小)
            int queueIdx = runFirstBestFit(pageIdx);
            if (queueIdx == -1) {
                // 没有找到可用的内存段，分配失败，返回负数
                return -1;
            }

            LongPriorityQueue queue = runsAvail[queueIdx];
            // 在存储内存段的优先级队列中，poll返回的是offset最小的run
            long unUsedHandle = queue.poll();

            // 将待分配的handle对应的内存段从queue中先摘出来
            removeAvailRun(queue, unUsedHandle);

            // 一个大的run，在分配了一部分内存出去后，需要进行拆分
            long allocatedRunHandle = splitLargeRun(unUsedHandle, pages);

            // 获得所分配handle的总大小
            int pinnedSize = runSize(pageShifts, allocatedRunHandle);
            // 分配成功后，当前Chunk空闲大小减去本次被分配的大小
            freeBytes -= pinnedSize;
            // 返回当前分配出去的内存段对应的handle
            return allocatedRunHandle;
        } finally {
            // 操作完毕，释放互斥锁
            runsAvailLock.unlock();
        }
    }

    /**
     * 从handle中获得对应内存段的总大小
     * */
    static int runSize(int pageShifts, long handle) {
        // 一共多少页
        int runPages = runPages(handle);
        // 页数 * 页大小 = 总大小
        return runPages << pageShifts;
    }

    /**
     * 基于偏移量，从handle中获得pages属性
     * */
    static int runPages(long handle) {
        return (int) (handle >> SIZE_SHIFT & 0x7fff);
    }

    /**
     * 基于偏移量，从handle中获得offset属性
     * */
    static int runOffset(long handle) {
        return (int) (handle >> RUN_OFFSET_SHIFT);
    }

    /**
     * 伙伴系统，从小到大查找，找到最合适的可分配内存段规格(在满足大小的情况下，尽可能的小)
     * */
    private int runFirstBestFit(int pageIdx) {
        int nPSizes = arena.getMySizeClasses().getNPageSizes();
        if (freeBytes == chunkSize) {
            // 特殊情况，说明整个PoolChunk只有一个最大的完整的run，直接返回最后一个
            return nPSizes - 1;
        }

        // 伙伴系统，从pageIdx对应的规格开始从小往大查找，直到从对应的LongPriorityQueue中找到一个可以用来分配的内存段规格
        for (int i = pageIdx; i < nPSizes; i++) {
            LongPriorityQueue queue = runsAvail[i];
            if (queue != null && !queue.isEmpty()) {
                // 找到了一个不为空的队列，说明内部存在对应的可供分配的run，返回runsAvail下标
                return i;
            }
        }

        // 遍历了一遍，发现无法分配
        return -1;
    }

    private void removeAvailRun(LongPriorityQueue queue, long handle) {
        // 将handle从当前LongPriorityQueue中移除
        queue.remove(handle);

        // 将handle在runsAvailMap中的索引也一并移除
        int runOffset = runOffset(handle);
        int pages = runPages(handle);
        // remove first page of run
        runsAvailMap.remove(runOffset);
        if (pages > 1) {
            // remove last page of run
            runsAvailMap.remove(lastPage(runOffset, pages));
        }
    }

    private void removeAvailRun(long handle) {
        int pageIdxFloor = arena.getMySizeClasses().pages2pageIdxFloor(runPages(handle));
        LongPriorityQueue queue = runsAvail[pageIdxFloor];
        removeAvailRun(queue, handle);
    }

    private long splitLargeRun(long handle, int needPages) {
        // 计算出待分配的当前handle一共多少页
        int totalPages = runPages(handle);

        // 分配完成后剩余多少页
        int remPages = totalPages - needPages;

        if (remPages > 0) {
            // 当前handle内存段在分配后还有空余，需要进行拆分
            // 获得当前待拆分handle的偏移
            int runOffset = runOffset(handle);

            // 将待拆分的handle前面这部分分配出去，计算出剩余的那段内存的偏移量
            int availOffset = runOffset + needPages;
            // 剩余的那段内存段转换为一个新的handle放入当前Chunk中(inUsed=0 未使用)
            long availRun = toRunHandle(availOffset, remPages, 0);
            insertAvailRun(availOffset, remPages, availRun);

            // 返回分配出去的那段run的handle(inUsed=1 已使用)
            return toRunHandle(runOffset, needPages, 1);
        }else{
            // 当前handle内存段被完整的分配掉了，直接整体标记为已使用即可
            handle |= 1L << IS_USED_SHIFT;
            return handle;
        }
    }

    /**
     * 基于runOffset、runPages和inUsed 构建一个long类型的handle结构
     * */
    private static long toRunHandle(int runOffset, int runPages, int inUsed) {
        return (long) runOffset << RUN_OFFSET_SHIFT
            | (long) runPages << SIZE_SHIFT
            | (long) inUsed << IS_USED_SHIFT;
    }

    /**
     * 释放之前分配出去的内存
     * @param handle 之前分配出去的内存段handle
     * */
    void free(long handle, int normCapacity) {
        // 获得所释放内存段的大小
        int runSize = runSize(pageShifts, handle);

        // todo subPage free 下个版本再实现

        // 开始释放内存，加锁防并发
        runsAvailLock.lock();
        try {
            // 伙伴算法，在回收时，如果对应的内存段前后存在同样空闲的内存段，将其合并为一个更大的空闲内存段
            long finalRun = collapseRuns(handle);

            // 整个内存段设置为未使用(可能合并了，也可能就只是回收了当前内存段)
            finalRun &= ~(1L << IS_USED_SHIFT);
//            // if it is a subpage, set it to run
//            finalRun &= ~(1L << IS_SUBPAGE_SHIFT);

            // 将空闲的内存段插入到PoolChunk中
            insertAvailRun(runOffset(finalRun), runPages(finalRun), finalRun);
            // 回收后，空闲字节数增加对应的值
            freeBytes += runSize;
        } finally {
            runsAvailLock.unlock();
        }
    }

    /**
     * 尝试合并与handle头尾相邻的内存段
     * */
    private long collapseRuns(long handle) {
        // 先尝试和handle内存段头部相邻的内存段合并
        long afterCollapsePast = collapsePast(handle);

        // 再尝试和尾部相邻的内存段合并
        return collapseNext(afterCollapsePast);
    }

    private long collapsePast(long currentHandle) {
        for (;;) {
            int runOffset = runOffset(currentHandle);
            int runPages = runPages(currentHandle);

            // 从索引中尝试获得当前handle头部相邻的内存段(与头部直接相邻，就是比当前内存段的runOffset恰好少1)
            long pastRun = getAvailRunByOffset(runOffset - 1);
            if (pastRun == -1) {
                // 返回-1，说明与当前内存段头部相邻的内存段不存在(已经被分配出去了或者当前内存段的起始达到了左边界) 合并结束，直接返回
                return currentHandle;
            }

            int pastOffset = runOffset(pastRun);
            int pastPages = runPages(pastRun);

            if (pastRun != currentHandle && pastOffset + pastPages == runOffset) {
                // 发现可以进行合并，将前面的handle内存与当前handle合并成一个更大的内存段run
                // 先从Chunk中摘掉之前的run
                removeAvailRun(pastRun);
                // 合并成一个新的，更大的内存段(offset以前一段内存的offset为准，大小为两个run的和，inUsed=0代表未分配)
                currentHandle = toRunHandle(pastOffset, pastPages + runPages, 0);

                // 这个过程可以循环往复多次，反复合并，所以是循环处理
            } else {
                // 无法再继续合并了
                return currentHandle;
            }
        }
    }

    private long collapseNext(long handle) {
        for (;;) {
            int runOffset = runOffset(handle);
            int runPages = runPages(handle);

            // 从索引中尝试获得当前handle尾部相邻的内存段(与尾部直接相邻，就是其起始的offset等于当前内存段的runOffset+页数pages)
            long nextRun = getAvailRunByOffset(runOffset + runPages);
            if (nextRun == -1) {
                // 返回-1，说明与当前内存段尾部相邻的内存段不存在(已经被分配出去了或者当前内存段的末尾达到了右边界) 合并结束，直接返回
                return handle;
            }

            int nextOffset = runOffset(nextRun);
            int nextPages = runPages(nextRun);

            if (nextRun != handle && runOffset + runPages == nextOffset) {
                // 发现可以进行合并，将尾部的handle内存与当前handle合并成一个更大的内存段run
                // 先从Chunk中摘掉之前的run
                removeAvailRun(nextRun);

                // 合并成一个新的，更大的内存段(offset以前当前offset为准，大小为两个run的和，inUsed=0代表未分配)
                handle = toRunHandle(runOffset, runPages + nextPages, 0);

                // 这个过程和collapsePast一样，同样可以循环往复多次，反复合并，所以是循环处理
            } else {
                return handle;
            }
        }
    }

    static boolean isSubpage(long handle) {
        return (handle >> IS_SUBPAGE_SHIFT & 1) == 1L;
    }

    private long getAvailRunByOffset(int runOffset) {
        return runsAvailMap.get(runOffset);
    }
}
