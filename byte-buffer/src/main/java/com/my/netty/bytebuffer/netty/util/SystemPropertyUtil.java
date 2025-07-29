package com.my.netty.bytebuffer.netty.util;

public class SystemPropertyUtil {

    public static boolean getBoolean(String key, boolean def) {
        // 在netty的基础上做了简化
        String value = System.getProperty(key,null);
        if (value == null) {
            return def;
        }

        value = value.trim().toLowerCase();
        if (value.isEmpty()) {
            return def;
        }

        if ("true".equals(value) || "yes".equals(value) || "1".equals(value)) {
            return true;
        }

        if ("false".equals(value) || "no".equals(value) || "0".equals(value)) {
            return false;
        }

        return def;
    }
}
