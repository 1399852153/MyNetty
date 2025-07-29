package com.my.netty.bytebuffer.netty;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * 参考最初版本的netty AbstractReferenceCountedByteBuf实现（4.16.Final）
 * 高版本的netty进行了性能上的优化，但是细节太多，太复杂。我们这个netty学习的demo更关注netty整体的结构，细节上的优化先放过
 * 具体原理可以参考大佬的博客：https://www.cnblogs.com/binlovetech/p/18369244
 * */
public abstract class MyAbstractReferenceCountedByteBuf extends MyAbstractByteBuf{

    /**
     * 原子更新refCnt字段
     * */
    private static final AtomicIntegerFieldUpdater<MyAbstractReferenceCountedByteBuf> refCntUpdater =
            AtomicIntegerFieldUpdater.newUpdater(MyAbstractReferenceCountedByteBuf.class, "refCnt");

    /**
     * 被引用的次数
     *
     * 主要用于实现ReferenceCounted接口相关的逻辑
     * */
    private volatile int refCnt;

    protected MyAbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
        // 被创建就说明被引用了，被引用数初始化为1
        refCntUpdater.set(this, 1);
    }

    @Override
    public int refCnt() {
        return this.refCnt;
    }

    protected final void setRefCnt(int refCnt) {
        refCntUpdater.set(this, refCnt);
    }

    public MyByteBuf retain() {
        return this.retain0(1);
    }

    public MyByteBuf retain(int increment) {
        if(increment <= 0){
            throw new IllegalArgumentException("increment must > 0");
        }

        return this.retain0(increment);
    }

    private MyByteBuf retain0(int increment) {
        int refCnt;
        int nextCnt;
        do {
            refCnt = this.refCnt;
            // 先算出更新后预期的值，用于cas
            nextCnt = refCnt + increment;
            if (nextCnt <= increment) {
                // 参数有问题
                throw new IllegalArgumentException("illegal retain refCnt=" + refCnt + ", increment=" + increment);
            }
            // cas原子更新，如果compareAndSet返回false，则说明出现了并发更新
            // doWhile循环重新计算过，直到更新成功
        } while(!refCntUpdater.compareAndSet(this, refCnt, nextCnt));

        return this;
    }

    public boolean release() {
        return this.release0(1);
    }

    public boolean release(int decrement) {
        if(decrement <= 0){
            throw new IllegalArgumentException("decrement must > 0");
        }

        return this.release0(decrement);
    }

    private boolean release0(int decrement) {
        int refCnt;
        do {
            refCnt = this.refCnt;
            if (refCnt < decrement) {
                // 参数有问题，减的太多了
                throw new IllegalArgumentException("illegal retain refCnt=" + refCnt + ", decrement=" + decrement);
            }
        } while(!refCntUpdater.compareAndSet(this, refCnt, refCnt - decrement));

        if (refCnt == decrement) {
            // refCnt减为0了，释放该byteBuf（具体释放方式由子类处理）
            this.deallocate();
            // 返回true，说明当前release后，成功释放了
            return true;
        } else {
            // 返回false，说明当前release后还有别的地方仍然在引用该ByteBuf
            return false;
        }
    }

    protected abstract void deallocate();
}
