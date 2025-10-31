package com.my.netty.demo.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class NIOEchoServer {

    public static void main(String[] args) throws IOException {
        SelectorProvider selectorProvider = SelectorProvider.provider();
        Selector selector = selectorProvider.openSelector();

        // 服务端监听accept事件的channel
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        for(;;){
            try{
                int keys = selector.select(60000);
                if (keys == 0) {
                    System.out.println("server 60s未监听到事件，继续监听！");
                    continue;
                }

                // processSelectedKeysPlain
                Iterator<SelectionKey> selectionKeyItr = selector.selectedKeys().iterator();
                while (selectionKeyItr.hasNext()) {
                    SelectionKey key = selectionKeyItr.next();
                    System.out.println("process SelectionKey=" + key.readyOps());
                    try {

                        // 拿出来后，要把集合中已经获取到的事件移除掉，避免重复的处理
                        selectionKeyItr.remove();

                        if (key.isAcceptable()) {
                            // 处理accept事件（接受到来自客户端的连接请求）
                            processAcceptEvent(key);
                        }

                        if (key.isReadable()) {
                            // 处理read事件
                            processReadEvent(key);
                        }
                    }catch (Exception e){
                        System.out.println("server event loop process an selectionKey error! " + e.getMessage());
                        e.printStackTrace();

                        key.cancel();
                        if(key.channel() != null){
                            System.out.println("has error, close channel! " + key.channel());
                            key.channel().close();
                        }
                    }
                }
            }catch (Exception e){
                System.out.println("server event loop error! ");
                e.getStackTrace();
            }
        }
    }

    private static void processAcceptEvent(SelectionKey key) throws IOException {
        // 能收到accept事件的channel一定是ServerSocketChannel

        ServerSocketChannel ssChannel = (ServerSocketChannel)key.channel();
        // 获得与客户端建立的那个连接
        SocketChannel socketChannel = ssChannel.accept();
        socketChannel.configureBlocking(false);

        socketChannel.finishConnect();

        System.out.println("socketChannel=" + socketChannel + " finishConnect!");
        // 将接受到的连接注册到同样的selector中，并监听read事件
        socketChannel.register(key.selector(),SelectionKey.OP_READ);
    }

    private static void processReadEvent(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel)key.channel();

        // 简单起见，buffer不缓存，每次读事件来都新创建一个
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);

        int byteRead = socketChannel.read(readBuffer);
        if(byteRead == -1){
            // 简单起见不考虑tcp半连接的情况，返回-1直接关掉连接
            socketChannel.close();
        }else{
            // 将缓冲区当前的limit设置为position=0，用于后续对缓冲区的读取操作
            readBuffer.flip();
            // 根据缓冲区可读字节数创建字节数组
            byte[] bytes = new byte[readBuffer.remaining()];
            // 将缓冲区可读字节数组复制到新建的数组中
            readBuffer.get(bytes);
            String receivedStr = new String(bytes, StandardCharsets.UTF_8);

            System.out.println("received message:" + receivedStr + " ,from " + socketChannel.socket().getRemoteSocketAddress());

            // 读完了，echo服务器准备回写数据到客户端
            String echoMessage = "server echo:" + receivedStr;

            ByteBuffer writeBuffer = ByteBuffer.allocateDirect(1024);
            writeBuffer.put(echoMessage.getBytes(StandardCharsets.UTF_8));
            writeBuffer.flip(); // 写完了，flip供后续去读取
            socketChannel.write(writeBuffer);
        }
    }
}
