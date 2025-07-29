package com.my.netty.bytebuffer.jdk;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.InvalidMarkException;


/**
 * 1 capacity/limit/position/mark 四个关键属性
 * 2 相对的读写/绝对的读写
 * 3 Clearing, flipping, and rewinding 三种方便的操作
 * 4 举例使用方式
 * 5 各种子类的差异
 * 6 存在的缺陷，引出netty的ByteBuf
 * */
public abstract class MyBuffer {

    /**
     * A buffer's capacity is the number of elements it contains.
     * The capacity of a buffer is never negative and never changes.
     *
     * buffer的capacity代表着总容量大小限制，不能为负数并且不可变
     * */
    private final int capacity;

    /**
     * A buffer's limit is the index of the first element that should not be read or written.
     * A buffer's limit is never negative and is never greater than its capacity.
     *
     * buffer的limit标识着第一个不可读或者写的index位置，不能为负数并且不能大于capacity
     * */
    private int limit;

    /**
     * A buffer's position is the index of the next element to be read or written.
     * A buffer's position is never negative and is never greater than its limit.
     *
     * buffer的position标识着下一个读或者写的元素的index位置，不能为负数并且不能大于limit
     * */
    private int position = 0;

    /**
     * Marking and resetting
     * A buffer's mark is the index to which its position will be reset when the reset method is invoked.
     * The mark is not always defined, but when it is defined it is never negative and is never greater than the position.
     * If the mark is defined then it is discarded when the position or the limit is adjusted to a value smaller than the mark.
     * If the mark is not defined then invoking the reset method causes an InvalidMarkException to be thrown.
     *
     * buffer的mark是用于reset方法(重置)被调用时，将position的值重试为mark对应下标.
     * mark并不总是被定义，但当它被定义时，它不会为负数，并且不会超过position
     * 如果mark被定义了，则它将会在position或者limit被调整为小于mark时被废弃(变成未定义的状态)
     * 如果mark没有被定义，则在调用reset方法时将会抛出InvalidMarkException
     * */
    private int mark = -1;

    // Used only by direct buffers
    // NOTE: hoisted here for speed in JNI GetDirectBufferAddress
    /**
     * 有两种Buffer，分别基于堆内内存和堆外内存
     * 堆外内存中，这个属性标示堆外内存具体的起始地址
     * */
    long address;

