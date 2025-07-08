package com.my.netty.core.reactor.eventloop;

import com.my.netty.core.reactor.handler.MyEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyNioEventLoop implements Executor {

    private static final Logger logger = LoggerFactory.getLogger(MyNioEventLoop.class);

    /**
     * 原始的jdk中的selector
     * */
    private final Selector unwrappedSelector;

    private final Queue<Runnable> taskQueue = new LinkedBlockingQueue<>(16);

    private volatile Thread thread;
    private final MyNioEventLoopGroup childGroup;

    private final AtomicBoolean threadStartedFlag = new AtomicBoolean(false);

    private MyEventHandler myEventHandler;

    public MyNioEventLoop(){
        this(null);
    }

    public MyNioEventLoop(MyNioEventLoopGroup childGroup) {
        this.childGroup = childGroup;

        SelectorProvider selectorProvider = SelectorProvider.provider();
        try {
            this.unwrappedSelector = selectorProvider.openSelector();
        } catch (IOException e) {
            throw new RuntimeException("open selector error!",e);
        }
    }

    @Override
    public void execute(Runnable task) {
        // 将任务加入eventLoop所属的任务队列，事件循环中会
        taskQueue.add(task);

        if(this.thread != Thread.currentThread()){
            // 如果执行execute方法的线程不是当前线程，可能当前eventLoop对应的thread还没有启动
            // 尝试启动当前eventLoop对应的线程(cas防并发)
            if(threadStartedFlag.compareAndSet(false,true)){
                // 类似netty的ThreadPerTaskExecutor,启动一个线程来执行事件循环
                new Thread(()->{
                    // 将eventLoop的thread与新启动的这个thread进行绑定
                    this.thread = Thread.currentThread();

                    // 执行监听selector的事件循环
                    doEventLoop();
                }).start();
            }
        }
    }

    public Selector getUnwrappedSelector() {
        return unwrappedSelector;
    }

    public void setMyEventHandler(MyEventHandler myEventHandler) {
        this.myEventHandler = myEventHandler;
    }

    private void doEventLoop(){
        // 事件循环
        for(;;){
            try{
                if(taskQueue.isEmpty()){
                    int keys = unwrappedSelector.select(60000);
                    if (keys == 0) {
                        logger.info("server 60s未监听到事件，继续监听！");
                        continue;
                    }
                }else{
                    // 确保任务队列里的任务能够被触发
                    unwrappedSelector.selectNow();
                }

                // 简单起见，暂不实现基于时间等元素的更为公平的执行策略
                // 直接先处理io，再处理所有task(ioRatio=100)
                try {
                    // 处理监听到的io事件
                    processSelectedKeys();
                }finally {
                    // Ensure we always run tasks.
                    // 处理task队列里的任务
                    runAllTasks();
                }
            }catch (Throwable e){
                logger.error("server event loop error!",e);
            }
        }
    }

    private void processSelectedKeys() throws IOException {
        // processSelectedKeysPlain
        Iterator<SelectionKey> selectionKeyItr = unwrappedSelector.selectedKeys().iterator();
        while (selectionKeyItr.hasNext()) {
            SelectionKey key = selectionKeyItr.next();
            logger.info("process SelectionKey={}",key.readyOps());
            try {
                // 拿出来后，要把集合中已经获取到的事件移除掉，避免重复的处理
                selectionKeyItr.remove();

                if (key.isConnectable()) {
                    // 处理客户端连接建立相关事件
                    processConnectEvent(key);
                }

                if (key.isAcceptable()) {
                    // 处理服务端accept事件（接受到来自客户端的连接请求）
                    processAcceptEvent(key);
                }

                if (key.isReadable()) {
                    // 处理read事件
                    processReadEvent(key);
                }
            }catch (Throwable e){
                logger.error("server event loop process an selectionKey error!",e);

                // 处理io事件有异常，取消掉监听的key，并且尝试把channel也关闭掉
                key.cancel();
                if(key.channel() != null){
                    logger.error("has error, close channel={} ",key.channel());
                    key.channel().close();
                }
            }
        }
    }

    private void runAllTasks(){
        for (;;) {
            // 通过无限循环，直到把队列里的任务全部捞出来执行掉
            Runnable task = taskQueue.poll();
            if (task == null) {
                return;
            }

            try {
                task.run();
            } catch (Throwable t) {
                logger.warn("A task raised an exception. Task: {}", task, t);
            }
        }
    }

    private void processAcceptEvent(SelectionKey key) throws IOException {
        ServerSocketChannel ssChannel = (ServerSocketChannel)key.channel();

        SocketChannel socketChannel = ssChannel.accept();
        if(this.childGroup != null){
            // boss/worker模式，boss线程只负责接受和建立连接
            // 将建立的连接交给child线程组去处理后续的读写
            MyNioEventLoop childEventLoop = childGroup.next();
            childEventLoop.execute(()->{
                doRegister(childEventLoop,socketChannel);
            });
        }else{
            doRegister(this,socketChannel);
        }
    }

    private void processConnectEvent(SelectionKey key) throws IOException {
        // remove OP_CONNECT as otherwise Selector.select(..) will always return without blocking
        // See https://github.com/netty/netty/issues/924
        int ops = key.interestOps();
        ops &= ~SelectionKey.OP_CONNECT;
        key.interestOps(ops);

        SocketChannel socketChannel = (SocketChannel) key.channel();
        if(socketChannel.finishConnect()){
            // 确认完成连接
            logger.info("client channel connected! socketChannel={}",socketChannel);
        }else{
            logger.error("client channel connect failed!");
            // 连接建立失败，连接关闭(上层catch住会关闭连接)
            throw new Error();
        }
    }

    private void processReadEvent(SelectionKey key) throws Exception {
        SocketChannel socketChannel = (SocketChannel)key.channel();

        // 简单起见，buffer不缓存，每次读事件来都新创建一个
        // 暂时也不考虑黏包/拆包场景(Netty中靠ByteToMessageDecoder解决，后续再分析其原理)，理想的认为每个消息都小于1024，且每次读事件都只有一个消息
        ByteBuffer readBuffer = ByteBuffer.allocate(64);

        int byteRead = socketChannel.read(readBuffer);
        if(byteRead == -1){
            // 简单起见不考虑tcp半连接的情况，返回-1直接关掉连接
            socketChannel.close();
            // 取消key的监听
            key.cancel();
        }else{
            // 将缓冲区当前的limit设置为position=0，用于后续对缓冲区的读取操作
            readBuffer.flip();
            // 根据缓冲区可读字节数创建字节数组
            byte[] bytes = new byte[readBuffer.remaining()];
            // 将缓冲区可读字节数组复制到新建的数组中
            readBuffer.get(bytes);

            if(myEventHandler != null) {
                myEventHandler.fireChannelRead(socketChannel, bytes);
            }
        }
    }

    private void doRegister(MyNioEventLoop myNioEventLoop, SocketChannel socketChannel){
        try {
            // nio的非阻塞channel
            socketChannel.configureBlocking(false);

            socketChannel.finishConnect();

            logger.info("socketChannel={} finishConnect!",socketChannel);

            // 将接受到的连接注册到selector中，并监听read事件
            socketChannel.register(myNioEventLoop.unwrappedSelector, SelectionKey.OP_READ);

            logger.info("socketChannel={} doRegister success!",socketChannel);
        }catch (Exception e){
            logger.error("register socketChannel={} error!",socketChannel,e);
            try {
                socketChannel.close();
            } catch (IOException ex) {
                logger.error("register channel close={} error!",socketChannel,ex);
            }
        }
    }
}
