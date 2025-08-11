package com.my.netty.bytebuffer.netty.allocator;


import com.my.netty.bytebuffer.netty.allocator.enums.SizeClassEnum;
import com.my.netty.bytebuffer.netty.util.MathUtil;

/**
 * 功能与Netty的SizeClasses类似，
 * netty里支持动态配置pageSize和chunkSize来调优，并能够得到合理的伸缩比例，所以实现的比较复杂
 * MySizeClasses这里size的规格全部写死为netty的默认值，简化复杂度
 * */
public class MySizeClasses {

    /**
     * 页大小直接写死，8K
     * */
    private final int pageSize = 8192;

    /**
     * 页大小的log2对数
     * log2(8192) = 13
     * */
    private final int pageShifts = 13;

    /**
     * 一共有多个个Page类型的规格(所有的规格，恰好是pageSize的倍数的就是Page类型的规格，比如8K，16K，24K以此类推)
     * */
    private final int nPageSizes;

    /**
     * chunk大小直接写死，4M
     * */
    private final int chunkSize = 1024 * 1024 * 4;

    /**
     * size低于该值的直接通过16的倍数索引数组(size2idxTab)来快速匹配
     * */
    private final int lookupMaxSize = 4096;

    /**
     * QUANTUM是量子的意思，即最小的规格 是16
     * LOG2_QUANTUM = (log2(16) = 4)
     * */
    private final int LOG2_QUANTUM = 4;

    /**
     * 每一个规格组的大小固定为4，组内每一个size规格的间距是固定的
     * */
    private static final int LOG2_SIZE_CLASS_GROUP = 2;

    private final MySizeClassesMetadataItem[] sizeTable;

    private final MySizeClassesMetadataItem[] size2idxTab;

    private final MySizeClassesMetadataItem[] pageIdx2sizeTab;

