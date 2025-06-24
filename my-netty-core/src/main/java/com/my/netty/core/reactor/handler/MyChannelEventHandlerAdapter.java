package com.my.netty.core.reactor.handler;


import com.my.netty.core.reactor.handler.annotation.Skip;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;

/**
 * 用于简化用户自定义的handler的适配器
 *
 * 由于所有支持的方法都加上了@Skip注解，子类只需要重写想要关注的方法即可，其它未重写的方法将会在事件传播时被跳过
 * */
public class MyChannelEventHandlerAdapter implements MyChannelEventHandler{

    @Skip
    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.fireChannelRead(msg);
    }

    @Skip
    @Override
    public void exceptionCaught(MyChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }

    @Skip
    @Override
    public void close(MyChannelHandlerContext ctx) throws Exception {
        ctx.close();
    }

    @Skip
    @Override
    public void write(MyChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.write(msg);
    }
}
