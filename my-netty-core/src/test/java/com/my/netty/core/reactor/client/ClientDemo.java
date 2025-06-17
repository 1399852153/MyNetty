package com.my.netty.core.reactor.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Scanner;

public class ClientDemo {

    public static void main(String[] args) throws IOException {
        MyNettyNioClient myNioClient = new MyNettyNioClient(new InetSocketAddress(8080),new EchoClientEventHandler(),5);
        myNioClient.start();

        System.out.println("please input message:");
        while(true){
            Scanner sc = new Scanner(System.in);
            String msg = sc.next();
            System.out.println("get input message:" + msg);

            // 发送消息
            myNioClient.sendMessage(msg);
        }
    }
}
