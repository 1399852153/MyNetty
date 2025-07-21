package com.my.netty.threadlocal.adapter;

import com.my.netty.threadlocal.api.ThreadLocalApi;
import io.netty.util.concurrent.FastThreadLocal;

public class NettyFastThreadLocalAdapter<T> implements ThreadLocalApi<T> {

    private final FastThreadLocal<T> fastThreadLocal = new FastThreadLocal<>();

    @Override
    public void set(T value) {
        fastThreadLocal.set(value);
    }

    @Override
    public T get() {
        return fastThreadLocal.get();
    }

    @Override
    public void remove() {
        fastThreadLocal.remove();
    }
}
