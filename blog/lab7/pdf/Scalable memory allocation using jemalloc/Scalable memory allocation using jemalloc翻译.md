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
每一个Arena中的chunk都包含了元数据(主要是一个页映射表)，后面接着是一个或多个连续页内存段(page runs)。    
small规格的对象按照规格进行分组存放，在每一个内存段的开头带有额外的元数据，而large规格的对象则彼此独立存放，并且其元数据被完整的存放在chunk的头部。  
每一个Arena通过一个红黑树来追踪每一个未满的存放small规格对象的连续页内存段(每个small规格级别都对应一个红黑树)，
并且总是使用每个规格的未满内存段中最低的地址来满足应用程序的内存分配请求。  
每一个Arena都通过两颗红黑树来追踪可用的连续页内存段——一个用于维护干净的/未被使用的连续页内存段，而另一个用于维护脏的/已被使用的连续页内存段。  
优先从维护脏页的树中使用最少使用为优的策略分配连续内存段。  

#####
![Arena and thread cache layout .png](Arena and thread cache layout.png)

#####
Each thread maintains a cache of small objects, as well as large objects up to a limited size (32 KiB by default).
Thus, the vast majority of allocation requests first check for a cached available object before accessing an arena.
Allocation via a thread cache requires no locking whatsoever, whereas allocation via an arena requires locking an arena bin (one per small size class) and/or the arena as a whole.
#####
每一个线程都维护了一个small对象的缓存，并且也维护了一个大小受限的large对象缓存(默认32KB)。  
因此，绝大多数内存分配请求都会在访问Arena之前检查缓存中是否存在可用的已缓存对象。  
通过线程缓存进行分配不需要任何的加锁操作，而通过一个Arena进行分配则需要进行加锁，small规格的分配需要锁住其中的一个小区域，而large类型的分配则可能需要锁住整个Arena。

#####
The main goal of thread caches is to reduce the volume of synchronization events.
Therefore, the maximum number of cached objects for each size class is capped at a level that allows for a 10-100X synchronization reduction in practice. 
Higher caching limits would further speed up allocation for some applications, but at an unacceptable fragmentation cost in the general case. 
To further limit fragmentation, thread caches perform incremental "garbage collection" (GC), where time is measured in terms of allocation requests.
Cached objects that go unused for one or more GC passes are progressively flushed to their respective arenas using an exponential decay approach.
#####
引入线程缓存的主要目的是减少同步事件的量。  
因此，缓存对象的最大数量被限制在实际上将同步事件数量减少10-100倍的量。
更大的缓存对象的数量虽然能进一步的提升一些应用的分配速度，但是在一般情况下带来了不可接受的内存碎片开销。  
为了进一步限制内存碎片，线程缓存执行增量的垃圾回收(GC)机制，其执行时间通过分配请求的次数来衡量。  
采用指数衰减算法，将经历过一次或者更多次GC后依然未被使用的被缓存对象逐步的将其刷新回到对应所属的Arena中。  

### Facebook-motivated innovations
#####
In 2009, a Facebook engineer might have summarized jemalloc's effectiveness by saying something like, 
"jemalloc's consistency and reliability are great, but it isn't fast enough, plus we need better ways to monitor what it's actually doing."

### Speed
We addressed speed by making many improvements. Here are some of the highlights:  
* We rewrote thread caching. Most of the improved speed came from reducing constant-factor overheads (they matter in the critical path!), 
  but we also noticed that tighter control of cache size often improves data locality, which tends to mitigate the increased cache fill/flush costs. 
  Therefore we chose a very simple design in terms of data structures (singly linked LIFO for each size class) and size control (hard limit for each size class, plus incremental GC completely independent of other threads).
