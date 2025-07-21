package com.my.netty.threadlocal.impl.netty;

import java.util.concurrent.ThreadFactory;

public class MyFastThreadLocalThreadFactory implements ThreadFactory {

    @Override
    public Thread newThread(Runnable r) {
        return new MyFastThreadLocalThread(r);
    }
}
