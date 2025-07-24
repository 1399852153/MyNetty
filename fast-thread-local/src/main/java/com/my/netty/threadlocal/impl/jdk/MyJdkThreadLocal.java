package com.my.netty.threadlocal.impl.jdk;

import com.my.netty.threadlocal.api.ThreadLocalApi;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 基本参考自jdk中的ThreadLocal类
 * */
public class MyJdkThreadLocal<T> implements ThreadLocalApi<T> {

    private final int threadLocalHashCode = generateNextHashCode();

    private static final AtomicInteger nextHashCode = new AtomicInteger();

    /**
     * 对于二次幂扩容的map，冲突率最低的魔数
     * */
    private static final int HASH_INCREMENT = 0x61c88647;

    /**
     * 初始化时的值，默认是null
     *
     * 可以通过子类重写方法的方式自定义initialValue的返回值
     * */
    protected T initialValue() {
        return null;
    }

    public static <S> MyJdkThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        return new MyJdkThreadLocal<S>(){
            @Override
            protected S initialValue() {
                return supplier.get();
            }
        };
    }

    public int getThreadLocalHashCode() {
        return threadLocalHashCode;
    }

    @Override
    public T get() {
        Thread t = Thread.currentThread();
        if(!(t instanceof MyJdkThread)){
            // 简单起见，只支持MyJdkThread，不对其它类型的thread做兼容
            throw new IllegalStateException("Not a MyJdkThread");
        }

        MyJdkThread myJdkThread = (MyJdkThread) t;
        MyJdkThreadLocalMap myJdkThreadLocalMap = myJdkThread.getMyJdkThreadLocalMap();

        if (myJdkThreadLocalMap != null) {
            // 如果ThreadLocalMap存在，直接尝试获取当前的threadLocal对应的entry
            MyJdkThreadLocalMap.Entry e = myJdkThreadLocalMap.getEntry(this);
            if (e != null) {
                // 当前threadLocal在对应的thread的threadLocalMap中存在，则直接返回value值
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }

        // 走到这里有两种情况
        // 1. myJdkThreadLocalMap == null, 当前thread没有初始化ThreadLocalMap
        // 2. myJdkThreadLocalMap != null && threadLocalMap.getEntry == null
        //    当前thread存在threadLocalMap，但是里面不存在当前threadLocal对应的entry
        return setInitialValue(myJdkThread);
    }

    @Override
    public void set(T value) {
        Thread t = Thread.currentThread();
        if(!(t instanceof MyJdkThread)){
            // 简单起见，只支持MyJdkThread，不对其它类型的thread做兼容
            throw new IllegalStateException("Not a MyJdkThread");
        }

        MyJdkThread myJdkThread = (MyJdkThread) t;
        MyJdkThreadLocalMap myJdkThreadLocalMap = myJdkThread.getMyJdkThreadLocalMap();

        if (myJdkThreadLocalMap == null) {
            // set时当前thread还没有threadLocalMao,创建一个新的
            myJdkThread.setMyJdkThreadLocalMap(new MyJdkThreadLocalMap());
        }

        // 将value值set进当前线程的threadLocalMap中
        myJdkThread.getMyJdkThreadLocalMap().set(this,value);
    }

    @Override
    public void remove() {
        Thread t = Thread.currentThread();
        if(!(t instanceof MyJdkThread)){
            // 简单起见，只支持MyJdkThread，不对其它类型的thread做兼容
            throw new IllegalStateException("Not a MyJdkThread");
        }

        MyJdkThread myJdkThread = (MyJdkThread) t;
        MyJdkThreadLocalMap myJdkThreadLocalMap = myJdkThread.getMyJdkThreadLocalMap();

        if (myJdkThreadLocalMap != null) {
            myJdkThreadLocalMap.remove(this);
        }
    }

    private T setInitialValue(MyJdkThread myJdkThread) {
        T value = initialValue();

        if (myJdkThread.getMyJdkThreadLocalMap() == null) {
            // threadLocalMap是惰性加载的，按需创建(因为不是所有的thread都需要用到threadLocal，这样可以节约内存)
            MyJdkThreadLocalMap myJdkThreadLocalMap = new MyJdkThreadLocalMap();
            myJdkThread.setMyJdkThreadLocalMap(myJdkThreadLocalMap);
        }

        // 将将当前的threadLocal变量的初始化的值设置进去
        myJdkThread.getMyJdkThreadLocalMap().set(this,value);

        return value;
    }

    private static int generateNextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

}
