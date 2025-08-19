# Scalable memory allocation using jemalloc(使用jemalloc的可拓展内存分配)

##### 作者： Jason Evans <jasone@FreeBSD.org> Monday, January 3, 2011

#####
The Facebook website comprises a diverse set of server applications, most of which run on dedicated machines with 8+ CPU cores and 8+ GiB of RAM.   
These applications typically use POSIX threads for concurrent computations, with the goal of maximizing throughput by fully utilizing the CPUs and RAM.   
This environment poses some serious challenges for memory allocation, in particular:
#####
* Allocation and deallocation must be fast. Ideally, little memory allocation would be required in an application's steady state, 
  but this is far from reality for large dynamic data structures based on dynamic input. 
  Even modest allocator improvements can have a major impact on throughput.
* The relation between active memory and RAM usage must be consistent. In other words, practical bounds on allocator-induced fragmentation are critical. 
  Consider that if fragmentation causes RAM usage to increase by 1 GiB per day, an application that is designed to leave only a little head room will fail within days.
* Memory heap profiling is a critical operational aid. If all goes according to plan, leak detection and removal is a development task. 
  But even then, dynamic input can cause unexpected memory usage spikes that can only be characterized by analyzing behavior under production loads.
#####
Facebook网站由一系列不同的服务端应用程序构成，其中绝大多数应用程序都运行在有着至少8个核心CPU并且大于等于8GB内存的专用机器上。  
这些应用程序通常使用POSIX线程进行并发的计算，目的是通过最大限度的使用CPU和RAM内存以达到最大的吞吐量。  
这种环境对内存分配带来了一些严峻的挑战，特别是：
#####
* 内存的分配与释放必须够快。理想情况下，应用在稳定状态下应该只进行少量的内存分配，但因为要处理基于动态输入而构建的大型动态数据结构，因此实际情况与理想情况相差甚远。  
  即使对内存分配器进行一点微小的改变，也会对内存分配的吞吐量产生重大影响。
* 实际用户使用的内存与RAM的使用量之间的关系必须保持一致。换句话说，限制分配器实际所产生的内存碎片大小是至关重要的。  
  试想，如果内存分配所产生的内存碎片每天增长1GB，那么一个在设计时只预留了少量冗余内存空间的应用程序将在几天内崩溃。  
* 堆内存分析是一个非常关键的运维辅助工具。理论上，内存泄漏的探测与修复应该是一项在开发阶段完成的任务。  
  但即便如此，动态的输入依然会造成内存使用量预期外的激增，只有分析生产负载下的实际应用行为才能准确解决相关的内存泄漏问题。

#####
In 2009, existing memory allocators met at most two of these three requirements, so we added heap profiling to jemalloc and made many optimizations, such that jemalloc is now strong on all three counts.  
The remainder of this post surveys jemalloc's core algorithms and data structures before detailing numerous Facebook-motivated enhancements, 
followed by a real-world benchmark that compares six memory allocators under a production Web server load.

#####
在2009年，已有的jemalloc内存分配器只能满足上述三种需求种的两种，因此我们为jemalloc做了很多的优化，其中就包括为jemalloc增加堆内存分析的能力，现在jemalloc能很好的满足上述的三种需求了。  
本文的剩余部分将首先对jemalloc的核心算法和数据结构进行概述，然后详细讲解Facebook基于实际需求而推动的诸多优化措施，最后再展示一个真实世界的基准测试(benchmark)，该基准测试在一个来自生产环境服务器的负载下对6种不同的内存分配器进行了横向对比。

### Core algorithms and data structures
#####
The C and C++ programming languages rely on a very basic untyped allocator API that consists primarily of five functions: malloc(), posix_memalign(), calloc(), realloc(), andfree().   
Many malloc implementations also provide rudimentary introspection capabilities, like malloc_usable_size().   
While the API is simple, high concurrent throughput and fragmentation avoidance require considerable internal complexity.   
jemalloc combines a few original ideas with a rather larger set of ideas that were first validated in other allocators.   
Following is a mix of those ideas and allocation philosophy, which combined to form jemalloc.  
#####
C和C++语言依赖于一个非常基础的无类型的分配器API，其主要包含了5个函数：malloc(), posix_memalign(), calloc(), realloc(), andfree()。
很多malloc的实现也提供了基本的内省能力，例如malloc_usable_size()。  
虽然API使用上很简单，但为了实现高的并发吞吐量和尽可能的避免内存碎片，malloc其内部的实现是非常复杂的。  
jemalloc将少数独创的理念与一些早已在其它内存分配器中得到验证的理念结合了起来。
接下来将一一介绍这些思想和分配的哲学，正是它们的有机结合才造就了jemalloc。

#####
* Segregate small objects according to size class, and **prefer low addresses during re-use**. 
  This layout policy originated in phkmalloc , and is the key to jemalloc's predictable low fragmentation behavior.
* **Carefully choose size classes** (somewhat inspired by Vam). If size classes are spaced far apart, objects will tend to have excessive unusable trailing space (internal fragmentation).  
  As the size class count increases, there will tend to be a corresponding increase in unused memory dedicated to object sizes that are currently underutilized (external fragmentation).
* **Impose tight limits on allocator metadata overhead**. Ignoring fragmentation, jemalloc limits metadata to less than 2% of total memory usage, for all size classes.
* **Minimize the active page set**. Operating system kernels manage virtual memory in terms of pages (usually 4 KiB per page), so it is important to concentrate all data into as few pages as possible. 
  phkmalloc validated this tenet, at a time when applications routinely contended with active pages being swapped out to hard disk, though it remains important in the modern context of avoiding swapping altogether.
