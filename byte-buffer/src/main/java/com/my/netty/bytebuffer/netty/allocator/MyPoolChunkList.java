package com.my.netty.bytebuffer.netty.allocator;


import com.my.netty.bytebuffer.netty.MyPooledByteBuf;

import static java.lang.Math.max;

public class MyPoolChunkList<T> {

    /**
     * 当前chunkList所归属的Arena
     * */
    private final MyPoolArena<T> arena;

    /**
     * 最小空间使用率(百分比)
     * */
    private final int minUsage;

    /**
     * 最大空间使用率(百分比)
     * */
    private final int maxUsage;

    /**
     * 最大可用空间
     * */
    private final int maxCapacity;

    /**
     * PoolChunkList本质上是一个PoolChunk的链表，将同样使用率区间的PoolChunk给管理起来
     * head就是这个PoolChunk链表的头节点
     * */
    private MyPoolChunk<T> head;

    /**
     * 最小空闲阈值(单位：字节)
     * 如果当前ChunkList内所维护的PoolChunk的实际空闲字节低于了这个最小空闲阈值，就要将其移动下一个更充实的ChunkList中去(next)
     * (比如从(25-75%)的利用率list => (50-100%)的那个list里去)
     * */
    private final int freeMinThreshold;

    /**
     * 最大空闲阈值(单位：字节)
     * 如果当前ChunkList内所维护的PoolChunk的实际空闲字节高于了这个最大空闲阈值，就要将其移动上一个更空旷的ChunkList中去(prev)
     * (比如从(25-75%)的利用率list => (1-50%)的那个list里去)
     * */
    private final int freeMaxThreshold;

    /**
     * 当前chunkList的后继list节点
     * */
    private final MyPoolChunkList<T> nextList;

    /**
     * 当前chunkList的前驱list节点
     * 前驱节点仅在PoolArena的构造函数中进行更新，本质上也可以看作时final的(因为构造对象时必须按照顺序创建，无法同时指定一个节点的前驱和后继)
     * */
    private MyPoolChunkList<T> prevList;

    MyPoolChunkList(MyPoolArena<T> arena, MyPoolChunkList<T> nextList, int minUsage, int maxUsage, int chunkSize) {
        this.arena = arena;
        this.nextList = nextList;
        this.minUsage = minUsage;
        this.maxUsage = maxUsage;

        // 计算出最大可用空间，方便下面计算空闲空间阈值上下限
        maxCapacity = calculateMaxCapacity(minUsage, chunkSize);

        // 基于最大使用率maxUsage，计算出最小的剩余空间阈值
        freeMinThreshold = (maxUsage == 100) ? 0 : (int) (chunkSize * (100.0 - maxUsage + 0.99999999) / 100L);
        // 基于最小使用率minUsage，计算出最大的剩余空间阈值
        freeMaxThreshold = (minUsage == 100) ? 0 : (int) (chunkSize * (100.0 - minUsage + 0.99999999) / 100L);
    }

    /**
     * 基于最小使用率和chunkSize计算出当前chunkList中所包含的PoolChunk最大可用空间
     */
    private static int calculateMaxCapacity(int minUsage, int chunkSize) {
        minUsage = minUsage0(minUsage);

        if (minUsage == 100) {
            // If the minUsage is 100 we can not allocate anything out of this list.
            // 如果最小使用率都是100%了，那就没有额外空间可以继续分配了
            return 0;
        }

        // Calculate the maximum amount of bytes that can be allocated from a PoolChunk in this PoolChunkList.
        //
        // As an example:
        // - If a PoolChunkList has minUsage == 25 we are allowed to allocate at most 75% of the chunkSize because
        //   this is the maximum amount available in any PoolChunk in this PoolChunkList.
        return  (int) (chunkSize * (100L - minUsage) / 100L);
    }

    private static int minUsage0(int value) {
        return max(1, value);
    }

    void setPrevList(MyPoolChunkList<T> prevList) {
        this.prevList = prevList;
    }

