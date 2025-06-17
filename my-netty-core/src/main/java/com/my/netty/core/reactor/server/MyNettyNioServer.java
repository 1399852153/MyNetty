package com.my.netty.core.reactor.server;

import com.my.netty.core.reactor.eventloop.MyNioEventLoop;
import com.my.netty.core.reactor.eventloop.MyNioEventLoopGroup;
import com.my.netty.core.reactor.handler.MyEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

public class MyNettyNioServer {

    private static final Logger logger = LoggerFactory.getLogger(MyNettyNioServer.class);

    private final InetSocketAddress endpointAddress;

    private final MyNioEventLoopGroup bossGroup;

    public MyNettyNioServer(InetSocketAddress endpointAddress, MyEventHandler myEventHandler,
                            int bossThreads, int childThreads) {
        this.endpointAddress = endpointAddress;

        MyNioEventLoopGroup childGroup = new MyNioEventLoopGroup(myEventHandler,childThreads);
        this.bossGroup = new MyNioEventLoopGroup(myEventHandler,bossThreads,childGroup);
    }

    public void start() throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);

        MyNioEventLoop myNioEventLoop = this.bossGroup.next();

        myNioEventLoop.execute(()->{
            try {
                Selector selector = myNioEventLoop.getUnwrappedSelector();
                serverSocketChannel.socket().bind(endpointAddress);
                SelectionKey selectionKey = serverSocketChannel.register(selector, 0);
                // 监听accept事件
                selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_ACCEPT);
                logger.info("MyNioServer do start! endpointAddress={}",endpointAddress);
            } catch (IOException e) {
                logger.error("MyNioServer do bind error!",e);
            }
        });
    }
}
