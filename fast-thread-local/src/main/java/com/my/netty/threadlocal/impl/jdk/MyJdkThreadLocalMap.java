package com.my.netty.threadlocal.impl.jdk;

import java.lang.ref.WeakReference;

/**
 * 基本参考自jdk中的ThreadLocal类中的ThreadLocalMap内部类
 * */
public class MyJdkThreadLocalMap {

    public static class Entry extends WeakReference<MyJdkThreadLocal<?>> {
        Object value;

        Entry(MyJdkThreadLocal<?> k, Object v) {
            super(k);
            value = v;
        }
    }

    private static final int INITIAL_CAPACITY = 16;

    private Entry[] table;

    private int size = 0;

    private int threshold; // Default to 0

    public MyJdkThreadLocalMap() {
        table = new Entry[INITIAL_CAPACITY];
        setThresholdByLength(INITIAL_CAPACITY);
    }

    public void set(MyJdkThreadLocal<?> key, Object value) {
        Entry[] tab = table;
        int len = tab.length;
        int i = key.getThreadLocalHashCode() & (len-1);

        // 先找到目标ThreadLocal的hashCode对应的slot插槽
        // 1 如果对应插槽为null,直接不执行for循环逻辑
        for (Entry e = tab[i]; e != null; e = tab[i = nextIndex(i, len)]) {
            // 2 如果对应插槽不为null
            MyJdkThreadLocal<?> k = e.get();

            if (k == key) {
                // 如果对应slot上就是目标threadLocal,直接将value进行覆盖后直接返回
                e.value = value;
                return;
            }

            if (k == null) {
                // Entry不为null，但是key弱引用已经因为gc而为null了，将这个Entry给当前的key/value用
                replaceStaleEntry(key, value, i);
                return;
            }
        }

        // 找到了一个为空的slot，构建一个新的Entry放入该slot
        tab[i] = new Entry(key, value);
        // map的size自增1
        int sz = ++size;
        // 尝试清理一些slot
        if (!cleanSomeSlots(i, sz) && sz >= threshold) {
            // cleanSomeSlots返回false，说明没有找到可以清除的插槽。同时size已经达到了规定的阈值，map需要进行rehash扩容
            rehash();
        }
    }

    public Entry getEntry(MyJdkThreadLocal<?> key) {
        // 基于threadLocal的hashCode值，获得其slot下标
        int i = key.getThreadLocalHashCode() & (table.length - 1);
        Entry e = table[i];
        if (e != null && e.get() == key) {
            // 对应slot正好就是key对应的entry，直接返回
            return e;
        } else {
            // entry为空，或者对应的entry的key不匹配
            // 使用基于线性探测的开放寻址法，解决hashKey的冲突，尝试在当前slot后面的slot中去寻找key对应的entry
            return getEntryAfterMiss(key, i, e);
        }
    }

    public void remove(MyJdkThreadLocal<?> key){
        Entry[] tab = table;
        int len = tab.length;
        int i = key.getThreadLocalHashCode() & (len-1);
        // 遍历table，直到找到为null的插槽才退出
        for (Entry e = tab[i]; e != null; e = tab[i = nextIndex(i, len)]) {
            if (e.get() == key) {
                // 找到了key对应的entry，将key清理掉，然后再通过expungeStaleEntry将value和entry一并清除
                e.clear();
                expungeStaleEntry(i);
                return;
            }
        }

        // 从hashCode对应的slot开始遍历，并没有找到key对应的entry，说明对应的key不存在
    }

    private Entry getEntryAfterMiss(MyJdkThreadLocal<?> key, int i, Entry e) {
        Entry[] tab = table;
        int len = tab.length;

        while (e != null) {
            MyJdkThreadLocal<?> k = e.get();
            if (k == key) {
                return e;
            }
            if (k == null) {
                // 弱引用会导致一些entry对应的key过期，在发现entry不为null，但对应的key为null时，启发性的将过期的entry从当前map中清除出去
                expungeStaleEntry(i);
            } else {
                // Entry中的key不为null，继续向后遍历
                i = nextIndex(i, len);
            }
            e = tab[i];
        }

        // 从i开始，直到一个为null的slot，都没有找到匹配的key，返回null
        return null;
    }

    /**
     * 从头开始遍历整个table，将key为null的Entry清理掉
     * */
    private void expungeStaleEntries() {
        Entry[] tab = table;
        int len = tab.length;
        for (int j = 0; j < len; j++) {
            Entry e = tab[j];
            if (e != null && e.get() == null) {
                expungeStaleEntry(j);
            }
        }
    }

