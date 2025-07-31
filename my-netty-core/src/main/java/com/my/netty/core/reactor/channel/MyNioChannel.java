package com.my.netty.core.reactor.channel;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.core.reactor.channel.buffer.MyChannelOutboundBuffer;
import com.my.netty.core.reactor.config.DefaultChannelConfig;
import com.my.netty.core.reactor.eventloop.MyNioEventLoop;
import com.my.netty.core.reactor.exception.MyNettyException;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipeline;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipelineSupplier;
import com.my.netty.core.reactor.util.AssertUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.CompletableFuture;

public abstract class MyNioChannel {

    private static final Logger logger = LoggerFactory.getLogger(MyNioChannel.class);

    private Selector selector;

    private SelectionKey selectionKey;

    private SelectableChannel javaChannel;

    private MyNioEventLoop myNioEventLoop;

    private MyChannelPipeline channelPipeline;

    private MyChannelOutboundBuffer myChannelOutboundBuffer;

    protected DefaultChannelConfig defaultChannelConfig;

    public MyNioChannel(Selector selector,
                        SelectableChannel javaChannel,
                        MyChannelPipelineSupplier channelPipelineSupplier,
                        DefaultChannelConfig defaultChannelConfig) {

        this.selector = selector;
        this.javaChannel = javaChannel;
        this.channelPipeline = channelPipelineSupplier.buildMyChannelPipeline(this);
        this.myChannelOutboundBuffer = new MyChannelOutboundBuffer(this);
        this.defaultChannelConfig = defaultChannelConfig;

        AssertUtil.notNull(this.channelPipeline,"channelPipeline is null");
        AssertUtil.notNull(this.defaultChannelConfig,"defaultChannelConfig is null");

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

    public void doWrite(Object msg, boolean doFlush, CompletableFuture<MyNioChannel> completableFuture) throws IOException {
        if(!(msg instanceof MyByteBuf)){
            // 约定好，msg走到head节点的时候，只支持MyByteBuf类型
            throw new Error();
        }

        MyByteBuf byteBufferMsg = (MyByteBuf)msg;

        MyChannelOutboundBuffer myChannelOutboundBuffer = this.myChannelOutboundBuffer;
        // netty在存入outBoundBuffer时使用的是堆外内存缓冲，避免积压过多的数据造成堆内存移除
        // 这里简单起见先不考虑这方面的性能优化，重点关注ChannelOutboundBuffer本身的功能实现
        myChannelOutboundBuffer.addMessage(byteBufferMsg,byteBufferMsg.readableBytes(),completableFuture);

        if(doFlush){
            myChannelOutboundBuffer.addFlush();

            // 进行实际的写出操作
            flush0();
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

    public void flush0(){
        if(myChannelOutboundBuffer.isEmpty()){
            // 没有需要flush的消息，直接返回
            return;
        }

        // netty针对当前channel的状态做了很多判断(isActive、isOpen)，避免往一个不可用的channel里写入数据，简单起见先不考虑这些场景

        try {
            doWrite(myChannelOutboundBuffer);
        }catch (Exception e){
            logger.error("flush0 doWrite error! close channel={}",this,e);

            // 写出时有异常时，关闭channel
            this.channelPipeline.close();
        }
    }

    public boolean isWritable() {
        MyChannelOutboundBuffer buf = this.myChannelOutboundBuffer;
        return buf != null && buf.isWritable();
    }

    public DefaultChannelConfig config() {
        return defaultChannelConfig;
    }

    protected abstract void doWrite(MyChannelOutboundBuffer channelOutboundBuffer) throws Exception;

    protected final void setOpWrite() {
        final SelectionKey key = selectionKey;
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (!key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(interestOps | SelectionKey.OP_WRITE);
        }
    }


    protected final void clearOpWrite() {
        final SelectionKey key = selectionKey;
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (!key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) != 0) {
            // 去掉对于op_write事件的监听
            key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
        }
    }

    protected final void incompleteWrite(boolean setOpWrite) {
        // Did not write completely.
        if (setOpWrite) {
            setOpWrite();
        } else {
            // It is possible that we have set the write OP, woken up by NIO because the socket is writable, and then
            // use our write quantum. In this case we no longer want to set the write OP because the socket is still
            // writable (as far as we know). We will find out next time we attempt to write if the socket is writable
            // and set the write OP if necessary.
            clearOpWrite();

            // Schedule flush again later so other tasks can be picked up in the meantime
            // Calling flush0 directly to ensure we not try to flush messages that were added via write(...) in the
            // meantime.
            myNioEventLoop.execute(this::flush0);
        }
    }
}
