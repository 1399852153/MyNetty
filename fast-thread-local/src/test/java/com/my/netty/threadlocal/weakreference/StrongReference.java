package com.my.netty.threadlocal.weakreference;

public class StrongReference<T> {

    private final T referent;

    public StrongReference(T referent) {
        this.referent = referent;
    }

    public T get() {
        return referent;
    }
}
