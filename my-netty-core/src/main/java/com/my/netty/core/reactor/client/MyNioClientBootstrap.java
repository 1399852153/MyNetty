package com.my.netty.core.reactor.client;

import com.my.netty.core.reactor.channel.MyNioSocketChannel;
import com.my.netty.core.reactor.config.DefaultChannelConfig;
import com.my.netty.core.reactor.eventloop.MyNioEventLoop;
import com.my.netty.core.reactor.eventloop.MyNioEventLoopGroup;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipelineSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class MyNioClientBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(MyNioClientBootstrap.class);

    private final InetSocketAddress remoteAddress;

    private final MyNioEventLoopGroup eventLoopGroup;

    private MyNioSocketChannel myNioSocketChannel;

    private final MyChannelPipelineSupplier myChannelPipelineSupplier;

    private final DefaultChannelConfig defaultChannelConfig;

    public MyNioClientBootstrap(InetSocketAddress remoteAddress, MyChannelPipelineSupplier myChannelPipelineSupplier,
                                DefaultChannelConfig defaultChannelConfig) {
        this.remoteAddress = remoteAddress;

        this.eventLoopGroup = new MyNioEventLoopGroup(myChannelPipelineSupplier, 1,defaultChannelConfig);

        this.myChannelPipelineSupplier = myChannelPipelineSupplier;

        this.defaultChannelConfig = defaultChannelConfig;
    }

    public void start() throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        MyNioEventLoop myNioEventLoop = this.eventLoopGroup.next();

        myNioEventLoop.execute(()->{
            try {
                Selector selector = myNioEventLoop.getUnwrappedSelector();

                myNioSocketChannel = new MyNioSocketChannel(selector,socketChannel,myChannelPipelineSupplier,defaultChannelConfig);

                myNioEventLoop.register(myNioSocketChannel);

                // doConnect
                // Returns: true if a connection was established,
                //          false if this channel is in non-blocking mode and the connection operation is in progress;
                if(!socketChannel.connect(remoteAddress)){
                    // 简单起见也监听READ事件，相当于netty中开启了autoRead
                    int clientInterestOps = SelectionKey.OP_CONNECT | SelectionKey.OP_READ;

                    myNioSocketChannel.getSelectionKey().interestOps(clientInterestOps);

                    // 监听connect事件
                    logger.info("MyNioClient do start! remoteAddress={}",remoteAddress);
                }else{
                    logger.info("MyNioClient do start connect error! remoteAddress={}",remoteAddress);

                    // connect操作直接失败，关闭连接
                    socketChannel.close();
                }
            } catch (IOException e) {
                logger.error("MyNioClient do connect error!",e);
            }
        });
    }

    public void sendMessage(String msg) {
        // 发送消息, 由encode编码器去编码为byteBuf
        myNioSocketChannel.getChannelPipeline().write(msg,true);
    }
}
