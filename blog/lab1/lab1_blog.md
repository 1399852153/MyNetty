# 从零开始实现简易版Netty(一) MyNetty Reactor模式
## 1. MyNetty介绍
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

## 2. 操作系统I/O模型与Reactor模式介绍
作为MyNetty系列的第一篇博客，按照规划，第一个迭代中需要实现基于NIO的reactor模式。这也是netty最核心的功能，一个基于事件循环的reactor线程工作模型。  
在学习的过程中，我们要尽量做到知其然且知其所以然。  
因此，在介绍Reactor模式之前，先简单介绍一下两种常见的操作系统网络I/O模型，只要在了解其各自的优缺点后，才能帮助我们更好的理解为什么Netty最终选择了reactor模式。
### 2.1 操作系统I/O模型介绍
##### 同步阻塞I/O(BIO)
同步阻塞IO，顾名思义，其读写是阻塞性的，在数据还没有准备好时(比如客户端还未发送新请求,或者未收到服务端响应)，当前处理IO的线程是处于阻塞态的，直到数据就绪(比如接受到客户端发送的请求，或收到服务端响应)时才会被唤醒。  
由于其阻塞的特性，因此每一个新的客户端连接都需要一个独立的线程来承载。
#####
| BIO | 详情                                                                         |
|-----|----------------------------------------------------------------------------|
| 优点  | 简单易理解，同步阻塞式的线性代码执行流符合人的直觉。因此普通的web业务后台服务器大多是基于BIO模型开发的                     |
| 缺点  | 由于客户端连接数与服务器线程数是1:1的，而服务器由于线程上下文切换的CPU开销和内存大小限制，难以应对大规模的并发连接(大几千甚至几万)，性能较差 |
##### java实现的BIO服务端demo
```java
public class BIOEchoServer {

    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) throws IOException {
        int port = 8080;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("BIOServer started on port " + port);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client connected: " + clientSocket.getInetAddress());

            // 每个新的连接都启用一个线程去处理
            threadPool.execute(
                () -> handleClientConnect(clientSocket)
            );
        }
    }

    private static void handleClientConnect(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received from client: " + inputLine);

                // echo message
                String responseMessage = "server echo: " + inputLine;
                out.println(responseMessage);
                System.out.println("Sent response: " + responseMessage);
            }
        } catch (IOException e) {
            System.out.println("Client connection closed: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                System.out.println("clientSocket closed! " + clientSocket.getInetAddress());
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }
}
```
##### java实现的BIO客户端demo
```java
public class BIOClient {

    public static void main(String[] args) throws IOException {
        String hostname = "127.0.0.1";
        int port = 8080;

        try (Socket socket = new Socket(hostname, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to server. Type messages (type 'exit' to quit)");

            String userInput;
            while ((userInput = stdIn.readLine()) != null) {
                out.println(userInput);
                System.out.println("Server response: " + in.readLine());
            }
        }
    }
}
```
##### I/O多路复用 
I/O多路复用，顾名思义，其不同于BIO中一个线程对应一个客户端连接的模式。I/O多路复用模型中，一个服务端线程能够同时处理多个客户端连接。  
I/O多路复用解决了传统BIO模型下面对海量并发时系统资源不足的问题，但同时也引入了一些新的问题。
#####
| I/O多路复用 | 详情                          |
|---------|-----------------------------|
| 优点      | 性能好，吞吐量高。单个线程即可处理海量连接       |
| 缺点      | 比起BIO的阻塞模式，基于事件触发的编程模型非常复杂。 |
##### java实现的IO多路复用服务端demo
```java
public class NIOEchoServer {

    public static void main(String[] args) throws IOException {
        SelectorProvider selectorProvider = SelectorProvider.provider();
        Selector selector = selectorProvider.openSelector();

        // 服务端监听accept事件的channel
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        for(;;){
            try{
                int keys = selector.select(60000);
                if (keys == 0) {
                    System.out.println("server 60s未监听到事件，继续监听！");
                    continue;
                }

                // processSelectedKeysPlain
                Iterator<SelectionKey> selectionKeyItr = selector.selectedKeys().iterator();
                while (selectionKeyItr.hasNext()) {
                    SelectionKey key = selectionKeyItr.next();
                    System.out.println("process SelectionKey=" + key.readyOps());
                    try {

                        // 拿出来后，要把集合中已经获取到的事件移除掉，避免重复的处理
                        selectionKeyItr.remove();

                        if (key.isAcceptable()) {
                            // 处理accept事件（接受到来自客户端的连接请求）
                            processAcceptEvent(key);
                        }

                        if (key.isReadable()) {
                            // 处理read事件
                            processReadEvent(key);
                        }
                    }catch (Exception e){
                        System.out.println("server event loop process an selectionKey error! " + e.getMessage());
                        e.printStackTrace();

                        key.cancel();
                        if(key.channel() != null){
                            System.out.println("has error, close channel! " + key.channel());
                            key.channel().close();
                        }
                    }
                }
            }catch (Exception e){
                System.out.println("server event loop error! ");
                e.getStackTrace();
            }
        }
    }

    private static void processAcceptEvent(SelectionKey key) throws IOException {
        // 能收到accept事件的channel一定是ServerSocketChannel

        ServerSocketChannel ssChannel = (ServerSocketChannel)key.channel();
        // 获得与客户端建立的那个连接
        SocketChannel socketChannel = ssChannel.accept();
        socketChannel.configureBlocking(false);

        socketChannel.finishConnect();

        System.out.println("socketChannel=" + socketChannel + " finishConnect!");
        // 将接受到的连接注册到同样的selector中，并监听read事件
        socketChannel.register(key.selector(),SelectionKey.OP_READ);
    }

    private static void processReadEvent(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel)key.channel();

        // 简单起见，buffer不缓存，每次读事件来都新创建一个
        // 暂时也不考虑黏包/拆包场景(Netty中靠ByteToMessageDecoder解决，后续再分析其原理)，理想的认为每个消息都小于1024，且每次读事件都只有一个消息
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);

        int byteRead = socketChannel.read(readBuffer);
        if(byteRead == -1){
            // 简单起见不考虑tcp半连接的情况，返回-1直接关掉连接
            socketChannel.close();
        }else{
            // 将缓冲区当前的limit设置为position=0，用于后续对缓冲区的读取操作
            readBuffer.flip();
            // 根据缓冲区可读字节数创建字节数组
            byte[] bytes = new byte[readBuffer.remaining()];
            // 将缓冲区可读字节数组复制到新建的数组中
            readBuffer.get(bytes);
            String receivedStr = new String(bytes, StandardCharsets.UTF_8);

            System.out.println("received message:" + receivedStr + " ,from " + socketChannel.socket().getRemoteSocketAddress());

            // 读完了，echo服务器准备回写数据到客户端
            String echoMessage = "server echo:" + receivedStr;

            ByteBuffer writeBuffer = ByteBuffer.allocateDirect(1024);
            writeBuffer.put(echoMessage.getBytes(StandardCharsets.UTF_8));
            writeBuffer.flip(); // 写完了，flip供后续去读取
            socketChannel.write(writeBuffer);
        }
    }
}
```
##### java实现的IO多路复用客户端demo
```java
public class NIOClient {

    private static volatile SocketChannel clientSocketChannel;

    public static void main(String[] args) throws Exception {
        SelectorProvider selectorProvider = SelectorProvider.provider();
        Selector selector = selectorProvider.openSelector();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        new Thread(()->{
            try {
                startClient(selector,countDownLatch);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        countDownLatch.await();
        System.out.println("please input message:");
        while(true){
            Scanner sc = new Scanner(System.in);
            String msg = sc.next();
            System.out.println("get input message:" + msg);

            // 发送消息
            ByteBuffer writeBuffer = ByteBuffer.allocate(64);
            writeBuffer.put(msg.getBytes(StandardCharsets.UTF_8));
            writeBuffer.flip(); // 写完了，flip供后续去读取
            clientSocketChannel.write(writeBuffer);
        }
    }

    private static void startClient(Selector selector, CountDownLatch countDownLatch) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        clientSocketChannel = socketChannel;

        // doConnect
        // Returns: true if a connection was established,
        //          false if this channel is in non-blocking mode and the connection operation is in progress;
        if(!socketChannel.connect(new InetSocketAddress("127.0.0.1", 8080))) {
            // 配置为非阻塞，会返回false，通过注册并监听connect事件的方式进行交互
            socketChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
        }

        for(;;){
            try {
                int keys = selector.select(60000);
                if (keys == 0) {
                    System.out.println("client 60s未监听到事件，继续监听！");
                    continue;
                }

                // processSelectedKeysPlain
                Iterator<SelectionKey> selectionKeyItr = selector.selectedKeys().iterator();

                while (selectionKeyItr.hasNext()) {
                    SelectionKey key = selectionKeyItr.next();
                    try {
                        System.out.println("process SelectionKey=" + key.readyOps());

                        // 拿出来后，要把集合中已经获取到的事件移除掉，避免重复的处理
                        selectionKeyItr.remove();

                        if (key.isConnectable()) {
                            // 处理连接相关事件
                            processConnectEvent(key,countDownLatch);
                        }

                        if (key.isReadable()){
                            processReadEvent(key);
                        }

                        if (key.isWritable()){
                            System.out.println("watch an write event!");
                        }

                    } catch (Exception e) {
                        System.out.println("client event loop process an selectionKey error! " + e.getMessage());

                        key.cancel();
                        if(key.channel() != null){
                            key.channel().close();
                            System.out.println("has error, close channel!" );
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("client event loop error! ");
                e.getStackTrace();
            }
        }
    }

    private static void processConnectEvent(SelectionKey key, CountDownLatch countDownLatch) throws IOException {
        // remove OP_CONNECT as otherwise Selector.select(..) will always return without blocking
        int ops = key.interestOps();
        ops &= ~SelectionKey.OP_CONNECT;
        key.interestOps(ops);

        SocketChannel socketChannel = (SocketChannel) key.channel();
        if(socketChannel.finishConnect()){
            // 确认完成连接
            System.out.println("client channel connected!");

            countDownLatch.countDown();
        }else{
            // 连接建立失败，程序退出
            System.out.println("client channel connect failed!");
            System.exit(1);
        }
    }

    private static void processReadEvent(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // 创建ByteBuffer，并开辟一个1M的缓冲区
        ByteBuffer buffer = ByteBuffer.allocate(64);
        // 读取请求码流，返回读取到的字节数
        int readBytes = socketChannel.read(buffer);

        // 读取到字节，对字节进行编解码
        if(readBytes > 0){
            // 将缓冲区当前的limit设置为position=0，用于后续对缓冲区的读取操作
            buffer.flip();
            // 根据缓冲区可读字节数创建字节数组
            byte[] bytes = new byte[buffer.remaining()];
            // 将缓冲区可读字节数组复制到新建的数组中
            buffer.get(bytes);
            String response = new String(bytes, StandardCharsets.UTF_8);
            System.out.println("client received response message: " + response);
        }

        // 读取到了EOF，关闭连接
        if(readBytes < 0){
            socketChannel.close();
        }
    }
}
```
#####
上述对于操作系统I/O模型的介绍限于篇幅，点到为止。想进一步了解的读者可以参考我之前写的博客：[谈谈对不同I/O模型的理解](https://www.cnblogs.com/xiaoxiongcanguan/p/13938877.html)
### 2.2 Reactor模式
从上面的介绍中我们可以看到，I/O多路复用模型的高性能、高吞吐的特点更加适合互联网时代海量连接，所以netty也是基于I/O多路复用模型的。  
但是上述给出的I/O多路复用的demo中还存在两个很严重的问题，第一个问题是java中NIO的能力过于底层，在开发业务时所需要考虑的细节太多,一个简单的、不考虑各种异常、边界场景的echo服务器都要写近百行的代码。  
第二个问题则是服务端单线程的I/O多路复用模型没法很好的利用现代的多核CPU硬件，会出现处理大量连接时1核有难8核围观的场面。   
#####
针对第一个问题，正是netty作为java NIO的更高层次封装而诞生的原因，我们会在后续的迭代中逐步的优化这一问题。而第二个问题的解决方案便是本章要引出的主题，reactor模式。  
I/O多路复用模型与多线程并不冲突，一个线程可以独自处理所有连接，也可以用多个线程来均匀的分摊所有来自客户端的连接。  
在reactor模式下，将接收连接与处理连接后续读写的任务分离开来。接受客户端连接的逻辑较为简单，因此一个线程(cpu核心)通常足够处理这一任务。
相对的，处理连接建立后的读写操作则压力会大的多，所以需要多个CPU核心(多个线程)来分摊压力。  
在reactor模式下，将专门用于接受连接的线程称为Boss线程，而连接建立后处理读写操作的线程成为Worker线程(Boss工作压力小，Worker工作压力大；Boss接了单子后把活直接派给Worker)。

##### reactor模式示意图
![reactor.png](lab1_reactor.png)

## 3. MyNetty reactor模式实现源码解析

## 总结



