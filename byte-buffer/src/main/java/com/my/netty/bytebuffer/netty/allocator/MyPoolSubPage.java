package com.my.netty.bytebuffer.netty.allocator;

import java.util.concurrent.locks.ReentrantLock;

public class MyPoolSubPage<T> {

    /**
     * 当前PoolSubPage所属的PoolChunk
     * */
    final MyPoolChunk<T> chunk;

    /**
     * 所维护的small类型内存项的大小
     * */
    final int elemSize;

    /**
     * 页大小的log2
     * */
    private final int pageShifts;

    /**
     * 当前内存段在PoolChunk中的页偏移量
     * */
    private final int runOffset;

    /**
     * 当前内存段的大小(单位：字节)
     * */
    private final int runSize;

    /**
     * 1个long有64位，可以维护64块small类型的内存块的使用情况(0为未分配，1为已分配)
     * 具体需要多少个这样的long，取决于elemSize的大小
     * */
    private final long[] bitmap;

    final int headIndex;

    private int bitmapLength;

    /**
     * 是否不需销毁
     * */
    boolean doNotDestroy;

    /**
     * 可以维护的最大内存元素项个数
     * */
    private int maxNumElems;

    /**
     * 当前可分配的元素个数
     * */
    private int numAvail;

    /**
     * 下一个可用于分配的对象下标
     * 最近释放的small内存块对应额下标会被设置为nextAvail，期望获得更好的CPU高速缓存的局部性
     * 因为刚释放后如果同一线程再要求分配同样规格的buf，可能对应的内存块已经被映射加载到了高速缓存中，对性能会有所提升
     * */
    private int nextAvail;

    /**
     * 双向链表节点
     * */
    MyPoolSubPage<T> prev;
    MyPoolSubPage<T> next;

    final ReentrantLock lock;

    /**
     * PoolArena维护的PoolSubPage链表的头节点(头节点是哨兵节点，本身不承担small规格的内存分配)
     * */
    MyPoolSubPage(int headIndex) {
        chunk = null;
        lock = new ReentrantLock();
        pageShifts = -1;
        runOffset = -1;
        elemSize = -1;
        runSize = -1;
        bitmap = null;
        bitmapLength = -1;
        maxNumElems = 0;
        this.headIndex = headIndex;
    }

    /**
     * 创建普通的PoolSubPage对象(实际进行small类型buf的分配)
     * */
    MyPoolSubPage(MyPoolSubPage<T> head, MyPoolChunk<T> chunk, int pageShifts, int runOffset, int runSize, int elemSize) {
        this.headIndex = head.headIndex;
        this.chunk = chunk;
        this.pageShifts = pageShifts;
        this.runOffset = runOffset;
        this.runSize = runSize;
        this.elemSize = elemSize;

        doNotDestroy = true;

        // 最大可分配对象数 = 内存段总大小 / 单个内存对象元素大小
        maxNumElems = runSize / elemSize;
        // 初始化时，当前可分配对象数 = 最大可分配对象数
        numAvail = maxNumElems;
        // bitMap中维护的是long类型64位，所以bitMap所需要的long元素的数量应该是最大元素数除以64(2^6)
        int bitmapLength = maxNumElems >>> 6;
        if ((maxNumElems & 63) != 0) {
            // 除不尽，则bitMap长度向上取整补足
            bitmapLength ++;
        }
        this.bitmapLength = bitmapLength;
        bitmap = new long[bitmapLength];

        // 初始化时，从第0号位置开始分配
        nextAvail = 0;

        lock = null;

        // 将当前新的PoolSubPage挂载在对应head节点所在的双向链表中
        addToPool(head);
    }

    /**
     * 选择一个可用的内存块进行small分配，包装成handle返回
     * @return -1代表分配失败， 大于0则代表分配成功
     */
    long allocate() {
        if (numAvail == 0 || !doNotDestroy) {
            // 当前SubPage无可分配内存块，分配失败返回-1
            return -1;
        }

        final int bitmapIdx = getNextAvail();
        int q = bitmapIdx >>> 6; // bitmap中的第几个long 2^6=64
        int r = bitmapIdx & 63; //  对应long中的第几位，通过对64-1求模
        // 将对应位数修正为1，标识为已分配出去
        bitmap[q] |= 1L << r;

        numAvail--;
        if (numAvail == 0) {
            // 如果是最后一个空闲位被分配出去了，说明当前PoolSubPage已满，将当前PoolSubPage从双向链表中摘出去
            removeFromPool();
        }

        // 将当前分配的subPage内存块转换成handle返回
        return toHandle(bitmapIdx);
    }

