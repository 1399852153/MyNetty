package com.my.netty.threadlocal.constants;

public class PerformanceTestingConfig {

    public static final int threads;
    public static final int threadLocalNum = 100000;
    public static final int repeatNum = 1000;

    static {
        threads = Runtime.getRuntime().availableProcessors() * 2;
        System.out.println("threads=" + threads);
    }
}
