# 从零开始实现简易版Netty(五) MyNetty ThreadLocal原理解析与FastThreadLocal实现
## 1. ThreadLocal介绍
在上一篇博客中，lab4版本的MyNetty对事件循环中的IO写事件处理进行了优化，解决了之前版本无法进行大数据消息写出的问题。  
按照计划，本篇博客中，lab5版本的MyNetty需要实现FastThreadLocal。由于本文属于系列博客，读者需要对之前的博客内容有所了解才能更好地理解本文内容。
* lab1版本博客：[从零开始实现简易版Netty(一) MyNetty Reactor模式](https://www.cnblogs.com/xiaoxiongcanguan/p/18939320)
* lab2版本博客：[从零开始实现简易版Netty(二) MyNetty pipeline流水线](https://www.cnblogs.com/xiaoxiongcanguan/p/18964326)
* lab3版本博客：[从零开始实现简易版Netty(三) MyNetty 高效的数据读取实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18979699)
* lab4版本博客：[从零开始实现简易版Netty(四) MyNetty 高效的数据写出实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18992091)
#####
