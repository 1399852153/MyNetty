package com.my.netty.core.reactor.handler.context;

import com.my.netty.core.reactor.handler.MyChannelEventHandler;
import com.my.netty.core.reactor.handler.pinpline.MyChannelPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * pipeline的tail哨兵节点
 * */
public class MyChannelPipelineTailContext extends MyAbstractChannelHandlerContext implements MyChannelEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(MyChannelPipelineTailContext.class);

    public MyChannelPipelineTailContext(MyChannelPipeline pipeline) {
        super(pipeline, MyChannelPipelineTailContext.class);
    }

    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) {
        // 如果channelRead事件传播到了tail节点，说明用户自定义的handler没有处理好，但问题不大，打日志警告下
        onUnhandledInboundMessage(ctx,msg);
    }

    @Override
    public void exceptionCaught(MyChannelHandlerContext ctx, Throwable cause) {
        // 如果exceptionCaught事件传播到了tail节点，说明用户自定义的handler没有处理好，但问题不大，打日志警告下
        onUnhandledInboundException(cause);
    }

    @Override
    public void close(MyChannelHandlerContext ctx) throws Exception {
        // do nothing
        logger.info("close op, tail context do nothing");
    }

    @Override
    public void write(MyChannelHandlerContext ctx, Object msg) throws Exception {
        // do nothing
        logger.info("write op, tail context do nothing");
    }

    @Override
    public MyChannelEventHandler handler() {
        return this;
    }

    private void onUnhandledInboundException(Throwable cause) {
        logger.warn(
            "An exceptionCaught() event was fired, and it reached at the tail of the pipeline. " +
                "It usually means the last handler in the pipeline did not handle the exception.",
            cause);
    }

    private void onUnhandledInboundMessage(MyChannelHandlerContext ctx, Object msg) {
        logger.debug(
            "Discarded inbound message {} that reached at the tail of the pipeline. " +
                "Please check your pipeline configuration.", msg);

        logger.debug("Discarded message pipeline : {}. Channel : {}.",
            ctx.getPipeline(), ctx.channel());
    }
}
