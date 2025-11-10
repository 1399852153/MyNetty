package com.my.netty.core.reactor.handler.context;


import com.my.netty.core.reactor.handler.MyChannelEventHandler;
import com.my.netty.core.reactor.handler.pipeline.MyChannelPipeline;

public class MyDefaultChannelHandlerContext extends MyAbstractChannelHandlerContext{

    private final MyChannelEventHandler handler;

    public MyDefaultChannelHandlerContext(MyChannelPipeline pipeline, MyChannelEventHandler handler) {
        super(pipeline, handler.getClass());
        this.handler = handler;
    }

    @Override
    public MyChannelEventHandler handler() {
        return handler;
    }
}