    /**
     * 向当前PoolChunkList申请分配内存
     * @return true 分配成功
     *         false 分配失败
     * */
    boolean allocate(MyPooledByteBuf<T> buf, int reqCapacity, MySizeClassesMetadataItem sizeClassesMetadataItem) {
        if (sizeClassesMetadataItem.getSize() > maxCapacity) {
            // 所申请的内存超过了当前ChunkList中Chunk单元的最大空间，无法分配返回false
            return false;
        }

        for (MyPoolChunk<T> currentChunk = head; currentChunk != null; currentChunk = currentChunk.next) {
            // todo 支持threadCache缓存

            // 从head节点开始遍历当前Chunk链表，尝试进行分配
            if (currentChunk.allocate(buf, reqCapacity, sizeClassesMetadataItem)) {
                // 当前迭代的Chunk分配内存成功，空闲空间减少
                // 判断一下分配后的空闲空间是否低于当前最小阈值
                if (currentChunk.freeBytes <= freeMinThreshold) {
                    // 分配后确实低于了当前ChunkList的最小阈值，将当前Chunk从原有的ChunkList链表中摘除
                    // 尝试将其放入后面内存使用率更高的ChunkList里(不一定就能放入直接后继，如果一次分配了很多，可能会放到更后面的ChunkList里)
                    remove(currentChunk);
                    nextList.add(currentChunk);
                }
                // 分配成功，返回true
                return true;
            }
        }

        // 遍历了当前ChunkList中的所有节点，没有任何一个Chunk单元能够进行分配，返回false
        return false;
    }

    /**
     * 属于该PoolChunkList中的Chunk进行内存释放
     * @return true 释放后，当前chunk还未完全释放，继续保留该chunk
     *         false 释放后，当前chunk已经完全空闲，需要被销毁掉
     * */
    boolean free(MyPoolChunk<T> chunk, long handle, int normCapacity) {
        // 首先通过free方法将当前Chunk中申请的(handle + normCapacity)对应的内存段释放掉
        chunk.free(handle, normCapacity);
        // 释放完成后，chunk的空闲空间会增加，判断一下chunk的空间使用率是否已经低于了当前ChunkList的下限
        if (chunk.freeBytes > freeMaxThreshold) {
            // 确实低于，将当前chunk从当前ChunkList中摘出来
            remove(chunk);

            // 尝试移动到更前面的(prev)的空间使用率更低的ChunkList里去(不一定就能放入直接前驱，如果一次释放了很多，可能会放到更前面的ChunkList里；或者因为Chunk完全空闲了，整个chunk直接回收掉)
            return move0(chunk);
        }
        return true;
    }

    private boolean move0(MyPoolChunk<T> chunk) {
        if (prevList == null) {
//            assert chunk.usage() == 0;

            // q000这个ChunkList是相对特殊的，prevList为null。在这个ChunkList中的Chunk如果低于了阈值，说明Chunk已经完全空闲了，返回false，在上层将Chunk直接销毁
            return false;
        }else{
            // 将当前Chunk移动到空间使用率更低的ChunkList中(prev)
            return prevList.move(chunk);
        }
    }

    private boolean move(MyPoolChunk<T> chunk) {
//        assert chunk.usage() < maxUsage;

        // 如果当前chunk的空闲空间大于当前PoolChunkList的最大空闲空间阈值
        if (chunk.freeBytes > freeMaxThreshold) {
            // 尝试着将其往空间使用率更低的PoolChunkList里放
            return move0(chunk);
        }

        // 当前chunk的空间使用率与当前PoolChunkList匹配，可以将其插入
        add0(chunk);
        return true;
    }

    void add(MyPoolChunk<T> chunk) {
        // 如果当前chunk的空闲空间少于当前PoolChunkList的最小空闲空间阈值
        if (chunk.freeBytes <= freeMinThreshold) {
            // 尝试着将其往空间使用率更高的PoolChunkList里放
            nextList.add(chunk);
            return;
        }

        // 当前chunk的空间使用率与当前PoolChunkList匹配，可以将其插入
        add0(chunk);
    }

    /**
     * 将chunk节点插入到当前双向链表的头部
     * */
    void add0(MyPoolChunk<T> chunk) {
        chunk.parent = this;
        if (head == null) {
            // 如果当前链表为空，则新插入的这个chunk自己当头结点
            head = chunk;
            chunk.prev = null;
            chunk.next = null;
        } else {
            // 当前插入的chunk作为新的head头节点
            chunk.prev = null;
            chunk.next = head;
            head.prev = chunk;
            head = chunk;
        }
    }

    /**
     * 将当前节点从其所属的双向链表中移除
     * */
    private void remove(MyPoolChunk<T> currentNode) {
        if (currentNode == head) {
            // 如果需要摘除的是头结点，则令其next节点为新的head节点
            head = currentNode.next;
            if (head != null) {
                // 如果链表不为空(老head节点存在next)，将老的head节点彻底从其next节点中摘除
                head.prev = null;
            }
        } else {
            // 是双向链表的非头结点，将其prev与next直接进行关联
            MyPoolChunk<T> next = currentNode.next;
            currentNode.prev.next = next;
            if (next != null) {
                next.prev = currentNode.prev;
            }
        }
    }
}
