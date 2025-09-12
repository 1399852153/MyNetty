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
而在针对申请的容量相对更小、申请更加频繁的池化内存分配，Netty参考操作系统内核中的slab分配算法，在Normal规格分配的基础上，实现了Small规格的池化内存分配管理功能。
#####

## 2. MyNetty Small规格池化内存分配实现
