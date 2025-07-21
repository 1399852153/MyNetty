package com.my.netty.threadlocal.impl.simple;


import com.my.netty.threadlocal.api.ThreadLocalApi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MySimpleThreadLocal<T> implements ThreadLocalApi<T> {

    private final Map<Thread,T> threadLocalMap = new ConcurrentHashMap<>();

    public void set(T value){
        threadLocalMap.put(Thread.currentThread(),value);
    }

    public T get(){
        return threadLocalMap.get(Thread.currentThread());
    }

    public void remove(){
        threadLocalMap.remove(Thread.currentThread());
    }
}
