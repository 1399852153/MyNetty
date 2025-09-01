package com.my.netty.bytebuffer.netty.objectpool;

import com.my.netty.threadlocal.impl.netty.MyFastThreadLocal;
import com.my.netty.threadlocal.impl.netty.MyFastThreadLocalThread;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscChunkedArrayQueue;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * 参考自Netty 4.1.118的Recycler类，但做了一定的简化
 * */
public abstract class MyRecycler<T> {

    /**
     * 每个线程最多可以池化缓存的对象数量。因为内存是有限的，不能无限制的池化。所以需要用户基于开启的线程数和所池化缓存对象的平均内存大小来设置
     * maxCapacityPerThread也可以被设置为0，来标识不进行池化
     *
     * netty里可以基于构造函数指定，但一般基于系统参数io.netty.recycler.maxCapacityPerThread来控制，简单起见直接写死
     * */
    private final int maxCapacityPerThread = 4096;

    /**
     * netty里可以基于构造函数指定，但一般基于系统参数io.netty.recycler.ratio控制，简单起见直接写死
     * */
    private final int interval = 8;

    /**
     * netty里可以基于构造函数指定，但一般基于系统参数io.netty.recycler.chunkSize控制，简单起见直接写死
     * */
    private final int chunkSize = 32;

    /**
     * 是否只有FastThreadLocal类型的线程才进行byteBuf的池化
     * */
    private static final boolean BATCH_FAST_TL_ONLY = true;

    private final MyFastThreadLocal<LocalPool<T>> threadLocal = new MyFastThreadLocal<LocalPool<T>>() {
        @Override
        protected LocalPool<T> initialValue() {
            return new LocalPool<T>(maxCapacityPerThread, interval, chunkSize);
        }

        @Override
        protected void onRemoval(LocalPool<T> value) throws Exception {
            super.onRemoval(value);
            MessagePassingQueue<DefaultHandle<T>> handles = value.pooledHandles;
            value.pooledHandles = null;
            value.owner = null;
            handles.clear();
        }
    };

    protected abstract T newObject(Handle<T> handle);


    /**
     * 获取池化对象
     * 1. 优先从线程本地对象池LocalPool获取
     *    对象池本身内部很复杂，获取池化对象时一般都要加同步锁防止并发。引入LocalPool的目的是为了尽可能的无锁化，提高效率
     * 2. 每个线程能池化的对象个数是有限的(maxCapacityPerThread控制)
     * 3. 不是所有类型的线程都能持有池化对象（默认配置只有netty的FastThreadLocalThread才行）
     *
     * 第2和3设计的目的都是为了避免过多的池化对象导致占用过多的内存
     * */
    public final T get() {
        if (maxCapacityPerThread == 0) {
            // netty中maxCapacityPerThread是可以动态配置的。如果每个线程池化的个数为0，就不需要进行池化，返回一个持有NOOP_HANDLE的对象做降级兼容（逻辑上是非池化的）。NOOP_HANDLE在回收时recycle方法里什么也不做
            return newObject((Handle<T>) NOOP_HANDLE);
        }

        // 获得当前线程自己独有的threadLocal本地对象池
        LocalPool<T> localPool = threadLocal.get();
        // 从本地对象池中尝试获取一个可用的对象出来
        DefaultHandle<T> handle = localPool.claim();
        T obj;
        if (handle == null) {
            // 为null说明线程本地对象池中拿不出来可用的池化对象
            // 调用newHandle尝试创建一个handle
            handle = localPool.newHandle();
            if (handle != null) {
                // 获取handle句柄成功，由子类实现newObject方法创建需要池化的对象(比如new一个PooledHeapByteBuf对象)
                obj = newObject(handle);
                // 将handle句柄与创建出来的池化对象进行绑定
                handle.set(obj);
            } else {
                // 创建handle句柄失败，返回一个逻辑上非池化的对象(newHandle里控制了创建需要池化的对象的比例)
                obj = newObject((Handle<T>) NOOP_HANDLE);
            }
        } else {
            // 线程本地对象池中可以拿出来一个可用的对象，直接返回
            obj = handle.get();
        }

        return obj;
    }

    private static final class LocalPool<T> implements MessagePassingQueue.Consumer<DefaultHandle<T>>{
        /**
         * 当前LocalPool中队列所缓存的最大handle个数(有batch和pooledHandles两个队列，有并发时好像会略高于这个值)
         * */
        private final int chunkSize;

        private final ArrayDeque<DefaultHandle<T>> batch;
        private volatile Thread owner;
        private volatile MessagePassingQueue<DefaultHandle<T>> pooledHandles;

        /**
         * 创建池化对象的比例(默认是8)
         * */
        private final int ratioInterval;

        /**
         * 配合ratioInterval比例功能的计数器，在newHandle方法中有用到
         * */
        private int ratioCounter;

        @SuppressWarnings("unchecked")
        LocalPool(int maxCapacity, int ratioInterval, int chunkSize) {
            this.ratioInterval = ratioInterval;
            this.chunkSize = chunkSize;

            batch = new ArrayDeque<>(chunkSize);

            Thread currentThread = Thread.currentThread();

            // owner = !BATCH_FAST_TL_ONLY || currentThread instanceof MyFastThreadLocalThread ? currentThread : null;
            if(!BATCH_FAST_TL_ONLY){
                // 配置中指定了不只有FastThreadLocal类型的线程才能池化，说明所有类型的线程都能池化
                owner = currentThread;
            }else{
                // 配置了只有FastThreadLocal类型的线程才能池化，判断一下currentThread的类型
                if(currentThread instanceof MyFastThreadLocalThread){
                    owner = currentThread;
                }else{
                    // 非FastThreadLocalThread类型的线程不能池化
                    owner = null;
                }
            }

            // netty这里有多种配置来设置不同类型的队列，这里简单起见直接写死一种
            // Mpsc即MultiProducerSingleConsumer，多写一读的队列
            // 别的线程在将handle释放时，将其写入该队列中。而owner线程则负责消费读取该队列，将别的线程释放的对象往batch里存
            pooledHandles = (MessagePassingQueue<DefaultHandle<T>>) new MpscChunkedArrayQueue<T>(chunkSize, maxCapacity);

            // 初始化时令ratioCounter = ratioInterval。确保第一次newHandle被调用时，创建的是可被回收的池化对象
            ratioCounter = ratioInterval; // Start at interval so the first one will be recycled.
        }

