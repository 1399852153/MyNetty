# 从零开始实现简易版Netty(三) MyNetty 高效的数据读取实现
## 1. MyNetty 数据读取处理优化
在上一篇博客中，lab2版本的MyNetty实现了基本的reactor模型和一个简易的pipeline流水线处理机制。  
目前的MyNetty就像一款手搓的拖拉机，勉强能晃晃悠悠的跑起来，但碰到一点极端的路况就会抛锚甚至散架。对于很多边界场景，现版本的MyNetty都无力应对。  
MyNetty的目标是实现一个简易的netty，虽然不可能像Netty那般健壮，能丝滑的处理绝大多数极端场景，但也会对一些常见的场景进行针对性优化。  
#####
按照计划，本篇博客中，lab3版本的MyNetty需要实现高效的数据读取。由于本文属于系列博客，读者需要对之前的博客内容有所了解才能更好地理解本文内容。
* lab1版本博客：[从零开始实现简易版Netty(一) MyNetty Reactor模式](https://www.cnblogs.com/xiaoxiongcanguan/p/18939320)  
* lab2版本博客：[从零开始实现简易版Netty(二) MyNetty pipeline流水线](https://www.cnblogs.com/xiaoxiongcanguan/p/18964326)  
#####
在分析lab3的MyNetty源码实现之前，我们先来看看当前版本的MyNetty中对于IO读事件的处理主要有哪些问题。
##### lab2版本MyNetty读事件处理源码
```java
public class MyNioEventLoop implements Executor {
    // ... 省略无关代码 
    
    private void processReadEvent(SelectionKey key) throws Exception {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // 目前所有的attachment都是MyNioChannel
        MyNioSocketChannel myNioChannel = (MyNioSocketChannel) key.attachment();

        // 简单起见，buffer不缓存，每次读事件来都新创建一个
        // 暂时也不考虑黏包/拆包场景(Netty中靠ByteToMessageDecoder解决，后续再分析其原理)，理想的认为每个消息都小于1024，且每次读事件都只有一个消息
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);

        int byteRead = socketChannel.read(readBuffer);
        logger.info("processReadEvent byteRead={}", byteRead);
        if (byteRead == -1) {
            // 简单起见不考虑tcp半连接的情况，返回-1直接关掉连接
            socketChannel.close();

            key.cancel();
        } else {
            // 将缓冲区当前的limit设置为position=0，用于后续对缓冲区的读取操作
            readBuffer.flip();
            // 根据缓冲区可读字节数创建字节数组
            byte[] bytes = new byte[readBuffer.remaining()];
            // 将缓冲区可读字节数组复制到新建的数组中
            readBuffer.get(bytes);

            if (myNioChannel != null) {
                // 触发pipeline的读事件
                myNioChannel.getChannelPipeline().fireChannelRead(bytes);
            } else {
                logger.error("processReadEvent attachment myNioChannel is null!");
            }
        }
    }
}
```
#####
从上面的源码实现中可以看到，lab2版本的MyNetty在处理事件循环的读事件时，至少存在以下三个问题。  
1. 过于理想的假设每次读事件中消息的大小都小于1024个字节，实际超过时会出现各种问题。
2. 每次处理读事件都临时创建一个新的ByteBuffer，没有支持ByteBuffer的池化功能。
3. 没有提供方便的处理黏包、拆包场景的功能。
#####
这三个问题涉及到不同的方向，限于篇幅不会在一次迭代中全部优化。在本篇博客中，lab3版本的Netty主要优化第一个问题，剩下的两个问题的优化会在后续的迭代中逐步完成。  

## 2. MyNetty 高效的数据读取实现源码解析
前面提到，我们理想的假设事件循环中的读事件中，所读取到的消息总是小于1024个字节。但实际的场景中，channel中会读取到的数据可能会远远的多于1024字节。同时，在读取数据的同时，对端可能还在源源不断的发送数据包，往channel中写入数据(读不完，根本读不完)。  
此时，事件循环处理读事件时需要考虑以下两个问题。
1. 从channel中读取数据类似用一个水桶去水管中接水，channel水管中需要读取的数据远多于1024字节，那么用多大容量的ByteBuffer去接收数据会比较合适?  
   水桶如果小了，那么需要执行的读操作就会特别多，性能不好。水桶如果大了，就会占用较多内存，在多EventLoop的场景下，多个超大杯的ByteBuffer会对系统内存产生很大压力。
2. 一个EventLoop线程是IO多路复用的，如果其中某个channel需要读取的数据非常的多。EventLoop作为一个单线程的处理模型，不能无限制的在一个channel上反复读取数据处理，而要避免其它channel中的IO事件迟迟得不到处理而出现饥饿的问题。
#####
* 在netty中，针对第一个接受数据的buffer容器大小的问题，netty使用了一种启发式的算法来解决这个问题。简单来说，就是基于最近读取到的数据来动态调整下一次读取数据时的容器大小。  
  如果最近读取的数据超过了某个阈值(比如让容器装满了)，则对容器进行扩容，下一次就用更大容量的Buffer去接受数据；反之，如果最近读取的数据低于了某个阈值(比如读取时当前容器的一半都没有装满)，则动态的减少Buffer的容量。  
  通过这种启发式的算法，很好的解决了如何确定Buffer容器大小的问题。能在channel具有大流量时提升读取数据的效率，并在channel流量低峰期时减少对内存的占用。  
* 而对于第二个可能导致channel饥饿的问题，netty的做法也很简单，就是在单次事件循环的处理时，禁止在单个channel上无限制的读取数据。  
  举个例子，限制在处理一个读事件时，最多只能用Buffer容器读取接收16次数据。在可读数据很多时，即使可读的数据还没有完全读完，也要退出处理，转而去处理其它的IO事件或者task任务。至于还未读取完成的数据则留待下一次事件循环再处理，以保证事件循环中整体的公平性从而避免其它channel饥饿。
##### MyNetty高效的读源码实现
```java
public class MyNioSocketChannel extends MyNioChannel{
    // ... 省略无关代码 
    
    public void read(SelectionKey key) throws IOException {
       // receivedMessageLimiter相当于netty中简化版的不包含buffer类型分配的RecvByteBufAllocator(参考自AdaptiveRecvByteBufAllocator)
       // 新的一次read事件开始前，刷新下readLimiter的状态
       receivedMessageBytesLimiter.reset();
       do {
          int receiveBufferSize = receivedMessageBytesLimiter.getReceiveBufferSize();
          // 简单起见，buffer不缓存，每次读事件来都新创建一个
          // 暂时也不考虑黏包/拆包场景(Netty中靠ByteToMessageDecoder解决，后续再分析其原理)
          ByteBuffer readBuffer = ByteBuffer.allocate(receiveBufferSize);

          int byteRead = socketChannel.read(readBuffer);
          logger.info("processReadEvent byteRead={}", byteRead);

          // 记录下最近一次读取的字节数
          receivedMessageBytesLimiter.recordLastBytesRead(byteRead);
          if (byteRead < 0) {
             // 简单起见不考虑tcp半连接的情况，返回-1直接关掉连接
             socketChannel.close();
             // 取消key的监听
             key.cancel();
          } else if(byteRead == 0){
             // 当前读事件已经读取完毕了，退出循环
             break;
          } else {
             // 总消息读取次数+1
             receivedMessageBytesLimiter.incMessagesRead();

             // 将缓冲区当前的limit设置为position=0，用于后续对缓冲区的读取操作
             readBuffer.flip();
             // 根据缓冲区可读字节数创建字节数组
             byte[] bytes = new byte[readBuffer.remaining()];
             // 将缓冲区可读字节数组复制到新建的数组中
             readBuffer.get(bytes);

             // 触发pipeline的读取操作
             this.getChannelPipeline().fireChannelRead(bytes);
          }
       }while (receivedMessageBytesLimiter.canContinueReading());

       // 整理一下此次read事件读取的字节数，调整下一次read事件分配的bufferSize大小
       receivedMessageBytesLimiter.readComplete();

       // 一次read事件读取完成，触发一次readComplete的inbound事件
       this.getChannelPipeline().fireChannelReadComplete();
    }
}
```
```java
/**
 * 参考netty的AdaptiveRecvByteBufAllocator，用于限制读取某个channel的读事件时过多的占用IO线程
 * 保证事件循环中的不同channel事件处理的公平性
 * */
public class ReceivedMessageBytesLimiter {

    // Use an initial value that is bigger than the common MTU of 1500
    private static final int DEFAULT_INITIAL = 2048;

    private static final int INDEX_INCREMENT = 4;
    private static final int INDEX_DECREMENT = 1;

    private static final int[] SIZE_TABLE;

    static {
        List<Integer> sizeTable = new ArrayList<>();
        // 16-512区间，总大小较小，因此每一级保守的每级线性增加16
        for (int i = 16; i < 512; i += 16) {
            sizeTable.add(i);
        }

        // 512以上的区间，每一级都是上一级的两倍，最大为128MB(判断条件为i>0,每次翻倍后整型变量i最终会溢出为负数而终止循环)
        for (int i = 512; i > 0; i <<= 1) {
            sizeTable.add(i);
        }

        SIZE_TABLE = new int[sizeTable.size()];
        for (int i = 0; i < SIZE_TABLE.length; i ++) {
            SIZE_TABLE[i] = sizeTable.get(i);
        }
    }

    /**
     * 总共读取的消息数量
     * */
    private int totalMessages;

    /**
     * 总共读取过的字节数量
     * */
    private int totalBytesRead;

    /**
     * 缩容时多给一次机会的flag
     * */
    private boolean decreaseNow;

    /**
     * 上一次读取是否写满了所分配的buffer
     * */
    private boolean lastReadIsFull;

    private int index;
    private int receiveBufferSize;

    /**
     * 最多一次IO事件read多少次
     * */
    private final int maxMessagesPerRead = 16;

    public ReceivedMessageBytesLimiter() {
        this(DEFAULT_INITIAL);
    }

    public ReceivedMessageBytesLimiter(int receiveBufferSize) {
        if(receiveBufferSize <= 0) {
            receiveBufferSize = DEFAULT_INITIAL;
        }

        this.receiveBufferSize = receiveBufferSize;
    }

    public int getReceiveBufferSize(){
        return receiveBufferSize;
    }

    public void recordLastBytesRead(int bytes){
        // 如果单次接收的数据就已经把buffer装满了，及时的扩容，而不是等到readComplete的时候处理
        if(bytes == receiveBufferSize){
            lastReadIsFull = true;
            adjustNextReceiveBufferSize(bytes);
        }

        if(bytes > 0){
            this.totalBytesRead += bytes;
        }
    }

    public void incMessagesRead(){
        this.totalMessages += 1;
    }

    public boolean canContinueReading(){
        if(totalMessages >= maxMessagesPerRead){
            // 一次OP_READ事件读取数据的次数已经超过了限制，不用再读了，限制下
            return false;
        }

        if(!lastReadIsFull){
            // 上一次读取没有写满所分配的buffer，说明后面大概率没数据了
            return false;
        }

        return true;
    }

    public void readComplete(){
        adjustNextReceiveBufferSize(totalBytesRead);
    }

    public void reset(){
        this.totalMessages = 0;
        this.totalBytesRead = 0;
    }

    private void adjustNextReceiveBufferSize(int actualReadBytes){
        // 如果实际读取的bytes数量小于等于当前级别减1
        if (actualReadBytes <= SIZE_TABLE[max(0, index - INDEX_DECREMENT)]) {
            // 有两次机会，第一次低于，只修改decreaseNow的值(缩容较为保守)
            if (decreaseNow) {
                // 第二次还是低于，当前级别index降一级，缩容
                index = max(index - INDEX_DECREMENT, 1);
                receiveBufferSize = SIZE_TABLE[index];
                decreaseNow = false;
            } else {
                // 第一次低于，保守点，再给一次机会
                decreaseNow = true;
            }
        } else if (actualReadBytes >= receiveBufferSize) {
            // 如果实际读取的bytes数量大于等于下一次接收的buffer的大小，则当前等级升4级(扩容比较奔放)
            index = min(index + INDEX_INCREMENT, SIZE_TABLE.length-1);
            receiveBufferSize = SIZE_TABLE[index];
            decreaseNow = false;
        }
    }
}
```
#####
* netty的实现中在AbstractNioByteChannel使用RecvByteBufAllocator.Handle来限制读事件的(NioByteUnsafe)。而MyNetty中目前还没有实现ByteBuffer的池化能力，所以单独定义了一个类ReceivedMessageBytesLimiter，专门用于处理读事件，逻辑基本是参考Netty的。  
  netty中除了针对读事件做限制，针对accept连接事件的处理也同样做了限制，即限制Boss EventLoop无限制的接受新链接，同样要分出一部分执行时间给待处理的任务(NioMessageUnsafe)。
* 每个channel都拥有一个自己独有的ReceivedMessageBytesLimiter用于记录对应channel最近读取的字节数，用来动态的调整下一次读取数据时的ByteBuffer的大小。
* Netty为接收容器的大小设定了一系列的规格，其中绝对字节数偏小的规格密度较大，16-512的区间内，每16字节一个规格。因为大多数时候channel的流量都是较小的，精细的设计规格可以在扩容时不会过于激进而产生内部碎片，从而更高效的使用内存。  
  而在512字节以上的区间，则是按照2次幂来设置规格，netty的设计者认为一旦扩容到该区间时，说明流量确实很大，相比节约内存，尽快扩容到一个合适的大容量容器以更快的消费流量更重要。当然，netty也限制了读取容器的最大值为128M，避免单个channel就把内存耗尽。
* 在扩缩容调整容器大小时，扩容的策略比较激进，当发现容器可能不足以完全承载数据时，规格直接增加4级；而缩容时则保守的多，只有当连续两次都低于阈值时才将规格降低1级。
* MyNetty为MyChannelHandler新增了channelReadComplete这一IO事件，在一次读事件完成时，就会触发该事件。无论是因为数据已经被读取完，还是因为读取限制策略而主动的退出了读，都视作完成了当前IO读事件。

## 总结
* 在lab3中，MyNetty参考netty优化了读事件的处理逻辑，提高了读事件的处理效率的同时也允许读事件处理的字节数远高于1024这样一个固定数值。  
* 相对应的，现版本的MyNetty其实对写出操作也有隐藏的限制，即不允许写出的数据过大。而实际上，操作系统的网络缓冲区容量是有极限的，如果想要通过write方法一次性写出大量数据是会出现问题的。对写出操作的优化，将在MyNetty的lab4版本中完成。
#####
博客中展示的完整代码在我的github上：https://github.com/1399852153/MyNetty (release/lab3_efficient_read 分支)，内容如有错误，还请多多指教。