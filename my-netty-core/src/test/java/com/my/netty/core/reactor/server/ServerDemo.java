package com.my.netty.core.reactor.server;

import com.my.netty.bytebuffer.netty.allocator.MyPooledByteBufAllocator;
import com.my.netty.bytebuffer.netty.allocator.MyUnpooledByteBufAllocator;
import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.codec.v1.EchoMessageDecoderV1;
import com.my.netty.core.reactor.codec.v1.EchoMessageEncoderV1;
import com.my.netty.core.reactor.codec.v2.EchoMessageDecoderV2;
import com.my.netty.core.reactor.codec.v2.EchoMessageEncoderV2;
import com.my.netty.core.reactor.config.DefaultChannelConfig;
import com.my.netty.core.reactor.handler.codec.MyLengthFieldBasedFrameDecoder;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipeline;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipelineSupplier;
import com.my.netty.core.reactor.server.v1.EchoServerEventHandler;
import com.my.netty.core.reactor.server.v2.EchoServerEventHandlerV2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.locks.LockSupport;

public class ServerDemo {

    public static void main(String[] args) throws IOException {
        DefaultChannelConfig defaultChannelConfig = new DefaultChannelConfig();
        defaultChannelConfig.setInitialReceiveBufferSize(16); // 设置小一点，方便测试
        defaultChannelConfig.setAllocator(new MyPooledByteBufAllocator()); // 测试池化ByteBuf功能

        MyNioServerBootstrap myNioServerBootstrap = new MyNioServerBootstrap(
            new InetSocketAddress(8080),
            // 先简单一点，只支持childEventGroup自定义配置pipeline
            new MyChannelPipelineSupplier() {
                @Override
                public MyChannelPipeline buildMyChannelPipeline(MyNioChannel myNioChannel) {
                    MyChannelPipeline myChannelPipeline = new MyChannelPipeline(myNioChannel);
                    // 解码器，解决拆包、黏包问题
                    myChannelPipeline.addLast(new MyLengthFieldBasedFrameDecoder(1024 * 1024, 4, 4));
                    // 注册自定义的EchoServerEventHandler
                    myChannelPipeline.addLast(new EchoMessageEncoderV2());
                    myChannelPipeline.addLast(new EchoMessageDecoderV2());
                    myChannelPipeline.addLast(new EchoServerEventHandlerV2());
                    return myChannelPipeline;
                }
            },1,5, defaultChannelConfig);
        myNioServerBootstrap.start();

        LockSupport.park();
    }
}
