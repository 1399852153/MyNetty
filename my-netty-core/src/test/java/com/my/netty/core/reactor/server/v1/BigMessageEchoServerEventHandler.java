package com.my.netty.core.reactor.server.v1;

import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class BigMessageEchoServerEventHandler extends MyChannelEventHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(BigMessageEchoServerEventHandler.class);

    private static String message;

    static{
        StringBuilder stringBuilder = new StringBuilder();
        for(int i=0; i<50000000; i++){
            stringBuilder.append("i ");
        }

        message = stringBuilder.toString();
    }

    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) {
        // 已经decoder过了
        String receivedStr = (String) msg;

        logger.info("echo server received message:{}, channel={}",receivedStr,ctx.channel());

        // 读完了，echo服务器准备回写数据到客户端
        String echoMessage = "server echo:" + receivedStr;

        CompletableFuture<MyNioChannel> channelCompletedFuture = ctx.write("1 ",false);
        channelCompletedFuture.whenComplete(new BiConsumer<MyNioChannel, Throwable>() {
            @Override
            public void accept(MyNioChannel myNioChannel, Throwable throwable) {
                System.out.println("write 1 success!" + myNioChannel);
            }
        });
        ctx.write("222 ",false);
        ctx.write(message,false);
        ctx.write(echoMessage,true);
    }

    @Override
    public void channelReadComplete(MyChannelHandlerContext ctx) throws Exception {
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
