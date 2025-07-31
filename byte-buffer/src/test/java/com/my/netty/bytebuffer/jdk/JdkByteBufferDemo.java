package com.my.netty.bytebuffer.jdk;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class JdkByteBufferDemo {

    public static void main(String[] args) {
        // 1. 创建ByteBuffer（分配10字节空间）
        MyByteBuffer myByteBuffer = MyByteBuffer.allocate(10);
        System.out.println("初始状态: " + formatState(myByteBuffer));

        // 2. 添加数据(初始化时是写模式)
        myByteBuffer.put((byte) 0x41);  // 写入'A'
        myByteBuffer.putInt(123456);    // 写入一个整数
        System.out.println("写入后: " + formatState(myByteBuffer));

        // 3. 写完后翻转到读模式
        myByteBuffer.flip();
        System.out.println("flip后: " + formatState(myByteBuffer));

        // 4. 读取数据
        byte b = myByteBuffer.get();
        int i = myByteBuffer.getInt();
        System.out.println("\n读取的数据:");
        System.out.println("byte: " + b + " (ASCII: " + (char)b + ")");
        System.out.println("int: " + i);
        System.out.println("读取完毕: " + formatState(myByteBuffer));
        System.out.println("===========================");

        // 5. 演示重置操作
        myByteBuffer.rewind(); // 回到缓冲区的开始位置
        System.out.println("rewind后: " + formatState(myByteBuffer));
        System.out.println("重新读取第一个字节: " + myByteBuffer.get());
        System.out.println("重新读取int整数: " + myByteBuffer.getInt());
        System.out.println("rewind后，第二次读取完毕: " + formatState(myByteBuffer));
        System.out.println("===========================");

        // 6. 清空缓冲区（准备重新写入）
        myByteBuffer.clear();
        System.out.println("\nclear后: " + formatState(myByteBuffer));
        System.out.println("===========================");

        // 7. 字节序演示
        int num = 0x01020304;
        myByteBuffer.clear();
        myByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        myByteBuffer.putInt(num);
        myByteBuffer.flip();
        System.out.println("小端序读取: " + String.format("0x%08X", myByteBuffer.getInt()));

        myByteBuffer.clear();
        myByteBuffer.order(ByteOrder.BIG_ENDIAN);
        myByteBuffer.putInt(num);
        myByteBuffer.flip();
        System.out.println("大端序读取: " + String.format("0x%08X", myByteBuffer.getInt()));

        // 8. 处理字符串
        String text = "Hello world!";
        MyByteBuffer strBuffer = MyByteBuffer.allocate(20);
        strBuffer.put(text.getBytes(StandardCharsets.UTF_8));
        strBuffer.flip();
        byte[] strBytes = new byte[strBuffer.remaining()];
        strBuffer.get(strBytes);
        System.out.println("\n字符串内容: " + new String(strBytes));
    }

    /**
     * 辅助方法：格式化缓冲区状态
     * */
    private static String formatState(MyByteBuffer buf) {
        return String.format("position=%d, limit=%d, capacity=%d", buf.getPosition(), buf.getLimit(), buf.getCapacity());
    }
}