    /**
     * Creates a new buffer with the given mark, position, limit, and capacity, after checking invariants.
     */
    MyBuffer(int mark, int pos, int lim, int cap) {       // package-private
        if (cap < 0) {
            // The capacity of a buffer is never negative
            throw new IllegalArgumentException("Negative capacity: " + cap);
        }
        this.capacity = cap;
        setLimit(lim);
        setPosition(pos);
        if (mark >= 0) {
            // The mark is not always defined, but when it is defined it is never negative and is never greater than the position.
            if (mark > pos) {
                throw new IllegalArgumentException("mark > position: (" + mark + " > " + pos + ")");
            }
            this.mark = mark;
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public int getPosition() {
        return position;
    }

    public int getLimit() {
        return limit;
    }

    public int getMark() {
        return mark;
    }

    final void discardMark() {
        mark = -1;
    }

    /**
     * 是否可读
     * */
    public abstract boolean isReadOnly();

    public final MyBuffer setLimit(int newLimit) {
        if ((newLimit > capacity) || (newLimit < 0)) {
            // A buffer's limit is never negative and is never greater than its capacity
            throw new IllegalArgumentException();
        }

        // 给limit赋值
        limit = newLimit;
        if (position > newLimit) {
            // 确保position不能大于limit
            position = newLimit;
        }
        if (mark > newLimit) {
            // 如果mark被定义了，则它将会在position被调整为小于mark时被废弃(变成未定义的状态)
            mark = -1;
        }

        return this;
    }

    public final MyBuffer setPosition(int newPosition) {
        if ((newPosition > limit) || (newPosition < 0)) {
            // A buffer's position is never negative and is never greater than its limit
            throw new IllegalArgumentException("invalid newPosition=" + newPosition + " limit=" + limit);
        }
        if (mark > newPosition) {
            // 如果mark被定义了，则它将会在limit被调整为小于mark时被废弃(变成未定义的状态)
            mark = -1;
        }
        // 给position赋值
        position = newPosition;
        return this;
    }

    public final MyBuffer mark() {
        // 将mark记录为当前position的位置
        this.mark = this.position;
        return this;
    }

    /**
     * A buffer's mark is the index to which its position will be reset when the reset method is invoked.
     *
     * buffer的mark是用于reset方法(重置)被调用时，将position的值重试为mark对应下标.
     * */
    public final MyBuffer reset() {
        int m = mark;
        if (m < 0) {
            // If the mark is not defined then invoking the reset method causes an InvalidMarkException to be thrown.
            // 如果mark没有被定义，则在调用reset方法时将会抛出InvalidMarkException
            throw new InvalidMarkException();
        }
        position = m;
        return this;
    }

    /**
     * makes a buffer ready for a new sequence of channel-read or relative put operations:
     * It sets the limit to the capacity and the position to zero.
     *
     * 令buffer准备好作为一个新的序列用于channel读操作或者说让channel将数据put进来
     * 设置limit为capacity并且将position设置为0
     *
     * 注意：clear并没有真正的将buffer里的数据完全清零，而仅仅是通过修改关键属性的方式逻辑进行了逻辑上的clear，这样性能更好
     * */
    public final MyBuffer clear() {
        position = 0;
        limit = capacity;
        mark = -1;
        return this;
    }

    /**
     * makes a buffer ready for a new sequence of channel-write or relative get operations:
     * It sets the limit to the current position and then sets the position to zero.
     *
     * 令buffer准备好作为一个新的序列用于channel写操作或者说让channel将写进去的数据get走：
     * 设置limit为当前的position的值，并且将position设置为0
     * */
    public final MyBuffer flip() {
        limit = position;
        position = 0;
        mark = -1;
        return this;
    }

    /**
     * makes a buffer ready for re-reading the data that it already contains:
     * It leaves the limit unchanged and sets the position to zero.
     *
     * 让一个buffer准备好重新读取数据，即在limit不变的情况下，将position设置为0
     * 因为读取操作会不断地推进position的位置，重置position为0，相当于允许读取重头读(类似磁带进行了倒带，即rewind)
     * */
    public final MyBuffer rewind() {
        position = 0;
        mark = -1;
        return this;
    }

    /**
     * 返回当前buffer还剩余可用的元素个数(即limit-position)
     * */
    public final int remaining() {
        int rem = limit - position;
        return Math.max(rem, 0);
    }

    /**
     * 是否还有剩余可用的元素个数
     * */
    public final boolean hasRemaining() {
        return position < limit;
    }

    static void checkBounds(int off, int len, int size) {
        if ((off | len | (off + len) | (size - (off + len))) < 0) {
            throw new IndexOutOfBoundsException();
        }
    }

    final int nextGetIndex() {
        int p = position;
        if (p >= limit) {
            throw new BufferUnderflowException();
        }
        position = p + 1;
        return p;
    }

    final int nextGetIndex(int nb) {
        int p = position;
        if (limit - p < nb){
            throw new BufferUnderflowException();
        }
        position = p + nb;
        return p;
    }

    final int nextPutIndex() {
        int p = position;
        if (p >= limit) {
            // 相比nextGetIndex，抛出的异常不同
            throw new BufferOverflowException();
        }
        position = p + 1;
        return p;
    }

    final int nextPutIndex(int nb) {
        int p = position;
        if (limit - p < nb) {
            throw new BufferOverflowException();
        }
        position = p + nb;
        return p;
    }

    final int checkIndex(int i) {
        if ((i < 0) || (i >= limit)) {
            throw new IndexOutOfBoundsException();
        }
        return i;
    }

    final int checkIndex(int i, int nb) {
        if ((i < 0) || (nb > limit - i)) {
            throw new IndexOutOfBoundsException();
        }
        return i;
    }
}
