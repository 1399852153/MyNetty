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
可以发现如果使用伙伴算法实现Small规格内存分配，其性能开销、锁的同步开销都大的惊人，同时对于单次分配所节约出来的内部碎片，甚至不足以抵消大量元数据所额外占用的外部碎片。  
因此Netty参考操作系统内核，针对申请的容量相对更小、申请更加频繁的Small规格池化内存分配，不使用伙伴算法，而是在Normal规格分配能力的基础上采用slab算法实现Small规格的池化内存分配管理功能。

## 2. MyNetty Small规格池化内存分配实现
