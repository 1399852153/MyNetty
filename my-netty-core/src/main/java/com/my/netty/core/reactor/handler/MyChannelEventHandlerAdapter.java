package com.my.netty.core.reactor.handler;


import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.handler.annotation.Sharable;
import com.my.netty.core.reactor.handler.annotation.Skip;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于简化用户自定义的handler的适配器
 *
 * 由于所有支持的方法都加上了@Skip注解，子类只需要重写想要关注的方法即可，其它未重写的方法将会在事件传播时被跳过
 * */
public class MyChannelEventHandlerAdapter implements MyChannelEventHandler{

    /**
     * 当前是否已经被加入sharable缓存
     * */
    public volatile boolean added;

    @Skip
    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.fireChannelRead(msg);
    }

    @Skip
    @Override
    public void channelReadComplete(MyChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadComplete();
    }

    @Skip
    @Override
    public void exceptionCaught(MyChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }

    @Skip
    @Override
    public void close(MyChannelHandlerContext ctx) throws Exception {
        ctx.close();
    }

    @Skip
    @Override
    public void write(MyChannelHandlerContext ctx, Object msg, boolean doFlush, CompletableFuture<MyNioChannel> completableFuture) throws Exception {
        ctx.write(msg,doFlush,completableFuture);
    }

    private static ConcurrentHashMap<Class<?>, Boolean> isSharableCacheMap = new ConcurrentHashMap<>();

    /**
     * Throws {@link IllegalStateException} if {@link MyChannelEventHandlerAdapter#isSharable()} returns {@code true}
     */
    protected void ensureNotSharable() {
        if (isSharable()) {
            throw new IllegalStateException("ChannelHandler " + getClass().getName() + " is not allowed to be shared");
        }
    }

    public boolean isSharable() {
        /**
         * MyNetty中直接用全局的ConcurrentHashMap来缓存handler类是否是sharable可共享的，实现起来很简单
         * 而netty中利用FastThreadLocal做了优化，避免了不同线程之间的锁争抢
         * 高并发下每分每秒都会创建大量的链接以及所属的Handler，优化后性能会有很大提升
         *
         * See <a href="https://github.com/netty/netty/issues/2289">#2289</a>.
         */
        Class<?> clazz = getClass();
        Boolean sharable = isSharableCacheMap.computeIfAbsent(
                clazz, k -> clazz.isAnnotationPresent(Sharable.class));
        return sharable;
    }
}
