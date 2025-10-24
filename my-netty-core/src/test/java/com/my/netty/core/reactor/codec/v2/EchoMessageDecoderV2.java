package com.my.netty.core.reactor.codec.v2;

import com.my.netty.core.reactor.handler.codec.MyLengthFieldBasedFrameDecoder;

public class EchoMessageDecoderV2 extends MyLengthFieldBasedFrameDecoder {
    public EchoMessageDecoderV2() {
        // 协议头一个magic魔数(int类型，占4字节，offset=4)，一个长度字段(int类型，占四字节)
        super(1024 * 1024, 4, 4);
    }


}
