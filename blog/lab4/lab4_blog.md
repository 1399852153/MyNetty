# 从零开始实现简易版Netty(四) MyNetty 高效的数据写出实现
## 1. MyNetty 数据写出处理优化
在上一篇博客中，lab3版本的MyNetty对事件循环中的IO读事件处理做了一定的优化，解决了之前版本无法进行大数据量读取的问题。  
按照计划，本篇博客中，lab4版本的MyNetty需要实现高效的数据写出。由于本文属于系列博客，读者需要对之前的博客内容有所了解才能更好地理解本文内容。
* lab1版本博客：[从零开始实现简易版Netty(一) MyNetty Reactor模式](https://www.cnblogs.com/xiaoxiongcanguan/p/18939320)
* lab2版本博客：[从零开始实现简易版Netty(二) MyNetty pipeline流水线](https://www.cnblogs.com/xiaoxiongcanguan/p/18964326)  
* lab3版本博客：[从零开始实现简易版Netty(三) MyNetty 高效的数据读取实现](https://www.cnblogs.com/xiaoxiongcanguan/p/18979699)

#####
目前版本的实现中，MyNetty允许用户在自定义的ChannelHandler中使用write方法向远端写出消息。  
write操作被视为一个出站事件会一直从后往前传播到head节点，在pipeline流水线的head节点中通过jdk的socketChannel.write方法将消息写出。
##### demo中的EchoServerEventHandler实现
```java
public class EchoServerEventHandler extends MyChannelEventHandlerAdapter {
    // 已省略无关逻辑
    @Override
    public void channelRead(MyChannelHandlerContext ctx, Object msg) {
        // 已经decoder过了
        String receivedStr = (String) msg;
        logger.info("echo server received message:{}, channel={}", receivedStr, ctx.channel());
        // 读完了，echo服务器准备回写数据到客户端
        String echoMessage = "server echo:" + receivedStr;
        ctx.write(echoMessage);
    }
}    
```
#####
面对大量的待读取消息，Netty针对性的实现了如启发式算法动态调节buffer容器大小、限制单channel读事件中读取消息的次数以避免其它channel饥饿等机制。  
同样的，如果需要短时间内向外写出大量的消息时，目前版本MyNetty中简单的socketChannel.write来实现写出功能是远远不够的，极端场景下会有很多问题。  
1. 操作系统向外发送数据的缓冲区是有限的，当网络拥塞、系统负载过高等场景时，通过socketChannel.write写出的数据并不能总是完全成功的写出，而是可能部分甚至全部都无法写出。  
   此时，未成功写出的数据需要由应用程序自己维护好，等待缓冲区空闲后进行重试。  
2. write方法的执行是异步的，事件循环线程a发出的消息可能实际上是由线程b来真正的写出，而线程b又不总是能一次成功的写出消息。
   MyNetty中线程a目前无法感知到write方法发送的消息是否被成功写出，同时也无法在缓冲区负载较高时，主动限制自己写出消息的速率而在源头实现限流。
3. 写出操作与读取操作一样，都是需要消耗cpu的，即使write方法写出的消息总是能一次性的成功发出，也不能让当前channel无限制的写出数据，而导致同处一个事件循环中的其它channel饥饿。
#####
因此在本篇博客中，lab4版本的MyNetty需要对写出操作进行改进，优化大数据写出时的各种问题。

## 2. MyNetty 高效的数据写出实现源码解析

