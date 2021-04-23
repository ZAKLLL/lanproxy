package org.fengfei.lanproxy.server.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.fengfei.lanproxy.protocol.Constants;
import org.fengfei.lanproxy.protocol.ProxyMessage;
import org.fengfei.lanproxy.server.HttpProxyServerContainer;
import org.fengfei.lanproxy.server.ProxyChannelManager;
import org.fengfei.lanproxy.server.config.web.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务端使用http代理的 请求方式,使用url 替代 具体端口
 */
public class UserChannelHttpHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static Logger logger = LoggerFactory.getLogger(HttpProxyServerContainer.class);

    private static AtomicLong userIdProducer = new AtomicLong(0);


    private ProxyChannelManager proxyChannelManager;


    public UserChannelHttpHandler() {
        super();
    }

    public UserChannelHttpHandler(ProxyChannelManager proxyChannelManager) {
        this.proxyChannelManager = proxyChannelManager;
    }

    public Map<Channel, Channel> userProxyChannelMap = new ConcurrentHashMap<>();


    public static Channel tmpProxyChannel;

    String value = "GET / HTTP/1.1\n" +
            "Host: 127.0.0.1:7788\n" +
            "Connection: keep-alive\n" +
            "Cache-Control: max-age=0\n" +
            "sec-ch-ua: \" Not A;Brand\";v=\"99\", \"Chromium\";v=\"90\", \"Microsoft Edge\";v=\"90\"\n" +
            "sec-ch-ua-mobile: ?0\n" +
            "Upgrade-Insecure-Requests: 1\n" +
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.72 Safari/537.36 Edg/90.0.818.39\n" +
            "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\n" +
            "Sec-Fetch-Site: none\n" +
            "Sec-Fetch-Mode: navigate\n" +
            "Sec-Fetch-User: ?1\n" +
            "Sec-Fetch-Dest: document\n" +
            "Accept-Encoding: gzip, deflate, br\n" +
            "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6\n" +
            "Cookie: token=8b6cadfe556a4e35a1b19a085c6b31df\n" +
            "\n";

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {

        Channel userChannel = ctx.channel();


        ServerChannelHandler.tmpUserChannnel = userChannel;


        String userId = newUserId();

        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        String httpContent = new String(bytes);
        int fSpaceIndex = httpContent.indexOf(" ");
        int sSpaceIndex = httpContent.indexOf(" ", fSpaceIndex + 1);
        String httpMethod = httpContent.substring(0, fSpaceIndex);
        String url = httpContent.substring(fSpaceIndex + 1, sSpaceIndex);
        if (url.equals("/")) {
            logger.error("未指定clientKey");
        }
        if (!url.endsWith("/")) {
            url += "/";
        }
        int i = url.indexOf("/", 1);
        String clientKeyAndTargetHost = url.substring(1, i);
        url = url.substring(i);
        httpContent = httpMethod + " " + url + httpContent.substring(sSpaceIndex);
        bytes = httpContent.getBytes();
        String[] targetInfos = clientKeyAndTargetHost.split("-");
        String clientKey = targetInfos[0];
        String lanInfo = targetInfos[1];


        //doconnect
        Channel clientChannel = ProxyChannelManager.getClientChannel(clientKey);
        // 用户连接到代理服务器时，设置用户连接不可读，等待代理后端服务器连接成功后再改变为可读状态
        userChannel.config().setOption(ChannelOption.AUTO_READ, false);
        ProxyChannelManager.addUserChannelToCmdChannel(clientChannel, userId, userChannel);
        ProxyMessage proxyMessage = new ProxyMessage();
        proxyMessage.setType(ProxyMessage.TYPE_CONNECT);
        proxyMessage.setUri(userId);
        proxyMessage.setData(lanInfo.getBytes());
        clientChannel.writeAndFlush(proxyMessage);


        Channel proxyChannel = null;
        int retryCnt = 0;
        while (proxyChannel == null && retryCnt < 10) {
            retryCnt++;
            proxyChannel = userChannel.attr(Constants.NEXT_CHANNEL).get();
            TimeUnit.MILLISECONDS.sleep(5);
        }
        if (proxyChannel == null) {
            // 该端口还没有代理客户端
            ctx.channel().close();
        } else {
            userId = ProxyChannelManager.getUserChannelUserId(userChannel);
            proxyMessage = new ProxyMessage();
            proxyMessage.setType(ProxyMessage.P_TYPE_TRANSFER);
            proxyMessage.setUri(userId);


            proxyMessage.setData(bytes);
            proxyChannel.writeAndFlush(proxyMessage);
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        // 通知代理客户端
        Channel userChannel = ctx.channel();
        InetSocketAddress sa = (InetSocketAddress) userChannel.localAddress();
        Channel cmdChannel = ProxyChannelManager.getClientChannel(sa.getPort());
        if (cmdChannel == null) {

            // 该端口还没有代理客户端
            ctx.channel().close();
        } else {

            // 用户连接断开，从控制连接中移除
            String userId = ProxyChannelManager.getUserChannelUserId(userChannel);
            ProxyChannelManager.removeUserChannelFromCmdChannel(cmdChannel, userId);
            Channel proxyChannel = userChannel.attr(Constants.NEXT_CHANNEL).get();
            if (proxyChannel != null && proxyChannel.isActive()) {
                proxyChannel.attr(Constants.NEXT_CHANNEL).remove();
                proxyChannel.attr(Constants.CLIENT_KEY).remove();
                proxyChannel.attr(Constants.USER_ID).remove();

                proxyChannel.config().setOption(ChannelOption.AUTO_READ, true);
                // 通知客户端，用户连接已经断开
                ProxyMessage proxyMessage = new ProxyMessage();
                proxyMessage.setType(ProxyMessage.TYPE_DISCONNECT);
                proxyMessage.setUri(userId);
                proxyChannel.writeAndFlush(proxyMessage);
            }
        }

        super.channelInactive(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {

        // 通知代理客户端
        Channel userChannel = ctx.channel();
        InetSocketAddress sa = (InetSocketAddress) userChannel.localAddress();
        Channel cmdChannel = ProxyChannelManager.getClientChannel(sa.getPort());
        if (cmdChannel == null) {

            // 该端口还没有代理客户端
            ctx.channel().close();
        } else {
            Channel proxyChannel = userChannel.attr(Constants.NEXT_CHANNEL).get();
            if (proxyChannel != null) {
                proxyChannel.config().setOption(ChannelOption.AUTO_READ, userChannel.isWritable());
            }
        }

        super.channelWritabilityChanged(ctx);
    }

    private static String newUserId() {
        return String.valueOf(userIdProducer.incrementAndGet());
    }
}
