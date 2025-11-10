package com.my.netty.core.reactor.handler.context;


import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.handler.MyChannelEventHandler;
import com.my.netty.core.reactor.handler.pipeline.MyChannelPipeline;

import java.util.concurrent.CompletableFuture;

/**
 * pipeline的head哨兵节点
 * */
public class MyChannelPipelineHeadContext extends MyAbstractChannelHandlerContext implements MyChannelEventHandler {

    public MyChannelPipelineHeadContext(MyChannelPipeline pipeline) {
        super(pipeline,MyChannelPipelineHeadContext.class);
    }

    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) {
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelReadComplete(MyChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();
    }

    @Override
    public void exceptionCaught(MyChannelHandlerContext ctx, Throwable cause) {
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void close(MyChannelHandlerContext ctx) throws Exception {
        // 调用jdk原生的channel方法，关闭掉连接
        ctx.getPipeline().getChannel().getJavaChannel().close();
    }

    @Override
    public void write(MyChannelHandlerContext ctx, Object msg, boolean doFlush, CompletableFuture<MyNioChannel> completableFuture) throws Exception {
        // head是最后一个outBoundHandler，处理最终的写出操作
        ctx.getPipeline().getChannel().doWrite(msg,doFlush,completableFuture);
    }

    @Override
    public MyChannelEventHandler handler() {
        return this;
    }
}
