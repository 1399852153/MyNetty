package com.my.netty.threadlocal.api;

public interface ThreadLocalApi<T> {

    void set(T value);

    T get();

    void remove();
}
