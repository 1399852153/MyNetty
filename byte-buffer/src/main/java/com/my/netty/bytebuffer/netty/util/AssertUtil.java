package com.my.netty.bytebuffer.netty.util;

public class AssertUtil {

    public static void isNull(Object object, String message) {
        if (object != null) {
            throw new NullPointerException(message);
        }
    }
}