    /**
     * 释放bitmapIdx处所对应的small规格内存
     * @return true 当前PoolSubPage依然需要被使用，不需回收
     *              当前PoolSubPage已经完全空闲，并且不再需要被使用，需要回收掉
     */
    boolean free(MyPoolSubPage<T> head, int bitmapIdx) {
        if (elemSize == 0) {
            // 特殊case，元素大小为0，不处理
            return true;
        }

        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        // 找到当前bitmapIdx对应的位数，将其标识为0(标识释放)
        bitmap[q] ^= 1L << r;

        // 记录一下nextAvail，提高性能
        this.nextAvail = bitmapIdx;

        int oldNumAvail = numAvail;
        this.numAvail++;
        if (oldNumAvail == 0) {
            // 如果之前PoolSubPage是满的（oldNumAvail为0），那么将当前PoolSubPage放回到对应的双向链表中去
            addToPool(head);
            /* When maxNumElems == 1, the maximum numAvail is also 1.
             * Each of these PoolSubpages will go in here when they do free operation.
             * If they return true directly from here, then the rest of the code will be unreachable
             * and they will not actually be recycled. So return true only on maxNumElems > 1. */
            if (maxNumElems > 1) {
                // 对于maxNumElems == 1的逻辑不直接返回
                return true;
            }
        }

        if (numAvail != maxNumElems) {
            // 回收掉当前small规格后，当前PoolSubPage没有完全空闲
            return true;
        } else {
            // 回收掉当前small规格后，当前PoolSubPage已经完全空闲了，尝试着将其整个释放掉以节约内存

            if (prev == next) {
                // Do not remove if this subpage is the only one left in the pool.
                // 如果其是当前双向链表中唯一存在的PoolSubPage(prev == next == head)
                // 不进行回收
                return true;
            }else{
                // Remove this subpage from the pool if there are other subpages left in the pool.

                // 当前Arena内，还存在可用的PoolSubPage，将当前PoolSubPage销毁掉以节约内存
                doNotDestroy = false;
                removeFromPool();
                return false;
            }
        }
    }

    /**
     * handle转换逻辑和normal类型类似
     * */
    private long toHandle(int bitmapIdx) {
        int pages = runSize >> pageShifts;
        return (long) runOffset << MyPoolChunk.RUN_OFFSET_SHIFT
            | (long) pages << MyPoolChunk.SIZE_SHIFT
            | 1L << MyPoolChunk.IS_USED_SHIFT
            | 1L << MyPoolChunk.IS_SUBPAGE_SHIFT
            | bitmapIdx;
    }


    private int getNextAvail() {
        // nextAvail >= 0，说明之前恰好有内存块被回收了，就使用这个内存块
        // 因为刚释放后如果同一线程再要求分配同样规格的buf，可能对应的内存块已经被映射加载到了高速缓存中，对性能会有所提升
        int nextAvail = this.nextAvail;
        if (nextAvail >= 0) {
            // 分配后，将其标识为-1，避免重复分配
            this.nextAvail = -1;
            return nextAvail;
        }

        return findNextAvail();
    }

    private int findNextAvail() {
        // 没有最近被释放的内存块，从bitMap中小到大遍历一遍，直到找到一个可分配的内存块
        final long[] bitmap = this.bitmap;
        final int bitmapLength = this.bitmapLength;
        for (int i = 0; i < bitmapLength; i ++) {
            long bits = bitmap[i];
            if (~bits != 0) {
                // 当前long类型标识的64位中，存在至少一位为0，标识着大概率有空闲的内存可以分配
                // 为什么说是大概率？因为可能最后一位的long，末尾并没有映射实际的内存块，就为0了，但这个情况的0不代表可以分配出去
                // 所以还要在findNextAvail0里面继续精确的判断
                return findNextAvail0(i, bits);
            }else{
                // 整个long所有的位数都是1，全满，直接返回-1让上层往bitMap后面的位数里找
            }
        }

        // 遍历完了，没有可以空闲的内存块
        return -1;
    }

    private int findNextAvail0(int i, long bits) {
        final int maxNumElems = this.maxNumElems;
        final int baseVal = i << 6;

        // 遍历long类型64位中的每一位
        for (int j = 0; j < 64; j ++) {
            if ((bits & 1) == 0) {
                // bits与上1为0，说明最后1位为0
                int val = baseVal | j;
                if (val < maxNumElems) {
                    // val小于maxNumElems，说明是合法的空闲位，直接返回
                    return val;
                } else {
                    // val大于等于maxNumElems，说明已经越界了(bitmap最后一个long不满补齐的场景)。
                    // 不合法，无法分配了，break后返回-1
                    break;
                }
            }

            // 每次循环，推移1位，实现遍历每一位的逻辑
            bits >>>= 1;
        }
        return -1;
    }

    /**
     * 将当前节点插入到head节点的直接后继
     * */
    private void addToPool(MyPoolSubPage<T> head) {
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }

    /**
     * 将当前节点从PoolSubPage双向链表中删除
     * */
    private void removeFromPool() {
        prev.next = next;
        next.prev = prev;
        next = null;
        prev = null;
    }

    void lock() {
        lock.lock();
    }

    void unlock() {
        lock.unlock();
    }
}