* **Minimize lock contention**. jemalloc's independent arenas were inspired by lkmalloc, but as time went on, tcmalloc made it abundantly clear that it's even better to avoid synchronization altogether, 
  so jemalloc also implements thread-specific caching.
* **If it isn't general purpose, it isn't good enough**. When jemalloc was first incorporated into FreeBSD, 
  it had serious fragmentation issues for some applications, and the suggestion was put forth to include multiple allocators in the operating system,
  the notion being that developers would be empowered to make informed choices based on application characteristics. 
  The correct solution was to dramatically simplify jemalloc's layout algorithms, in order to improve both performance and predictability. 
  Over the past year, this philosophy has motivated numerous major performance improvements in jemalloc, and it will continue to guide development as weaknesses are discovered.
#####
* 按照规格等级将小对象隔离存放，并且在**重用时优先使用低地址位置的内存**。这一布局策略源于phkmalloc，同时也是jemaaloc实现可预测的低内存碎片分配的关键。
* **精心设计规格等级**(部分灵感来自于Vam)。如果不同规格等级之间差距过大，对象尾部将会产生过多的不可使用的空间(内部碎片)。
  而随着规格等级数量的增加(译者注：规格等级过多、排布过密)，又会导致专门服务于某一使用率较低的规格大小的内存空间闲置(外部碎片)。
* **严格的控制分配器元数据的空间开销**。在忽略内存碎片的情况下，jemelloc限制用于管理所有规格等级的元数据使用量必须低于总内存使用的2%。
* **最小化活动页集合**。操作系统内核以页为单位管理虚拟内存(通常一页为4Kb大小)，因此将所有的数据尽可能的集中在少数的页中是非常重要的。
  phkmalloc在应用需要频繁将活动页交换到磁盘的时代就早已验证过了这一规则的正确性。而在如今应用彻底禁用交换机制的前提下，这一规则依然重要。
* **优秀的设计必须足够通用**。当jemalloc被初次集成进FreeBSD操作系统时，有一些应用在使用jemalloc时遭遇了严重的内存碎片问题，有人提议操作系统应该同时包含多种不同类型的内存分配器，
  寄希望于开发者能够基于它们的应用特性去选择最合适的内存分配器。
  正确的解决方案是大幅的简化jemalloc的布局算法，目的是同时提升jemalloc的性能和可预测性。过去的一年中，这一设计理念推动了jemalloc实现了多个重要的性能优化，随着未来jemalloc中更多的缺陷被发现，这一思想将持续指导jemalloc的发展。

#####
Jemalloc implements three main size class categories as follows (assuming default configuration on a 64-bit system):
#####
以下是jemalloc在64位操作系统默认配置的三大尺寸分类：
#####
* **Small**: [8], [16, 32, 48, ..., 128], [192, 256, 320, ..., 512], [768, 1024, 1280, ..., 3840]
* **Large**: [4 KiB, 8 KiB, 12 KiB, ..., 4072 KiB]
* **Huge**: [4 MiB, 8 MiB, 12 MiB, …]

#####
Virtual memory is logically partitioned into chunks of size 2^k (4 MiB by default).
As a result, it is possible to find allocator metadata for small/large objects (interior pointers) in constant time via pointer manipulations, 
and to look up metadata for huge objects (chunk-aligned) in logarithmic time via a global red-black tree.
#####
虚拟内存在Chunk内按照2次幂进行逻辑分区(Chunk默认大小为4MB)。
因此，可以通过指针运算以常数时间复杂度找到small和large对象(内部指针)的分配器元数据，并且能够通过一个全局的红黑树以对数时间复杂度查找到huge对象(基于Chunk对齐)的分配器元数据。
#####
Application threads are assigned arenas in round-robin fashion upon first allocating a small/large object.
Arenas are completely independent of each other. They maintain their own chunks, from which they carve page runs for small/large objects. 
Freed memory is always returned to the arena from which it came, regardless of which thread performs the deallocation.
#####
应用线程在首次分配small或large对象时，使用round-robin轮训为其分配一个arena。  
不同的Arena彼此之间完全独立。Arena维护独属于它自己的Chunk集合，从中切割出连续的页段用于分配small或large对象。  
内存被释放时总是被归还到其一开始所属的Arena中，而与执行deallocation释放内存的线程无关。

#####
![Arena chunk layout.png](Arena chunk layout.png)

#####
Each arena chunk contains metadata (primarily a page map), followed by one or more page runs.
Small objects are grouped together, with additional metadata at the start of each page run, 
whereas large objects are independent of each other, and their metadata reside entirely in the arena chunk header.
Each arena tracks non-full small object page runs via red-black trees (one for each size class), 
and always services allocation requests using the non-full run with the lowest address for that size class.
Each arena tracks available page runs via two red-black trees — one for clean/untouched page runs, and one for dirty/touched page runs. 
Page runs are preferentially allocated from the dirty tree, using lowest best fit.

#####
![Arena and thread cache layout .png](Arena and thread cache layout.png)

#####
Each thread maintains a cache of small objects, as well as large objects up to a limited size (32 KiB by default).
Thus, the vast majority of allocation requests first check for a cached available object before accessing an arena.
Allocation via a thread cache requires no locking whatsoever, whereas allocation via an arena requires locking an arena bin (one per small size class) and/or the arena as a whole.

#####
The main goal of thread caches is to reduce the volume of synchronization events.
Therefore, the maximum number of cached objects for each size class is capped at a level that allows for a 10-100X synchronization reduction in practice. 
Higher caching limits would further speed up allocation for some applications, but at an unacceptable fragmentation cost in the general case. 
To further limit fragmentation, thread caches perform incremental "garbage collection" (GC), where time is measured in terms of allocation requests.
Cached objects that go unused for one or more GC passes are progressively flushed to their respective arenas using an exponential decay approach.

#####