package com.my.netty.core.reactor.exception;

public class MyNettyException extends RuntimeException{

    public MyNettyException() {}

    public MyNettyException(String message, Throwable cause) {
        super(message, cause);
    }

    public MyNettyException(String message) {
        super(message);
    }
}
