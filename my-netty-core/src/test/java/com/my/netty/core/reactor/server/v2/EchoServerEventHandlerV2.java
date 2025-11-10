package com.my.netty.core.reactor.server.v2;

import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import com.my.netty.core.reactor.model.EchoMessageFrame;
import com.my.netty.core.reactor.server.v1.EchoServerEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class EchoServerEventHandlerV2 extends MyChannelEventHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EchoServerEventHandlerV2.class);

    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) {
        // 经过decode后，接收到的msg一定是EchoMessage对象才对
        if(!(msg instanceof EchoMessageFrame)){
            logger.error("EchoServerEventHandlerV2 msg is not EchoMessage! msg.type={}", msg.getClass());
        }else{
            EchoMessageFrame receivedMessage = (EchoMessageFrame) msg;

            logger.info("echo server received receivedMessage:{}, channel={}",receivedMessage,ctx.channel());

            // 读完了，echo服务器准备回写数据到客户端
            String echoMessageStr = "server echo:" + receivedMessage.getMessageContent();

            EchoMessageFrame echoMessageFrame = new EchoMessageFrame(echoMessageStr);
            CompletableFuture channelCompletedFuture = ctx.write(echoMessageFrame,true);
            channelCompletedFuture.whenComplete(new BiConsumer<MyNioChannel, Throwable>() {
                @Override
                public void accept(MyNioChannel myNioChannel, Throwable throwable) {
                    logger.info("write message success! channel={}",myNioChannel);
                }
            });
        }
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
    public void close(MyChannelHandlerContext ctx) {
        logger.info("echo server close, channel={}",ctx.channel());

        ctx.close();
    }
}
