package com.my.netty.demo.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

public class NIOClient {

    private static volatile SocketChannel clientSocketChannel;

    public static void main(String[] args) throws Exception {
        SelectorProvider selectorProvider = SelectorProvider.provider();
        Selector selector = selectorProvider.openSelector();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        new Thread(()->{
            try {
                startClient(selector,countDownLatch);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        countDownLatch.await();
        System.out.println("please input message:");
        while(true){
            Scanner sc = new Scanner(System.in);
            String msg = sc.next();
            System.out.println("get input message:" + msg);

            // 发送消息
            ByteBuffer writeBuffer = ByteBuffer.allocate(64);
            writeBuffer.put(msg.getBytes(StandardCharsets.UTF_8));
            writeBuffer.flip(); // 写完了，flip供后续去读取
            clientSocketChannel.write(writeBuffer);
        }
    }

    private static void startClient(Selector selector, CountDownLatch countDownLatch) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        clientSocketChannel = socketChannel;

        // doConnect
        // Returns: true if a connection was established,
        //          false if this channel is in non-blocking mode and the connection operation is in progress;
        if(!socketChannel.connect(new InetSocketAddress("127.0.0.1", 8080))) {
            // 配置为非阻塞，会返回false，通过注册并监听connect事件的方式进行交互
            socketChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
        }

        for(;;){
            try {
                int keys = selector.select(60000);
                if (keys == 0) {
                    System.out.println("client 60s未监听到事件，继续监听！");
                    continue;
                }

                // processSelectedKeysPlain
                Iterator<SelectionKey> selectionKeyItr = selector.selectedKeys().iterator();

                while (selectionKeyItr.hasNext()) {
                    SelectionKey key = selectionKeyItr.next();
                    try {
                        System.out.println("process SelectionKey=" + key.readyOps());

                        // 拿出来后，要把集合中已经获取到的事件移除掉，避免重复的处理
                        selectionKeyItr.remove();

                        if (key.isConnectable()) {
                            // 处理连接相关事件
                            processConnectEvent(key,countDownLatch);
                        }

                        if (key.isReadable()){
                            processReadEvent(key);
                        }

                        if (key.isWritable()){
                            System.out.println("watch an write event!");
                        }

                    } catch (Exception e) {
                        System.out.println("client event loop process an selectionKey error! " + e.getMessage());

                        key.cancel();
                        if(key.channel() != null){
                            key.channel().close();
                            System.out.println("has error, close channel!" );
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("client event loop error! ");
                e.getStackTrace();
            }
        }
    }

    private static void processConnectEvent(SelectionKey key, CountDownLatch countDownLatch) throws IOException {
        // remove OP_CONNECT as otherwise Selector.select(..) will always return without blocking
        int ops = key.interestOps();
        ops &= ~SelectionKey.OP_CONNECT;
        key.interestOps(ops);

        SocketChannel socketChannel = (SocketChannel) key.channel();
        if(socketChannel.finishConnect()){
            // 确认完成连接
            System.out.println("client channel connected!");

            countDownLatch.countDown();
        }else{
            // 连接建立失败，程序退出
            System.out.println("client channel connect failed!");
            System.exit(1);
        }
    }

    private static void processReadEvent(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // 创建ByteBuffer，并开辟一个1M的缓冲区
        ByteBuffer buffer = ByteBuffer.allocate(64);
        // 读取请求码流，返回读取到的字节数
        int readBytes = socketChannel.read(buffer);

        // 读取到字节，对字节进行编解码
        if(readBytes > 0){
            // 将缓冲区当前的limit设置为position=0，用于后续对缓冲区的读取操作
            buffer.flip();
            // 根据缓冲区可读字节数创建字节数组
            byte[] bytes = new byte[buffer.remaining()];
            // 将缓冲区可读字节数组复制到新建的数组中
            buffer.get(bytes);
            String response = new String(bytes, StandardCharsets.UTF_8);
            System.out.println("client received response message: " + response);
        }

        // 读取到了EOF，关闭连接
        if(readBytes < 0){
            socketChannel.close();
        }
    }
}
