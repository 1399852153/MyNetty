package com.my.netty.core.reactor.pipeline;

import com.my.netty.core.reactor.handler.MyChannelEventHandlerAdapter;
import com.my.netty.core.reactor.handler.annotation.Sharable;

@Sharable
public class TestSharableHandler extends MyChannelEventHandlerAdapter {
}
