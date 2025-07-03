package com.my.netty.core.reactor.eventloop;


import com.my.netty.core.reactor.handler.pinpline.MyChannelPipelineSupplier;

import java.util.concurrent.atomic.AtomicInteger;

public class MyNioEventLoopGroup {

    private final MyNioEventLoop[] executors;

    private final int nThreads;

    private final AtomicInteger atomicInteger = new AtomicInteger();

    public MyNioEventLoopGroup(MyChannelPipelineSupplier myChannelPipelineSupplier, int nThreads) {
        this(myChannelPipelineSupplier,nThreads,null);
    }

    public MyNioEventLoopGroup(MyChannelPipelineSupplier myChannelPipelineSupplier, int nThreads, MyNioEventLoopGroup childGroup) {
        if(nThreads <= 0){
            throw new IllegalArgumentException("MyNioEventLoopGroup nThreads must > 0");
        }

        this.nThreads = nThreads;

        // 基于参数，初始化对应数量的eventLoop
        executors = new MyNioEventLoop[nThreads];
        for(int i=0; i<nThreads; i++){
            MyNioEventLoop myNioEventLoop = new MyNioEventLoop(childGroup);
            myNioEventLoop.setMyChannelPipelineSupplier(myChannelPipelineSupplier);
            executors[i] = myNioEventLoop;
        }
    }

    public MyNioEventLoop next(){
        // 轮训分摊负载
        int index = atomicInteger.getAndIncrement() % nThreads;
        return executors[index];
    }
}
