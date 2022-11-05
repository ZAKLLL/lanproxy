package org.fengfei.lanparoxy.client.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;
import lombok.SneakyThrows;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class NettyUdpSendTest {

    @SneakyThrows
    public static void main(String[] args) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group( new NioEventLoopGroup());
        bootstrap.channel(NioDatagramChannel.class);
        bootstrap.handler(new ChannelInitializer<NioDatagramChannel>() {
            @Override
            protected void initChannel(NioDatagramChannel ch) {
                ch.pipeline().addLast(new ChannelHandler() {
                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

                    }

                    @Override
                    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

                    }
                });
            }
        });
        Channel channel = bootstrap.bind(8081).sync().channel();
//        ChannelFuture connect = bootstrap.connect("127.0.0.1", 1234);
//        connect.get();
//        Channel channel = connect.channel();
        String retData = "hi" + new SimpleDateFormat().format(new Date());
        byte[] bytes = retData.getBytes(StandardCharsets.UTF_8);
        channel.writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer(retData, CharsetUtil.UTF_8), new InetSocketAddress("127.0.0.1",8989)));
    }
}
