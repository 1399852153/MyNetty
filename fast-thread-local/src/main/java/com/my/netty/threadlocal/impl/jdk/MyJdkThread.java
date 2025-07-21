package com.my.netty.threadlocal.impl.jdk;

public class MyJdkThread extends Thread {

    private MyJdkThreadLocalMap myJdkThreadLocalMap;

    public MyJdkThread(Runnable target) {
        super(target);
    }

    public MyJdkThreadLocalMap getMyJdkThreadLocalMap() {
        return myJdkThreadLocalMap;
    }

    public void setMyJdkThreadLocalMap(MyJdkThreadLocalMap myJdkThreadLocalMap) {
        this.myJdkThreadLocalMap = myJdkThreadLocalMap;
    }
}
