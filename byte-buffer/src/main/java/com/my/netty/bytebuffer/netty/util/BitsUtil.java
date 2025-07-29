package com.my.netty.bytebuffer.netty.util;

public class BitsUtil {

    public static int getInt(byte[] memory, int index) {
        // 和jdk的实现是一样的，在指定index处开始取4个字节，大端法靠前的字节视作高位，靠后的字节视作低位
        return  (memory[index]     & 0xff) << 24 |
                (memory[index + 1] & 0xff) << 16 |
                (memory[index + 2] & 0xff) <<  8 |
                memory[index + 3] & 0xff;
    }

    public static int getIntLE(byte[] memory, int index) {
        // 和jdk的实现是一样的，在指定index处开始取4个字节，小端法靠前的字节视作低位，靠后的字节视作高位
        return  memory[index]      & 0xff        |
                (memory[index + 1] & 0xff) << 8  |
                (memory[index + 2] & 0xff) << 16 |
                (memory[index + 3] & 0xff) << 24;
    }
}
