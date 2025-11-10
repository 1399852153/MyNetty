package com.my.netty.core.reactor.handler.context;

import com.my.netty.bytebuffer.netty.allocator.MyByteBufAllocator;
import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.eventloop.MyNioEventLoop;
import com.my.netty.core.reactor.handler.MyChannelEventHandler;
import com.my.netty.core.reactor.handler.mask.MyChannelHandlerMaskManager;
import com.my.netty.core.reactor.handler.pipeline.MyChannelPipeline;
import com.my.netty.core.reactor.util.ThrowableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public abstract class MyAbstractChannelHandlerContext implements MyChannelHandlerContext{

    private static final Logger logger = LoggerFactory.getLogger(MyAbstractChannelHandlerContext.class);

    private final MyChannelPipeline pipeline;

    private final int executionMask;

    /**
     * 双向链表前驱/后继节点
     * */
    private MyAbstractChannelHandlerContext prev;
    private MyAbstractChannelHandlerContext next;

    public MyAbstractChannelHandlerContext(MyChannelPipeline pipeline, Class<? extends MyChannelEventHandler> handlerClass) {
        this.pipeline = pipeline;

        this.executionMask = MyChannelHandlerMaskManager.mask(handlerClass);
    }

    @Override
    public MyNioChannel channel() {
        return pipeline.getChannel();
    }

    public MyAbstractChannelHandlerContext getPrev() {
        return prev;
    }

    public void setPrev(MyAbstractChannelHandlerContext prev) {
        this.prev = prev;
    }

    public MyAbstractChannelHandlerContext getNext() {
        return next;
    }

    public void setNext(MyAbstractChannelHandlerContext next) {
        this.next = next;
    }

    @Override
    public MyNioEventLoop executor() {
        return this.pipeline.getChannel().getMyNioEventLoop();
    }

    @Override
    public MyByteBufAllocator alloc() {
        return channel().config().getAllocator();
    }

    @Override
    public void fireChannelRead(Object msg) {
        // 找到当前链条下最近的一个支持channelRead方法的MyAbstractChannelHandlerContext（inbound事件，从前往后找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextInbound(MyChannelHandlerMaskManager.MASK_CHANNEL_READ);

        // 调用找到的那个ChannelHandlerContext其handler的channelRead方法
        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            invokeChannelRead(nextHandlerContext,msg);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                invokeChannelRead(nextHandlerContext,msg);
            });
        }
    }

    @Override
    public void fireExceptionCaught(Throwable cause) {
        // 找到当前链条下最近的一个支持exceptionCaught方法的MyAbstractChannelHandlerContext（inbound事件，从前往后找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextInbound(MyChannelHandlerMaskManager.MASK_EXCEPTION_CAUGHT);

        // 调用找到的那个ChannelHandlerContext其handler的exceptionCaught方法

        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            invokeExceptionCaught(nextHandlerContext,cause);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                invokeExceptionCaught(nextHandlerContext,cause);
            });
        }
    }

    @Override
    public void fireChannelReadComplete() {
        // 找到当前链条下最近的一个支持channelReadComplete方法的MyAbstractChannelHandlerContext（inbound事件，从前往后找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextInbound(MyChannelHandlerMaskManager.MASK_CHANNEL_READ_COMPLETE);

        // 调用找到的那个ChannelHandlerContext其handler的channelReadComplete方法

        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            invokeChannelReadComplete(nextHandlerContext);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                invokeChannelReadComplete(nextHandlerContext);
            });
        }
    }

    @Override
    public void close() {
        // 找到当前链条下最近的一个支持close方法的MyAbstractChannelHandlerContext（outbound事件，从后往前找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextOutbound(MyChannelHandlerMaskManager.MASK_CLOSE);

        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            doClose(nextHandlerContext);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                doClose(nextHandlerContext);
            });
        }
    }

    private void doClose(MyAbstractChannelHandlerContext nextHandlerContext){
        try {
            nextHandlerContext.handler().close(nextHandlerContext);
        } catch (Throwable t) {
            logger.error("{} do close error!",nextHandlerContext,t);
        }
    }

    @Override
    public CompletableFuture<MyNioChannel> write(Object msg, boolean doFlush) {
        CompletableFuture<MyNioChannel> completableFuture = new CompletableFuture<>();

        return write(msg,doFlush,completableFuture);
    }

    @Override
    public CompletableFuture<MyNioChannel> write(Object msg, boolean doFlush, CompletableFuture<MyNioChannel> completableFuture) {
        // 找到当前链条下最近的一个支持write方法的MyAbstractChannelHandlerContext（outbound事件，从后往前找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextOutbound(MyChannelHandlerMaskManager.MASK_WRITE);

        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            doWrite(nextHandlerContext,msg,doFlush,completableFuture);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                doWrite(nextHandlerContext,msg,doFlush,completableFuture);
            });
        }

        return completableFuture;
    }

    private void doWrite(MyAbstractChannelHandlerContext nextHandlerContext, Object msg, boolean doFlush, CompletableFuture<MyNioChannel> completableFuture) {
        try {
            nextHandlerContext.handler().write(nextHandlerContext,msg,doFlush,completableFuture);
        } catch (Throwable t) {
            logger.error("{} do write error!",nextHandlerContext,t);

            completableFuture.completeExceptionally(t);
        }
    }

    @Override
    public MyChannelPipeline getPipeline() {
        return pipeline;
    }

    public static void invokeChannelRead(MyAbstractChannelHandlerContext next, Object msg) {
        try {
            next.handler().channelRead(next, msg);
        }catch (Throwable t){
            // 处理抛出的异常
            next.invokeExceptionCaught(t);
        }
    }

    public static void invokeChannelReadComplete(MyAbstractChannelHandlerContext next) {
        try {
            next.handler().channelReadComplete(next);
        }catch (Throwable t){
            // 处理抛出的异常
            next.invokeExceptionCaught(t);
        }
    }

    public static void invokeExceptionCaught(MyAbstractChannelHandlerContext next, Throwable cause) {
        next.invokeExceptionCaught(cause);
    }

    private void invokeExceptionCaught(final Throwable cause) {
        try {
            this.handler().exceptionCaught(this, cause);
        } catch (Throwable error) {
            // 如果捕获异常的handler依然抛出了异常，则打印debug日志
            logger.error(
                "An exception {}" +
                    "was thrown by a user handler's exceptionCaught() " +
                    "method while handling the following exception:",
                ThrowableUtil.stackTraceToString(error), cause);
        }
    }

    private MyAbstractChannelHandlerContext findContextInbound(int mask) {
        MyAbstractChannelHandlerContext ctx = this;
        do {
            // inbound事件，从前往后找
            ctx = ctx.next;
        } while (needSkipContext(ctx, mask));

        return ctx;
    }

    private MyAbstractChannelHandlerContext findContextOutbound(int mask) {
        MyAbstractChannelHandlerContext ctx = this;
        do {
            // outbound事件，从后往前找
            ctx = ctx.prev;
        } while (needSkipContext(ctx, mask));

        return ctx;
    }

    private static boolean needSkipContext(MyAbstractChannelHandlerContext ctx, int mask) {
        // 如果与运算后为0，说明不支持对应掩码的操作，需要跳过
        return (ctx.executionMask & (mask)) == 0;
    }
}
