# 从零开始实现简易版Netty(四) MyNetty 高效的数据写出实现
## 1. MyNetty 数据写出处理优化
在上一篇博客中，lab3版本的MyNetty对事件循环中的IO读事件处理做了一定的优化，解决了之前版本无法进行大数据量读取的问题。  
按照计划，本篇博客中，lab4版本的MyNetty需要实现高效的数据写出。由于本文属于系列博客，读者需要对之前的博客内容有所了解才能更好地理解本文内容。
* lab1版本博客：[从零开始实现简易版Netty(一) MyNetty Reactor模式](https://www.cnblogs.com/xiaoxiongcanguan/p/18939320)
* lab2版本博客：[从零开始实现简易版Netty(二) MyNetty pipeline流水线](https://www.cnblogs.com/xiaoxiongcanguan/p/18964326)  
* lab3版本博客：[从零开始实现简易版Netty(三) MyNetty 高效的数据读取实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18979699)

#####
目前版本的实现中，MyNetty允许用户在自定义的ChannelHandler中使用ChannelHandlerContext的write方法向远端写出消息。  
write操作被视为一个出站事件会一直从后往前传播到head节点，在pipeline流水线的head节点中通过jdk的socketChannel.write方法将消息写出。
##### demo中的EchoServerEventHandler实现
```java
public class EchoServerEventHandler extends MyChannelEventHandlerAdapter {
    // 已省略无关逻辑
    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) {
        // 已经decoder过了
        String receivedStr = (String) msg;
        logger.info("echo server received message:{}, channel={}", receivedStr, ctx.channel());
        // 读完了，echo服务器准备回写数据到客户端
        String echoMessage = "server echo:" + receivedStr;
        ctx.write(echoMessage);
    }
}    
```
#####
面对大量的待读取消息，Netty针对性的实现了如启发式算法动态调节buffer容器大小、限制单channel读事件中读取消息的次数以避免其它channel饥饿等机制。  
同样的，如果需要短时间内向外写出大量的消息时，目前版本MyNetty中简单的socketChannel.write来实现写出功能是远远不够的，极端场景下同样存在很多问题。  
1. 操作系统向外发送数据的缓冲区是有限的，当网络拥塞、系统负载过高等场景时，通过socketChannel.write写出的数据并不能总是完全成功的写出，而是可能部分甚至全部都无法写出。  
   此时，未成功写出的数据需要由应用程序自己维护好，等待缓冲区空闲后进行重试。  
2. write方法的执行是异步的，事件循环线程a发出的消息可能实际上是由线程b来真正的写出，而线程b又不总是能一次成功的写出消息。
   MyNetty中线程a目前无法感知到write方法发送的消息是否被成功写出，同时也无法在缓冲区负载较高时，主动限制自己写出消息的速率而在源头实现限流。
3. 写出操作与读取操作一样，都是需要消耗cpu的，即使write方法写出的消息总是能一次性的成功发出，也不能让当前channel无限制的写出数据，而导致同处一个事件循环中的其它channel饥饿。
#####
因此在本篇博客中，lab4版本的MyNetty需要对写出操作进行改进，优化大量数据写出时的各种问题。

## 2. MyNetty 高效的数据写出实现源码解析
下面我们结合MyNetty的源码，分析一下netty是如何解决上述问题的。
1. 针对操作系统缓冲区有限，可能在网络拥塞等场景无法立即写出数据的问题，Netty内部为每一个Channel都设置了一个名为ChannelOutboundBuffer的应用层缓冲区。  
   通过write方法写出的消息数据，会先暂存在该缓冲区中。而只有通过flush/writeAndFlush方法触发flush操作时，才会实际的往channel的对端写出。  
   当所暂存的消息无法完全写出时，消息也不会丢失，而是会一直待在缓冲区内等待缓冲区空闲后重试写出(具体原理在后面解析)。
2. 针对write方法异步操作而无法令用户感知结果的问题，netty的write方法返回了一个Future对象，用户可以通过注册回调方法来感知当前所写出消息的结果。  
3. 针对用户无法感知当前系统负载情况而无法主动限流的问题，netty引入了高水位、低水位的概念。
   当channel对应的ChannelOutboundBuffer中待写出消息总大小高于某个阈值时，被认为处于高水位，负载较高，不能继续写入了；而待写出消息的总大小低于某个阈值时，被认为处于低水位，负载较低，可以继续写入了。  
   用户可以通过channel的isWritable主动的查询当前的负载状态(返回false代表负载较高，不可写)，也可以监听channelHandler的channelWritabilityChanged获取isWritable状态的转换结果。  
   基于isWritable，用户能够在负载较高时主动的调节自己发送消息的速率，避免待发送消息越写越多，最终导致OOM。
4. 针对单个channel内待写出消息过多而可能导致同一EventLoop内其它channel饥饿的问题，与避免读事件饥饿的机制一样，netty同样限制了一次完整的写出操作内可以写出的消息次数。  
   在写出次数超过阈值时，则立即终止当前channel的写出，转而处理其它事件/任务；当前channel内未写完的消息留待下一次事件循环中再接着处理。
##### MyNetty ChannelOutboundBuffer实现源码
```java
public class MyChannelOutboundBuffer {

    private static final Logger logger = LoggerFactory.getLogger(MyChannelOutboundBuffer.class);

    private static final int DEFAULT_LOW_WATER_MARK = 32 * 1024;
    private static final int DEFAULT_HIGH_WATER_MARK = 64 * 1024;

    private final MyNioChannel channel;

    private MyChannelOutBoundBufferEntry flushedEntry;

    private MyChannelOutBoundBufferEntry unFlushedEntry;

    private MyChannelOutBoundBufferEntry tailEntry;

    public int num;

    /**
     * 简单一点，这里直接用boolean类型
     * netty中用整型，是为了同时表达多个index下的unWritable语义(setUserDefinedWritability)
     * */
    private volatile boolean unWritable;

    /**
     * 当前outBoundBuffer
     * 目前只支持持有channel的IO线程更新，所以不设置为volatile
     * */
    private long totalPendingSize;

    /**
     * 一次写出操作的nioBuffer集合的大小总和
     * */
    private long nioBufferSize;

    // The number of flushed entries that are not written yet
    private int flushed;

    private static final ThreadLocal<ByteBuffer[]> NIO_BUFFERS = ThreadLocal.withInitial(() -> new ByteBuffer[1024]);

    public MyChannelOutboundBuffer(MyNioChannel channel) {
        this.channel = channel;
    }

    public void addMessage(ByteBuffer msg, int size, CompletableFuture<MyNioChannel> completableFuture) {
        // 每个msg对应一个链表中的entry对象
        MyChannelOutBoundBufferEntry entry = MyChannelOutBoundBufferEntry.newInstance(msg, size, completableFuture);
        if (tailEntry == null) {
            // 当前队列为空
            flushedEntry = null;
        } else {
            // 当前待flush的队列不为空，新节点追加到tail上
            MyChannelOutBoundBufferEntry tail = tailEntry;
            tail.next = entry;
        }
        tailEntry = entry;

        // 当前的unFlushed队列为空，当前消息作为头结点
        if (unFlushedEntry == null) {
            unFlushedEntry = entry;
        }

        // 加入新的msg后，当前outBoundBuffer所占用的总字节数自增
        incrementPendingOutboundBytes(entry.pendingSize);
    }

    public void addFlush() {
        // There is no need to process all entries if there was already a flush before and no new messages
        // where added in the meantime.
        //
        // See https://github.com/netty/netty/issues/2577
        MyChannelOutBoundBufferEntry entry = unFlushedEntry;
        if(entry == null) {
            // 触发flush操作，如果此时unFlushedEntry为null，说明没有需要flush的消息了，直接返回(针对重复的flush操作进行性能优化)
            return;
        }

        if (flushedEntry == null) {
            // there is no flushedEntry yet, so start with the entry

            // 当前需要flush的队列(flushedEntry为head)为空，调转指针，将unFlushed队列中的消息全部转为待flushedEntry(相当于重新取了一批数据来写出)
            flushedEntry = entry;
        }

        // 允许用户在flush之前取消掉此次写入,将待flush的节点最后检查一遍
        do {
            flushed ++;
            if (entry.completableFuture.isCancelled()) {
                // 设置cancel标志位，标识为不需要去写入
                entry.cancel();
                // 如果用户自己cancel了这次write操作，直接自减totalPendingSize即可
                decrementPendingOutboundBytes(entry.pendingSize);
            }
            entry = entry.next;
        } while (entry != null);

        // flush操作完毕后，unFlushed队列为空
        unFlushedEntry = null;
    }

    /**
     * 按照写出的字节数，将已经写完的byteBuffer清除掉。
     *
     * 由于缓冲区可能受限，socketChannel.write实际没有完整的写出一个byteBuffer，这种情况ByteBuffer的remaining实际上是减少了(写出了多少减多少)
     * */
    public void removeBytes(long totalWrittenBytes) {
        for (;;) {
            MyChannelOutBoundBufferEntry currentEntry = currentEntry();
            if(currentEntry == null){
                // 已flushed的节点都遍历完成了
                return;
            }

            final int readableBytes = currentEntry.msg.remaining();

            if (readableBytes == 0) {
                // 总共写出的bytes自减掉对应消息的大小
                totalWrittenBytes -= currentEntry.msgSize;

                // 完整的写出了一个byteBuffer，将其移除掉
                remove();
            } else {
                // readableBytes > writtenBytes
                // 发现一个未写完的ByteBuffer，不能移除，退出本次处理。等待下一次继续写出
                return;
            }
        }
    }

    /**
     * Will remove the current message, mark its as success and return {@code true}. If no
     * flushed message exists at the time this method is called it will return {@code false} to signal that no more
     * messages are ready to be handled.
     */
    public boolean remove() {
        MyChannelOutBoundBufferEntry entry = flushedEntry;
        if (entry == null) {
            return false;
        }

        CompletableFuture<MyNioChannel> completableFuture = entry.completableFuture;
        int size = entry.pendingSize;

        removeEntry(entry);

        if (!entry.cancelled) {
            // only release message, notify and decrement if it was not canceled before.

            // 写入操作flush成功，通知future
            try {
                completableFuture.complete(this.channel);
            }catch (Throwable ex) {
                logger.error("MyChannelOutboundBuffer notify write complete error! channel={}",this.channel,ex);
            }
            decrementPendingOutboundBytes(size);
        }

        return true;
    }

    /**
     * 参考netty的ChannelOutboundBuffer的nioByteBuffers方法，因为没有ByteBuf到ByteBuffer的转换，所以简单不少
     * */
    public List<ByteBuffer> nioByteBuffers(int maxCount, int maxBytes) {
        long totalNioBufferSize = 0;

        // 简单起见，需要处理的byteBuffer列表直接new出来，暂不考虑优化
        List<ByteBuffer> needWriteByteBufferList = new ArrayList<>();

        MyChannelOutBoundBufferEntry entry = flushedEntry;
        // 遍历队列中所有已经flush的节点
        while (isFlushedEntry(entry)){
            // 只处理未cancel的节点
            if(!entry.cancelled) {
                // 和netty不同，这里直接msg就是jdk的ByteBuffer，直接操作msg即可，不需要转换
                int readableBytes = entry.msg.remaining();
                // 只处理可读的消息，空msg忽略掉
                if (readableBytes > 0) {
                    // 判断一下是否需要将当前的msg进行写出，如果超出了maxBytes就留到下一次再处理
                    // 判断!byteBufferList.isEmpty的目的是避免一个超大的msg直接超过了maxBytes
                    // 如果是这种极端情况即byteBufferList.isEmpty，且 readableBytes > maxBytes,那也要尝试着进行写出
                    // 让底层的操作系统去尽可能的写入，不一定要一次写完，下次再进来就能继续写(readableBytes会变小)
                    if (maxBytes < totalNioBufferSize + readableBytes && !needWriteByteBufferList.isEmpty()) {
                        break;
                    }

                    // 总共要写出的bufferSize自增
                    totalNioBufferSize += readableBytes;

                    // 当前msg加入待写出的list中
                    needWriteByteBufferList.add(entry.msg);

                    if (needWriteByteBufferList.size() >= maxCount) {
                        // 限制一下一次写出最大的msg数量
                        break;
                    }
                }
            }
            // 遍历下一个节点
            entry = entry.next;
        }

        this.nioBufferSize = totalNioBufferSize;
        return needWriteByteBufferList;
    }

    public long getNioBufferSize() {
        return nioBufferSize;
    }

    public boolean isWritable() {
        return !unWritable;
    }

    public boolean isEmpty() {
        return flushed == 0;
    }

    private void removeEntry(MyChannelOutBoundBufferEntry e) {
        // 已flush队列头节点出队，队列长度自减1，
        flushed--;
        if (flushed == 0) {
            // 当前已flush的队列里的消息已经全部写出完毕，将整个队列整理一下

            // 首先已flush队列清空
            flushedEntry = null;
            if (e == tailEntry) {
                // 如果当前写出的是队列里的最后一个entry，说明所有的消息都写完了，整个队列清空
                tailEntry = null;
                unFlushedEntry = null;
            }
        } else {
            // 当前已flush的队列里还有剩余的消息待写出，已flush队列的头部出队，队列头部指向下一个待写出节点
            flushedEntry = e.next;
        }
    }

    private boolean isFlushedEntry(MyChannelOutBoundBufferEntry e) {
        return e != null && e != unFlushedEntry;
    }

    /**
     * Return the current message to write or {@code null}
     * if nothing was flushed before and so is ready to be written.
     */
    public MyChannelOutBoundBufferEntry currentEntry() {
        MyChannelOutBoundBufferEntry entry = flushedEntry;
        if (entry == null) {
            return null;
        }

        return entry;
    }

    private void incrementPendingOutboundBytes(long size) {
        if (size == 0) {
            return;
        }

        this.totalPendingSize += size;
        if (totalPendingSize > DEFAULT_HIGH_WATER_MARK) {
            // 超过了所配置的高水位线，标识设置为不可写
            this.unWritable = true;
        }
    }

    private void decrementPendingOutboundBytes(long size) {
        if (size == 0) {
            return;
        }

        this.totalPendingSize -= size;
        if (totalPendingSize < DEFAULT_LOW_WATER_MARK) {
            // 低于了所配置的低水位线，标识设置为可写
            this.unWritable = false;
        }
    }
}
```
```java
class MyChannelOutBoundBufferEntry {

    // Assuming a 64-bit JVM:
    //  - 16 bytes object header
    //  - 6 reference fields
    //  - 2 long fields
    //  - 2 int fields
    //  - 1 boolean field
    //  - padding
    //  netty中ChannelOutboundBuffer的Entry对象属性较多，16 + 6*8 + 2*8 + 2*4 + 1 = 89
    //  往大了计算，未开启指针压缩时，64位机器按照8的倍数向上取整，算出填充默认需要96字节(netty中可通过系统参数(io.netty.transport.outboundBufferEntrySizeOverhead)动态配置)
    //  MyNetty做了简化，暂时没那么属性，但这里就不改了，只是多浪费了一些空间
    //  详细的计算方式可参考大佬的博客：https://www.cnblogs.com/binlovetech/p/16453634.html
    private static final int DEFAULT_CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD = 96;

    MyChannelOutBoundBufferEntry next;
    ByteBuffer msg;
    CompletableFuture<MyNioChannel> completableFuture;
    int msgSize;
    int pendingSize;
    boolean cancelled;

    static MyChannelOutBoundBufferEntry newInstance(ByteBuffer msg, int msgSize, CompletableFuture<MyNioChannel> completableFuture) {
        // 简单起见，暂时不使用对象池，直接new
        MyChannelOutBoundBufferEntry entry = new MyChannelOutBoundBufferEntry();
        entry.msg = msg;
        entry.msgSize = msgSize;
        // entry实际的大小 = 消息体的大小 + 对象头以及各个属性值占用的大小
        entry.pendingSize = msgSize + DEFAULT_CHANNEL_OUTBOUND_BUFFER_ENTRY_OVERHEAD;
        entry.completableFuture = completableFuture;
        return entry;
    }

    void cancel() {
        if (!cancelled) {
            cancelled = true;
        }
    }
}
```

#####
