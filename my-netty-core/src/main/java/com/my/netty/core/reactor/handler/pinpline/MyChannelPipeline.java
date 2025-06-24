package com.my.netty.core.reactor.handler.pinpline;


import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.handler.MyChannelEventHandler;
import com.my.netty.core.reactor.handler.MyChannelEventInvoker;
import com.my.netty.core.reactor.handler.context.MyAbstractChannelHandlerContext;
import com.my.netty.core.reactor.handler.context.MyChannelPipelineHeadContext;
import com.my.netty.core.reactor.handler.context.MyChannelPipelineTailContext;
import com.my.netty.core.reactor.handler.context.MyDefaultChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * pipeline首先自己也是一个handler
 *
 * 包括head和tail两个哨兵节点
 * */
public class MyChannelPipeline implements MyChannelEventInvoker {

    private static final Logger logger = LoggerFactory.getLogger(MyChannelPipeline.class);

    private final MyNioChannel channel;

    /**
     * 整条pipeline上的，head和tail两个哨兵节点
     *
     * inbound入站事件默认都从head节点开始向tail传播
     * outbound出站事件默认都从tail节点开始向head传播
     * */
    private final MyAbstractChannelHandlerContext head;
    private final MyAbstractChannelHandlerContext tail;

    public MyChannelPipeline(MyNioChannel channel) {
        this.channel = channel;

        head = new MyChannelPipelineHeadContext(this);
        tail = new MyChannelPipelineTailContext(this);

        head.setNext(tail);
        tail.setPrev(head);
    }

    @Override
    public void fireChannelRead(Object msg) {
        // 从head节点开始传播读事件(入站)
        MyChannelPipelineHeadContext.invokeChannelRead(head,msg);
    }

    @Override
    public void fireExceptionCaught(Throwable cause) {
        // 异常传播到了pipeline的末尾，打印异常信息
        onUnhandledInboundException(cause);
    }

    @Override
    public void close() {
        // 出站时间，从尾节点向头结点传播
        tail.close();
    }

    @Override
    public void write(Object msg) {
        tail.write(msg);
    }

    public void addFirst(MyChannelEventHandler handler){
        MyAbstractChannelHandlerContext newCtx = newContext(handler);

        MyAbstractChannelHandlerContext oldFirstCtx = head.getNext();
        newCtx.setPrev(head);
        newCtx.setNext(oldFirstCtx);
        head.setNext(newCtx);
        oldFirstCtx.setPrev(newCtx);
    }

    public void addLast(MyChannelEventHandler handler){
        MyAbstractChannelHandlerContext newCtx = newContext(handler);

        // 加入链表尾部节点之前
        MyAbstractChannelHandlerContext oldLastCtx = tail.getPrev();
        newCtx.setPrev(oldLastCtx);
        newCtx.setNext(tail);
        oldLastCtx.setNext(newCtx);
        tail.setPrev(newCtx);
    }

    public MyNioChannel getChannel() {
        return channel;
    }

    private void onUnhandledInboundException(Throwable cause) {
        logger.warn(
            "An exceptionCaught() event was fired, and it reached at the tail of the pipeline. " +
                "It usually means the last handler in the pipeline did not handle the exception.",
            cause);
    }

    private MyAbstractChannelHandlerContext newContext(MyChannelEventHandler handler) {
        return new MyDefaultChannelHandlerContext(this,handler);
    }
}
