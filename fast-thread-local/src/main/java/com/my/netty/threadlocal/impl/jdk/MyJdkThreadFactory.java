package com.my.netty.threadlocal.impl.jdk;

import java.util.concurrent.ThreadFactory;

public class MyJdkThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable r) {
        return new MyJdkThread(r);
    }
}
