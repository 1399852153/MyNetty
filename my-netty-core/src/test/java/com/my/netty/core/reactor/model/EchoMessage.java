package com.my.netty.core.reactor.model;

public class EchoMessage {

    /**
     * 协议魔数，随便取的
     * */
    public static final short MAGIC = (short)0x2233;

    /**
     * 消息内容
     * */
    private String messageContent;

    /**
     * 用于校验解码是否成功的属性
     * */
    private Integer msgLength;

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public Integer getMsgLength() {
        return msgLength;
    }

    public void setMsgLength(Integer msgLength) {
        this.msgLength = msgLength;
    }

    @Override
    public String toString() {
        return "EchoMessage{" +
            "messageContent='" + messageContent + '\'' +
            ", msgLength=" + msgLength +
            '}';
    }
}
