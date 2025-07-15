package com.my.netty.core.reactor.buffer;

import com.my.netty.core.reactor.channel.buffer.MyChannelOutboundBuffer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class MyChannelOutboundBufferTest {

    @Test
    public void testFLushComplete() {
        MyChannelOutboundBuffer myChannelOutboundBuffer = new MyChannelOutboundBuffer(null);

        ByteBuffer byteBuffer = ByteBuffer.allocate(2);
        myChannelOutboundBuffer.addMessage(byteBuffer,byteBuffer.remaining(),new CompletableFuture<>());

        ByteBuffer byteBuffer2 = ByteBuffer.allocate(4);
        myChannelOutboundBuffer.addMessage(byteBuffer2,byteBuffer2.remaining(),new CompletableFuture<>());

        ByteBuffer byteBuffer3 = ByteBuffer.allocate(14);
        myChannelOutboundBuffer.addMessage(byteBuffer3,byteBuffer3.remaining(),new CompletableFuture<>());

        myChannelOutboundBuffer.addFlush();

        // 模拟buffer完全的写出
        byteBuffer.get(new byte[byteBuffer.remaining()]);
        Assert.assertFalse(byteBuffer.hasRemaining());

        byteBuffer2.get(new byte[byteBuffer2.remaining()]);
        Assert.assertFalse(byteBuffer2.hasRemaining());

        byteBuffer3.get(new byte[byteBuffer3.remaining()]);
        Assert.assertFalse(byteBuffer3.hasRemaining());

        myChannelOutboundBuffer.removeBytes(2+4+14);

        Assert.assertTrue(myChannelOutboundBuffer.isEmpty());
    }

    @Test
    public void testFLushUnComplete() {
        MyChannelOutboundBuffer myChannelOutboundBuffer = new MyChannelOutboundBuffer(null);

        ByteBuffer byteBuffer = ByteBuffer.allocate(2);
        myChannelOutboundBuffer.addMessage(byteBuffer,byteBuffer.remaining(),new CompletableFuture<>());

        ByteBuffer byteBuffer2 = ByteBuffer.allocate(4);
        myChannelOutboundBuffer.addMessage(byteBuffer2,byteBuffer2.remaining(),new CompletableFuture<>());

        ByteBuffer byteBuffer3 = ByteBuffer.allocate(14);
        myChannelOutboundBuffer.addMessage(byteBuffer3,byteBuffer3.remaining(),new CompletableFuture<>());

        myChannelOutboundBuffer.addFlush();

        // 模拟buffer写出
        byteBuffer.get(new byte[byteBuffer.remaining()]);
        Assert.assertFalse(byteBuffer.hasRemaining());

        byteBuffer2.get(new byte[byteBuffer2.remaining()]);
        Assert.assertFalse(byteBuffer2.hasRemaining());

        // 模拟byteBuffer3留了一点没写出去
        byteBuffer3.get(new byte[byteBuffer3.remaining()-2]);
        Assert.assertTrue(byteBuffer3.hasRemaining());

        myChannelOutboundBuffer.removeBytes((2+4+14) - 2);

        Assert.assertFalse(myChannelOutboundBuffer.isEmpty());
    }
}
