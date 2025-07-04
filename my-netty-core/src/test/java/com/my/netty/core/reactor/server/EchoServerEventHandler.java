package com.my.netty.core.reactor.server;

import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoServerEventHandler extends MyChannelEventHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EchoServerEventHandler.class);

    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) {
        // 已经decoder过了
        String receivedStr = (String) msg;

        logger.info("echo server received message:{}, channel={}",receivedStr,ctx.channel());

        // 读完了，echo服务器准备回写数据到客户端
        String echoMessage = "server echo:" + receivedStr;

        ctx.write(echoMessage);
    }

    @Override
    public void channelReadComplete(MyChannelHandlerContext ctx) {
        logger.info("echo server channelReadComplete, channel={}",ctx.channel());
    }

    @Override
    public void exceptionCaught(MyChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void close(MyChannelHandlerContext ctx) throws Exception {
        ctx.close();
    }
}
