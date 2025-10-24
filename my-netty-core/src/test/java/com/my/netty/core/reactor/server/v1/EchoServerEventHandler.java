package com.my.netty.core.reactor.server.v1;

import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class EchoServerEventHandler extends MyChannelEventHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EchoServerEventHandler.class);

    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) {
        // 已经decoder过了
        String receivedStr = (String) msg;

        logger.info("echo server received message:{}, channel={}",receivedStr,ctx.channel());

        // 读完了，echo服务器准备回写数据到客户端
        String echoMessage = "server echo:" + receivedStr;

        CompletableFuture channelCompletedFuture = ctx.write(echoMessage,true);
        channelCompletedFuture.whenComplete(new BiConsumer<MyNioChannel, Throwable>() {
            @Override
            public void accept(MyNioChannel myNioChannel, Throwable throwable) {
                logger.info("write message success! channel={}",myNioChannel);
            }
        });
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