    public MySizeClasses() {
        int sizeNum = 69;
        sizeTable = new MySizeClassesMetadataItem[sizeNum];

        // 简单起见，写死size表规格
        // 规格跨度逐渐增加。绝大多数情况下，越小的内存规格，被申请的次数越多，因此更为精确的小规格配合slab分配可以显著减少内部内存碎片
        // 对于较大的规格(比如normal级别的)，如果还是按照几十的间距来设置规格(1k-2k之间就有几十个不同的规格)，就会导致过多的元数据需要维护，产生过多的外部碎片而浪费内存。
        // normal级别的内存通过伙伴算法进行分配，可以在内部内存碎片和外部内存碎片的取舍上得到一个不错的空间效率
        sizeTable[0] = new MySizeClassesMetadataItem(16, SizeClassEnum.SMALL);
        sizeTable[1] = new MySizeClassesMetadataItem(32, SizeClassEnum.SMALL);
        sizeTable[2] = new MySizeClassesMetadataItem(48, SizeClassEnum.SMALL);
        sizeTable[3] = new MySizeClassesMetadataItem(64, SizeClassEnum.SMALL);//组内固定间距16(2^4)

        sizeTable[4] = new MySizeClassesMetadataItem(80, SizeClassEnum.SMALL);
        sizeTable[5] = new MySizeClassesMetadataItem(96, SizeClassEnum.SMALL);
        sizeTable[6] = new MySizeClassesMetadataItem(112, SizeClassEnum.SMALL);
        sizeTable[7] = new MySizeClassesMetadataItem(128, SizeClassEnum.SMALL);//组内固定间距16(2^4)

        sizeTable[8] = new MySizeClassesMetadataItem(160, SizeClassEnum.SMALL);
        sizeTable[9] = new MySizeClassesMetadataItem(192, SizeClassEnum.SMALL);
        sizeTable[10] = new MySizeClassesMetadataItem(224, SizeClassEnum.SMALL);
        sizeTable[11] = new MySizeClassesMetadataItem(256, SizeClassEnum.SMALL);//组内固定间距32(2^5)

        sizeTable[12] = new MySizeClassesMetadataItem(320, SizeClassEnum.SMALL);
        sizeTable[13] = new MySizeClassesMetadataItem(384, SizeClassEnum.SMALL);
        sizeTable[14] = new MySizeClassesMetadataItem(448, SizeClassEnum.SMALL);
        sizeTable[15] = new MySizeClassesMetadataItem(512, SizeClassEnum.SMALL);//组内固定间距64(2^6)

        sizeTable[16] = new MySizeClassesMetadataItem(640, SizeClassEnum.SMALL);
        sizeTable[17] = new MySizeClassesMetadataItem(768, SizeClassEnum.SMALL);
        sizeTable[18] = new MySizeClassesMetadataItem(896, SizeClassEnum.SMALL);
        sizeTable[19] = new MySizeClassesMetadataItem(1024, SizeClassEnum.SMALL);//组内固定间距128(2^7)

        sizeTable[20] = new MySizeClassesMetadataItem(1280, SizeClassEnum.SMALL);
        sizeTable[21] = new MySizeClassesMetadataItem(1536, SizeClassEnum.SMALL);
        sizeTable[22] = new MySizeClassesMetadataItem(1792, SizeClassEnum.SMALL);
        sizeTable[23] = new MySizeClassesMetadataItem(1024 * 2, SizeClassEnum.SMALL);//组内固定间距256(2^8)

        sizeTable[24] = new MySizeClassesMetadataItem((int) (1024 * 2.5), SizeClassEnum.SMALL);
        sizeTable[25] = new MySizeClassesMetadataItem(1024 * 3, SizeClassEnum.SMALL);
        sizeTable[26] = new MySizeClassesMetadataItem((int) (1024 * 3.5), SizeClassEnum.SMALL);
        sizeTable[27] = new MySizeClassesMetadataItem(1024 * 4, SizeClassEnum.SMALL);//组内固定间距512(2^9)

        sizeTable[28] = new MySizeClassesMetadataItem(1024 * 5, SizeClassEnum.SMALL);
        sizeTable[29] = new MySizeClassesMetadataItem(1024 * 6, SizeClassEnum.SMALL);
        sizeTable[30] = new MySizeClassesMetadataItem(1024 * 7, SizeClassEnum.SMALL);
        sizeTable[31] = new MySizeClassesMetadataItem(1024 * 8, SizeClassEnum.NORMAL);//组内固定间距1024(2^10) 大于等于PageSize的都是Normal级别

        sizeTable[32] = new MySizeClassesMetadataItem(1024 * 10, SizeClassEnum.NORMAL);
        sizeTable[33] = new MySizeClassesMetadataItem(1024 * 12, SizeClassEnum.NORMAL);
        sizeTable[34] = new MySizeClassesMetadataItem(1024 * 14, SizeClassEnum.NORMAL);
        sizeTable[35] = new MySizeClassesMetadataItem(1024 * 16, SizeClassEnum.NORMAL);//组内固定间距2048(2^11)

        sizeTable[36] = new MySizeClassesMetadataItem(1024 * 20, SizeClassEnum.NORMAL);
        sizeTable[37] = new MySizeClassesMetadataItem(1024 * 24, SizeClassEnum.NORMAL);
        sizeTable[38] = new MySizeClassesMetadataItem(1024 * 28, SizeClassEnum.NORMAL);
        sizeTable[39] = new MySizeClassesMetadataItem(1024 * 32, SizeClassEnum.NORMAL);//组内固定间距4096(2^12)

        sizeTable[40] = new MySizeClassesMetadataItem(1024 * 40, SizeClassEnum.NORMAL);
        sizeTable[41] = new MySizeClassesMetadataItem(1024 * 48, SizeClassEnum.NORMAL);
        sizeTable[42] = new MySizeClassesMetadataItem(1024 * 56, SizeClassEnum.NORMAL);
        sizeTable[43] = new MySizeClassesMetadataItem(1024 * 64, SizeClassEnum.NORMAL);//组内固定间距8192(2^13)

        sizeTable[44] = new MySizeClassesMetadataItem(1024 * 80, SizeClassEnum.NORMAL);
        sizeTable[45] = new MySizeClassesMetadataItem(1024 * 96, SizeClassEnum.NORMAL);
        sizeTable[46] = new MySizeClassesMetadataItem(1024 * 112, SizeClassEnum.NORMAL);
        sizeTable[47] = new MySizeClassesMetadataItem(1024 * 128, SizeClassEnum.NORMAL);//组内固定间距16384(2^14)

        sizeTable[48] = new MySizeClassesMetadataItem(1024 * 160, SizeClassEnum.NORMAL);
        sizeTable[49] = new MySizeClassesMetadataItem(1024 * 192, SizeClassEnum.NORMAL);
        sizeTable[50] = new MySizeClassesMetadataItem(1024 * 224, SizeClassEnum.NORMAL);
        sizeTable[51] = new MySizeClassesMetadataItem(1024 * 256, SizeClassEnum.NORMAL);//组内固定间距32768(2^15)

        sizeTable[52] = new MySizeClassesMetadataItem(1024 * 320, SizeClassEnum.NORMAL);
        sizeTable[53] = new MySizeClassesMetadataItem(1024 * 384, SizeClassEnum.NORMAL);
        sizeTable[54] = new MySizeClassesMetadataItem(1024 * 448, SizeClassEnum.NORMAL);
        sizeTable[55] = new MySizeClassesMetadataItem(1024 * 512, SizeClassEnum.NORMAL);//组内固定间距65536(2^16)

        sizeTable[56] = new MySizeClassesMetadataItem(1024 * 640, SizeClassEnum.NORMAL);
        sizeTable[57] = new MySizeClassesMetadataItem(1024 * 768, SizeClassEnum.NORMAL);
        sizeTable[58] = new MySizeClassesMetadataItem(1024 * 896, SizeClassEnum.NORMAL);
        sizeTable[59] = new MySizeClassesMetadataItem(1024 * 1024, SizeClassEnum.NORMAL);//组内固定间距131072(2^17)

        sizeTable[60] = new MySizeClassesMetadataItem((int) (1024 * 1024 * 1.25), SizeClassEnum.NORMAL);
        sizeTable[61] = new MySizeClassesMetadataItem((int) (1024 * 1024 * 1.5), SizeClassEnum.NORMAL);
        sizeTable[62] = new MySizeClassesMetadataItem((int) (1024 * 1024 * 1.75), SizeClassEnum.NORMAL);
        sizeTable[63] = new MySizeClassesMetadataItem(1024 * 1024 * 2, SizeClassEnum.NORMAL);//组内固定间距262144(2^18)

        sizeTable[64] = new MySizeClassesMetadataItem((int) (1024 * 1024 * 2.5), SizeClassEnum.NORMAL);
        sizeTable[65] = new MySizeClassesMetadataItem(1024 * 1024 * 3, SizeClassEnum.NORMAL);
        sizeTable[66] = new MySizeClassesMetadataItem((int) (1024 * 1024 * 3.5), SizeClassEnum.NORMAL);
        sizeTable[67] = new MySizeClassesMetadataItem(1024 * 1024 * 4, SizeClassEnum.NORMAL);//组内固定间距524288(2^19)

        // 一个chunk就是4M，超过chunk级别的规格就是Huge类型了,这个是MyNetty额外加的一项
        sizeTable[68] = new MySizeClassesMetadataItem(0, SizeClassEnum.HUGE);

        // 计算一共有多个个Page类型的规格(所有的规格，恰好是pageSize的倍数的就是Page类型的规格，比如8K，16K，24K以此类推)
        int nPageSizes = 0;
        for (MySizeClassesMetadataItem item : sizeTable) {
            int size = item.getSize();
            if (size >= pageSize & size % pageSize == 0) {
                nPageSizes++;
            }
        }
        this.nPageSizes = nPageSizes;

        // 构建lookupMaxSize以下规格的快速查找索引表
        this.size2idxTab = buildSize2idxTab(lookupMaxSize,sizeTable);
        this.pageIdx2sizeTab = newPageIdx2sizeTab(sizeTable, sizeTable.length-1, this.nPageSizes);
    }

