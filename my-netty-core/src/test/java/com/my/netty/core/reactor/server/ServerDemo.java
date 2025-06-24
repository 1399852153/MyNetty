package com.my.netty.core.reactor.server;

import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.codec.EchoMessageDecoder;
import com.my.netty.core.reactor.codec.EchoMessageEncoder;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipeline;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipelineSupplier;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.locks.LockSupport;

public class ServerDemo {

    public static void main(String[] args) throws IOException {
        MyNioServerBootstrap myNioServerBootstrap = new MyNioServerBootstrap(
            new InetSocketAddress(8080),
            // 先简单一点，只支持childEventGroup自定义配置pipeline
            new MyChannelPipelineSupplier() {
                @Override
                public MyChannelPipeline buildMyChannelPipeline(MyNioChannel myNioChannel) {
                    MyChannelPipeline myChannelPipeline = new MyChannelPipeline(myNioChannel);
                    // 注册自定义的EchoServerEventHandler
                    myChannelPipeline.addLast(new EchoMessageEncoder());
                    myChannelPipeline.addLast(new EchoMessageDecoder());
                    myChannelPipeline.addLast(new EchoServerEventHandler());
                    return myChannelPipeline;
                }
            },1,5);
        myNioServerBootstrap.start();

        LockSupport.park();
    }
}
