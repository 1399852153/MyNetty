# 从零开始实现简易版Netty(六) MyNetty ByteBuf实现
## 1. jdk Buffer简单介绍
在上一篇博客中，lab5版本的MyNetty中实现了FastThreadLocal，为后续实现池化内存分配功能打下了基础。池化内存分配是netty中非常核心也非常复杂的一个功能，没法在一次迭代中完整的实现，MyNetty打算分为4个迭代逐步的将其实现。  
按照计划，本篇博客中，lab6版本的MyNetty需要实现一个非常基础的，非池化的ByteBuf作为后续迭代的基础。  
由于本文属于系列博客，读者需要对之前的博客内容有所了解才能更好地理解本文内容。
* lab1版本博客：[从零开始实现简易版Netty(一) MyNetty Reactor模式](https://www.cnblogs.com/xiaoxiongcanguan/p/18939320)
* lab2版本博客：[从零开始实现简易版Netty(二) MyNetty pipeline流水线](https://www.cnblogs.com/xiaoxiongcanguan/p/18964326)
* lab3版本博客：[从零开始实现简易版Netty(三) MyNetty 高效的数据读取实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18979699)
* lab4版本博客：[从零开始实现简易版Netty(四) MyNetty 高效的数据写出实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18992091)
* lab5版本博客：[从零开始实现简易版Netty(五) MyNetty FastThreadLocal实现](https://www.cnblogs.com/xiaoxiongcanguan/p/19005381)
#####
在前面的实验中，MyNetty中用来承载消息的容器一直是java中nio包下的ByteBuffer。与FastThreadLocal类似，Netty同样不满足于jdk自带的ByteBuffer，而是基于ByteBuffer实现了性能更好，功能更强大的ByteBuf容器。  
但在学习Netty的ByteBuf容器之前，我们还是需要先了解jdk中的ByteBuffer工作原理。只有在理解了jdk原生的ByteBuffer的实现原理和优缺点后，我们才能更好的理解Netty中ByteBuf，以及ByteBuf的优势。