    private MySizeClassesMetadataItem[] buildSize2idxTab(int lookupMaxSize, MySizeClassesMetadataItem[] sizeTable) {
        MySizeClassesMetadataItem[] size2idxTab = new MySizeClassesMetadataItem[lookupMaxSize >> LOG2_QUANTUM];

        int size2idxTabIndex = 0;
        int lastQuantumBaseSize = 0;
        for(int i=0; i<sizeTable.length; i++){
            MySizeClassesMetadataItem item = sizeTable[i];

            // 量子基数(size都是QUANTUM(16)的倍数，比如size=16，那就是1；size=32，那就是2 依此类推)
            int quantumBaseSize = item.getSize() >> LOG2_QUANTUM;

            for(int j=lastQuantumBaseSize; j<quantumBaseSize; j++){
                if(size2idxTabIndex < size2idxTab.length){
                    size2idxTab[size2idxTabIndex] = item;
                    size2idxTabIndex++;
                }
            }

            lastQuantumBaseSize = quantumBaseSize;
        }

        return size2idxTab;
    }

    private MySizeClassesMetadataItem[] newPageIdx2sizeTab(MySizeClassesMetadataItem[] sizeTable, int nSizes, int nPSizes) {
        MySizeClassesMetadataItem[] pageIdx2sizeTab = new MySizeClassesMetadataItem[nPSizes];
        int pageIdx = 0;
        for (int i = 0; i < nSizes; i++) {
            MySizeClassesMetadataItem item = sizeTable[i];
            int size = item.getSize();
            if (size >= pageSize && size % pageSize == 0) {
                pageIdx2sizeTab[pageIdx] = item;
                pageIdx++;
            }
        }
        return pageIdx2sizeTab;
    }

