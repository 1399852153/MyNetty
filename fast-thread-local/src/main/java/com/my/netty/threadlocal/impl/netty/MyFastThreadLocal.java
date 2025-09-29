package com.my.netty.threadlocal.impl.netty;

import com.my.netty.threadlocal.api.ThreadLocalApi;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class MyFastThreadLocal<V> implements ThreadLocalApi<V> {

    private static final int variablesToRemoveIndex = 0;

    /**
     * threadLocal对象在线程对应的ThreadLocalMap的下标(构造函数中，对象初始化的时候就确定了)
     * */
    private final int index;

    public MyFastThreadLocal() {
        // 原子性自增，确保每一个FastThreadLocal对象都有独一无二的下标
        index = MyFastThreadLocalMap.nextVariableIndex();
    }

    /**
     * 删除与当前线程绑定的所有ThreadLocal对象
     * */
    @SuppressWarnings("unchecked")
    public static void removeAll() {
        // 获得与当前Thread绑定的ThreadLocalMap
        MyFastThreadLocalMap threadLocalMap = MyFastThreadLocalMap.getIfSet();
        if (threadLocalMap == null) {
            // 没有初始化过，无事发生
            return;
        }

        try {
            Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
            if (v != null && v != MyFastThreadLocalMap.UNSET) {
                // ThreadLocalMap数组中下标为0的地方，固定放线程所属的全体FastThreadLocal集合
                Set<MyFastThreadLocal<?>> variablesToRemove = (Set<MyFastThreadLocal<?>>) v;
                MyFastThreadLocal<?>[] variablesToRemoveArray = variablesToRemove.toArray(new MyFastThreadLocal[0]);
                for (MyFastThreadLocal<?> tlv: variablesToRemoveArray) {
                    tlv.remove(threadLocalMap);
                }
            }
        } finally {
            MyFastThreadLocalMap.remove();
        }
    }

    @Override
    public void remove() {
        MyFastThreadLocalMap myFastThreadLocalMap = MyFastThreadLocalMap.getIfSet();
        remove(myFastThreadLocalMap);
    }

    @SuppressWarnings("unchecked")
    private void remove(MyFastThreadLocalMap threadLocalMap) {
        if (threadLocalMap == null) {
            return;
        }

        // 从ThreadLocalMap中删除
        Object v = threadLocalMap.removeIndexedVariable(index);
        removeFromVariablesToRemove(threadLocalMap, this);

        if (v != MyFastThreadLocalMap.UNSET) {
            try {
                // threadLocal被删除时，供业务在子类中自定义的回调函数
                onRemoval((V) v);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public final V get() {
        // 获得当前线程所对应的threadLocalMap
        MyFastThreadLocalMap threadLocalMap = MyFastThreadLocalMap.get();

        // 基于index，以O(1)的效率精确的获得对应的threadLocalMap中的元素
        Object v = threadLocalMap.indexedVariable(index);
        if (v != MyFastThreadLocalMap.UNSET) {
            // 不为null，返回
            return (V) v;
        }

        // 为null，返回初始化的值
        return initialize(threadLocalMap);
    }

    @SuppressWarnings("unchecked")
    public final V getIfExists() {
        MyFastThreadLocalMap threadLocalMap = MyFastThreadLocalMap.getIfSet();
        if (threadLocalMap != null) {
            Object v = threadLocalMap.indexedVariable(index);
            if (v != MyFastThreadLocalMap.UNSET) {
                return (V) v;
            }
        }

        // 相比正常的get，在不存在时就直接返回null即可
        return null;
    }

    @Override
    public final void set(V value) {
        if (value != MyFastThreadLocalMap.UNSET) {
            // 正常set值
            MyFastThreadLocalMap threadLocalMap = MyFastThreadLocalMap.get();
            if (threadLocalMap.setIndexedVariable(index, value)) {
                // 如果之前的值是UNSET，把这个新的threadLocal加入到总的待删除集合中去
                addToVariablesToRemove(threadLocalMap, this);
            }
        } else {
            // 传的是UNSET逻辑上等于remove
            remove(MyFastThreadLocalMap.getIfSet());
        }
    }

    private V initialize(MyFastThreadLocalMap threadLocalMap) {
        // 获得默认初始化的值(与jdk的ThreadLocal一样，可以通过子类来重写initialValue)
        V v = initialValue();

        threadLocalMap.setIndexedVariable(index, v);
        addToVariablesToRemove(threadLocalMap, this);
        return v;
    }

    protected V initialValue(){
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void removeFromVariablesToRemove(MyFastThreadLocalMap threadLocalMap, MyFastThreadLocal<?> variable) {
        Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);

        if (v == MyFastThreadLocalMap.UNSET || v == null) {
            return;
        }

        // 从FastThreadLocalMap数组起始处的Set中也移除掉FastThreadLocal变量
        Set<MyFastThreadLocal<?>> variablesToRemove = (Set<MyFastThreadLocal<?>>) v;
        variablesToRemove.remove(variable);
    }

    @SuppressWarnings("unchecked")
    private static void addToVariablesToRemove(MyFastThreadLocalMap threadLocalMap, MyFastThreadLocal<?> variable) {
        // 获得threadLocal对象集合
        Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
        Set<MyFastThreadLocal<?>> variablesToRemove;
        if (v == MyFastThreadLocalMap.UNSET || v == null) {
            // 为null还未初始化，创建一个集合然后放到variablesToRemoveIndex位置上
            variablesToRemove = Collections.newSetFromMap(new IdentityHashMap<>());
            threadLocalMap.setIndexedVariable(variablesToRemoveIndex, variablesToRemove);
        } else {
            variablesToRemove = (Set<MyFastThreadLocal<?>>) v;
        }

        // 将threadLocal变量加入到集合汇总
        variablesToRemove.add(variable);
    }

    /**
     * threadLocal被删除时，供业务自定义的回调函数
     * */
    protected void onRemoval(@SuppressWarnings("UnusedParameters") V value) throws Exception { }

}
