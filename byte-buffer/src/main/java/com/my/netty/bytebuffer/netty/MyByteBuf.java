package com.my.netty.bytebuffer.netty;

import com.my.netty.bytebuffer.netty.util.ByteProcessor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

public abstract class MyByteBuf implements MyReferenceCounted{

    public abstract int capacity();

    public abstract MyByteBuf capacity(int newCapacity);

    public abstract int maxCapacity();

    public abstract int readerIndex();

    public abstract MyByteBuf readerIndex(int readerIndex);

    public abstract int writerIndex();

    public abstract MyByteBuf writerIndex(int writerIndex);

    public abstract MyByteBuf setIndex(int readerIndex, int writerIndex);

    public abstract int readableBytes();

    public abstract int writableBytes();

    public abstract int maxWritableBytes();

    public abstract MyByteBuf markReaderIndex();

    public abstract MyByteBuf resetReaderIndex();

    public abstract MyByteBuf markWriterIndex();

    public abstract MyByteBuf resetWriterIndex();

    public abstract boolean getBoolean(int index);

    public abstract byte getByte(int index);

    public abstract byte readByte();

    public abstract boolean readBoolean();

    public abstract int getInt(int index);

    public abstract int getIntLE(int index);

    public abstract int readInt();

    public abstract int readIntLE();

    public abstract long getUnsignedInt(int index);

    public abstract long getUnsignedIntLE(int index);

    public abstract MyByteBuf setBoolean(int index, boolean value);

    public abstract MyByteBuf setByte(int index, int value);

    public abstract MyByteBuf writeByte(int value);

    public abstract MyByteBuf writeBytes(byte[] src);

    public abstract MyByteBuf writeBytes(byte[] src, int srcIndex, int length);

    public abstract MyByteBuf writeBytes(MyByteBuf src);

    public abstract MyByteBuf setBytes(int index, byte[] src, int srcIndex, int length);

    public abstract MyByteBuf setBytes(int index, MyByteBuf src, int srcIndex, int length);

    public abstract MyByteBuf writeBoolean(boolean value);

    public abstract ByteBuffer internalNioBuffer(int index, int length);

    public abstract int readBytes(GatheringByteChannel out, int length) throws IOException;

    public abstract MyByteBuf readBytes(byte[] dst);

    public abstract MyByteBuf readBytes(byte[] dst, int dstIndex, int length);

    public abstract MyByteBuf readBytes(MyByteBuf dst);

    public abstract int getBytes(int index, GatheringByteChannel out, int length) throws IOException;

    public abstract MyByteBuf getBytes(int index, MyByteBuf dst, int dstIndex, int length);

    public abstract MyByteBuf getBytes(int index, byte[] dst, int dstIndex, int length);

    public abstract int writeBytes(ScatteringByteChannel in, int length) throws IOException;

    public abstract int setBytes(int index, ScatteringByteChannel in, int length) throws IOException;

    public abstract byte[] array();

    public abstract int arrayOffset();

    public abstract boolean isReadable();

    public abstract String toString(Charset charset);

    public int maxFastWritableBytes() {
        return writableBytes();
    }

    public abstract MyByteBuf discardSomeReadBytes();

    public abstract int forEachByte(int index, int length, ByteProcessor processor);

    public abstract MyByteBuf skipBytes(int length);
}
