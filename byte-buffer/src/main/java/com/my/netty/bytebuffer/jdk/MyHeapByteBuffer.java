package com.my.netty.bytebuffer.jdk;

public class MyHeapByteBuffer extends MyByteBuffer{

    MyHeapByteBuffer(int cap, int lim) {
        super(-1, 0, lim, cap, new byte[cap], 0);
    }

    protected int ix(int i) {
        return i + offset;
    }

    @Override
    public byte get() {
        // 获得position的位置(相对操作，有副作用，会推进position)
        int nextGetIndex = nextGetIndex();
        // 加上offset偏移量获得最终的index值
        int finallyIndex = ix(nextGetIndex);
        return hb[finallyIndex];
    }

    @Override
    public byte get(int index) {
        // 检查index的合法性，必须是0 <= index < limit，避免越界
        checkIndex(index);
        // 加上offset偏移量获得最终的index值
        int finallyIndex = ix(index);
        return hb[finallyIndex];
    }

    @Override
    public MyByteBuffer put(byte b) {
        // 获得position的位置(相对操作，有副作用，会推进position)
        int nextPutIndex = nextPutIndex();
        // 加上offset偏移量获得最终的index值
        int finallyIndex = ix(nextPutIndex);

        // 将b放入对应的index处
        hb[finallyIndex] = b;
        return this;
    }

    @Override
    public MyByteBuffer put(int index, byte b) {
        // 检查index的合法性，必须是0 <= index < limit，避免越界
        checkIndex(index);

        // 加上offset偏移量获得最终的index值
        int finallyIndex = ix(index);

        // 将b放入对应的index处
        hb[finallyIndex] = b;
        return this;
    }

    @Override
    byte _get(int i) {
        return hb[i];
    }

    @Override
    void _put(int i, byte b) {
        hb[i] = b;
    }

    @Override
    public MyByteBuffer compact() {

        // The bytes between the buffer's current position and its limit,
        // if any, are copied to the beginning of the buffer
        // 把底层数组中(position+offset)到(limit+offset)之间的内容整体往前挪，挪到数组起始处(0+offset), 实际内容的长度=remaining
        System.arraycopy(hb, ix(getPosition()), hb, ix(0), remaining());

        // That is, the byte at index p=position() is copied to index zero, the byte at index p+1 is copied to index one,
        // and so forth until the byte at index limit()-1 is copied to index n=limit()-1-p

        // 要理解下面position和limit的变化，需要意识到compact是一次从读模式切换到写模式的操作(之前读完了，就把剩下的还没有读完的压缩整理一下)

        // 压缩后实际内容的长度=remaining,所以压缩完后position，也就是后续开始写的位置就是从remaining开始
        // The buffer's position is then set to n+1
        setPosition(remaining());

        // 写模式下，limit当然就变成了capacity了
        // and its limit is set to its capacity.
        setLimit(getCapacity());

        // 压缩后，mark没意义了，就直接丢弃掉
        // The mark, if defined, is discarded.
        discardMark();
        return this;
    }

    @Override
    public int getInt() {
        // 相比get方法获得1个字节，getInt一次要读取4个字节，所以position一次性推进4字节
        int nextGetIndex = nextGetIndex(4);
        int finallyIndex = ix(nextGetIndex);

        // 从指定的index处读取4个字节，构造成1个int类型返回(基于bigEndian，决定如何解析这4个字节(大端还是小端))
        return BitsUtil.getInt(this, finallyIndex, bigEndian);
    }

    @Override
    public int getInt(int index) {
        // 检查index的合法性，必须是0 <= index < limit-4，避免越界
        checkIndex(index,4);
        // 加上offset偏移量获得最终的index值
        int finallyIndex = ix(index);

        // 从指定的index处读取4个字节，构造成1个int类型返回(基于bigEndian，决定如何解析这4个字节(大端还是小端))
        return BitsUtil.getInt(this, finallyIndex, bigEndian);
    }

    @Override
    public MyByteBuffer putInt(int value) {
        // 获得position的位置(相对操作，有副作用，会推进position)
        // 相比put方法写入1个字节，putInt一次要读取4个字节，所以position一次性推进4字节

        int nextPutIndex = nextPutIndex(4);
        // 加上offset偏移量获得最终的index值
        int finallyIndex = ix(nextPutIndex);

        // 向指定的index处写入大小为4个字节的1个int数据(基于bigEndian，决定写入字节的顺序(大端还是小端))
        BitsUtil.putInt(this, finallyIndex, value, bigEndian);
        return this;
    }

    @Override
    public MyByteBuffer putInt(int index, int value) {
        // 检查index的合法性，必须是0 <= index < limit-4，避免越界
        checkIndex(index,4);

        // 加上offset偏移量获得最终的index值
        int finallyIndex = ix(index);

        // 向指定的index处写入大小为4个字节的1个int数据(基于bigEndian，决定写入字节的顺序(大端还是小端))
        BitsUtil.putInt(this, finallyIndex, value, bigEndian);
        return this;
    }

    @Override
    public boolean isReadOnly() {
        // 可读/可写
        return false;
    }
}
