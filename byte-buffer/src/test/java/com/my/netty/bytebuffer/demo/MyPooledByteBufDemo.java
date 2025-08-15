package com.my.netty.bytebuffer.demo;


import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.bytebuffer.netty.allocator.MyPooledByteBufAllocator;

public class MyPooledByteBufDemo {

    public static void main(String[] args) {
        MyPooledByteBufAllocator myPooledByteBufAllocator = new MyPooledByteBufAllocator();

        MyByteBuf myByteBuf1 = myPooledByteBufAllocator.heapBuffer(8192,8192 * 10);
        System.out.println("myByteBuf1 writableBytes=" + myByteBuf1.writableBytes());
        myByteBuf1.writeBoolean(true);
        myByteBuf1.writeBoolean(true);
        System.out.println("myByteBuf1 writableBytes=" + myByteBuf1.writableBytes());
        System.out.println(myByteBuf1.readBoolean());
        System.out.println(myByteBuf1.readBoolean());
        System.out.println("myByteBuf1 writableBytes=" + myByteBuf1.writableBytes());

        // 再申请一次，看看池化的处理如何
        MyByteBuf myByteBuf2 = myPooledByteBufAllocator.heapBuffer(8192*2,8192 * 10);
        System.out.println("myByteBuf2 writableBytes=" + myByteBuf2.writableBytes());
        myByteBuf2.writeBoolean(true);
        myByteBuf2.writeBoolean(true);
        System.out.println("myByteBuf2 writableBytes=" + myByteBuf2.writableBytes());
        System.out.println(myByteBuf2.readBoolean());
        System.out.println(myByteBuf2.readBoolean());
        System.out.println("myByteBuf2 writableBytes=" + myByteBuf2.writableBytes());

        myByteBuf1.release();
        myByteBuf2.release();
    }
}
