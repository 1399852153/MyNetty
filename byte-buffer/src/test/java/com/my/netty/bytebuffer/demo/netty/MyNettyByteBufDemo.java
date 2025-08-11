package com.my.netty.bytebuffer.demo.netty;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.bytebuffer.netty.allocator.MyByteBufAllocator;
import com.my.netty.bytebuffer.netty.allocator.MyUnpooledByteBufAllocator;

public class MyNettyByteBufDemo {

    public static void main(String[] args) {
        MyByteBufAllocator myByteBufAllocator = new MyUnpooledByteBufAllocator();
        // 1. 创建ByteBuf
        MyByteBuf heapBuffer = myByteBufAllocator.heapBuffer(16);
        System.out.println("初始状态: " + formatState(heapBuffer));

        // 2. 写入数据
        heapBuffer.writeBytes("Hello world!".getBytes());
        System.out.println("写入后: " + formatState(heapBuffer));

        // 3. 读取数据
        String readStr = readAsString(heapBuffer); // read操作，不推进读指针
        System.out.println("读取到的数据：" + readStr + " size=" + readStr.length());
        System.out.println("读取后: " + formatState(heapBuffer));

        // 4. 重置读指针(更灵活的rewind操作)
        heapBuffer.resetReaderIndex(); // markedReaderIndex默认为0，等价于rewind
        System.out.println("重置读指针后: " + formatState(heapBuffer));

        // 5. 重置写指针(更灵活的clear操作)
        heapBuffer.resetWriterIndex(); // markedWriterIndex默认为0，等价于clear
        System.out.println("重置写指针后: " + formatState(heapBuffer));

        // 6. 引用技术展示
        System.out.println("初始化时：ref=" + heapBuffer.refCnt());
        heapBuffer.retain();
        System.out.println("retain后：ref=" + heapBuffer.refCnt());
        heapBuffer.retain(2);
        System.out.println("retain(2)后：ref=" + heapBuffer.refCnt());

        heapBuffer.release();
        System.out.println("release后：ref=" + heapBuffer.refCnt());

        heapBuffer.release(2);
        System.out.println("release(2)后：ref=" + heapBuffer.refCnt() + " " + formatState(heapBuffer));

        heapBuffer.release();
        System.out.println("最终release()完全释放后: ref=" + heapBuffer.refCnt() + " " + heapBuffer.capacity());

    }

    /**
     * 辅助方法：格式化缓冲区状态
     * */
    private static String formatState(MyByteBuf myByteBuf) {
        return String.format("readableBytes=%d writableBytes=%d capacity=%d%n", myByteBuf.readableBytes(), myByteBuf.writableBytes(), myByteBuf.capacity());
    }

    private static String readAsString(MyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return new String(bytes);
    }
}
