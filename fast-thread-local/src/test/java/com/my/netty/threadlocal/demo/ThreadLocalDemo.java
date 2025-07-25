package com.my.netty.threadlocal.demo;

import java.util.concurrent.locks.LockSupport;

public class ThreadLocalDemo {

    private static ThreadLocal<String> threadLocal = new ThreadLocal<>();

    public static void main(String[] args) {
        new Thread(() -> {
            doSleep();
            for(int i=0; i<100; i++){
                //设置t1线程中本地变量的值
                threadLocal.set("t1-set");
                //获取t1线程中本地变量的值
                System.out.println("t1线程局部变量的value : " + threadLocal.get() + " " + Thread.currentThread().getName());
            }
        }, "t1").start();

        new Thread(() -> {
            doSleep();
            for(int i=0; i<100; i++){
                //设置t2线程中本地变量的值
                threadLocal.set("t2-set");
                //获取t1线程中本地变量的值
                System.out.println("t2线程局部变量的value : " + threadLocal.get()  + " " + Thread.currentThread().getName());
            }
        }, "t2").start();

        LockSupport.park();
    }

    private static void doSleep(){
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
