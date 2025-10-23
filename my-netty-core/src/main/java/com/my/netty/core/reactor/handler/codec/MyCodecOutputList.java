/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.my.netty.core.reactor.handler.codec;

import com.my.netty.bytebuffer.netty.util.MathUtil;
import com.my.netty.bytebuffer.netty.util.ObjectUtil;
import com.my.netty.threadlocal.impl.netty.MyFastThreadLocal;

import java.util.AbstractList;
import java.util.RandomAccess;


/**
 * 几乎完全copy自netty的CodecOutputList类
 *
 * 相比ArrayList主要有几点优化
 * 1. 提供了池化机制，避免每次Decode时都需要创建一个新的List，降低GC的压力(销毁时，直接clear，令size=0)
 * 2. 提供了不需要检查下标越界的get方法(getUnsafe)，遍历操作性能更优
 */
final class MyCodecOutputList extends AbstractList<Object> implements RandomAccess {

    private static final CodecOutputListRecycler NOOP_RECYCLER = new CodecOutputListRecycler() {
        @Override
        public void recycle(MyCodecOutputList object) {
            // drop on the floor and let the GC handle it.
        }
    };

    private static final MyFastThreadLocal<CodecOutputLists> CODEC_OUTPUT_LISTS_POOL =
            new MyFastThreadLocal<CodecOutputLists>() {
                @Override
                protected CodecOutputLists initialValue(){
                    // 16 CodecOutputList per Thread are cached.
                    return new CodecOutputLists(16);
                }
            };

    private interface CodecOutputListRecycler {
        void recycle(MyCodecOutputList myCodecOutputList);
    }

    private static final class CodecOutputLists implements CodecOutputListRecycler {
        private final MyCodecOutputList[] elements;
        private final int mask;

        private int currentIdx;
        private int count;

        CodecOutputLists(int numElements) {
            elements = new MyCodecOutputList[MathUtil.safeFindNextPositivePowerOfTwo(numElements)];
            for (int i = 0; i < elements.length; ++i) {
                // Size of 16 should be good enough for the majority of all users as an initial capacity.
                elements[i] = new MyCodecOutputList(this, 16);
            }
            count = elements.length;
            currentIdx = elements.length;
            mask = elements.length - 1;
        }

        public MyCodecOutputList getOrCreate() {
            if (count == 0) {
                // Return a new CodecOutputList which will not be cached. We use a size of 4 to keep the overhead
                // low.
                return new MyCodecOutputList(NOOP_RECYCLER, 4);
            }
            --count;

            int idx = (currentIdx - 1) & mask;
            MyCodecOutputList list = elements[idx];
            currentIdx = idx;
            return list;
        }

        @Override
        public void recycle(MyCodecOutputList myCodecOutputList) {
            int idx = currentIdx;
            elements[idx] = myCodecOutputList;
            currentIdx = (idx + 1) & mask;
            ++count;
            assert count <= elements.length;
        }
    }

    static MyCodecOutputList newInstance() {
        return CODEC_OUTPUT_LISTS_POOL.get().getOrCreate();
    }

    private final CodecOutputListRecycler recycler;
    private int size;
    private Object[] array;
    private boolean insertSinceRecycled;

    private MyCodecOutputList(CodecOutputListRecycler recycler, int size) {
        this.recycler = recycler;
        array = new Object[size];
    }

    @Override
    public Object get(int index) {
        checkIndex(index);
        return array[index];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(Object element) {
        ObjectUtil.checkNotNull(element, "element");
        try {
            insert(size, element);
        } catch (IndexOutOfBoundsException ignore) {
            // This should happen very infrequently so we just catch the exception and try again.
            expandArray();
            insert(size, element);
        }
        ++ size;
        return true;
    }

    @Override
    public Object set(int index, Object element) {
        ObjectUtil.checkNotNull(element, "element");
        checkIndex(index);

        Object old = array[index];
        insert(index, element);
        return old;
    }

    @Override
    public void add(int index, Object element) {
        ObjectUtil.checkNotNull(element, "element");
        checkIndex(index);

        if (size == array.length) {
            expandArray();
        }

        if (index != size) {
            System.arraycopy(array, index, array, index + 1, size - index);
        }

        insert(index, element);
        ++ size;
    }

    @Override
    public Object remove(int index) {
        checkIndex(index);
        Object old = array[index];

        int len = size - index - 1;
        if (len > 0) {
            System.arraycopy(array, index + 1, array, index, len);
        }
        array[-- size] = null;

        return old;
    }

    @Override
    public void clear() {
        // We only set the size to 0 and not null out the array. Null out the array will explicit requested by
        // calling recycle()
        size = 0;
    }

    /**
     * Returns {@code true} if any elements where added or set. This will be reset once {@link #recycle()} was called.
     */
    boolean insertSinceRecycled() {
        return insertSinceRecycled;
    }

    /**
     * Recycle the array which will clear it and null out all entries in the internal storage.
     */
    void recycle() {
        for (int i = 0 ; i < size; i ++) {
            array[i] = null;
        }
        size = 0;
        insertSinceRecycled = false;

        recycler.recycle(this);
    }

    /**
     * Returns the element on the given index. This operation will not do any range-checks and so is considered unsafe.
     */
    Object getUnsafe(int index) {
        return array[index];
    }

    private void checkIndex(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException("expected: index < ("
                    + size + "),but actual is (" + size + ")");
        }
    }

    private void insert(int index, Object element) {
        array[index] = element;
        insertSinceRecycled = true;
    }

    private void expandArray() {
        // double capacity
        int newCapacity = array.length << 1;

        if (newCapacity < 0) {
            throw new OutOfMemoryError();
        }

        Object[] newArray = new Object[newCapacity];
        System.arraycopy(array, 0, newArray, 0, array.length);

        array = newArray;
    }
}
