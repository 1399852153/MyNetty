package com.my.netty.core.reactor.handler;

import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;

import java.util.concurrent.CompletableFuture;

/**
 * 事件处理器(相当于netty中ChannelInboundHandler和ChannelOutboundHandler合在一起)
 * */
public interface MyChannelEventHandler {

    // ========================= inbound入站事件 ==============================
    void channelRead(MyChannelHandlerContext ctx, Object msg) throws Exception;

    void channelReadComplete(MyChannelHandlerContext ctx) throws Exception;

    void exceptionCaught(MyChannelHandlerContext ctx, Throwable cause) throws Exception;

    // ========================= outbound出站事件 ==============================
    void close(MyChannelHandlerContext ctx) throws Exception;

    /**
     * doFlush为false ==> 相当于netty的write
     * doFLush为true ==> 相当于netty的writeAndFlush
     * */
    void write(MyChannelHandlerContext ctx, Object msg, boolean doFlush, CompletableFuture<MyNioChannel> completableFuture) throws Exception;

}
