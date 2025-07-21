package com.my.netty.threadlocal.impl.netty;

public class MyFastThreadLocalRunnable implements Runnable {

    private final Runnable runnable;

    private MyFastThreadLocalRunnable(Runnable runnable) {
        this.runnable = runnable;
    }

    public void run() {
        try {
            this.runnable.run();
        } finally {
            MyFastThreadLocal.removeAll();
        }

    }

    static Runnable wrap(Runnable runnable) {
        if(runnable instanceof MyFastThreadLocalRunnable) {
            // 如果已经是MyFastThreadLocalRunnable包装类，直接返回原始对象
            return runnable;
        }else{
            // 否则返回包装后的MyFastThreadLocalRunnable
            return new MyFastThreadLocalRunnable(runnable);
        }
    }
}
