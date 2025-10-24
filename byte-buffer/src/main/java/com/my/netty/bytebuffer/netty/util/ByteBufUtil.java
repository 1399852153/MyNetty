package com.my.netty.bytebuffer.netty.util;


import com.my.netty.bytebuffer.netty.MyByteBuf;

import java.nio.charset.Charset;

public class ByteBufUtil {

    public static String decodeString(MyByteBuf src, int readerIndex, int len, Charset charset) {
        if (len == 0) {
            return "";
        }

        // 暂时只有堆内存的，所以都是由底层array的
        byte[] array = src.array();
        int offset = src.arrayOffset() + readerIndex;

        return new String(array, offset, len, charset);
    }

    public static void setInt(byte[] memory, int index, int value) {
        memory[index]     = (byte) (value >>> 24);
        memory[index + 1] = (byte) (value >>> 16);
        memory[index + 2] = (byte) (value >>> 8);
        memory[index + 3] = (byte) value;
    }
}
