package com.my.netty.bytebuffer;

import com.my.netty.bytebuffer.netty.MyByteBuf;
import com.my.netty.bytebuffer.netty.MyPooledHeapByteBuf;
import com.my.netty.bytebuffer.netty.allocator.MyPoolArena;
import com.my.netty.bytebuffer.netty.allocator.MyPooledByteBufAllocator;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.*;

public class MyPooledByteBufTest {

    @Test
    public void test() {
        MyPooledByteBufAllocator myPooledByteBufAllocator = new MyPooledByteBufAllocator();

        // normal
        MyByteBuf myByteBuf1 = testAllocate(myPooledByteBufAllocator,1024 * 32);
        MyByteBuf myByteBuf2 = testAllocate(myPooledByteBufAllocator,1024 * 32 * 2);

        // small
        MyByteBuf myByteBuf3 = testAllocate(myPooledByteBufAllocator,32);
        MyByteBuf myByteBuf4 = testAllocate(myPooledByteBufAllocator,32 * 2);

        Assert.assertTrue(myByteBuf1 instanceof MyPooledHeapByteBuf);
        Assert.assertTrue(myByteBuf2 instanceof MyPooledHeapByteBuf);
        Assert.assertTrue(myByteBuf3 instanceof MyPooledHeapByteBuf);
        Assert.assertTrue(myByteBuf4 instanceof MyPooledHeapByteBuf);

        int base1 = 0;  // myByteBuf1 0 - 32K
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base1], 1);
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base1+1], 1);
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base1+2], 0);

        int base2 = base1 + 1024 * 32; // myByteBuf2 32K - 96K
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base2-1], 0);
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base2], 1);
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base2+1], 1);
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base2+2], 0);

        int base3 = base2 + 1024 * 32 * 2; // myByteBuf3 96K + 8K(1个page) = 104K
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base3-1], 0);
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base3], 1);
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base3+1], 1);
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base3+2], 0);

        int base4 = base3 + 8192; // myByteBuf4 104K + 8K(1个page) = 112K
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base4-1], 0);
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base4], 1);
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base4+1], 1);
        Assert.assertEquals(((MyPooledHeapByteBuf) myByteBuf4).getMemory()[base4+2], 0);

        myByteBuf1.release();
        myByteBuf2.release();
        myByteBuf3.release();
        myByteBuf4.release();

        // huge
        MyByteBuf hugeBuf = testAllocate(myPooledByteBufAllocator, 1024 * 1024 * 4 + 1);
        hugeBuf.release();
    }

    @Test
    public void testMultiThreadAllocate() throws InterruptedException {
        MyPooledByteBufAllocator myPooledByteBufAllocator = new MyPooledByteBufAllocator();

        int taskNum = myPooledByteBufAllocator.getHeapArenas().length;
        ExecutorService executorService = Executors.newFixedThreadPool(taskNum);

        CountDownLatch countDownLatch = new CountDownLatch(taskNum);
        IdentityHashMap<MyPoolArena,MyPoolArena> poolArenaMap = new IdentityHashMap<>();
        for(int i=0; i<taskNum; i++){
            executorService.execute(()->{
                // normal
                MyByteBuf myByteBuf1 = testAllocate(myPooledByteBufAllocator,1024 * 32);

                // small
                MyByteBuf myByteBuf2 = testAllocate(myPooledByteBufAllocator,32 * 2);

                MyPoolArena<byte[]> arena1 = getPoolArena(myByteBuf1);
                MyPoolArena<byte[]> arena2 = getPoolArena(myByteBuf2);
                poolArenaMap.put(arena1,arena1);
                poolArenaMap.put(arena2,arena2);

                Assert.assertSame(arena1, arena2);

                countDownLatch.countDown();
            });
        }

        countDownLatch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(poolArenaMap.size() == taskNum);
    }

    @Test
    public void testWithCache() {
        MyPooledByteBufAllocator myPooledByteBufAllocator = new MyPooledByteBufAllocator(true);

        // normal
        MyByteBuf myByteBuf1 = testAllocate(myPooledByteBufAllocator,1024 * 32);
        myByteBuf1.release();

        MyByteBuf myByteBuf2 = testAllocate(myPooledByteBufAllocator,1024 * 32);
        myByteBuf2.release();

        // small
        MyByteBuf myByteBuf3 = testAllocate(myPooledByteBufAllocator,32);
        myByteBuf3.release();

        MyByteBuf myByteBuf4 = testAllocate(myPooledByteBufAllocator,32);
        myByteBuf4.release();
    }

    @Test
    public void testBatchAllocate(){
        MyPooledByteBufAllocator myPooledByteBufAllocator = new MyPooledByteBufAllocator();

        for(int i=0; i<1000; i++){
            List<MyByteBuf> myByteBufList = new ArrayList<>();
            for(int j=0; j<1000; j++){
                ThreadLocalRandom random = ThreadLocalRandom.current();
                MyByteBuf byteBuf = myPooledByteBufAllocator.heapBuffer(random.nextInt(15,1024 * 64));
                myByteBufList.add(byteBuf);
            }

            for(MyByteBuf myByteBuf : myByteBufList){
                myByteBuf.release();
            }
        }
    }

    private static MyPoolArena<byte[]> getPoolArena(MyByteBuf myByteBuf){
        return ((MyPooledHeapByteBuf) myByteBuf).getChunk().getArena();
    }

    private static MyByteBuf testAllocate(MyPooledByteBufAllocator myPooledByteBufAllocator, int bytes){
        MyByteBuf myByteBuf1 = myPooledByteBufAllocator.heapBuffer(bytes,bytes * 10);
        System.out.println("myByteBuf1 writableBytes=" + myByteBuf1.writableBytes());
        myByteBuf1.writeBoolean(true);
        myByteBuf1.writeBoolean(true);
        System.out.println("myByteBuf1 writableBytes=" + myByteBuf1.writableBytes());
        System.out.println(myByteBuf1.readBoolean());
        System.out.println(myByteBuf1.readBoolean());
        System.out.println("myByteBuf1 writableBytes=" + myByteBuf1.writableBytes());
        return myByteBuf1;
    }
}
