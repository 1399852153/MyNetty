package com.my.netty.threadlocal.impl.netty;

public class MyFastThreadLocalThread extends Thread{

    /**
     * 和jdk一样，thread对应的ThreadLocalMap也是惰性初始化的，目的是节约内存
     * */
    private MyFastThreadLocalMap myFastThreadLocalMap;

    public MyFastThreadLocalThread(Runnable target) {
        super(MyFastThreadLocalRunnable.wrap(target));
    }

    public MyFastThreadLocalMap getMyFastThreadLocalMap() {
        return myFastThreadLocalMap;
    }

    public void setMyFastThreadLocalMap(MyFastThreadLocalMap myFastThreadLocalMap) {
        this.myFastThreadLocalMap = myFastThreadLocalMap;
    }
}