    private int expungeStaleEntry(int staleSlot) {
        Entry[] tab = table;
        int len = tab.length;

        // expunge entry at staleSlot
        // 只有当发现当前slot为null时，才会调用该方法。
        // 首先将对应entry和entry对应的value都设置为null，便于gc
        tab[staleSlot].value = null;
        tab[staleSlot] = null;
        // map的size自减1
        size--;

        // Rehash until we encounter null
        Entry e;
        int i;
        // 从当前插槽staleSlot开始向后遍历，直到发现一个为null的插槽才停止
        for (i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {
            MyJdkThreadLocal<?> k = e.get();
            if (k == null) {
                // 如果发现对应entry的key为null了，说明对应的threadLocal被回收了。和上面一样，把value和entry一并清空；同时size自减1
                e.value = null;
                tab[i] = null;
                size--;
            } else {
                // 获得当前key的hashCode对数组长度求余后的slot值
                int h = k.getThreadLocalHashCode() & (len - 1);
                if (h != i) {
                    // 如果不是最佳匹配，说明之前set时是顺延过了的，需要将该entry移动到距离最佳匹配slot(h)最近的位置中
                    // 首先将slot i清除掉
                    tab[i] = null;

                    // Unlike Knuth 6.4 Algorithm R, we must scan until
                    // null because multiple entries could have been stale.

                    // 然后从slot h开始向后遍历，直到找到一个null的slot，将当前entry放进去(最佳匹配slot(h)最近的位置)
                    while (tab[h] != null) {
                        h = nextIndex(h, len);
                    }
                    tab[h] = e;
                }
            }
        }

        // 返回为遍历到最后，为null的插槽的标识
        return i;
    }

    /**
     * 在set时，发现一个slot为null时被调用
     * 1 虽然staleSlot为null，但是有可能key对应的entry在后面的slot中，所以不能直接放在staleSlot上
     *   所以要从staleSlot开始向后遍历，最晚直到一个为null的slot才停止
     * 2 如果在遍历的过程中找到了key对应的slot，那么就替换掉value，提前退出扫描
     *   如果在遍历的过程中没有找到key对应的slot，那么就把新的key放到一开始staleSlot对应的slot上
     * 3 会尝试着在staleSlot的前面和后面查找key为null的slot时，进行一波清理
     * */
    private void replaceStaleEntry(MyJdkThreadLocal<?> key, Object value, final int staleSlot) {
        Entry[] tab = table;
        int len = tab.length;
        Entry e;

        // Back up to check for prior stale entry in current run.
        // We clean out whole runs at a time to avoid continual
        // incremental rehashing due to garbage collector freeing
        // up refs in bunches (i.e., whenever the collector runs).

        // 从当前slot向前遍历，将entry不为null，但key为null的entry都回收掉
        // 使用另一种回收的策略(因为大多数策略是从前往后遍历的，这里是从后往前)，来减少key被批量gc时造成持续不断地rehash
        int slotToExpunge = staleSlot;
        for (int i = prevIndex(staleSlot, len); (e = tab[i]) != null; i = prevIndex(i, len)) {
            if (e.get() == null) {
                slotToExpunge = i;
            }
        }

        // Find either the key or trailing null slot of run, whichever occurs first
        // 从staleSlot开始遍历，直到找到一个空的slot退出循环
        for (int i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {
            MyJdkThreadLocal<?> k = e.get();

            // If we find key, then we need to swap it
            // with the stale entry to maintain hash table order.
            // The newly stale slot, or any other stale slot
            // encountered above it, can then be sent to expungeStaleEntry
            // to remove or rehash all of the other entries in run.

            // 注意，此方法只在set时被调用
            if (k == key) {
                // 当找到了与set指定的key匹配的，先把value替换下
                e.value = value;

                // staleSlot对应的slot中key是为null的，
                // 交换下，把这个staleSlot下标对应的slot让给当前的key用
                tab[i] = tab[staleSlot];
                tab[staleSlot] = e;

                // Start expunge at preceding stale entry if it exists
                if (slotToExpunge == staleSlot) {
                    // staleSlot之前的entry不为null，从i开始尝试清理一遍过期的entry(前面向前遍历时slotToExpunge没有变化)
                    slotToExpunge = i;
                }else{
                    // 否则，cleanSomeSlots时将staleSlot前面那部分key为null的过期Entry清理掉
                }

                // 进行一波清理
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                return;
            }

            // If we didn't find stale entry on backward scan, the
            // first stale entry seen while scanning for key is the
            // first still present in the run.

            // 如果在向后扫描时没有找到过期的entry
            // 那么在扫描时遇到的第一个过期entry就是在该运行中仍然存在的第一个过期entry
            if (k == null && slotToExpunge == staleSlot) {
                slotToExpunge = i;
            }
        }

        // If key not found, put new entry in stale slot
        // 扫描的过程中没有找到key对应的Entry，就将原来的那个key为null的Entry的value置为null
        // 同时将空的这个slot放上新的Entry(set方法时调用)
        tab[staleSlot].value = null;
        tab[staleSlot] = new Entry(key, value);

        // If there are any other stale entries in run, expunge them

        // 走到这里说明在遍历的过程中没有找到key匹配的entry，没有提前返回
        // slotToExpunge不等于staleSlot
        // 第1种可能：在一开始向前查找的过程中，发现了key为null的slot，从对应前置位点开始清理一波过期的slot
        // 第2种可能：在向后遍历的过程中，找到了为null的slot，退出了循环，那么就从这里开始检查，尝试清理一波过期slot
        if (slotToExpunge != staleSlot) {
            cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }
    }

    /**
     * 以i开始遍历之后的插槽，以至少log2(n)的次数进行检查，线性扫描并删除过期的Entry
     *
     * @return 是否删除了至少一个过期的Entry
     * */
    private boolean cleanSomeSlots(int i, int n) {
        boolean removed = false;
        Entry[] tab = table;
        int len = tab.length;
        do {
            // 找到i的下一个entry
            i = nextIndex(i, len);
            Entry e = tab[i];
            if (e != null && e.get() == null) {
                // 发现了一个过期的Entry,令n=len，相当于重置了需要扫描的次数
                // 因为过期的entry，说明内存泄露的情况可能比较严重，加大扫描的次数，增加清理的力度
                n = len;
                removed = true;
                // 清理一波过期的entry
                i = expungeStaleEntry(i);
            }

            // 通过左移1位的方式，控制尝试清理的次数至少为log2(n)，
            // 如果清理过程中发现了过期的entry，会执行更多次
        } while ( (n >>>= 1) != 0);

        return removed;
    }

    /**
     * 准备以2次幂对hash表进行扩容
     * */
    private void rehash() {
        // 准备扩容前先把所有的过期entry清理掉
        expungeStaleEntries();

        // Use lower threshold for doubling to avoid hysteresis

        // 如果清理掉了过期entry后，元素个数还是高于阈值
        if (size >= threshold - threshold / 4) {
            // 2次幂对hash表进行扩容
            resize();
        }
    }

    /**
     * 以2次幂对hash表进行扩容
     */
    private void resize() {
        Entry[] oldTab = table;
        int oldLen = oldTab.length;
        int newLen = oldLen * 2;
        // 新的entry数组为之前的两倍大
        Entry[] newTab = new Entry[newLen];
        int count = 0;

        for (Entry e : oldTab) {
            if (e != null) {
                MyJdkThreadLocal<?> k = e.get();
                if (k == null) {
                    // 最后再确认一遍key是否为null(因为在这个过程中可能gc过)
                    // 为null的就不再放入新的table中了
                    e.value = null; // Help the GC
                } else {
                    int h = k.getThreadLocalHashCode() & (newLen - 1);
                    while (newTab[h] != null) {
                        // 开放定址法放入entry
                        h = nextIndex(h, newLen);
                    }
                    newTab[h] = e;
                    count++;
                }
            }
        }

        // 扩容完毕，设置阈值和size
        setThresholdByLength(newLen);
        size = count;
        table = newTab;
    }

    private static int prevIndex(int i, int len) {
        // 做下回环，避免下标越界
        return ((i - 1 >= 0) ? i - 1 : len - 1);
    }

    private static int nextIndex(int i, int len) {
        // 做下回环，避免下标越界
        return ((i + 1 < len) ? i + 1 : 0);
    }

    private void setThresholdByLength(int len) {
        // Set the resize threshold to maintain at worst a 2/3 load factor.
        // 元素数量超过了map数组长度的2/3时触发map的扩容(resize)

        // 注意：threshold和len的比例一定要小于1，因为是开放定位法，否则会导致数组装满而放不下新元素
        threshold = len * 2 / 3;
    }
}
