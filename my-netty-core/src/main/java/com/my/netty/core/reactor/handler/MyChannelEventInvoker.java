package com.my.netty.core.reactor.handler;

import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;

import java.util.concurrent.CompletableFuture;

public interface MyChannelEventInvoker {

    // ========================= inbound入站事件 ==============================
    void fireChannelRead(Object msg);

    void fireChannelReadComplete();

    void fireExceptionCaught(Throwable cause);

    // ========================= outbound出站事件 ==============================
    void close();

    CompletableFuture<MyNioChannel> write(Object msg, boolean doFlush);

    CompletableFuture<MyNioChannel> write(Object msg, boolean doFlush, CompletableFuture<MyNioChannel> completableFuture);
}