* We increased mutex granularity, and restructured the synchronization logic to drop all mutexes during system calls. 
  jemalloc previously had a very simple locking strategy — one mutex per arena — 
  but we determined that holding an arena mutex during mmap(),munmap(), or madvise() system calls had a dramatic serialization effect, especially for Linux kernels prior to 2.6.27. 
  Therefore we demoted arena mutexes to protect all operations related to arena chunks and page runs,
  and for each arena we added one mutex per small size class to protect data structures that are typically accessed for small allocation/deallocation. 
  This was an insufficient remedy though, so we restructured dirty page purging facilities to drop all mutexes before calling madvise(). 
  This change completely solved the mutex serialization problem for Linux 2.6.27 and newer.
* We rewrote dirty page purging such that the maximum number of dirty pages is proportional to total memory usage, rather than constant. 
  We also segregated clean and dirty unused pages, rather than coalescing them, in order to preferentially re-use dirty pages and reduce the total dirty page purging volume. 
  Although this change somewhat increased virtual memory usage, it had a marked positive impact on throughput.
* We developed a new red-black tree implementation that has the same low memory overhead (two pointer fields per node), but is approximately 30% faster for insertion/removal.
  This constant-factor improvement actually mattered for one of our applications.
  The previous implementation was based on leftleaning 2-3-4 red-black trees, and all operations were performed using only down passes.
  While this iterative approach avoids recursion or the need for parent pointers, 
  it requires extra tree manipulations that could be avoided if tree consistency were lazily restored during a subsequent up pass. 
  Experiments revealed that optimal red-black tree implementations must do lazy fix-up. 
  Furthermore, fix-up can often terminate before completing the up pass, and recursion unwinding is an unacceptable cost in such cases. 
  We settled on a non-recursive left-leaning 2-3 red-black tree implementation that initializes an array of parent pointers during the down pass, 
  then uses the array for lazy fix-up in the up pass, which terminates as early as possible.

### Introspection
#####
Jemalloc has always been able to print detailed internal statistics in human-readable form at application exit,
but this is of limited use for long-running server applications, so we exposedmalloc_stats_print() such that it can be called repeatedly by the application. 
Unfortunately this still imposed a parsing burden on consumers, so we added the mallctl*() API, patterned after BSD's sysctl() system call,
to provide access to individual statistics. We reimplementedmalloc_stats_print() in terms of mallctl*() in order to assure full coverage, 
and over time we also extended this facility to provide miscellaneous controls, such as thread cache flushing and forced dirty page purging.

#####
Memory leak diagnosis poses a major challenge, especially when live production conditions are required to expose the leaks. 
Google's tcmalloc provides an excellent heap profiling facility suitable for production use, and we have found it invaluable. 
However, we increasingly faced a dilemma for some applications, in that only jemalloc was capable of adequately controlling memory usage, 
but only tcmalloc provided adequate tools for understanding memory utilization. 
Therefore, we added compatible heap profiling functionality to jemalloc. This allowed us to leverage the post-processing tools that come with tcmalloc.

### Experimental
#####
Research and development of untried algorithms is in general a risky proposition; the majority of experiments fail.
Indeed, a vast graveyard of failed experiments bears witness to jemalloc's past, despite its nature as a practical endeavor.
That hasn't stopped us from continuing to try new things though.
Specifically, we developed two innovations that have the potential for broader usefulness than our current applications.

* Some of the datasets we work with are huge, far beyond what can fit in RAM on a single machine.
  With the recent increased availability of solid state disks (SSDs), it is tempting to expand datasets to scale with SSD rather than RAM.
  To this end we added the ability to explicitly map one or more files, rather than using anonymous mmap().
  Our experiments thus far indicate that this is a promising approach for applications with working sets that fit in RAM, 
  but we are still analyzing whether we can take sufficient advantage of this approach to justify the cost of SSD.
