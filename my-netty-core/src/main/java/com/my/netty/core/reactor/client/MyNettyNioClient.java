package com.my.netty.core.reactor.client;

import com.my.netty.core.reactor.eventloop.MyNioEventLoop;
import com.my.netty.core.reactor.eventloop.MyNioEventLoopGroup;
import com.my.netty.core.reactor.handler.MyEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class MyNettyNioClient {

    private static final Logger logger = LoggerFactory.getLogger(MyNettyNioClient.class);

    private final InetSocketAddress remoteAddress;

    private final MyNioEventLoopGroup eventLoopGroup;

    private SocketChannel socketChannel;

    public MyNettyNioClient(InetSocketAddress remoteAddress, MyEventHandler myEventHandler, int nThreads) {
        this.remoteAddress = remoteAddress;

        this.eventLoopGroup = new MyNioEventLoopGroup(myEventHandler,nThreads);
    }

    public void start() throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        this.socketChannel = socketChannel;

        MyNioEventLoop myNioEventLoop = this.eventLoopGroup.next();

        myNioEventLoop.execute(()->{
            try {
                Selector selector = myNioEventLoop.getUnwrappedSelector();

                // doConnect
                // Returns: true if a connection was established,
                //          false if this channel is in non-blocking mode and the connection operation is in progress;
                if(!socketChannel.connect(remoteAddress)){
                    SelectionKey selectionKey = socketChannel.register(selector, 0);
                    int clientInterestOps = SelectionKey.OP_CONNECT | SelectionKey.OP_READ;
                    selectionKey.interestOps(selectionKey.interestOps() | clientInterestOps);
                }

                // 监听connect事件
                logger.info("MyNioClient do start! remoteAddress={}",remoteAddress);
            } catch (IOException e) {
                logger.error("MyNioClient do connect error!",e);
            }
        });
    }

    public void sendMessage(String msg) throws IOException {
        // 发送消息
        ByteBuffer writeBuffer = ByteBuffer.allocate(512);
        writeBuffer.put(msg.getBytes(StandardCharsets.UTF_8));
        writeBuffer.flip(); // 写完了，flip供后续去读取
        socketChannel.write(writeBuffer);
    }
}
