package com.my.netty.core.reactor.channel;


import com.my.netty.core.reactor.channel.buffer.MyChannelOutboundBuffer;
import com.my.netty.core.reactor.config.DefaultChannelConfig;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipelineSupplier;
import com.my.netty.core.reactor.limiter.ReceivedMessageBytesLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.List;

public class MyNioSocketChannel extends MyNioChannel{
    private static final Logger logger = LoggerFactory.getLogger(MyNioSocketChannel.class);

    private final SocketChannel socketChannel;
    private final ReceivedMessageBytesLimiter receivedMessageBytesLimiter;
    private final DefaultChannelConfig defaultChannelConfig;

    /**
     * 一次聚合写出的最大字节数
     * */
    private int maxBytesPerGatheringWrite = 1024 * 1024 * 1024;

    public static final int MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD = 4096;

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

    public DefaultChannelConfig getDefaultChannelConfig() {
        return defaultChannelConfig;
    }

    public void read(SelectionKey key) throws IOException {
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
                // 取消key的监听
                key.cancel();

                break;
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
    protected void doWrite(MyChannelOutboundBuffer myChannelOutboundBuffer) throws Exception {
        // 默认一次写出16次
//        int writeSpinCount = 16;

        int writeSpinCount = 2;  // 方便测试

        do {
            if (myChannelOutboundBuffer.isEmpty()) {
                // 当前积压的待flush消息已经写完了，清理掉注册的write监听

                // All written so clear OP_WRITE
                clearOpWrite();
                // Directly return here so incompleteWrite(...) is not called.
                return;
            }

            // 计算出当前这一次写出的bytebuffer的数量
            List<ByteBuffer> needWriteByteBufferList = myChannelOutboundBuffer.nioByteBuffers(1024,maxBytesPerGatheringWrite);
            // 相比netty里用数组，List会多一次数组copy，但这样简单一点，不用考虑缓存or动态扩容的问题，暂不优化
            ByteBuffer[] byteBuffers = needWriteByteBufferList.toArray(new ByteBuffer[0]);
            SocketChannel socketChannel = this.getSocketChannel();

            // 调用jdk channel的write方法一次性写入byteBuffer集合
            final long localWrittenBytes = socketChannel.write(byteBuffers,0, needWriteByteBufferList.size());
            logger.info("localWrittenBytes={},attemptedBytes={},needWriteByteBufferList.size={}"
                , localWrittenBytes, myChannelOutboundBuffer.getNioBufferSize(),needWriteByteBufferList.size());
            if (localWrittenBytes <= 0) {
                // 返回值localWrittenBytes小于等于0，说明当前Socket缓冲区写满了，不能再写入了。注册一个OP_WRITE事件(setOpWrite=true)，
                // 当channel所在的NIO循环中监听到当前channel的OP_WRITE事件时，就说明缓冲区又可写了，在对应逻辑里继续执行写入操作
                incompleteWrite(true);
                // 既然写不下了就直接返回，不需要继续尝试了
                return;
            }

            long attemptedBytes = myChannelOutboundBuffer.getNioBufferSize();
            // 基于本次写出的情况，动态的调整一次写出的最大字节数maxBytesPerGatheringWrite
            adjustMaxBytesPerGatheringWrite((int) attemptedBytes, (int) localWrittenBytes, maxBytesPerGatheringWrite);
            // 按照实际写出的字节数进行计算，将写出完毕的ByteBuffer从channelOutboundBuffer中移除掉
            myChannelOutboundBuffer.removeBytes(localWrittenBytes);

            // 每次写入一次消息，writeSpinCount自减
            writeSpinCount--;
        } while (writeSpinCount > 0);

        // 自然的退出了循环，说明已经正确的写完了writeSpinCount指定条数的消息，但channelOutboundBuffer还不为空(如果写完了会提前return)
        // incompleteWrite内部提交一个flush0的任务，等待到下一次事件循环中再捞出来处理，保证不同channel间读写的公平性
        incompleteWrite(false);
    }

    private void adjustMaxBytesPerGatheringWrite(int attempted, int written, int oldMaxBytesPerGatheringWrite) {
        // By default we track the SO_SNDBUF when ever it is explicitly set. However some OSes may dynamically change
        // SO_SNDBUF (and other characteristics that determine how much data can be written at once) so we should try
        // make a best effort to adjust as OS behavior changes.

        // 默认情况下，我们会追踪明确设置SO_SNDBUF的地方。(setSendBufferSize等)
        // 然而，一些操作系统可能会动态更改SO_SNDBUF（以及其他决定一次可以写入多少数据的特性），
        // 因此我们应该尽力根据操作系统的行为变化进行调整。

        if (attempted == written) {
            // 本次操作写出的数据能够完全写出，说明操作系统当前还有余力
            if (attempted << 1 > oldMaxBytesPerGatheringWrite) { // 左移1位，大于的判断可以保证maxBytesPerGatheringWrite不会溢出为负数
                // 进一步判断，发现实际写出的数据比指定的maxBytesPerGatheringWrite要大一倍以上
                // 则扩大maxBytesPerGatheringWrite的值，在后续尽可能多的写出数据
                // 通常在maxBytesPerGatheringWrite较小，而某一个消息很大的场景下会出现(nioBuffers方法)
                this.maxBytesPerGatheringWrite = attempted << 1;
            }
        } else if (attempted > MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD && written < attempted >>> 1) {
            // 如果因为操作系统底层缓冲区不够的原因导致实际写出的数据量(written)比需要写出的数据量(attempted)低了一倍以上,可能是比较拥塞或者其它原因(配置或动态变化)
            // 将一次写出的最大字节数缩小为原来的一半，下次尝试少发送一些消息，以提高性能
            this.maxBytesPerGatheringWrite = attempted >>> 1;
        }
    }

    @Override
    public String toString() {
        return "MyNioSocketChannel{" +
            "socketChannel=" + socketChannel +
            "} " + super.toString();
    }
}
