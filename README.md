# MyNetty(自己动手实现简易版netty)
### 分享个人学习netty的心得
不同于大多数博客直接针对netty官方源码进行原理解析的方式，本项目中通过从零到一的实现一个简易版的netty来帮助读者更好的理解netty的工作原理。  
相比于完整版的netty，MyNetty只实现了netty中最核心的功能点，以降低理解的难度，避免初学者在学习netty的过程中，对netty源码中复杂的抽象及过深的调用链而感到畏惧。  
本博客会按照以下功能实现顺序，由简单到复杂的实现一个精简版netty，核心逻辑主要参考自netty 4.1.80.Final版本。  
非常感谢大佬[bin的技术小屋](https://home.cnblogs.com/u/binlovetech)，在学习netty的过程中给予了我很大的帮助。  
#####
1. Reactor模型
2. Pipeline管道
3. 高效的数据读取
4. 高效的数据写出
5. FastThreadLocal
6. ByteBuf
7. Normal级别的池化内存分配(伙伴算法)
8. Small级别的池化内存分配(slab算法)
9. 池化内存分配支持线程本地缓存(ThreadLocalCache)
10. 常用的编解码器(FixedLengthFrameDecoder/LineBasedFrameDecoder等)
#####
每一部分都会有单独的分支和对应的博客（持续更新中）
#####
1. v1版本分支:  release/lab1_nio_reactor



