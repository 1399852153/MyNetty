package com.my.netty.bytebuffer.netty.allocator;


import com.my.netty.bytebuffer.netty.allocator.enums.SizeClassEnum;

public class MySizeClassesMetadataItem {

    private int size;
    private SizeClassEnum sizeClassEnum;

    public MySizeClassesMetadataItem(int size, SizeClassEnum sizeClassEnum) {
        this.size = size;
        this.sizeClassEnum = sizeClassEnum;
    }

    public int getSize() {
        return size;
    }

    public SizeClassEnum getSizeClassEnum() {
        return sizeClassEnum;
    }

    @Override
    public String toString() {
        return "MySizeClassesMetadataItem{" +
            "size=" + size +
            ", sizeClassEnum=" + sizeClassEnum +
            '}';
    }
}
