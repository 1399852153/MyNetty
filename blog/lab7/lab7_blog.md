# 从零开始实现简易版Netty(七) MyNetty 实现Normal规格的池化内存分配
## 1. Netty池化内存分配介绍
在上一篇博客中，lab6版本的MyNetty中实现了一个简易的非池化ByteBuf容器，池化内存分配是netty中非常核心也非常复杂的一个功能，没法在一次迭代中完整的实现，MyNetty打算分为4个迭代逐步的将其实现。按照计划，lab7版本的MyNetty需要实现Normal规格的内存分配。  
由于本文属于系列博客，读者需要对之前的博客内容有所了解才能更好地理解本文内容。
* lab1版本博客：[从零开始实现简易版Netty(一) MyNetty Reactor模式](https://www.cnblogs.com/xiaoxiongcanguan/p/18939320)
* lab2版本博客：[从零开始实现简易版Netty(二) MyNetty pipeline流水线](https://www.cnblogs.com/xiaoxiongcanguan/p/18964326)
* lab3版本博客：[从零开始实现简易版Netty(三) MyNetty 高效的数据读取实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18979699)
* lab4版本博客：[从零开始实现简易版Netty(四) MyNetty 高效的数据写出实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18992091)
* lab5版本博客：[从零开始实现简易版Netty(五) MyNetty FastThreadLocal实现](https://www.cnblogs.com/xiaoxiongcanguan/p/19005381)
* lab6版本博客：[从零开始实现简易版Netty(六) MyNetty ByteBuf实现](https://www.cnblogs.com/xiaoxiongcanguan/p/19029215)
#####
在上一篇博客中我们提到，Netty的ByteBuf容器相比jdk的ByteBuffer的最大的一个优势就是实现了池化功能。通过对ByteBuf容器的池化，大幅提高了需要大量创建并回收ByteBuf容器的场景下的性能。  
在Netty中对于ByteBuf的池化分为两部分，一个是**ByteBuf对象本身的池化**，另一个则是**对ByteBuf背后底层数组所使用内存的池化**。  
对于ByteBuf对象的池化，Netty中实现了一个简单的对象池(Recycler)以支持并发的对象创建与回收。  


## 2. MyNetty Normal规格池化内存分配实现解析

