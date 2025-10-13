# 从零开始实现简易版Netty(九) MyNetty 实现池化内存的线程本地缓存
## 1. Netty 池化内存线程本地缓存介绍
在上一篇博客中，截止lab8版本MyNetty已经实现了Normal和Small规格的池化内存分配。按照计划，在lab9中MyNetty将实现池化内存的线程本地缓存功能，以完成池化内存功能的最后一块拼图。
由于本文属于系列博客，读者需要对之前的博客内容有所了解才能更好地理解本文内容。
* lab1版本博客：[从零开始实现简易版Netty(一) MyNetty Reactor模式](https://www.cnblogs.com/xiaoxiongcanguan/p/18939320)
* lab2版本博客：[从零开始实现简易版Netty(二) MyNetty pipeline流水线](https://www.cnblogs.com/xiaoxiongcanguan/p/18964326)
* lab3版本博客：[从零开始实现简易版Netty(三) MyNetty 高效的数据读取实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18979699)
* lab4版本博客：[从零开始实现简易版Netty(四) MyNetty 高效的数据写出实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18992091)
* lab5版本博客：[从零开始实现简易版Netty(五) MyNetty FastThreadLocal实现](https://www.cnblogs.com/xiaoxiongcanguan/p/19005381)
* lab6版本博客：[从零开始实现简易版Netty(六) MyNetty ByteBuf实现](https://www.cnblogs.com/xiaoxiongcanguan/p/19029215)
* lab7版本博客：[从零开始实现简易版Netty(七) MyNetty 实现Normal规格的池化内存分配](https://www.cnblogs.com/xiaoxiongcanguan/p/19084677)
* lab8版本博客：[从零开始实现简易版Netty(八) MyNetty 实现Small规格的池化内存分配](https://www.cnblogs.com/xiaoxiongcanguan/p/19109991)
#####
在lab7、lab8的实现中可以发现，出于空间利用率的考虑，一个PoolArena会同时被多个线程并发访问。因此无论是Normal还是Small规格的池化内存分配，Netty在进行实际的池化内存分配时都或多或少的需要使用互斥锁来确保用于追踪池化内存状态的元数据(PoolArena或是PoolSubPage)不会被并发的更新而出现问题。  
jemalloc的论文中提到，内存分配作为一个高频的操作需要尽可能的减少线程的同步竞争以提高效率，大量线程都阻塞在同步锁上会大大降低内存分配的整体吞吐率。  
因此jemalloc中提到，可以引入线程本地缓存来减少同步事件。_"The main goal of thread caches is to reduce the volume of synchronization events."_  
#####
引入线程本地缓存后，当前线程在释放池化内存时，不会直接将空闲的池化内存对象还给公共的PoolArena中，而是优先尝试放入独属于本线程的本地缓存中。同时，在尝试申请池化内存分配时，也会优先查询线程本地缓存中是否存在对应规格的可用池化内存对象，如果有则直接使用，而无需访问公共的PoolArena。   
有了线程本地缓存，线程在绝大多数情况下都只和独属于自己的本地缓存进行交互，因此能够大幅减少与其它线程争抢公共PoolArena元数据互斥锁的场景，从而大幅提升吞吐量。  
当然，线程本地缓存也不是没有缺点的，线程本地缓存毫无疑问增加了内存的开销，规格繁多的本地池化内存对象多数时候都只会静静地在缓存中等待被使用(视为内部碎片)，因此线程本地所能缓存的池化对象数量是被严格限制的，使用者需要在池化内存分配效率与空间利用率的取舍上达成平衡。   
具体的实现细节，我们在下文中结合源码再展开介绍。

## 2. MyNetty 池化内存线程本地缓存源码实现

## 总结
