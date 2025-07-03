package com.my.netty.core.reactor.handler;

public interface MyChannelEventInvoker {

    // ========================= inbound入站事件 ==============================
    void fireChannelRead(Object msg);

    void fireExceptionCaught(Throwable cause);

    // ========================= outbound出站事件 ==============================
    void close();

    void write(Object msg);
}
