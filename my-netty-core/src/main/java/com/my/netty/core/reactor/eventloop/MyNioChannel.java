package com.my.netty.core.reactor.eventloop;

import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;

public class MyNioChannel {

    private Selector selector;

    private SelectableChannel selectableChannel;

    private MyNioEventLoop myNioEventLoop;

    public Selector getSelector() {
        return selector;
    }

    public void setSelector(Selector selector) {
        this.selector = selector;
    }

    public SelectableChannel getSelectableChannel() {
        return selectableChannel;
    }

    public void setSelectableChannel(SelectableChannel selectableChannel) {
        this.selectableChannel = selectableChannel;
    }

    public MyNioEventLoop getMyNioEventLoop() {
        return myNioEventLoop;
    }

    public void setMyNioEventLoop(MyNioEventLoop myNioEventLoop) {
        this.myNioEventLoop = myNioEventLoop;
    }
}
