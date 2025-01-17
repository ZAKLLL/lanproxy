package org.fengfei.lanproxy.client;

import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import io.netty.channel.socket.nio.NioDatagramChannel;
import org.fengfei.lanproxy.client.handlers.ClientChannelHandler;
import org.fengfei.lanproxy.client.handlers.RealServerChannelHandler;
import org.fengfei.lanproxy.client.udp.realChannelHandler.TcpOverUdpRealServerChannelHandler;
import org.fengfei.lanproxy.client.listener.ChannelStatusListener;
import org.fengfei.lanproxy.client.udp.realChannelHandler.UdpToUdpRealServerChannelHandler;
import org.fengfei.lanproxy.common.Config;
import org.fengfei.lanproxy.common.container.Container;
import org.fengfei.lanproxy.common.container.ContainerHelper;
import org.fengfei.lanproxy.protocol.IdleCheckHandler;
import org.fengfei.lanproxy.protocol.ProxyMessage;
import org.fengfei.lanproxy.protocol.ProxyMessageDecoder;
import org.fengfei.lanproxy.protocol.ProxyMessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;

public class ProxyClientContainer implements Container, ChannelStatusListener {

    private static Logger logger = LoggerFactory.getLogger(ProxyClientContainer.class);

    private static final int MAX_FRAME_LENGTH = 1024 * 1024;

    private static final int LENGTH_FIELD_OFFSET = 0;

    private static final int LENGTH_FIELD_LENGTH = 4;

    private static final int INITIAL_BYTES_TO_STRIP = 0;

    private static final int LENGTH_ADJUSTMENT = 0;

    private NioEventLoopGroup workerGroup;

    private Bootstrap clientBootstrap;

    private Bootstrap realServerBootstrap;


    private Bootstrap tcpOverUdpRealServerBootstrap;

    //udp p2p
    private Bootstrap udpRealServerBootstrap;

    private Config config = Config.getInstance();

    private SSLContext sslContext;

    private long sleepTimeMill = 1000;

    public ProxyClientContainer() {
        workerGroup = new NioEventLoopGroup();

        /*------------tcp 访问目标服务器-------------------*/
        realServerBootstrap = new Bootstrap();
        realServerBootstrap.group(workerGroup);
        realServerBootstrap.channel(NioSocketChannel.class);
        realServerBootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new RealServerChannelHandler());
            }
        });


        /*-------------------使用udp传输数据的tcp代理访问目标服务器(tcp)----------------------------*/
        tcpOverUdpRealServerBootstrap = new Bootstrap();
        tcpOverUdpRealServerBootstrap.group(workerGroup);
        tcpOverUdpRealServerBootstrap.channel(NioSocketChannel.class);
        tcpOverUdpRealServerBootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new TcpOverUdpRealServerChannelHandler());
            }
        });


        /*-------------------udp 点对点(目标服务器与客户端访问均为udp)----------------------------*/
        udpRealServerBootstrap = new Bootstrap();
        udpRealServerBootstrap.group(workerGroup);
        udpRealServerBootstrap.channel(NioDatagramChannel.class);
        udpRealServerBootstrap.handler(new ChannelInitializer<NioDatagramChannel>() {
            @Override
            protected void initChannel(NioDatagramChannel ch) {
                ch.pipeline().addLast(new UdpToUdpRealServerChannelHandler());
            }
        });


        /*----------------当前客户端与中心服务器的链接-----------------*/
        clientBootstrap = new Bootstrap();
        clientBootstrap.group(workerGroup);
        clientBootstrap.channel(NioSocketChannel.class);
        clientBootstrap.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                if (Config.getInstance().getBooleanValue("ssl.enable", false)) {
                    if (sslContext == null) {
                        sslContext = SslContextCreator.createSSLContext();
                    }

                    ch.pipeline().addLast(createSslHandler(sslContext));
                }
                ch.pipeline().addLast(new ProxyMessageDecoder(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP));
                ch.pipeline().addLast(new ProxyMessageEncoder());
                ch.pipeline().addLast(new IdleCheckHandler(IdleCheckHandler.READ_IDLE_TIME, IdleCheckHandler.WRITE_IDLE_TIME - 10, 0));
                ch.pipeline().addLast(new ClientChannelHandler(
                        realServerBootstrap,
                        clientBootstrap,
                        tcpOverUdpRealServerBootstrap,
                        udpRealServerBootstrap,
                        ProxyClientContainer.this));
            }
        });
    }

    @Override
    public void start() {
        connectProxyServer();
    }

    private ChannelHandler createSslHandler(SSLContext sslContext) {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(true);
        return new SslHandler(sslEngine);
    }

    private void connectProxyServer() {

        clientBootstrap.connect(config.getStringValue("server.host"), config.getIntValue("server.port")).addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {

                    // 连接成功，向服务器发送客户端认证信息（clientKey）
                    ClientChannelMannager.setCmdChannel(future.channel());
                    ProxyMessage proxyMessage = new ProxyMessage();
                    proxyMessage.setType(ProxyMessage.C_TYPE_AUTH);
                    proxyMessage.setUri(config.getStringValue("client.key"));
                    future.channel().writeAndFlush(proxyMessage);
                    sleepTimeMill = 1000;
                    logger.info("connect proxy server success, {}", future.channel());
                } else {
                    logger.warn("connect proxy server failed", future.cause());

                    // 连接失败，发起重连
                    reconnectWait();
                    connectProxyServer();
                }
            }
        });
    }

    @Override
    public void stop() {
        workerGroup.shutdownGracefully();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        reconnectWait();
        connectProxyServer();
    }

    private void reconnectWait() {
        try {
            if (sleepTimeMill > 60000) {
                sleepTimeMill = 1000;
            }

            synchronized (this) {
                sleepTimeMill = sleepTimeMill * 2;
                wait(sleepTimeMill);
            }
        } catch (InterruptedException e) {
        }
    }

    public static void main(String[] args) {

        ContainerHelper.start(Arrays.asList(new Container[]{new ProxyClientContainer()}));
    }

}
