package com.my.netty.core.reactor.buffer;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.bytebuffer.netty.allocator.MyByteBufAllocator;
import com.my.netty.core.reactor.channel.buffer.MyChannelOutboundBuffer;
import com.my.netty.core.reactor.config.DefaultChannelConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

public class MyChannelOutboundBufferTest {

    @Test
    public void testFLushComplete() {
        MyChannelOutboundBuffer myChannelOutboundBuffer = new MyChannelOutboundBuffer(null);

        DefaultChannelConfig defaultChannelConfig = new DefaultChannelConfig();
        MyByteBufAllocator allocator =  defaultChannelConfig.getAllocator();
        MyByteBuf byteBuffer = buildReadableByteBuf(allocator,2);
        myChannelOutboundBuffer.addMessage(byteBuffer,byteBuffer.readableBytes(),new CompletableFuture<>());

        MyByteBuf byteBuffer2 = buildReadableByteBuf(allocator,4);
        myChannelOutboundBuffer.addMessage(byteBuffer2,byteBuffer2.readableBytes(),new CompletableFuture<>());

        MyByteBuf byteBuffer3 = buildReadableByteBuf(allocator,14);
        myChannelOutboundBuffer.addMessage(byteBuffer3,byteBuffer3.readableBytes(),new CompletableFuture<>());

        myChannelOutboundBuffer.addFlush();

        myChannelOutboundBuffer.removeBytes(2+4+14);

        Assert.assertTrue(myChannelOutboundBuffer.isEmpty());
    }

    @Test
    public void testFLushUnComplete() {
        MyChannelOutboundBuffer myChannelOutboundBuffer = new MyChannelOutboundBuffer(null);

        DefaultChannelConfig defaultChannelConfig = new DefaultChannelConfig();
        MyByteBufAllocator allocator =  defaultChannelConfig.getAllocator();
        MyByteBuf byteBuffer = buildReadableByteBuf(allocator,2);
        myChannelOutboundBuffer.addMessage(byteBuffer,byteBuffer.readableBytes(),new CompletableFuture<>());

        MyByteBuf byteBuffer2 = buildReadableByteBuf(allocator,4);
        myChannelOutboundBuffer.addMessage(byteBuffer2,byteBuffer2.readableBytes(),new CompletableFuture<>());

        MyByteBuf byteBuffer3 = buildReadableByteBuf(allocator,14);
        myChannelOutboundBuffer.addMessage(byteBuffer3,byteBuffer3.readableBytes(),new CompletableFuture<>());

        myChannelOutboundBuffer.addFlush();

        myChannelOutboundBuffer.removeBytes((2+4+14) - 2);

        Assert.assertFalse(myChannelOutboundBuffer.isEmpty());
    }

    private static MyByteBuf buildReadableByteBuf(MyByteBufAllocator allocator, int writeSize){
        MyByteBuf byteBuffer = allocator.heapBuffer(2);

        for(int i=0; i<writeSize; i++){
            byteBuffer.writeByte('c');
        }

        return byteBuffer;
    }
}
