package com.my.netty.core.reactor.client;

import com.my.netty.bytebuffer.netty.allocator.MyPooledByteBufAllocator;
import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.client.v2.EchoClientEventHandlerV2;
import com.my.netty.core.reactor.codec.v2.EchoMessageDecoderV2;
import com.my.netty.core.reactor.codec.v2.EchoMessageEncoderV2;
import com.my.netty.core.reactor.config.DefaultChannelConfig;
import com.my.netty.core.reactor.handler.codec.MyLengthFieldBasedFrameDecoder;
import com.my.netty.core.reactor.handler.pipeline.MyChannelPipeline;
import com.my.netty.core.reactor.handler.pipeline.MyChannelPipelineSupplier;
import com.my.netty.core.reactor.model.EchoMessageFrame;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.Scanner;

public class ClientDemoLoopSend {

    public static void main(String[] args) throws IOException, InterruptedException {
        DefaultChannelConfig defaultChannelConfig = new DefaultChannelConfig();
        defaultChannelConfig.setInitialReceiveBufferSize(1024); // 设置小一点，方便测试
        defaultChannelConfig.setAllocator(new MyPooledByteBufAllocator()); // 测试池化ByteBuf功能

        MyNioClientBootstrap myNioClientBootstrap = new MyNioClientBootstrap(new InetSocketAddress(8080),new MyChannelPipelineSupplier() {
            @Override
            public MyChannelPipeline buildMyChannelPipeline(MyNioChannel myNioChannel) {
                MyChannelPipeline myChannelPipeline = new MyChannelPipeline(myNioChannel);
                // 解码器，解决拆包、黏包问题
                myChannelPipeline.addLast(new MyLengthFieldBasedFrameDecoder(1024 * 1024, 4, 4));
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

            int count = 0;
            while(true) {
                for (int i = 0; i < 100; i++) {
                    EchoMessageFrame request = new EchoMessageFrame(msg + "#" + i);
                    // 批量发送消息，简单的压测下
                    myNioClientBootstrap.sendMessage(request);
                }

                count++;
                System.out.println("send batch message once! count=" + count + " " + new Date());
                Thread.sleep(1000L);
            }
        }
    }
}
