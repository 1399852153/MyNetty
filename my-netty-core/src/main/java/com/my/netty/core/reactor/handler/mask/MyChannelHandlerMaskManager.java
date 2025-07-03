package com.my.netty.core.reactor.handler.mask;

import com.my.netty.core.reactor.handler.MyChannelEventHandler;
import com.my.netty.core.reactor.handler.annotation.Skip;
import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 计算并缓存每一个类型的handler所需要处理方法掩码的管理器
 *
 * 参考自netty的ChannelHandlerMask类
 * */
public class MyChannelHandlerMaskManager {

    private static final Logger logger = LoggerFactory.getLogger(MyChannelHandlerMaskManager.class);

    public static final int MASK_EXCEPTION_CAUGHT = 1;

    // ==================== inbound ==========================
    public static final int MASK_CHANNEL_REGISTERED = 1 << 1;
    public static final int MASK_CHANNEL_UNREGISTERED = 1 << 2;
    public static final int MASK_CHANNEL_ACTIVE = 1 << 3;
    public static final int MASK_CHANNEL_INACTIVE = 1 << 4;
    public static final int MASK_CHANNEL_READ = 1 << 5;
    public static final int MASK_CHANNEL_READ_COMPLETE = 1 << 6;
//    static final int MASK_USER_EVENT_TRIGGERED = 1 << 7;
//    static final int MASK_CHANNEL_WRITABILITY_CHANGED = 1 << 8;

    // ===================== outbound =========================
    public static final int MASK_BIND = 1 << 9;
    public static final int MASK_CONNECT = 1 << 10;
//    static final int MASK_DISCONNECT = 1 << 11;
    public static final int MASK_CLOSE = 1 << 12;
//    static final int MASK_DEREGISTER = 1 << 13;
    public static final int MASK_READ = 1 << 14;
    public static final int MASK_WRITE = 1 << 15;
    public static final int MASK_FLUSH = 1 << 16;

    private static final ThreadLocal<Map<Class<? extends MyChannelEventHandler>, Integer>> MASKS =
        ThreadLocal.withInitial(() -> new WeakHashMap<>(32));

    public static int mask(Class<? extends MyChannelEventHandler> clazz) {
        // 对于非共享的handler，会随着channel的创建而被大量创建
        // 为了避免反复的计算同样类型handler的mask掩码而引入缓存，优先从缓存中获得对应处理器类的掩码
        Map<Class<? extends MyChannelEventHandler>, Integer> cache = MASKS.get();
        Integer mask = cache.get(clazz);
        if (mask == null) {
            // 缓存中不存在，计算出对应类型的掩码值
            mask = calculateChannelHandlerMask(clazz);
            cache.put(clazz, mask);
        }
        return mask;
    }

    private static int calculateChannelHandlerMask(Class<? extends MyChannelEventHandler> handlerType) {
        int mask = 0;

        // MyChannelEventHandler中的方法一一对应，如果支持就通过掩码的或运算将对应的bit位设置为1

        if(!needSkip(handlerType,"channelRead", MyChannelHandlerContext.class,Object.class)){
            mask |= MASK_CHANNEL_READ;
        }

        if(!needSkip(handlerType,"exceptionCaught", MyChannelHandlerContext.class,Throwable.class)){
            mask |= MASK_EXCEPTION_CAUGHT;
        }

        if(!needSkip(handlerType,"close", MyChannelHandlerContext.class)){
            mask |= MASK_CLOSE;
        }

        if(!needSkip(handlerType,"write", MyChannelHandlerContext.class,Object.class)){
            mask |= MASK_WRITE;
        }

        return mask;
    }

    private static boolean needSkip(Class<?> handlerType, String methodName, Class<?>... paramTypes) {
        try {
            Method method = handlerType.getMethod(methodName, paramTypes);

            // 如果有skip注解，说明需要跳过
            return method.isAnnotationPresent(Skip.class);
        } catch (NoSuchMethodException e) {
            // 没有这个方法，就不需要设置掩码
            return false;
        }
    }
}
