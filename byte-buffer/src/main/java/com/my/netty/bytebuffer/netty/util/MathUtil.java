package com.my.netty.bytebuffer.netty.util;

public class MathUtil {

    private static final int INTEGER_SIZE_MINUS_ONE = Integer.SIZE - 1;

    public static boolean isOutOfBounds(int index, int length, int capacity) {
        return (index | length | capacity | (index + length) | (capacity - (index + length))) < 0;
    }

    public static int log2(int val) {
        return INTEGER_SIZE_MINUS_ONE - Integer.numberOfLeadingZeros(val);
    }

    public static int findNextPositivePowerOfTwo(final int value) {
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }

    /**
     * Fast method of finding the next power of 2 greater than or equal to the supplied value.
     * <p>This method will do runtime bounds checking and call {@link #findNextPositivePowerOfTwo(int)} if within a
     * valid range.
     * @param value from which to search for next power of 2
     * @return The next power of 2 or the value itself if it is a power of 2.
     * <p>Special cases for return values are as follows:
     * <ul>
     *     <li>{@code <= 0} -> 1</li>
     *     <li>{@code >= 2^30} -> 2^30</li>
     * </ul>
     */
    public static int safeFindNextPositivePowerOfTwo(final int value) {
        // return value <= 0 ? 1 : value >= 0x40000000 ? 0x40000000 : findNextPositivePowerOfTwo(value);

        if(value <= 0){
            return 1;
        }

        if(value >= 0x40000000){
            return 0x40000000;
        }else{
            return findNextPositivePowerOfTwo(value);
        }
    }

}
