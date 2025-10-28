package com.my.netty.core.reactor.client.v2;

import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import com.my.netty.core.reactor.model.EchoMessageFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SocketChannel;

public class EchoClientEventHandlerV2 extends MyChannelEventHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EchoClientEventHandlerV2.class);

    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) {
        SocketChannel socketChannel = (SocketChannel) ctx.channel().getJavaChannel();

        // 经过decode后，msg一定是EchoMessage对象才对
        if(!(msg instanceof EchoMessageFrame)){
            logger.error("EchoClientEventHandlerV2 msg is not EchoMessage! msg.type={}", msg.getClass());
        }else{
            EchoMessageFrame echoMessageFrame = (EchoMessageFrame) msg;

            if(echoMessageFrame.getMsgLength() == echoMessageFrame.getMessageContent().length()){
                logger.info("echo client received message:{} , from={}, valid success!", echoMessageFrame,socketChannel.socket().getRemoteSocketAddress());
            }else{
                logger.info("echo client received message:{} , from={}, valid failed!", echoMessageFrame,socketChannel.socket().getRemoteSocketAddress());
            }
        }
    }

    @Override
    public void exceptionCaught(MyChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void close(MyChannelHandlerContext ctx) throws Exception {
        logger.info("echo client close channel!");

        ctx.close();
    }
}
