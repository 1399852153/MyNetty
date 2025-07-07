package com.my.netty.core.reactor.limiter;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;

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