    public MySizeClassesMetadataItem size2SizeIdx(int size) {
        if (size == 0) {
            return sizeTable[0];
        }
        if (size > chunkSize) {
            // 申请的是huge级别的内存规格
            return sizeTable[sizeTable.length-1];
        }

        // 简单起见，不支持配置内存对齐(directMemoryCacheAlignment)

        if (size <= lookupMaxSize) {
            // 小于4096的size规格，可以通过以16为基数的索引表，以O(1)的时间复杂度直接找到对应的规格
            // size-1 / MIN_TINY
            return size2idxTab[size - 1 >> LOG2_QUANTUM];
        }

        // 申请规格对应的向上取整的2次幂（比如15，sizeToClosestLog2就是4，代表距离最近的二次幂规格是16）
        int sizeToClosestLog2 = MathUtil.log2((size << 1) - 1);

        // size所在内存规格组编号，每一个组内包含4个规格(LOG2_SIZE_CLASS_GROUP=2),每个组内第4个也是最后一个规格是64的倍数(2^6)
        // 第一个组最后一个规格为64，第二组最后一个规格为64*2=128，第三组最后一个规格为64*4=128*2=256，依次类推，每一组的最后一个规格都是前一组最后一个规格的2倍
        // 所以size在二次幂向上对其后，除以64就能得到其所属的组的编号(log2对数就是直接减6(2+4))
        // lookupMaxSize很大，所以sizeGroupShift肯定大于0
        int sizeGroupShift = sizeToClosestLog2 - (LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM);;

        // 每个组内有4个规格，所以sizeGroupShift*4后获得对应的组的起始规格下标号
        int groupFirstIndex = sizeGroupShift << LOG2_SIZE_CLASS_GROUP;

        // 组内规格之间固定的间隔(每一组内部有4个规格，而相邻两组之间规格相差2倍，所以除以8就能得到组内的固定间隔)
        int log2Delta = sizeToClosestLog2 - LOG2_SIZE_CLASS_GROUP - 1;

        // size最贴近组内哪一个规格
        // (size - 1 >> log2Delta)得到当前size具体是几倍的log2Delta
        // 组内共(1 << LOG2_SIZE_CLASS_GROUP)=4个规格，因此对4求余数(x为二次幂可以通过&(x-1)的方式优化)，就能得到组内具体的规格项下标
        int mod = size - 1 >> log2Delta & (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        // 最终确定实际对应内存规格的下标
        int finallySizeIndex = groupFirstIndex + mod;
        // 返回最终所匹配到的规格信息
        return sizeTable[finallySizeIndex];
    }

    public MySizeClassesMetadataItem sizeIdx2size(int sizeIdx) {
        return sizeTable[sizeIdx];
    }

    /**
     * 申请pages个连续页时，向上规格化后所对应的规格
     * */
    public int pages2pageIdx(int pages) {
        return pages2pageIdxCompute(pages, false);
    }

    /**
     * 申请pages个连续页时，向下规格化后所对应的规格
     * */
    public int pages2pageIdxFloor(int pages) {
        return pages2pageIdxCompute(pages, true);
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getNPageSizes() {
        return nPageSizes;
    }

    public int getPageShifts() {
        return pageShifts;
    }

    public int getChunkSize() {
        return chunkSize;
    }



    /**
     * 和size2SizeIdx逻辑几乎一样，也是基于有规律的pageIdx2sizeTab表来快速定位对应规格
     * */
    private int pages2pageIdxCompute(int pages, boolean floor) {
        // 将申请的总页数 乘以 页大小 得到总共需要申请的size之和
        int pageSize = pages << pageShifts;
        if (pageSize > chunkSize) {
            // 申请的是huge级别的内存规格
            return this.pageIdx2sizeTab.length;
        }

        // 申请规格对应的向上取整的2次幂（比如8100，sizeToClosestLog2就是13，代表距离最近的二次幂规格是8192）
        int sizeToClosestLog2 = MathUtil.log2((pageSize << 1) - 1);

        // pageSize所在内存规格组编号，每一个组内包含4个规格(LOG2_SIZE_CLASS_GROUP=2),单位起步是页级别(PAGE_SHIFTS)
        int sizeGroupShift;
        if(sizeToClosestLog2 < LOG2_SIZE_CLASS_GROUP + pageShifts){
            // 说明是第一组内的规格
            sizeGroupShift = 0;
        }else{
            sizeGroupShift = sizeToClosestLog2 - (LOG2_SIZE_CLASS_GROUP + pageShifts);
        }

        // 每个组内有4个规格，所以sizeGroupShift*4后获得对应的组的起始规格下标号
        int groupFirstIndex = sizeGroupShift << LOG2_SIZE_CLASS_GROUP;

        // 组内规格之间固定的间隔(每一组内部有4个规格)
        int log2Delta;
        if(sizeToClosestLog2 < LOG2_SIZE_CLASS_GROUP + pageShifts + 1){
            // 前两个group的组内间隔都是8K
            log2Delta = pageShifts;
        }else{
            // 后面每个组的组内间隔，相比前一个组都大两倍
            log2Delta = sizeToClosestLog2 - LOG2_SIZE_CLASS_GROUP - 1;
        }

        // pageSize最贴近组内哪一个规格
        // (pageSize - 1 & deltaInverseMask)得到当前pageSize具体是几倍的log2Delta
        // 组内共(1 << LOG2_SIZE_CLASS_GROUP)=4个规格，因此对4求余数(x为二次幂可以通过&(x-1)的方式优化)，就能得到组内具体的规格项下标
        int deltaInverseMask = -1 << log2Delta;
        int mod = (pageSize - 1 & deltaInverseMask) >> log2Delta & (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        int pageIdx = groupFirstIndex + mod;

        if (floor && pageIdx2sizeTab[pageIdx].getSize() > pages << pageShifts) {
            // 默认都是向上取整的，如果floor=true，且当前向上取整的size规格确实大于pages对应的size，则返回前一个规格
            return pageIdx-1;
        }else{
            return pageIdx;
        }
    }
}
