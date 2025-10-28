package com.my.netty.core.reactor.codec.v2;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import com.my.netty.core.reactor.model.EchoMessageFrame;
import com.my.netty.core.reactor.util.JsonUtil;
import com.my.netty.core.reactor.util.MyReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class EchoMessageDecoderV2 extends MyChannelEventHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EchoMessageDecoderV2.class);

    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) {
        // 读事件从head向tail传播，经过解码后，拿到的是解决黏包、拆包问题后完整的Frame
        MyByteBuf myByteBuf = (MyByteBuf) msg;

        int magicNum = myByteBuf.readInt();
        if(magicNum != EchoMessageFrame.MAGIC){
            logger.error("magicNum not match!");
            return;
        }

        int length = myByteBuf.readInt();

        // Frame的内容是一个完整的json字符串
        String receivedStr = myByteBuf.toString(StandardCharsets.UTF_8);
        if(receivedStr.length() != length){
            logger.error("receivedStr.length != length");
            return;
        }

        logger.info("EchoMessageDecoder byteBuffer to message, msg={}, receivedStr={}",msg,receivedStr);

        // 转换完成，将读取到ByteBuf给释放掉
        MyReferenceCountUtil.safeRelease(myByteBuf);

        // 转化为EchoMessageFrame给业务处理器
        EchoMessageFrame echoMessageFrame = JsonUtil.json2Obj(receivedStr, EchoMessageFrame.class);
        ctx.fireChannelRead(echoMessageFrame);
    }
}
