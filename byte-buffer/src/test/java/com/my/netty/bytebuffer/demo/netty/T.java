package com.my.netty.bytebuffer.demo.netty;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.bytebuffer.netty.allocator.MyByteBufAllocator;
import com.my.netty.bytebuffer.netty.allocator.MyUnpooledByteBufAllocator;

public class T {

    public static void main(String[] args) {
        MyByteBufAllocator myByteBufAllocator = new MyUnpooledByteBufAllocator();
        // 1. 创建ByteBuf
        MyByteBuf heapBuffer = myByteBufAllocator.heapBuffer(16);

        // 2. 写入数据
        heapBuffer.writeBytes("Hello world!".getBytes());

        MyByteBuf frame = myByteBufAllocator.heapBuffer(8);
        heapBuffer.getBytes(2,frame,0,8);

        System.out.println(frame.readerIndex());
    }
}
