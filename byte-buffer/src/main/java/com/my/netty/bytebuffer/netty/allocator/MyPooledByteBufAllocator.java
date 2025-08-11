package com.my.netty.bytebuffer.netty.allocator;


import com.my.netty.bytebuffer.netty.MyByteBuf;

public class MyPooledByteBufAllocator extends MyAbstractByteBufAllocator{

    private final MyPoolArena<byte[]>[] heapArenas;

    public MyPooledByteBufAllocator() {
        // 简单起见，Arena数量写死为1方便测试，后续支持与线程绑定后再拓展为与处理器数量挂钩
        int arenasNum = 1;

        // 初始化好heapArena数组
        heapArenas = new MyPoolArena.HeapArena[arenasNum];
        for (int i = 0; i < heapArenas.length; i ++) {
            MyPoolArena.HeapArena arena = new MyPoolArena.HeapArena(this);
            heapArenas[i] = arena;
        }
    }

    @Override
    protected MyByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        // 简单起见，Arena数量写死为1方便测试，后续支持与线程绑定后再拓展为与处理器数量挂钩
        MyPoolArena<byte[]> targetArena = heapArenas[0];

        return targetArena.allocate(initialCapacity, maxCapacity);
    }
}
