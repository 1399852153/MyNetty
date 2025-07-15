package com.my.netty.core.reactor.channel.buffer;

import com.my.netty.core.reactor.channel.MyNioChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

            // 当前需要flush的队列(flushedEntry为head)为空，调转指针，将unFlushed队列中的消息全部转为待flushedEntry
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
