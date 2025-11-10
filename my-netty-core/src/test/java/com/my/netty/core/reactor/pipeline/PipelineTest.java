package com.my.netty.core.reactor.pipeline;

import com.my.netty.core.reactor.exception.MyNettyException;
import com.my.netty.core.reactor.handler.pipeline.MyChannelPipeline;
import org.junit.Assert;
import org.junit.Test;

public class PipelineTest {

    @Test
    public void testAddNoSharableHandlerMulti(){
        MyChannelPipeline myChannelPipeline = new MyChannelPipeline(null);

        TestNoSharableHandler noSharableHandler = new TestNoSharableHandler();
        myChannelPipeline.addFirst(noSharableHandler);

        MyNettyException exception = null;
        try {
            myChannelPipeline.addLast(noSharableHandler);
        }catch (MyNettyException ex){
            exception = ex;
        }

        Assert.assertNotNull(exception);
        Assert.assertTrue(exception.getMessage().contains("is not a @Sharable handler"));
    }

    @Test
    public void testSharableHandler(){
        MyChannelPipeline myChannelPipeline = new MyChannelPipeline(null);

        TestSharableHandler sharableHandler = new TestSharableHandler();

        MyNettyException exception = null;
        try {
            myChannelPipeline.addFirst(sharableHandler);
            myChannelPipeline.addLast(sharableHandler);
            myChannelPipeline.addLast(sharableHandler);
            myChannelPipeline.addFirst(sharableHandler);
        }catch (MyNettyException ex){
            exception = ex;
        }

        Assert.assertNull(exception);
    }
}
