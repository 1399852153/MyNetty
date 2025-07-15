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

        myChannelOutboundBuffer.removeBytes(20);

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

        myChannelOutboundBuffer.removeBytes(18);

        Assert.assertFalse(myChannelOutboundBuffer.isEmpty());
    }
}
