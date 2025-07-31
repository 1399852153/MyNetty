package com.my.netty.core.reactor.handler.context;


import com.my.netty.bytebuffer.netty.allocator.MyByteBufAllocator;
import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.eventloop.MyNioEventLoop;
import com.my.netty.core.reactor.handler.MyChannelEventHandler;
import com.my.netty.core.reactor.handler.MyChannelEventInvoker;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipeline;

/**
 * channelHandler上下文
 * */
public interface MyChannelHandlerContext extends MyChannelEventInvoker {

    /**
     * 获得当前的渠道
     * */
    MyNioChannel channel();

    /**
     * 获得当前上下文所对应的处理器
     * */
    MyChannelEventHandler handler();

    /**
     * 获得当前上下文所对应的pipeline流水线
     * */
    MyChannelPipeline getPipeline();

    /**
     * 获得当前上下文所对应的执行单元(EventLoop)
     * */
    MyNioEventLoop executor();

    /**
     * 获得ByteBufAllocator
     * */
    MyByteBufAllocator alloc();
}
