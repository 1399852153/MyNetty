package com.my.netty.core.reactor.util;

import com.my.netty.bytebuffer.netty.MyReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyReferenceCountUtil {

    private static final Logger logger = LoggerFactory.getLogger(MyReferenceCountUtil.class);

    public static void safeRelease(Object msg) {
        try {
            release(msg);
        } catch (Throwable t) {
            logger.warn("Failed to release a message: {}", msg, t);
        }
    }

    public static boolean release(Object msg) {
        if (msg instanceof MyReferenceCounted) {
            return ((MyReferenceCounted) msg).release();
        }
        return false;
    }
}
