package com.my.netty.core.reactor.handler.pinpline;


import com.my.netty.core.reactor.channel.MyNioChannel;

public interface MyChannelPipelineSupplier {

    MyChannelPipeline buildMyChannelPipeline(MyNioChannel myNioChannel);
}
