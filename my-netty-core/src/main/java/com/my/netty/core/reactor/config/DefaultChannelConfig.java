package com.my.netty.core.reactor.config;

/**
 * channel相关的配置，待后续拓展
 * */
public class DefaultChannelConfig {

    private int initialReceiveBufferSize = -1;

    public int getInitialReceiveBufferSize() {
        return initialReceiveBufferSize;
    }

    public void setInitialReceiveBufferSize(int initialReceiveBufferSize) {
        this.initialReceiveBufferSize = initialReceiveBufferSize;
    }
}