* The venerable malloc API is quite limited: malloc(), calloc(), realloc(), andfree(). 
  Over the years, various extensions have been bolted on, like valloc(),memalign(), posix_memalign(), recalloc(), and malloc_usable_size(), just to name a few. 
  Of these, only posix_memalign() has been standardized, and its bolton limitations become apparent when attempting to reallocate aligned memory. 
  Similar issues exist for various combinations of alignment, zeroing, padding, and extension/contraction with/without relocation.
  We developed a new *allocm() API that supports all reasonable combinations. For API details, see the jemalloc manual page. 
  We are currently using this feature for an optimized C++ string class that depends on reallocation succeeding only if it can be done in place. 
  We also have imminent plans to use it for aligned reallocation in a hash table implementation, which will simplify the existing application logic.

### Successes at Facebook
#####
Some of jemalloc's practical benefits for Facebook are difficult to quantify. 
For example, we have on numerous occasions used heap profiling on production systems to diagnose memory issues before they could cause service disruptions,
not to mention all the uses of heap profiling for development/optimization purposes. 
More generally, jemalloc's consistent behavior has allowed us to make more accurate memory utilization projections, 
which aids operations as well as long term infrastructure planning.
All that said, jemalloc does have one very tangible benefit: it is fast.

#####
Memory allocator microbenchmark results are notoriously difficult to extrapolate to real-world applications (though that doesn't stop people from trying).
Facebook devotes a significant portion of its infrastructure to machines that use HipHop to serve Web pages to users.
Although this is just one of many ways in which jemalloc is used at Facebook, it provides a striking real-world example of how much allocator performance can matter. 
We used a set of six identical machines, each with 8 CPU cores, to compare total server throughput. 
The machines served similar, though not identical, requests, over the course of one hour. 
We sampled at four-minute intervals (15 samples total), measured throughput as inversely related to CPU consumption, and computed relative averages. 
For one machine we used the default malloc implementation that is part of glibc 2.5,
and for the other five machines we used the LD_PRELOAD environment variable to load ptmalloc3, Hoard 3.8, concur1.0.2, tcmalloc 1.4, and jemalloc 2.1.0. 
Note that newer versions of glibc exist (we used the default for CentOS 5.2), and that the newest version of tcmalloc is 1.6,
but we encountered undiagnosed application instability when using versions 1.5 and 1.6.

#####
![Web server throughput.png](Web server throughput.png)

#####
Glibc derives its allocator from ptmalloc, so their performance similarity is no surprise. 
The Hoard allocator appears to spend a great deal of time contending on a spinlock, possibly as a side effect of its blowup avoidance algorithms.
The concur allocator appears to scale well, but it does not implement thread caching, so it incurs a substantial synchronization cost even though contention is low. 
tcmalloc under-performs jemalloc by about 4.5%.

#####
The main point of this experiment was to show the huge impact that allocator quality can have, as in glibc versus jemalloc, 
but we have performed numerous experiments at larger scales, using various hardware and client request loads,
in order to quantify the performance advantage of jemalloc over tcmalloc. 
In general we found that as the number of CPUs increases, the performance gap widens.
We interpret this to indicate that jemalloc will continue to scale as we deploy new hardware with ever-increasing CPU core counts.


### Future work(未来的工作)
#####
Jemalloc is now quite mature, but there remain known weaknesses, most of which involve how arenas are assigned to threads.
Consider an application that launches a pool of threads to populate a large static data structure,
then reverts to single-threaded operation for the remainder of execution.
Unless the application takes special action to control how arenas are assigned (or simply limits the number of arenas),
the initialization phase is apt to leave behind a wasteland of poorly utilized arenas (that is, high fragmentation).
Workarounds exist, but we intend to address this and other issues as they arise due to unforeseen application behavior.

### Acknowledgements(致谢)
#####
Although the vast majority of jemalloc was written by a single author, many others at Facebook,Mozilla, the FreeBSD Project, and elsewhere have contributed both directly and indirectly it its success. 
At Facebook the following people especially stand out: Keith Adams, Andrei Alexandrescu,Jordan DeLong, Mark Rabkin, Paul Saab, and Gary Wu.