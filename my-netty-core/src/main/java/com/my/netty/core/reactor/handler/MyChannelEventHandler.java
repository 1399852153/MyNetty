package com.my.netty.core.reactor.handler;

import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;

public interface MyChannelEventHandler {

    // ========================= inbound入站事件 ==============================
    void channelRead(MyChannelHandlerContext ctx, Object msg) throws Exception;

    void exceptionCaught(MyChannelHandlerContext ctx, Throwable cause) throws Exception;

    // ========================= outbound出站事件 ==============================
    void close(MyChannelHandlerContext ctx) throws Exception;

    void write(MyChannelHandlerContext ctx, Object msg) throws Exception;
}
