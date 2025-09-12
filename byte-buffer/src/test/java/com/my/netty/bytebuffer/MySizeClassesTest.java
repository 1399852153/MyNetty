package com.my.netty.bytebuffer;


import com.my.netty.bytebuffer.netty.allocator.MySizeClasses;
import org.junit.Assert;
import org.junit.Test;

public class MySizeClassesTest {

    @Test
    public void testSize2SizeIdx() {
        MySizeClasses mySizeClasses = new MySizeClasses();

        Assert.assertEquals(mySizeClasses.size2SizeIdx(0).getSize(), 16);
        Assert.assertEquals(mySizeClasses.size2SizeIdx(11).getSize(), 16);
        Assert.assertEquals(mySizeClasses.size2SizeIdx(15).getSize(), 16);
        Assert.assertEquals(mySizeClasses.size2SizeIdx(16).getSize(), 16);
        Assert.assertEquals(mySizeClasses.size2SizeIdx(18).getSize(), 32);
        Assert.assertEquals(mySizeClasses.size2SizeIdx(31).getSize(), 32);
        Assert.assertEquals(mySizeClasses.size2SizeIdx(32).getSize(), 32);
        Assert.assertEquals(mySizeClasses.size2SizeIdx(33).getSize(), 48);
        Assert.assertEquals(mySizeClasses.size2SizeIdx(4096).getSize(), 1024 * 4);
        Assert.assertEquals(mySizeClasses.size2SizeIdx(4097).getSize(), 1024 * 5);
        Assert.assertEquals(mySizeClasses.size2SizeIdx(8100).getSize(), 8192);
        Assert.assertEquals(mySizeClasses.size2SizeIdx(8193).getSize(), 1024 * 10);
    }

    @Test
    public void testPages2pageIdx() {
        MySizeClasses mySizeClasses = new MySizeClasses();

        Assert.assertEquals(mySizeClasses.pages2pageIdx(1), 0);
        Assert.assertEquals(mySizeClasses.pages2pageIdx(2), 1);
        Assert.assertEquals(mySizeClasses.pages2pageIdx(10), 8);
        Assert.assertEquals(mySizeClasses.pages2pageIdx(12), 9);
        Assert.assertEquals(mySizeClasses.pages2pageIdx(31), 15);

        Assert.assertEquals(mySizeClasses.pages2pageIdxFloor(12), 9);
        Assert.assertEquals(mySizeClasses.pages2pageIdxFloor(31), 14);
    }
}
