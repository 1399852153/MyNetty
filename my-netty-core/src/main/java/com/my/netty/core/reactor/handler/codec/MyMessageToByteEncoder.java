package com.my.netty.core.reactor.handler.codec;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import com.my.netty.core.reactor.internal.TypeParameterMatcher;
import com.my.netty.core.reactor.util.MyReferenceCountUtil;

import java.util.concurrent.CompletableFuture;

public abstract class MyMessageToByteEncoder<I> extends MyChannelEventHandlerAdapter {

    private final TypeParameterMatcher matcher;

    public MyMessageToByteEncoder(Class<? extends I> clazz) {
        this.matcher = TypeParameterMatcher.get(clazz);
    }

    @Override
    public void write(MyChannelHandlerContext ctx, Object msg, boolean doFlush, CompletableFuture<MyNioChannel> completableFuture) throws Exception {
        MyByteBuf buf = null;
        try {
            // 判断当前msg的类型和当前Encoder是否匹配
            if (acceptOutboundMessage(msg)) {
                // 类型匹配，说明该msg需要由当前Encoder来编码，将msg转化成ByteBuf用于输出
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                // 先分配一个ByteBuf出来
                buf = ctx.alloc().heapBuffer();
                try {
                    // 由子类实现的自定义逻辑进行编码，将msg写入到buf中
                    encode(ctx, cast, buf);
                } finally {
                    // 编码完成，尝试将当前被编码完成的消息释放掉
                    MyReferenceCountUtil.release(cast);
                }

                // 将编码后的buf传到后续的outBoundHandler中(比起netty，少了一个空buf的优化逻辑)
                ctx.write(buf, doFlush, completableFuture);

                buf = null;
            } else {
                // 不匹配，跳过当前的outBoundHandler，直接交给后续的handler处理
                ctx.write(msg, doFlush, completableFuture);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            if (buf != null) {
                // buf不为null，说明编码逻辑有异常，提前release掉
                buf.release();
            }
        }
    }

    protected abstract void encode(MyChannelHandlerContext ctx, I msg, MyByteBuf out) throws Exception;

    private boolean acceptOutboundMessage(Object msg) {
        return matcher.match(msg);
    }
}
