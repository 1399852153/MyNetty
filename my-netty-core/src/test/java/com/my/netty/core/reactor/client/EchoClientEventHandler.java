package com.my.netty.core.reactor.client;

import com.my.netty.core.reactor.handler.MyEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class EchoClientEventHandler implements MyEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(EchoClientEventHandler.class);

    @Override
    public void fireChannelRead(SelectableChannel selectableChannel, byte[] msg) {
        SocketChannel socketChannel = (SocketChannel) selectableChannel;

        // echo客户端接收到读事件后，只是简单的打印出来
        String receivedStr = new String(msg, StandardCharsets.UTF_8);
        logger.info("echo client received message:{} ,from={}",receivedStr,socketChannel.socket().getRemoteSocketAddress());
    }
}
