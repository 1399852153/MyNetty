package com.my.netty.core.reactor.client.v1;

import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SocketChannel;

public class EchoClientEventHandler extends MyChannelEventHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EchoClientEventHandler.class);

    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) {
        SocketChannel socketChannel = (SocketChannel) ctx.channel().getJavaChannel();

        // echo客户端接收到读事件后，只是简单的打印出来
        String receivedStr = (String) msg;
        logger.info("echo client received message:{} , from={}",receivedStr,socketChannel.socket().getRemoteSocketAddress());
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
