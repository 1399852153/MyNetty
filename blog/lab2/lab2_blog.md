# 从零开始实现简易版Netty(二) MyNetty pipeline流水线

## 1. Netty pipeline流水线介绍
在上一篇博客中v1版本的MyNetty参考netty实现了一个极其精简的reactor模型。按照计划，v2版本的MyNetty需要实现pipeline流水线，以支持不同模块处理逻辑的解耦。  
#####
由于本文属于系列博客，读者需要对之前的博客内容有所了解才能更好地理解本篇内容。  
* v1版本博客：[从零开始实现简易版Netty(一) MyNetty Reactor模式](https://www.cnblogs.com/xiaoxiongcanguan/p/18939320)  

#####
在v1版本中，MyNetty的EventLoop处理逻辑中，允许使用者配置一个EventHandler，并在处理read事件时调用其实现的自定义fireChannelRead方法。  
这一机制在实现demo中的简易echo服务器时是够用的，但在实际的场景中，一个完备的网络框架，业务想要处理的IO事件有很多类型，并且不希望在一个大而全的臃肿的处理器中处理所有的IO事件，而是能够模块化的拆分不同的处理逻辑，实现架构上的灵活解耦。  
**因此netty提供了pipeline流水线机制，允许用户在使用netty时能按照自己的需求，按顺序组装自己的处理器链条。**

### netty中的IO事件




