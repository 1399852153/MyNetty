package com.my.netty.threadlocal.util;

import com.my.netty.threadlocal.api.ThreadLocalApi;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.ArrayList;
import java.util.List;
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

        List<ThreadLocalApi<String>> threadLocalApiList = createThreadLocal(clazz,threadLocalNum);

        ExecutorService executorService = Executors.newFixedThreadPool(threads, threadFactory);

        long start = System.currentTimeMillis();
        for(int i=0; i<threads; i++){
            executorService.execute(()->{
                for(ThreadLocalApi<String> threadLocal : threadLocalApiList){
                    for(int j=0; j<repeatNum; j++){
                        String obj = "aaaaaa" + j;
                        threadLocal.set(obj);
                        String getObj = threadLocal.get();
                        if(getObj == null){
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

    private static List<ThreadLocalApi<String>> createThreadLocal(Class<? extends ThreadLocalApi> clazz,int num) throws InstantiationException, IllegalAccessException {
        List<ThreadLocalApi<String>> threadLocalApiList = new ArrayList<>();
        for(int i=0; i<num; i++){
            threadLocalApiList.add(clazz.newInstance());
        }

        return threadLocalApiList;
    }
}
