package com.my.netty.threadlocal.util;

import com.my.netty.threadlocal.api.ThreadLocalApi;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class PerformanceTestingUtil {

    public static long testThreadLocal(Class<? extends ThreadLocalApi> clazz, int threads, int threadLocalNum, int repeatNum) throws Exception {
        DefaultThreadFactory defaultThreadFactory = new DefaultThreadFactory("performance-testing");
        return testThreadLocal(clazz,threads,threadLocalNum,repeatNum,defaultThreadFactory);
    }

    public static long testThreadLocal(Class<? extends ThreadLocalApi> clazz, int threads, int threadLocalNum, int repeatNum, ThreadFactory threadFactory) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(threads);

        ThreadLocalApi<Integer>[] threadLocalApiArr = createThreadLocal(clazz,threadLocalNum);

        ExecutorService executorService = Executors.newFixedThreadPool(threads, threadFactory);

        long start = System.currentTimeMillis();
        for(int i=0; i<threads; i++){
            executorService.execute(()->{
                for(ThreadLocalApi<Integer> threadLocal : threadLocalApiArr){
                    for(int j=0; j<repeatNum; j++){
                        threadLocal.set(j);
                        Integer getObj = threadLocal.get();
                        if(getObj == null){
                            System.out.println("?????");
                        }

                        threadLocal.remove();
                        Integer getObj2 = threadLocal.get();
                        if(getObj2 != null){
                            System.out.println("?????");
                        }
                    }
                }

                countDownLatch.countDown();
            });
        }

        countDownLatch.await();
        long end = System.currentTimeMillis();

        executorService.shutdown();
        return end-start;
    }

    private static ThreadLocalApi<Integer>[] createThreadLocal(Class<? extends ThreadLocalApi> clazz,int num) throws InstantiationException, IllegalAccessException {
        ThreadLocalApi<Integer>[] threadLocalApiArr = new ThreadLocalApi[num];
        for(int i=0; i<num; i++){
            threadLocalApiArr[i] = clazz.newInstance();
        }

        return threadLocalApiArr;
    }
}
