package com.my.netty.core.reactor.handler;

public interface MyChannelEventInvoker {

    void fireChannelRead(Object msg);

    void fireExceptionCaught(Throwable cause);

    void close();

    void write(Object msg);
}
