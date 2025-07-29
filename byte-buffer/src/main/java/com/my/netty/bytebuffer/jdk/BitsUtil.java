package com.my.netty.bytebuffer.jdk;

public class BitsUtil {

    static int getInt(MyByteBuffer bb, int bi, boolean bigEndian) {
        if(bigEndian){
            // 按照大端法读取一个整数
            return getIntB(bb, bi);
        }else{
            // 按照小端法读取一个整数
            return getIntL(bb, bi);
        }
    }

    static void putInt(MyByteBuffer bb, int bi, int x, boolean bigEndian) {
        if (bigEndian) {
            // 按照大端法写入一个整数
            putIntB(bb, bi, x);
        } else {
            // 按照小端法写入一个整数
            putIntL(bb, bi, x);
        }
    }

    static int getIntL(MyByteBuffer bb, int bi) {
        return makeInt(bb._get(bi + 3),
            bb._get(bi + 2),
            bb._get(bi + 1),
            bb._get(bi    ));
    }

    static int getIntB(MyByteBuffer bb, int bi) {
        return makeInt(bb._get(bi),
            bb._get(bi + 1),
            bb._get(bi + 2),
            bb._get(bi + 3));
    }

    static void putIntB(MyByteBuffer bb, int bi, int x) {
        bb._put(bi, int3(x));
        bb._put(bi + 1, int2(x));
        bb._put(bi + 2, int1(x));
        bb._put(bi + 3, int0(x));
    }

    static void putIntL(MyByteBuffer bb, int bi, int x) {
        bb._put(bi + 3, int3(x));
        bb._put(bi + 2, int2(x));
        bb._put(bi + 1, int1(x));
        bb._put(bi, int0(x));
    }

    static private int makeInt(byte b3, byte b2, byte b1, byte b0) {
        return (((b3) << 24) |
            ((b2 & 0xff) << 16) |
            ((b1 & 0xff) <<  8) |
            ((b0 & 0xff)));
    }

    private static byte int3(int x) { return (byte)(x >> 24); }
    private static byte int2(int x) { return (byte)(x >> 16); }
    private static byte int1(int x) { return (byte)(x >>  8); }
    private static byte int0(int x) { return (byte)(x); }
}
