package com.my.netty.core.reactor.channel;

import com.my.netty.core.reactor.eventloop.MyNioEventLoop;
import com.my.netty.core.reactor.exception.MyNettyException;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipeline;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipelineSupplier;
import com.my.netty.core.reactor.util.AssertUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public abstract class MyNioChannel {

    private static final Logger logger = LoggerFactory.getLogger(MyNioChannel.class);

    private Selector selector;

    private SelectionKey selectionKey;

    private SelectableChannel javaChannel;

    private MyNioEventLoop myNioEventLoop;

    private MyChannelPipeline channelPipeline;

    public MyNioChannel(Selector selector,
                        SelectableChannel javaChannel,
                        MyChannelPipelineSupplier channelPipelineSupplier) {

        this.selector = selector;
        this.javaChannel = javaChannel;
        this.channelPipeline = channelPipelineSupplier.buildMyChannelPipeline(this);

        AssertUtil.notNull(this.channelPipeline,"channelPipeline is null");

        try {
            // nio，非阻塞
            javaChannel.configureBlocking(false);
        } catch (IOException e) {
            try {
                javaChannel.close();
            } catch (IOException e2) {
                logger.warn("Failed to close a partially initialized socket.", e2);
            }

            throw new MyNettyException("Failed to enter non-blocking mode.", e);
        }
    }

    public Selector getSelector() {
        return selector;
    }

    public void setSelector(Selector selector) {
        this.selector = selector;
    }

    public SelectableChannel getJavaChannel() {
        return javaChannel;
    }

    public void setJavaChannel(SelectableChannel javaChannel) {
        this.javaChannel = javaChannel;
    }

    public MyNioEventLoop getMyNioEventLoop() {
        return myNioEventLoop;
    }

    public void setMyNioEventLoop(MyNioEventLoop myNioEventLoop) {
        this.myNioEventLoop = myNioEventLoop;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    public MyChannelPipeline getChannelPipeline() {
        return channelPipeline;
    }

    public void setChannelPipeline(MyChannelPipeline channelPipeline) {
        this.channelPipeline = channelPipeline;
    }
}
