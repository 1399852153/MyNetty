package com.my.netty.threadlocal.impl.netty;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class MyFastThreadLocalMap {

    /**
     * 从1开始，0是特殊的位置
     * */
    private static final AtomicInteger nextIndex = new AtomicInteger(1);

    private static final int INDEXED_VARIABLE_TABLE_INITIAL_SIZE = 32;

    /** Used by {@link MyFastThreadLocal} */
    private Object[] indexedVariables;

    private static final ThreadLocal<MyFastThreadLocalMap> slowThreadLocalMap = new ThreadLocal<>();

    private static final int ARRAY_LIST_CAPACITY_EXPAND_THRESHOLD = 1 << 30;

    /**
     * 等价于null的一个全局对象
     * */
    public static final Object UNSET = new Object();

    // Reference: https://hg.openjdk.java.net/jdk8/jdk8/jdk/file/tip/src/share/classes/java/util/ArrayList.java#l229
    private static final int ARRAY_LIST_CAPACITY_MAX_SIZE = Integer.MAX_VALUE - 8;

    public MyFastThreadLocalMap() {
        // 初始化内部数组
        this.indexedVariables = newIndexedVariableTable();
    }

    private static Object[] newIndexedVariableTable() {
        Object[] array = new Object[INDEXED_VARIABLE_TABLE_INITIAL_SIZE];
        Arrays.fill(array, UNSET);
        return array;
    }

    public static int nextVariableIndex() {
        int index = nextIndex.getAndIncrement();
        if (index >= ARRAY_LIST_CAPACITY_MAX_SIZE || index < 0) {
            nextIndex.set(ARRAY_LIST_CAPACITY_MAX_SIZE);
            throw new IllegalStateException("too many thread-local indexed variables");
        }
        return index;
    }

    public static MyFastThreadLocalMap getIfSet() {
        Thread thread = Thread.currentThread();
        if (thread instanceof MyFastThreadLocalThread) {
            return ((MyFastThreadLocalThread) thread).getMyFastThreadLocalMap();
        }else{
            return slowThreadLocalMap.get();
        }
    }

    public static void remove() {
        Thread thread = Thread.currentThread();
        if (thread instanceof MyFastThreadLocalThread) {
            // 清理掉thread的这个map设置为null
            ((MyFastThreadLocalThread) thread).setMyFastThreadLocalMap(null);
        } else {
            slowThreadLocalMap.remove();
        }
    }

    public static MyFastThreadLocalMap get() {
        Thread thread = Thread.currentThread();
        if (thread instanceof MyFastThreadLocalThread) {
            // 如果当前线程是FastThreadLocalThread，直接获取对应的MyFastThreadLocalMap
            MyFastThreadLocalThread myFastThreadLocalThread = (MyFastThreadLocalThread) thread;
            MyFastThreadLocalMap threadLocalMap = myFastThreadLocalThread.getMyFastThreadLocalMap();
            if (threadLocalMap == null) {
                // 没有就初始化一个空的，然后与当前线程绑定一下
                myFastThreadLocalThread.setMyFastThreadLocalMap(threadLocalMap = new MyFastThreadLocalMap());
            }
            return threadLocalMap;
        } else {
            // 如果当前线程不是FastThreadLocalThread，降级从slowThreadLocalMap中获取
            MyFastThreadLocalMap ret = slowThreadLocalMap.get();
            if (ret == null) {
                // 没有就初始化一个空的，然后与当前线程绑定一下
                ret = new MyFastThreadLocalMap();
                slowThreadLocalMap.set(ret);
            }
            return ret;
        }
    }

    public Object indexedVariable(int index) {
        Object[] lookup = indexedVariables;
        // 判断有没有越界，越界了返回UNSET
        return index < lookup.length? lookup[index] : UNSET;
    }

    /**
     * 将value放到index对应的位置上去
     * */
    public boolean setIndexedVariable(int index, Object value) {
        Object[] lookup = indexedVariables;
        if (index < lookup.length) {
            // 简单的将value放入index处
            Object oldValue = lookup[index];
            lookup[index] = value;
            return oldValue == UNSET;
        } else {
            // 发现index超过了当前数组的大小，进行扩容。然后将value放入index处
            expandIndexedVariableTableAndSet(index, value);
            return true;
        }
    }

    /**
     * 数组扩容，并且将value放入index下标处
     * */
    private void expandIndexedVariableTableAndSet(int index, Object value) {
        Object[] oldArray = indexedVariables;
        final int oldCapacity = oldArray.length;
        int newCapacity;
        if (index < ARRAY_LIST_CAPACITY_EXPAND_THRESHOLD) {
            // 类似jdk HashMap的扩容方法(tableSizeFor)
            // 以index为基础进行扩容,将newCapacity设置为比恰好index大的，为2次幂的数(如果index已经是2次幂的数，则newCapacity会扩容两倍)
            newCapacity = index;
            newCapacity |= newCapacity >>>  1;
            newCapacity |= newCapacity >>>  2;
            newCapacity |= newCapacity >>>  4;
            newCapacity |= newCapacity >>>  8;
            newCapacity |= newCapacity >>> 16;
            newCapacity ++;
        } else {
            newCapacity = ARRAY_LIST_CAPACITY_MAX_SIZE;
        }

        // 类似jdk的ArrayList，将内部数组扩容(老数组的数据copy到新数组里)
        Object[] newArray = Arrays.copyOf(oldArray, newCapacity);
        Arrays.fill(newArray, oldCapacity, newArray.length, UNSET);
        newArray[index] = value;

        // 内部数组指向扩容后的新数组
        indexedVariables = newArray;
    }

    public Object removeIndexedVariable(int index) {
        Object[] lookup = indexedVariables;
        if (index < lookup.length) {
            // 将对应下标标识为UNSET就算删除了
            Object v = lookup[index];
            lookup[index] = UNSET;
            return v;
        } else {
            return UNSET;
        }
    }
}
