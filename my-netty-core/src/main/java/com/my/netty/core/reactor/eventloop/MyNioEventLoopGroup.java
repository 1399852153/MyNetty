package com.my.netty.core.reactor.eventloop;


import com.my.netty.core.reactor.config.DefaultChannelConfig;
import com.my.netty.core.reactor.handler.pipeline.MyChannelPipelineSupplier;

import java.util.concurrent.atomic.AtomicInteger;

public class MyNioEventLoopGroup {

    private final MyNioEventLoop[] executors;

    private final int nThreads;

    private final AtomicInteger atomicInteger = new AtomicInteger();

    public MyNioEventLoopGroup(MyChannelPipelineSupplier myChannelPipelineSupplier, int nThreads, DefaultChannelConfig defaultChannelConfig) {
        this(myChannelPipelineSupplier,nThreads,null,defaultChannelConfig);
    }

    public MyNioEventLoopGroup(MyChannelPipelineSupplier myChannelPipelineSupplier, int nThreads, MyNioEventLoopGroup childGroup,
                               DefaultChannelConfig defaultChannelConfig) {
        if(nThreads <= 0){
            throw new IllegalArgumentException("MyNioEventLoopGroup nThreads must > 0");
        }

        this.nThreads = nThreads;

        // 基于参数，初始化对应数量的eventLoop
        executors = new MyNioEventLoop[nThreads];
        for(int i=0; i<nThreads; i++){
            MyNioEventLoop myNioEventLoop = new MyNioEventLoop(childGroup,defaultChannelConfig);
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
