package com.my.netty.core.reactor.codec.v2;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.core.reactor.handler.codec.MyMessageToByteEncoder;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import com.my.netty.core.reactor.model.EchoMessage;
import com.my.netty.core.reactor.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class EchoMessageEncoderV2 extends MyMessageToByteEncoder<EchoMessage> {

    private static final Logger logger = LoggerFactory.getLogger(EchoMessageEncoderV2.class);

    public EchoMessageEncoderV2() {
        super(EchoMessage.class);
    }

    @Override
    protected void encode(MyChannelHandlerContext ctx, EchoMessage msg, MyByteBuf out) {
        // 写事件从tail向head传播，msg一定是EchoMessage类型

        String messageJson = JsonUtil.obj2Str(msg);
        byte[] bytes = messageJson.getBytes(StandardCharsets.UTF_8);

        // 写入魔数，确保协议是匹配的
        out.writeInt(EchoMessage.MAGIC);
        // LengthFieldBased协议，先写入消息帧的长度
        out.writeInt(bytes.length);
        // 再写入消息体
        out.writeBytes(bytes);

        logger.info("EchoMessageEncoder message to byteBuffer, " +
            "messageJson.length={}, myByteBuf={}",messageJson.length(),out);
    }
}
