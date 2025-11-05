//package com.my.netty.core.reactor.handler.codec;
//
//import com.my.netty.bytebuffer.netty.MyByteBuf;
//import com.my.netty.bytebuffer.netty.util.ObjectUtil;
//import com.my.netty.core.reactor.handler.context.MyChannelHandlerContext;
//
//import java.util.List;
//
//public class MyFixedLengthFrameDecoder extends MyByteToMessageDecoder{
//    private final int frameLength;
//
//    /**
//     * Creates a new instance.
//     *
//     * @param frameLength the length of the frame
//     */
//    public MyFixedLengthFrameDecoder(int frameLength) {
//        ObjectUtil.checkPositive(frameLength, "frameLength");
//        this.frameLength = frameLength;
//    }
//
//    @Override
//    protected final void decode(MyChannelHandlerContext ctx, MyByteBuf in, List<Object> out) throws Exception {
//        Object decoded = decode(ctx, in);
//        if (decoded != null) {
//            out.add(decoded);
//        }
//    }
//
//    protected Object decode(@SuppressWarnings("UnusedParameters") MyChannelHandlerContext ctx, MyByteBuf in) {
//        if (in.readableBytes() < frameLength) {
//            return null;
//        } else {
//            // netty中使用slice机制(readRetainedSlice)，切分出来的ByteBuf与原ByteBuf共享底层内存，性能好很多，但是较为复杂
//            // MyNetty简单起见，直接new一个新的ByteBuf来实现decode解码的功能
//            MyByteBuf message = ctx.alloc().heapBuffer(frameLength);
//
//            in.readBytes(message);
//
//            return message;
//        }
//    }
//}
