package com.my.netty.core.reactor.eventloop;

import com.my.netty.core.reactor.channel.MyNioChannel;
import com.my.netty.core.reactor.channel.MyNioSocketChannel;
import com.my.netty.core.reactor.config.DefaultChannelConfig;
import com.my.netty.core.reactor.exception.MyNettyException;
import com.my.netty.core.reactor.handler.pipeline.MyChannelPipelineSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    private final Queue<Runnable> taskQueue = new LinkedBlockingQueue<>(256);

    private volatile Thread thread;
    private final MyNioEventLoopGroup childGroup;

    private final AtomicBoolean threadStartedFlag = new AtomicBoolean(false);

    private MyChannelPipelineSupplier channelPipelineSupplier;

    private DefaultChannelConfig defaultChannelConfig;

    public MyNioEventLoop(DefaultChannelConfig defaultChannelConfig){
        this(null,defaultChannelConfig);
    }

    public MyNioEventLoop(MyNioEventLoopGroup childGroup, DefaultChannelConfig defaultChannelConfig) {
        this.childGroup = childGroup;

        SelectorProvider selectorProvider = SelectorProvider.provider();
        try {
            this.unwrappedSelector = selectorProvider.openSelector();
        } catch (IOException e) {
            throw new MyNettyException("open selector error!",e);
        }

        this.defaultChannelConfig = defaultChannelConfig;
    }

    @Override
    public void execute(Runnable task) {
        // 将任务加入eventLoop所属的任务队列，事件循环中线程的无限会把任务捞起来处理
        taskQueue.add(task);

        if(this.thread != Thread.currentThread()){
            // 如果执行execute方法的线程不是当前线程，可能当前eventLoop对应的thread还没有启动
            // 尝试启动当前eventLoop对应的线程(cas防并发，避免重复启动新线程)
            if(threadStartedFlag.compareAndSet(false,true)){
                // 类似netty的ThreadPerTaskExecutor,启动一个线程来执行事件循环
                // 使用自定义的Thread，能够更好的使用FastThreadLocal
                Thread newThread = this.defaultChannelConfig.getDefaultThreadFactory().newThread(()->{
                    // 将eventLoop的thread与新启动的这个thread进行绑定
                    this.thread = Thread.currentThread();

                    // 执行监听selector的事件循环
                    doEventLoop();
                });
                newThread.start();
            }
        }

        boolean inEventLoop = inEventLoop();
        if(!inEventLoop){
            // 因为eventLoop中selector.select是阻塞等待的，如果是别的线程提交了任务
            // 要尝试把当前eventLoop对应的线程唤醒，令其能去执行队列里的任务
            this.unwrappedSelector.wakeup();
        }
    }

    public boolean inEventLoop(){
        return this.thread == Thread.currentThread();
    }

    public Selector getUnwrappedSelector() {
        return unwrappedSelector;
    }

    public void setMyChannelPipelineSupplier(MyChannelPipelineSupplier channelPipelineSupplier) {
        this.channelPipelineSupplier = channelPipelineSupplier;
    }

    public void register(MyNioChannel myNioChannel){
        doRegister(this,myNioChannel);
    }

    private void doEventLoop(){
        // 事件循环
        for(;;){
            try{
                if(taskQueue.isEmpty()){
                    int keys = unwrappedSelector.select();
                    if (keys == 0) {
                        continue;
                    }
                }else{
                    // 确保任务队列里的任务能够被触发
                    unwrappedSelector.selectNow();
                }

                // 简单起见，暂不实现基于时间等元素的更为公平的执行策略
                // 直接先处理io，再处理所有task(ioRatio=100), netty默认ioRatio=50(比如处理某个channel大量写出时，可以公平一点)
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

                if(key.isWritable()){
                    // 处理OP_WRITE事件（setOpWrite中注册的）
                    processWriteEvent(key);
                }
            }catch (Throwable e){
                logger.error("server event loop process an selectionKey error!",e);

                // 处理io事件有异常，取消掉监听的key，并且尝试把channel也关闭掉
                key.cancel();
                if(key.channel() != null){
                    logger.error("has error, close channel={} ",key.channel());
                    key.channel().close();
                }

                Object attachment = key.attachment();
                if(attachment != null){
                    // 目前所有的attachment都是MyNioChannel
                    MyNioSocketChannel myNioSocketChannel = (MyNioSocketChannel)attachment;
                    myNioSocketChannel.getChannelPipeline().close();
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
        socketChannel.finishConnect();
        logger.info("socketChannel={} finishConnect!",socketChannel);

        MyNioSocketChannel myNioSocketChannel = new MyNioSocketChannel(this.unwrappedSelector,socketChannel,channelPipelineSupplier,defaultChannelConfig);
        if(this.childGroup != null){
            // boss/worker模式，boss线程只负责接受和建立连接
            // 将建立的连接交给child线程组去处理后续的读写
            MyNioEventLoop childEventLoop = childGroup.next();
            childEventLoop.execute(()->{
                childEventLoop.register(myNioSocketChannel);
            });
        }else{
            // 没有设置childGroup，就由bossGroup自己处理
            doRegister(this,myNioSocketChannel);
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
        // 目前所有的attachment都是MyNioChannel
        MyNioSocketChannel myNioChannel = (MyNioSocketChannel) key.attachment();

        myNioChannel.read(key);
    }

    private void processWriteEvent(SelectionKey key) throws IOException {
        // 目前所有的attachment都是MyNioChannel
        MyNioSocketChannel myNioChannel = (MyNioSocketChannel) key.attachment();

        // 执行flush0方法
        myNioChannel.flush0();
    }

    private void doRegister(MyNioEventLoop myNioEventLoop, MyNioChannel myNioChannel){
        try {
            // 与当前eventLoop绑定
            myNioChannel.setMyNioEventLoop(myNioEventLoop);

            // 将接受到的连接注册到selector中，并监听read事件
            // 并且将MyNioChannel这一channel的包装类作为附件与socketChannel进行绑定
            SelectionKey selectionKey = myNioChannel.getJavaChannel().register(unwrappedSelector, SelectionKey.OP_READ, myNioChannel);
            myNioChannel.setSelectionKey(selectionKey);

            logger.info("socketChannel={} register success! eventLoop={}",myNioChannel,this);
        }catch (Exception e){
            logger.error("register socketChannel={} error!",myNioChannel,e);
            try {
                myNioChannel.getJavaChannel().close();
            } catch (IOException ex) {
                logger.error("register channel close={} error!",myNioChannel,ex);
            }
        }
    }
}
