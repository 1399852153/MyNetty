# 从零开始实现简易版Netty(二) MyNetty pipeline流水线

## 1. Netty pipeline流水线介绍
在上一篇博客中v1版本的MyNetty参考netty实现了一个极其精简的reactor模型。按照计划，v2版本的MyNetty需要实现pipeline流水线，以支持不同模块处理逻辑的解耦。  
#####
由于本文属于系列博客，读者需要对之前的博客内容有所了解才能更好地理解本篇内容。  
* v1版本博客：[从零开始实现简易版Netty(一) MyNetty Reactor模式](https://www.cnblogs.com/xiaoxiongcanguan/p/18939320)  

#####
在v1版本中，MyNetty的EventLoop处理逻辑中，允许使用者配置一个EventHandler，并在处理read事件时调用其实现的自定义fireChannelRead方法。  
这一机制在实现demo中的简易echo服务器时是够用的，但在实际的场景中，一个完备的网络框架，业务想要处理的IO事件有很多类型，并且不希望在一个大而全的臃肿的处理器中处理所有的IO事件，而是能够模块化的拆分不同的处理逻辑，实现架构上的灵活解耦。  
**因此netty提供了pipeline流水线机制，允许用户在使用netty时能按照自己的需求，按顺序组装自己的处理器链条。**

### 1.1 netty的IO事件
在实际的网络环境中，有非常多不同类型的IO事件，最典型的就是读取来自远端的数据(read)以及向远端写出发送数据(write)。  
netty对这些IO事件进行了抽象，并允许用户编写自定义的处理器监听或是主动触发这些事件。  
netty按照事件数据流的传播方向将IO事件分成了入站(InBound)与出站(OutBound)两大类，由远端输入传播到本地应用程序的事件被叫做入站事件，从本地应用程序触发向远端传播的事件叫出站事件。主要的入站事件有channelRead、channelActive等，主要的出站事件有write、connect、bind等。

### 1.2 netty的IO事件处理器与pipeline流水线
针对InBound入站IO事件，netty抽象出了ChannelInboundHandler接口；针对OutBound出站IO事件，netty抽象出了ChannelOutboundHandler接口。  
用户可以编写一系列继承自对应ChannelHandler接口的自定义处理器，将其绑定到ChannelPipeline中，ChannelPipeline实例是独属于某个特定channel连接的。
todo 附图

## 2. MyNetty实现pipeline流水线
经过上述对于netty的IO事件与pipeline流水线简要介绍后，读者对netty的流水线虽然有了一定的概念，但对具体的细节还是知之甚少。下面我们结合MyNetty的源码，展开介绍netty的流水线机制实现。

### 2.1 MyNetty的事件处理器
```java
/**
 * 事件处理器(相当于netty中ChannelInboundHandler和ChannelOutboundHandler合在一起)
 * */
public interface MyChannelEventHandler {

    // ========================= inbound入站事件 ==============================
    void channelRead(MyChannelHandlerContext ctx, Object msg) throws Exception;

    void exceptionCaught(MyChannelHandlerContext ctx, Throwable cause) throws Exception;

    // ========================= outbound出站事件 ==============================
    void close(MyChannelHandlerContext ctx) throws Exception;

    void write(MyChannelHandlerContext ctx, Object msg) throws Exception;
}
```
* 前面说到，netty将入站与出站事件用两个不同的ChannelEventHandler接口进行了抽象，而在MyNetty中因为最终要支持的IO事件没有netty那么多，所以出站、入站的处理接口进行了合并。  
  这样做虽然在架构上不如netty那样拆分开来的优雅，但相对来说理解起来会更加简单。  
* 未来MyChannelEventHandler还会随着迭代支持更多的IO事件，但这是个渐进的过程，目前lab2中只需要支持少数几个IO事件便能满足需求。

### 2.2 MyNetty的pipeline流水线与ChannelHandler上下文
##### MyNetty pipeline流水线实现
```java
public interface MyChannelEventInvoker {

    // ========================= inbound入站事件 ==============================
    void fireChannelRead(Object msg);

    void fireExceptionCaught(Throwable cause);

    // ========================= outbound出站事件 ==============================
    void close();

    void write(Object msg);
}
```
```java
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
        // 非sharable的handler是否重复加入校验
        checkMultiplicity(handler);

        MyAbstractChannelHandlerContext newCtx = newContext(handler);

        MyAbstractChannelHandlerContext oldFirstCtx = head.getNext();
        newCtx.setPrev(head);
        newCtx.setNext(oldFirstCtx);
        head.setNext(newCtx);
        oldFirstCtx.setPrev(newCtx);
    }

    public void addLast(MyChannelEventHandler handler){
        // 非sharable的handler是否重复加入校验
        checkMultiplicity(handler);

        MyAbstractChannelHandlerContext newCtx = newContext(handler);

        // 加入链表尾部节点之前
        MyAbstractChannelHandlerContext oldLastCtx = tail.getPrev();
        newCtx.setPrev(oldLastCtx);
        newCtx.setNext(tail);
        oldLastCtx.setNext(newCtx);
        tail.setPrev(newCtx);
    }

    private static void checkMultiplicity(MyChannelEventHandler handler) {
        if (handler instanceof MyChannelEventHandlerAdapter) {
            MyChannelEventHandlerAdapter h = (MyChannelEventHandlerAdapter) handler;

            if (!h.isSharable() && h.added) {
                // 一个handler实例不是sharable，但是被加入到了pipeline一次以上，有问题
                throw new MyNettyException(
                        h.getClass().getName() + " is not a @Sharable handler, so can't be added or removed multiple times.");
            }

            // 第一次被引入，当前handler实例标记为已加入
            h.added = true;
        }
    }

    public MyNioChannel getChannel() {
        return channel;
    }

    private void onUnhandledInboundException(Throwable cause) {
        logger.warn("An exceptionCaught() event was fired, and it reached at the tail of the pipeline. " +
                "It usually means the last handler in the pipeline did not handle the exception.", cause);
    }

    private MyAbstractChannelHandlerContext newContext(MyChannelEventHandler handler) {
        return new MyDefaultChannelHandlerContext(this,handler);
    }
}
```
* pipeline实现了ChannelEventInvoker接口，ChannelEventInvoker与ChannelEventHandler中对应IO事件的方法是一一对应的，唯一的区别在于其方法中缺失了(MyChannelHandlerContext ctx)参数。
* 同时，pipeline流水线中定义了两个关键属性，head和tail，其都是AbstractChannelHandlerContext类型的，其内部工作原理我们在下一小节展开。  
  pipeline提供了addFirst和addLast两个方法(netty中提供了非常多功能类似的方法，MyNetty简单起见只实现了最常用的两个)，允许将用户自定义的ChannelHandler挂载在pipeline中，与head、tail组成一个双向链表，而入站出站事件会按照双向链表中节点的顺序进行传播。  
* 对于入站事件(比如fireChannelRead)，事件从head节点开始，从前到后的在handler链表中传播；而出站事件(比如write), 事件则从tail节点开始，从后往前的在handler链表中传播。

### 2.3 MyNetty ChannelHandlerContext上下文实现
下面我们来深入讲解ChannelHandlerContext上下文原理，看看一个具体的事件在pipeline的双向链表中的传播是如何实现的。
```java
public interface MyChannelHandlerContext extends MyChannelEventInvoker {

    MyNioChannel channel();

    MyChannelEventHandler handler();

    MyChannelPipeline getPipeline();

    MyNioEventLoop executor();
}
```
```java
public abstract class MyAbstractChannelHandlerContext implements MyChannelHandlerContext{

    private static final Logger logger = LoggerFactory.getLogger(MyAbstractChannelHandlerContext.class);

    private final MyChannelPipeline pipeline;

    private final int executionMask;

    /**
     * 双向链表前驱/后继节点
     * */
    private MyAbstractChannelHandlerContext prev;
    private MyAbstractChannelHandlerContext next;

    public MyAbstractChannelHandlerContext(MyChannelPipeline pipeline, Class<? extends MyChannelEventHandler> handlerClass) {
        this.pipeline = pipeline;

        this.executionMask = MyChannelHandlerMaskManager.mask(handlerClass);
    }

    @Override
    public MyNioChannel channel() {
        return pipeline.getChannel();
    }

    public MyAbstractChannelHandlerContext getPrev() {
        return prev;
    }

    public void setPrev(MyAbstractChannelHandlerContext prev) {
        this.prev = prev;
    }

    public MyAbstractChannelHandlerContext getNext() {
        return next;
    }

    public void setNext(MyAbstractChannelHandlerContext next) {
        this.next = next;
    }

    @Override
    public MyNioEventLoop executor() {
        return this.pipeline.getChannel().getMyNioEventLoop();
    }

    @Override
    public void fireChannelRead(Object msg) {
        // 找到当前链条下最近的一个支持channelRead方法的MyAbstractChannelHandlerContext（inbound事件，从前往后找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextInbound(MyChannelHandlerMaskManager.MASK_CHANNEL_READ);

        // 调用找到的那个ChannelHandlerContext其handler的channelRead方法
        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            invokeChannelRead(nextHandlerContext,msg);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                invokeChannelRead(nextHandlerContext,msg);
            });
        }
    }

    @Override
    public void fireExceptionCaught(Throwable cause) {
        // 找到当前链条下最近的一个支持exceptionCaught方法的MyAbstractChannelHandlerContext（inbound事件，从前往后找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextInbound(MyChannelHandlerMaskManager.MASK_EXCEPTION_CAUGHT);

        // 调用找到的那个ChannelHandlerContext其handler的exceptionCaught方法

        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            invokeExceptionCaught(nextHandlerContext,cause);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                invokeExceptionCaught(nextHandlerContext,cause);
            });
        }
    }

    @Override
    public void close() {
        // 找到当前链条下最近的一个支持close方法的MyAbstractChannelHandlerContext（outbound事件，从后往前找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextOutbound(MyChannelHandlerMaskManager.MASK_CLOSE);

        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            doClose(nextHandlerContext);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                doClose(nextHandlerContext);
            });
        }
    }

    private void doClose(MyAbstractChannelHandlerContext nextHandlerContext){
        try {
            nextHandlerContext.handler().close(nextHandlerContext);
        } catch (Throwable t) {
            logger.error("{} do close error!",nextHandlerContext,t);
        }
    }

    @Override
    public void write(Object msg) {
        // 找到当前链条下最近的一个支持write方法的MyAbstractChannelHandlerContext（outbound事件，从后往前找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextOutbound(MyChannelHandlerMaskManager.MASK_WRITE);

        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            doWrite(nextHandlerContext,msg);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                doWrite(nextHandlerContext,msg);
            });
        }
    }

    private void doWrite(MyAbstractChannelHandlerContext nextHandlerContext, Object msg) {
        try {
            nextHandlerContext.handler().write(nextHandlerContext,msg);
        } catch (Throwable t) {
            logger.error("{} do write error!",nextHandlerContext,t);
        }
    }

    @Override
    public MyChannelPipeline getPipeline() {
        return pipeline;
    }

    public static void invokeChannelRead(MyAbstractChannelHandlerContext next, Object msg) {
        try {
            next.handler().channelRead(next, msg);
        }catch (Throwable t){
            // 处理抛出的异常
            next.invokeExceptionCaught(t);
        }
    }

    public static void invokeExceptionCaught(MyAbstractChannelHandlerContext next, Throwable cause) {
        next.invokeExceptionCaught(cause);
    }

    private void invokeExceptionCaught(final Throwable cause) {
        try {
            this.handler().exceptionCaught(this, cause);
        } catch (Throwable error) {
            // 如果捕获异常的handler依然抛出了异常，则打印debug日志
            logger.error(
                "An exception {}" +
                    "was thrown by a user handler's exceptionCaught() " +
                    "method while handling the following exception:",
                ThrowableUtil.stackTraceToString(error), cause);
        }
    }

    private MyAbstractChannelHandlerContext findContextInbound(int mask) {
        MyAbstractChannelHandlerContext ctx = this;
        do {
            // inbound事件，从前往后找
            ctx = ctx.next;
        } while (needSkipContext(ctx, mask));

        return ctx;
    }

    private MyAbstractChannelHandlerContext findContextOutbound(int mask) {
        MyAbstractChannelHandlerContext ctx = this;
        do {
            // outbound事件，从后往前找
            ctx = ctx.prev;
        } while (needSkipContext(ctx, mask));

        return ctx;
    }

    private static boolean needSkipContext(MyAbstractChannelHandlerContext ctx, int mask) {
        // 如果与运算后为0，说明不支持对应掩码的操作，需要跳过
        return (ctx.executionMask & (mask)) == 0;
    }
}
```
```java
/**
 * pipeline的head哨兵节点
 * */
public class MyChannelPipelineHeadContext extends MyAbstractChannelHandlerContext implements MyChannelEventHandler {

    public MyChannelPipelineHeadContext(MyChannelPipeline pipeline) {
        super(pipeline,MyChannelPipelineHeadContext.class);
    }

    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) {
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(MyChannelHandlerContext ctx, Throwable cause) {
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void close(MyChannelHandlerContext ctx) throws Exception {
        // 调用jdk原生的channel，关闭掉连接
        ctx.getPipeline().getChannel().getJavaChannel().close();
    }

    @Override
    public void write(MyChannelHandlerContext ctx, Object msg) throws Exception {
        // 往外写的操作，一定是socketChannel
        SocketChannel socketChannel = (SocketChannel) ctx.getPipeline().getChannel().getJavaChannel();

        if(msg instanceof ByteBuffer){
            socketChannel.write((ByteBuffer) msg);
        }else{
            // msg走到head节点的时候，必须是ByteBuffer类型
            throw new Error();
        }
    }

    @Override
    public MyChannelEventHandler handler() {
        return this;
    }
}
```
```java
public abstract class MyAbstractChannelHandlerContext implements MyChannelHandlerContext{

    private static final Logger logger = LoggerFactory.getLogger(MyAbstractChannelHandlerContext.class);

    private final MyChannelPipeline pipeline;

    private final int executionMask;

    /**
     * 双向链表前驱/后继节点
     * */
    private MyAbstractChannelHandlerContext prev;
    private MyAbstractChannelHandlerContext next;

    public MyAbstractChannelHandlerContext(MyChannelPipeline pipeline, Class<? extends MyChannelEventHandler> handlerClass) {
        this.pipeline = pipeline;

        this.executionMask = MyChannelHandlerMaskManager.mask(handlerClass);
    }

    @Override
    public MyNioChannel channel() {
        return pipeline.getChannel();
    }

    public MyAbstractChannelHandlerContext getPrev() {
        return prev;
    }

    public void setPrev(MyAbstractChannelHandlerContext prev) {
        this.prev = prev;
    }

    public MyAbstractChannelHandlerContext getNext() {
        return next;
    }

    public void setNext(MyAbstractChannelHandlerContext next) {
        this.next = next;
    }

    @Override
    public MyNioEventLoop executor() {
        return this.pipeline.getChannel().getMyNioEventLoop();
    }

    @Override
    public void fireChannelRead(Object msg) {
        // 找到当前链条下最近的一个支持channelRead方法的MyAbstractChannelHandlerContext（inbound事件，从前往后找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextInbound(MyChannelHandlerMaskManager.MASK_CHANNEL_READ);

        // 调用找到的那个ChannelHandlerContext其handler的channelRead方法
        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            invokeChannelRead(nextHandlerContext,msg);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                invokeChannelRead(nextHandlerContext,msg);
            });
        }
    }

    @Override
    public void fireExceptionCaught(Throwable cause) {
        // 找到当前链条下最近的一个支持exceptionCaught方法的MyAbstractChannelHandlerContext（inbound事件，从前往后找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextInbound(MyChannelHandlerMaskManager.MASK_EXCEPTION_CAUGHT);

        // 调用找到的那个ChannelHandlerContext其handler的exceptionCaught方法

        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            invokeExceptionCaught(nextHandlerContext,cause);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                invokeExceptionCaught(nextHandlerContext,cause);
            });
        }
    }

    @Override
    public void close() {
        // 找到当前链条下最近的一个支持close方法的MyAbstractChannelHandlerContext（outbound事件，从后往前找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextOutbound(MyChannelHandlerMaskManager.MASK_CLOSE);

        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            doClose(nextHandlerContext);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                doClose(nextHandlerContext);
            });
        }
    }

    private void doClose(MyAbstractChannelHandlerContext nextHandlerContext){
        try {
            nextHandlerContext.handler().close(nextHandlerContext);
        } catch (Throwable t) {
            logger.error("{} do close error!",nextHandlerContext,t);
        }
    }

    @Override
    public void write(Object msg) {
        // 找到当前链条下最近的一个支持write方法的MyAbstractChannelHandlerContext（outbound事件，从后往前找）
        MyAbstractChannelHandlerContext nextHandlerContext = findContextOutbound(MyChannelHandlerMaskManager.MASK_WRITE);

        MyNioEventLoop myNioEventLoop = nextHandlerContext.executor();
        if(myNioEventLoop.inEventLoop()){
            doWrite(nextHandlerContext,msg);
        }else{
            // 防并发，每个针对channel的操作都由自己的eventLoop线程去执行
            myNioEventLoop.execute(()->{
                doWrite(nextHandlerContext,msg);
            });
        }
    }

    private void doWrite(MyAbstractChannelHandlerContext nextHandlerContext, Object msg) {
        try {
            nextHandlerContext.handler().write(nextHandlerContext,msg);
        } catch (Throwable t) {
            logger.error("{} do write error!",nextHandlerContext,t);
        }
    }

    @Override
    public MyChannelPipeline getPipeline() {
        return pipeline;
    }

    public static void invokeChannelRead(MyAbstractChannelHandlerContext next, Object msg) {
        try {
            next.handler().channelRead(next, msg);
        }catch (Throwable t){
            // 处理抛出的异常
            next.invokeExceptionCaught(t);
        }
    }

    public static void invokeExceptionCaught(MyAbstractChannelHandlerContext next, Throwable cause) {
        next.invokeExceptionCaught(cause);
    }

    private void invokeExceptionCaught(final Throwable cause) {
        try {
            this.handler().exceptionCaught(this, cause);
        } catch (Throwable error) {
            // 如果捕获异常的handler依然抛出了异常，则打印debug日志
            logger.error(
                "An exception {}" +
                    "was thrown by a user handler's exceptionCaught() " +
                    "method while handling the following exception:",
                ThrowableUtil.stackTraceToString(error), cause);
        }
    }

    private MyAbstractChannelHandlerContext findContextInbound(int mask) {
        MyAbstractChannelHandlerContext ctx = this;
        do {
            // inbound事件，从前往后找
            ctx = ctx.next;
        } while (needSkipContext(ctx, mask));

        return ctx;
    }

    private MyAbstractChannelHandlerContext findContextOutbound(int mask) {
        MyAbstractChannelHandlerContext ctx = this;
        do {
            // outbound事件，从后往前找
            ctx = ctx.prev;
        } while (needSkipContext(ctx, mask));

        return ctx;
    }

    private static boolean needSkipContext(MyAbstractChannelHandlerContext ctx, int mask) {
        // 如果与运算后为0，说明不支持对应掩码的操作，需要跳过
        return (ctx.executionMask & (mask)) == 0;
    }
}
```
* AbstractChannelHandlerContext作为ChannelHandlerContext子类的基础骨架，是理解Netty中IO事件传播机制的重中之重。  
  AbstractChannelHandlerContext做为ChannelPipeline的实际节点，其拥有了prev和next两个属性，用于关联链表中的前驱和后继。
* 在触发IO事件时，AbstractChannelHandlerContext会按照一定的规则(具体原理在下一节展开)找到下一个需要处理当前类型IO事件的事件处理器(findContextInbound、findContextInbound)。  
* 在找到后会先判断当前线程与目标MyAbstractChannelHandlerContext的执行器线程是否相同(inEventLoop)，如果是则直接触发对应handler的回调方法；如果不是则将当前事件包装成一个任务交给next节点的executor执行。  
  这样设计的主要原因是netty作为一个高性能网络框架，是非常忌讳使用同步锁的。EventLoop线程是按照引入taskQueue队列多写单读的方式消费IO事件以及相关任务的，这样可以避免处理IO事件时防止不同线程间并发而大量加锁。  
* 举个例子，一个聊天服务器，用户a通过连接A发送了一条消息给服务端，而服务端需要通过连接b将消息同步给用户b，连接a和连接b属于不同的EventLoop线程。  
  连接a所在的EventLoop在接受到读事件后，需要往连接b写出数据，此时不能直接由连接a的线程执行channel的写出操作(inEventLoop为false)，而必须通过execute方法写入taskQueue交给管理连接b的EventLoop线程，让它异步的处理。  
  试想如果能允许别的EventLoop线程来回调触发不属于它的channel的IO事件，那么所有的ChannelHandler都必须考虑多线程并发的问题，而导致性能大幅降低。
  
### 2.4 ChannelHandler mask掩码过滤机制
```java

```

```java
/**
 * 用于简化用户自定义的handler的适配器
 *
 * 由于所有支持的方法都加上了@Skip注解，子类只需要重写想要关注的方法即可，其它未重写的方法将会在事件传播时被跳过
 * */
public class MyChannelEventHandlerAdapter implements MyChannelEventHandler{

    /**
     * 当前是否已经被加入sharable缓存
     * */
    public volatile boolean added;

    @Skip
    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.fireChannelRead(msg);
    }

    @Skip
    @Override
    public void exceptionCaught(MyChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }

    @Skip
    @Override
    public void close(MyChannelHandlerContext ctx) throws Exception {
        ctx.close();
    }

    @Skip
    @Override
    public void write(MyChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.write(msg);
    }


    private static ConcurrentHashMap<Class<?>, Boolean> isSharableCacheMap = new ConcurrentHashMap<>();

    public boolean isSharable() {
        /**
         * MyNetty中直接用全局的ConcurrentHashMap来缓存handler类是否是sharable可共享的，实现起来很简单
         * 而netty中利用FastThreadLocal做了优化，避免了不同线程之间的锁争抢
         * 高并发下每分每秒都会创建大量的链接以及所属的Handler，优化后性能会有很大提升
         *
         * See <a href="https://github.com/netty/netty/issues/2289">#2289</a>.
         */
        Class<?> clazz = getClass();
        Boolean sharable = isSharableCacheMap.computeIfAbsent(
                clazz, k -> clazz.isAnnotationPresent(Sharable.class));
        return sharable;
    }
}
```
### 2.5 MyNettyBootstrap与新版本Echo服务器实现

## 总结



