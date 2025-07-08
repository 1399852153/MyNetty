package com.my.netty.core.reactor.server;

import com.my.netty.core.reactor.client.EchoClientEventHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.locks.LockSupport;

public class ServerDemo {

    public static void main(String[] args) throws IOException {
        MyNettyNioServer myNioServer = new MyNettyNioServer(
            new InetSocketAddress(8080),new EchoServerEventHandler(),1,5);
        myNioServer.start();

        LockSupport.park();
    }
}
