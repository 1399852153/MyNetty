package com.my.netty.core.reactor.channel;


import com.my.netty.core.reactor.handler.pinpline.MyChannelPipelineSupplier;

import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class MyNioSocketChannel extends MyNioChannel{
    private SocketChannel socketChannel;

    public MyNioSocketChannel(
        Selector selector, SocketChannel socketChannel, MyChannelPipelineSupplier myChannelPipelineSupplier) {
        super(selector,socketChannel,myChannelPipelineSupplier);

        this.socketChannel = socketChannel;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    @Override
    public String toString() {
        return "MyNioSocketChannel{" +
            "socketChannel=" + socketChannel +
            "} " + super.toString();
    }
}
