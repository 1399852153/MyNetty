package com.my.netty.threadlocal.adapter;

import com.my.netty.threadlocal.api.ThreadLocalApi;

public class JDKThreadLocalAdapter<T> implements ThreadLocalApi<T> {

    private final ThreadLocal<T> threadLocal = new ThreadLocal<>();

    @Override
    public void set(T value) {
        threadLocal.set(value);
    }

    @Override
    public T get() {
        return threadLocal.get();
    }

    @Override
    public void remove() {
        threadLocal.remove();
    }

    public static void main(String[] args) {
        String result = useThreadLocal();
        System.out.println(result);

        System.gc();

        ThreadLocal<Integer> threadLocal = new ThreadLocal<>();
        threadLocal.set(1111);

        System.out.println(threadLocal.get());
    }

    private static String useThreadLocal(){
        ThreadLocal<String> threadLocal = new ThreadLocal<>();
        threadLocal.set("aaaaa");
        System.out.println(threadLocal.get());

        return threadLocal.get();
    }
}
