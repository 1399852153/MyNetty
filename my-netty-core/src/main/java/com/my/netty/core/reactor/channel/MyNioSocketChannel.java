package com.my.netty.core.reactor.channel;


import com.my.netty.core.reactor.config.DefaultChannelConfig;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipelineSupplier;
import com.my.netty.core.reactor.limiter.ReceivedMessageBytesLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class MyNioSocketChannel extends MyNioChannel{
    private static final Logger logger = LoggerFactory.getLogger(MyNioSocketChannel.class);

    private final SocketChannel socketChannel;
    private final ReceivedMessageBytesLimiter receivedMessageBytesLimiter;
    private final DefaultChannelConfig defaultChannelConfig;

    public MyNioSocketChannel(
        Selector selector, SocketChannel socketChannel, MyChannelPipelineSupplier myChannelPipelineSupplier,
        DefaultChannelConfig defaultChannelConfig) {
        super(selector,socketChannel,myChannelPipelineSupplier);

        this.socketChannel = socketChannel;

        this.defaultChannelConfig = defaultChannelConfig;

        this.receivedMessageBytesLimiter = new ReceivedMessageBytesLimiter(defaultChannelConfig.getInitialReceiveBufferSize());
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void read() throws IOException {
        // receivedMessageLimiter相当于netty中简化版的不包含buffer类型分配的RecvByteBufAllocator(参考自AdaptiveRecvByteBufAllocator)
        // 新的一次read事件开始前，刷新下readLimiter的状态
        receivedMessageBytesLimiter.reset();
        do {
            int receiveBufferSize = receivedMessageBytesLimiter.getReceiveBufferSize();
            // 简单起见，buffer不缓存，每次读事件来都新创建一个
            // 暂时也不考虑黏包/拆包场景(Netty中靠ByteToMessageDecoder解决，后续再分析其原理)
            ByteBuffer readBuffer = ByteBuffer.allocate(receiveBufferSize);

            int byteRead = socketChannel.read(readBuffer);
            logger.info("processReadEvent byteRead={}", byteRead);

            // 记录下最近一次读取的字节数
            receivedMessageBytesLimiter.recordLastBytesRead(byteRead);
            if (byteRead < 0) {
                // 简单起见不考虑tcp半连接的情况，返回-1直接关掉连接
                socketChannel.close();
            } else if(byteRead == 0){
                // 当前读事件已经读取完毕了，退出循环
                break;
            } else {
                // 总消息读取次数+1
                receivedMessageBytesLimiter.incMessagesRead();

                // 将缓冲区当前的limit设置为position=0，用于后续对缓冲区的读取操作
                readBuffer.flip();
                // 根据缓冲区可读字节数创建字节数组
                byte[] bytes = new byte[readBuffer.remaining()];
                // 将缓冲区可读字节数组复制到新建的数组中
                readBuffer.get(bytes);

                // 触发pipeline的读取操作
                this.getChannelPipeline().fireChannelRead(bytes);
            }
        }while (receivedMessageBytesLimiter.canContinueReading());

        // 整理一下此次read事件读取的字节数，调整下一次read事件分配的bufferSize大小
        receivedMessageBytesLimiter.readComplete();

        // 一次read事件读取完成，触发一次readComplete的inbound事件
        this.getChannelPipeline().fireChannelReadComplete();
    }

    @Override
    public String toString() {
        return "MyNioSocketChannel{" +
            "socketChannel=" + socketChannel +
            "} " + super.toString();
    }
}
