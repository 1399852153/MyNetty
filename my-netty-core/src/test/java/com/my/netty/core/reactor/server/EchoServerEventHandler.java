package com.my.netty.core.reactor.server;

import com.my.netty.core.reactor.handler.MyEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class EchoServerEventHandler implements MyEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(EchoServerEventHandler.class);

    @Override
    public void fireChannelRead(SelectableChannel selectableChannel, byte[] msg) throws IOException {
        SocketChannel socketChannel = (SocketChannel) selectableChannel;
        String receivedStr = new String(msg, StandardCharsets.UTF_8);

        logger.info("echo server received message:{} ,from={}",receivedStr,socketChannel.socket().getRemoteSocketAddress());

        // 读完了，echo服务器准备回写数据到客户端
        String echoMessage = "server echo:" + receivedStr;
        ByteBuffer writeBuffer = ByteBuffer.allocateDirect(1024);
        writeBuffer.put(echoMessage.getBytes(StandardCharsets.UTF_8));
        writeBuffer.flip();
        socketChannel.write(writeBuffer);
    }
}
