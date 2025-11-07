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
10. 常用的编解码器(比如LengthFieldBasedFrameDecoder)
#####
每一部分都会有单独的分支和对应的博客
#####
1. lab1版本分支:  release/lab1_nio_reactor   
   博客地址: [从零开始实现简易版Netty(一) MyNetty Reactor模式](https://www.cnblogs.com/xiaoxiongcanguan/p/18939320)
2. lab2版本分支:  release/lab2_pipeline_handle  
   博客地址: [从零开始实现简易版Netty(二) MyNetty pipeline流水线](https://www.cnblogs.com/xiaoxiongcanguan/p/18964326)
3. lab3版本分支:  release/lab3_efficient_read  
   博客地址: [从零开始实现简易版Netty(三) MyNetty 高效的数据读取实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18979699)
4. lab4版本分支:  release/lab4_efficient_write  
   博客地址: [从零开始实现简易版Netty(四) MyNetty 高效的数据写出实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18992091)
5. lab5版本分支:  release/lab5_fast_thread_local  
   博客地址: [从零开始实现简易版Netty(五) MyNetty FastThreadLocal实现](https://www.cnblogs.com/xiaoxiongcanguan/p/19005381)
6. lab6版本分支:  release/lab6_bytebuf  
   博客地址: [从零开始实现简易版Netty(六) MyNetty ByteBuf实现](https://www.cnblogs.com/xiaoxiongcanguan/p/19029215)
7. lab7版本分支:  release/lab7_normal_allocate  
   博客地址: [从零开始实现简易版Netty(七) MyNetty 实现Normal规格的池化内存分配](https://www.cnblogs.com/xiaoxiongcanguan/p/19084677)  
8. lab8版本分支:  release/lab8_small_allocate   
   博客地址: [从零开始实现简易版Netty(八) MyNetty 实现Small规格的池化内存分配](https://www.cnblogs.com/xiaoxiongcanguan/p/19109991)  
9. lab9版本分支:  release/lab9_thread_local_cache  
   博客地址: [从零开始实现简易版Netty(九) MyNetty 实现池化内存的线程本地缓存](https://www.cnblogs.com/xiaoxiongcanguan/p/19148861)  
10. lab10版本分支: release/lab10_codec_handler  
   博客地址: [从零开始实现简易版Netty(十) MyNetty 通用编解码器解决TCP黏包/拆包问题](https://www.cnblogs.com/xiaoxiongcanguan/p/19200844) 