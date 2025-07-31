package com.my.netty.core.reactor.codec;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import com.my.netty.core.reactor.util.MyReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class EchoMessageDecoder extends MyChannelEventHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EchoMessageDecoder.class);

    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) throws Exception {
        // 读事件从head向tail传播，最原始的msg是byteBuf类型
        MyByteBuf myByteBuf = (MyByteBuf) msg;

        String receivedStr = myByteBuf.toString(StandardCharsets.UTF_8);

        logger.info("EchoMessageDecoder byteBuffer to message, " +
            "msg={}, receivedStr={}",msg,receivedStr);

        // 转换完成，将读取到ByteBuf给释放掉
        MyReferenceCountUtil.safeRelease(myByteBuf);

        // 当前版本，不考虑黏包拆包等各种问题，decoder只负责将byte转为string
        ctx.fireChannelRead(receivedStr);
    }
}
