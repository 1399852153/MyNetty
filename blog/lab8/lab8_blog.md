# 从零开始实现简易版Netty(八) MyNetty 实现Small规格的池化内存分配
## 1. Netty Small规格池化内存分配介绍
在上一篇博客中，lab7版本的MyNetty中实现了PooledByteBuf对象的池化，以及Normal规格的池化内存管理，并结合jemalloc的论文详细分析了其背后的设计理念。  
按照计划，lab8版本的MyNetty需要实现Small规格的内存分配。  
由于本文属于系列博客，读者需要对之前的博客内容有所了解才能更好地理解本文内容。
* lab1版本博客：[从零开始实现简易版Netty(一) MyNetty Reactor模式](https://www.cnblogs.com/xiaoxiongcanguan/p/18939320)
* lab2版本博客：[从零开始实现简易版Netty(二) MyNetty pipeline流水线](https://www.cnblogs.com/xiaoxiongcanguan/p/18964326)
* lab3版本博客：[从零开始实现简易版Netty(三) MyNetty 高效的数据读取实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18979699)
* lab4版本博客：[从零开始实现简易版Netty(四) MyNetty 高效的数据写出实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18992091)
* lab5版本博客：[从零开始实现简易版Netty(五) MyNetty FastThreadLocal实现](https://www.cnblogs.com/xiaoxiongcanguan/p/19005381)
* lab6版本博客：[从零开始实现简易版Netty(六) MyNetty ByteBuf实现](https://www.cnblogs.com/xiaoxiongcanguan/p/19029215)
* lab7版本博客：[从零开始实现简易版Netty(七) MyNetty 实现Normal规格的池化内存分配](https://www.cnblogs.com/xiaoxiongcanguan/p/19084677)
#####
书接上文，在lab7的博客中我们已经知道，在Netty中Normal规格的池化内存分配是参考自操作系统内核的伙伴算法实现的。  
通过将内存块视为一批大小相同的页，在分配时按照所申请的大小将1至N个连续的内存页逻辑上组合成一个内存段以满足需求，并在回收时尽可能的将此前因为分配而被切割的、彼此直接相邻的小内存段合并为更大、更完整的内存段。  
### Small规格的内存分配，使用伙伴算法是否合适？
在开始分析Small规格池化内存分配的实现细节之前，先思考一个问题：对于Small规格的内存分配，延用伙伴算法是否依然合适？伙伴算法的优缺点分别是什么呢？ 
##### 伙伴算法的优缺点
* 优点：以页为基础单位进行分配，相比于实际所申请的大小(规范化之前的大小)，对于Normal规格中较小级别的申请所浪费的内部碎片，最多不会超过1页(比如极端情况实际申请3页 + 1b，最终分配4页)。而对于Normal规格中较大规格的申请，平均所浪费的内部碎片占所申请的大小比例依然较小。  
* 缺点：分配时对连续内存段的拆分以及释放时对连续内存段的合并操作，逻辑较为复杂，存在一定的时间开销和锁同步开销。同时，runAvail数组等维护信息的元数据占用了一定的空间，是为外部碎片。  
##### 使用伙伴算法实现Small规格内存分配的缺点
Normal规格的阈值为8Kb，这意味着Small规格的内存在绝大多数应用中其申请的次数会远多于Normal规格。  
如果使用伙伴算法，以16b这一最小分配级别作为管理单元，在面对高出几个数量级的分配/回收申请下，对连续内存段的拆分以及合并操作的开销对吞吐量的影响会被指数级放大。  
同时，runAvail数组等元数据也会因为Small规格的大小排布过密，相比Normal规格，所占用的空间也会异常的多。  
于此同时，使用伙伴算法实现8KB以下，且大多数都是16b、32b这种极小级别的内存申请，其节约的内部碎片也小的可怜(实际申请17b，最终分配32b，在最极端的情况下最多也就节约15b的内部空间)。
#####
可以发现，如果使用伙伴算法实现Small规格内存分配，其性能开销、锁的同步开销都大的惊人，同时对于单次分配所节约出来的内部碎片，甚至不足以抵消其对应追踪元数据所额外占用的空间。  
因此Netty参考操作系统内核，针对申请的容量相对更小、申请更加频繁的Small规格池化内存分配，不使用伙伴算法，而是在Normal规格分配能力的基础上采用slab算法实现Small规格的池化内存分配管理功能。

## 2. MyNetty Small规格池化内存分配实现
下面我们开始展开分析Netty的Small规格池化内存分配的实现原理。
### 2.1 Slab算法介绍
slab算法是操作系统内核中专门用于小对象分配的算法。slab算法最核心的设计思想是缓存对象池。  
* 通过为相同大小的对象预先创建对应的对象池，对象池通常由伙伴算法中所管理的连续内存页段组成。与伙伴算法中将Chunk切割为一个个相同的页，对象池将对应的连续内存段切割为N个相同大小的对象槽以供分配。  
* 当申请对应大小的内存进行分配时，直接将对象池中的某一个槽对应的内存分配出去，并在元数据中将对应的槽从空闲状态标记为已使用；而在回收内存时，则简单的将之前所分配出去的对象槽还原为空闲态。  
  就像一个装网球的盒子，里面有N个网球整齐的排布，当用户想要用球时(分配内存)，就从盒子中拿一个分配给用户，当用户使用完后(释放内存)，就放回到之前对应的格子中(不能错放)。
* 一个对象池能够缓存的对象数量是有限的，因此用于某一特定大小级别的对象池可以有多个。对象池有三种状态，完全空闲(装满了网球的盒子)，部分空闲(部分网球已被分配的盒子)和已满(空盒子)。    
  申请内存分配时，优先从部分空闲状态的slab对象池中分配；并且在内存吃紧时自动的回收掉完全空闲，无人使用的对象池。随着slab对象的不断申请和回收，对象池也会在这几种状态中不断地变化。
#####
![img.png](img.png)
#####
相比伙伴算法，slab算法的优缺点同样明显。  
* 优点：进行内存的分配与释放时，无需考虑内存块的拆分与拼接，直接修改对应槽的状态即可，时间性能非常好，修改元数据时需要防并发处理的临界区也很小。  
  因为对象槽的大小与对应的申请的规格完全匹配，不会出现因为空闲内存不连续而导致较大级别内存申请无法分配的问题。  
* 缺点：每一种大小级别都需要单独的创建对象池，在大小级别设置较多的场景中，会创建大量的对象池。  
  由于对象池是预先分配的设计，在对象池刚被创建，或对象池使用率不高的场景中，预先缓存却不被实际被分配的对象槽会浪费大量的空间。  
可以看到，slab算法相比伙伴算法，其时间复杂度更优，但空间复杂度较差。
但slab算法空间复杂度较差的问题，在所缓存对象槽很小的场景下，问题并不严重。因此slab算法作为小对象的内存分配管理算法时，能够做到扬长避短，只需浪费少量的内存空间，便可非常高效的完成小对象内存的分配与回收。  

##### 2.2 Netty Small规格内存分配功能入口
与Normal规格的内存分配的入口一样，Small规格的内存分配入口同样是PoolArena的allocate方法，唯一的区别在于所申请的实际大小。在被SizeClasses规范化计算后，如果被判定为是较小的Small规格的内存分配，则会执行tcacheAllocateSmall方法。  
* tcacheAllocateSmall使用到了PoolArena中一个关键的成员属性poolSubPages数组。
  PoolSubPages数组的大小与SizeClasses中规范化后的Small规格大小的数量相同(即16b，32b，... 24kb，28kb等规格大小)，每一个Small规格的大小都对应一个PoolSubPage链表，该链表可以将其看做是对应规格的PoolSubPage的对象池(PoolSubPage是Small规格分配的核心数据结构，在下一节再分析)。
* 在进行分配时，基于规范化后的规格，去PoolSubPages中找到对应的链表，检查其中是否有可用的PoolSubPage。  
  数组中的PoolSubPage链表的头节点是默认存在的哨兵节点，如果发现head.next==head，则说明链表实际是空的，因此需要新创建一个空的PoolSubPage用于分配。  
  与内核中使用伙伴算法为slab算法分配连续内存段一样，Netty中也通过Normal规格分配来为Small规格的PoolSubPage分配其底层的连续内存段。  
  而如果PoolSubPage链表不为空，则直接从中取出逻辑头结点的PoolSubPage(head.next)进行Small规格的分配。
* 用于分配的PoolSubPage在被创建出来后，便会被挂载到对应规格的PoolSubPage链表中，当PoolSubPage已满或者因为内存释放而完全空闲时，会从PoolSubPage链表中被摘除。  
  特别的，当PoolSubPage链表中存在新节点后，后续将至少保证链表中至少存在一个可用的PoolSubPage节点，即使该节点是完全空闲状态也不会被回收掉。具体的细节会在PoolSubPage的分析环节中结合源码展开讲解。
#####
```java
public abstract class MyPoolArena<T> {
	// ...... 已省略无关逻辑

    final MyPooledByteBufAllocator parent;
    final MySizeClasses mySizeClasses;
    private final MyPoolSubPage<T>[] myPoolSubPages;
    private final ReentrantLock lock = new ReentrantLock();

    public MyPoolArena(MyPooledByteBufAllocator parent) {
        // ...... 
        
        // 初始化用于small类型分配的SubPage双向链表head节点集合，每一个subPage的规格都会有一个对应的双向链表
        // 参考linux内核的slab分配算法，对于小对象来说，区别于伙伴算法的拆分/合并，而是直接将申请的内存大小规范化后，将相同规格的内存块同一管理起来
        // 当需要分配某个规格的小内存时，直接去对应的SubPage链表中找到一个可用的分片，直接进行分配
        // 不需要和normal那样在分配时拆分，释放时合并；虽然会浪费一些内存空间(内部碎片)，但因为只适用于small的小内存分配所以浪费的量很少
        // 同时small类型的分配的场景又是远高于normal的，以空间换时间(大幅提高分配速度，但只浪费了少量的内存)
        int nSubPages = this.mySizeClasses.getNSubPage();
        this.myPoolSubPages = new MyPoolSubPage[nSubPages];
        for(int i=0; i<nSubPages; i++){
            MyPoolSubPage<T> myPoolSubPageItem = new MyPoolSubPage<>(i);
            // 初始化时，令每一个PoolSubPage的头结点单独构成一个双向链表(头尾指针都指向自己)
            myPoolSubPageItem.prev = myPoolSubPageItem;
            myPoolSubPageItem.next = myPoolSubPageItem;
            this.myPoolSubPages[i] = myPoolSubPageItem;
        }

       // ......
    }

    /**
     * 从当前PoolArena中申请分配内存，并将其包装成一个PooledByteBuf返回
     * */
    MyPooledByteBuf<T> allocate(int reqCapacity, int maxCapacity) {
        // 从对象池中获取缓存的PooledByteBuf对象
        MyPooledByteBuf<T> buf = newByteBuf(maxCapacity);
        // 为其分配底层数组对应的内存
        allocate(buf, reqCapacity);
        return buf;
    }

    private void allocate(MyPooledByteBuf<T> buf, int reqCapacity) {
        MySizeClassesMetadataItem sizeClassesMetadataItem = mySizeClasses.size2SizeIdx(reqCapacity);
        switch (sizeClassesMetadataItem.getSizeClassEnum()){
            case SMALL:
                // small规格内存分配
                tcacheAllocateSmall(buf, reqCapacity, sizeClassesMetadataItem);
                return;
            case NORMAL:
                // normal规格内存分配
                tcacheAllocateNormal(buf, reqCapacity, sizeClassesMetadataItem);
                return;
            case HUGE:
                // 超过了PoolChunk大小的内存分配就是Huge级别的申请，每次分配使用单独的非池化的新PoolChunk来承载
                allocateHuge(buf, reqCapacity);
        }
    }

    private void tcacheAllocateSmall(MyPooledByteBuf<T> buf, final int reqCapacity, final MySizeClassesMetadataItem sizeClassesMetadataItem) {
        MyPoolSubPage<T> head = this.myPoolSubPages[sizeClassesMetadataItem.getTableIndex()];
        boolean needsNormalAllocation;
        head.lock();
        try {
            final MyPoolSubPage<T> s = head.next;
            // 如果head.next = head自己，说明当前规格下可供分配的PoolSubPage内存段不存在，需要新分配一个内存段(needsNormalAllocation=true)
            needsNormalAllocation = (s == head);
            if (!needsNormalAllocation) {
                // 走到这里，head节点下挂载了至少一个可供当前规格分配的使用的PoolSubPage，直接调用其allocate方法进行分配
                long handle = s.allocate();
                // 分配好，将对应的handle与buf进行绑定
                s.chunk.initBufWithSubpage(buf, null, handle, reqCapacity);
            }
        } finally {
            head.unlock();
        }

        // 需要申请一个新的run来进行small类型的subPage分配
        if (needsNormalAllocation) {
            lock();
            try {
                allocateNormal(buf, reqCapacity, sizeClassesMetadataItem);
            } finally {
                unlock();
            }
        }
    }

    MyPoolSubPage<T> findSubpagePoolHead(int sizeIdx) {
        return myPoolSubPages[sizeIdx];
    }

    private void allocateNormal(MyPooledByteBuf<T> buf, int reqCapacity, MySizeClassesMetadataItem sizeIdx) {
        // 优先从050的PoolChunkList开始尝试分配，尽可能的复用已经使用较充分的PoolChunk。如果分配失败，就尝试另一个区间内的PoolChunk
        // 分配成功则直接return快速返回
        if (q050.allocate(buf, reqCapacity, sizeIdx)){
            return;
        }
        if (q025.allocate(buf, reqCapacity, sizeIdx)){
            return;
        }
        if (q000.allocate(buf, reqCapacity, sizeIdx)){
            return;
        }
        if (qInit.allocate(buf, reqCapacity, sizeIdx)){
            return;
        }
        if (q075.allocate(buf, reqCapacity, sizeIdx)){
            return;
        }

        // 所有的PoolChunkList都尝试过了一遍，都没能分配成功，说明已经被创建出来的，所有有剩余空间的PoolChunk空间都不够了(或者最初阶段还没有创建任何一个PoolChunk)

        // MyNetty对sizeClass做了简化，里面的规格都是写死的，所以直接从sizeClass里取
        int pageSize = this.mySizeClasses.getPageSize();
        int nPSizes = this.mySizeClasses.getNPageSizes();
        int pageShifts = this.mySizeClasses.getPageShifts();
        int chunkSize = this.mySizeClasses.getChunkSize();

        // 创建一个新的PoolChunk，用来进行本次内存分配
        MyPoolChunk<T> c = newChunk(pageSize, nPSizes, pageShifts, chunkSize);
        c.allocate(buf, reqCapacity, sizeIdx);
        // 新创建的PoolChunk首先加入qInit(可能使用率较高，add方法里会去移动到合适的PoolChunkList中(nextList.add))
        qInit.add(c);
    }
}
```
#####
```java
/**
 * 内存分配chunk
 * */
public class MyPoolChunk<T> {

    /**
     * manage all subpages in this chunk
     */
    private final MyPoolSubPage<T>[] subpages;

    /**
     * 用于small和normal类型分配的构造函数 unpooled为false，需要池化
     * */
    public MyPoolChunk(MyPoolArena<T> arena, Object base, T memory, int pageSize, int pageShifts, int chunkSize, int maxPageIdx) {
        
		// PoolSubPage数组，用于管理PoolSubPage
        this.subpages = new MyPoolSubPage[totalPages];

		// ......
    }

    boolean allocate(MyPooledByteBuf<T> buf, int reqCapacity, MySizeClassesMetadataItem mySizeClassesMetadataItem) {
        long handle;
        if (mySizeClassesMetadataItem.getSizeClassEnum() == SizeClassEnum.SMALL) {
            // small规格分配
            handle = allocateSubpage(mySizeClassesMetadataItem);
            if (handle < 0) {
                // 如果handle为-1，说明当前的Chunk分配失败，返回false
                return false;
            }
        } else {
            // 除了Small就只可能是Normal，huge的不去池化，进不来
            // runSize must be multiple of pageSize(normal类型分配的连续内存段的大小必须是pageSize的整数倍)
            int runSize = mySizeClassesMetadataItem.getSize();
            handle = allocateRun(runSize);
            if (handle < 0) {
                // 如果handle为-1，说明当前的Chunk分配失败，返回false
                return false;
            }
        }

        // 分配成功，将这个空的buf对象进行初始化
        initBuf(buf,null,handle,reqCapacity);
        return true;
    }

    void initBuf(MyPooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity) {
        if (isSubpage(handle)) {
            initBufWithSubpage(buf, nioBuffer, handle, reqCapacity);
        } else {
            int maxLength = runSize(pageShifts, handle);
            buf.init(this, nioBuffer, handle, runOffset(handle) << pageShifts,
                reqCapacity, maxLength);
        }
    }

    void initBufWithSubpage(MyPooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity) {
        int runOffset = runOffset(handle);
        int bitmapIdx = bitmapIdx(handle);

        MyPoolSubPage<T> s = subpages[runOffset];

        int offset = (runOffset << pageShifts) + bitmapIdx * s.elemSize;
        buf.init(this, nioBuffer, handle, offset, reqCapacity, s.elemSize);
    }


    /**
     * Create / initialize a new PoolSubpage of normCapacity. Any PoolSubpage created / initialized here is added to
     * subpage pool in the PoolArena that owns this PoolChunk
     */
    private long allocateSubpage(MySizeClassesMetadataItem sizeClassesMetadataItem) {
        MyPoolSubPage<T> head = arena.findSubpagePoolHead(sizeClassesMetadataItem.getTableIndex());
        // 对头结点上锁，保证当前规格的small内存分配不会出现并发(锁的粒度正好)
        head.lock();
        try {
            // 计算出为当前规格small类型内存分配所需要申请的PoolSubPage大小
            int runSize = calculateRunSize(sizeClassesMetadataItem);
            // 根据计算出的内存段规格，尝试从该PoolChunk划分出一块run内存段出来以供分配
            long runHandle = allocateRun(runSize);
            if (runHandle < 0) {
                // 内存不足，分配不出来对应大小的规格，返回-1代表分配失败
                return -1;
            }

            int runOffset = runOffset(runHandle);
            int elemSize = sizeClassesMetadataItem.getSize();

            MyPoolSubPage<T> subpage = new MyPoolSubPage<>(head, this, pageShifts, runOffset,
                runSize(pageShifts, runHandle), elemSize);

            // 记录一下用于分配PoolSubPage内存段的偏移量，等释放内存的时候能根据handle中记录的offset快速的找到所对应的PoolSubPage
            subpages[runOffset] = subpage;
            // 分配一个small类型的内存段出去(以handle的形式)
            return subpage.allocate();
        } finally {
            head.unlock();
        }
    }

    /**
     * 计算出为当前规格small类型内存分配所需要申请的PoolSubPage大小
     * */
    private int calculateRunSize(MySizeClassesMetadataItem sizeClassesMetadataItem) {
        // 一页是8K，最小的规格是16b，所以最大的元素个数maxElements为8K/16b
        int maxElements = 1 << (pageShifts - MySizeClasses.LOG2_QUANTUM);
        int runSize = 0;
        int nElements;

        final int elemSize = sizeClassesMetadataItem.getSize();

        // 获得pageSize和elemSize的最小公倍数
        // 首先，PoolChunk中的run内存段是以Page为单位进行分配的，所以分配出去的PoolSubPage大小一定要是Page的整数倍
        // 而pageSize和elemSize的最小公倍数，在其期望上可以减少内部内存碎片。极端情况下可能不是最优策略，但是总体上来说是最节约空间的
        // 举个例子如果整个运行周期就只申请了一次1280字节的规格，那么最小公倍数的策略(8192 * 5 = 40960)就不如直接分配一个1页大小的节约空间，但这毕竟是极端情况
        do {
            runSize += pageSize;
            nElements = runSize / elemSize;
        } while (nElements < maxElements && runSize != nElements * elemSize);

        while (nElements > maxElements) {
            runSize -= pageSize;
            nElements = runSize / elemSize;
        }

        return runSize;
    }

    static int bitmapIdx(long handle) {
        return (int) handle;
    }
}
```