package com.my.netty.core.reactor.handler.context;


import com.my.netty.core.reactor.handler.MyChannelEventHandler;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipeline;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

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
    public void write(MyChannelHandlerContext ctx, Object msg) throws Exception {
        // 往外写的操作，一定是socketChannel
        SocketChannel socketChannel = (SocketChannel) ctx.getPipeline().getChannel().getJavaChannel();

        if(msg instanceof ByteBuffer){
            socketChannel.write((ByteBuffer) msg);
        }else{
            // msg走到head节点的时候，必须是ByteBuffer类型
            throw new Error();
        }
    }

    @Override
    public MyChannelEventHandler handler() {
        return this;
    }
}
