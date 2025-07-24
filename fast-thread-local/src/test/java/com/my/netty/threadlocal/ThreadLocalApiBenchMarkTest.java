package com.my.netty.threadlocal;

import com.my.netty.threadlocal.adapter.JDKThreadLocalAdapter;
import com.my.netty.threadlocal.adapter.NettyFastThreadLocalAdapter;
import com.my.netty.threadlocal.api.ThreadLocalApi;
import com.my.netty.threadlocal.constants.PerformanceTestingConfig;
import com.my.netty.threadlocal.impl.jdk.MyJdkThreadFactory;
import com.my.netty.threadlocal.impl.jdk.MyJdkThreadLocal;
import com.my.netty.threadlocal.impl.netty.MyFastThreadLocal;
import com.my.netty.threadlocal.impl.netty.MyDefaultThreadFactory;
import com.my.netty.threadlocal.impl.simple.MySimpleThreadLocal;
import com.my.netty.threadlocal.util.PerformanceTestingUtil;
import org.junit.Test;

import java.util.concurrent.ThreadFactory;

public class ThreadLocalApiBenchMarkTest {

    @Test
    public void testMySimpleThreadLocal() throws Exception {
        doTest(MySimpleThreadLocal.class);
    }

    @Test
    public void testMyJdkThreadLocal() throws Exception {
        doTest(MyJdkThreadLocal.class, new MyJdkThreadFactory());
    }

    @Test
    public void testJDKThreadLocal() throws Exception {
        doTest(JDKThreadLocalAdapter.class);
    }

    @Test
    public void testMyFastThreadLocal() throws Exception {
        doTest(MyFastThreadLocal.class, new MyDefaultThreadFactory());
    }

    @Test
    public void testNettyFastThreadLocal() throws Exception {
        doTest(NettyFastThreadLocalAdapter.class);
    }

    private void doTest(Class<? extends ThreadLocalApi> clazz) throws Exception {
        System.out.println(clazz + ":" + PerformanceTestingUtil.testThreadLocal(
            clazz,
            PerformanceTestingConfig.threads,
            PerformanceTestingConfig.threadLocalNum,
            PerformanceTestingConfig.repeatNum) + "ms");
    }

    private void doTest(Class<? extends ThreadLocalApi> clazz, ThreadFactory threadFactory) throws Exception {
        System.out.println(clazz + ":" + PerformanceTestingUtil.testThreadLocal(
            clazz,
            PerformanceTestingConfig.threads,
            PerformanceTestingConfig.threadLocalNum,
            PerformanceTestingConfig.repeatNum, threadFactory) + "ms");
    }
}
