package com.my.netty.core.reactor.handler;

import java.nio.channels.SelectableChannel;

public interface MyEventHandler {

    void fireChannelRead(SelectableChannel selectableChannel, byte[] msg) throws Exception;
}
