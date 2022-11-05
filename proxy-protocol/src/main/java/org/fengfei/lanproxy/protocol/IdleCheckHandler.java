package org.fengfei.lanproxy.protocol;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;


@Slf4j
public class IdleCheckHandler extends IdleStateHandler {

    public static final int USER_CHANNEL_READ_IDLE_TIME = 1200;

    public static final int READ_IDLE_TIME = 60;

    public static final int WRITE_IDLE_TIME = 40;


    public IdleCheckHandler(int readerIdleTimeSeconds, int writerIdleTimeSeconds, int allIdleTimeSeconds) {
        super(readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {

        if (IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT == evt) {
            log.debug("channel write timeout {}", ctx.channel());
            ProxyMessage proxyMessage = new ProxyMessage();
            proxyMessage.setType(ProxyMessage.TYPE_HEARTBEAT);
            ctx.channel().writeAndFlush(proxyMessage);
        } else if (IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT == evt) {
            log.warn("channel read timeout {}", ctx.channel());
            ctx.channel().close();
        }
        super.channelIdle(ctx, evt);
    }
}
