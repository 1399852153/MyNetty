package com.my.netty.core.reactor.model;

public class EchoMessageFrame {

    /**
     * 协议魔数，随便取的
     * */
    public static final int MAGIC = 0x2233;

    public EchoMessageFrame() {
    }

    public EchoMessageFrame(String messageContent) {
        this.messageContent = messageContent;
        this.msgLength = messageContent.length();
    }

    /**
     * 消息内容，实际消息体的json字符串
     * */
    private String messageContent;

    /**
     * 用于校验解码是否成功的属性
     * */
    private Integer msgLength;

    public String getMessageContent() {
        return messageContent;
    }

    public Integer getMsgLength() {
        return msgLength;
    }

    @Override
    public String toString() {
        return "EchoMessageFrame{" +
            "messageContent='" + messageContent + '\'' +
            ", msgLength=" + msgLength +
            '}';
    }
}
