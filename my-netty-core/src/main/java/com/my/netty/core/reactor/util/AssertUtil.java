package com.my.netty.core.reactor.util;

public class AssertUtil {

    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
