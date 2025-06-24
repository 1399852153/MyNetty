package com.my.netty.core.reactor.codec;

import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class EchoMessageEncoder extends MyChannelEventHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EchoMessageEncoder.class);

    @Override
    public void write(MyChannelHandlerContext ctx, Object msg) throws Exception {
        // 写事件从tail向head传播，msg一定是string类型
        String message = (String) msg;

        ByteBuffer writeBuffer = ByteBuffer.allocateDirect(1024);
        writeBuffer.put(message.getBytes(StandardCharsets.UTF_8));
        writeBuffer.flip();

        logger.info("EchoMessageEncoder message to byteBuffer, " +
            "message={}, writeBuffer={}",message,writeBuffer);

        ctx.write(writeBuffer);
    }
}
