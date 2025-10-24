package com.my.netty.core.reactor.client;

import com.my.netty.bytebuffer.netty.allocator.MyPooledByteBufAllocator;
import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.client.v1.EchoClientEventHandler;
import com.my.netty.core.reactor.client.v2.EchoClientEventHandlerV2;
import com.my.netty.core.reactor.codec.v1.EchoMessageDecoderV1;
import com.my.netty.core.reactor.codec.v1.EchoMessageEncoderV1;
import com.my.netty.core.reactor.codec.v2.EchoMessageDecoderV2;
import com.my.netty.core.reactor.codec.v2.EchoMessageEncoderV2;
import com.my.netty.core.reactor.config.DefaultChannelConfig;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipeline;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipelineSupplier;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Scanner;

public class ClientDemo {

    public static void main(String[] args) throws IOException {
        DefaultChannelConfig defaultChannelConfig = new DefaultChannelConfig();
        defaultChannelConfig.setInitialReceiveBufferSize(16); // 设置小一点，方便测试
        defaultChannelConfig.setAllocator(new MyPooledByteBufAllocator()); // 测试池化ByteBuf功能

        MyNioClientBootstrap myNioClientBootstrap = new MyNioClientBootstrap(new InetSocketAddress(8080),new MyChannelPipelineSupplier() {
            @Override
            public MyChannelPipeline buildMyChannelPipeline(MyNioChannel myNioChannel) {
                MyChannelPipeline myChannelPipeline = new MyChannelPipeline(myNioChannel);
                // 注册自定义的EchoClientEventHandler
                myChannelPipeline.addLast(new EchoMessageEncoderV2());
                myChannelPipeline.addLast(new EchoMessageDecoderV2());
                myChannelPipeline.addLast(new EchoClientEventHandlerV2());
                return myChannelPipeline;
            }
        }, defaultChannelConfig);

        myNioClientBootstrap.start();

        System.out.println("please input message:");
        while(true){
            Scanner sc = new Scanner(System.in);
            String msg = sc.next();
            System.out.println("get input message:" + msg);

            // 发送消息
            myNioClientBootstrap.sendMessage(msg);
        }
    }
}
