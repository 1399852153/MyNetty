package com.my.netty.core.reactor.handler.context;


import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.eventloop.MyNioEventLoop;
import com.my.netty.core.reactor.handler.MyChannelEventHandler;
import com.my.netty.core.reactor.handler.MyChannelEventInvoker;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipeline;

public interface MyChannelHandlerContext extends MyChannelEventInvoker {

    MyNioChannel channel();

    MyChannelEventHandler handler();

    MyChannelPipeline getPipeline();

    MyNioEventLoop executor();
}
