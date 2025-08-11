package com.my.netty.bytebuffer.netty.objectpool;

/**
 * 基本copy自netty的ObjectPool类
 *
 * 一个轻量级的对象池
 * */
public abstract class MyObjectPool<T> {

    /**
     * 从对象池中获取一个对象
     * 如果对象池中没有一个可以被重用的池化对象，也许会通过newObject(Handle)方法来创建一个对象
     */
    public abstract T get();

    /**
     * 用于通知对象池可以再次重用的句柄(handle)
     */
    public interface Handle<T> {
        /**
         * 如果可能的话，将对象回收并且使其能准备好被复用
         */
        void recycle(T self);
    }

    /**
     * 创建一个新的对象，该对象引用了给定的handle句柄对象.
     * 其一旦可以被重用时，调用Handle#recycle(Object)方法
     */
    public interface ObjectCreator<T> {

        /**
         * Creates an returns a new {@link Object} that can be used and later recycled via
         * {@link Handle#recycle(Object)}.
         *
         * @param handle can NOT be null.
         */
        T newObject(Handle<T> handle);
    }

    /**
     * 使用给定的ObjectCreator，创建一个新的对象池(ObjectPool)
     * 该对象池创建的对象应该被池化
     */
    public static <T> MyObjectPool<T> newPool(final ObjectCreator<T> creator) {
        if (creator == null) {
            throw new NullPointerException("creator");
        }

        return new RecyclerObjectPool<>(creator);
    }


    private static final class RecyclerObjectPool<T> extends MyObjectPool<T> {
        private final MyRecycler<T> recycler;

        RecyclerObjectPool(final ObjectCreator<T> creator) {
            recycler = new MyRecycler<T>() {
                @Override
                protected T newObject(Handle<T> handle) {
                    return creator.newObject(handle);
                }
            };
        }

        @Override
        public T get() {
            return recycler.get();
        }
    }
}