        /**
         * 从本地对象池中尝试获取一个可池化对象的handle句柄
         * */
        DefaultHandle<T> claim() {
            MessagePassingQueue<DefaultHandle<T>> handles = pooledHandles;
            if (handles == null) {
                // pooledHandles为空，说明持有该LocalPool的线程已经被关闭了，所以被设置为了null(release方法里的逻辑)
                // 没法去缓存池化对象了，返回null
                return null;
            }

            if (batch.isEmpty()) {
                // 本地缓存的对象为空，从handles里面尝试获取一批可池化对象，放到batch队列里(chunkSize控制个数)
                handles.drain(this, chunkSize);
            }

            // 从本地缓存的对象池里获取一个handle句柄
            DefaultHandle<T> handle = batch.pollLast();
            if (null != handle) {
                handle.toClaimed();
            }

            return handle;
        }

        /**
         * 池化对象回收时，尝试将其放回本地对象池中
         * */
        void release(DefaultHandle<T> handle) {
            // 回收时进行状态的判断，避免重复回收，提前发现bug
            handle.toAvailable();

            // owner线程是持有当前localPool对象池的线程
            Thread owner = this.owner;
            if (owner != null && Thread.currentThread() == owner && batch.size() < chunkSize) {
                // 如果是当前线程用完了进行回收，并且batch队列里缓存的池化对象数量小于chunkSize，则将其放回到LocalPool里
                accept(handle);
            } else if (owner != null && isTerminated(owner)) {
                // 如果持有LocalPool的线程已经被杀掉了(isTerminated)，清空当前LocalPool的pooledHandles
                // 防止claim方法里继续去获取池化对象
                this.owner = null;
                pooledHandles = null;
            } else {
                // 释放对象的线程不是持有LocalPool的线程，通过pooledHandles内存队列将其异步的放回到该LocalPool里去
                MessagePassingQueue<DefaultHandle<T>> handles = pooledHandles;
                if (handles != null) {
                    handles.relaxedOffer(handle);
                }
            }
        }

        private static boolean isTerminated(Thread owner) {
            // Do not use `Thread.getState()` in J9 JVM because it's known to have a performance issue.
            // See: https://github.com/netty/netty/issues/13347#issuecomment-1518537895
            // return PlatformDependent.isJ9Jvm() ? !owner.isAlive() : owner.getState() == Thread.State.TERMINATED;

            // 用的java8，简单起见直接判断状态
            return owner.getState() == Thread.State.TERMINATED;
        }

        DefaultHandle<T> newHandle() {
            if (++ratioCounter >= ratioInterval) {
                // 基于ratioInterval创建可回收的池化对象
                ratioCounter = 0; // 重置计数器，实现按比例生成可回收的池化对象
                return new DefaultHandle<>(this);
            }

            // 生成的是不回收的对象(get方法里调用的地方做了判断)
            return null;
        }

        @Override
        public void accept(DefaultHandle<T> tDefaultHandle) {
            // 将可池化的对象handle，放到本地缓存的batch队列里
            batch.addLast(tDefaultHandle);
        }
    }


    private static final class DefaultHandle<T> implements Handle<T> {
        private static final int STATE_CLAIMED = 0;
        private static final int STATE_AVAILABLE = 1;
        private static final AtomicIntegerFieldUpdater<DefaultHandle<?>> STATE_UPDATER;
        private volatile int state;
        private final LocalPool<T> localPool;
        private T value;

        static {
            AtomicIntegerFieldUpdater<?> updater = AtomicIntegerFieldUpdater.newUpdater(DefaultHandle.class, "state");
            // noinspection unchecked
            STATE_UPDATER = (AtomicIntegerFieldUpdater<DefaultHandle<?>>) updater;
        }

        DefaultHandle(LocalPool<T> localPool) {
            this.localPool = localPool;
        }

        @Override
        public void recycle(Object object) {
            if (object != this.value) {
                throw new IllegalArgumentException("object does not belong to handle");
            } else {
                // handle句柄被释放时，将其放回到本地缓存LocalPool中
                this.localPool.release(this);
            }
        }

        void toClaimed() {
            assert state == STATE_AVAILABLE;
            STATE_UPDATER.lazySet(this, STATE_CLAIMED);
        }

        T get() {
            return this.value;
        }

        void set(T value) {
            this.value = value;
        }

        void toAvailable() {
            int prev = STATE_UPDATER.getAndSet(this, 1);
            if (prev == 1) {
                throw new IllegalStateException("Object has been recycled already.");
            }
        }
    }


    /**
     * 为什么弄了个子interface？我的理解是类的内部引用起来更方便，也可以在未来进行进一步的拓展(基于4.1.80)，更高版本的弄了个EnhancedHandle
     * */
    public interface Handle<T> extends MyObjectPool.Handle<T> { }

    private static final Handle<?> NOOP_HANDLE = new Handle<Object>() {
        @Override
        public void recycle(Object object) {
            // NOOP
        }

        @Override
        public String toString() {
            return "NOOP_HANDLE";
        }
    };

}