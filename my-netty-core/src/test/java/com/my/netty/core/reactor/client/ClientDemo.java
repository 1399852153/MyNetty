package com.my.netty.core.reactor.client;

import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.codec.EchoMessageDecoder;
import com.my.netty.core.reactor.codec.EchoMessageEncoder;
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

        MyNioClientBootstrap myNioClientBootstrap = new MyNioClientBootstrap(new InetSocketAddress(8080),new MyChannelPipelineSupplier() {
            @Override
            public MyChannelPipeline buildMyChannelPipeline(MyNioChannel myNioChannel) {
                MyChannelPipeline myChannelPipeline = new MyChannelPipeline(myNioChannel);
                // 注册自定义的EchoClientEventHandler
                myChannelPipeline.addLast(new EchoMessageEncoder());
                myChannelPipeline.addLast(new EchoMessageDecoder());
                myChannelPipeline.addLast(new EchoClientEventHandler());
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
