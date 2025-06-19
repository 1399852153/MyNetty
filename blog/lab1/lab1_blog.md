# 从零开始实现简易版Netty(一) MyNetty Reactor模式
## MyNetty介绍
自从18年作为一个java程序员入行以来，所接触到的大量组件如dubbo、rocketmq、redisson等都是基于netty这一高性能网络框架实现的。  
限于个人水平，在过去很长一段时间中都只能算是netty的初级使用者；在使用基于netty的中间件时，总是因为对netty底层不够了解而导致排查问题时效率不高。  
因此，在过去的一段时间中我对netty源码进行了一定的研究，并以博客的形式将心得分享出来，希望能帮助到同样对netty工作原理感兴趣的读者。  
非常感谢大佬[bin的技术小屋](https://home.cnblogs.com/u/binlovetech)，在我学习netty的过程中给了我很大的帮助。  
#####
不同于大多数博客直接针对netty官方源码进行原理解析的方式，本系列博客通过从零到一的实现一个简易版的netty(即MyNetty)来帮助读者更好的理解netty的工作原理。  
相比于完整版的netty，MyNetty只实现了netty中最核心的功能点，目的是降低复杂度，避免初学者在学习netty的过程中，对netty源码中复杂的抽象及过深的调用链而感到畏惧。  
本博客会按照以下顺序，通过一个接一个的小迭代由简单到复杂的实现MyNetty，核心逻辑主要参考自netty 4.1.80.Final版本。  
#####
1. Reactor模式
2. Pipeline管道
3. 高效的数据读取
4. 高效的数据写出
5. FastThreadLocal
6. ByteBuf
7. Normal级别的池化内存分配(伙伴算法)
8. Small级别的池化内存分配(slab算法)
9. 池化内存分配支持线程本地缓存(ThreadLocalCache)
10. 常用的编解码器(FixedLengthFrameDecoder/LineBasedFrameDecoder等)

## Reactor模式介绍

## MyNetty reactor模式实现源码解析

## 总结



