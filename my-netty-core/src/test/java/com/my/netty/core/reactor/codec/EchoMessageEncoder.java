package com.my.netty.core.reactor.codec;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class EchoMessageEncoder extends MyChannelEventHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EchoMessageEncoder.class);

    @Override
    public void write(MyChannelHandlerContext ctx, Object msg, boolean doFlush, CompletableFuture<MyNioChannel> completableFuture) {
        // 写事件从tail向head传播，msg一定是string类型
        String message = (String) msg;

        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        MyByteBuf myByteBuf = ctx.alloc().heapBuffer(bytes.length);
        myByteBuf.writeBytes(bytes);

        logger.info("EchoMessageEncoder message to byteBuffer, " +
            "message={}, myByteBuf={}",message.length(),myByteBuf);

        ctx.write(myByteBuf,doFlush,completableFuture);
    }
}
